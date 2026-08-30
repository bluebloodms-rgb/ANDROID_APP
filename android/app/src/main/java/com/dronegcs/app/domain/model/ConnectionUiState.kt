package com.dronegcs.app.domain.model

/**
 * UI State for Connection screen
 */
sealed interface ConnectionUiState {
    object Idle : ConnectionUiState
    data class Connecting(val deviceName: String?) : ConnectionUiState
    data class Connected(val deviceName: String, val deviceAddress: String) : ConnectionUiState
    data class Error(val message: String) : ConnectionUiState

    val isConnected: Boolean
        get() = this is Connected

    val isConnecting: Boolean
        get() = this is Connecting

    val isError: Boolean
        get() = this is Error
}