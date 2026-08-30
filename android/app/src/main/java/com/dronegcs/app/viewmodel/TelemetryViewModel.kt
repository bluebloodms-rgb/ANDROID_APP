package com.dronegcs.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dronegcs.app.data.mavlink.MavlinkFlightRepository
import com.dronegcs.app.domain.model.FlightState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import javax.inject.Inject

/**
 * ViewModel for flight telemetry data
 */
@HiltViewModel
class TelemetryViewModel @Inject constructor(
    private val mavlinkRepository: MavlinkFlightRepository
) : ViewModel() {

    // Flight state as StateFlow for Compose
    val flightState = mavlinkRepository.flightState
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = FlightState()
        )

    // Convenience getters for UI
    val battery = flightState.map { it.battery }
    val altitude = flightState.map { it.altitude }
    val hdop = flightState.map { it.hdop }
    val mode = flightState.map { it.modeName }
    val satellites = flightState.map { it.satellites }
    val isConnected = flightState.map { it.connectionState.isConnected() }
    val connectionState = flightState.map { it.connectionState }

    // Operation state
    val op = flightState.map { it.op }
    val st = flightState.map { it.st }
    val md = flightState.map { it.md }
    val cls = flightState.map { it.cls }
    val spd = flightState.map { it.spd }
    val pitch = flightState.map { it.pitch }
    val zoom = flightState.map { it.zoom }
    val can = flightState.map { it.can }
}