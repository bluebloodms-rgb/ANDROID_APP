package com.dronegcs.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CommandTest {

    @Test
    fun `start command without pid encodes correctly`() {
        assertEquals("START:TRUE", Command.Start(null).toStatustextString())
    }

    @Test
    fun `start command with pid values encodes correctly`() {
        assertEquals("START:TRUE,1.0,0.1,0.05", Command.Start("1.0,0.1,0.05").toStatustextString())
    }

    @Test
    fun `cancel command encodes correctly`() {
        assertEquals("CANCEL:TRUE,Notcare:TRUE", Command.Cancel.toStatustextString())
    }

    @Test
    fun `set mode commands encode correctly`() {
        assertEquals("MANUAL", Command.SetMode(Command.SetMode.Mode.MANUAL).toStatustextString())
        assertEquals("AUTOMAT", Command.SetMode(Command.SetMode.Mode.AUTO).toStatustextString())
    }

    @Test
    fun `set speed encodes with one decimal`() {
        assertEquals("Speed 19.0 → Pitch 19.0", Command.SetSpeed(19f).toStatustextString())
    }

    @Test
    fun `set class encodes target value`() {
        val cmd = Command.SetClass(Command.SetClass.TargetClass.CAR)
        assertEquals("CLASS:2,Notcare:2", cmd.toStatustextString())
    }

    @Test
    fun `target class from int maps known values`() {
        assertEquals(Command.SetClass.TargetClass.BALLOON, Command.SetClass.TargetClass.fromInt(0))
        assertEquals(Command.SetClass.TargetClass.PERSON, Command.SetClass.TargetClass.fromInt(1))
        assertEquals(Command.SetClass.TargetClass.CAR, Command.SetClass.TargetClass.fromInt(2))
        assertEquals(Command.SetClass.TargetClass.DRONE, Command.SetClass.TargetClass.fromInt(3))
    }

    @Test
    fun `target class from int falls back to person for unknown`() {
        assertEquals(Command.SetClass.TargetClass.PERSON, Command.SetClass.TargetClass.fromInt(99))
    }

    @Test
    fun `zoom and pitch encode with one decimal`() {
        assertEquals("Zoom:2.5", Command.SetZoom(2.5f).toStatustextString())
        assertEquals("Pitch:-12.0", Command.SetPitch(-12f).toStatustextString())
    }

    @Test
    fun `send position encodes coordinates`() {
        assertEquals("Pos:120,-45", Command.SendPosition(120, -45).toStatustextString())
    }

    @Test
    fun `zoom out of range throws`() {
        assertThrows(IllegalArgumentException::class.java) { Command.SetZoom(0.5f) }
        assertThrows(IllegalArgumentException::class.java) { Command.SetZoom(11f) }
    }

    @Test
    fun `pitch out of range throws`() {
        assertThrows(IllegalArgumentException::class.java) { Command.SetPitch(91f) }
        assertThrows(IllegalArgumentException::class.java) { Command.SetPitch(-91f) }
    }
}
