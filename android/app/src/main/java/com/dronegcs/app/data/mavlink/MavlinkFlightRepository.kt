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

    private fun parseMavlinkData(bytes: ByteArray) {
        // Use the protocol parser to extract MAVLink messages
        var offset = 0
        while (offset < bytes.size) {
            val message = MavlinkProtocol.parseMavlinkMessage(bytes.copyOfRange(offset, bytes.size))
            if (message == null) {
                // Try to find next STX
                var found = false
                for (i in offset + 1 until bytes.size) {
                    if (bytes[i].toInt() and 0xFF == MavlinkProtocol.MAVLINK_STX) {
                        offset = i
                        found = true
                        break
                    }
                }
                if (!found) break
                continue
            }

            // Process the parsed message
            processMavlinkMessage(message)

            // Move offset past this message
            val msgLen = message.payload.size
            offset += 9 + msgLen + 2 // header(9) + payload + checksum(2)
        }
    }

    private fun processMavlinkMessage(message: MavlinkMessage) {
        when (message.msgId) {
            MavlinkProtocol.MSG_ID_STATUSTEXT -> {
                val text = MavlinkProtocol.decodeStatustextPayload(message.payload)
                text?.let { processStatustext(it) }
            }
            0 -> processHeartbeat(message)        // HEARTBEAT
            173 -> processBattery(message)        // BATTERY_STATUS
            174 -> processRangefinder(message)    // RANGEFINDER
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
        // BATTERY_STATUS: voltages[0] / 1000.0 -> battery voltage
        if (message.payload.size >= 2) {
            // First 2 bytes are voltages[0] (uint16_t, mV)
            val voltageMv = (message.payload[0].toInt() and 0xFF) or
                           ((message.payload[1].toInt() and 0xFF) shl 8)
            val voltage = voltageMv / 1000.0f
            _flightState.update { current ->
                current.updateTelemetry(battery = voltage)
            }
        }
    }

    private fun processRangefinder(message: MavlinkMessage) {
        // RANGEFINDER: distance + 0.05 -> altitude (meters, rounded to 4 decimals)
        if (message.payload.size >= 4) {
            // distance is float at offset 0 (assuming standard MAVLink layout)
            val distanceBytes = message.payload.copyOfRange(0, 4)
            val distance = java.nio.ByteBuffer.wrap(distanceBytes).order(java.nio.ByteOrder.LITTLE_ENDIAN).float
            val altitude = ((distance + 0.05f) * 10000f).roundToInt() / 10000.0f
            _flightState.update { current ->
                current.updateTelemetry(altitude = altitude)
            }
        }
    }

    private fun processGps(message: MavlinkMessage) {
        // GPS_RAW_INT: eph/100.0 -> hdop, satellites_visible -> satellites
        if (message.payload.size >= 22) {
            // eph is uint16_t at offset 18 (after time_usec, lat, lon, alt)
            val eph = (message.payload[18].toInt() and 0xFF) or
                      ((message.payload[19].toInt() and 0xFF) shl 8)
            val hdop = eph / 100.0f

            // satellites_visible is uint8_t at offset 21
            val satellites = message.payload[21].toInt() and 0xFF

            _flightState.update { current ->
                current.updateTelemetry(hdop = hdop, satellites = satellites)
            }
        }
    }

    private fun startHeartbeatWatchdog() {
        heartbeatWatchdogJob = scope.launch {
            while (true) {
                delay(1000)
                _flightState.update { current ->
                    if (current.isHeartbeatTimeout() && current.connectionState.isConnected()) {
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