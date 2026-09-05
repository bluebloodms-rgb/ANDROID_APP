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
import com.dronegcs.app.BuildConfig

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
    }

    @Volatile private var vehicleSysId: Int? = null
    @Volatile private var vehicleCompId: Int? = null

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

    init {
        startParsing()
        startHeartbeatWatchdog()
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
                }
                _flightState.update { it.copy(connectionState = state, initialized = true) }
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
    suspend fun sendCommand(command: Command): Boolean {
        val frames = listOf(MavlinkProtocol.encodeStatustext(command.toStatustextString()))

        var allSent = false
        frames.forEach { frame ->
            val ok = sendFrameWithRetry(frame)
            if (ok && !allSent) allSent = true
            if (frame.size > 14) {
                val isCmd = frame[7].toInt() == MavlinkProtocol.MSG_ID_COMMAND_LONG
                Timber.d(if (isCmd) "Sent COMMAND_LONG frame (${frame.size} bytes)" else "Sent STATUSTEXT: ${command.toStatustextString()}")
            }
        }
        return allSent
    }

    /**
     * Send one raw MAVLink frame with retry logic: 5 attempts with 50ms delay
     */
    private suspend fun sendFrameWithRetry(frame: ByteArray): Boolean {
        repeat(5) { attempt ->
            try {
                link.sendRaw(frame)
                if (attempt > 0) Timber.d("Resent frame (attempt ${attempt + 1})")
                return true
            } catch (e: Exception) {
                Timber.w(e, "Send attempt ${attempt + 1} failed")
                if (attempt < 4) delay(50)
            }
        }
        Timber.e("Failed to send frame after 5 retries")
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
            processHeartbeat(message)
            return
        }
        val vsid = vehicleSysId
        if (vsid == null || message.sysId != vsid) {
            Timber.v("Ignored MAVLink msg id=%d from sys=%d comp=%d (not the vehicle)", message.msgId, message.sysId, message.compId)
            return
        }
        when (message.msgId) {
            MavlinkProtocol.MSG_ID_STATUSTEXT -> {
                val text = MavlinkProtocol.decodeStatustextPayload(message.payload)
                if (text == null) {
                    Timber.w("STATUSTEXT with unparsable payload (len=%d)", message.payload.size)
                } else {
                    Timber.i("RX STATUSTEXT: %s", text)
                    processStatustext(text)
                }
            }
            MavlinkProtocol.MSG_ID_COMMAND_ACK -> processCommandAck(message)  // COMMAND_ACK
            0 -> processHeartbeat(message)        // HEARTBEAT
            1 -> processSysStatus(message)        // SYS_STATUS (ArduPilot battery voltage)
            24 -> processGps(message)             // GPS_RAW_INT
            33 -> processGlobalPosition(message)  // GLOBAL_POSITION_INT (relative_alt, lat, lon)
            147 -> processBattery(message)        // BATTERY_STATUS
            173 -> processRangefinder(message)    // RANGEFINDER (ArduPilot dialect)
            else -> Timber.v("Unhandled MAVLink message: ${message.msgId}")
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
        if (BuildConfig.DEBUG) Timber.d("[RX STATUSTEXT] $text")
        _flightState.update { current ->
            current.updateFromMessage(text)
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
        if (BuildConfig.DEBUG) Timber.d("HEARTBEAT: custom_mode=$mc armed=$armed")
    }

    private fun processBattery(message: MavlinkMessage) {
        // BATTERY_STATUS wire layout:
        // id(1) battery_function(1) type(1) temperature(2) voltages[10](20) current_battery(2) ...
        // voltages[0] at offset 5 (uint16_t mV)
        if (message.payload.size >= 7) {
            val voltageMv = (message.payload[5].toInt() and 0xFF) or
                            ((message.payload[6].toInt() and 0xFF) shl 8)
            if (voltageMv in 1..0xFFFF && voltageMv != 0xFFFF) {
                _flightState.update { current ->
                    current.updateTelemetry(battery = voltageMv / 1000.0f)
                }
                if (BuildConfig.DEBUG) Timber.d("BATTERY_STATUS: battery=%.2fV", voltageMv / 1000.0f)
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
                _flightState.update { current ->
                    current.updateTelemetry(battery = voltageMv / 1000.0f)
                }
                if (BuildConfig.DEBUG) Timber.d("SYS_STATUS: battery=%.2fV", voltageMv / 1000.0f)
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
        // GPS_RAW_INT wire layout (ArduPilot):
        // time_usec(8) fix_type(1) lat(4) lon(4) alt(4) eph(2)@20 epv(2)@22 vel(2)@24 cog(2)@26 ... sats(1)@29
        // Note: fix_type at offset 8 is skipped in our offset calculation because fields are sorted by size
        // Verified against live frames: eph@20, epv@22, vel@24, cog@26, satellites@29
        if (message.payload.size >= 30) {
            val eph = (message.payload[20].toInt() and 0xFF) or
                      ((message.payload[21].toInt() and 0xFF) shl 8)
            val hdop = eph / 100.0f

            val satellites = message.payload[29].toInt() and 0xFF

            if (BuildConfig.DEBUG) Timber.d("GPS_RAW_INT: eph=%d -> hdop=%.2f sats=%d", eph, hdop, satellites)

            _flightState.update { current ->
                current.updateTelemetry(
                    hdop = hdop,
                    satellites = satellites.coerceIn(0, 32)
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

            if (BuildConfig.DEBUG) Timber.d("GLOBAL_POSITION_INT: lat=%.7f lon=%.7f relative_alt=%.2fm", lat, lon, altitude)

            if (altitude >= -100f && altitude < 10000f) { // sanity check
                _flightState.update { current ->
                    current.updateTelemetry(altitude = altitude)
                }
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
                        vehicleSysId = null
                        vehicleCompId = null
                        current.copy(connectionState = com.dronegcs.app.domain.model.ConnectionState.Disconnected("Heartbeat timeout"))
                    } else {
                        current
                    }
                }
            }
        }
    }

    fun shutdown() {
        parseJob?.cancel()
        heartbeatWatchdogJob?.cancel()
        parseChannel.close()
    }
}