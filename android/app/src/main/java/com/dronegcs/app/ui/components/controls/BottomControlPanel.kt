package com.dronegcs.app.ui.components.controls

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dronegcs.app.R
import com.dronegcs.app.domain.model.Command
import com.dronegcs.app.domain.model.FlightState
import com.dronegcs.app.viewmodel.ConnectionViewModel

/**
 * Bottom control panel mirroring the Windows ControlBar exactly:
 *   Row 1: PID inputs YAW1 (y1,d1,l1) + YAW2 (y2,d2,l2) + ROLL (r,dr,lr)
 *   Row 2: PID inputs THRUST (t,dt,lt) + SERVO (s,ds,ls)
 *   Row 3: START / CANCEL / MANUAL / AUTO
 *   Row 4: Speed (12, 19, 22) + Targets (Person, Car, Balloon, UAV)
 *
 * START sends "START:TRUE,<key=value,...>" for the filled PID fields and clears them
 * (identical to the Python ControlBar._get_pid_values behavior).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomControlPanel(
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .height(238.dp),
    connectionViewModel: ConnectionViewModel,
    flightState: FlightState,
    isConnected: Boolean
) {
    // ---- PID fields (Python protocol keys) ----
    var y1 by remember { mutableStateOf("") }  // kp_yaw1
    var d1 by remember { mutableStateOf("") }  // kd_yaw1
    var l1 by remember { mutableStateOf("") }  // limit_yaw1
    var y2 by remember { mutableStateOf("") }  // kp_yaw2
    var d2 by remember { mutableStateOf("") }  // kd_yaw2
    var l2 by remember { mutableStateOf("") }  // limit_yaw2
    var r by remember { mutableStateOf("") }   // kp_roll
    var dr by remember { mutableStateOf("") }  // kd_roll
    var lr by remember { mutableStateOf("") }  // limit_roll
    var t by remember { mutableStateOf("") }   // kp_thrust
    var dt by remember { mutableStateOf("") }  // kd_thrust
    var lt by remember { mutableStateOf("") }  // limit_thrust
    var s by remember { mutableStateOf("") }   // kp_srv
    var ds by remember { mutableStateOf("") }  // kd_srv
    var ls by remember { mutableStateOf("") }  // limit_srv

    fun buildPidStringAndClear(): String {
        val entries = linkedMapOf(
            "y1" to y1, "d1" to d1, "l1" to l1,
            "y2" to y2, "d2" to d2, "l2" to l2,
            "r" to r, "dr" to dr, "lr" to lr,
            "t" to t, "dt" to dt, "lt" to lt,
            "s" to s, "ds" to ds, "ls" to ls
        )
        val pid = entries.filterValues { it.isNotBlank() }
            .map { (k, v) -> "$k=${v.trim()}" }
            .joinToString(",")
        y1 = ""; d1 = ""; l1 = ""; y2 = ""; d2 = ""; l2 = ""
        r = ""; dr = ""; lr = ""; t = ""; dt = ""; lt = ""
        s = ""; ds = ""; ls = ""
        return pid
    }

    val initialized = flightState.initialized
    val startEnabled = initialized && flightState.op != 2
    val manualEnabled = initialized && flightState.md != 1
    val autoEnabled = initialized && flightState.md != 2

    Surface(
        modifier = modifier,
        color = Color(0xCC151515),
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
        shadowElevation = 12.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Row 1: YAW1, YAW2, ROLL
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PidGroup("YAW1", Modifier.weight(1f), listOf(
                    MiniPidFieldData("y1", y1) { y1 = it },
                    MiniPidFieldData("d1", d1) { d1 = it },
                    MiniPidFieldData("l1", l1) { l1 = it }
                ))
                PidGroup("YAW2", Modifier.weight(1f), listOf(
                    MiniPidFieldData("y2", y2) { y2 = it },
                    MiniPidFieldData("d2", d2) { d2 = it },
                    MiniPidFieldData("l2", l2) { l2 = it }
                ))
                PidGroup("ROLL", Modifier.weight(1f), listOf(
                    MiniPidFieldData("r", r) { r = it },
                    MiniPidFieldData("dr", dr) { dr = it },
                    MiniPidFieldData("lr", lr) { lr = it }
                ))
            }

            // Row 2: THRUST + SERVO (aligned to the same 1/3 columns)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PidGroup("THRUST", Modifier.weight(1f), listOf(
                    MiniPidFieldData("t", t) { t = it },
                    MiniPidFieldData("dt", dt) { dt = it },
                    MiniPidFieldData("lt", lt) { lt = it }
                ))
                PidGroup("SERVO", Modifier.weight(1f), listOf(
                    MiniPidFieldData("s", s) { s = it },
                    MiniPidFieldData("ds", ds) { ds = it },
                    MiniPidFieldData("ls", ls) { ls = it }
                ))
                Spacer(modifier = Modifier.weight(1f))
            }

            // Row 3: Mission buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                PanelButton("START", enabled = startEnabled, isPrimary = true) {
                    val pid = buildPidStringAndClear()
                    connectionViewModel.sendStart(pid.ifEmpty { null })
                }
                PanelButton("CANCEL", enabled = initialized, isDestructive = true) {
                    connectionViewModel.sendCancel()
                }
                PanelButton("MANUAL", enabled = manualEnabled) {
                    connectionViewModel.sendMode(Command.SetMode.Mode.MANUAL)
                }
                PanelButton("AUTO", enabled = autoEnabled) {
                    connectionViewModel.sendMode(Command.SetMode.Mode.AUTO)
                }
            }

            // Row 4: Speed + Targets
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text("SPEED", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        SpeedButton("12", enabled = initialized && flightState.spd != 12f) { connectionViewModel.sendSpeed(12f) }
                        SpeedButton("19", enabled = initialized && flightState.spd != 19f) { connectionViewModel.sendSpeed(19f) }
                        SpeedButton("22", enabled = initialized && flightState.spd != 22f) { connectionViewModel.sendSpeed(22f) }
                    }
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text("TARGET", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TargetIconButton(R.drawable.ic_person, "Person", enabled = initialized && flightState.cls != 0) {
                            connectionViewModel.sendClass(Command.SetClass.TargetClass.PERSON)
                        }
                        TargetIconButton(R.drawable.ic_car, "Car", enabled = initialized && flightState.cls != 2) {
                            connectionViewModel.sendClass(Command.SetClass.TargetClass.CAR)
                        }
                        TargetIconButton(R.drawable.ic_balloon, "Balloon", enabled = initialized && flightState.cls != 3) {
                            connectionViewModel.sendClass(Command.SetClass.TargetClass.BALLOON)
                        }
                        TargetIconButton(R.drawable.ic_drone, "UAV", enabled = initialized && flightState.cls != 4) {
                            connectionViewModel.sendClass(Command.SetClass.TargetClass.UAV)
                        }
                    }
                }
            }
        }
    }
}

private data class MiniPidFieldData(
    val label: String,
    val value: String,
    val onValueChange: (String) -> Unit
)

@Composable
private fun PidGroup(
    label: String,
    modifier: Modifier = Modifier,
    fields: List<MiniPidFieldData>
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontSize = 7.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            fields.forEach { field ->
                MiniPidField(field.label, field.value, field.onValueChange)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MiniPidField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    TextField(
        value = value,
        onValueChange = { v ->
            if (v.length <= 6) onValueChange(v.filter { it.isDigit() || it == '.' || it == '-' })
        },
        label = { Text(label, fontSize = 6.sp, color = Color.Gray) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier
            .width(33.dp)
            .height(34.dp),
        textStyle = MaterialTheme.typography.bodySmall.copy(
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            color = Color.Black
        ),
        colors = TextFieldDefaults.textFieldColors(
            containerColor = Color(0xFFE8E8E8),
            focusedTextColor = Color.Black,
            unfocusedTextColor = Color.Black,
            cursorColor = MaterialTheme.colorScheme.primary,
            focusedLabelColor = Color.Gray,
            unfocusedLabelColor = Color.Gray,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent
        )
    )
}

@Composable
private fun PanelButton(
    text: String,
    enabled: Boolean = true,
    isPrimary: Boolean = false,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    val colors = when {
        isPrimary -> ButtonDefaults.buttonColors(
            containerColor = Color(0xFF2E7D32),
            contentColor = Color.White,
            disabledContainerColor = Color(0xFF2E7D32).copy(alpha = 0.35f)
        )
        isDestructive -> ButtonDefaults.buttonColors(
            containerColor = Color(0xFFC62828),
            contentColor = Color.White,
            disabledContainerColor = Color(0xFFC62828).copy(alpha = 0.35f)
        )
        else -> ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    }
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .width(62.dp)
            .height(32.dp),
        colors = colors,
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            fontSize = 9.sp
        )
    }
}

@Composable
private fun SpeedButton(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .width(34.dp)
            .height(30.dp),
        shape = RoundedCornerShape(6.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            fontSize = 9.sp
        )
    }
}

@Composable
private fun TargetIconButton(
    iconRes: Int,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(38.dp),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = label,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 6.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
