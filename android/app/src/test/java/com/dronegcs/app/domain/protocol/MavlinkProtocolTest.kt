package com.dronegcs.app.domain.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MavlinkProtocolTest {

    @Test
    fun `encodeStatustext produces valid frame structure`() {
        val frame = MavlinkProtocol.encodeStatustext("HELLO")

        assertEquals(MavlinkProtocol.MAVLINK_STX.toByte(), frame[0])
        // payload = severity(1) + max text length(50)
        assertEquals(51, frame[1].toInt() and 0xFF)
        // sysId and compId
        assertEquals(1, frame[4].toInt())
        assertEquals(1, frame[5].toInt())
        // msgId little endian 24-bit
        val msgId = (frame[6].toInt() and 0xFF) or
            ((frame[7].toInt() and 0xFF) shl 8) or
            ((frame[8].toInt() and 0xFF) shl 16)
        assertEquals(253, msgId)
        // total size: header(9: STX+len(2)+seq+sysid+compid+msgid(3)) + payload(51) + crc(2) + 1 padding byte
        assertEquals(63, frame.size)
    }

    @Test
    fun `encode then parse roundtrips payload`() {
        val text = "Op:1,St:1,Md:2,Cls:0"
        val frame = MavlinkProtocol.encodeStatustext(text)

        val message = MavlinkProtocol.parseMavlinkMessage(frame)

        assertNotNull(message)
        message!!
        assertEquals(253, message.msgId)
        assertEquals(6, message.payload[0].toInt()) // severity INFO
        val decoded = MavlinkProtocol.decodeStatustextPayload(message.payload)
        assertEquals(text, decoded)
    }

    @Test
    fun `parseMavlinkMessage tolerates leading garbage bytes`() {
        val frame = MavlinkProtocol.encodeStatustext("X")
        val noisy = byteArrayOf(0x00, 0x12, 0x34) + frame

        val message = MavlinkProtocol.parseMavlinkMessage(noisy)

        assertNotNull(message)
        assertEquals(253, message!!.msgId)
    }

    @Test
    fun `parseMavlinkMessage rejects corrupted crc`() {
        val frame = MavlinkProtocol.encodeStatustext("CRC")
        frame[frame.size - 2] = (frame[frame.size - 2] + 1).toByte()

        assertNull(MavlinkProtocol.parseMavlinkMessage(frame))
    }

    @Test
    fun `parseMavlinkMessage returns null for empty or short buffer`() {
        assertNull(MavlinkProtocol.parseMavlinkMessage(ByteArray(0)))
        assertNull(MavlinkProtocol.parseMavlinkMessage(byteArrayOf(0xFD.toByte(), 0x01)))
    }

    @Test
    fun `decodeStatustextPayload trims trailing nulls`() {
        val payload = ByteArray(20)
        payload[0] = 6.toByte()
        "AB".toByteArray().copyInto(payload, 1)

        assertEquals("AB", MavlinkProtocol.decodeStatustextPayload(payload))
    }

    @Test
    fun `decodeStatustextPayload rejects non-info severity`() {
        val payload = ByteArray(10)
        payload[0] = 3

        assertNull(MavlinkProtocol.decodeStatustextPayload(payload))
    }

    @Test
    fun `parseStateString extracts all fields`() {
        val update = MavlinkProtocol.parseStateString(
            "Op:2,St:2,Md:1,Cls:2,Spd:21.5,Zoom:2.0,Pitch:-4.5,Can:1"
        )

        assertEquals(2, update.op)
        assertEquals(2, update.st)
        assertEquals(1, update.md)
        assertEquals(2, update.cls)
        assertEquals(21.5f, update.spd)
        assertEquals(2.0f, update.zoom)
        assertEquals(-4.5f, update.pitch)
        assertEquals(1, update.can)
    }

    @Test
    fun `parseStateString ignores unknown keys and garbage`() {
        val update = MavlinkProtocol.parseStateString("Foo:1, Op:3 , no-colon")

        assertEquals(3, update.op)
        assertNull(update.st)
        assertNull(update.md)
    }

    @Test
    fun `sequence counter wraps at 256`() {
        val sequences = mutableListOf<Int>()
        for (i in 0 until 257) {
            val frame = MavlinkProtocol.encodeStatustext("s")
            sequences.add(frame[3].toInt() and 0xFF)
        }
        // increments wrap modulo 256: sequence #257 equals sequence #1
        assertEquals(sequences[0], sequences[256])
    }
}
