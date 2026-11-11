package com.dronegcs.app.domain.model

/**
 * UI State for Connection screen
 */
sealed interface ConnectionUiState {
    object Idle : ConnectionUiState
    data class Connecting(val deviceName: String?) : ConnectionUiState

    /**
     * [linkOnly] is true when the Bluetooth socket is open but the vehicle has
     * stopped sending MAVLink data. The link is usable for TX, but telemetry is
     * not live; the UI must not present this as a healthy connection.
     */
    data class Connected(
        val deviceName: String,
        val deviceAddress: String,
        val linkOnly: Boolean = false
    ) : ConnectionUiState

    data class Error(val message: String) : ConnectionUiState

    val isConnected: Boolean
        get() = this is Connected

    val isConnecting: Boolean
        get() = this is Connecting

    val isError: Boolean
        get() = this is Error
}