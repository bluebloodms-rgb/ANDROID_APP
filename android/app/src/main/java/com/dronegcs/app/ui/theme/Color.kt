package com.dronegcs.app.ui.theme

import androidx.compose.ui.graphics.Color

// ─── Night Ops avionics palette ────────────────────────────────────────────
// Carbon base + phosphor green (info/alive) + amber (flight-critical) +
// red (armed/danger only). Modeled after night-vision GCS/OSD conventions.

// Background: near-black carbon with a green cast
val BackgroundDark = Color(0xFF0A0E0C)

// Panel translucent: carbon glass
val PanelTranslucent = Color(0xB30D1411)  // ~70% alpha dark green-black

// Phosphor green: primary "system alive" color
val PhosphorGreen = Color(0xFF33FF88)
val PhosphorGreenDim = Color(0xFF1E9954)

// Legacy alias (still referenced in a few places)
val ElectricBlue = Color(0xFF00BFFF)

// Amber: flight-critical data (mode, speed, pitch)
val AvionicsAmber = Color(0xFFFFB300)
// Legacy alias
val SafetyOrange = Color(0xFFFF8C00)

// Alert red: armed / disconnect / STOP only
val AlertRed = Color(0xFFFF3D3D)

// Panel hairline borders / corner ticks
val PanelHairline = Color(0x3333FF88)      // 20% phosphor
val TextPrimary = Color(0xFFD8FFE9)        // green-white
val TextDim = Color(0xFF6FA98A)

// Additional colors for Material3 theme
val PrimaryLight = PhosphorGreen
val OnPrimaryLight = Color(0xFF04140B)
val PrimaryContainerLight = PhosphorGreen.copy(alpha = 0.2f)
val OnPrimaryContainerLight = PhosphorGreen

val BackgroundLight = Color(0xFFF5F5F5)
val OnBackgroundLight = Color(0xFF1A1A1A)

val SurfaceLight = Color.White
val OnSurfaceLight = Color(0xFF1A1A1A)

// Dark theme colors
val PrimaryDark = PhosphorGreen
val OnPrimaryDark = Color(0xFF04140B)
val PrimaryContainerDark = PhosphorGreen.copy(alpha = 0.3f)
val OnPrimaryContainerDark = PhosphorGreen

val BackgroundDarkTheme = BackgroundDark
val OnBackgroundDark = TextPrimary

val SurfaceDark = Color(0xFF101713)
val OnSurfaceDark = TextPrimary
