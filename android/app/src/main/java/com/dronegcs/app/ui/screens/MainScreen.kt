package com.dronegcs.app.ui.screens

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.text.KeyboardOptions
import com.dronegcs.app.domain.model.Command
import com.dronegcs.app.ui.theme.BackgroundDark
import com.dronegcs.app.ui.theme.PanelTranslucent
import com.dronegcs.app.ui.theme.PhosphorGreen
import com.dronegcs.app.ui.theme.AlertRed
import com.dronegcs.app.ui.theme.AvionicsAmber
import com.dronegcs.app.ui.theme.UiScaleContainer
import com.dronegcs.app.domain.model.ConnectionUiState
import com.dronegcs.app.domain.model.VideoSource
import com.dronegcs.app.ui.components.controls.BottomControlPanel
import com.dronegcs.app.ui.components.controls.PidFieldsState
import com.dronegcs.app.ui.components.controls.ControlDock
import com.dronegcs.app.ui.components.controls.PitchSlider
import com.dronegcs.app.ui.components.controls.ZoomPill
import com.dronegcs.app.ui.components.hud.CrosshairOverlay
import com.dronegcs.app.ui.components.hud.TapReticle
import com.dronegcs.app.ui.components.hud.TopBar
import com.dronegcs.app.ui.components.video.VideoSurface
import com.dronegcs.app.viewmodel.CameraViewModel
import com.dronegcs.app.viewmodel.ConnectionViewModel
import com.dronegcs.app.viewmodel.SettingsViewModel
import com.dronegcs.app.viewmodel.TelemetryViewModel
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Main GCS screen: full-screen video + telemetry + all flight controls.
 * Mirrors the Windows app once the drone is connected (features available for later debug):
 *   - Pitch/steer slider (left)
 *   - Zoom D-pad (right, 1x-10x)
 *   - Bottom control panel (PID, START/CANCEL, mode, speed, target)
 *   - Tap on video -> Pos:x,y (scaled to 1280x720) + reticle
 *   - RTSP shortcut (video icon) -> in-app RTSP configuration dialog
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    connectionViewModel: ConnectionViewModel = viewModel(),
    telemetryViewModel: TelemetryViewModel = viewModel(),
    cameraViewModel: CameraViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel()
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
    var showRtspDialog by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        cameraGranted = grants[Manifest.permission.CAMERA] == true || cameraGranted
        btGranted = bluetoothPermissionGranted(context)
        if (btGranted) connectionViewModel.refreshBondedDevices()
    }

    LaunchedEffect(Unit) {
        cameraViewModel.restoreSavedVideoSource()
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

    // Mobile-first collapsible panels
    var showPitchPanel by remember { mutableStateOf(false) }
    var showZoomPanel by remember { mutableStateOf(true) }
    var showControlPanel by remember { mutableStateOf(false) }
    // Hoisted PID field state: filled by BottomControlPanel, collected+cleared by START
    val pidFields = remember { PidFieldsState() }
    var displayPitch by remember { mutableStateOf(flightState.pitch ?: 0f) }
    var lastSentPitch by remember { mutableStateOf(0f) }
    LaunchedEffect(flightState.pitch) { flightState.pitch?.let { displayPitch = it } }

    var tapPosition by remember { mutableStateOf<Offset?>(null) }
    var surfaceSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(tapPosition) {
        if (tapPosition != null) {
            delay(3000)
            tapPosition = null
        }
    }

    fun changeZoom(delta: Float) {
        zoomValue = (zoomValue + delta).coerceIn(1f, 10f)
        cameraViewModel.setCameraZoom(zoomValue)   // zooms the local phone-camera preview
        connectionViewModel.sendZoom(zoomValue)    // drone camera zoom over MAVLink
    }

    fun resetZoom() {
        zoomValue = 1f
        cameraViewModel.setCameraZoom(1f)
        connectionViewModel.sendZoom(1f)
    }

    fun onPitchSliderChange(value: Float) {
        displayPitch = value
        // Throttle: only send when the value moved by >= 1° since the last send.
        // Sends are safe no-ops without a link, so no isConnected gate here.
        if (abs(value - lastSentPitch) >= 1f) {
            lastSentPitch = value
            connectionViewModel.sendPitch(value)
        }
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
    UiScaleContainer {
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

        // Scrim: when the bottom control panel is expanded, tapping anywhere
        // outside it closes it. Drawn behind the dock/panel (they come later),
        // so those stay interactive while everything else dismisses the panel.
        if (showControlPanel) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(BackgroundDark.copy(alpha = 0.55f))
                    .clickable(
                        indication = null,
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                    ) { showControlPanel = false }
            )
        }

        TopBar(
            modifier = Modifier.align(Alignment.TopCenter),
            flightState = flightState,
            onConnectClick = {
                connectionViewModel.refreshBondedDevices()
                showConnectDialog = true
            },
            onDisconnectClick = { connectionViewModel.disconnect() },
            onRtspClick = { showRtspDialog = true }
        )

        // Left edge handle: toggle the collapsible pitch slider
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .background(
                    PanelTranslucent,
                    RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp, bottomStart = 10.dp, bottomEnd = 0.dp)
                )
                .clickable { showPitchPanel = !showPitchPanel }
                .padding(vertical = 14.dp, horizontal = 2.dp)
        ) {
            Icon(
                imageVector = if (showPitchPanel) Icons.Default.ChevronLeft else Icons.Default.ChevronRight,
                contentDescription = "Toggle pitch slider",
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }

        AnimatedVisibility(
            visible = showPitchPanel,
            enter = slideInHorizontally(initialOffsetX = { -it }),
            exit = slideOutHorizontally(targetOffsetX = { -it }),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 34.dp)
        ) {
            PitchSlider(
                modifier = Modifier
                    .width(56.dp)
                    .fillMaxHeight(0.6f),
                pitch = displayPitch,
                onPitchChange = { onPitchSliderChange(it) },
                enabled = true  // offline-safe: sends are no-ops without a link
            )
        }

        // Right edge handle: toggle the collapsible zoom pill (like the pitch slider)
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .background(
                    PanelTranslucent,
                    RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp, bottomStart = 10.dp, bottomEnd = 0.dp)
                )
                .clickable { showZoomPanel = !showZoomPanel }
                .padding(vertical = 14.dp, horizontal = 2.dp)
        ) {
            Icon(
                imageVector = if (showZoomPanel) Icons.Default.ChevronRight else Icons.Default.ChevronLeft,
                contentDescription = "Toggle zoom control",
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }

        AnimatedVisibility(
            visible = showZoomPanel,
            enter = slideInHorizontally(initialOffsetX = { it }),
            exit = slideOutHorizontally(targetOffsetX = { it }),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 34.dp)
        ) {
            // Compact zoom pill (replaces the big D-pad). Enabled even when
            // disconnected: in phone-camera mode the local preview zooms too,
            // and the Zoom:x.x command is queued for the drone.
            ZoomPill(
                zoom = zoomValue,
                enabled = true,
                onZoomIn = { changeZoom(1f) },
                onZoomOut = { changeZoom(-1f) },
                onReset = { resetZoom() }
            )
        }

        // Bottom: collapsible control panel (slides up like the pitch slider)
        // + slim control dock. The dock chevron toggles the panel.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            AnimatedVisibility(
                visible = showControlPanel,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it })
            ) {
                BottomControlPanel(
                    connectionViewModel = connectionViewModel,
                    flightState = flightState,
                    isConnected = isConnected,
                    pidFields = pidFields
                )
            }
            ControlDock(
                modifier = Modifier.fillMaxWidth(),
                isConnected = isConnected,
                modeName = flightState.modeName,
                expanded = showControlPanel,
                // Server state drives the dock: Op:2 = operation in progress ->
                // START disabled; Md: echoes fill the MODE button label.
                startEnabled = flightState.op != 2,
                onStartClick = {
                    // Exactly like the Windows app: send START:TRUE with the filled
                    // PID values ("y1=..,d1=..,..."), then clear all the fields.
                    val pidValues = pidFields.pidValuesString()
                    connectionViewModel.sendStart(pidValues.ifBlank { null })
                    pidFields.clear()
                },
                onCancelClick = { connectionViewModel.sendCancel() },
                onModeClick = { mode -> connectionViewModel.sendMode(mode) },
                onExpandClick = { showControlPanel = !showControlPanel }
            )
        }

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
    } // Box
    } // UiScaleContainer

    if (showConnectDialog) {
        BluetoothConnectDialog(
            connectionViewModel = connectionViewModel,
            onDismiss = { showConnectDialog = false }
        )
    }

    if (showRtspDialog) {
        RtspConfigDialog(
            settingsViewModel = settingsViewModel,
            cameraViewModel = cameraViewModel,
            onDismiss = { showRtspDialog = false }
        )
    }
}

