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
import androidx.compose.material.icons.Icons
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

/**
 * Mobile-first slim bottom dock (replaces the always-visible Windows-style panel).
 *
 * [expand] | START | CANCEL | MODE
 *
 * The full PID / speed / target panel lives in a ModalBottomSheet opened by [onExpandClick];
 * MODE toggles MANUAL <-> AUTO in one tap.
 */
@Composable
fun ControlDock(
    modifier: Modifier = Modifier,
    isConnected: Boolean,
    modeName: String,
    onStartClick: () -> Unit,
    onCancelClick: () -> Unit,
    onModeClick: () -> Unit,
    onExpandClick: () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color(0xCC000000)
    ) {
        Row(
            modifier = Modifier
                .navigationBarsPadding()
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = onExpandClick,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = "More controls",
                    tint = Color(0xFFFFD700),
                    modifier = Modifier.size(28.dp)
                )
            }

            DockButton(
                text = "START",
                container = Color(0xFF1E8E3E),
                enabled = isConnected,
                onClick = onStartClick,
                modifier = Modifier.weight(1f)
            )
            DockButton(
                text = "CANCEL",
                container = Color(0xFFC5221F),
                enabled = isConnected,
                onClick = onCancelClick,
                modifier = Modifier.weight(1f)
            )
            DockButton(
                text = modeName,
                container = Color.Transparent,
                enabled = isConnected,
                border = BorderStroke(1.dp, Color(0xFFFF8C00)),
                textColor = Color(0xFFFFD700),
                onClick = onModeClick,
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
            .width(120.dp)
            .height(44.dp),
        shape = RoundedCornerShape(12.dp),
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
