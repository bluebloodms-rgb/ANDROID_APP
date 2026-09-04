package com.dronegcs.app.ui.components.hud

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dronegcs.app.R
import com.dronegcs.app.domain.model.ConnectionState
import com.dronegcs.app.domain.model.FlightState
import com.dronegcs.app.ui.theme.AlertRed
import com.dronegcs.app.ui.theme.AvionicsAmber
import com.dronegcs.app.ui.theme.PanelHairline
import com.dronegcs.app.ui.theme.PanelTranslucent
import com.dronegcs.app.ui.theme.PhosphorGreen
import com.dronegcs.app.ui.theme.PhosphorGreenDim
import com.dronegcs.app.ui.components.hud.ChamferShape
import com.dronegcs.app.ui.theme.BackgroundDark
import com.dronegcs.app.ui.theme.scaled

/**
 * Compact top telemetry bar: connection status, telemetry chips and connect button.
 * Handles status-bar insets so it never draws under system UI on any Android version.
 */
@Composable
fun TopBar(
    modifier: Modifier = Modifier,
    flightState: FlightState,
    onConnectClick: () -> Unit,
    onDisconnectClick: () -> Unit,
    onRtspClick: () -> Unit = {}
) {
    val connection = flightState.connectionState
    val isConnected = connection is ConnectionState.Connected
    val isConnecting = connection is ConnectionState.Connecting

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = BackgroundDark.copy(alpha = 0.85f)
    ) {
        // Small screens (Android 9 test phone, ~360 dp wide in landscape):
        // the badge + 4 chips + CONNECT + videocam do not fit, forcing a swipe.
        // Drop the HDOP chip (least critical) and tighten spacing on narrow
        // widths so everything fits without scrolling.
        BoxWithConstraints {
            // Compact below 560dp: covers small phones in landscape (e.g. a
            // 720p device at ~550-780dp) where the full-size row overflows.
            val narrow = maxWidth < 560.dp
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .height(52.dp)
                .padding(horizontal = if (narrow) 4.dp else 8.dp)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (narrow) 3.dp else 6.dp)
        ) {
            ConnectionStatusBadge(
                isConnected = isConnected,
                isConnecting = isConnecting,
                deviceName = (connection as? ConnectionState.Connected)?.deviceName,
                compact = narrow
            )

            // Flight mode chip (Guided / AltHold / ... from HEARTBEAT custom_mode)
            // TOP BAR ONLY — the bottom dock shows Manual/Auto from STATUSTEXT Md.
            flightState.fcModeName?.let { mode ->
                Surface(
                    shape = ChamferShape(6.dp.scaled()),
                    color = if (flightState.isArmed) PhosphorGreenDim else PanelHairline
                ) {
                    Text(
                        text = mode,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = Color(0xFFFFD54F),
                        modifier = Modifier.padding(horizontal = 10.dp.scaled(), vertical = 4.dp.scaled())
                    )
                }
            }

            TelemetryWidget(
                iconRes = R.drawable.ic_battery,
                label = "BAT",
                value = flightState.battery?.let { "%.1f".format(it) } ?: "--",
                unit = "V",
                compact = narrow
            )
            TelemetryWidget(
                iconRes = R.drawable.ic_satellite,
                label = "SAT",
                value = flightState.satellites?.toString() ?: "--",
                compact = narrow
            )
            TelemetryWidget(
                iconRes = R.drawable.ic_altitude,
                label = "ALT",
                value = flightState.altitude?.let { "%.1f".format(it) } ?: "--",
                unit = "m",
                compact = narrow
            )
            if (!narrow) {
                TelemetryWidget(
                    iconRes = R.drawable.ic_hdop,
                    label = "HDOP",
                    value = flightState.hdop?.let { "%.1f".format(it) } ?: "--"
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            TextButton(
                onClick = if (isConnected) onDisconnectClick else onConnectClick,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = if (isConnected) AlertRed else PhosphorGreen
                )
            ) {
                Text(
                    text = when {
                        isConnected -> "DISCONNECT"
                        isConnecting -> "CONNECTING…"
                        else -> "CONNECT"
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = if (narrow) 10.sp else 12.sp
                )
            }

            IconButton(onClick = onRtspClick, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Default.Videocam,
                    contentDescription = "Configure RTSP",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
}