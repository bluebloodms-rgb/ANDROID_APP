package com.dronegcs.app.domain.model

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
    // Barometric altitude (EKF) relative to home — from GLOBAL_POSITION_INT
    val altitude: Float? = null,
    // Height Above Ground Level from the RANGEFINDER sensor (meters)
    val altitudeAgl: Float? = null,
    val hdop: Float? = null,
    val mode: String? = null,
    val satellites: Int? = null,

    // Heartbeat tracking
    val lastHeartbeat: Long = 0,
    // Wall-clock of the last server STATUSTEXT (Op:...,Md:...). Used by the UI
    // to know whether the mode chip has EVER been echoed by the drone.
    val lastStatusMessageMs: Long = 0,
    val isArmed: Boolean = false,
    val fcMode: Int? = null,   // ArduPilot custom_mode from HEARTBEAT

    // Command tracking
    val lastCommand: Int = 0,
    val lastCommandResult: Int = 0
) {
    /**
     * Update state from a STATUSTEXT message string
     * Format: "Op:1,St:1,Md:1,Cls:0,Spd:19.0,Zoom:1.0,Pitch:0.0,Can:0"
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
                "Spd" -> newSpd = v.toFloatOrNull() ?: newSpd
                "Zoom" -> newZoom = v.toFloatOrNull()
                "Pitch" -> newPitch = v.toFloatOrNull()
                "Can" -> newCan = v.toIntOrNull() ?: 0
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
            initialized = true,
            lastStatusMessageMs = System.currentTimeMillis()
        )
    }

    fun updateTelemetry(
        battery: Float? = null,
        altitude: Float? = null,
        altitudeAgl: Float? = null,
        hdop: Float? = null,
        mode: String? = null,
        satellites: Int? = null
    ): FlightState {
        return copy(
            battery = battery ?: this.battery,
            altitude = altitude ?: this.altitude,
            altitudeAgl = altitudeAgl ?: this.altitudeAgl,
            hdop = hdop ?: this.hdop,
            mode = mode ?: this.mode,
            satellites = satellites ?: this.satellites
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
}