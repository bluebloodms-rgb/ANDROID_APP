package com.dronegcs.app.ui.components.hud

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Reticle drawn at the last video-tap position (mirrors the Windows app's cornerRect overlay).
 * positionFraction is normalized (0..1) relative to the parent bounds.
 */
@Composable
fun TapReticle(
    positionFraction: Offset?,
    modifier: Modifier = Modifier.fillMaxSize(),
    color: Color = Color(0xFF00FF78)
) {
    if (positionFraction == null) return
    Canvas(modifier = modifier) {
        val cx = size.width * positionFraction.x
        val cy = size.height * positionFraction.y
        val half = 26.dp.toPx()
        val arm = 11.dp.toPx()
        val stroke = 4.dp.toPx()

        // Four corner brackets around the tapped point.
        val corners = listOf(
            listOf(Offset(cx - half, cy - half), Offset(cx - half + arm, cy - half), Offset(cx - half, cy - half + arm)),
            listOf(Offset(cx + half, cy - half), Offset(cx + half - arm, cy - half), Offset(cx + half, cy - half + arm)),
            listOf(Offset(cx - half, cy + half), Offset(cx - half + arm, cy + half), Offset(cx - half, cy + half - arm)),
            listOf(Offset(cx + half, cy + half), Offset(cx + half - arm, cy + half), Offset(cx + half, cy + half - arm))
        )
        corners.forEach { (a, b, c) ->
            drawLine(color, a, b, strokeWidth = stroke)
            drawLine(color, a, c, strokeWidth = stroke)
        }
        drawCircle(
            color = color.copy(alpha = 0.6f),
            radius = 6.dp.toPx(),
            center = Offset(cx, cy),
            style = Stroke(width = 2.dp.toPx())
        )
    }
}