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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
 * [expand] | START | CANCEL | MODE
 *
 * The full PID / speed / target panel slides up above this dock (slider-style),
 * toggled by [onExpandClick]; [expanded] flips the chevron. MODE toggles
 * MANUAL <-> AUTO in one tap.
 */
@Composable
fun ControlDock(
    modifier: Modifier = Modifier,
    isConnected: Boolean,
    modeName: String,
    expanded: Boolean = false,
    onStartClick: () -> Unit,
    onCancelClick: () -> Unit,
    onModeClick: (Command.SetMode.Mode) -> Unit,
    onExpandClick: () -> Unit
) {
    // Optimistic mode toggle: the FC never echoes "Md:" on this protocol
    // (verified in live logs), so without local state the button label would
    // never change. FC echo (Md:) still wins whenever it does arrive.
    var optimisticAuto by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(modeName) { optimisticAuto = null }
    val effectiveModeName = optimisticAuto?.let { if (it) "AUTO" else "MANUAL" } ?: modeName

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
                text = "START",
                container = PhosphorGreenDim,
                enabled = isConnected,
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
            DockButton(
                text = effectiveModeName,
                container = Color.Transparent,
                // Mode toggle stays usable even while disconnected (the label
                // flip is local/optimistic; the send is a harmless no-op when
                // offline). START/CANCEL remain connection-gated for safety.
                enabled = true,
                border = BorderStroke(1.dp, AvionicsAmber),
                textColor = AvionicsAmber,
                onClick = {
                    val nextAuto = effectiveModeName != "AUTO"
                    optimisticAuto = nextAuto
                    onModeClick(if (nextAuto) Command.SetMode.Mode.AUTO else Command.SetMode.Mode.MANUAL)
                },
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
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
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
