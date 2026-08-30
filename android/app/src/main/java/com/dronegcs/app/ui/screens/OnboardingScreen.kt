package com.dronegcs.app.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dronegcs.app.R
import com.dronegcs.app.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    settingsViewModel: SettingsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onComplete: () -> Unit
) {
    var currentPage by remember { mutableStateOf(0) }
    val pages = listOf(
            OnboardingPage(
                title = "Welcome to Drone GCS",
                description = "Ground Control Station for your drone with real-time telemetry, video feed, and flight controls.",
                iconRes = R.drawable.ic_drone,
                color = MaterialTheme.colorScheme.primary
            ),
            OnboardingPage(
                title = "Bluetooth Connection",
                description = "Pair your flight controller (HC-05/06) via Bluetooth. The app will scan for bonded devices automatically.",
                iconRes = R.drawable.ic_bluetooth,
                color = MaterialTheme.colorScheme.primary
            ),
            OnboardingPage(
                title = "Camera & Video",
                description = "Use your phone's camera or connect to an RTSP stream from a wireless video transmitter on the drone.",
                iconRes = R.drawable.ic_videocam,
                color = MaterialTheme.colorScheme.secondary
            ),
            OnboardingPage(
                title = "Permissions Required",
                description = "The app needs access to Bluetooth, Location (for BT scanning), and Camera. Please grant these permissions when prompted.",
                iconRes = R.drawable.ic_location,
                color = MaterialTheme.colorScheme.tertiary
            ),
            OnboardingPage(
                title = "Ready to Fly",
                description = "All set! Connect to your flight controller, select a video source, and start flying.",
                iconRes = R.drawable.ic_check_circle,
                color = MaterialTheme.colorScheme.primary
            )
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Page indicator
            androidx.compose.foundation.layout.Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                pages.forEachIndexed { index, _ ->
                    Box(
                        modifier = Modifier
                            .size(if (index == currentPage) 24.dp else 8.dp, 8.dp)
                            .background(if (index == currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                            .animateContentSize()
                    )
                }
            }

            // Page content
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
            ) {
                pages[currentPage].let { page ->
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Icon
                        Box(
                            modifier = Modifier.size(120.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                shape = androidx.compose.foundation.shape.CircleShape,
                                color = page.color.copy(alpha = 0.15f),
                                modifier = Modifier.size(120.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = page.iconRes),
                                    contentDescription = null,
                                    tint = page.color,
                                    modifier = Modifier.size(60.dp)
                                )
                            }
                        }

                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(32.dp))

                        // Title
                        Text(
                            text = page.title,
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )

                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))

                        // Description
                        Text(
                            text = page.description,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                    }
                }
            }

            // Navigation buttons
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (currentPage > 0) {
                    Button(onClick = { currentPage-- }) {
                        Text("Back")
                    }
                } else {
                    androidx.compose.foundation.layout.Box(modifier = Modifier.weight(1f))
                }

                if (currentPage < pages.size - 1) {
                    Button(onClick = { currentPage++ }, colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )) {
                        Text("Next")
                    }
                } else {
                    Button(onClick = {
                        settingsViewModel.completeFirstRun()
                        onComplete()
                    }, colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )) {
                        Text("Get Started")
                    }
                }
            }
        }
    }
}

data class OnboardingPage(
    val title: String,
    val description: String,
    val iconRes: Int,
    val color: Color
)