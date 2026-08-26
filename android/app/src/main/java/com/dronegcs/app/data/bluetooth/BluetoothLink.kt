package com.dronegcs.app.data.bluetooth

import com.dronegcs.app.domain.model.ConnectionState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-wide bridge between [BluetoothSppService] and consumers
 * (repositories / viewmodels). The running service publishes connection
 * state and raw incoming bytes here; consumers observe them and enqueue
 * outgoing frames without holding a reference to the Service itself.
 */
@Singleton
class BluetoothLink @Inject constructor() {

    private val _connectionState =
        MutableStateFlow<ConnectionState>(ConnectionState.Disconnected())
    val connectionState = _connectionState.asStateFlow()

    private val _rawDataFlow = MutableStateFlow(ByteArray(0))
    val rawDataFlow = _rawDataFlow.asStateFlow()

    private val _outgoing = Channel<ByteArray>(Channel.UNLIMITED)
    val outgoing: Channel<ByteArray> get() = _outgoing

    // --- Called by the foreground service ---

    fun publishState(state: ConnectionState) {
        _connectionState.value = state
    }

    fun currentState(): ConnectionState = _connectionState.value

    fun publishData(bytes: ByteArray) {
        _rawDataFlow.value = bytes
    }

    // --- Called by consumers ---

    fun sendRaw(bytes: ByteArray): Boolean = _outgoing.trySend(bytes).isSuccess
}
