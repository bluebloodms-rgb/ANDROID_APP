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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dronegcs.app.domain.model.ConnectionUiState
import com.dronegcs.app.domain.model.VideoSource
import com.dronegcs.app.ui.components.controls.BottomControlPanel
import com.dronegcs.app.ui.components.controls.Direction
import com.dronegcs.app.ui.components.controls.DirectionalPad
import com.dronegcs.app.ui.components.controls.PitchSlider
import com.dronegcs.app.ui.components.hud.CrosshairOverlay
import com.dronegcs.app.ui.components.hud.TapReticle
import com.dronegcs.app.ui.components.hud.TopBar
import com.dronegcs.app.ui.components.video.VideoSurface
import com.dronegcs.app.viewmodel.CameraViewModel
import com.dronegcs.app.viewmodel.ConnectionViewModel
import com.dronegcs.app.viewmodel.TelemetryViewModel
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Main GCS screen: full-screen video + telemetry + all flight controls.
 * Mirrors the Windows app once the drone is connected (features available for later debug):
 *   - Pitch/steer slider (left)
 *   - Zoom D-pad (right, 1x-10x)
 *   - Bottom control panel (PID, START/CANCEL, mode, speed, target)
 *   - Tap on video -> Pos:x,y (scaled to 1280x720) + reticle
 *   - Settings & camera-switch shortcuts
 */
@Composable
fun MainScreen(
    connectionViewModel: ConnectionViewModel = viewModel(),
    telemetryViewModel: TelemetryViewModel = viewModel(),
    cameraViewModel: CameraViewModel = viewModel(),
    onOpenSettings: () -> Unit = {}
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
        cameraViewModel.setVideoSource(VideoSource.PhoneCamera())
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

    // ---------- Local control state ----------
    var zoomValue by remember { mutableStateOf(flightState.zoom ?: 1f) }
    LaunchedEffect(flightState.zoom) { flightState.zoom?.let { zoomValue = it } }

    var tapPosition by remember { mutableStateOf<Offset?>(null) }
    var surfaceSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(tapPosition) {
        if (tapPosition != null) {
            delay(3000)
            tapPosition = null
        }
    }

    fun adjustZoom(dir: Direction) {
        val step = 1f
        zoomValue = when (dir) {
            Direction.UP, Direction.RIGHT -> (zoomValue + step).coerceAtMost(10f)
            Direction.DOWN, Direction.LEFT -> (zoomValue - step).coerceAtLeast(1f)
        }
        connectionViewModel.sendZoom(zoomValue)
    }

    val onVideoTap: (Float, Float) -> Unit = { x, y ->
        val w = surfaceSize.width.coerceAtLeast(1)
        val h = surfaceSize.height.coerceAtLeast(1)
        val fx = ((x / w) * 1280f).roundToInt()
        val fy = ((y / h) * 720f).roundToInt()
        if (isConnected) connectionViewModel.sendPosition(fx, fy)
        tapPosition = Offset(x / w, y / h)
    }

    // ---------- Layout ----------
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { surfaceSize = it }
    ) {
        VideoSurface(
            cameraViewModel = cameraViewModel,
            cameraPermissionGranted = cameraGranted,
            onTap = onVideoTap
        )

        CrosshairOverlay()
        TapReticle(positionFraction = tapPosition)

        TopBar(
            modifier = Modifier.align(Alignment.TopCenter),
            flightState = flightState,
            onConnectClick = {
                connectionViewModel.refreshBondedDevices()
                showConnectDialog = true
            },
            onDisconnectClick = { connectionViewModel.disconnect() }
        )

        // Settings + camera-switch shortcuts (top-right, under the telemetry bar)
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 60.dp, end = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OverlayIconButton(Icons.Default.Settings, "Settings", onOpenSettings)
            OverlayIconButton(Icons.Default.Cameraswitch, "Switch camera") { cameraViewModel.switchCamera() }
        }

        // Left: pitch/steer slider
        PitchSlider(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 6.dp)
                .width(56.dp)
                .fillMaxHeight(0.6f),
            pitch = flightState.pitch ?: 0f,
            onPitchChange = { connectionViewModel.sendPitch(it) },
            enabled = isConnected
        )

        // Right: zoom D-pad
        DirectionalPad(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 4.dp)
                .width(200.dp)
                .height(200.dp),
            zoom = zoomValue,
            onDirectionClick = { dir -> adjustZoom(dir) },
            onCenterClick = {
                zoomValue = 1f
                connectionViewModel.sendZoom(1f)
            },
            enabled = isConnected
        )

        // Bottom: full control panel
        BottomControlPanel(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(238.dp),
            connectionViewModel = connectionViewModel,
            flightState = flightState,
            isConnected = isConnected
        )

        if (isConnecting) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 64.dp),
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

@Composable
private fun OverlayIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color(0xAA000000),
        modifier = Modifier.size(40.dp)
    ) {
        IconButton(onClick = onClick, modifier = Modifier.fillMaxSize()) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }
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
