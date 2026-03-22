package com.elishaazaria.sayboard.theme
import androidx.compose.ui.graphics.Color

// Primary palette - app logo blue
val Primary = Color(0xFF1F5DD7)
val PrimaryDark = Color(0xFF184CB0)
val PrimaryLight = Color(0xFF4A82E8)

// Surface colors - dark theme (neutral Samsung-like gray)
val DarkBackground = Color(0xFF1B1B1B)
val DarkSurface = Color(0xFF242424)
val DarkSurfaceVariant = Color(0xFF303030)

// Key colors - Samsung-style dark keyboard
val KeyBackground = Color(0xFF2C2C2C)         // Regular key background
val SpecialKeyBackground = Color(0xFF3A3A3A)  // Special key background (shift, backspace, etc.)
val KeyPressedBackground = Color(0xFF555555)  // Key pressed state

val LightSurface = Color(0xFFF4F8FF)
val LightBackground = Color(0xFFEFF5FF)
val LightSurfaceVariant = Color(0xFFDCE8FF)

// Status colors
val ListeningBlue = Color(0xFF4FC3F7)   // Active listening indicator
val ErrorRed = Color(0xFFEF5350)        // Error state
val ReadyGreen = Color(0xFF4A82E8)      // Ready state
val ActiveGreen = Color(0xFF4CAF50)     // Active status indicator
val WarningOrange = Color(0xFFFF9800)   // Warning / requires action

// Legacy (kept for backward compat with user prefs)
val Green500 = Color(0xFF1F5DD7)
val Green700 = Color(0xFF184CB0)
val Orange700 = Color(0xFF4A82E8)
val Orange900 = Color(0xFF184CB0)
