package com.dronegcs.app.domain.model

/**
 * Sealed class representing all commands that can be sent to the flight controller
 * All commands are sent via MAVLink STATUSTEXT with severity = 6 (INFO)
 */
sealed interface Command {
    /**
     * Start operation with optional PID values
     * Format: "START:TRUE,{pid_values}" or "START:TRUE"
     */
    data class Start(val pidValues: String? = null) : Command

    /**
     * Cancel operation
     * Format: "CANCEL:TRUE,Notcare:TRUE"
     */
    object Cancel : Command

    /**
     * Set flight mode
     * Format: "MANUAL" (md=1) or "AUTOMAT" (md=2)
     */
    data class SetMode(val mode: Mode) : Command {
        enum class Mode { MANUAL, AUTO }
    }

    /**
     * Set speed
     * Format: "Speed {spd:.1f} → Pitch {spd:.1f}"
     */
    data class SetSpeed(val speed: Float) : Command

    /**
     * Set target class
     * Format: "CLASS:{cls},Notcare:{cls}"
     * Class numbers (matching the Windows app): Balloon = 0, Person = 1, Car = 2, Drone = 3
     */
    data class SetClass(val targetClass: TargetClass) : Command {
        enum class TargetClass(val value: Int) {
            BALLOON(0), PERSON(1), CAR(2), DRONE(3);

            companion object {
                fun fromInt(value: Int): TargetClass = when (value) {
                    0 -> BALLOON
                    1 -> PERSON
                    2 -> CAR
                    3 -> DRONE
                    else -> PERSON
                }
            }
        }
    }

    /**
     * Set zoom level
     * Format: "Zoom:{zoom:.1f}" (1.0 to 10.0)
     */
    data class SetZoom(val zoom: Float) : Command {
        init {
            require(zoom in 1.0f..10.0f) { "Zoom must be between 1.0 and 10.0" }
        }
    }

    /**
     * Set pitch angle
     * Format: "Pitch:{pitch:.1f}" (-90 to 90)
     */
    data class SetPitch(val pitch: Float) : Command {
        init {
            require(pitch in -90.0f..90.0f) { "Pitch must be between -90 and 90" }
        }
    }

    /**
     * Send target position from tap on video
     * Format: "Pos:{x},{y}"
     */
    data class SendPosition(val x: Int, val y: Int) : Command

    /**
     * Convert command to STATUSTEXT string
     */
    fun toStatustextString(): String = when (this) {
        is Start -> if (pidValues != null) "START:TRUE,$pidValues" else "START:TRUE"
        is Cancel -> "CANCEL:TRUE,Notcare:TRUE"
        is SetMode -> when (mode) {
            SetMode.Mode.MANUAL -> "MANUAL"
            SetMode.Mode.AUTO -> "AUTOMAT"
        }
        is SetSpeed -> "Speed ${"%.1f".format(speed)} → Pitch ${"%.1f".format(speed)}"
        is SetClass -> "CLASS:${targetClass.value},Notcare:${targetClass.value}"
        is SetZoom -> "Zoom:${"%.1f".format(zoom)}"
        is SetPitch -> "Pitch:${"%.1f".format(pitch)}"
        is SendPosition -> "Pos:$x,$y"
    }

    companion object {
        const val MAX_STATUSTEXT_LENGTH = 50
        const val STATUSTEXT_SEVERITY = 6 // INFO
    }
}