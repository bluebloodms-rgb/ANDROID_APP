package com.dronegcs.app.ui.screens

import android.bluetooth.BluetoothDevice
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material3.CardDefaults
import com.dronegcs.app.domain.model.Command
import com.dronegcs.app.domain.model.ConnectionState
import com.dronegcs.app.domain.model.FlightState
import com.dronegcs.app.viewmodel.ConnectionViewModel
import com.dronegcs.app.viewmodel.TelemetryViewModel
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Phase1TestScreen(
    connectionViewModel: ConnectionViewModel = viewModel(),
    telemetryViewModel: TelemetryViewModel = viewModel()
) {
    val uiState by connectionViewModel.uiState.collectAsStateWithLifecycle()
    val flightState by telemetryViewModel.flightState.collectAsStateWithLifecycle()
    val devices by connectionViewModel.availableDevices.collectAsStateWithLifecycle()

    val connectionColor = when (uiState) {
        is com.dronegcs.app.domain.model.ConnectionUiState.Connected -> Color(0xFF00BFFF)
        is com.dronegcs.app.domain.model.ConnectionUiState.Connecting -> Color(0xFFFF8C00)
        is com.dronegcs.app.domain.model.ConnectionUiState.Error -> Color(0xFFFF4444)
        else -> Color(0xFF888888)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Phase 1: Bluetooth + MAVLink Test", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                actions = {
                    // Connection status indicator
                    Icon(
                        imageVector = when (uiState) {
                            is com.dronegcs.app.domain.model.ConnectionUiState.Connected -> Icons.Default.BluetoothConnected
                            is com.dronegcs.app.domain.model.ConnectionUiState.Connecting -> Icons.Default.BluetoothSearching
                            is com.dronegcs.app.domain.model.ConnectionUiState.Error -> Icons.Default.Error
                            else -> Icons.Default.BluetoothDisabled
                        },
                        contentDescription = "Connection status",
                        tint = connectionColor,
                        modifier = Modifier.padding(end = 16.dp)
                    )
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Connection Controls Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Connection", style = MaterialTheme.typography.titleLarge)

                    // Device list
                    if (devices.isNotEmpty()) {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(devices) { device ->
                                DeviceItem(device = device, onClick = {
                                    connectionViewModel.connect(device)
                                }, isConnected = uiState is com.dronegcs.app.domain.model.ConnectionUiState.Connected && device.address == (uiState as com.dronegcs.app.domain.model.ConnectionUiState.Connected).deviceAddress)
                            }
                        }
                    } else {
                        Text("No bonded flight controller devices found", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    // Connection buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        androidx.compose.foundation.layout.Box(modifier = Modifier.weight(1f)) {
                            Button(
                                onClick = { connectionViewModel.reconnect() },
                                enabled = uiState is com.dronegcs.app.domain.model.ConnectionUiState.Idle || uiState is com.dronegcs.app.domain.model.ConnectionUiState.Error,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(imageVector = Icons.Default.Refresh, contentDescription = "Reconnect")
                                androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(8.dp))
                                Text("Reconnect")
                            }
                        }

                        androidx.compose.foundation.layout.Box(modifier = Modifier.weight(1f)) {
                            Button(
                                onClick = { connectionViewModel.disconnect() },
                                enabled = uiState is com.dronegcs.app.domain.model.ConnectionUiState.Connected,
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(imageVector = Icons.Default.Stop, contentDescription = "Disconnect")
                                androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(8.dp))
                                Text("Disconnect")
                            }
                        }
                    }
                }
            }

            // Flight State Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Flight State", style = MaterialTheme.typography.titleLarge)

                    TelemetryRow("Battery", flightState.battery?.let { "%.1fV".format(it) } ?: "---")
                    TelemetryRow("Altitude", flightState.altitude?.let { "%.1fm".format(it) } ?: "---")
                    TelemetryRow("HDOP", flightState.hdop?.let { "%.1f".format(it) } ?: "---")
                    TelemetryRow("Mode", flightState.modeName)
                    TelemetryRow("Satellites", flightState.satellites?.toString() ?: "---")
                    TelemetryRow("Connection", flightState.connectionState.displayName)

                    // Operation state
                    androidx.compose.foundation.layout.Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        TelemetryValue("Op", flightState.op.toString())
                        TelemetryValue("St", flightState.st.toString())
                        TelemetryValue("Md", flightState.md.toString())
                        TelemetryValue("Cls", flightState.className)
                        TelemetryValue("Spd", "%.1f".format(flightState.spd))
                        TelemetryValue("Zoom", flightState.zoom?.let { "%.1f".format(it) } ?: "---")
                        TelemetryValue("Pitch", flightState.pitch?.let { "%.1f°".format(it) } ?: "---")
                        TelemetryValue("Can", flightState.can.toString())
                    }
                }
            }

            // Command Test Buttons
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Command Tests", style = MaterialTheme.typography.titleLarge)

                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TestButton("Manual", { connectionViewModel.sendMode(Command.SetMode.Mode.MANUAL) }, enabled = uiState is com.dronegcs.app.domain.model.ConnectionUiState.Connected, modifier = Modifier.weight(1f))
                        TestButton("Auto", { connectionViewModel.sendMode(Command.SetMode.Mode.AUTO) }, enabled = uiState is com.dronegcs.app.domain.model.ConnectionUiState.Connected, modifier = Modifier.weight(1f))
                    }

                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TestButton("Speed 12", { connectionViewModel.sendSpeed(12f) }, enabled = uiState is com.dronegcs.app.domain.model.ConnectionUiState.Connected, modifier = Modifier.weight(1f))
                        TestButton("Speed 19", { connectionViewModel.sendSpeed(19f) }, enabled = uiState is com.dronegcs.app.domain.model.ConnectionUiState.Connected, modifier = Modifier.weight(1f))
                        TestButton("Speed 22", { connectionViewModel.sendSpeed(22f) }, enabled = uiState is com.dronegcs.app.domain.model.ConnectionUiState.Connected, modifier = Modifier.weight(1f))
                    }

                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TestButton("Class: Person", { connectionViewModel.sendClass(Command.SetClass.TargetClass.PERSON) }, enabled = uiState is com.dronegcs.app.domain.model.ConnectionUiState.Connected, modifier = Modifier.weight(1f))
                        TestButton("Class: Car", { connectionViewModel.sendClass(Command.SetClass.TargetClass.CAR) }, enabled = uiState is com.dronegcs.app.domain.model.ConnectionUiState.Connected, modifier = Modifier.weight(1f))
                    }

                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TestButton("Zoom 1.0", { connectionViewModel.sendZoom(1.0f) }, enabled = uiState is com.dronegcs.app.domain.model.ConnectionUiState.Connected, modifier = Modifier.weight(1f))
                        TestButton("Zoom 5.0", { connectionViewModel.sendZoom(5.0f) }, enabled = uiState is com.dronegcs.app.domain.model.ConnectionUiState.Connected, modifier = Modifier.weight(1f))
                        TestButton("Zoom 10.0", { connectionViewModel.sendZoom(10.0f) }, enabled = uiState is com.dronegcs.app.domain.model.ConnectionUiState.Connected, modifier = Modifier.weight(1f))
                    }

                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TestButton("Pitch -90", { connectionViewModel.sendPitch(-90f) }, enabled = uiState is com.dronegcs.app.domain.model.ConnectionUiState.Connected, modifier = Modifier.weight(1f))
                        TestButton("Pitch 0", { connectionViewModel.sendPitch(0f) }, enabled = uiState is com.dronegcs.app.domain.model.ConnectionUiState.Connected, modifier = Modifier.weight(1f))
                        TestButton("Pitch +90", { connectionViewModel.sendPitch(90f) }, enabled = uiState is com.dronegcs.app.domain.model.ConnectionUiState.Connected, modifier = Modifier.weight(1f))
                    }

                    TestButton("Start (no PID)", { connectionViewModel.sendStart() }, enabled = uiState is com.dronegcs.app.domain.model.ConnectionUiState.Connected, modifier = Modifier.weight(1f))
                    TestButton("Cancel", { connectionViewModel.sendCancel() }, enabled = uiState is com.dronegcs.app.domain.model.ConnectionUiState.Connected, isDestructive = true, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceItem(
    device: BluetoothDevice,
    onClick: () -> Unit,
    isConnected: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = if (isConnected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        ),
        onClick = onClick
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = if (isConnected) Icons.Default.BluetoothConnected else Icons.Default.Bluetooth,
                contentDescription = "Device",
                tint = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column {
                Text(device.name ?: "Unknown", style = MaterialTheme.typography.titleMedium)
                Text(device.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))
            if (isConnected) {
                Text("CONNECTED", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun TelemetryRow(label: String, value: String) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
    }
}

@Composable
fun TelemetryValue(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestButton(text: String, onClick: () -> Unit, enabled: Boolean, isDestructive: Boolean = false, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = if (isDestructive) {
            androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            )
        } else {
            androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        },
        modifier = modifier.fillMaxWidth()
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}