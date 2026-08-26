package com.dronegcs.app.ui.screens

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dronegcs.app.domain.model.ConnectionUiState
import com.dronegcs.app.ui.components.hud.CrosshairOverlay
import com.dronegcs.app.ui.components.hud.TopBar
import com.dronegcs.app.ui.components.video.VideoSurface
import com.dronegcs.app.viewmodel.CameraViewModel
import com.dronegcs.app.viewmodel.ConnectionViewModel
import com.dronegcs.app.viewmodel.TelemetryViewModel

/**
 * Phase-1 GCS screen: phone camera preview + telemetry bar + START/CANCEL.
 * Flight-controller / gimbal controls are intentionally omitted for now.
 */
@Composable
fun MainScreen(
    connectionViewModel: ConnectionViewModel = viewModel(),
    telemetryViewModel: TelemetryViewModel = viewModel(),
    cameraViewModel: CameraViewModel = viewModel()
) {
    val context = LocalContext.current
    val flightState by telemetryViewModel.flightState.collectAsStateWithLifecycle()
    val connectionUiState by connectionViewModel.uiState.collectAsStateWithLifecycle()

    val isConnected = connectionUiState is ConnectionUiState.Connected
    val isConnecting = connectionUiState is ConnectionUiState.Connecting

    // ---------- Runtime permissions ----------
    var cameraGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var btGranted by remember { mutableStateOf(bluetoothPermissionGranted(context)) }
    var showConnectDialog by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        cameraGranted = grants[Manifest.permission.CAMERA] == true || cameraGranted
        btGranted = bluetoothPermissionGranted(context)
        if (btGranted) connectionViewModel.refreshBondedDevices()
    }

    LaunchedEffect(Unit) {
        cameraViewModel.setVideoSource(
            com.dronegcs.app.domain.model.VideoSource.PhoneCamera()
        )
        val wanted = mutableListOf<String>()
        if (!cameraGranted) wanted += Manifest.permission.CAMERA
        if (Build.VERSION.SDK_INT >= 31 && !btGranted) {
            wanted += Manifest.permission.BLUETOOTH_CONNECT
        }
        if (Build.VERSION.SDK_INT <= 30 && !btGranted) {
            wanted += Manifest.permission.ACCESS_FINE_LOCATION
        }
        if (Build.VERSION.SDK_INT >= 33) {
            wanted += Manifest.permission.POST_NOTIFICATIONS
        }
        if (wanted.isNotEmpty()) {
            permissionLauncher.launch(wanted.toTypedArray())
        } else {
            connectionViewModel.refreshBondedDevices()
        }
    }

    // ---------- Layout ----------
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        VideoSurface(
            cameraViewModel = cameraViewModel,
            cameraPermissionGranted = cameraGranted
        )

        CrosshairOverlay()

        TopBar(
            modifier = Modifier.align(Alignment.TopCenter),
            flightState = flightState,
            onConnectClick = {
                connectionViewModel.refreshBondedDevices()
                showConnectDialog = true
            },
            onDisconnectClick = { connectionViewModel.disconnect() }
        )

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 32.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally)
        ) {
            Button(
                onClick = { connectionViewModel.sendStart() },
                enabled = isConnected,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
            ) {
                Text("START", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Button(
                onClick = { connectionViewModel.sendCancel() },
                enabled = isConnected,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
            ) {
                Text("CANCEL", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }

        if (isConnecting) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 76.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.height(18.dp).width(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = (connectionUiState as? ConnectionUiState.Connecting)?.deviceName
                        ?.let { "Connecting to $it…" } ?: "Connecting…",
                    color = Color.White,
                    fontSize = 13.sp
                )
            }
        }
    }

    if (showConnectDialog) {
        BluetoothConnectDialog(
            connectionViewModel = connectionViewModel,
            bluetoothReady = btGranted && connectionViewModel.isBluetoothReady(),
            onDismiss = { showConnectDialog = false }
        )
    }
}

private fun bluetoothPermissionGranted(context: android.content.Context): Boolean =
    if (Build.VERSION.SDK_INT >= 31) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    }

@Composable
private fun BluetoothConnectDialog(
    connectionViewModel: ConnectionViewModel,
    bluetoothReady: Boolean,
    onDismiss: () -> Unit
) {
    val devices by connectionViewModel.availableDevices.collectAsStateWithLifecycle()
    val uiState by connectionViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { connectionViewModel.refreshBondedDevices() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bluetooth Devices") },
        text = {
            when {
                !bluetoothReady -> {
                    Column {
                        Text(
                            "Bluetooth is off or permission is missing.\n" +
                                "Enable Bluetooth and grant the Nearby-devices permission, then refresh.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(12.dp))
                        TextButton(onClick = { connectionViewModel.refreshBondedDevices() }) {
                            Text("Refresh")
                        }
                    }
                }
                uiState is ConnectionUiState.Connecting -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(20.dp).width(20.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text("Connecting…")
                    }
                }
                devices.isEmpty() -> {
                    Column {
                        Text(
                            "No paired devices found.\n\nPair your flight controller in Android Bluetooth settings first.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(12.dp))
                        TextButton(onClick = { connectionViewModel.refreshBondedDevices() }) {
                            Text("Refresh")
                        }
                    }
                }
                else -> {
                    LazyColumn {
                        items(devices, key = { it.address }) { device ->
                            DeviceRow(device = device) {
                                connectionViewModel.connect(device)
                                onDismiss()
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun DeviceRow(device: BluetoothDevice, onClick: () -> Unit) {
    val name = try {
        device.name ?: "Unknown device"
    } catch (_: SecurityException) {
        "Unknown device"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp)
    ) {
        Text(text = name, fontWeight = FontWeight.Bold)
        Text(
            text = device.address,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
