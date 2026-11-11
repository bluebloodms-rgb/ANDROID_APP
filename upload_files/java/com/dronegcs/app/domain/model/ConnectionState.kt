package com.dronegcs.app.domain.model

/**
 * Sealed class representing the Bluetooth connection state
 */
sealed interface ConnectionState {
    data class Disconnected(val reason: String? = null) : ConnectionState
    data class Connecting(val deviceName: String? = null) : ConnectionState

    /**
     * The Bluetooth socket is open. [telemetryStale] is set when no MAVLink
     * message has arrived from the vehicle for longer than the health
     * threshold: the link is up, but the vehicle state is not live.
     */
    data class Connected(
        val deviceName: String,
        val deviceAddress: String,
        val telemetryStale: Boolean = false
    ) : ConnectionState

    data class Error(val message: String) : ConnectionState

    fun isConnected(): Boolean = this is Connected
    fun isConnecting(): Boolean = this is Connecting
    fun isDisconnected(): Boolean = this is Disconnected || this is Error

    /** Socket is up but the vehicle has gone silent. */
    fun isLinkOnly(): Boolean = this is Connected && telemetryStale

    val displayName: String
        get() = when (this) {
            is Disconnected -> "Disconnected"
            is Connecting -> "Connecting..."
            is Connected -> if (telemetryStale) "Link only (no vehicle data)" else "Connected to $deviceName"
            is Error -> "Error: $message"
        }

    val colorResource: Int
        get() = when (this) {
            is Disconnected -> 0xFFFF4444.toInt() // Red
            is Connecting -> 0xFFFF8C00.toInt() // Orange
            is Connected -> if (telemetryStale) 0xFFFF8C00.toInt() else 0xFF00BFFF.toInt() // Amber / Electric Blue
            is Error -> 0xFFFF4444.toInt() // Red
        }
}