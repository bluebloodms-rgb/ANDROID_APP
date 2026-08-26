package com.dronegcs.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import com.dronegcs.app.domain.model.Command
import com.dronegcs.app.domain.model.VideoSource
import com.dronegcs.app.ui.components.controls.BottomControlPanel
import com.dronegcs.app.ui.components.controls.DirectionalPad
import com.dronegcs.app.ui.components.controls.Direction
import com.dronegcs.app.ui.components.controls.PitchSlider
import com.dronegcs.app.ui.components.hud.CrosshairOverlay
import com.dronegcs.app.ui.components.hud.TopBar
import com.dronegcs.app.ui.components.video.VideoSurface
import com.dronegcs.app.viewmodel.CameraViewModel
import com.dronegcs.app.viewmodel.ConnectionViewModel
import com.dronegcs.app.viewmodel.TelemetryViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    connectionViewModel: ConnectionViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    telemetryViewModel: TelemetryViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    cameraViewModel: CameraViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val flightState by telemetryViewModel.flightState.collectAsStateWithLifecycle()
    val connectionUiState by connectionViewModel.uiState.collectAsStateWithLifecycle()
    val videoSource by cameraViewModel.videoSource.collectAsStateWithLifecycle()

    var expandedVideoMenu by remember { mutableStateOf(false) }
    var rtspUrl by remember { mutableStateOf("") }
    var showRtspInput by remember { mutableStateOf(false) }

    val isConnected = connectionUiState is com.dronegcs.app.domain.model.ConnectionUiState.Connected

    Scaffold(
        topBar = {
            TopBar(
                flightState = flightState,
                onTabSelected = { index ->
                    // Handle tab selection
                },
                onReconnectClick = {
                    connectionViewModel.reconnect()
                },
                currentTab = 0
            )
        },
        content = { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                // Video Surface (full screen background)
                VideoSurface(
                    cameraViewModel = cameraViewModel,
                    onTap = { x, y ->
                        connectionViewModel.sendPosition(x.toInt(), y.toInt())
                    }
                )

                // Crosshair overlay
                CrosshairOverlay()

                // Left: Pitch Slider
                PitchSlider(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 16.dp)
                        .fillMaxHeight(0.6f),
                    pitch = flightState.pitch ?: 0f,
                    onPitchChange = { pitch ->
                        connectionViewModel.sendPitch(pitch)
                    },
                    enabled = isConnected
                )

                // Right: Directional Pad
                DirectionalPad(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 16.dp)
                        .size(200.dp),
                    zoom = flightState.zoom ?: 1f,
                    onDirectionClick = { direction ->
                        when (direction) {
                            Direction.UP -> connectionViewModel.sendPitch((flightState.pitch ?: 0f) + 5f)
                            Direction.DOWN -> connectionViewModel.sendPitch((flightState.pitch ?: 0f) - 5f)
                            Direction.LEFT -> connectionViewModel.sendSpeed((flightState.spd ?: 19f) - 1f)
                            Direction.RIGHT -> connectionViewModel.sendSpeed((flightState.spd ?: 19f) + 1f)
                        }
                    },
                    onCenterClick = { connectionViewModel.sendCancel() },
                    enabled = isConnected
                )

                // Video source selector (top-right)
                VideoSourceSelector(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp),
                    videoSource = videoSource,
                    onVideoSourceSelected = { source ->
                        when (source) {
                            is VideoSource.RtspStream -> {
                                showRtspInput = true
                                rtspUrl = source.url
                            }
                            else -> {
                                cameraViewModel.setVideoSource(source)
                                showRtspInput = false
                            }
                        }
                    },
                    expanded = expandedVideoMenu,
                    onExpandedChange = { expandedVideoMenu = it },
                    onRtspUrlSubmit = { url ->
                        cameraViewModel.setVideoSource(VideoSource.RtspStream(url))
                        showRtspInput = false
                        expandedVideoMenu = false
                    },
                    rtspUrl = rtspUrl,
                    onRtspUrlChange = { rtspUrl = it },
                    showRtspInput = showRtspInput
                )
            }
        },
        bottomBar = {
            BottomControlPanel(
                modifier = Modifier.fillMaxWidth(),
                connectionViewModel = connectionViewModel,
                telemetryViewModel = telemetryViewModel,
                enabled = isConnected
            )
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoSourceSelector(
    modifier: Modifier = Modifier,
    videoSource: VideoSource,
    onVideoSourceSelected: (VideoSource) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onRtspUrlSubmit: (String) -> Unit,
    rtspUrl: String,
    onRtspUrlChange: (String) -> Unit,
    showRtspInput: Boolean
) {
    val sources = listOf(
        VideoSource.PhoneCamera(facing = androidx.camera.core.CameraSelector.LENS_FACING_BACK) to "Back Camera",
        VideoSource.PhoneCamera(facing = androidx.camera.core.CameraSelector.LENS_FACING_FRONT) to "Front Camera",
        VideoSource.RtspStream("") to "RTSP Stream"
    )

    var currentRtspUrl by remember { mutableStateOf(rtspUrl) }

    Surface(
        modifier = modifier,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        color = androidx.compose.ui.graphics.Color(0xCC000000),
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // Dropdown trigger
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth().clickable { onExpandedChange(!expanded) },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when (videoSource) {
                        is VideoSource.PhoneCamera -> if (videoSource.facing == androidx.camera.core.CameraSelector.LENS_FACING_BACK) "Back Camera" else "Front Camera"
                        is VideoSource.RtspStream -> "RTSP Stream"
                        else -> "No Source"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = "Expand",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            if (expanded) {
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    sources.forEach { (source, label) ->
                        androidx.compose.foundation.layout.Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    if (source is VideoSource.RtspStream) {
                                        onExpandedChange(true)
                                        // Will show RTSP input below
                                    } else {
                                        onVideoSourceSelected(source)
                                        onExpandedChange(false)
                                    }
                                },
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (videoSource == source) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    // RTSP URL input when RTSP is selected or expanded
                    if (showRtspInput || (videoSource is VideoSource.RtspStream)) {
                        TextField(
                            value = currentRtspUrl,
                            onValueChange = onRtspUrlChange,
                            label = { Text("RTSP URL") },
                            placeholder = { Text("rtsp://...") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = androidx.compose.material3.TextFieldDefaults.textFieldColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        )

                        androidx.compose.foundation.layout.Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Button(onClick = { onRtspUrlSubmit(currentRtspUrl) }) {
                                Text("Connect")
                            }
                        }
                    }
                }
            }
        }
    }
}