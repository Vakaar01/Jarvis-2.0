package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val JarvisColorScheme = darkColorScheme(
    primary = CyanPrimary,
    onPrimary = Color(0xFF041E28),
    primaryContainer = Color(0xFF004D5A),
    onPrimaryContainer = CyanGlow,
    secondary = BlueSecondary,
    onSecondary = Color.White,
    secondaryContainer = BlueDeep,
    onSecondaryContainer = Color(0xFFD1E4FF),
    tertiary = AmberAccent,
    onTertiary = Color.Black,
    tertiaryContainer = Color(0xFF5D4200),
    onTertiaryContainer = Color(0xFFFFDEA3),
    background = HudBackground,
    onBackground = HudTextPrimary,
    surface = HudSurface,
    onSurface = HudTextPrimary,
    surfaceVariant = HudSurfaceVariant,
    onSurfaceVariant = HudTextSecondary,
    outline = HudBorder,
    outlineVariant = Color(0xFF162A45),
    error = NeonRed,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    // JARVIS is an iconic sci-fi high-contrast holographic HUD interface
    MaterialTheme(
        colorScheme = JarvisColorScheme,
        typography = Typography,
        content = content
    )
}

