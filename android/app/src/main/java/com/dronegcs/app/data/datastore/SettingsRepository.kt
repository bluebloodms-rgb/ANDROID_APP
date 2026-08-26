package com.dronegcs.app.data.datastore

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DataStore repository for app settings persistence.
 */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>
) {

    // Keys
    private val btDeviceAddressKey = stringPreferencesKey("bt_device_address")
    private val btDeviceNameKey = stringPreferencesKey("bt_device_name")
    private val baudRateKey = intPreferencesKey("baud_rate")
    private val rtspUrlKey = stringPreferencesKey("rtsp_url")
    private val defaultCameraFacingKey = intPreferencesKey("default_camera_facing")
    private val defaultSpeedKey = intPreferencesKey("default_speed")
    private val defaultTargetClassKey = intPreferencesKey("default_target_class")
    private val autoReconnectEnabledKey = booleanPreferencesKey("auto_reconnect_enabled")
    private val firstRunKey = booleanPreferencesKey("first_run")

    // Defaults
    private val DEFAULT_BAUD_RATE = 115200
    private val DEFAULT_CAMERA_FACING = CameraSelector.LENS_FACING_BACK
    private val DEFAULT_SPEED = 19
    private val DEFAULT_TARGET_CLASS = 0 // Person

    // Auto-reconnect enabled
    val autoReconnectEnabled: Flow<Boolean> = dataStore.data
        .map { it[autoReconnectEnabledKey] ?: true }

    suspend fun setAutoReconnectEnabled(enabled: Boolean) {
        dataStore.edit { it[autoReconnectEnabledKey] = enabled }
    }

    // Bluetooth device address
    suspend fun getBtDeviceAddress(): String? =
        dataStore.data.first()[btDeviceAddressKey]

    suspend fun setBtDeviceAddress(address: String?) {
        dataStore.edit { prefs ->
            if (address != null) prefs[btDeviceAddressKey] = address else prefs.remove(btDeviceAddressKey)
        }
    }

    // Bluetooth device name
    suspend fun getBtDeviceName(): String? =
        dataStore.data.first()[btDeviceNameKey]

    suspend fun setBtDeviceName(name: String?) {
        dataStore.edit { prefs ->
            if (name != null) prefs[btDeviceNameKey] = name else prefs.remove(btDeviceNameKey)
        }
    }

    // Baud rate
    suspend fun getBaudRate(): Int =
        dataStore.data.first()[baudRateKey] ?: DEFAULT_BAUD_RATE

    suspend fun setBaudRate(baud: Int) {
        dataStore.edit { it[baudRateKey] = baud }
    }

    // RTSP URL
    suspend fun getRtspUrl(): String? =
        dataStore.data.first()[rtspUrlKey]

    suspend fun setRtspUrl(url: String?) {
        dataStore.edit { prefs ->
            if (url != null) prefs[rtspUrlKey] = url else prefs.remove(rtspUrlKey)
        }
    }

    // Default camera facing
    suspend fun getDefaultCameraFacing(): Int =
        dataStore.data.first()[defaultCameraFacingKey] ?: DEFAULT_CAMERA_FACING

    suspend fun setDefaultCameraFacing(facing: Int) {
        dataStore.edit { it[defaultCameraFacingKey] = facing }
    }

    // Default speed
    suspend fun getDefaultSpeed(): Int =
        dataStore.data.first()[defaultSpeedKey] ?: DEFAULT_SPEED

    suspend fun setDefaultSpeed(speed: Int) {
        dataStore.edit { it[defaultSpeedKey] = speed }
    }

    // Default target class
    suspend fun getDefaultTargetClass(): Int =
        dataStore.data.first()[defaultTargetClassKey] ?: DEFAULT_TARGET_CLASS

    suspend fun setDefaultTargetClass(targetClass: Int) {
        dataStore.edit { it[defaultTargetClassKey] = targetClass }
    }

    // First run
    suspend fun isFirstRun(): Boolean =
        dataStore.data.first()[firstRunKey] ?: true

    suspend fun setFirstRunComplete() {
        dataStore.edit { it[firstRunKey] = false }
    }

    // Get all settings as a single object for UI
    data class Settings(
        val btDeviceAddress: String?,
        val btDeviceName: String?,
        val baudRate: Int,
        val rtspUrl: String?,
        val defaultCameraFacing: Int,
        val defaultSpeed: Int,
        val defaultTargetClass: Int,
        val autoReconnectEnabled: Boolean,
        val isFirstRun: Boolean
    )

    suspend fun getAllSettings(): Settings {
        val prefs = dataStore.data.first()
        return Settings(
            btDeviceAddress = prefs[btDeviceAddressKey],
            btDeviceName = prefs[btDeviceNameKey],
            baudRate = prefs[baudRateKey] ?: DEFAULT_BAUD_RATE,
            rtspUrl = prefs[rtspUrlKey],
            defaultCameraFacing = prefs[defaultCameraFacingKey] ?: DEFAULT_CAMERA_FACING,
            defaultSpeed = prefs[defaultSpeedKey] ?: DEFAULT_SPEED,
            defaultTargetClass = prefs[defaultTargetClassKey] ?: DEFAULT_TARGET_CLASS,
            autoReconnectEnabled = prefs[autoReconnectEnabledKey] ?: true,
            isFirstRun = prefs[firstRunKey] ?: true
        )
    }
}
