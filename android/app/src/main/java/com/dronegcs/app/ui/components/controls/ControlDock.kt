package com.dronegcs.app.ui.components.controls

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import com.dronegcs.app.ui.components.hud.ChamferShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import com.dronegcs.app.domain.model.Command
import com.dronegcs.app.ui.theme.AlertRed
import com.dronegcs.app.ui.theme.AvionicsAmber
import com.dronegcs.app.ui.theme.PanelHairline
import com.dronegcs.app.ui.theme.PanelTranslucent
import com.dronegcs.app.ui.theme.PhosphorGreen
import com.dronegcs.app.ui.theme.PhosphorGreenDim
import com.dronegcs.app.ui.theme.scaled

/**
 * Mobile-first slim bottom dock (replaces the always-visible Windows-style panel).
 *
 * [expand] | START | CANCEL | SWITCH
 *
 * The full PID / speed / target panel slides up above this dock (slider-style),
 * toggled by [onExpandClick]; [expanded] flips the chevron.
 *
 * The mode DISPLAY chip (MANUAL/AUTO from the drone's Md: echo) lives in the
 * TOP BAR — this dock only hosts the SWITCH action button. SWITCH sends the
 * mode-change command; the displayed mode updates only from the drone's echo.
 */
@Composable
fun ControlDock(
    modifier: Modifier = Modifier,
    isConnected: Boolean,
    expanded: Boolean = false,
    startEnabled: Boolean = true,
    onStartClick: () -> Unit,
    onCancelClick: () -> Unit,
    onSwitchModeClick: () -> Unit,
    onExpandClick: () -> Unit
) {

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = PanelTranslucent
    ) {
        Row(
            modifier = Modifier
                .navigationBarsPadding()
                .fillMaxWidth()
                .height(64.dp.scaled())
                .padding(horizontal = 8.dp.scaled()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp.scaled())
        ) {
            IconButton(
                onClick = onExpandClick,
                modifier = Modifier.size(44.dp.scaled())
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                    contentDescription = "More controls",
                    tint = AvionicsAmber,
                    modifier = Modifier.size(28.dp.scaled())
                )
            }

            DockButton(
                // Server-driven lock: the drone's periodic "Op:.."/"Can:.." echo
                // (plus the local post-send latch) disable START while an
                // operation is running. Re-enabled only by the drone's echo.
                text = if (isConnected && !startEnabled) "RUNNING" else "START",
                container = PhosphorGreenDim,
                enabled = isConnected && startEnabled,
                onClick = onStartClick,
                modifier = Modifier.weight(1f)
            )
            DockButton(
                text = "CANCEL",
                container = AlertRed,
                enabled = isConnected,
                onClick = onCancelClick,
                modifier = Modifier.weight(1f)
            )
            // SWITCHER: sends the mode-change command (MAN<->AUTO); the displayed
            // mode is the MODE chip in the TOP BAR and updates only from the
            // drone's Md: echo.
            DockButton(
                text = "MAN↔AUTO",
                container = Color.Transparent,
                enabled = isConnected,
                border = BorderStroke(1.dp, AvionicsAmber),
                textColor = AvionicsAmber,
                onClick = onSwitchModeClick,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun DockButton(
    text: String,
    container: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    border: BorderStroke? = null,
    textColor: Color = Color.White
) {
    // --- Tap feedback animation -------------------------------------------
    // Material's ripple only shows while the finger is DOWN, so it is gone by
    // the time the user lifts it and cannot answer "what did I just tap?".
    // Instead we detect the press -> RELEASE transition and fire a short
    // pulse that plays AFTER lift-off: the button flashes bright (overlay +
    // glowing border) and does a quick scale down-up bounce (~320 ms).
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    var wasPressed by remember { mutableStateOf(false) }
    val pulse = remember { Animatable(0f) }

    LaunchedEffect(pressed) {
        if (!pressed && wasPressed) {
            // Released: play the pulse now (1 = fully lit, 0 = idle).
            pulse.snapTo(1f)
            pulse.animateTo(0f, animationSpec = tween(durationMillis = 320))
        }
        wasPressed = pressed
    }

    // Pressing squeezes slightly (also gives instant feedback before lift);
    // on release the pulse animates the flash on top of the rebound.
    val scale = if (pressed) 0.94f else 1f - 0.06f * pulse.value

    Box(
        modifier = modifier
            .wrapContentSize(align = Alignment.Center)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .drawWithContent {
                drawContent()
                // Bright wash over the whole button, fading out with the pulse.
                if (pulse.value > 0f) {
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.45f * pulse.value),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx())
                    )
                }
            }
    ) {
        // Glowing outline that only exists while the pulse is running.
        if (pulse.value > 0f) {
            Surface(
                modifier = Modifier.matchParentSize(),
                shape = ChamferShape(8.dp.scaled()),
                color = Color.Transparent,
                border = BorderStroke(2.dp, textColor.copy(alpha = pulse.value))
            ) {}
        }
        Button(
            onClick = onClick,
            enabled = enabled,
            interactionSource = interactionSource,
            modifier = Modifier
                .width(120.dp.scaled())
                .height(44.dp.scaled()),
            shape = ChamferShape(8.dp.scaled()),
            border = border,
            colors = ButtonDefaults.buttonColors(
                containerColor = container,
                contentColor = textColor,
                disabledContainerColor = container.copy(alpha = 0.35f),
                disabledContentColor = textColor.copy(alpha = 0.4f)
            )
        ) {
            Text(text = text, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
            if (!enabled) Spacer(Modifier.size(0.dp))
        }
    }
}
