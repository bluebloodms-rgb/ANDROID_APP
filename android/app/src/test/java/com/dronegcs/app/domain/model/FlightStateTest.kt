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
    fun `gps fix type name mapping - updated for MAVLink enum`() {
        assertEquals("NO GPS", FlightState(gpsFixType = 0).gpsFixTypeName)
        assertEquals("NO FIX", FlightState(gpsFixType = 1).gpsFixTypeName)
        assertEquals("2D FIX", FlightState(gpsFixType = 2).gpsFixTypeName)
        assertEquals("3D FIX", FlightState(gpsFixType = 3).gpsFixTypeName)
        assertEquals("DGPS", FlightState(gpsFixType = 4).gpsFixTypeName)
        assertEquals("RTK FIXED", FlightState(gpsFixType = 5).gpsFixTypeName)
        assertEquals("RTK FLOAT", FlightState(gpsFixType = 6).gpsFixTypeName)
        assertEquals("UNKNOWN(99)", FlightState(gpsFixType = 99).gpsFixTypeName)
        assertNull(FlightState().gpsFixTypeName)
    }

    @Test
    fun `hasGpsFix requires 3D fix or better (>= 3)`() {
        assertFalse(FlightState(gpsFixType = 0).hasGpsFix)
        assertFalse(FlightState(gpsFixType = 1).hasGpsFix)
        assertFalse(FlightState(gpsFixType = 2).hasGpsFix)
        assertTrue(FlightState(gpsFixType = 3).hasGpsFix)
        assertTrue(FlightState(gpsFixType = 4).hasGpsFix)
        assertTrue(FlightState(gpsFixType = 5).hasGpsFix)
        assertTrue(FlightState(gpsFixType = 6).hasGpsFix)
        assertFalse(FlightState().hasGpsFix)
    }

    @Test
    fun `hasValidPosition requires fix >= 3 AND non-zero lat/lon`() {
        assertFalse(FlightState(gpsFixType = 3, latitude = 0.0, longitude = 0.0).hasValidPosition)
        assertFalse(FlightState(gpsFixType = 3, latitude = 37.123, longitude = 0.0).hasValidPosition)
        assertFalse(FlightState(gpsFixType = 3, latitude = 0.0, longitude = -122.456).hasValidPosition)
        assertTrue(FlightState(gpsFixType = 3, latitude = 37.123, longitude = -122.456).hasValidPosition)
        assertTrue(FlightState(gpsFixType = 4, latitude = 37.123, longitude = -122.456).hasValidPosition)
        assertFalse(FlightState(gpsFixType = 2, latitude = 37.123, longitude = -122.456).hasValidPosition)
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
    fun `emergency stop flag updates with TTL`() {
        assertFalse(FlightState().emergencyStop)
        val state = FlightState().updateEmergencyStop(true, System.currentTimeMillis() + 10_000)
        assertTrue(state.emergencyStop)
        assertTrue(state.emergencyStopExpiresAt > System.currentTimeMillis())
        val cleared = state.updateEmergencyStop(false, 0)
        assertFalse(cleared.emergencyStop)
        assertEquals(0, cleared.emergencyStopExpiresAt)
    }

    @Test
    fun `low battery warning with hysteresis`() {
        // Below threshold (30.0V) triggers warning
        val state1 = FlightState(battery = 29.9f).updateTelemetry(battery = 29.9f)
        assertTrue(state1.isLowBattery)
        
        // At threshold
        val state2 = FlightState(battery = 30.0f).updateTelemetry(battery = 30.0f)
        assertTrue(state2.isLowBattery)
        
        // Above threshold but below hysteresis (30.5V) - still warning if was already warning
        val warningState = FlightState(battery = 29.5f, lowBatteryWarning = true).updateTelemetry(battery = 30.2f)
        assertTrue(warningState.lowBatteryWarning, "Should still warn due to hysteresis")
        
        // Above hysteresis (30.5V) clears warning
        val clearState = FlightState(battery = 29.5f, lowBatteryWarning = true).updateTelemetry(battery = 30.6f)
        assertFalse(clearState.lowBatteryWarning, "Should clear warning above hysteresis")
        
        // Fresh battery OK state
        val state3 = FlightState(battery = 30.6f).updateTelemetry(battery = 30.6f)
        assertFalse(state3.lowBatteryWarning)
        assertTrue(state3.isBatteryOk)
        
        // Null battery should not trigger warning
        assertFalse(FlightState().lowBatteryWarning)
    }

    @Test
    fun `critical battery tier`() {
        assertTrue(FlightState(battery = 28.9f).isCriticalBattery)
        assertTrue(FlightState(battery = 29.0f).isCriticalBattery)
        assertFalse(FlightState(battery = 29.1f).isCriticalBattery)
        assertFalse(FlightState(battery = 32.0f).isCriticalBattery)
    }

    @Test
    fun `isFinite validation for all float fields`() {
        val state = FlightState()
            .updateTelemetry(battery = Float.NaN, altitude = Float.NaN, hdop = Float.POSITIVE_INFINITY)
        
        assertNull(state.battery)
        assertNull(state.altitude)
        assertNull(state.hdop)
    }

    @Test
    fun `freshness timestamps updated`() {
        val before = System.currentTimeMillis()
        val state = FlightState().updateTelemetry(battery = 31.0f, altitude = 10.0f, latitude = 37.123, longitude = -122.456)
        val after = System.currentTimeMillis()
        
        assertTrue(state.lastBatteryRx >= before && state.lastBatteryRx <= after)
        assertTrue(state.lastAltitudeRx >= before && state.lastAltitudeRx <= after)
        assertTrue(state.lastGpsRx >= before && state.lastGpsRx <= after)
    }

    @Test
    fun `isFieldFresh detects staleness`() {
        val freshState = FlightState(lastBatteryRx = System.currentTimeMillis())
        assertTrue(freshState.isFieldFresh(freshState.lastBatteryRx))
        
        val staleState = FlightState(lastBatteryRx = System.currentTimeMillis() - 2000)
        assertFalse(staleState.isFieldFresh(staleState.lastBatteryRx))
    }

    @Test
    fun `lidar distance update`() {
        val state = FlightState().updateTelemetry(lidarDistance = 12.5f)
        assertEquals(12.5f, state.lidarDistance!!, 0.001f)
    }

    @Test
    fun `lidar distance NaN ignored`() {
        val state = FlightState(lidarDistance = 10.0f).updateTelemetry(lidarDistance = Float.NaN)
        assertEquals(10.0f, state.lidarDistance!!, 0.001f)
    }

    @Test
    fun `lidar freshness timestamp updated`() {
        val before = System.currentTimeMillis()
        val state = FlightState().updateTelemetry(lidarDistance = 5.0f)
        val after = System.currentTimeMillis()
        
        assertTrue(state.lastLidarRx >= before && state.lastLidarRx <= after)
    }

    @Test
    fun `lidar field fresh detection`() {
        val freshState = FlightState(lastLidarRx = System.currentTimeMillis())
        assertTrue(freshState.isFieldFresh(freshState.lastLidarRx))
        
        val staleState = FlightState(lastLidarRx = System.currentTimeMillis() - 2000)
        assertFalse(staleState.isFieldFresh(staleState.lastLidarRx))
    }
}
