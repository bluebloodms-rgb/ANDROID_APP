package com.dronegcs.app.ui.components.hud

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.platform.LocalDensity
import com.dronegcs.app.ui.theme.PanelHairline
import com.dronegcs.app.ui.theme.PanelTranslucent
import com.dronegcs.app.ui.theme.PhosphorGreen

/**
 * Chamfered rectangle: top-left and bottom-right corners are cut at 45°,
 * the signature angular motif of the Night Ops HUD language.
 */
class ChamferShape(private val cut: Dp) : Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: LayoutDirection,
        density: androidx.compose.ui.unit.Density
    ): Outline {
        val c = with(density) { cut.toPx() }
        val path = Path().apply {
            moveTo(c, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width, size.height - c)
            lineTo(size.width - c, size.height)
            lineTo(0f, size.height)
            lineTo(0f, c)
            close()
        }
        return Outline.Generic(path)
    }
}

/**
 * HUD panel: chamfered carbon-glass surface with a hairline phosphor border
 * and a faint top-edge glow. The single container used by all overlay UI.
 */
@Composable
fun HudPanel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clip(ChamferShape(10.dp))
            .background(
                Brush.verticalGradient(
                    0f to PanelTranslucent,
                    1f to PanelTranslucent.copy(alpha = 0.85f)
                )
            )
            .border(1.dp, PanelHairline, ChamferShape(10.dp))
    ) {
        content()
    }
}

/**
 * Corner tick brackets (the L-shaped HUD marks). Drawn around [size].
 */
fun Modifier.hudCornerTicks(
    tickLen: Dp,
    color: Color = PhosphorGreen,
    thickness: Dp = 1.5.dp
): Modifier = drawBehind {
    val s = tickLen.toPx()
    val t = thickness.toPx()
    val w = size.width
    val h = size.height
    drawLine(color, Offset(0f, 0f), Offset(s, 0f), t)
    drawLine(color, Offset(0f, 0f), Offset(0f, s), t)
    drawLine(color, Offset(w, 0f), Offset(w - s, 0f), t)
    drawLine(color, Offset(w, 0f), Offset(w, s), t)
    drawLine(color, Offset(0f, h), Offset(s, h), t)
    drawLine(color, Offset(0f, h), Offset(0f, h - s), t)
    drawLine(color, Offset(w, h), Offset(w - s, h), t)
    drawLine(color, Offset(w, h), Offset(w, h - s), t)
}
