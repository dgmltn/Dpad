package com.dgmltn.dpad.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// A dark, high-contrast palette so the remote reads as hardware: a near-black background,
// a lighter surface for the physical pad, and a single accent for the center/pressed state.
private val DpadBackground = Color(0xFF0A0A0C)
private val DpadSurface = Color(0xFF1A1A1D)
private val DpadPadSurface = Color(0xFF3A3A40)
private val DpadOnColor = Color(0xFFECECEC)
private val DpadOnPadSurface = Color(0xFFD0D0D5)
private val DpadAccent = Color(0xFF5B8DEF)
private val DpadOnAccent = Color(0xFF06122B)
private val DpadError = Color(0xFFFF5252)
private val DpadOnError = Color(0xFF2B0000)

private val DpadColorScheme = darkColorScheme(
    primary = DpadAccent,
    onPrimary = DpadOnAccent,
    secondary = DpadAccent,
    onSecondary = DpadOnAccent,
    background = DpadBackground,
    onBackground = DpadOnColor,
    surface = DpadSurface,
    onSurface = DpadOnColor,
    surfaceVariant = DpadPadSurface,
    onSurfaceVariant = DpadOnPadSurface,
    error = DpadError,
    onError = DpadOnError,
)

private val DpadTypography = Typography()

/** Dark, high-contrast Material3 theme for the remote-control UI. */
@Composable
fun DpadTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DpadColorScheme,
        typography = DpadTypography,
        content = content,
    )
}
