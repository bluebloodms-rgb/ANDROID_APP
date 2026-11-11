package com.dronegcs.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FlightStateTest {

    @Test
    fun `parses responder state string`() {
        val state = FlightState().updateFromMessage(
            "Op:2,St:1,Md:2,Cls:1,Spd:22.0,Zoom:3.0,Pitch:-12.0,Can:1"
        )

        assertEquals(2, state.op)
        assertEquals(1, state.st)
        assertEquals(2, state.md)
        assertEquals(1, state.cls)
        assertEquals(22.0f, state.spd, 0.001f)
        assertEquals(3.0f, state.zoom!!, 0.001f)
        assertEquals(-12.0f, state.pitch!!, 0.001f)
        assertEquals(1, state.can)
    }

    @Test
    fun `parses direct CLASS echo`() {
        val state = FlightState().updateFromMessage("CLASS:2,Notcare:2")

        assertEquals(2, state.cls)
    }

    @Test
    fun `keeps previous values when a field is absent`() {
        val state = FlightState(op = 2, spd = 19f)
            .updateFromMessage("Cls:3")

        assertEquals(2, state.op)
        assertEquals(19f, state.spd, 0.001f)
        assertEquals(3, state.cls)
    }

    @Test
    fun `mark responders sets online status`() {
        val state = FlightState().markResponderRx(1234L)

        assertEquals(1234L, state.lastResponderRx)
        assertEquals(ResponderStatus.ONLINE, state.responderStatus)
    }

    @Test
    fun `unparsable zoom keeps previous value`() {
        val state = FlightState(zoom = 4f).updateFromMessage("Zoom:nope")
        assertEquals(4f, state.zoom!!, 0.001f)
    }

    @Test
    fun `ignores NaN altitude in updateTelemetry`() {
        val state = FlightState(altitude = 10f).updateTelemetry(altitude = Float.NaN)
        assertEquals(10f, state.altitude!!, 0.001f, "Previous altitude should be preserved when NaN is provided")
    }

    @Test
    fun `ignores NaN altitude when no previous value`() {
        val state = FlightState().updateTelemetry(altitude = Float.NaN)
        assertNull(state.altitude, "Altitude should remain null when NaN provided with no previous value")
    }

    @Test
    fun `gps fix type name mapping`() {
        assertEquals("NO GPS", FlightState(gpsFixType = 0).gpsFixTypeName)
        assertEquals("2D FIX", FlightState(gpsFixType = 1).gpsFixTypeName)
        assertEquals("3D FIX", FlightState(gpsFixType = 2).gpsFixTypeName)
        assertEquals("DGPS", FlightState(gpsFixType = 3).gpsFixTypeName)
        assertEquals("RTK FIXED", FlightState(gpsFixType = 4).gpsFixTypeName)
        assertEquals("RTK FLOAT", FlightState(gpsFixType = 5).gpsFixTypeName)
        assertEquals("UNKNOWN(99)", FlightState(gpsFixType = 99).gpsFixTypeName)
        assertNull(FlightState().gpsFixTypeName)
    }

    @Test
    fun `hasGpsFix requires 3D fix or better`() {
        assertFalse(FlightState(gpsFixType = 0).hasGpsFix)
        assertFalse(FlightState(gpsFixType = 1).hasGpsFix)
        assertTrue(FlightState(gpsFixType = 2).hasGpsFix)
        assertTrue(FlightState(gpsFixType = 3).hasGpsFix)
        assertTrue(FlightState(gpsFixType = 4).hasGpsFix)
        assertTrue(FlightState(gpsFixType = 5).hasGpsFix)
        assertFalse(FlightState().hasGpsFix)
    }

    @Test
    fun `link quality name mapping`() {
        assertEquals("EXCELLENT", FlightState(linkQuality = 100).linkQualityName)
        assertEquals("EXCELLENT", FlightState(linkQuality = 90).linkQualityName)
        assertEquals("GOOD", FlightState(linkQuality = 85).linkQualityName)
        assertEquals("GOOD", FlightState(linkQuality = 70).linkQualityName)
        assertEquals("FAIR", FlightState(linkQuality = 65).linkQualityName)
        assertEquals("FAIR", FlightState(linkQuality = 50).linkQualityName)
        assertEquals("POOR", FlightState(linkQuality = 45).linkQualityName)
        assertEquals("POOR", FlightState(linkQuality = 30).linkQualityName)
        assertEquals("CRITICAL", FlightState(linkQuality = 25).linkQualityName)
        assertEquals("CRITICAL", FlightState(linkQuality = 0).linkQualityName)
    }

    @Test
    fun `emergency stop flag updates`() {
        assertFalse(FlightState().emergencyStop)
        val state = FlightState().updateEmergencyStop(true)
        assertTrue(state.emergencyStop)
        val cleared = state.updateEmergencyStop(false)
        assertFalse(cleared.emergencyStop)
    }

    @Test
    fun `low battery warning threshold`() {
        // Below threshold should trigger warning
        assertTrue(FlightState(battery = 29.9f).lowBatteryWarning)
        assertTrue(FlightState(battery = 30.0f).lowBatteryWarning)
        
        // Above threshold should not trigger warning
        assertFalse(FlightState(battery = 30.1f).lowBatteryWarning)
        assertFalse(FlightState(battery = 32.0f).lowBatteryWarning)
        
        // Null battery should not trigger warning
        assertFalse(FlightState().lowBatteryWarning)
    }
}
