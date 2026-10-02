package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val TripPilotDarkColorScheme = darkColorScheme(
    primary = PrimaryCyan,
    onPrimary = DarkBackground,
    primaryContainer = DarkSurfaceElevated,
    onPrimaryContainer = PrimaryCyan,
    secondary = AccentGreenSuccess,
    onSecondary = DarkBackground,
    secondaryContainer = DarkSurfaceVariant,
    onSecondaryContainer = AccentGreenSuccess,
    tertiary = AccentAmberWarning,
    error = AccentRedDanger,
    onError = Color.White,
    background = DarkBackground,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = BorderDivider,
    outlineVariant = DarkSurfaceVariant
)

private val TripPilotLightColorScheme = lightColorScheme(
    primary = PrimaryCyanVariant,
    onPrimary = Color.White,
    primaryContainer = LightSurfaceElevated,
    onPrimaryContainer = PrimaryCyanVariant,
    secondary = AccentGreenSuccess,
    onSecondary = Color.White,
    secondaryContainer = LightSurfaceVariant,
    onSecondaryContainer = AccentGreenSuccess,
    tertiary = AccentAmberWarning,
    error = AccentRedDanger,
    onError = Color.White,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceElevated,
    onSurfaceVariant = LightTextSecondary,
    outline = LightBorderDivider,
    outlineVariant = LightSurfaceVariant
)

val TripPilotShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(14.dp), // Inputs 14dp
    large = RoundedCornerShape(16.dp),  // Buttons 16dp
    extraLarge = RoundedCornerShape(20.dp) // Cards 20dp
)

@Composable
fun TripPilotTheme(
    themeSetting: String = "dark", // "dark", "light", "system"
    content: @Composable () -> Unit
) {
    val isDark = when (themeSetting.lowercase()) {
        "light" -> false
        "system" -> isSystemInDarkTheme()
        else -> true // Dark-first default
    }

    val colorScheme = if (isDark) TripPilotDarkColorScheme else TripPilotLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = TripPilotShapes,
        content = content
    )
}
