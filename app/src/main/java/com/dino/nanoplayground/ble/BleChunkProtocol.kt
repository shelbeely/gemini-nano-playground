package com.dino.nanoplayground.ble

/**
 * Binary framing protocol that splits large payloads across MTU-limited BLE packets.
 *
 * ## Frame layout (big-endian)
 * ```
 * [1 byte : flags ] [2 bytes : seq (0-based)] [2 bytes : total] [N bytes : payload]
 * ```
 *
 * ## Flag bitmask
 * | Bit  | Meaning                                                              |
 * |------|----------------------------------------------------------------------|
 * | 0x01 | FIRST     – this is the first frame of the transfer                  |
 * | 0x02 | LAST      – this is the final frame of the transfer                  |
 * | 0x04 | MORE      – additional frames will follow                            |
 * | 0x08 | TRUNCATED – payload was cut at [MAX_PAYLOAD_BYTES]; use Wi-Fi API    |
 *
 * ## Usage
 * - **Sender** calls [encode] once with the full payload and the negotiated MTU.
 * - **Receiver** calls [decode] on each incoming frame and accumulates payloads
 *   until [isLast] is true, then concatenates them in sequence order.
 */
object BleChunkProtocol {

    /** Byte length of the binary frame header. */
    const val HEADER_SIZE = 5 // 1 (flags) + 2 (seq) + 2 (total)

    /** Hard cap on outgoing response payloads to avoid exhausting device memory. */
    const val MAX_PAYLOAD_BYTES = 50_000

    private const val FLAG_FIRST: Int = 0x01
    private const val FLAG_LAST: Int = 0x02
    private const val FLAG_MORE: Int = 0x04
    private const val FLAG_TRUNCATED: Int = 0x08

    // ── Encoding ─────────────────────────────────────────────────────────────

    /**
     * Splits [payload] into a list of frames sized to fit within [mtu] bytes.
     *
     * Each frame is at most `mtu` bytes total (header + payload). If [payload]
     * exceeds [MAX_PAYLOAD_BYTES] it is silently truncated and the TRUNCATED flag
     * is set on the last frame.
     *
     * @throws IllegalArgumentException if [mtu] is too small to hold even one byte of payload.
     */
    fun encode(payload: ByteArray, mtu: Int): List<ByteArray> {
        val usable = mtu - HEADER_SIZE
        require(usable > 0) { "MTU $mtu is too small (minimum ${HEADER_SIZE + 1})" }

        val truncated = payload.size > MAX_PAYLOAD_BYTES
        val data = if (truncated) payload.copyOf(MAX_PAYLOAD_BYTES) else payload
        val chunks = data.toChunks(usable)
        val total = chunks.size

        return chunks.mapIndexed { idx, chunk ->
            val isFirst = idx == 0
            val isLast = idx == total - 1
            var flags = 0
            if (isFirst) flags = flags or FLAG_FIRST
            if (isLast) {
                flags = flags or FLAG_LAST
                if (truncated) flags = flags or FLAG_TRUNCATED
            } else {
                flags = flags or FLAG_MORE
            }
            buildFrame(flags.toByte(), idx, total, chunk)
        }
    }

    // ── Decoding ─────────────────────────────────────────────────────────────

    /**
     * Parses a raw [frame] into its header fields and payload.
     *
     * @throws IllegalArgumentException if [frame] is shorter than [HEADER_SIZE].
     */
    fun decode(frame: ByteArray): DecodedFrame {
        require(frame.size >= HEADER_SIZE) {
            "Frame too short: ${frame.size} bytes (minimum $HEADER_SIZE)"
        }
        val flags = frame[0].toInt() and 0xFF
        val seq = ((frame[1].toInt() and 0xFF) shl 8) or (frame[2].toInt() and 0xFF)
        val total = ((frame[3].toInt() and 0xFF) shl 8) or (frame[4].toInt() and 0xFF)
        val payload = frame.copyOfRange(HEADER_SIZE, frame.size)
        return DecodedFrame(flags, seq, total, payload)
    }

    fun isLast(flags: Int): Boolean = flags and FLAG_LAST != 0
    fun isTruncated(flags: Int): Boolean = flags and FLAG_TRUNCATED != 0

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildFrame(flags: Byte, seq: Int, total: Int, payload: ByteArray): ByteArray {
        val frame = ByteArray(HEADER_SIZE + payload.size)
        frame[0] = flags
        frame[1] = (seq ushr 8).toByte()
        frame[2] = (seq and 0xFF).toByte()
        frame[3] = (total ushr 8).toByte()
        frame[4] = (total and 0xFF).toByte()
        payload.copyInto(frame, HEADER_SIZE)
        return frame
    }

    private fun ByteArray.toChunks(size: Int): List<ByteArray> {
        if (isEmpty()) return emptyList()
        val result = mutableListOf<ByteArray>()
        var offset = 0
        while (offset < this.size) {
            val end = minOf(offset + size, this.size)
            result.add(copyOfRange(offset, end))
            offset = end
        }
        return result
    }

    // ── Data classes ──────────────────────────────────────────────────────────

    data class DecodedFrame(
        val flags: Int,
        /** 0-based index of this frame within the transfer. */
        val seq: Int,
        /** Total number of frames in the transfer (0 if unknown). */
        val total: Int,
        val payload: ByteArray,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is DecodedFrame) return false
            return flags == other.flags && seq == other.seq &&
                    total == other.total && payload.contentEquals(other.payload)
        }

        override fun hashCode(): Int {
            var result = flags
            result = 31 * result + seq
            result = 31 * result + total
            result = 31 * result + payload.contentHashCode()
            return result
        }
    }
}
