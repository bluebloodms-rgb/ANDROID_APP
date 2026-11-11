package com.dronegcs.app.data.mavlink

import com.dronegcs.app.data.bluetooth.BluetoothLink
import com.dronegcs.app.data.bluetooth.WriteResult
import com.dronegcs.app.domain.command.CommandFeedback
import com.dronegcs.app.domain.command.CommandPhase
import com.dronegcs.app.domain.command.CommandTracker
import com.dronegcs.app.domain.model.Command
import com.dronegcs.app.domain.model.ConnectionState
import com.dronegcs.app.domain.model.FlightState
import com.dronegcs.app.domain.model.ResponderStatus
import com.dronegcs.app.domain.protocol.MavlinkMessage
import com.dronegcs.app.domain.protocol.MavlinkProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    // Vehicle identity on the MAVLink wire, discovered at runtime by locking onto
    // the first HEARTBEAT whose MAV_TYPE is an aerial vehicle. Hardcoding
    // sysId=1/compId=1 broke the mode chip on setups where the FC uses other IDs.
    private companion object {
        // MAV_TYPE values that identify a drone: FIXED_WING=1, QUADROTOR=2,
        // COAXIAL=3, HELICOPTER=4, HEXAROTOR=13, OCTOROTOR=14, TRICOPTOR=15.
        val VEHICLE_MAV_TYPES = setOf(1, 2, 3, 4, 13, 14, 15)

        /** No message from the vehicle (sys=1) for this long -> LINK ONLY. */
        const val VEHICLE_STALE_MS = 5_000L

        /** No responder state (sys=10) for this long -> TRACKING OFFLINE. */
        const val RESPONDER_OFFLINE_MS = 3_000L

        /** Keys that identify a state reply from the command/tracking responder. */
        val RESPONDER_STATE_KEYS = listOf(
            "Op:", "St:", "Md:", "Cls:", "CLASS:", "Spd:", "Zoom:", "Pitch:", "Can:"
        )

        /** Battery voltage threshold (V) for low battery warning. */
        const val LOW_BATTERY_THRESHOLD_V = 30.0f
    }

    @Volatile private var vehicleSysId: Int? = null
    @Volatile private var vehicleCompId: Int? = null

    /** Wall-clock time the current socket connection became Connected. */
    @Volatile private var connectedAtMs: Long = 0L

    // Current flight state
    private val _flightState = MutableStateFlow(FlightState())
    val flightState = _flightState.asStateFlow()

    // Command outcomes for the UI (replay=1 so a late collector still sees the last result)
    private val _commandEvents = MutableSharedFlow<CommandFeedback>(
        replay = 1,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val commandEvents = _commandEvents.asSharedFlow()

    private val commandTracker = CommandTracker()

    /**
     * Commands are written in order and tracked in order. Without this, two
     * coroutines resumed out of order could record command A *after* command B
     * and leave the wrong command pending for ACK matching.
     */
    private val sendMutex = Mutex()

    // Connection state (mirrored from service)
    val connectionState = link.connectionState

    // Raw data channel for parsing
    private val parseChannel = Channel<ByteArray>(Channel.UNLIMITED)

    // Jobs
    private var parseJob: Job? = null
    private var healthWatchdogJob: Job? = null

    init {
        startParsing()
        startHealthWatchdog()
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
                if (!state.isConnected()) {
                    // Re-arm the vehicle lock-on so the next connection re-identifies
                    // the FC (its sysId/compId may change between sessions).
                    vehicleSysId = null
                    vehicleCompId = null
                    connectedAtMs = 0L
                    _flightState.update {
                        it.copy(
                            connectionState = state,
                            initialized = true,
                            lastVehicleRx = 0,
                            lastResponderRx = 0,
                            telemetryStale = false,
                            responderStatus = ResponderStatus.UNKNOWN,
                            crcMismatchCount = 0,
                            linkQuality = 100,
                            emergencyStop = false,
                            lowBatteryWarning = false
                        )
                    }
                } else {
                    if (connectedAtMs == 0L) connectedAtMs = System.currentTimeMillis()
                    _flightState.update { it.copy(connectionState = state, initialized = true) }
                }
            }
        }
    }

    /**
     * Send a command to the flight controller.
     *
     * All commands use the custom STATUSTEXT protocol — identical to the working
     * Windows/desktop app (see core/flight_interface.py send_start/send_cancel etc.).
     * START = STATUSTEXT "START:TRUE[,pid...]", CANCEL = "CANCEL:TRUE,Notcare:TRUE".
     * Do NOT send COMMAND_LONG (ARM/TAKEOFF/RTL) here: the FC consumes the custom
     * STATUSTEXT protocol and COMMAND_LONG would actually arm/move the vehicle.
     */
    suspend fun sendCommand(command: Command): Boolean = sendMutex.withLock {
        val text = command.toStatustextString()
        val frame = MavlinkProtocol.encodeStatustext(text)
        Timber.d("Enqueued STATUSTEXT: %s", text)

        when (val result = link.sendAndAwait(frame)) {
            is WriteResult.Written -> {
                // Only now may we claim the command was sent: the write worker
                // completed after outputStream.write() + flush().
                Timber.i("Sent STATUSTEXT (written to socket): %s", text)
                emitFeedback(commandTracker.onCommandSent(command))
                true
            }

            is WriteResult.Failed -> {
                Timber.w("NOT sent (%s): %s", result.reason, text)
                emitFeedback(commandTracker.onCommandFailed(command, result.reason))
                false
            }
        }
    }

    private fun emitFeedback(feedback: CommandFeedback) {
        when (feedback.phase) {
            CommandPhase.SENT ->
                Timber.d("CMD SENT: %s", feedback.commandText)
            CommandPhase.EXECUTED ->
                Timber.i("EXECUTED: %s (%s)", feedback.commandText, feedback.detail)
            CommandPhase.NOT_EXECUTED ->
                Timber.w("NOT EXECUTED: %s (%s)", feedback.commandText, feedback.detail)
            CommandPhase.NO_ACK ->
                Timber.w("NO ACK: %s (%s)", feedback.commandText, feedback.detail)
            CommandPhase.UNCONFIRMED ->
                Timber.i("UNCONFIRMED: %s (%s)", feedback.commandText, feedback.detail)
            CommandPhase.FAILED ->
                Timber.e("COMMAND NOT SENT: %s (%s)", feedback.commandText, feedback.detail)
        }
        _commandEvents.tryEmit(feedback)
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

                val (message, crcMismatch) = MavlinkProtocol.parseMavlinkMessage(buf.copyOfRange(offset, buf.size))
                if (crcMismatch) {
                    _flightState.update { current ->
                        current.copy(
                            crcMismatchCount = current.crcMismatchCount + 1,
                            linkQuality = (current.linkQuality - 1).coerceIn(0, 100)
                        )
                    }
                }
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
        val now = System.currentTimeMillis()

        // Slowly recover link quality on successful messages
        _flightState.update { current ->
            if (current.linkQuality < 100) {
                current.copy(linkQuality = current.linkQuality + 1)
            } else {
                current
            }
        }

        // Lock onto the vehicle by MAV_TYPE, not by hardcoded sysId/compId:
        // other components on the wire (SIYI gimbal, companion, ...) also emit
        // HEARTBEATs with different custom_mode values, which made the flight
        // mode chip fluctuate between e.g. GUIDED / STABILIZE.
        if (message.msgId == 0) { // HEARTBEAT
            val mavType = message.payload.getOrNull(4)?.toInt()?.and(0xFF)
            val lockedSys = vehicleSysId
            if (lockedSys == null) {
                if (mavType != null && mavType in VEHICLE_MAV_TYPES) {
                    vehicleSysId = message.sysId
                    vehicleCompId = message.compId
                    Timber.i("Locked onto vehicle: sys=%d comp=%d mavType=%d", message.sysId, message.compId, mavType)
                } else {
                    Timber.v("Ignored HEARTBEAT from sys=%d comp=%d mavType=%s (not an aerial vehicle)", message.sysId, message.compId, mavType)
                    return
                }
            } else if (message.sysId != lockedSys) {
                Timber.v("Ignored HEARTBEAT from sys=%d (vehicle is sys=%d)", message.sysId, lockedSys)
                return
            }
            markVehicleRx(now)
            processHeartbeat(message)
            return
        }

        // STATUSTEXT is a broadcast message. The custom command/tracking
        // responder (sys=10) reports state through it but never sends a
        // heartbeat, so it must be handled before the "only the vehicle" filter
        // below — otherwise every responder state is silently discarded.
        if (message.msgId == MavlinkProtocol.MSG_ID_STATUSTEXT) {
            processStatustextMessage(message, now)
            return
        }

        val vsid = vehicleSysId
        if (vsid == null || message.sysId != vsid) {
            Timber.v("Ignored MAVLink msg id=%d from sys=%d comp=%d (not the vehicle)", message.msgId, message.sysId, message.compId)
            return
        }
        markVehicleRx(now)
        when (message.msgId) {
            MavlinkProtocol.MSG_ID_COMMAND_ACK -> processCommandAck(message)  // COMMAND_ACK
            1 -> processSysStatus(message)        // SYS_STATUS (ArduPilot battery voltage)
            24 -> processGps(message)             // GPS_RAW_INT
            33 -> processGlobalPosition(message)  // GLOBAL_POSITION_INT (relative_alt, lat, lon)
            147 -> processBattery(message)        // BATTERY_STATUS
            173 -> processRangefinder(message)    // RANGEFINDER (ArduPilot dialect)
            else -> Timber.v("Unhandled MAVLink message: ${message.msgId}")
        }
    }

    /**
     * Handle a STATUSTEXT from either the vehicle (sys=1) or the on-board
     * tracking responder (sys=10).
     */
    private fun processStatustextMessage(message: MavlinkMessage, now: Long) {
        val text = MavlinkProtocol.decodeStatustextPayload(message.payload)
        if (text == null) {
            Timber.w("STATUSTEXT with unparsable payload (len=%d)", message.payload.size)
            return
        }

        val vsid = vehicleSysId
        val fromVehicle = vsid != null && message.sysId == vsid
        val responderState = looksLikeResponderState(text)

        if (fromVehicle) markVehicleRx(now)
        if (responderState) markResponderRx(message.sysId, now)

        if (fromVehicle || responderState) {
            Timber.i("RX STATUSTEXT (sys=%d): %s", message.sysId, text)
            processStatustext(text)
        } else {
            Timber.i("Ignored STATUSTEXT from sys=%d: %s", message.sysId, text)
            return
        }

        if (responderState) {
            commandTracker.onResponderState(text, _flightState.value)?.let { emitFeedback(it) }
        }
    }

    private fun looksLikeResponderState(text: String): Boolean =
        RESPONDER_STATE_KEYS.any { text.contains(it, ignoreCase = true) }

    private fun markVehicleRx(now: Long) {
        if (_flightState.value.lastVehicleRx == 0L) {
            Timber.i("Vehicle telemetry online (sys=%s)", vehicleSysId)
        }
        _flightState.update { it.copy(lastVehicleRx = now) }
    }

    private fun markResponderRx(sysId: Int, now: Long) {
        if (_flightState.value.responderStatus != ResponderStatus.ONLINE) {
            Timber.i("Tracking responder online (sys=%d)", sysId)
        }
        _flightState.update {
            it.copy(lastResponderRx = now, responderStatus = ResponderStatus.ONLINE)
        }
    }

    private fun processCommandAck(message: MavlinkMessage) {
        // COMMAND_ACK payload: command(2) result(1) progress(1) result_param2(2) target_system(1) target_component(1)
        if (message.payload.size >= 7) {
            val command = (message.payload[0].toInt() and 0xFF) or
                          ((message.payload[1].toInt() and 0xFF) shl 8)
            val result = message.payload[2].toInt() and 0xFF
            val resultNames = arrayOf(
                "ACCEPTED", "TEMPORARILY_REJECTED", "DENIED", "UNSUPPORTED",
                "FAILED", "IN_PROGRESS", "CANCELLED", "AUTH_DENIED"
            )
            val resultName = if (result < resultNames.size) resultNames[result] else "UNKNOWN($result)"
            Timber.i("COMMAND_ACK: cmd=$command result=$resultName")
            _flightState.update { current ->
                current.updateFromCommandAck(command, result)
            }
        }
    }

    private fun processStatustext(text: String) {
        _flightState.update { current ->
            // Check for PreArm emergency stop message
            val emergencyStop = text.contains("PreArm: Motors Emergency Stopped", ignoreCase = true)
            current.updateFromMessage(text).updateEmergencyStop(emergencyStop)
        }
    }

    private fun processHeartbeat(message: MavlinkMessage) {
        // MAVLink HEARTBEAT wire layout (declaration order):
        //   custom_mode u32 @0, type u8 @4, autopilot u8 @5, base_mode u8 @6,
        //   system_status u8 @7, mavlink_version u8 @8
        // custom_mode is the ArduPilot flight-mode enum (little-endian uint32).
        var customMode: Int? = null
        var armed: Boolean? = null
        if (message.payload.size >= 9) {
            customMode = (message.payload[0].toInt() and 0xFF) or
                    ((message.payload[1].toInt() and 0xFF) shl 8) or
                    ((message.payload[2].toInt() and 0xFF) shl 16) or
                    ((message.payload[3].toInt() and 0xFF) shl 24)
            val baseMode = message.payload[6].toInt() and 0xFF
            // MAV_MODE_FLAG_SAFETY_ARMED = 0x80
            armed = (baseMode and 0x80) != 0
        }
        val mc = customMode
        _flightState.update { current ->
            current.updateHeartbeat(armed = armed, customMode = mc)
        }
        Timber.d("HEARTBEAT: custom_mode=$mc armed=$armed")
    }

    private fun processBattery(message: MavlinkMessage) {
        // BATTERY_STATUS wire layout:
        // id(1) battery_function(1) type(1) temperature(2) voltages[10](20) current_battery(2) ...
        // voltages[0] at offset 5 (uint16_t mV)
        if (message.payload.size >= 7) {
            val voltageMv = (message.payload[5].toInt() and 0xFF) or
                            ((message.payload[6].toInt() and 0xFF) shl 8)
            if (voltageMv in 1..0xFFFF && voltageMv != 0xFFFF) {
                val voltageV = voltageMv / 1000.0f
                _flightState.update { current ->
                    current.updateTelemetry(battery = voltageV)
                        .copy(lowBatteryWarning = voltageV < LOW_BATTERY_THRESHOLD_V)
                }
                Timber.d("BATTERY_STATUS: battery=%.2fV", voltageV)
            }
        }
    }

    private fun processSysStatus(message: MavlinkMessage) {
        // SYS_STATUS wire layout (ArduPilot):
        // sensors_present(4) sensors_enabled(4) sensors_health(4) load(2) voltage_battery(2)@14
        if (message.payload.size >= 16) {
            val voltageMv = (message.payload[14].toInt() and 0xFF) or
                            ((message.payload[15].toInt() and 0xFF) shl 8)
            if (voltageMv > 0) {
                val voltageV = voltageMv / 1000.0f
                _flightState.update { current ->
                    current.updateTelemetry(battery = voltageV)
                        .copy(lowBatteryWarning = voltageV < LOW_BATTERY_THRESHOLD_V)
                }
                Timber.d("SYS_STATUS: battery=%.2fV", voltageV)
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
            if (distance > -1f && distance < 1000f && !distance.isNaN()) { // sanity check + NaN guard
                val altitude = ((distance + 0.05f) * 10000f).roundToInt() / 10000.0f
                _flightState.update { current ->
                    current.updateTelemetry(altitude = altitude)
                }
            }
        }
    }

    private fun processGps(message: MavlinkMessage) {
        // GPS_RAW_INT wire layout (ArduPilot):
        // time_usec(8) fix_type(1) lat(4) lon(4) alt(4) eph(2)@20 epv(2)@22 vel(2)@24 cog(2)@26 ... sats(1)@29
        // Note: fix_type at offset 8 is skipped in our offset calculation because fields are sorted by size
        // Verified against live frames: eph@20, epv@22, vel@24, cog@26, satellites@29
        if (message.payload.size >= 30) {
            val fixType = message.payload[8].toInt() and 0xFF
            val eph = (message.payload[20].toInt() and 0xFF) or
                      ((message.payload[21].toInt() and 0xFF) shl 8)
            val hdop = eph / 100.0f

            val satellites = message.payload[29].toInt() and 0xFF

            Timber.d("GPS_RAW_INT: fix_type=%d eph=%d -> hdop=%.2f sats=%d", fixType, eph, hdop, satellites)

            _flightState.update { current ->
                current.updateTelemetry(
                    hdop = hdop,
                    satellites = satellites.coerceIn(0, 32),
                    gpsFixType = fixType
                )
            }
        }
    }

    private fun processGlobalPosition(message: MavlinkMessage) {
        // GLOBAL_POSITION_INT wire layout:
        // time_boot_ms(4) lat(4) lon(4) alt(4) relative_alt(4) vx(2) vy(2) vz(2) hdg(2)
        // relative_alt at offset 16 (int32_t, mm)
        if (message.payload.size >= 20) {
            val relativeAltMm = java.nio.ByteBuffer.wrap(message.payload, 16, 4)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN).int
            val altitude = relativeAltMm / 1000.0f // convert mm to meters

            // Also extract lat/lon for potential future use
            val lat = java.nio.ByteBuffer.wrap(message.payload, 4, 4)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN).int / 1e7
            val lon = java.nio.ByteBuffer.wrap(message.payload, 8, 4)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN).int / 1e7

            Timber.d("GLOBAL_POSITION_INT: lat=%.7f lon=%.7f relative_alt=%.2fm", lat, lon, altitude)

            if (altitude >= -100f && altitude < 10000f && !altitude.isNaN()) { // sanity check + NaN guard
                _flightState.update { current ->
                    current.updateTelemetry(altitude = altitude)
                }
            }
        }
    }

    /**
     * Link-health watchdog.
     *
     * The Bluetooth socket being open is *not* proof that the vehicle is alive.
     * Two independent signals are tracked:
     *
     *  - vehicle telemetry (any message from sys=1): silent for 5 s -> LINK ONLY
     *  - command responder state (STATUSTEXT from sys=10): silent for 3 s ->
     *    TRACKING OFFLINE
     *
     * Stale telemetry is reported, never force-disconnected: dropping the
     * socket while the aircraft is off just causes reconnect loops.
     */
    private fun startHealthWatchdog() {
        healthWatchdogJob = scope.launch {
            while (true) {
                delay(500)
                val now = System.currentTimeMillis()
                val state = _flightState.value
                if (!state.connectionState.isConnected()) continue

                val vehicleReference = state.lastVehicleRx.takeIf { it > 0 }
                    ?: connectedAtMs.takeIf { it > 0 }
                    ?: now
                val stale = now - vehicleReference > VEHICLE_STALE_MS

                val responder = when {
                    state.lastResponderRx > 0 ->
                        if (now - state.lastResponderRx > RESPONDER_OFFLINE_MS) {
                            ResponderStatus.OFFLINE
                        } else {
                            ResponderStatus.ONLINE
                        }
                    connectedAtMs > 0 && now - connectedAtMs > RESPONDER_OFFLINE_MS ->
                        ResponderStatus.OFFLINE
                    else -> ResponderStatus.UNKNOWN
                }

                if (state.telemetryStale != stale) {
                    if (stale) {
                        Timber.w(
                            "Vehicle telemetry stale (%dms since sys=1 message) - LINK ONLY",
                            now - vehicleReference
                        )
                    } else {
                        Timber.i("Vehicle telemetry recovered")
                    }
                }
                if (state.responderStatus != responder) {
                    when (responder) {
                        ResponderStatus.OFFLINE -> Timber.w(
                            "Tracking responder offline (%dms since sys=10 state)",
                            if (state.lastResponderRx > 0) now - state.lastResponderRx else -1
                        )
                        ResponderStatus.ONLINE -> Timber.i("Tracking responder online")
                        ResponderStatus.UNKNOWN -> Unit
                    }
                }

                val connection = (state.connectionState as? ConnectionState.Connected)
                    ?.copy(telemetryStale = stale)
                    ?: state.connectionState
                _flightState.update {
                    it.copy(
                        connectionState = connection,
                        telemetryStale = stale,
                        responderStatus = responder
                    )
                }

                commandTracker.tick(state, responder == ResponderStatus.ONLINE)
                    ?.let { emitFeedback(it) }
            }
        }
    }

    fun shutdown() {
        parseJob?.cancel()
        healthWatchdogJob?.cancel()
        parseChannel.close()
    }
}