package com.dronegcs.app.ui.theme

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Screen-size-aware scaling so the drone controls and HUD render correctly on
 * both small phones and large tablets, without clipping or being tiny.
 *
 * We pick a reference design size (1096 x 2616 dp, approx a modern tall phone)
 * and derive a scale from the actual dimensions, clamped to a sane range. Every
 * dp value that matters is multiplied by [uiScale] on the actual device.
 */
object UiScaleDefaults {
    const val REF_WIDTH = 411f
    const val REF_HEIGHT = 914f
    const val MIN_SCALE = 0.80f
    const val MAX_SCALE = 1.6f
    const val CLAMP_DP = 8f // smallest safe denominator to avoid div-by-zero
}

/** Derived per-device scale (unitless multiplier applied to dp values). */
@Immutable
data class UiScale(val value: Float)

/** CompositionLocal holding the current screen scale; defaults to 1.0. */
val LocalUiScale = staticCompositionLocalOf { UiScale(1f) }

/** Convenience: scale a dp value by the current [LocalUiScale]. */
@Composable
fun Dp.scaled(): Dp = this * LocalUiScale.current.value

/** Convenience: divide a dp value by the current scale (e.g. inverse padding). */
@Composable
fun Dp.invScaled(): Dp = this / LocalUiScale.current.value

/**
 * Wraps content and provides [LocalUiScale] computed from this layout's
 * constraints (width and height) vs the reference dimensions.
 *
 * Usage:
 *   UiScaleContainer { Unit; myScreen() }
 *
 * Inside, use `xx.dp.scaled()` for touch targets and text sizes so they grow
 * on tablets and shrink neatly on small phones.
 */
@Composable
fun UiScaleContainer(content: @Composable BoxWithConstraintsScope.() -> Unit) {
    BoxWithConstraints {
        val w = maxWidth.value
        val h = maxHeight.value
        val scale = if (w > 0f && h > 0f) {
            val byW = w / UiScaleDefaults.REF_WIDTH
            val byH = h / UiScaleDefaults.REF_HEIGHT
            // Use the smaller factor so small phones never get oversized controls.
            (minOf(byW, byH))
                .coerceIn(UiScaleDefaults.MIN_SCALE, UiScaleDefaults.MAX_SCALE)
        } else {
            1f
        }
        CompositionLocalProvider(LocalUiScale provides UiScale(scale)) {
            content()
        }
    }
}

/** Returns the current scale as a plain Float (for arithmetic in controllers). */
val currentScale: Float
    @Composable get() = LocalUiScale.current.value