package com.dronegcs.app.ui.components.hud

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dronegcs.app.ui.theme.ElectricBlue
import com.dronegcs.app.ui.theme.PanelTranslucent

/**
 * Individual telemetry widget for the top bar
 */
@Composable
fun TelemetryWidget(
    modifier: Modifier = Modifier,
    iconRes: Int,
    label: String,
    value: String,
    unit: String = "",
    color: androidx.compose.ui.graphics.Color = ElectricBlue
) {
    Surface(
        modifier = modifier
            .padding(horizontal = 4.dp, vertical = 4.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        color = PanelTranslucent,
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp)
            )
            Column {
                Text(
                    text = label,
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp
                )
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = value,
                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                        color = color,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    if (unit.isNotBlank()) {
                        Text(
                            text = unit,
                            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Connection status badge for top bar
 */
@Composable
fun ConnectionStatusBadge(
    isConnected: Boolean,
    isConnecting: Boolean,
    deviceName: String? = null
) {
    val (color, text) = when {
        isConnected -> androidx.compose.ui.graphics.Color(0xFF00BFFF) to "● $deviceName"
        isConnecting -> androidx.compose.ui.graphics.Color(0xFFFF8C00) to "◐ Connecting..."
        else -> androidx.compose.ui.graphics.Color(0xFFFF4444) to "○ Disconnected"
    }

    Surface(
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .height(28.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
        color = androidx.compose.ui.graphics.Color(0xCC000000),
        border = BorderStroke(1.dp, color)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.size(8.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(color)
            )
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = text,
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
        }
    }
}

/**
 * Mode badge showing flight mode
 */
@Composable
fun ModeBadge(mode: String) {
    val (color, bgColor) = when (mode.uppercase()) {
        "MANUAL" -> androidx.compose.ui.graphics.Color(0xFF00BFFF) to androidx.compose.ui.graphics.Color(0xCC00BFFF)
        "AUTO", "AUTOMATIC" -> androidx.compose.ui.graphics.Color(0xFFFF8C00) to androidx.compose.ui.graphics.Color(0xCCFF8C00)
        else -> androidx.compose.ui.graphics.Color(0xFF888888) to androidx.compose.ui.graphics.Color(0xCC888888)
    }

    Surface(
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .height(28.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
        color = bgColor
    ) {
        Text(
            text = mode,
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 10.dp).fillMaxHeight().wrapContentWidth()
        )
    }
}