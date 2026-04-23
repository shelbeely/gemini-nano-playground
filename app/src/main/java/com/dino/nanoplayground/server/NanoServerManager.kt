package com.dino.nanoplayground.server

import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton manager for the NanoAI HTTP (Wi-Fi) server.
 *
 * Exposes observable state used by the Settings UI and by [NanoBleManager]
 * for BLE discovery handoff (Config characteristic).
 *
 * The actual Ktor server lifecycle is handled in [NanoServerService].
 */
@Singleton
class NanoServerManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _port = MutableStateFlow(11434)
    val port: StateFlow<Int> = _port.asStateFlow()

    private val _wifiIp = MutableStateFlow("")
    val wifiIp: StateFlow<String> = _wifiIp.asStateFlow()

    /** Optional bearer token.  Empty string means authentication is disabled. */
    private val _bearerToken = MutableStateFlow("")
    val bearerToken: StateFlow<String> = _bearerToken.asStateFlow()

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    fun startServer() {
        if (_isRunning.value) return
        _wifiIp.value = resolveWifiIp()
        context.startForegroundService(
            Intent(context, NanoServerService::class.java).apply {
                putExtra(NanoServerService.EXTRA_PORT, _port.value)
            }
        )
        _isRunning.value = true
    }

    fun stopServer() {
        context.stopService(Intent(context, NanoServerService::class.java))
        _isRunning.value = false
        _wifiIp.value = ""
    }

    // ── Settings mutations ────────────────────────────────────────────────────

    fun setPort(port: Int) {
        _port.value = port.coerceIn(1024, 65535)
    }

    fun setBearerToken(token: String) {
        _bearerToken.value = token
    }

    // ── Called by NanoServerService ───────────────────────────────────────────

    internal fun onServiceStopped() {
        _isRunning.value = false
        _wifiIp.value = ""
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun resolveWifiIp(): String {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            ?: return ""
        val info = wm.connectionInfo ?: return ""
        val ip = info.ipAddress
        if (ip == 0) return ""
        return "${ip and 0xFF}.${(ip shr 8) and 0xFF}.${(ip shr 16) and 0xFF}.${(ip shr 24) and 0xFF}"
    }
}
