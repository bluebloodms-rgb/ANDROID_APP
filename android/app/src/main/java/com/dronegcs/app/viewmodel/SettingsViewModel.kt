package com.dronegcs.app.viewmodel

import androidx.camera.core.CameraSelector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dronegcs.app.data.datastore.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for Settings screen
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    // Settings state
    private val _btDeviceAddress = MutableStateFlow<String?>(null)
    val btDeviceAddress = _btDeviceAddress.asStateFlow()

    private val _btDeviceName = MutableStateFlow<String?>(null)
    val btDeviceName = _btDeviceName.asStateFlow()

    private val _baudRate = MutableStateFlow(115200)
    val baudRate = _baudRate.asStateFlow()

    private val _rtspUrl = MutableStateFlow<String?>(null)
    val rtspUrl = _rtspUrl.asStateFlow()

    private val _defaultCameraFacing = MutableStateFlow(CameraSelector.LENS_FACING_BACK)
    val defaultCameraFacing = _defaultCameraFacing.asStateFlow()

    private val _defaultSpeed = MutableStateFlow(19)
    val defaultSpeed = _defaultSpeed.asStateFlow()

    private val _defaultTargetClass = MutableStateFlow(0)
    val defaultTargetClass = _defaultTargetClass.asStateFlow()

    private val _autoReconnectEnabled = MutableStateFlow(true)
    val autoReconnectEnabled = _autoReconnectEnabled.asStateFlow()

    private val _isFirstRun = MutableStateFlow(true)
    val isFirstRun = _isFirstRun.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            try {
                val settings = settingsRepository.getAllSettings()
                _btDeviceAddress.value = settings.btDeviceAddress
                _btDeviceName.value = settings.btDeviceName
                _baudRate.value = settings.baudRate
                _rtspUrl.value = settings.rtspUrl
                _defaultCameraFacing.value = settings.defaultCameraFacing
                _defaultSpeed.value = settings.defaultSpeed
                _defaultTargetClass.value = settings.defaultTargetClass
                _autoReconnectEnabled.value = settings.autoReconnectEnabled
                _isFirstRun.value = settings.isFirstRun
            } catch (_: Exception) {
                // Handle error
            }
        }
    }

    fun updateBtDevice(address: String?, name: String?) {
        _btDeviceAddress.value = address
        _btDeviceName.value = name
        viewModelScope.launch {
            try {
                settingsRepository.setBtDeviceAddress(address)
                settingsRepository.setBtDeviceName(name)
            } catch (_: Exception) {
            }
        }
    }

    fun updateBaudRate(baud: Int) {
        _baudRate.value = baud
        viewModelScope.launch {
            try {
                settingsRepository.setBaudRate(baud)
            } catch (_: Exception) {
            }
        }
    }

    fun updateRtspUrl(url: String?) {
        _rtspUrl.value = url
        viewModelScope.launch {
            try {
                settingsRepository.setRtspUrl(url)
            } catch (_: Exception) {
            }
        }
    }

    fun updateDefaultCameraFacing(facing: Int) {
        _defaultCameraFacing.value = facing
        viewModelScope.launch {
            try {
                settingsRepository.setDefaultCameraFacing(facing)
            } catch (_: Exception) {
            }
        }
    }

    fun updateDefaultSpeed(speed: Int) {
        _defaultSpeed.value = speed
        viewModelScope.launch {
            try {
                settingsRepository.setDefaultSpeed(speed)
            } catch (_: Exception) {
            }
        }
    }

    fun updateDefaultTargetClass(targetClass: Int) {
        _defaultTargetClass.value = targetClass
        viewModelScope.launch {
            try {
                settingsRepository.setDefaultTargetClass(targetClass)
            } catch (_: Exception) {
            }
        }
    }

    fun updateAutoReconnectEnabled(enabled: Boolean) {
        _autoReconnectEnabled.value = enabled
        viewModelScope.launch {
            try {
                settingsRepository.setAutoReconnectEnabled(enabled)
            } catch (_: Exception) {
            }
        }
    }

    fun completeFirstRun() {
        _isFirstRun.value = false
        viewModelScope.launch {
            try {
                settingsRepository.setFirstRunComplete()
            } catch (_: Exception) {
            }
        }
    }
}
