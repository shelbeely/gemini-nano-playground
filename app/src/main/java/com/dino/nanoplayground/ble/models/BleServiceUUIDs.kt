package com.dino.nanoplayground.ble.models

import java.util.UUID

/**
 * Custom GATT service and characteristic UUIDs for the NanoAI BLE transport layer.
 *
 * Service: NanoAI Inference (12340000-0000-1000-8000-00805f9b34fb)
 *
 * Characteristics:
 *  - PROMPT_WRITE    (…0001): Client writes chunked prompt frames (WRITE | WRITE_NO_RESPONSE)
 *  - RESPONSE_NOTIFY (…0002): Server sends chunked response frames (READ | NOTIFY)
 *  - CONTROL         (…0003): JSON control messages, e.g. {"cmd":"cancel"} (WRITE)
 *  - STATUS_NOTIFY   (…0004): JSON status updates, e.g. {"status":"idle"} (READ | NOTIFY)
 *  - CONFIG_READ     (…0005): JSON discovery handoff — returns Wi-Fi IP/port (READ)
 */
object BleServiceUUIDs {
    val SERVICE: UUID = UUID.fromString("12340000-0000-1000-8000-00805f9b34fb")
    val PROMPT_WRITE: UUID = UUID.fromString("12340001-0000-1000-8000-00805f9b34fb")
    val RESPONSE_NOTIFY: UUID = UUID.fromString("12340002-0000-1000-8000-00805f9b34fb")
    val CONTROL: UUID = UUID.fromString("12340003-0000-1000-8000-00805f9b34fb")
    val STATUS_NOTIFY: UUID = UUID.fromString("12340004-0000-1000-8000-00805f9b34fb")
    val CONFIG_READ: UUID = UUID.fromString("12340005-0000-1000-8000-00805f9b34fb")

    /** Standard Client Characteristic Configuration Descriptor (CCCD). */
    val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
}
