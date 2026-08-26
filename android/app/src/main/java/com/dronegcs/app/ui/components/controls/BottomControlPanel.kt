package com.dronegcs.app.ui.components.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dronegcs.app.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.dronegcs.app.domain.model.Command
import com.dronegcs.app.viewmodel.ConnectionViewModel
import kotlinx.coroutines.launch

/**
 * Bottom control panel with:
 * Row 1: PID Inputs (kp_yaw_2, kp_roll, kp_pitch)
 * Row 2: D-Gains (kd_yaw_1, kd_yaw_2, kd_roll) + Mission (Start, Cancel, Manual, Auto) + Speed (12, 19, 22)
 * Row 3: Limits (limit_yaw_1, limit_yaw_2, limit_roll) + Targets (Person, Car, Balloon, UAV)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomControlPanel(
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .height(150.dp),
    connectionViewModel: ConnectionViewModel,
    telemetryViewModel: com.dronegcs.app.viewmodel.TelemetryViewModel,
    enabled: Boolean = true
) {
    // PID Inputs state
    var kpYaw2 by remember { mutableStateOf("") }
    var kpRoll by remember { mutableStateOf("") }
    var kpPitch by remember { mutableStateOf("") }

    var kdYaw1 by remember { mutableStateOf("") }
    var kdYaw2 by remember { mutableStateOf("") }
    var kdRoll by remember { mutableStateOf("") }

    var limitYaw1 by remember { mutableStateOf("") }
    var limitYaw2 by remember { mutableStateOf("") }
    var limitRoll by remember { mutableStateOf("") }

    val isConnected = enabled

    Surface(
        modifier = modifier,
        color = Color(0xCC222222),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Row 1: PID Inputs
            PidInputRow(
                label = "KP",
                fields = listOf(
                    PidField("kp_yaw_2", kpYaw2, { kpYaw2 = it }),
                    PidField("kp_roll", kpRoll, { kpRoll = it }),
                    PidField("kp_pitch", kpPitch, { kpPitch = it })
                )
            )

            // Row 2: D-Gains + Mission + Speed
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // D-Gains
                PidInputRow(
                    modifier = Modifier.weight(1f),
                    label = "KD",
                    fields = listOf(
                        PidField("kd_yaw_1", kdYaw1, { kdYaw1 = it }),
                        PidField("kd_yaw_2", kdYaw2, { kdYaw2 = it }),
                        PidField("kd_roll", kdRoll, { kdRoll = it })
                    )
                )

                // Mission buttons
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("MISSION", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        MissionButton(
                            text = "START",
                            onClick = {
                                val pidValues = "$kpYaw2,$kpRoll,$kpPitch,$kdYaw1,$kdYaw2,$kdRoll,$limitYaw1,$limitYaw2,$limitRoll"
                                connectionViewModel.sendStart(pidValues)
                            },
                            enabled = isConnected,
                            isPrimary = true
                        )
                        MissionButton(
                            text = "CANCEL",
                            onClick = { connectionViewModel.sendCancel() },
                            enabled = isConnected,
                            isDestructive = true
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        MissionButton(
                            text = "MANUAL",
                            onClick = { connectionViewModel.sendMode(Command.SetMode.Mode.MANUAL) },
                            enabled = isConnected
                        )
                        MissionButton(
                            text = "AUTO",
                            onClick = { connectionViewModel.sendMode(Command.SetMode.Mode.AUTO) },
                            enabled = isConnected
                        )
                    }
                }

                // Speed buttons
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("SPEED", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        SpeedButton(text = "12", speed = 12f, onClick = { connectionViewModel.sendSpeed(12f) }, enabled = isConnected)
                        SpeedButton(text = "19", speed = 19f, onClick = { connectionViewModel.sendSpeed(19f) }, enabled = isConnected)
                        SpeedButton(text = "22", speed = 22f, onClick = { connectionViewModel.sendSpeed(22f) }, enabled = isConnected)
                    }
                }
            }

            // Row 3: Limits + Targets
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Limits
                PidInputRow(
                    modifier = Modifier.weight(1f),
                    label = "LIMIT",
                    fields = listOf(
                        PidField("limit_yaw_1", limitYaw1, { limitYaw1 = it }),
                        PidField("limit_yaw_2", limitYaw2, { limitYaw2 = it }),
                        PidField("limit_roll", limitRoll, { limitRoll = it })
                    )
                )

                // Target selectors
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("TARGET", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TargetSelector(
                            iconRes = R.drawable.ic_person,
                            label = "Person",
                            targetClass = Command.SetClass.TargetClass.PERSON,
                            onClick = { connectionViewModel.sendClass(Command.SetClass.TargetClass.PERSON) },
                            enabled = isConnected
                        )
                        TargetSelector(
                            iconRes = R.drawable.ic_car,
                            label = "Car",
                            targetClass = Command.SetClass.TargetClass.CAR,
                            onClick = { connectionViewModel.sendClass(Command.SetClass.TargetClass.CAR) },
                            enabled = isConnected
                        )
                        TargetSelector(
                            iconRes = R.drawable.ic_balloon,
                            label = "Balloon",
                            targetClass = Command.SetClass.TargetClass.BALLOON,
                            onClick = { connectionViewModel.sendClass(Command.SetClass.TargetClass.BALLOON) },
                            enabled = isConnected
                        )
                        TargetSelector(
                            iconRes = R.drawable.ic_drone,
                            label = "UAV",
                            targetClass = Command.SetClass.TargetClass.UAV,
                            onClick = { connectionViewModel.sendClass(Command.SetClass.TargetClass.UAV) },
                            enabled = isConnected
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PidInputRow(
    modifier: Modifier = Modifier.fillMaxWidth(),
    label: String,
    fields: List<PidField>
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            fields.forEach { field ->
                PidInputField(
                    label = field.label,
                    value = field.value,
                    onValueChange = field.onValueChange
                )
            }
        }
    }
}

data class PidField(
    val label: String,
    val value: String,
    val onValueChange: (String) -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PidInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier
            .width(70.dp)
            .height(36.dp),
        colors = TextFieldDefaults.textFieldColors(
            containerColor = Color(0xFFDDDDDD),
            focusedTextColor = Color.Black,
            unfocusedTextColor = Color.Black,
            focusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            cursorColor = MaterialTheme.colorScheme.primary
        ),
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            color = Color.Black,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
        )
    )
}

@Composable
fun MissionButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    isPrimary: Boolean = false,
    isDestructive: Boolean = false
) {
    val colors = when {
        isPrimary -> androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
        )
        isDestructive -> androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
            disabledContainerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.3f)
        )
        else -> androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    }

    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .width(60.dp)
            .height(32.dp),
        colors = colors,
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp
        )
    }
}

@Composable
fun SpeedButton(
    text: String,
    speed: Float,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .width(40.dp)
            .height(32.dp),
        shape = RoundedCornerShape(6.dp),
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp
        )
    }
}

@Composable
fun TargetSelector(
    iconRes: Int,
    label: String,
    targetClass: Command.SetClass.TargetClass,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(44.dp),
        shape = RoundedCornerShape(8.dp),
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = label,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 8.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}