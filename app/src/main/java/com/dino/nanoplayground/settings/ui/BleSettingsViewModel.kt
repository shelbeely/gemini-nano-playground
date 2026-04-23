package com.dino.nanoplayground.settings.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import com.dino.nanoplayground.ble.NanoBleManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * [HiltViewModel] that exposes BLE server state to the Settings screen and
 * forwards user actions to [NanoBleManager].
 *
 * Since minSdk is 33 (Android 13), BLUETOOTH_ADVERTISE and BLUETOOTH_CONNECT
 * are always required at runtime.
 */
@HiltViewModel
class BleSettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bleManager: NanoBleManager,
) : ViewModel() {

    val isBleRunning: StateFlow<Boolean> = bleManager.isRunning
    val connectedCentralsCount: StateFlow<Int> = bleManager.connectedCentralsCount
    val deviceName: StateFlow<String> = bleManager.deviceName
    val advertiseWifiInfo: StateFlow<Boolean> = bleManager.advertiseWifiInfo
    val advertiseError: StateFlow<String?> = bleManager.advertiseError

    val isBleAvailable: Boolean get() = bleManager.isBluetoothAvailable()
    val isBleEnabled: Boolean get() = bleManager.isBluetoothEnabled()

    val requiredPermissions: Array<String> = arrayOf(
        Manifest.permission.BLUETOOTH_ADVERTISE,
        Manifest.permission.BLUETOOTH_CONNECT,
    )

    fun hasRequiredPermissions(): Boolean = requiredPermissions.all { permission ->
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    fun toggleBleServer(enabled: Boolean) {
        if (enabled) bleManager.startServer() else bleManager.stopServer()
    }

    fun setDeviceName(name: String) = bleManager.setDeviceName(name)

    fun setAdvertiseWifiInfo(enabled: Boolean) = bleManager.setAdvertiseWifiInfo(enabled)
}
