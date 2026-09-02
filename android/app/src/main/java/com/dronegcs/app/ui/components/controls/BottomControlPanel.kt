package com.dronegcs.app.ui.components.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.LaunchedEffect
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
import com.dronegcs.app.ui.theme.scaled
import com.dronegcs.app.viewmodel.ConnectionViewModel

/**
 * Hoisted PID field state so the ControlDock START button (in MainScreen) can
 * collect the filled values — exactly like the Windows app's _get_pid_values()
 * — and clear the fields after START is pressed.
 */
class PidFieldsState {
    val values = mutableStateMapOf(
        "y1" to "", "d1" to "", "l1" to "",
        "y2" to "", "d2" to "", "l2" to "",
        "r" to "", "dr" to "", "lr" to "",
        "t" to "", "dt" to "", "lt" to "",
        "s" to "", "ds" to "", "ls" to ""
    )

    private val order = listOf(
        "y1", "d1", "l1", "y2", "d2", "l2",
        "r", "dr", "lr", "t", "dt", "lt", "s", "ds", "ls"
    )

    /** "y1=1.2,d1=0.3,..." built from the filled fields only (Windows-identical). */
    fun pidValuesString(): String = order
        .mapNotNull { k -> values[k]?.takeIf { it.isNotBlank() }?.let { "$k=$it" } }
        .joinToString(",")

    fun clear() {
        order.forEach { values[it] = "" }
    }
}

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
    isConnected: Boolean,
    pidFields: PidFieldsState
) {
    // ---- PID fields (Python protocol keys) — hoisted in PidFieldsState ----
    fun f(key: String) = pidFields.values[key] ?: ""
    fun set(key: String, v: String) { pidFields.values[key] = v }

    val initialized = flightState.initialized

    Surface(
        modifier = modifier,
        color = Color(0xCC151515),
        shape = RoundedCornerShape(topStart = 18.dp.scaled(), topEnd = 18.dp.scaled()),
        shadowElevation = 12.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 8.dp.scaled(), vertical = 8.dp.scaled()),
            verticalArrangement = Arrangement.spacedBy(8.dp.scaled())
        ) {
            // Row 1: YAW1, YAW2, ROLL
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp.scaled())
            ) {
                PidGroup("YAW1", Modifier.weight(1f), listOf(
                    MiniPidFieldData("y1", f("y1")) { set("y1", it) },
                    MiniPidFieldData("d1", f("d1")) { set("d1", it) },
                    MiniPidFieldData("l1", f("l1")) { set("l1", it) }
                ))
                PidGroup("YAW2", Modifier.weight(1f), listOf(
                    MiniPidFieldData("y2", f("y2")) { set("y2", it) },
                    MiniPidFieldData("d2", f("d2")) { set("d2", it) },
                    MiniPidFieldData("l2", f("l2")) { set("l2", it) }
                ))
                PidGroup("ROLL", Modifier.weight(1f), listOf(
                    MiniPidFieldData("r", f("r")) { set("r", it) },
                    MiniPidFieldData("dr", f("dr")) { set("dr", it) },
                    MiniPidFieldData("lr", f("lr")) { set("lr", it) }
                ))
            }

            // Row 2: THRUST + SERVO (aligned to the same 1/3 columns)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp.scaled())
            ) {
                PidGroup("THRUST", Modifier.weight(1f), listOf(
                    MiniPidFieldData("t", f("t")) { set("t", it) },
                    MiniPidFieldData("dt", f("dt")) { set("dt", it) },
                    MiniPidFieldData("lt", f("lt")) { set("lt", it) }
                ))
                PidGroup("SERVO", Modifier.weight(1f), listOf(
                    MiniPidFieldData("s", f("s")) { set("s", it) },
                    MiniPidFieldData("ds", f("ds")) { set("ds", it) },
                    MiniPidFieldData("ls", f("ls")) { set("ls", it) }
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
            fontSize = 8.sp,
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

@Composable
private fun MiniPidField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    // BasicTextField (NOT M3 TextField): the Material3 text field ignores tiny
    // fixed sizes and stretches its container across the whole group, which
    // painted over the group labels and visually merged the PID rows.
    BasicTextField(
        value = value,
        onValueChange = { v ->
            if (v.length <= 6) onValueChange(v.filter { it.isDigit() || it == '.' || it == '-' })
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        textStyle = MaterialTheme.typography.bodySmall.copy(
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            color = Color.Black
        ),
        modifier = Modifier
            .size(width = 34.dp.scaled(), height = 38.dp.scaled())
            .background(Color(0xFFE8E8E8), RoundedCornerShape(4.dp.scaled()))
            .padding(horizontal = 3.dp),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = label,
                        fontSize = 7.sp,
                        color = Color(0xFF777777),
                        maxLines = 1
                    )
                }
                innerTextField()
            }
        }
    )
}

private data class TargetChoice(
    val cls: Int,
    val label: String,
    val iconRes: Int,
    val target: Command.SetClass.TargetClass
)

private val targetChoices = listOf(
    // Class numbers matching the Windows app: Balloon=0, Person=1, Car=2, Drone=3
    TargetChoice(1, "Person", R.drawable.ic_person, Command.SetClass.TargetClass.PERSON),
    TargetChoice(2, "Car", R.drawable.ic_car, Command.SetClass.TargetClass.CAR),
    TargetChoice(0, "Balloon", R.drawable.ic_balloon, Command.SetClass.TargetClass.BALLOON),
    TargetChoice(3, "Drone", R.drawable.ic_drone, Command.SetClass.TargetClass.DRONE)
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
    // Optimistic selection: update immediately on click; the FC echo (Spd:..)
    // replaces it when it arrives, and any echo clears the pending override.
    var pendingSpeed by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(flightState.spd) { if (flightState.spd != null) pendingSpeed = null }
    val current = pendingSpeed
        ?: flightState.spd?.takeIf { it in options }
        ?: options.first()

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
                        pendingSpeed = s
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
    // Optimistic selection: update immediately on click; the FC echo (Cls:..)
    // replaces it when it arrives, and any echo clears the pending override.
    var pendingTarget by remember { mutableStateOf<Command.SetClass.TargetClass?>(null) }
    LaunchedEffect(flightState.cls) { if (flightState.cls != null) pendingTarget = null }
    val current = targetChoices.firstOrNull { it.target == pendingTarget }
        ?: targetChoices.firstOrNull { it.cls == flightState.cls }
        ?: targetChoices.first()

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
                        pendingTarget = choice.target
                        connectionViewModel.sendClass(choice.target)
                    }
                )
            }
        }
    }
}
