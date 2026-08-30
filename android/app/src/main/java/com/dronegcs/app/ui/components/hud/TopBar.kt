package com.dronegcs.app.ui.components.hud

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Settings
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
    onSettingsClick: () -> Unit = {},
    onSwitchCameraClick: () -> Unit = {}
) {
    val connection = flightState.connectionState
    val isConnected = connection is ConnectionState.Connected
    val isConnecting = connection is ConnectionState.Connecting

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color(0xB3000000)
    ) {
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .height(52.dp)
                .padding(horizontal = 8.dp)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ConnectionStatusBadge(
                isConnected = isConnected,
                isConnecting = isConnecting,
                deviceName = (connection as? ConnectionState.Connected)?.deviceName
            )

            TelemetryWidget(
                iconRes = R.drawable.ic_battery,
                label = "BAT",
                value = flightState.battery?.let { "%.1f".format(it) } ?: "--",
                unit = "V"
            )
            TelemetryWidget(
                iconRes = R.drawable.ic_satellite,
                label = "SAT",
                value = flightState.satellites?.toString() ?: "--"
            )
            TelemetryWidget(
                iconRes = R.drawable.ic_altitude,
                label = "ALT",
                value = flightState.altitude?.let { "%.1f".format(it) } ?: "--",
                unit = "m"
            )
            TelemetryWidget(
                iconRes = R.drawable.ic_hdop,
                label = "HDOP",
                value = flightState.hdop?.let { "%.1f".format(it) } ?: "--"
            )

            Spacer(modifier = Modifier.width(4.dp))

            TextButton(
                onClick = if (isConnected) onDisconnectClick else onConnectClick,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = if (isConnected) Color(0xFFFF5252) else Color(0xFF00BFFF)
                )
            ) {
                Text(
                    text = when {
                        isConnected -> "DISCONNECT"
                        isConnecting -> "CONNECTING…"
                        else -> "CONNECT"
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }

            IconButton(onClick = onSwitchCameraClick, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Default.Cameraswitch,
                    contentDescription = "Switch camera",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(onClick = onSettingsClick, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
