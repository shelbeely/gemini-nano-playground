package com.dino.nanoplayground.ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothProfile
import com.dino.nanoplayground.ble.models.BleServiceUUIDs
import com.dino.nanoplayground.ble.models.BleTransferState
import com.google.mlkit.genai.prompt.GenerativeModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * [BluetoothGattServerCallback] implementation for the NanoAI GATT peripheral.
 *
 * Responsibilities:
 * - Track connected centrals and enforce a [MAX_CONNECTIONS] limit.
 * - Reassemble chunked prompt frames from the central using [BleChunkProtocol].
 * - Run on-device inference via [generativeModel] and stream response frames back.
 * - Serve status and config reads synchronously.
 * - Gate outgoing notifications via [onNotificationSent] to avoid BLE buffer overflow.
 *
 * [gattServer] must be assigned immediately after the server is opened, before any
 * connections arrive.
 */
class BleGattCallback(
    private val generativeModel: GenerativeModel,
    private val bleManager: NanoBleManager,
    private val scope: CoroutineScope,
) : BluetoothGattServerCallback() {

    lateinit var gattServer: BluetoothGattServer

    private val connections = ConcurrentHashMap<String, BleTransferState>()
    private val pendingNotify = ConcurrentHashMap<String, CompletableDeferred<Unit>>()

    companion object {
        private const val MAX_CONNECTIONS = 3
        /** Milliseconds to wait for each [onNotificationSent] before moving on. */
        private const val NOTIFY_TIMEOUT_MS = 5_000L
    }

    // ── Connection lifecycle ──────────────────────────────────────────────────

    override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
        when (newState) {
            BluetoothProfile.STATE_CONNECTED -> {
                if (connections.size < MAX_CONNECTIONS) {
                    connections[device.address] = BleTransferState(device)
                    bleManager.updateConnectedCount(connections.size)
                } else {
                    gattServer.cancelConnection(device)
                }
            }
            BluetoothProfile.STATE_DISCONNECTED -> {
                connections.remove(device.address)
                pendingNotify.remove(device.address)
                bleManager.updateConnectedCount(connections.size)
            }
        }
    }

    // ── MTU negotiation ───────────────────────────────────────────────────────

    override fun onMtuChanged(device: BluetoothDevice, mtu: Int) {
        connections[device.address]?.let { state ->
            connections[device.address] = state.copy(mtu = mtu)
        }
    }

    // ── Descriptor writes (CCCD) ──────────────────────────────────────────────

    override fun onDescriptorWriteRequest(
        device: BluetoothDevice,
        requestId: Int,
        descriptor: BluetoothGattDescriptor,
        preparedWrite: Boolean,
        responseNeeded: Boolean,
        offset: Int,
        value: ByteArray,
    ) {
        val state = connections[device.address]
        if (state != null && descriptor.uuid == BleServiceUUIDs.CCCD) {
            val enabled = value.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            connections[device.address] = when (descriptor.characteristic.uuid) {
                BleServiceUUIDs.RESPONSE_NOTIFY -> state.copy(responseNotificationsEnabled = enabled)
                BleServiceUUIDs.STATUS_NOTIFY -> state.copy(statusNotificationsEnabled = enabled)
                else -> state
            }
        }
        if (responseNeeded) {
            gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
        }
    }

    // ── Characteristic reads ──────────────────────────────────────────────────

    override fun onCharacteristicReadRequest(
        device: BluetoothDevice,
        requestId: Int,
        offset: Int,
        characteristic: BluetoothGattCharacteristic,
    ) {
        val full = when (characteristic.uuid) {
            BleServiceUUIDs.STATUS_NOTIFY -> buildStatusJson().toByteArray()
            BleServiceUUIDs.CONFIG_READ -> buildConfigJson().toByteArray()
            else -> ByteArray(0)
        }
        val slice = if (offset < full.size) full.copyOfRange(offset, full.size) else ByteArray(0)
        gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, slice)
    }

    // ── Characteristic writes ─────────────────────────────────────────────────

    override fun onCharacteristicWriteRequest(
        device: BluetoothDevice,
        requestId: Int,
        characteristic: BluetoothGattCharacteristic,
        preparedWrite: Boolean,
        responseNeeded: Boolean,
        offset: Int,
        value: ByteArray,
    ) {
        if (responseNeeded) {
            gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
        }
        when (characteristic.uuid) {
            BleServiceUUIDs.PROMPT_WRITE -> handlePromptChunk(device, value)
            BleServiceUUIDs.CONTROL -> handleControlMessage(device, value)
        }
    }

    // ── Notification acknowledgement ──────────────────────────────────────────

    override fun onNotificationSent(device: BluetoothDevice, status: Int) {
        pendingNotify[device.address]?.complete(Unit)
    }

    // ── Prompt chunk assembly & inference ─────────────────────────────────────

    private fun handlePromptChunk(device: BluetoothDevice, frameBytes: ByteArray) {
        val state = connections[device.address] ?: return
        try {
            val frame = BleChunkProtocol.decode(frameBytes)
            state.incomingChunks[frame.seq] = frame.payload

            if (frame.total > 0 && state.totalExpectedChunks == 0) {
                connections[device.address] = state.copy(totalExpectedChunks = frame.total)
            }

            if (BleChunkProtocol.isLast(frame.flags)) {
                val current = connections[device.address] ?: return
                val total = if (frame.total > 0) frame.total else current.totalExpectedChunks
                val assembled = reassemble(current.incomingChunks, total)
                current.incomingChunks.clear()
                connections[device.address] = current.copy(totalExpectedChunks = 0)

                val prompt = assembled.toString(Charsets.UTF_8)
                scope.launch(Dispatchers.IO) { runInference(device, prompt) }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun handleControlMessage(device: BluetoothDevice, value: ByteArray) {
        try {
            val json = JSONObject(value.toString(Charsets.UTF_8))
            when (json.optString("cmd")) {
                "clear" -> connections[device.address]?.incomingChunks?.clear()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun runInference(device: BluetoothDevice, prompt: String) {
        notifyStatus(device, "inferencing")
        try {
            val result = generativeModel.generateContent(prompt)
            val text = result.candidates.joinToString("") { it.text }
            if (text.isEmpty()) {
                val emptyErr = JSONObject().apply { put("error", "model returned no content") }.toString()
                sendResponseFrames(device, emptyErr.toByteArray())
            } else {
                sendResponseFrames(device, text.toByteArray(Charsets.UTF_8))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            val err = JSONObject().apply {
                put("error", e.message ?: "unknown error")
            }.toString()
            sendResponseFrames(device, err.toByteArray())
        } finally {
            notifyStatus(device, "idle")
        }
    }

    // ── Outgoing notifications ────────────────────────────────────────────────

    /**
     * Sends [responseBytes] to [device] as a sequence of NOTIFY frames, waiting for
     * each [onNotificationSent] acknowledgement before sending the next to avoid
     * BLE buffer overflow.
     */
    private suspend fun sendResponseFrames(device: BluetoothDevice, responseBytes: ByteArray) {
        val state = connections[device.address] ?: return
        if (!state.responseNotificationsEnabled) return

        val characteristic = gattServer.getService(BleServiceUUIDs.SERVICE)
            ?.getCharacteristic(BleServiceUUIDs.RESPONSE_NOTIFY) ?: return

        val frames = BleChunkProtocol.encode(responseBytes, state.mtu)
        for (frame in frames) {
            val deferred = CompletableDeferred<Unit>()
            pendingNotify[device.address] = deferred
            gattServer.notifyCharacteristicChanged(device, characteristic, false, frame)
            withTimeoutOrNull(NOTIFY_TIMEOUT_MS) { deferred.await() }
            pendingNotify.remove(device.address)
        }
    }

    private fun notifyStatus(device: BluetoothDevice, status: String) {
        val state = connections[device.address] ?: return
        if (!state.statusNotificationsEnabled) return

        val characteristic = gattServer.getService(BleServiceUUIDs.SERVICE)
            ?.getCharacteristic(BleServiceUUIDs.STATUS_NOTIFY) ?: return

        gattServer.notifyCharacteristicChanged(
            device, characteristic, false, buildStatusJson(status).toByteArray()
        )
    }

    // ── JSON helpers ──────────────────────────────────────────────────────────

    private fun buildStatusJson(status: String = "idle") = JSONObject().apply {
        put("status", status)
        put("model", "gemini-nano")
    }.toString()

    private fun buildConfigJson() = JSONObject().apply {
        if (bleManager.advertiseWifiInfo.value && bleManager.wifiIp.isNotEmpty()) {
            put("wifi_ip", bleManager.wifiIp)
            put("wifi_port", bleManager.wifiPort)
            put("api_base", "http://${bleManager.wifiIp}:${bleManager.wifiPort}/v1")
        }
    }.toString()

    // ── Reassembly helper ─────────────────────────────────────────────────────

    private fun reassemble(chunks: Map<Int, ByteArray>, total: Int): ByteArray {
        val size = (0 until total).sumOf { chunks[it]?.size ?: 0 }
        val result = ByteArray(size)
        var offset = 0
        for (idx in 0 until total) {
            val chunk = chunks[idx] ?: continue
            chunk.copyInto(result, offset)
            offset += chunk.size
        }
        return result
    }
}
