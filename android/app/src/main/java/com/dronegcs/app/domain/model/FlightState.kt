package com.dronegcs.app.domain.model

/** Health of the on-board command/tracking responder (MAVLink sys=10). */
enum class ResponderStatus {
    /** State STATUSTEXTs are arriving. */
    ONLINE,

    /** No state STATUSTEXT for longer than the health threshold. */
    OFFLINE,

    /** Not enough time has elapsed since connecting to decide yet. */
    UNKNOWN
}

/**
 * Immutable flight state data class representing the current state of the drone
 * Corresponds to FlightState class in the original Python code
 */
data class FlightState(
    // Connection state
    val connectionState: ConnectionState = ConnectionState.Disconnected(),
    val initialized: Boolean = false,

    // Operation state from STATUSTEXT
    val op: Int = 1,        // 1 = Ready, 2 = In operation
    val st: Int = 1,        // 1 = First, 2 = Track
    val md: Int = 1,        // 1 = Manual, 2 = Automatic
    val cls: Int = 0,       // 0 = Person, 2 = Car
    val spd: Float = 19f,   // m/s
    val pitch: Float? = null,
    val can: Int = 0,       // 0 = inactive, 1 = cancelled/active
    val zoom: Float? = null,

    // Mouse/tap position
    val mouseX: Float? = null,
    val mouseY: Float? = null,

    // Telemetry for overlays
    val battery: Float? = null,
    val altitude: Float? = null,
    val hdop: Float? = null,
    val mode: String? = null,
    val satellites: Int? = null,
    val gpsFixType: Int? = null, // GPS_RAW_INT fix_type: 0=NO_GPS, 1=NO_FIX, 2=2D, 3=3D, 4=DGPS, 5=RTK_FIXED, 6=RTK_FLOAT
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val lidarDistance: Float? = null, // DISTANCE_SENSOR current_distance in meters

    // Heartbeat tracking
    val lastHeartbeat: Long = 0,
    val isArmed: Boolean = false,
    val fcMode: Int? = null,   // ArduPilot custom_mode from HEARTBEAT

    // RX health: last message from the vehicle (sys=1) and last state
    // STATUSTEXT from the command/tracking responder (sys=10).
    val lastVehicleRx: Long = 0,
    val lastResponderRx: Long = 0,
    val telemetryStale: Boolean = false,
    val responderStatus: ResponderStatus = ResponderStatus.UNKNOWN,
    val crcMismatchCount: Int = 0,
    val linkQuality: Int = 100, // 0-100% link quality estimate
    val lastSeq: Int? = null, // For sequence gap detection

    // Command tracking
    val lastCommand: Int = 0,
    val lastCommandResult: Int = 0,
    val emergencyStop: Boolean = false,
    val emergencyStopExpiresAt: Long = 0, // TTL for emergency stop latch
    val lowBatteryWarning: Boolean = false,
    val lowBatteryCritical: Boolean = false,
    // Freshness timestamps for per-field staleness detection
    val lastBatteryRx: Long = 0,
    val lastAltitudeRx: Long = 0,
    val lastHeadingRx: Long = 0,
    val lastGpsRx: Long = 0,
    val lastLidarRx: Long = 0
) {
    /**
     * Update state from a STATUSTEXT message string
     * Format: "Op:1,St:1,Md:1,Cls:0,Spd:19.0,Zoom:1.0,Pitch:0.0,Can:0"
     * Also accepts the direct command echo "CLASS:1,Notcare:1".
     */
    fun updateFromMessage(text: String): FlightState {
        val parts = text.replace(" ", "").split(",")
        var newOp = op
        var newSt = st
        var newMd = md
        var newCls = cls
        var newSpd = spd
        var newPitch = pitch
        var newCan = can
        var newZoom = zoom

        for (p in parts) {
            if (!p.contains(":")) continue
            val (k, v) = p.split(":")
            when (k) {
                "Op" -> newOp = v.toIntOrNull() ?: newOp
                "St" -> newSt = v.toIntOrNull() ?: newSt
                "Md" -> newMd = v.toIntOrNull() ?: newMd
                "Cls" -> newCls = v.toIntOrNull() ?: newCls
                // Direct echo of the CLASS:n command from the responder.
                "CLASS" -> newCls = v.toIntOrNull() ?: newCls
                "Spd" -> newSpd = v.toFloatOrNull() ?: newSpd
                "Zoom" -> newZoom = v.toFloatOrNull() ?: newZoom
                "Pitch" -> newPitch = v.toFloatOrNull() ?: newPitch
                "Can" -> newCan = v.toIntOrNull() ?: newCan
            }
        }

        return copy(
            op = newOp,
            st = newSt,
            md = newMd,
            cls = newCls,
            spd = newSpd,
            pitch = newPitch,
            can = newCan,
            zoom = newZoom,
            initialized = true
        )
    }

    fun markVehicleRx(now: Long = System.currentTimeMillis()): FlightState =
        copy(lastVehicleRx = now)

    fun markResponderRx(now: Long = System.currentTimeMillis()): FlightState =
        copy(lastResponderRx = now, responderStatus = ResponderStatus.ONLINE)

    fun updateTelemetry(
        battery: Float? = null,
        altitude: Float? = null,
        hdop: Float? = null,
        mode: String? = null,
        satellites: Int? = null,
        gpsFixType: Int? = null,
        latitude: Double? = null,
        longitude: Double? = null,
        lidarDistance: Float? = null
    ): FlightState {
        // Guard against NaN altitude values from faulty sensors
        val safeAltitude = altitude?.let { alt ->
            if (alt.isNaN()) null else alt
        }
        
        // Central isFinite validation for all float fields
        val safeBattery = battery?.let { bat ->
            if (!bat.isFinite()) null else bat
        }
        val safeHdop = hdop?.let { h ->
            if (!h.isFinite()) null else h
        }
        val safeLatitude = latitude?.let { lat ->
            if (!lat.isFinite()) null else lat
        }
        val safeLongitude = longitude?.let { lon ->
            if (!lon.isFinite()) null else lon
        }
        val safeLidarDistance = lidarDistance?.let { dist ->
            if (!dist.isFinite()) null else dist
        }
        
        val now = System.currentTimeMillis()
        return copy(
            battery = safeBattery ?: this.battery,
            altitude = safeAltitude ?: this.altitude,
            hdop = safeHdop ?: this.hdop,
            mode = mode ?: this.mode,
            satellites = satellites ?: this.satellites,
            gpsFixType = gpsFixType ?: this.gpsFixType,
            latitude = safeLatitude ?: this.latitude,
            longitude = safeLongitude ?: this.longitude,
            lidarDistance = safeLidarDistance ?: this.lidarDistance,
            lastBatteryRx = safeBattery?.let { now } ?: this.lastBatteryRx,
            lastAltitudeRx = safeAltitude?.let { now } ?: this.lastAltitudeRx,
            lastGpsRx = safeLatitude?.let { now } ?: this.lastGpsRx,
            lastLidarRx = safeLidarDistance?.let { now } ?: this.lastLidarRx
        )
    }

    fun updateHeartbeat(armed: Boolean? = null, customMode: Int? = null): FlightState {
        return copy(
            lastHeartbeat = System.currentTimeMillis(),
            isArmed = armed ?: isArmed,
            fcMode = customMode ?: fcMode
        )
    }

    fun isHeartbeatTimeout(): Boolean {
        return (System.currentTimeMillis() - lastHeartbeat) > 5000
    }

    fun updateFromCommandAck(command: Int, result: Int): FlightState {
        return copy(
            lastCommand = command,
            lastCommandResult = result
        )
    }

    fun updateEmergencyStop(active: Boolean, expiresAt: Long = 0): FlightState {
        return copy(
            emergencyStop = active,
            emergencyStopExpiresAt = if (active) expiresAt else 0
        )
    }

    // Convenience getters for UI
    /** ArduPilot Copter custom_mode -> flight mode name (Guided, AltHold, ...). */
    val fcModeName: String?
        get() = fcMode?.let { m ->
            when (m) {
                0 -> "STABILIZE"; 1 -> "ACRO"; 2 -> "ALT_HOLD"; 3 -> "AUTO"
                4 -> "GUIDED"; 5 -> "LOITER"; 6 -> "RTL"; 7 -> "CIRCLE"
                8 -> "POSITION"; 9 -> "LAND"; 10 -> "OF_LOITER"; 11 -> "DRIFT"
                12 -> "SPORT"; 13 -> "FLIP"; 14 -> "AUTOTUNE"; 15 -> "POSHOLD"
                16 -> "BRAKE"; 17 -> "THROW"; 18 -> "AVOID_ADSB"; 19 -> "GUIDED_NOGPS"
                // This vehicle has no RTL: Smart RTL (20) / Auto RTL (26) / RTL (6)
                // are all displayed as GUIDED, matching the Windows app.
                6, 20, 26 -> "GUIDED"
                21 -> "FLOWHOLD"; 22 -> "FOLLOW"; 23 -> "ZIGZAG"
                24 -> "SYSTEMID"; 25 -> "AUTOROTATE"
                else -> "MODE $m"
            }
        }

    val modeName: String
        get() = when (md) {
            // Bottom dock ALWAYS shows Manual/Auto (from STATUSTEXT Md), exactly
            // like the Windows app. The real ArduPilot mode (Guided, ...) is only
            // shown in the TopBar via fcModeName.
            2 -> "AUTO"
            else -> "MANUAL"
        }

    val className: String
        get() = when (cls) {
            // Windows class numbers: Balloon = 0, Person = 1, Car = 2, Drone = 3
            0 -> "Balloon"
            1 -> "Person"
            2 -> "Car"
            3 -> "Drone"
            else -> "Unknown"
        }

    val lastCommandResultName: String
        get() = when (lastCommandResult) {
            0 -> "ACCEPTED"
            1 -> "TEMPORARILY_REJECTED"
            2 -> "DENIED"
            3 -> "UNSUPPORTED"
            4 -> "FAILED"
            5 -> "IN_PROGRESS"
            6 -> "CANCELLED"
            7 -> "AUTH_DENIED"
            else -> "UNKNOWN($lastCommandResult)"
        }

    /** GPS fix type: 0=NO_GPS, 1=NO_FIX, 2=2D, 3=3D, 4=DGPS, 5=RTK_FIXED, 6=RTK_FLOAT */
    val gpsFixTypeName: String?
        get() = gpsFixType?.let { ft ->
            when (ft) {
                0 -> "NO GPS"
                1 -> "NO FIX"
                2 -> "2D FIX"
                3 -> "3D FIX"
                4 -> "DGPS"
                5 -> "RTK FIXED"
                6 -> "RTK FLOAT"
                else -> "UNKNOWN($ft)"
            }
        }

    /** True when GPS has a usable 3D fix (fix_type >= 3) */
    val hasGpsFix: Boolean
        get() = gpsFixType != null && gpsFixType!! >= 3

    /** True when GPS has valid position (fix >= 3 AND lat/lon != 0) */
    val hasValidPosition: Boolean
        get() = hasGpsFix && (latitude != 0.0 && longitude != 0.0)

    /** Link quality as display string */
    val linkQualityName: String
        get() = when {
            linkQuality >= 90 -> "EXCELLENT"
            linkQuality >= 70 -> "GOOD"
            linkQuality >= 50 -> "FAIR"
            linkQuality >= 30 -> "POOR"
            else -> "CRITICAL"
        }

    /** Low battery warning threshold */
    companion object {
        const val LOW_BATTERY_THRESHOLD_V = 30.0f
        const val LOW_BATTERY_CRITICAL_V = 29.0f
        const val LOW_BATTERY_HYSTERESIS_V = 0.5f // Clear above threshold + hysteresis
    }

    /** True when battery voltage is below threshold (computed from battery value) */
    val isLowBattery: Boolean
        get() = battery != null && battery!! < LOW_BATTERY_THRESHOLD_V

    /** True when battery voltage is critically low - immediate action required */
    val isCriticalBattery: Boolean
        get() = battery != null && battery!! < LOW_BATTERY_CRITICAL_V

    /** True when battery is OK (above threshold + hysteresis) */
    val isBatteryOk: Boolean
        get() = battery != null && battery!! >= (LOW_BATTERY_THRESHOLD_V + LOW_BATTERY_HYSTERESIS_V)

    /** True when field is fresh (updated within last second) */
    fun isFieldFresh(lastRx: Long): Boolean = System.currentTimeMillis() - lastRx < 1000
}