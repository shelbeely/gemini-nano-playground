package com.dino.nanoplayground.ble

import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton manager for the NanoAI BLE GATT server.
 *
 * Acts as the source of truth for BLE state that the Settings UI observes.
 * The actual GATT server and BLE advertising run inside [NanoBleService].
 */
@Singleton
class NanoBleManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _connectedCentralsCount = MutableStateFlow(0)
    val connectedCentralsCount: StateFlow<Int> = _connectedCentralsCount.asStateFlow()

    private val _deviceName = MutableStateFlow("NanoAI")
    val deviceName: StateFlow<String> = _deviceName.asStateFlow()

    private val _advertiseWifiInfo = MutableStateFlow(true)
    val advertiseWifiInfo: StateFlow<Boolean> = _advertiseWifiInfo.asStateFlow()

    /** Wi-Fi IP to embed in the BLE Config characteristic for discovery handoff. */
    var wifiIp: String = ""

    /** Wi-Fi port to embed in the BLE Config characteristic for discovery handoff. */
    var wifiPort: Int = 11434

    private val _advertiseError = MutableStateFlow<String?>(null)
    /** Non-null when BLE advertising failed; null when advertising is healthy. */
    val advertiseError: StateFlow<String?> = _advertiseError.asStateFlow()

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    fun startServer() {
        if (_isRunning.value) return
        context.startForegroundService(Intent(context, NanoBleService::class.java))
        _isRunning.value = true
    }

    fun stopServer() {
        context.stopService(Intent(context, NanoBleService::class.java))
        _isRunning.value = false
        _connectedCentralsCount.value = 0
    }

    // ── Settings mutations ────────────────────────────────────────────────────

    fun setDeviceName(name: String) {
        _deviceName.value = name.ifBlank { "NanoAI" }
    }

    fun setAdvertiseWifiInfo(enabled: Boolean) {
        _advertiseWifiInfo.value = enabled
    }

    // ── Called by NanoBleService ──────────────────────────────────────────────

    internal fun updateConnectedCount(count: Int) {
        _connectedCentralsCount.value = count
    }

    internal fun onServiceStopped() {
        _isRunning.value = false
        _connectedCentralsCount.value = 0
        _advertiseError.value = null
    }

    internal fun onAdvertiseFailed(errorCode: Int) {
        _advertiseError.value = "BLE advertising failed (code $errorCode). " +
                "Direct connections are still accepted."
    }

    // ── Capability helpers ────────────────────────────────────────────────────

    fun isBluetoothAvailable(): Boolean {
        val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return bm?.adapter != null
    }

    fun isBluetoothEnabled(): Boolean {
        val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return bm?.adapter?.isEnabled == true
    }
}
