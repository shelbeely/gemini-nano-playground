package com.dino.nanoplayground.ble.models

import android.bluetooth.BluetoothDevice
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks per-connection BLE transfer state for a connected central device.
 *
 * [incomingChunks] is a [ConcurrentHashMap] because GATT callbacks may arrive on
 * different binder threads. All other fields are replaced atomically via [copy].
 */
data class BleTransferState(
    val device: BluetoothDevice,
    /** Negotiated MTU for this connection. Default 23 bytes (20 usable). */
    val mtu: Int = 23,
    /** Accumulated prompt payload chunks keyed by 0-based sequence index. */
    val incomingChunks: ConcurrentHashMap<Int, ByteArray> = ConcurrentHashMap(),
    /** Total number of prompt frames expected for the current transfer. */
    val totalExpectedChunks: Int = 0,
    /** Whether the central has enabled NOTIFY on the Response characteristic. */
    val responseNotificationsEnabled: Boolean = false,
    /** Whether the central has enabled NOTIFY on the Status characteristic. */
    val statusNotificationsEnabled: Boolean = false,
)