/**
 * RTSP configuration dialog (replaces the old Settings screen): enter the stream
 * URL, start streaming, or fall back to the phone back camera. Shows the live
 * source status (starting / buffering / streaming / error).
 */
@Composable
private fun RtspConfigDialog(
    settingsViewModel: SettingsViewModel,
    cameraViewModel: CameraViewModel,
    onDismiss: () -> Unit
) {
    val rtspUrl by settingsViewModel.rtspUrl.collectAsStateWithLifecycle()
    val videoSource by cameraViewModel.videoSource.collectAsStateWithLifecycle()
    val isPlaying by cameraViewModel.isRtspPlaying.collectAsStateWithLifecycle()
    val isBuffering by cameraViewModel.rtspBuffering.collectAsStateWithLifecycle()
    val rtspError by cameraViewModel.rtspError.collectAsStateWithLifecycle()
    var urlText by remember(rtspUrl) { mutableStateOf(rtspUrl ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("RTSP Stream") },
        text = {
            Column {
                val isError = videoSource is VideoSource.RtspStream && rtspError != null
                val status = when (videoSource) {
                    is VideoSource.RtspStream -> when {
                        rtspError != null -> "Stream error: $rtspError"
                        isBuffering -> "Buffering…"
                        isPlaying -> "Streaming"
                        else -> "Starting…"
                    }
                    is VideoSource.PhoneCamera -> "Source: phone camera (back)"
                    VideoSource.None -> "Source: none"
                }
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isError) Color(0xFFC62828) else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                TextField(
                    value = urlText,
                    onValueChange = { urlText = it },
                    label = { Text("RTSP URL") },
                    placeholder = { Text("rtsp://...") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        },
        // Action buttons live in the dialog's action row, which is always
        // visible regardless of screen height (on small landscape phones the
        // text area alone is too short to also fit them).
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { cameraViewModel.setVideoSource(VideoSource.PhoneCamera()) }) {
                    Text("Use camera")
                }
                Button(
                    enabled = urlText.trim().startsWith("rtsp://"),
                    onClick = {
                        settingsViewModel.updateRtspUrl(urlText.trim())
                        cameraViewModel.setVideoSource(VideoSource.RtspStream(urlText.trim()))
                    }
                ) { Text("Start stream") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
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
    onDismiss: () -> Unit
) {
    val devices by connectionViewModel.availableDevices.collectAsStateWithLifecycle()
    val uiState by connectionViewModel.uiState.collectAsStateWithLifecycle()
    // Re-read adapter state on every recomposition so toggling Bluetooth while
    // the dialog is open is picked up (Refresh also triggers this via devices).
    val bluetoothReady = connectionViewModel.isBluetoothReady()

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
