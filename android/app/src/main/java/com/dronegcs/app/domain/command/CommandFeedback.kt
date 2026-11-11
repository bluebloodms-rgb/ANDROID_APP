package com.dronegcs.app.domain.command

import com.dronegcs.app.domain.model.Command

/** Lifecycle of one command from the user's point of view. */
enum class CommandPhase {
    /** The frame was written to the Bluetooth socket. */
    SENT,

    /** The responder state now matches what the command asked for. */
    EXECUTED,

    /** The responder kept reporting state, but never reached the requested value. */
    NOT_EXECUTED,

    /** No responder state arrived at all before the timeout. */
    NO_ACK,

    /** The command has no observable feedback field (e.g. Pos:x,y). */
    UNCONFIRMED,

    /** The frame was never written (link down / write error). */
    FAILED
}

/**
 * User-visible outcome of one command. The UI banner and the log lines are
 * driven from the same object, so they cannot disagree.
 */
data class CommandFeedback(
    val command: Command,
    val commandText: String,
    val phase: CommandPhase,
    val detail: String,
    val timestampMs: Long
) {
    val isFailure: Boolean
        get() = phase == CommandPhase.NO_ACK ||
            phase == CommandPhase.NOT_EXECUTED ||
            phase == CommandPhase.FAILED

    val label: String
        get() = when (phase) {
            CommandPhase.SENT -> "SENT"
            CommandPhase.EXECUTED -> "EXECUTED"
            CommandPhase.NOT_EXECUTED -> "NOT EXECUTED"
            CommandPhase.NO_ACK -> "NO ACK"
            CommandPhase.UNCONFIRMED -> "UNCONFIRMED"
            CommandPhase.FAILED -> "NOT SENT"
        }
}
