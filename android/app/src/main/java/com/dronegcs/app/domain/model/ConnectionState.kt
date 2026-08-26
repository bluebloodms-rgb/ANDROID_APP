package com.dronegcs.app.domain.model

/**
 * Sealed class representing the Bluetooth connection state
 */
sealed interface ConnectionState {
    data class Disconnected(val reason: String? = null) : ConnectionState
    data class Connecting(val deviceName: String? = null) : ConnectionState
    data class Connected(val deviceName: String, val deviceAddress: String) : ConnectionState
    data class Error(val message: String) : ConnectionState

    fun isConnected(): Boolean = this is Connected
    fun isConnecting(): Boolean = this is Connecting
    fun isDisconnected(): Boolean = this is Disconnected || this is Error

    val displayName: String
        get() = when (this) {
            is Disconnected -> "Disconnected"
            is Connecting -> "Connecting..."
            is Connected -> "Connected to ${deviceName}"
            is Error -> "Error: ${message}"
        }

    val colorResource: Int
        get() = when (this) {
            is Disconnected -> 0xFFFF4444.toInt() // Red
            is Connecting -> 0xFFFF8C00.toInt() // Orange
            is Connected -> 0xFF00BFFF.toInt() // Electric Blue
            is Error -> 0xFFFF4444.toInt() // Red
        }
}