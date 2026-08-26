package com.dronegcs.app.domain.protocol

import com.dronegcs.app.domain.model.Command
import com.dronegcs.app.domain.model.FlightState
import timber.log.Timber

/**
 * MAVLink protocol handler for encoding/decoding STATUSTEXT messages
 * Implements the exact protocol from the original Python code
 */
object MavlinkProtocol {

    // MAVLink v2 constants
    const val MAVLINK_STX = 0xFD
    const val MSG_ID_STATUSTEXT = 253
    private const val MAV_SEVERITY_INFO = 6
    private const val MAX_STATUSTEXT_LENGTH = 50

    // SPP UUID for Bluetooth Classic
    const val SPP_UUID = "00001101-0000-1000-8000-00805F9B34FB"

    /**
     * Encode a STATUSTEXT message as MAVLink v2 frame
     * Returns byte array ready to send over Bluetooth SPP
     */
    fun encodeStatustext(text: String, severity: Int = MAV_SEVERITY_INFO): ByteArray {
        // Truncate to max length, handling UTF-8 properly
        val truncatedText = truncateUtf8(text, MAX_STATUSTEXT_LENGTH)
        val textBytes = truncatedText.toByteArray(charset = java.nio.charset.StandardCharsets.UTF_8)

        // MAVLink v2 frame: STX + len + seq + sysid + compid + msgid + payload + checksum
        // For STATUSTEXT: payload = severity (1 byte) + text (up to 50 bytes) + padding
        val payload = ByteArray(1 + MAX_STATUSTEXT_LENGTH)
        payload[0] = severity.toByte()
        textBytes.copyInto(payload, 1, 0, textBytes.size.coerceAtMost(MAX_STATUSTEXT_LENGTH))

        // Build frame
        val seq = nextSequence()
        val sysId = 1  // Ground station
        val compId = 1 // Mission planner

        // Calculate message length
        val msgLen = payload.size

        // Start building frame
        val frame = ByteArray(10 + msgLen + 2) // header(6) + payload + checksum(2) + signature(0 for v2 without signing)
        var idx = 0

        frame[idx++] = MAVLINK_STX.toByte()
        frame[idx++] = (msgLen and 0xFF).toByte()
        frame[idx++] = ((msgLen shr 8) and 0xFF).toByte()
        frame[idx++] = seq.toByte()
        frame[idx++] = sysId.toByte()
        frame[idx++] = compId.toByte()

        // Message ID (24-bit, little endian)
        frame[idx++] = (MSG_ID_STATUSTEXT and 0xFF).toByte()
        frame[idx++] = ((MSG_ID_STATUSTEXT shr 8) and 0xFF).toByte()
        frame[idx++] = ((MSG_ID_STATUSTEXT shr 16) and 0xFF).toByte()

        // Payload
        payload.copyInto(frame, idx)
        idx += payload.size

        // CRC (X.25 checksum)
        val crc = calculateCrc(frame, 0, idx, MSG_ID_STATUSTEXT)
        frame[idx++] = (crc and 0xFF).toByte()
        frame[idx++] = ((crc shr 8) and 0xFF).toByte()

        Timber.d("Encoded STATUSTEXT: $truncatedText (${frame.size} bytes)")
        return frame
    }

    /**
     * Decode STATUSTEXT payload from received MAVLink message
     */
    fun decodeStatustextPayload(payload: ByteArray): String? {
        if (payload.size < 1) return null
        val severity = payload[0].toInt()
        if (severity != MAV_SEVERITY_INFO) return null

        val textBytes = payload.copyOfRange(1, payload.size)
        // Trim trailing nulls
        val endIndex = textBytes.indexOfFirst { it == 0.toByte() }.takeIf { it >= 0 } ?: textBytes.size
        return String(textBytes.copyOfRange(0, endIndex), java.nio.charset.StandardCharsets.UTF_8)
    }

    /**
     * Parse state string from STATUSTEXT into FlightStateUpdate
     */
    fun parseStateString(text: String): FlightStateUpdate {
        val parts = text.replace(" ", "").split(",")
        var op: Int? = null
        var st: Int? = null
        var md: Int? = null
        var cls: Int? = null
        var spd: Float? = null
        var zoom: Float? = null
        var pitch: Float? = null
        var can: Int? = null

        for (p in parts) {
            if (!p.contains(":")) continue
            val (k, v) = p.split(":")
            when (k) {
                "Op" -> op = v.toIntOrNull()
                "St" -> st = v.toIntOrNull()
                "Md" -> md = v.toIntOrNull()
                "Cls" -> cls = v.toIntOrNull()
                "Spd" -> spd = v.toFloatOrNull()
                "Zoom" -> zoom = v.toFloatOrNull()
                "Pitch" -> pitch = v.toFloatOrNull()
                "Can" -> can = v.toIntOrNull()
            }
        }

        return FlightStateUpdate(op, st, md, cls, spd, zoom, pitch, can)
    }

