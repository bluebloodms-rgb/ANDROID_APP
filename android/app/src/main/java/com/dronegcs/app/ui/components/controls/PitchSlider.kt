package com.dronegcs.app.ui.components.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Vertical pitch slider (-90..+90 degrees).
 *
 * Mobile-first: compact edge panel; the track fills the available height
 * (no fixed 300 dp), taps and drags map 1:1 onto the track, and the current
 * value is shown under the track. Send-throttling happens at the call site.
 *
 * Semantics: top of track = +90 (gimbal up, red), bottom = -90 (blue).
 */
@Composable
fun PitchSlider(
    modifier: Modifier = Modifier,
    pitch: Float,
    onPitchChange: (Float) -> Unit,
    enabled: Boolean = true
) {
    val minPitch = -90f
    val maxPitch = 90f

    // Always-current state inside gesture lambdas (no stale closures)
    val currentPitch by rememberUpdatedState(pitch)
    val currentEnabled by rememberUpdatedState(enabled)
    val currentOnChange by rememberUpdatedState(onPitchChange)

    var trackHeightPx by remember { mutableStateOf(1f) }
    val density = LocalDensity.current
    val thumbSizeDp = 34.dp
    val thumbSizePx = with(density) { thumbSizeDp.toPx() }

    Column(
        modifier = modifier.padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        StepButton(
            icon = Icons.Default.ExpandLess,
            description = "Increase pitch",
            enabled = currentEnabled && pitch < maxPitch
        ) { currentOnChange((currentPitch + 1f).coerceAtMost(maxPitch)) }

        Box(
            modifier = Modifier
                .weight(1f)
                .width(30.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    Brush.verticalGradient(
                        0f to Color(0xFFFF4444),   // +90 (top)
                        0.5f to Color(0xFF9B59B6),
                        1f to Color(0xFF00BFFF)    // -90 (bottom)
                    )
                )
                .pointerInput(currentEnabled) {
                    if (currentEnabled) {
                        detectTapGestures { offset ->
                            val frac = 1f - (offset.y / size.height.toFloat())
                            currentOnChange(minPitch + frac * (maxPitch - minPitch))
                        }
                    }
                }
                .pointerInput(currentEnabled) {
                    if (currentEnabled) {
                        detectVerticalDragGestures { change, dragAmount ->
                            change.consume()
                            val degPerPx = (maxPitch - minPitch) / trackHeightPx
                            currentOnChange(
                                (currentPitch - dragAmount * degPerPx).coerceIn(minPitch, maxPitch)
                            )
                        }
                    }
                }
                .onSizeChanged { trackHeightPx = it.height.toFloat().coerceAtLeast(1f) }
        ) {
            val frac = ((pitch - minPitch) / (maxPitch - minPitch)).coerceIn(0f, 1f)
            val travel = (trackHeightPx - thumbSizePx).coerceAtLeast(0f)
            val thumbY = with(density) { ((1f - frac) * travel).toDp() }
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = thumbY)
                    .size(thumbSizeDp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .padding(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color(0xFF00BFFF))
                )
            }
        }

        Text(
            text = "%.0f\u00b0".format(pitch),
            color = Color(0xFFFFD700),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )

        StepButton(
            icon = Icons.Default.ExpandMore,
            description = "Decrease pitch",
            enabled = currentEnabled && pitch > minPitch
        ) { currentOnChange((currentPitch - 1f).coerceAtLeast(minPitch)) }
    }
}

@Composable
private fun StepButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(34.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (enabled) Color.White else Color.White.copy(alpha = 0.35f),
            modifier = Modifier.size(22.dp)
        )
    }
}
