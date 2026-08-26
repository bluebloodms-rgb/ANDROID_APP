package com.dronegcs.app.data.mavlink

import com.dronegcs.app.data.bluetooth.BluetoothLink
import com.dronegcs.app.domain.model.Command
import com.dronegcs.app.domain.model.FlightState
import com.dronegcs.app.domain.protocol.MavlinkMessage
import com.dronegcs.app.domain.protocol.MavlinkProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import timber.log.Timber

/**
 * Repository for MAVLink flight data
 * Parses raw bytes from BluetoothSppService and emits FlightState updates
 */
class MavlinkFlightRepository(
    private val link: BluetoothLink,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {

    // Current flight state
    private val _flightState = MutableStateFlow(FlightState())
    val flightState = _flightState.asStateFlow()

    // Connection state (mirrored from service)
    val connectionState = link.connectionState

    // Raw data channel for parsing
    private val parseChannel = Channel<ByteArray>(Channel.UNLIMITED)

    // Jobs
    private var parseJob: Job? = null
    private var heartbeatWatchdogJob: Job? = null
    private var modePollJob: Job? = null

    init {
        startParsing()
        startHeartbeatWatchdog()
        startModePoll()
        observeRawData()
        observeLinkConnectionState()
    }

    /**
     * Mirrors the Bluetooth link state into FlightState so the UI telemetry
     * (which reads flightState.connectionState) reflects connected/disconnected.
     */
    private fun observeLinkConnectionState() {
        scope.launch {
            link.connectionState.collect { state ->
                _flightState.update { it.copy(connectionState = state, initialized = true) }
            }
        }
    }

    /**
     * Send a command to the flight controller
     * Implements retry logic: 5 attempts with 50ms delay
     */
    suspend fun sendCommand(command: Command): Boolean {
        val text = command.toStatustextString()
        val frame = MavlinkProtocol.encodeStatustext(text)

        repeat(5) { attempt ->
            try {
                link.sendRaw(frame)
                Timber.d("Sent command (attempt ${attempt + 1}): $text")
                return true
            } catch (e: Exception) {
                Timber.w(e, "Send attempt ${attempt + 1} failed")
                if (attempt < 4) delay(50)
            }
        }
        Timber.e("Failed to send command after 5 retries: $text")
        return false
    }

    // Convenience methods matching Python API
    suspend fun sendStart(pidValues: String? = null): Boolean = sendCommand(Command.Start(pidValues))
    suspend fun sendCancel(): Boolean = sendCommand(Command.Cancel)
    suspend fun sendMode(mode: Command.SetMode.Mode): Boolean = sendCommand(Command.SetMode(mode))
    suspend fun sendSpeed(speed: Float): Boolean = sendCommand(Command.SetSpeed(speed))
    suspend fun sendClass(targetClass: Command.SetClass.TargetClass): Boolean = sendCommand(Command.SetClass(targetClass))
    suspend fun sendZoom(zoom: Float): Boolean = sendCommand(Command.SetZoom(zoom))
    suspend fun sendPitch(pitch: Float): Boolean = sendCommand(Command.SetPitch(pitch))
    suspend fun sendPosition(x: Int, y: Int): Boolean = sendCommand(Command.SendPosition(x, y))

    private fun observeRawData() {
        scope.launch {
            link.rawDataFlow.collect { bytes ->
                if (bytes.isNotEmpty()) {
                    parseChannel.trySend(bytes)
                }
            }
        }
    }

    private fun startParsing() {
        parseJob = scope.launch(Dispatchers.IO) {
            for (bytes in parseChannel) {
                parseMavlinkData(bytes)
            }
        }
    }

    // Persistent buffer across chunks - frames often span 64-byte BT reads
    private val pending = java.io.ByteArrayOutputStream()

    private fun parseMavlinkData(bytes: ByteArray) {
        synchronized(pending) {
            pending.write(bytes, 0, bytes.size)
            val buf = pending.toByteArray()
            var offset = 0
            val stx = MavlinkProtocol.MAVLINK_STX.toByte()

            while (true) {
                // sync to next STX candidate
                while (offset < buf.size && buf[offset] != stx) offset++
                if (offset >= buf.size) { offset = buf.size; break }
                val remaining = buf.size - offset

                // need at least a minimal v2 frame to decide anything
                if (remaining < 12) break // wait for more data

                val payloadLen = buf[offset + 1].toInt() and 0xFF
                val totalLen = 10 + payloadLen + 2
                if (remaining < totalLen) break // partial frame - wait for the rest

                val message = MavlinkProtocol.parseMavlinkMessage(buf.copyOfRange(offset, buf.size))
                if (message != null) {
                    processMavlinkMessage(message)
                    offset += totalLen
                } else {
                    // false STX inside payload data - skip one byte and resync
                    offset++
                }
            }

            // keep unparsed tail for next chunk; guard against garbage flood
            if (offset > 0) {
                val leftover = buf.copyOfRange(offset, buf.size)
                pending.reset()
                if (leftover.size <= 4096) {
                    pending.write(leftover, 0, leftover.size)
                } else {
                    Timber.w("Parse buffer overflow (${leftover.size}B) - flushed")
                }
            }
        }
    }

    private fun processMavlinkMessage(message: MavlinkMessage) {
        when (message.msgId) {
            MavlinkProtocol.MSG_ID_STATUSTEXT -> {
                val text = MavlinkProtocol.decodeStatustextPayload(message.payload)
                text?.let { processStatustext(it) }
            }
            0 -> processHeartbeat(message)        // HEARTBEAT
            1 -> processSysStatus(message)        // SYS_STATUS (ArduPilot battery voltage)
            147 -> processBattery(message)        // BATTERY_STATUS
            173 -> processRangefinder(message)    // RANGEFINDER (ArduPilot dialect)
            24 -> processGps(message)             // GPS_RAW_INT
            else -> Timber.v("Unhandled MAVLink message: ${message.msgId}")
        }
    }

    private fun processStatustext(text: String) {
        Timber.d("[RX STATUSTEXT] $text")
        _flightState.update { current ->
            current.updateFromMessage(text)
        }
    }

    private fun processHeartbeat(message: MavlinkMessage) {
        // HEARTBEAT message received
        _flightState.update { current ->
            current.updateHeartbeat()
        }
    }

    private fun processBattery(message: MavlinkMessage) {
        // BATTERY_STATUS: voltages[0] is uint16_t mV at offset 0 (10-bit encoding)
        if (message.payload.size >= 2) {
            val voltageMv = (message.payload[0].toInt() and 0xFF) or
                            ((message.payload[1].toInt() and 0xFF) shl 8)
            if (voltageMv in 1..0xFFFF && voltageMv != 0xFFFF) {
                _flightState.update { current ->
                    current.updateTelemetry(battery = voltageMv / 1000.0f)
                }
            }
        }
    }

    private fun processSysStatus(message: MavlinkMessage) {
        // SYS_STATUS: voltage_battery uint16_t mV at offset 14
        // (after sensors_present/enabled/found u32 x3 + load u16)
        if (message.payload.size >= 16) {
            val voltageMv = (message.payload[14].toInt() and 0xFF) or
                            ((message.payload[15].toInt() and 0xFF) shl 8)
            if (voltageMv > 0) {
                _flightState.update { current ->
                    current.updateTelemetry(battery = voltageMv / 1000.0f)
                }
            }
        }
    }

    private fun processRangefinder(message: MavlinkMessage) {
        // RANGEFINDER (ArduPilot): distance float @0, voltage uint16 @4
        // altitude = distance + 0.05 (matches original Python code)
        if (message.payload.size >= 4) {
            val distanceBytes = message.payload.copyOfRange(0, 4)
            val distance = java.nio.ByteBuffer.wrap(distanceBytes)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN).float
            if (distance > -1f && distance < 1000f) { // sanity check against garbage
                val altitude = ((distance + 0.05f) * 10000f).roundToInt() / 10000.0f
                _flightState.update { current ->
                    current.updateTelemetry(altitude = altitude)
                }
            }
        }
    }

    private fun processGps(message: MavlinkMessage) {
        // GPS_RAW_INT wire layout:
        // time_usec(8) fix_type(1) lat(4) lon(4) alt(4) eph(2) epv(2) vel(2) cog(2) satellites_visible(1)
        // -> eph @21, satellites_visible @29
        if (message.payload.size >= 30) {
            val eph = (message.payload[21].toInt() and 0xFF) or
                      ((message.payload[22].toInt() and 0xFF) shl 8)
            val hdop = eph / 100.0f

            val satellites = message.payload[29].toInt() and 0xFF
            val fixType = message.payload[8].toInt() and 0xFF

            _flightState.update { current ->
                current.updateTelemetry(
                    hdop = hdop,
                    satellites = if (fixType > 0) satellites else 0
                )
            }
        }
    }

    private fun startHeartbeatWatchdog() {
        heartbeatWatchdogJob = scope.launch {
            while (true) {
                delay(1000)
                _flightState.update { current ->
                    // Only devices that HAVE streamed a heartbeat before can be
                    // declared dead by silence (e.g. the custom STATUSTEXT FC).
                    // Devices like SIYI that never send heartbeats must stay
                    // "Connected" as long as the Bluetooth socket is alive -
                    // socket loss is reported by the service itself.
                    if (current.connectionState.isConnected() &&
                        current.lastHeartbeat > 0 &&
                        current.isHeartbeatTimeout()
                    ) {
                        Timber.w("Heartbeat timeout - marking disconnected")
                        current.copy(connectionState = com.dronegcs.app.domain.model.ConnectionState.Disconnected("Heartbeat timeout"))
                    } else {
                        current
                    }
                }
            }
        }
    }

    private fun startModePoll() {
        // Poll vehicle mode every 50ms (matching Python's QTimer at 50ms)
        // In Android, we'll poll the flight state for mode changes
        modePollJob = scope.launch {
            var lastMode: String? = null
            while (true) {
                delay(50)
                _flightState.update { current ->
                    // The mode is updated from HEARTBEAT or STATUSTEXT
                    // This is a placeholder for mode polling if needed
                    current
                }
            }
        }
    }

    fun shutdown() {
        parseJob?.cancel()
        heartbeatWatchdogJob?.cancel()
        modePollJob?.cancel()
        parseChannel.close()
    }
}