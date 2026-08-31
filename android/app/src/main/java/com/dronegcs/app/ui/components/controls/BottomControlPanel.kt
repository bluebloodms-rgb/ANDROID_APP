package com.dronegcs.app.ui.components.controls

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dronegcs.app.R
import com.dronegcs.app.domain.model.Command
import com.dronegcs.app.domain.model.FlightState
import com.dronegcs.app.viewmodel.ConnectionViewModel

/**
 * Collapsible control panel for the mobile GCS:
 *   Row 1: PID inputs YAW1 (y1,d1,l1) + YAW2 (y2,d2,l2) + ROLL (r,dr,lr)
 *   Row 2: PID inputs THRUST (t,dt,lt) + SERVO (s,ds,ls)
 *   Row 3: Speed dropdown (12/19/22) + Target dropdown (Person/Car/Balloon/UAV, with icons)
 *
 * START/CANCEL/MANUAL/AUTO live in the always-visible ControlDock (no duplicates here),
 * and the panel is scrollable + never clipped: it slides in above the dock on the main screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomControlPanel(
    modifier: Modifier = Modifier.fillMaxWidth(),
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

    val initialized = flightState.initialized

    Surface(
        modifier = modifier,
        color = Color(0xCC151515),
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
        shadowElevation = 12.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
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

            // Row 3: Speed + Target dropdowns
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SpeedDropdown(flightState = flightState, connectionViewModel = connectionViewModel)
                TargetDropdown(flightState = flightState, connectionViewModel = connectionViewModel)
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

private data class TargetChoice(
    val cls: Int,
    val label: String,
    val iconRes: Int,
    val target: Command.SetClass.TargetClass
)

private val targetChoices = listOf(
    TargetChoice(0, "Person", R.drawable.ic_person, Command.SetClass.TargetClass.PERSON),
    TargetChoice(2, "Car", R.drawable.ic_car, Command.SetClass.TargetClass.CAR),
    TargetChoice(3, "Balloon", R.drawable.ic_balloon, Command.SetClass.TargetClass.BALLOON),
    TargetChoice(4, "UAV", R.drawable.ic_drone, Command.SetClass.TargetClass.UAV)
)

/**
 * Speed preset dropdown (12 / 19 / 22 m/s). Sends the chosen speed immediately.
 */
@Composable
private fun SpeedDropdown(
    flightState: FlightState,
    connectionViewModel: ConnectionViewModel
) {
    val options = listOf(12f, 19f, 22f)
    var menuOpen by remember { mutableStateOf(false) }
    val current = if (flightState.spd in options) flightState.spd else options.first()

    Box {
        OutlinedButton(
            onClick = { menuOpen = true },
            enabled = flightState.initialized,
            modifier = Modifier.height(38.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                text = "SPEED %.0f".format(current),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            options.forEach { s ->
                DropdownMenuItem(
                    text = { Text("%.0f m/s".format(s), fontWeight = FontWeight.Bold) },
                    onClick = {
                        menuOpen = false
                        connectionViewModel.sendSpeed(s)
                    }
                )
            }
        }
    }
}

/**
 * Target class dropdown (Person / Car / Balloon / UAV) with per-option icons.
 * Sends the chosen target class immediately.
 */
@Composable
private fun TargetDropdown(
    flightState: FlightState,
    connectionViewModel: ConnectionViewModel
) {
    var menuOpen by remember { mutableStateOf(false) }
    val current = targetChoices.firstOrNull { it.cls == flightState.cls } ?: targetChoices.first()

    Box {
        OutlinedButton(
            onClick = { menuOpen = true },
            enabled = flightState.initialized,
            modifier = Modifier.height(38.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(
                painter = painterResource(current.iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = current.label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            targetChoices.forEach { choice ->
                DropdownMenuItem(
                    leadingIcon = {
                        Icon(
                            painter = painterResource(choice.iconRes),
                            contentDescription = choice.label,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    text = { Text(choice.label) },
                    onClick = {
                        menuOpen = false
                        connectionViewModel.sendClass(choice.target)
                    }
                )
            }
        }
    }
}
