package com.dino.nanoplayground.settings.ui

import androidx.lifecycle.ViewModel
import com.dino.nanoplayground.server.NanoServerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * [HiltViewModel] that exposes HTTP server state to the Settings screen and
 * forwards user actions to [NanoServerManager].
 */
@HiltViewModel
class HttpSettingsViewModel @Inject constructor(
    private val serverManager: NanoServerManager,
) : ViewModel() {

    val isServerRunning: StateFlow<Boolean> = serverManager.isRunning
    val port: StateFlow<Int> = serverManager.port
    val wifiIp: StateFlow<String> = serverManager.wifiIp
    val bearerToken: StateFlow<String> = serverManager.bearerToken

    fun toggleServer(enabled: Boolean) {
        if (enabled) serverManager.startServer() else serverManager.stopServer()
    }

    fun setPort(port: Int) = serverManager.setPort(port)

    fun setBearerToken(token: String) = serverManager.setBearerToken(token)
}
