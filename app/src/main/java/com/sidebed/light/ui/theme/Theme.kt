package com.sidebed.light.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SidebedColors = darkColorScheme(
    primary = WarmAmber,
    onPrimary = Color(0xFF2A1A06),
    primaryContainer = WarmAmberDeep,
    onPrimaryContainer = Color(0xFF20140A),
    secondary = WarmAmberSoft,
    onSecondary = Color(0xFF2A1A06),
    background = NightBackground,
    onBackground = SoftWhite,
    surface = NightSurface,
    onSurface = SoftWhite,
    surfaceVariant = NightSurfaceHigh,
    onSurfaceVariant = MutedText,
    outline = NightOutline,
    error = DangerRed,
    onError = Color(0xFF2A0A06),
)

/** The single app theme — always a warm, low-blue dark scheme. */
@Composable
fun SidebedLightTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SidebedColors,
        typography = SidebedTypography,
        content = content,
    )
}
