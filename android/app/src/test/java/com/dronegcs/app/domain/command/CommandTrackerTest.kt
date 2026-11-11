package com.dronegcs.app.domain.command

import com.dronegcs.app.domain.model.Command
import com.dronegcs.app.domain.model.FlightState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandTrackerTest {

    private var now = 10_000L
    private val tracker = CommandTracker { now }

    private fun state(
        op: Int = 0,
        st: Int = 1,
        md: Int = 2,
        cls: Int = 0,
        spd: Float = 22f,
        zoom: Float = 1f,
        pitch: Float = 0f,
        can: Int = 0
    ) = FlightState(
        op = op,
        st = st,
        md = md,
        cls = cls,
        spd = spd,
        zoom = zoom,
        pitch = pitch,
        can = can
    )

    @Test
    fun `start is executed when Op reaches 2`() {
        val sent = tracker.onCommandSent(Command.Start())
        assertEquals(CommandPhase.SENT, sent.phase)

        now += 900
        val result = tracker.onResponderState(
            "Op:2,St:1,Md:2,Cls:0,Spd:22,Zoom:1,Pitch:0,Can:0",
            state(op = 2)
        )

        assertEquals(CommandPhase.EXECUTED, result?.phase)
        assertEquals("START:TRUE", result?.commandText)
    }

    @Test
    fun `start is not executed when responder keeps reporting Op 0`() {
        tracker.onCommandSent(Command.Start())

        now += 1_000
        assertNull(
            tracker.onResponderState(
                "Op:0,St:1,Md:2,Cls:0,Spd:22,Zoom:1,Pitch:0,Can:0",
                state(op = 0)
            )
        )

        now += 2_100 // 3.1 s total, START timeout is 3 s
        val result = tracker.tick(state(op = 0), responderOnline = true)

        assertEquals(CommandPhase.NOT_EXECUTED, result?.phase)
        assertTrue(result?.detail?.contains("Op:2") == true)
    }

    @Test
    fun `no responder state produces no ack`() {
        tracker.onCommandSent(Command.SetZoom(2f))

        now += 4_100
        val result = tracker.tick(state(), responderOnline = false)

        assertEquals(CommandPhase.NO_ACK, result?.phase)
        assertTrue(result?.detail?.contains("offline") == true)
        assertTrue(result?.detail?.contains("4000ms") == true)
    }

    @Test
    fun `cancel is executed when Op leaves 2`() {
        tracker.onCommandSent(Command.Cancel)

        now += 500
        val result = tracker.onResponderState(
            "Op:0,St:1,Md:2,Cls:0,Spd:22,Zoom:1,Pitch:0,Can:0",
            state(op = 0)
        )

        assertEquals(CommandPhase.EXECUTED, result?.phase)
    }

    @Test
    fun `auto mode is executed when Md reaches 2`() {
        tracker.onCommandSent(Command.SetMode(Command.SetMode.Mode.AUTO))

        now += 500
        val result = tracker.onResponderState(
            "Op:0,St:1,Md:2,Cls:0,Spd:22,Zoom:1,Pitch:0,Can:0",
            state(md = 2)
        )

        assertEquals(CommandPhase.EXECUTED, result?.phase)
    }

    @Test
    fun `class direct echo is accepted as executed`() {
        tracker.onCommandSent(Command.SetClass(Command.SetClass.TargetClass.CAR))

        now += 500
        val result = tracker.onResponderState("CLASS:2,Notcare:2", state(cls = 2))

        assertEquals(CommandPhase.EXECUTED, result?.phase)
    }

    @Test
    fun `zoom matches within tolerance`() {
        tracker.onCommandSent(Command.SetZoom(3f))

        now += 500
        val result = tracker.onResponderState(
            "Op:0,St:1,Md:2,Cls:0,Spd:22,Zoom:3.4,Pitch:0,Can:0",
            state(zoom = 3.4f)
        )

        assertEquals(CommandPhase.EXECUTED, result?.phase)
    }

    @Test
    fun `pitch matches within one degree`() {
        tracker.onCommandSent(Command.SetPitch(-15f))

        now += 500
        val result = tracker.onResponderState(
            "Op:0,St:1,Md:2,Cls:0,Spd:22,Zoom:1,Pitch:-15.8,Can:0",
            state(pitch = -15.8f)
        )

        assertEquals(CommandPhase.EXECUTED, result?.phase)
    }

    @Test
    fun `position is unconfirmed not executed`() {
        tracker.onCommandSent(Command.SendPosition(100, 200))

        now += 2_600
        val result = tracker.tick(state(), responderOnline = true)

        assertEquals(CommandPhase.UNCONFIRMED, result?.phase)
    }

    @Test
    fun `new command supersedes the pending one`() {
        tracker.onCommandSent(Command.Start())
        now += 1_000
        tracker.onCommandSent(Command.SetZoom(2f))

        // The START timeout must not fire after it was superseded.
        now += 2_500
        assertNull(tracker.tick(state(op = 0), responderOnline = true))

        now += 1_600
        val result = tracker.tick(state(), responderOnline = true)
        assertEquals(CommandPhase.NO_ACK, result?.phase)
        assertEquals("Zoom:2.0", result?.commandText)
    }

    @Test
    fun `write failure produces a failed feedback`() {
        val result = tracker.onCommandFailed(Command.Start(), "link not connected")

        assertEquals(CommandPhase.FAILED, result.phase)
        assertEquals("START:TRUE", result.commandText)
        assertTrue(result.isFailure)
    }
}