    /**
     * Parse MAVLink message from raw bytes (simplified parser for STATUSTEXT and telemetry)
     * This is a minimal implementation - for full MAVLink parsing use mavlink-kotlin library
     */
    fun parseMavlinkMessage(buffer: ByteArray): MavlinkMessage? {
        // Find STX
        var startIdx = -1
        for (i in 0 until buffer.size - 1) {
            if (buffer[i].toInt() and 0xFF == MAVLINK_STX) {
                startIdx = i
                break
            }
        }

        if (startIdx == -1 || startIdx + 6 > buffer.size) return null

        val len = (buffer[startIdx + 1].toInt() and 0xFF) or ((buffer[startIdx + 2].toInt() and 0xFF) shl 8)
        val totalLen = 6 + len + 2 // header + payload + checksum

        if (startIdx + totalLen > buffer.size) return null

        val seq = buffer[startIdx + 3].toInt() and 0xFF
        val sysId = buffer[startIdx + 4].toInt() and 0xFF
        val compId = buffer[startIdx + 5].toInt() and 0xFF

        val msgId = (buffer[startIdx + 6].toInt() and 0xFF) or
                    ((buffer[startIdx + 7].toInt() and 0xFF) shl 8) or
                    ((buffer[startIdx + 8].toInt() and 0xFF) shl 16)

        val payloadStart = startIdx + 9
        val payload = buffer.copyOfRange(payloadStart, payloadStart + len)

        val crcReceived = (buffer[payloadStart + len].toInt() and 0xFF) or
                         ((buffer[payloadStart + len + 1].toInt() and 0xFF) shl 8)

        val crcCalculated = calculateCrc(buffer, startIdx, payloadStart + len, msgId)

        if (crcReceived != crcCalculated) {
            Timber.w("CRC mismatch for msgId $msgId")
            return null
        }

        return MavlinkMessage(seq, sysId, compId, msgId, payload)
    }

    // --- Private helpers ---

    private var sequenceCounter = 0
    private fun nextSequence(): Int {
        sequenceCounter = (sequenceCounter + 1) % 256
        return sequenceCounter
    }

    private fun truncateUtf8(text: String, maxBytes: Int): String {
        val bytes = text.toByteArray(charset = java.nio.charset.StandardCharsets.UTF_8)
        if (bytes.size <= maxBytes) return text
        return String(bytes.copyOfRange(0, maxBytes), java.nio.charset.StandardCharsets.UTF_8)
    }

    // X.25 CRC (used by MAVLink)
    private fun calculateCrc(buffer: ByteArray, start: Int, endExclusive: Int, msgId: Int): Int {
        var crc = 0xFFFF
        val crcExtra = getCrcExtra(msgId)

        for (i in start until endExclusive) {
            val data = buffer[i].toInt() and 0xFF
            crc = crcXor(crc, data)
        }

        // Add CRC extra
        crc = crcXor(crc, crcExtra and 0xFF)
        crc = crcXor(crc, (crcExtra shr 8) and 0xFF)

        return crc
    }

    private fun crcXor(crc: Int, data: Int): Int {
        var c = crc xor data
        repeat(8) {
            if ((c and 1) != 0) {
                c = (c shr 1) xor 0x8408
            } else {
                c = c shr 1
            }
        }
        return c
    }

    private fun getCrcExtra(msgId: Int): Int {
        // CRC_EXTRA values for common MAVLink messages
        return when (msgId) {
            MSG_ID_STATUSTEXT -> 0x02 // STATUSTEXT
            0 -> 0x31 // HEARTBEAT
            1 -> 0x20 // SYS_STATUS
            24 -> 0x5F // GPS_RAW_INT
            30 -> 0x2A // ATTITUDE
            33 -> 0x17 // GLOBAL_POSITION_INT
            173 -> 0x3E // BATTERY_STATUS
            174 -> 0x01 // RANGEFINDER
            else -> 0
        }
    }
}

/**
 * Parsed MAVLink message
 */
data class MavlinkMessage(
    val seq: Int,
    val sysId: Int,
    val compId: Int,
    val msgId: Int,
    val payload: ByteArray
)

/**
 * Update object for FlightState from STATUSTEXT parsing
 */
data class FlightStateUpdate(
    val op: Int? = null,
    val st: Int? = null,
    val md: Int? = null,
    val cls: Int? = null,
    val spd: Float? = null,
    val zoom: Float? = null,
    val pitch: Float? = null,
    val can: Int? = null
)