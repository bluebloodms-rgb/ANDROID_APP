package com.dronegcs.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dronegcs.app.domain.model.Command
import com.dronegcs.app.viewmodel.ConnectionViewModel
import com.dronegcs.app.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsViewModel: SettingsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    connectionViewModel: ConnectionViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    cameraViewModel: com.dronegcs.app.viewmodel.CameraViewModel? = null,
    onNavigateBack: () -> Unit = {}
) {
    val btDeviceAddress by settingsViewModel.btDeviceAddress.collectAsStateWithLifecycle()
    val btDeviceName by settingsViewModel.btDeviceName.collectAsStateWithLifecycle()
    val baudRate by settingsViewModel.baudRate.collectAsStateWithLifecycle()
    val rtspUrl by settingsViewModel.rtspUrl.collectAsStateWithLifecycle()
    val defaultSpeed by settingsViewModel.defaultSpeed.collectAsStateWithLifecycle()
    val defaultTargetClass by settingsViewModel.defaultTargetClass.collectAsStateWithLifecycle()
    val autoReconnectEnabled by settingsViewModel.autoReconnectEnabled.collectAsStateWithLifecycle()
    val isFirstRun by settingsViewModel.isFirstRun.collectAsStateWithLifecycle()

    var baudRateText by remember { mutableStateOf(baudRate.toString()) }
    var rtspUrlText by remember { mutableStateOf(rtspUrl ?: "") }
    var speedText by remember { mutableStateOf(defaultSpeed.toString()) }

    val devices by connectionViewModel.availableDevices.collectAsStateWithLifecycle()

    // This screen owns its own ConnectionViewModel instance (per nav entry),
    // so refresh the bonded-device list when Settings is opened.
    LaunchedEffect(Unit) { connectionViewModel.refreshBondedDevices() }

    Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text("Settings", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) },
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Bluetooth Section
            SettingsSection(title = "Bluetooth", icon = Icons.Default.Bluetooth) {
                SettingsCard {
                    // Device selection
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SettingRow(
                            label = "Paired Device",
                            value = btDeviceName ?: btDeviceAddress ?: "None selected",
                            onClick = { /* show device picker */ }
                        )

                        Divider()

                        SettingRow(
                            label = "Baud Rate",
                            value = baudRate.toString(),
                            onClick = { /* show baud picker */ }
                        )
                    }

                    if (devices.isNotEmpty()) {
                        androidx.compose.material3.Text(text = "Available Devices:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
                        devices.forEach { device ->
                            androidx.compose.foundation.layout.Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                                    .clickable {
                                        if (btDeviceAddress == device.address) {
                                            // Tapping the already-selected device clears the saved selection
                                            settingsViewModel.updateBtDevice(null, null)
                                        } else {
                                            settingsViewModel.updateBtDevice(device.address, device.name)
                                        }
                                    },
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                androidx.compose.foundation.layout.Column {
                                    Text(device.name ?: "Unknown", style = MaterialTheme.typography.bodyMedium)
                                    Text(device.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (btDeviceAddress == device.address) {
                                    Icon(Icons.Default.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }

            // Video Section
            SettingsSection(title = "Video", icon = Icons.Default.Videocam) {
                SettingsCard {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SettingRow(
                            label = "RTSP Stream URL",
                            value = rtspUrl ?: "Not set",
                            onClick = { /* focus text field */ }
                        )

                        TextField(
                            value = rtspUrlText,
                            onValueChange = { rtspUrlText = it },
                            label = { Text("RTSP URL") },
                            placeholder = { Text("rtsp://...") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardActions = KeyboardActions(
                                onDone = { settingsViewModel.updateRtspUrl(rtspUrlText) }
                            )
                        )

                        Divider()

                        Text(
                            "Apply video source:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        androidx.compose.foundation.layout.Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    cameraViewModel?.setVideoSource(
                                        com.dronegcs.app.domain.model.VideoSource.PhoneCamera(
                                            facing = androidx.camera.core.CameraSelector.LENS_FACING_BACK
                                        )
                                    )
                                }
                            ) { Text("Back camera") }
                            Button(
                                enabled = rtspUrlText.startsWith("rtsp://"),
                                onClick = {
                                    settingsViewModel.updateRtspUrl(rtspUrlText)
                                    cameraViewModel?.setVideoSource(
                                        com.dronegcs.app.domain.model.VideoSource.RtspStream(rtspUrlText.trim())
                                    )
                                }
                            ) { Text("RTSP stream") }
                        }
                    }
                }
            }

            // Flight Section
            SettingsSection(title = "Flight Defaults", icon = Icons.Default.Speed) {
                SettingsCard {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SettingRow(
                            label = "Default Speed (m/s)",
                            value = defaultSpeed.toString(),
                            onClick = { /* focus text field */ }
                        )

                        TextField(
                            value = speedText,
                            onValueChange = { speedText = it },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardActions = KeyboardActions(
                                onDone = { speedText.toIntOrNull()?.let { settingsViewModel.updateDefaultSpeed(it) } }
                            )
                        )

                        Divider()

                        SettingRow(
                            label = "Default Target",
                            value = when (defaultTargetClass) {
                                0 -> "Person"
                                2 -> "Car"
                                3 -> "Balloon"
                                4 -> "UAV"
                                else -> "Person"
                            },
                            onClick = { /* show target picker */ }
                        )
                    }
                }
            }

            // General Section
            SettingsSection(title = "General", icon = Icons.Default.Settings) {
                SettingsCard {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        androidx.compose.foundation.layout.Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.foundation.layout.Column {
                                Text("Auto Reconnect", style = MaterialTheme.typography.bodyMedium)
                                Text("Automatically reconnect to flight controller", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = autoReconnectEnabled,
                                onCheckedChange = { settingsViewModel.updateAutoReconnectEnabled(it) },
                                colors = androidx.compose.material3.SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            )
                        }

                        Divider()

                        androidx.compose.foundation.layout.Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.foundation.layout.Column {
                                Text("First Run Complete", style = MaterialTheme.typography.bodyMedium)
                                Text("Onboarding has been shown", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = !isFirstRun,
                                onCheckedChange = { checked ->
                                    if (checked) settingsViewModel.completeFirstRun()
                                },
                                enabled = false,
                                colors = androidx.compose.material3.SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsSection(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
        content()
    }
}

@Composable
fun SettingsCard(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shadowElevation = 2.dp
    ) {
        content()
    }
}

@Composable
fun SettingRow(
    label: String,
    value: String,
    onClick: () -> Unit
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onClick() },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        androidx.compose.foundation.layout.Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(Icons.Default.Check, contentDescription = "", tint = MaterialTheme.colorScheme.primary)
        }
    }
}