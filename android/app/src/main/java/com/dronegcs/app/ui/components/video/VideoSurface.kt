package com.dronegcs.app.ui.components.video

import androidx.camera.view.PreviewView
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.PlayerView
import com.dronegcs.app.R
import com.dronegcs.app.domain.model.VideoSource
import com.dronegcs.app.viewmodel.CameraViewModel

/**
 * Video surface composable that renders either CameraX preview or ExoPlayer RTSP stream
 */
@Composable
fun VideoSurface(
    modifier: Modifier = Modifier.fillMaxSize(),
    cameraViewModel: CameraViewModel,
    onTap: ((x: Float, y: Float) -> Unit)? = null
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val videoSource by cameraViewModel.videoSource.collectAsStateWithLifecycle()

    // PreviewView for CameraX
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    // PlayerView for RTSP
    var playerView by remember { mutableStateOf<PlayerView?>(null) }

    // Handle tap gestures on the surface
    val tapModifier = modifier
        .fillMaxSize()
        .pointerInput(Unit) {
            onTap?.let { callback ->
                detectTapGestures(
                    onTap = { offset ->
                        callback(offset.x, offset.y)
                    }
                )
            }
        }

    Box(modifier = tapModifier) {
        // CameraX PreviewView
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    scaleType = PreviewView.ScaleType.FIT_CENTER
                    previewView = this
                }
            },
            update = { view ->
                view.scaleType = PreviewView.ScaleType.FIT_CENTER
            },
            modifier = Modifier.fillMaxSize()
        )

        // ExoPlayer PlayerView
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    playerView = this
                }
            },
            update = { view ->
                view.useController = false
            },
            modifier = Modifier.fillMaxSize()
        )

        // Error overlay
        when (videoSource) {
            is VideoSource.PhoneCamera -> {
                val error by cameraViewModel.cameraError.collectAsStateWithLifecycle()
                val isActive by cameraViewModel.isCameraActive.collectAsStateWithLifecycle()

                if (error != null || !isActive) {
                    ErrorOverlay(
                        message = error ?: "Camera not available",
                        icon = Icons.Default.VideocamOff
                    )
                }
            }
            is VideoSource.RtspStream -> {
                val error by cameraViewModel.rtspError.collectAsStateWithLifecycle()
                val isPlaying by cameraViewModel.isRtspPlaying.collectAsStateWithLifecycle()
                val isBuffering by cameraViewModel.rtspBuffering.collectAsStateWithLifecycle()

                if (error != null || (!isPlaying && !isBuffering)) {
                    ErrorOverlay(
                        message = error ?: "Stream not available",
                        icon = Icons.Default.WifiOff
                    )
                } else if (isBuffering) {
                    BufferingOverlay()
                }
            }
            VideoSource.None -> {
                EmptyOverlay()
            }
        }
    }

    // Lifecycle handling for CameraX
    LaunchedEffect(videoSource, lifecycleOwner.lifecycle) {
        if (videoSource is VideoSource.PhoneCamera) {
            cameraViewModel.bindCameraPreview(
                previewView = previewView!!,
                lifecycleOwner = lifecycleOwner
            )
        }
    }

    // Lifecycle handling for RTSP
    LaunchedEffect(videoSource) {
        if (videoSource is VideoSource.RtspStream) {
            cameraViewModel.bindRtspPlayerView(playerView = playerView!!)
        }
    }

    // Cleanup on dispose
    DisposableEffect(Unit) {
        onDispose {
            cameraViewModel.releaseVideoResources()
        }
    }
}

@Composable
fun ErrorOverlay(
    message: String,
    icon: ImageVector
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.size(200.dp),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xCC000000),
            shadowElevation = 8.dp
        ) {
            Box(
                modifier = Modifier.padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun BufferingOverlay() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.size(120.dp),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xCC000000),
            shadowElevation = 8.dp
        ) {
            Box(
                modifier = Modifier.padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    androidx.compose.material3.CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Text(
                        text = "Buffering...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyOverlay() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.size(200.dp),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xCC000000),
            shadowElevation = 8.dp
        ) {
            Box(
                modifier = Modifier.padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_drone),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = "No video source selected",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
