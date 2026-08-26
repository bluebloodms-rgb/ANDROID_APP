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

    // Auto-reconnect state
    private var autoReconnectJob: kotlinx.coroutines.Job? = null
    private var isManualDisconnect = false
    private val backoffDelays = listOf(1000L, 2000L, 4000L, 8000L, 16000L, 30000L)
    private var backoffIndex = 0

    init {
        loadBondedDevices()
        observeConnectionState()
        observeAutoReconnectSetting()
    }

    private fun loadBondedDevices() {
        viewModelScope.launch {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val adapter = bluetoothManager.adapter
            if (adapter != null) {
                val bondedDevices = adapter.bondedDevices
                val flightControllerDevices = bondedDevices.filter { device ->
                    // Filter for likely flight controller devices (HC-05, HC-06, etc.)
                    device.name?.let { name ->
                        name.contains("HC-", true) ||
                        name.contains("BT", true) ||
                        name.contains("Flight", true) ||
                        name.contains("Drone", true) ||
                        name.contains("MAV", true)
                    } ?: false
                }
                _availableDevices.value = flightControllerDevices.toList()
                Timber.d("Found ${flightControllerDevices.size} bonded flight controller devices")
            }
        }
    }

    private fun observeConnectionState() {
        viewModelScope.launch {
            mavlinkRepository.connectionState
                .collect { state ->
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

    private fun scheduleAutoReconnect() {
        cancelAutoReconnect()

        // Get the last connected device or first available
        val device = _connectedDevice.value ?: _availableDevices.value.firstOrNull()
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