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
    const val MSG_ID_COMMAND_LONG = 76
    const val MSG_ID_COMMAND_ACK = 77
    private const val INCOMPAT_FLAG_SIGNED = 0x01
    private const val FRAME_HEADER_LEN = 10 // STX..msgid(3)
    private const val FRAME_CRC_LEN = 2
    private const val MAV_SEVERITY_INFO = 6
    private const val MAX_STATUSTEXT_LENGTH = 50

    // MAVLink Command IDs
    const val MAV_CMD_COMPONENT_ARM_DISARM = 400
    const val MAV_CMD_NAV_TAKEOFF = 22
    const val MAV_CMD_MISSION_START = 300
    const val MAV_CMD_NAV_RETURN_TO_LAUNCH = 20
    const val MAV_CMD_NAV_LAND = 21

    // GCS identity constants (same as dronekit/MAVLink defaults: 255/190).
    // ArduPilot runs as sysId=1 and IGNORES incoming frames whose sysId equals
    // its own — using 1 here was why phone commands never reached the FC.
    const val GCS_SYSTEM_ID = 255
    const val GCS_COMPONENT_ID = 190

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

        // Standard MAVLink v2 STATUSTEXT payload: severity (1 byte) + char[50]
        val payload = ByteArray(1 + MAX_STATUSTEXT_LENGTH)
        payload[0] = severity.toByte()
        textBytes.copyInto(payload, 1, 0, textBytes.size.coerceAtMost(MAX_STATUSTEXT_LENGTH))

        val seq = nextSequence()
        // GCS identity — must NOT collide with the vehicle's sysId (ArduPilot is
        // sysId=1 and silently drops frames claiming its own sysId).
        // dronekit/MAVLink GCS defaults: source_system=255, source_component=190.
        val sysId = GCS_SYSTEM_ID
        val compId = GCS_COMPONENT_ID

        // MAVLink v2 frame:
        // STX(1) len(1) incompat(1) compat(1) seq(1) sysid(1) compid(1) msgid(3)
        // payload(len) checksum(2)
        val msgLen = payload.size
        val frame = ByteArray(FRAME_HEADER_LEN + msgLen + FRAME_CRC_LEN)
        var idx = 0

        frame[idx++] = MAVLINK_STX.toByte()
        frame[idx++] = (msgLen and 0xFF).toByte()          // single-byte length!
        frame[idx++] = 0                                    // incompat flags (no signing)
        frame[idx++] = 0                                    // compat flags
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

        // CRC-16/X.25 over len..payload + CRC_EXTRA (standard MAVLink CRC)
        val crc = calculateCrc(frame, 1, idx, MSG_ID_STATUSTEXT)
        frame[idx++] = (crc and 0xFF).toByte()
        frame[idx++] = ((crc shr 8) and 0xFF).toByte()

        Timber.d("Encoded STATUSTEXT: $truncatedText (${frame.size} bytes)")
        return frame
    }

    /**
     * Encode a COMMAND_LONG message as MAVLink v2 frame
     * Standard MAVLink command for ARM/DISARM, TAKEOFF, RTL, etc.
     */
    fun encodeCommandLong(
        command: Int,
        param1: Float = 0f,
        param2: Float = 0f,
        param3: Float = 0f,
        param4: Float = 0f,
        param5: Float = 0f,
        param6: Float = 0f,
        param7: Float = 0f,
        targetSystem: Int = 1,
        targetComponent: Int = 1,
        confirmation: Int = 0
    ): ByteArray {
        // COMMAND_LONG payload: target_system(1) target_component(1) command(2) confirmation(1) param1-7(4*7=28)
        // Total: 1+1+2+1+28 = 33 bytes
        val payload = ByteArray(33)
        var idx = 0
        payload[idx++] = targetSystem.toByte()
        payload[idx++] = targetComponent.toByte()
        payload[idx++] = (command and 0xFF).toByte()
        payload[idx++] = ((command shr 8) and 0xFF).toByte()
        payload[idx++] = confirmation.toByte()

        // param1-7 as float (4 bytes each, little endian)
        floatToBytes(param1).copyInto(payload, idx); idx += 4
        floatToBytes(param2).copyInto(payload, idx); idx += 4
        floatToBytes(param3).copyInto(payload, idx); idx += 4
        floatToBytes(param4).copyInto(payload, idx); idx += 4
        floatToBytes(param5).copyInto(payload, idx); idx += 4
        floatToBytes(param6).copyInto(payload, idx); idx += 4
        floatToBytes(param7).copyInto(payload, idx); idx += 4

        val seq = nextSequence()
        val sysId = GCS_SYSTEM_ID
        val compId = GCS_COMPONENT_ID

        val msgLen = payload.size
        val frame = ByteArray(FRAME_HEADER_LEN + msgLen + FRAME_CRC_LEN)
        idx = 0

        frame[idx++] = MAVLINK_STX.toByte()
        frame[idx++] = (msgLen and 0xFF).toByte()
        frame[idx++] = 0
        frame[idx++] = 0
        frame[idx++] = seq.toByte()
        frame[idx++] = sysId.toByte()
        frame[idx++] = compId.toByte()

        frame[idx++] = (MSG_ID_COMMAND_LONG and 0xFF).toByte()
        frame[idx++] = ((MSG_ID_COMMAND_LONG shr 8) and 0xFF).toByte()
        frame[idx++] = ((MSG_ID_COMMAND_LONG shr 16) and 0xFF).toByte()

        payload.copyInto(frame, idx)
        idx += payload.size

        val crc = calculateCrc(frame, 1, idx, MSG_ID_COMMAND_LONG)
        frame[idx++] = (crc and 0xFF).toByte()
        frame[idx++] = ((crc shr 8) and 0xFF).toByte()

        Timber.d("Encoded COMMAND_LONG: cmd=$command params=[$param1,$param2,$param3,$param4,$param5,$param6,$param7] (${frame.size} bytes)")
        return frame
    }

    private fun floatToBytes(value: Float): ByteArray {
        return java.nio.ByteBuffer.allocate(4)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN)
            .putFloat(value)
            .array()
    }

    /**
     * Decode STATUSTEXT payload from received MAVLink message
     */
    fun decodeStatustextPayload(payload: ByteArray): String? {
        if (payload.size < 1) return null
        val severity = payload[0].toInt()
        // Accept ALL severities — the FC replies with WARNING/ERROR texts too and
        // dropping them hid real responses during live testing.

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
        // Never let malformed serial data crash the app
        return try {
            parseMavlinkMessageInternal(buffer)
        } catch (e: Exception) {
            Timber.w(e, "Malformed MAVLink frame discarded")
            null
        }
    }

    private fun parseMavlinkMessageInternal(buffer: ByteArray): MavlinkMessage? {
        // Find STX (0xFD, MAVLink v2)
        var startIdx = -1
        for (i in buffer.indices) {
            if ((buffer[i].toInt() and 0xFF) == MAVLINK_STX) {
                startIdx = i
                break
            }
        }
        if (startIdx == -1) return null

        val remaining = buffer.size - startIdx

        // Standard MAVLink v2 frame:
        // STX(1) len(1) incompat(1) compat(1) seq(1) sysid(1) compid(1) msgid(3) payload(len) crc(2)
        if (remaining < FRAME_HEADER_LEN + FRAME_CRC_LEN) return null

        val payloadLen = buffer[startIdx + 1].toInt() and 0xFF
        val incompatFlags = buffer[startIdx + 2].toInt() and 0xFF

        // Signed payloads are not supported -> skip this frame
        if (incompatFlags and INCOMPAT_FLAG_SIGNED != 0) {
            Timber.d("Skipping signed MAVLink frame")
            return null
        }

        val totalLen = FRAME_HEADER_LEN + payloadLen + FRAME_CRC_LEN
        if (remaining < totalLen) return null // partial frame - caller resyncs on next STX

        val seq = buffer[startIdx + 4].toInt() and 0xFF
        val sysId = buffer[startIdx + 5].toInt() and 0xFF
        val compId = buffer[startIdx + 6].toInt() and 0xFF

        val msgId = (buffer[startIdx + 7].toInt() and 0xFF) or
                    ((buffer[startIdx + 8].toInt() and 0xFF) shl 8) or
                    ((buffer[startIdx + 9].toInt() and 0xFF) shl 16)

        val payloadStart = startIdx + FRAME_HEADER_LEN
        val payload = buffer.copyOfRange(payloadStart, payloadStart + payloadLen)

        val crcReceived = (buffer[payloadStart + payloadLen].toInt() and 0xFF) or
                          ((buffer[payloadStart + payloadLen + 1].toInt() and 0xFF) shl 8)

        // CRC covers len..payload (everything except STX), plus CRC_EXTRA bytes
        val crcCalculated = calculateCrc(buffer, startIdx + 1, payloadStart + payloadLen, msgId)

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

        // CRC_EXTRA is a SINGLE byte (uint8_t) - official mavlink_checksum() adds it once.
        // Accumulating a second (high) byte here silently breaks every real-world frame.
        crc = crcXor(crc, crcExtra and 0xFF)

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

    private fun getCrcExtra(msgId: Int): Int = MavlinkCrcExtra.forMessageId(msgId)
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