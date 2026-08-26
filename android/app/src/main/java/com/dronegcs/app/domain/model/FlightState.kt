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
    val altitude: Float? = null,
    val hdop: Float? = null,
    val mode: String? = null,
    val satellites: Int? = null,

    // Heartbeat tracking
    val lastHeartbeat: Long = 0
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
            initialized = true
        )
    }

    fun updateTelemetry(
        battery: Float? = null,
        altitude: Float? = null,
        hdop: Float? = null,
        mode: String? = null,
        satellites: Int? = null
    ): FlightState {
        return copy(
            battery = battery ?: this.battery,
            altitude = altitude ?: this.altitude,
            hdop = hdop ?: this.hdop,
            mode = mode ?: this.mode,
            satellites = satellites ?: this.satellites
        )
    }

    fun updateHeartbeat(): FlightState {
        return copy(lastHeartbeat = System.currentTimeMillis())
    }

    fun isHeartbeatTimeout(): Boolean {
        return (System.currentTimeMillis() - lastHeartbeat) > 5000
    }

    // Convenience getters for UI
    val modeName: String
        get() = when (md) {
            1 -> "MANUAL"
            2 -> "AUTO"
            else -> mode ?: "---"
        }

    val className: String
        get() = when (cls) {
            0 -> "Person"
            2 -> "Car"
            3 -> "Balloon"
            4 -> "UAV"
            else -> "Unknown"
        }
}