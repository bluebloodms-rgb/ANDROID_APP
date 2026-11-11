package com.dronegcs.app.data.bluetooth

import com.dronegcs.app.domain.model.ConnectionState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One outbound frame plus the outcome of its *actual socket write*.
 *
 * A consumer that only needs fire-and-forget can ignore [result]; callers that
 * report "sent" to the user must await it, because enqueueing into the channel
 * does not mean the bytes reached the Bluetooth socket.
 */
class OutboundFrame(
    val bytes: ByteArray,
    val result: CompletableDeferred<WriteResult> = CompletableDeferred()
) {
    val size: Int get() = bytes.size
}

sealed interface WriteResult {
    /** Bytes were written and flushed to the socket. */
    data object Written : WriteResult

    /** The frame was never written. */
    data class Failed(val reason: String) : WriteResult
}

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

    private val _outgoing = Channel<OutboundFrame>(Channel.UNLIMITED)
    val outgoing: Channel<OutboundFrame> get() = _outgoing

    // --- Called by the foreground service ---

    fun publishState(state: ConnectionState) {
        _connectionState.value = state
    }

    fun currentState(): ConnectionState = _connectionState.value

    fun publishData(bytes: ByteArray) {
        _rawDataFlow.value = bytes
    }

    /**
     * Complete every queued frame as failed. Called when the socket goes away
     * so no sender waits for a write that can never happen.
     */
    fun failPending(reason: String) {
        while (true) {
            val frame = _outgoing.tryReceive().getOrNull() ?: break
            frame.result.complete(WriteResult.Failed(reason))
        }
    }

    // --- Called by consumers ---

    /**
     * Enqueue a frame only while the socket is connected.
     *
     * @return the enqueued frame (whose [OutboundFrame.result] completes after
     * the real write) or null when the link is down.
     */
    fun enqueue(bytes: ByteArray): OutboundFrame? {
        if (!_connectionState.value.isConnected()) {
            Timber.w("TX enqueue rejected (link not connected): %d bytes", bytes.size)
            return null
        }
        val frame = OutboundFrame(bytes)
        return if (_outgoing.trySend(frame).isSuccess) {
            frame
        } else {
            Timber.w("TX enqueue rejected (outgoing channel closed): %d bytes", bytes.size)
            null
        }
    }

    /** Fire-and-forget enqueue. Returns false when the link is down. */
    fun sendRaw(bytes: ByteArray): Boolean = enqueue(bytes) != null

    /**
     * Enqueue a frame and wait until the write worker has written and flushed
     * it (or reported a failure / timed out).
     */
    suspend fun sendAndAwait(
        bytes: ByteArray,
        timeoutMs: Long = DEFAULT_WRITE_TIMEOUT_MS
    ): WriteResult {
        val frame = enqueue(bytes) ?: return WriteResult.Failed("link not connected")
        return withTimeoutOrNull(timeoutMs) { frame.result.await() }
            ?: WriteResult.Failed("write timeout after ${timeoutMs}ms")
    }

    companion object {
        const val DEFAULT_WRITE_TIMEOUT_MS = 1500L
    }
}
