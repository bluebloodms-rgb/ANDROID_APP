package com.dronegcs.app.domain.command

import com.dronegcs.app.domain.model.Command
import com.dronegcs.app.domain.model.FlightState
import kotlin.math.abs

/**
 * Tracks the command that is currently awaiting feedback from the on-board
 * tracking/command responder (MAVLink sys=10).
 *
 * The responder does not send MAVLink COMMAND_ACK for the custom STATUSTEXT
 * protocol; it reports state as `Op/St/Md/Cls/Spd/Zoom/Pitch/Can`. A command is
 * therefore acknowledged when that state reaches the requested value.
 *
 * The class is pure Kotlin (clock injected) so the timeout/matching rules can be
 * unit-tested without Android or coroutines.
 */
class CommandTracker(
    private val clock: () -> Long = System::currentTimeMillis
) {

    private data class Pending(
        val command: Command,
        val text: String,
        val sentAtMs: Long,
        val timeoutMs: Long,
        var sawResponderState: Boolean = false
    )

    private var pending: Pending? = null

    /** Called only after the frame was actually written to the socket. */
    @Synchronized
    fun onCommandSent(command: Command): CommandFeedback {
        val text = command.toStatustextString()
        // A new command of the same family supersedes whatever was pending, so
        // dragging a slider does not produce a flood of NO ACK warnings.
        pending = Pending(
            command = command,
            text = text,
            sentAtMs = clock(),
            timeoutMs = timeoutFor(command)
        )
        return feedback(command, text, CommandPhase.SENT, "written to socket")
    }

    /**
     * Feed one responder state STATUSTEXT. Returns EXECUTED when the requested
     * value is now present; returns null while the responder is still reporting
     * an older value (the timeout decides NOT_EXECUTED vs NO_ACK).
     */
    @Synchronized
    fun onResponderState(text: String, state: FlightState): CommandFeedback? {
        val p = pending ?: return null
        p.sawResponderState = true
        if (!matches(p.command, text, state)) return null
        pending = null
        return feedback(
            p.command,
            p.text,
            CommandPhase.EXECUTED,
            describeExpected(p.command)
        )
    }

    /**
     * Advance the timeout. [responderOnline] only changes the wording; a
     * missing responder still produces NO_ACK.
     */
    @Synchronized
    fun tick(state: FlightState, responderOnline: Boolean): CommandFeedback? {
        val p = pending ?: return null
        val elapsed = clock() - p.sentAtMs
        if (elapsed < p.timeoutMs) return null
        pending = null

        return when {
            p.command is Command.SendPosition -> feedback(
                p.command,
                p.text,
                CommandPhase.UNCONFIRMED,
                "Pos has no state field to confirm it"
            )

            !p.sawResponderState -> feedback(
                p.command,
                p.text,
                CommandPhase.NO_ACK,
                if (responderOnline) {
                    "no responder state within ${p.timeoutMs}ms"
                } else {
                    "tracking responder offline; no state within ${p.timeoutMs}ms"
                }
            )

            else -> feedback(
                p.command,
                p.text,
                CommandPhase.NOT_EXECUTED,
                "expected ${describeExpected(p.command)}; last state ${summarize(state)}"
            )
        }
    }

    /** Frame never reached the socket. */
    @Synchronized
    fun onCommandFailed(command: Command, reason: String): CommandFeedback {
        pending = null
        return feedback(command, command.toStatustextString(), CommandPhase.FAILED, reason)
    }

    @Synchronized
    fun hasPending(): Boolean = pending != null

    // --- Matching rules -----------------------------------------------------

    private fun matches(command: Command, text: String, state: FlightState): Boolean {
        val keys = keysIn(text)
        return when (command) {
            is Command.Start -> "Op" in keys && state.op == 2
            is Command.Cancel -> "Op" in keys && state.op != 2
            is Command.SetMode -> "Md" in keys && state.md == when (command.mode) {
                Command.SetMode.Mode.MANUAL -> 1
                Command.SetMode.Mode.AUTO -> 2
            }
            is Command.SetSpeed -> "Spd" in keys && abs(state.spd - command.speed) <= SPEED_TOLERANCE
            is Command.SetClass -> ("Cls" in keys || "CLASS" in keys) &&
                state.cls == command.targetClass.value
            is Command.SetZoom -> "Zoom" in keys && state.zoom != null &&
                abs(state.zoom - command.zoom) <= ZOOM_TOLERANCE
            is Command.SetPitch -> "Pitch" in keys && state.pitch != null &&
                abs(state.pitch - command.pitch) <= PITCH_TOLERANCE
            is Command.SendPosition -> false
        }
    }

    private fun keysIn(text: String): Set<String> =
        text.replace(" ", "")
            .split(",")
            .filter { it.contains(":") }
            .map { it.substringBefore(":") }
            .toSet()

    private fun describeExpected(command: Command): String = when (command) {
        is Command.Start -> "Op:2"
        is Command.Cancel -> "Op != 2"
        is Command.SetMode -> if (command.mode == Command.SetMode.Mode.MANUAL) "Md:1" else "Md:2"
        is Command.SetSpeed -> "Spd:${"%.1f".format(command.speed)}"
        is Command.SetClass -> "Cls:${command.targetClass.value}"
        is Command.SetZoom -> "Zoom:${"%.1f".format(command.zoom)}"
        is Command.SetPitch -> "Pitch:${"%.1f".format(command.pitch)}"
        is Command.SendPosition -> "no feedback field"
    }

    private fun summarize(state: FlightState): String = buildString {
        append("Op:${state.op},Md:${state.md},Cls:${state.cls},Spd:${"%.1f".format(state.spd)}")
        state.zoom?.let { append(",Zoom:${"%.1f".format(it)}") }
        state.pitch?.let { append(",Pitch:${"%.1f".format(it)}") }
    }

    private fun timeoutFor(command: Command): Long = when (command) {
        is Command.Start, is Command.Cancel -> 3000L
        is Command.SendPosition -> 2500L
        else -> 4000L
    }

    private fun feedback(
        command: Command,
        text: String,
        phase: CommandPhase,
        detail: String
    ) = CommandFeedback(
        command = command,
        commandText = text,
        phase = phase,
        detail = detail,
        timestampMs = clock()
    )

    private companion object {
        const val SPEED_TOLERANCE = 0.6f
        const val ZOOM_TOLERANCE = 0.6f
        const val PITCH_TOLERANCE = 1.0f
    }
}
