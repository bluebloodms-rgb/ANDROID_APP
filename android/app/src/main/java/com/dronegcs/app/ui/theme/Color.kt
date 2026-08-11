package com.dronegcs.app.ui.theme

import androidx.compose.ui.graphics.Color

// Background dark: #0E0E0E
val BackgroundDark = Color(0xFF0E0E0E)

// Panel translucent: #333333 with alpha
val PanelTranslucent = Color(0x99333333)  // ~60% alpha

// Electric blue accent: #00BFFF
val ElectricBlue = Color(0xFF00BFFF)

// Safety orange: #FF8C00
val SafetyOrange = Color(0xFFFF8C00)

// Alert red: #FF4444
val AlertRed = Color(0xFFFF4444)

// Additional colors for Material3 theme
val PrimaryLight = ElectricBlue
val OnPrimaryLight = Color.White
val PrimaryContainerLight = ElectricBlue.copy(alpha = 0.2f)
val OnPrimaryContainerLight = ElectricBlue

val BackgroundLight = Color(0xFFF5F5F5)
val OnBackgroundLight = Color(0xFF1A1A1A)

val SurfaceLight = Color.White
val OnSurfaceLight = Color(0xFF1A1A1A)

// Dark theme colors
val PrimaryDark = ElectricBlue
val OnPrimaryDark = Color.White
val PrimaryContainerDark = ElectricBlue.copy(alpha = 0.3f)
val OnPrimaryContainerDark = Color.White

val BackgroundDarkTheme = BackgroundDark
val OnBackgroundDark = Color.White

val SurfaceDark = Color(0xFF1A1A1A)
val OnSurfaceDark = Color.White
