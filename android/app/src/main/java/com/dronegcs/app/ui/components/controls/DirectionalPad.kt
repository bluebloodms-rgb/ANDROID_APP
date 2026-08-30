package com.dronegcs.app.ui.components.controls

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Directional Pad (D-Pad) for flight control
 * Right side of screen with cross layout: Up, Down, Left, Right, Center
 * Includes zoom badge on top
 */
@Composable
fun DirectionalPad(
    modifier: Modifier = Modifier
        .width(200.dp)
        .height(200.dp),
    zoom: Float = 1.0f,
    onDirectionClick: (Direction) -> Unit,
    onCenterClick: () -> Unit,
    enabled: Boolean = true
) {
    val buttonSize = 60.dp
    val centerSize = 80.dp
    val arrowColor = Color.White
    val buttonColor = Color(0xFFFF8C00) // Safety Orange
    val centerGradient = Brush.radialGradient(
        colors = listOf(Color(0xFFFF8C00), Color(0xFFFFD700)) // Orange to Gold
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Zoom badge
            Surface(
                modifier = Modifier
                    .width(80.dp)
                    .height(32.dp),
                shape = RoundedCornerShape(16.dp),
                color = Color(0xCC000000),
                border = BorderStroke(1.dp, buttonColor)
            ) {
                Text(
                    text = "%.1fX".format(zoom),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFFFFD700), // Gold
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .fillMaxSize()
                        .wrapContentSize(Alignment.Center)
                )
            }

            // D-Pad grid
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Up button
                DirectionButton(
                    modifier = Modifier.size(buttonSize),
                    icon = Icons.Default.ArrowUpward,
                    onClick = { onDirectionClick(Direction.UP) },
                    enabled = enabled,
                    color = buttonColor,
                    iconColor = arrowColor
                )

                // Middle row: Left, Center, Right
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left button
                    DirectionButton(
                        modifier = Modifier.size(buttonSize),
                        icon = Icons.Default.ArrowBack,
                        onClick = { onDirectionClick(Direction.LEFT) },
                        enabled = enabled,
                        color = buttonColor,
                        iconColor = arrowColor
                    )

                    // Center button
                    Box(
                        modifier = Modifier.size(centerSize),
                        contentAlignment = Alignment.Center
                    ) {
                        Button(
                            onClick = onCenterClick,
                            enabled = enabled,
                            modifier = Modifier.fillMaxSize(),
                            shape = CircleShape,
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = Color.Transparent
                            )
                        ) {
                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                shape = CircleShape,
                                color = Color.Transparent
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(centerGradient, CircleShape)
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.RadioButtonChecked,
                                        contentDescription = "Center",
                                        tint = Color.White,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Right button
                    DirectionButton(
                        modifier = Modifier.size(buttonSize),
                        icon = Icons.Default.ArrowForward,
                        onClick = { onDirectionClick(Direction.RIGHT) },
                        enabled = enabled,
                        color = buttonColor,
                        iconColor = arrowColor
                    )
                }

                // Down button
                DirectionButton(
                    modifier = Modifier.size(buttonSize),
                    icon = Icons.Default.ArrowDownward,
                    onClick = { onDirectionClick(Direction.DOWN) },
                    enabled = enabled,
                    color = buttonColor,
                    iconColor = arrowColor
                )
            }
        }
    }
}

enum class Direction {
    UP, DOWN, LEFT, RIGHT
}

@Composable
fun DirectionButton(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
    color: Color,
    iconColor: Color
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = CircleShape,
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = color,
            disabledContainerColor = color.copy(alpha = 0.3f),
            contentColor = iconColor
        )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = "Direction",
            tint = iconColor,
            modifier = Modifier.size(28.dp)
        )
    }
}

