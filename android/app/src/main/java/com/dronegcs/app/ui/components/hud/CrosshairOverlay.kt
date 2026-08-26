package com.dronegcs.app.ui.components.hud

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Crosshair overlay for the video feed
 * Green crosshair with center circle as per the original design
 */
@Composable
fun CrosshairOverlay(
    modifier: Modifier = Modifier.fillMaxSize(),
    color: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color(0xFF00FF00),
    strokeWidth: Float = 2f
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerX = size.width / 2
            val centerY = size.height / 2
            val crosshairSize = 40.dp.toPx()
            val circleRadius = 20.dp.toPx()
            val gap = 10.dp.toPx()

            // Horizontal line (left)
            drawLine(
                color = color,
                start = androidx.compose.ui.geometry.Offset(centerX - crosshairSize, centerY),
                end = androidx.compose.ui.geometry.Offset(centerX - gap, centerY),
                strokeWidth = strokeWidth
            )

            // Horizontal line (right)
            drawLine(
                color = color,
                start = androidx.compose.ui.geometry.Offset(centerX + gap, centerY),
                end = androidx.compose.ui.geometry.Offset(centerX + crosshairSize, centerY),
                strokeWidth = strokeWidth
            )

            // Vertical line (top)
            drawLine(
                color = color,
                start = androidx.compose.ui.geometry.Offset(centerX, centerY - crosshairSize),
                end = androidx.compose.ui.geometry.Offset(centerX, centerY - gap),
                strokeWidth = strokeWidth
            )

            // Vertical line (bottom)
            drawLine(
                color = color,
                start = androidx.compose.ui.geometry.Offset(centerX, centerY + gap),
                end = androidx.compose.ui.geometry.Offset(centerX, centerY + crosshairSize),
                strokeWidth = strokeWidth
            )

            // Center circle
            drawCircle(
                color = Color.Transparent,
                center = androidx.compose.ui.geometry.Offset(centerX, centerY),
                radius = circleRadius,
                style = Stroke(width = strokeWidth)
            )
        }
    }
}