package com.dronegcs.app.viewmodel

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dronegcs.app.data.bluetooth.BluetoothSppService
import com.dronegcs.app.data.datastore.SettingsRepository
import com.dronegcs.app.data.mavlink.MavlinkFlightRepository
import com.dronegcs.app.domain.model.Command
import com.dronegcs.app.domain.model.ConnectionState
import com.dronegcs.app.domain.model.ConnectionUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.delay
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for managing Bluetooth connection with auto-reconnect
 */
@HiltViewModel
class ConnectionViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mavlinkRepository: MavlinkFlightRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    // UI State
    private val _uiState = MutableStateFlow<ConnectionUiState>(ConnectionUiState.Idle)
    val uiState = _uiState.asStateFlow()

    // Available bonded devices
    private val _availableDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val availableDevices = _availableDevices.asStateFlow()

    // Current connected device
    private val _connectedDevice = MutableStateFlow<BluetoothDevice?>(null)
    val connectedDevice = _connectedDevice.asStateFlow()

    // Last device we attempted to connect to; used by auto-reconnect when a
    // connection attempt fails before ever succeeding
    private val _lastAttemptedDevice = MutableStateFlow<BluetoothDevice?>(null)

    // Auto-reconnect state
    private var autoReconnectJob: kotlinx.coroutines.Job? = null
    private var isManualDisconnect = false
    private val backoffDelays = listOf(1000L, 2000L, 4000L, 8000L, 16000L, 30000L)
    private var backoffIndex = 0

    init {
        observeConnectionState()
        observeAutoReconnectSetting()
        autoConnectOnLaunch()
    }

    /** True if device has a Bluetooth adapter and it is currently enabled. */
    fun isBluetoothReady(): Boolean {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        return manager.adapter?.isEnabled == true
    }

    /** (Re)loads ALL paired devices - no name filtering. Requires BLUETOOTH_CONNECT on API 31+. */
    fun refreshBondedDevices() {
        viewModelScope.launch {
            try {
                val bluetoothManager =
                    context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                val adapter = bluetoothManager.adapter
                if (adapter == null || !adapter.isEnabled) {
                    _availableDevices.value = emptyList()
                    Timber.w("Bluetooth adapter missing or disabled")
                    return@launch
                }
                val devices = adapter.bondedDevices
                    .sortedBy { it.name?.lowercase() ?: "~" }
                _availableDevices.value = devices
                Timber.d("Found ${devices.size} paired devices")
            } catch (e: SecurityException) {
                Timber.e(e, "Missing BLUETOOTH_CONNECT permission")
                _availableDevices.value = emptyList()
            }
        }
    }

    private fun observeConnectionState() {
        viewModelScope.launch {
            mavlinkRepository.connectionState
                .collect { state ->
                    Timber.d("Connection state -> %s", state)
                    val previousState = _uiState.value
                    _uiState.update { current ->
                        when (state) {
                            is ConnectionState.Disconnected -> {
                                // Trigger auto-reconnect if enabled and not manual disconnect
                                if (!isManualDisconnect && state.reason != "User disconnected") {
                                    scheduleAutoReconnect()
                                }
                                ConnectionUiState.Idle
                            }
                            is ConnectionState.Connecting -> ConnectionUiState.Connecting(state.deviceName)
                            is ConnectionState.Connected -> {
                                // Reset backoff on successful connection
                                backoffIndex = 0
                                cancelAutoReconnect()
                                _connectedDevice.value = findDeviceByAddress(state.deviceAddress)
                                    ?: _lastAttemptedDevice.value
                                persistLastDevice(state.deviceAddress, state.deviceName)
                                ConnectionUiState.Connected(state.deviceName, state.deviceAddress)
                            }
                            is ConnectionState.Error -> {
                                // Trigger auto-reconnect on error
                                if (!isManualDisconnect) {
                                    scheduleAutoReconnect()
                                }
                                ConnectionUiState.Error(state.message)
                            }
                        }
                    }
                }
        }
    }

    private fun observeAutoReconnectSetting() {
        viewModelScope.launch {
            settingsRepository.autoReconnectEnabled
                .collect { enabled ->
                    if (!enabled) {
                        cancelAutoReconnect()
                    } else if (_uiState.value is ConnectionUiState.Idle && _connectedDevice.value != null) {
                        // Re-enable auto-reconnect if we have a previous device
                        scheduleAutoReconnect()
                    }
                }
        }
    }

    private fun findDeviceByAddress(address: String): BluetoothDevice? {
        return _availableDevices.value.find { it.address == address }
    }

    /**
     * Windows parity: on app start, silently reconnect to the last-used device.
     * Skips when no device was saved, Bluetooth is off, auto-reconnect is
     * disabled in Settings, or the saved device is no longer bonded.
     */
    private fun autoConnectOnLaunch() {
        viewModelScope.launch {
            try {
                delay(600) // let the first frame compose before starting the service
                val enabled = settingsRepository.autoReconnectEnabled.first()
                if (!enabled) {
                    Timber.d("Auto-connect on launch skipped: disabled in settings")
                    return@launch
                }
                val address = settingsRepository.getBtDeviceAddress()
                if (address.isNullOrBlank()) {
                    Timber.d("Auto-connect on launch skipped: no saved device")
                    return@launch
                }
                if (!isBluetoothReady()) {
                    Timber.w("Auto-connect on launch skipped: Bluetooth adapter off")
                    return@launch
                }
                val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                val device = try {
                    manager.adapter?.bondedDevices?.firstOrNull { it.address == address }
                } catch (e: SecurityException) {
                    Timber.w(e, "Auto-connect on launch: missing BLUETOOTH_CONNECT permission")
                    null
                }
                if (device == null) {
                    Timber.w("Auto-connect on launch skipped: saved device $address not bonded")
                    return@launch
                }
                Timber.i("Auto-connect on launch: connecting to ${device.name ?: "?"} ($address)")
                connect(device)
            } catch (e: Exception) {
                Timber.w(e, "Auto-connect on launch failed")
            }
        }
    }

    /** Remembers the last successfully-connected device so it can be auto-connected on next launch. */
    private fun persistLastDevice(address: String, name: String?) {
        viewModelScope.launch {
            try {
                settingsRepository.setBtDeviceAddress(address)
                settingsRepository.setBtDeviceName(name)
            } catch (e: Exception) {
                Timber.w(e, "Failed to persist last device")
            }
        }
    }

    private fun scheduleAutoReconnect() {
        cancelAutoReconnect()

        // Retry the device we last connected to (or just attempted); only fall
        // back to the first bonded device if neither is known
        val device = _connectedDevice.value
            ?: _lastAttemptedDevice.value
            ?: _availableDevices.value.firstOrNull()
        device?.let {
            autoReconnectJob = viewModelScope.launch {
                var currentDelay = 0L
                while (backoffIndex < backoffDelays.size) {
                    currentDelay = backoffDelays[backoffIndex]
                    Timber.d("Auto-reconnect attempt ${backoffIndex + 1} in ${currentDelay}ms to ${device.name}")
                    delay(currentDelay)

                    // Check if still disconnected and auto-reconnect enabled
                    val currentState = _uiState.value
                    val autoReconnectEnabled = settingsRepository.autoReconnectEnabled.first()

                    if (currentState is ConnectionUiState.Idle && autoReconnectEnabled) {
                        connect(device)
                        // Wait to see if connection succeeds
                        delay(5000)
                        if (_uiState.value is ConnectionUiState.Connected) {
                            break
                        }
                    } else {
                        break
                    }

                    backoffIndex++
                }

                if (backoffIndex >= backoffDelays.size) {
                    Timber.w("Auto-reconnect failed after all attempts")
                }
            }
        }
    }

    private fun cancelAutoReconnect() {
        autoReconnectJob?.cancel()
        autoReconnectJob = null
        backoffIndex = 0
    }

    fun connect(device: BluetoothDevice) {
        isManualDisconnect = false
        cancelAutoReconnect()
        _lastAttemptedDevice.value = device
        _uiState.value = ConnectionUiState.Connecting(device.name)
        val intent = android.content.Intent(context, BluetoothSppService::class.java).apply {
            action = BluetoothSppService.ACTION_CONNECT
            putExtra(BluetoothSppService.EXTRA_DEVICE, device)
        }
        context.startForegroundService(intent)
    }

    fun disconnect() {
        isManualDisconnect = true
        cancelAutoReconnect()
        val intent = android.content.Intent(context, BluetoothSppService::class.java).apply {
            action = BluetoothSppService.ACTION_DISCONNECT
        }
        context.startService(intent)
    }

    fun reconnect() {
        isManualDisconnect = false
        cancelAutoReconnect()
        _connectedDevice.value?.let { device ->
            connect(device)
        } ?: run {
            _availableDevices.value.firstOrNull()?.let { device ->
                connect(device)
            }
        }
    }

    fun sendCommand(command: Command) {
        viewModelScope.launch {
            mavlinkRepository.sendCommand(command)
        }
    }

    // Convenience methods for UI
    fun sendStart(pidValues: String? = null) = sendCommand(Command.Start(pidValues))
    fun sendCancel() = sendCommand(Command.Cancel)
    fun sendMode(mode: Command.SetMode.Mode) = sendCommand(Command.SetMode(mode))
    fun sendSpeed(speed: Float) = sendCommand(Command.SetSpeed(speed))
    fun sendClass(targetClass: Command.SetClass.TargetClass) = sendCommand(Command.SetClass(targetClass))
    fun sendZoom(zoom: Float) = sendCommand(Command.SetZoom(zoom))
    fun sendPitch(pitch: Float) = sendCommand(Command.SetPitch(pitch))
    fun sendPosition(x: Int, y: Int) = sendCommand(Command.SendPosition(x, y))

    override fun onCleared() {
        super.onCleared()
        cancelAutoReconnect()
    }
}