package com.dronegcs.app.ui.components.hud

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import com.dronegcs.app.R
import com.dronegcs.app.domain.model.ConnectionState
import com.dronegcs.app.domain.model.FlightState
import com.dronegcs.app.ui.theme.PanelTranslucent

/**
 * Top bar with tabs, telemetry widgets, and connection status
 */
@Composable
fun TopBar(
    modifier: Modifier = Modifier.fillMaxWidth(),
    flightState: FlightState,
    onTabSelected: (Int) -> Unit,
    onReconnectClick: () -> Unit,
    currentTab: Int = 0
) {
    val tabs = listOf("Camera", "Flight Controller")

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(top = 0.dp), // System insets handled by activity
        color = androidx.compose.ui.graphics.Color(0xCC1A1A1A),
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Tabs
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEachIndexed { index, title ->
                    val isSelected = currentTab == index
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .fillMaxHeight()
                            .weight(1f)
                            .clickable { onTabSelected(index) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (isSelected) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal
                        )
                        // Underline indicator
                        if (isSelected) {
                            androidx.compose.foundation.layout.Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.dp)
                                    .align(Alignment.BottomCenter)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                    }
                }
            }

            // Center: Connection status + Mode
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ConnectionStatusBadge(
                    isConnected = flightState.connectionState is ConnectionState.Connected,
                    isConnecting = flightState.connectionState is ConnectionState.Connecting,
                    deviceName = (flightState.connectionState as? ConnectionState.Connected)?.deviceName
                )

                ModeBadge(mode = flightState.modeName)
            }

            // Right: Telemetry widgets
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TelemetryWidget(
                    iconRes = R.drawable.ic_battery,
                    label = "BAT",
                    value = flightState.battery?.let { "%.1f".format(it) } ?: "---",
                    unit = "V"
                )
                TelemetryWidget(
                    iconRes = R.drawable.ic_satellite,
                    label = "SAT",
                    value = flightState.satellites?.toString() ?: "---"
                )
                TelemetryWidget(
                    iconRes = R.drawable.ic_altitude,
                    label = "ALT",
                    value = flightState.altitude?.let { "%.1f".format(it) } ?: "---",
                    unit = "m"
                )
                TelemetryWidget(
                    iconRes = R.drawable.ic_hdop,
                    label = "HDOP",
                    value = flightState.hdop?.let { "%.1f".format(it) } ?: "---"
                )
            }

            // Reconnect button (shown when disconnected)
            if (flightState.connectionState !is ConnectionState.Connected) {
                IconButton(
                    onClick = onReconnectClick,
                    modifier = Modifier.padding(start = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reconnect",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}