package com.dronegcs.app.ui.components.controls

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dronegcs.app.R
import com.dronegcs.app.viewmodel.ConnectionViewModel
import kotlinx.coroutines.launch

/**
 * Vertical pitch slider with gradient track (-90 to +90 degrees)
 * Left side of screen, 60% height, with +/- buttons
 */
@Composable
fun PitchSlider(
    modifier: Modifier = Modifier
        .width(56.dp)
        .fillMaxHeight(0.6f),
    pitch: Float,
    onPitchChange: (Float) -> Unit,
    enabled: Boolean = true
) {
    val minPitch = -90f
    val maxPitch = 90f
    val sliderHeight = 300.dp // Approximate 60% of screen
    val trackWidth = 24.dp
    val thumbSize = 48.dp

    // Animated thumb position
        val targetPitch = pitch
    val animatedValue by animateFloatAsState(
        targetValue = targetPitch,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 200f)
    )

    // Calculate thumb position (0 = top, 1 = bottom)
    val thumbProgress = (animatedValue - minPitch) / (maxPitch - minPitch)
    val thumbOffsetY = (sliderHeight - thumbSize) * (1 - thumbProgress)

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Up button (▲)
            Button(
                onClick = { onPitchChange((pitch + 1f).coerceAtMost(maxPitch)) },
                enabled = enabled && pitch < maxPitch,
                modifier = Modifier.width(48.dp).height(40.dp),
                shape = RoundedCornerShape(8.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.ExpandLess,
                    contentDescription = "Increase pitch",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Slider track with gradient
            Box(
                modifier = Modifier
                    .width(trackWidth)
                    .height(sliderHeight)
                    .pointerInput(Unit) {
                        if (enabled) {
                            detectDragGestures(
                                onDragStart = {},
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val dragDelta = dragAmount.y.toFloat()
                                    val pitchPerPixel = (maxPitch - minPitch) / (sliderHeight - thumbSize).toPx()
                                    val newPitch = (pitch - dragDelta * pitchPerPixel).coerceIn(minPitch, maxPitch)
                                    onPitchChange(newPitch)
                                },
                                onDragEnd = {}
                            )
                        }
                    }
            ) {
                // Gradient background
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color(0xFF00BFFF), // Electric Blue - top (-90)
                                0.5f to Color(0xFF9B59B6), // Purple - middle (0)
                                1f to Color(0xFFFF4444)  // Alert Red - bottom (+90)
                            )
                        )
                        .clip(RoundedCornerShape(12.dp))
                )

                // Markings
                Box(modifier = Modifier.fillMaxSize()) {
                    // -90° marking (top)
                    PitchMarking(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .padding(top = 4.dp),
                        text = "-90°",
                        color = Color.White
                    )
                    // 0° marking (center)
                    PitchMarking(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.Center)
                            .padding(horizontal = 8.dp),
                        text = "0°",
                        color = Color.White
                    )
                    // +90° marking (bottom)
                    PitchMarking(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 4.dp),
                        text = "+90°",
                        color = Color.White
                    )
                }

                // Thumb
                Box(
                    modifier = Modifier
                        .size(thumbSize)
                        .offset(y = thumbOffsetY)
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        shape = CircleShape,
                        color = Color.White,
                        shadowElevation = 4.dp,
                        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                // Hamburger icon
                                Box(
                                    modifier = Modifier
                                        .width(20.dp)
                                        .height(2.dp)
                                        .background(Color(0xFF333333))
                                )
                                Box(
                                    modifier = Modifier
                                        .width(20.dp)
                                        .height(2.dp)
                                        .background(Color(0xFF333333))
                                )
                                Box(
                                    modifier = Modifier
                                        .width(20.dp)
                                        .height(2.dp)
                                        .background(Color(0xFF333333))
                                )
                                // Pitch value
                                Text(
                                    text = "%.1f°".format(animatedValue),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }

            // Down button (▼)
            Button(
                onClick = { onPitchChange((pitch - 1f).coerceAtLeast(minPitch)) },
                enabled = enabled && pitch > minPitch,
                modifier = Modifier.width(48.dp).height(40.dp),
                shape = RoundedCornerShape(8.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = "Decrease pitch",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
fun PitchMarking(
    modifier: Modifier = Modifier,
    text: String,
    color: Color
) {
    Box(
        modifier = modifier
            .padding(horizontal = 4.dp)
            .wrapContentWidth(),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = Color(0xCC000000),
            modifier = Modifier.padding(2.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontSize = 9.sp,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
    }
}