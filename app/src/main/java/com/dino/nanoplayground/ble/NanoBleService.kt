package com.dino.nanoplayground.ble

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Intent
import android.os.IBinder
import android.os.ParcelUuid
import androidx.core.app.NotificationCompat
import com.dino.nanoplayground.ble.models.BleServiceUUIDs
import com.google.mlkit.genai.prompt.GenerativeModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import javax.inject.Inject

/**
 * Foreground service that hosts the NanoAI BLE GATT peripheral.
 *
 * On [onStartCommand]:
 * 1. Opens a [BluetoothGattServer] with the custom NanoAI service.
 * 2. Starts BLE advertising so centrals can discover this device by name.
 * 3. Shows a persistent foreground notification.
 *
 * On [onDestroy]:
 * - Stops advertising and closes the GATT server.
 * - Notifies [NanoBleManager] so the Settings UI reflects the stopped state.
 *
 * The [GenerativeModel] singleton is shared with the main chat screen — both
 * run inference on the same model instance via the Hilt [SingletonComponent].
 */
@AndroidEntryPoint
class NanoBleService : Service() {

    @Inject lateinit var generativeModel: GenerativeModel
    @Inject lateinit var bleManager: NanoBleManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var gattServer: BluetoothGattServer? = null
    private var advertiseCallback: AdvertiseCallback? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        val callback = BleGattCallback(generativeModel, bleManager, serviceScope)

        gattServer = bluetoothManager.openGattServer(this, callback)?.also { server ->
            callback.gattServer = server
            server.addService(buildGattService())
        }

        startAdvertising(bluetoothManager)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAdvertising()
        gattServer?.close()
        gattServer = null
        serviceScope.cancel()
        bleManager.onServiceStopped()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── GATT service definition ───────────────────────────────────────────────

    private fun buildGattService(): BluetoothGattService {
        val service = BluetoothGattService(
            BleServiceUUIDs.SERVICE,
            BluetoothGattService.SERVICE_TYPE_PRIMARY,
        )

        // Prompt Write — central writes chunked prompt frames
        service.addCharacteristic(
            BluetoothGattCharacteristic(
                BleServiceUUIDs.PROMPT_WRITE,
                BluetoothGattCharacteristic.PROPERTY_WRITE or
                        BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                BluetoothGattCharacteristic.PERMISSION_WRITE,
            )
        )

        // Response Notify — server streams response frames back
        val responseChar = BluetoothGattCharacteristic(
            BleServiceUUIDs.RESPONSE_NOTIFY,
            BluetoothGattCharacteristic.PROPERTY_READ or
                    BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ,
        )
        responseChar.addDescriptor(cccdDescriptor())
        service.addCharacteristic(responseChar)

        // Control — cancel / clear commands
        service.addCharacteristic(
            BluetoothGattCharacteristic(
                BleServiceUUIDs.CONTROL,
                BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_WRITE,
            )
        )

        // Status Notify — idle / inferencing state
        val statusChar = BluetoothGattCharacteristic(
            BleServiceUUIDs.STATUS_NOTIFY,
            BluetoothGattCharacteristic.PROPERTY_READ or
                    BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ,
        )
        statusChar.addDescriptor(cccdDescriptor())
        service.addCharacteristic(statusChar)

        // Config Read — Wi-Fi discovery handoff
        service.addCharacteristic(
            BluetoothGattCharacteristic(
                BleServiceUUIDs.CONFIG_READ,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ,
            )
        )

        return service
    }

    private fun cccdDescriptor() = BluetoothGattDescriptor(
        BleServiceUUIDs.CCCD,
        BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE,
    )

    // ── BLE advertising ───────────────────────────────────────────────────────

    private fun startAdvertising(bluetoothManager: BluetoothManager) {
        val adapter = bluetoothManager.adapter ?: return
        val advertiser = adapter.bluetoothLeAdvertiser ?: return

        adapter.name = bleManager.deviceName.value

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
            .setConnectable(true)
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .build()

        // Primary advertisement: include service UUID (stays within 31-byte AD limit)
        val advertiseData = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(BleServiceUUIDs.SERVICE))
            .setIncludeDeviceName(false)
            .build()

        // Scan response: device name so clients can filter by "NanoAI"
        val scanResponse = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .build()

        advertiseCallback = object : AdvertiseCallback() {
            override fun onStartFailure(errorCode: Int) {
                // Advertising failed — update manager so the UI can reflect the degraded state.
                // The GATT server remains open and still accepts directed connections.
                bleManager.onAdvertiseFailed(errorCode)
            }
        }

        advertiser.startAdvertising(settings, advertiseData, scanResponse, advertiseCallback)
    }

    private fun stopAdvertising() {
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as? BluetoothManager
        val advertiser = bluetoothManager?.adapter?.bluetoothLeAdvertiser
        advertiseCallback?.let { advertiser?.stopAdvertising(it) }
        advertiseCallback = null
    }

    // ── Foreground notification ───────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "BLE Server", NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("NanoAI BLE active")
        .setContentText("Advertising as \"${bleManager.deviceName.value}\"")
        .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
        .setOngoing(true)
        .build()

    companion object {
        private const val CHANNEL_ID = "nano_ble_server"
        private const val NOTIFICATION_ID = 2
    }
}
