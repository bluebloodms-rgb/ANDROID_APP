package com.dronegcs.app.ui.components.controls

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dronegcs.app.ui.theme.scaled
import androidx.compose.ui.unit.sp

/**
 * Compact vertical zoom control (replaces the 200 dp Windows-style D-pad).
 * [+ | 1.0X | − | reset] pill, docked at the screen edge so it never covers the video center.
 */
@Composable
fun ZoomPill(
    modifier: Modifier = Modifier,
    zoom: Float,
    enabled: Boolean = true,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onReset: () -> Unit
) {
    Surface(
        modifier = modifier.width(46.dp.scaled()),
        shape = RoundedCornerShape(22.dp.scaled()),
        color = Color(0xCC000000),
        border = BorderStroke(1.dp.scaled(), Color(0xFFFF8C00))
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(vertical = 4.dp.scaled())
        ) {
            PillIcon(Icons.Default.Add, "Zoom in", onZoomIn, enabled && zoom < 10f)
            Text(
                text = "%.1fX".format(zoom),
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFFFD700),
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
            PillIcon(Icons.Default.Remove, "Zoom out", onZoomOut, enabled && zoom > 1f)
            PillIcon(Icons.Default.Refresh, "Zoom reset", onReset, enabled)
        }
    }
}

@Composable
private fun PillIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
    enabled: Boolean
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(44.dp.scaled())) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (enabled) Color.White else Color.White.copy(alpha = 0.35f),
            modifier = Modifier.size(20.dp.scaled())
        )
    }
}
