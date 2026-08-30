package com.dronegcs.app.domain.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class RealFrameTest {
    @Test
    fun `parses real HEARTBEAT frame from flight controller`() {
        val frame = "FD090000CD01010000000200000002035103030D8E".chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val msg = MavlinkProtocol.parseMavlinkMessage(frame)
        assertNotNull("real HEARTBEAT frame must parse", msg)
        assertEquals(0, msg!!.msgId)
    }
}
