package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

data class AppColors(
    val background: Color,
    val surface: Color,
    val surface2: Color,
    val text: Color,
    val textSecondary: Color,
    val hairline: Color,
    val accent: Color,
    val success: Color,
    val warning: Color,
    val danger: Color
)

val DarkAppColors = AppColors(
    background = DarkBg,
    surface = DarkSurf,
    surface2 = DarkSurf2,
    text = DarkTxt,
    textSecondary = DarkTxtSec,
    hairline = DarkHairline,
    accent = DarkAccent,
    success = DarkSuccess,
    warning = DarkWarning,
    danger = DarkDanger
)

val LightAppColors = AppColors(
    background = LightBg,
    surface = LightSurf,
    surface2 = LightSurf2,
    text = LightTxt,
    textSecondary = LightTxtSec,
    hairline = LightHairline,
    accent = LightAccent,
    success = LightSuccess,
    warning = LightWarning,
    danger = LightDanger
)

val LocalAppColors = staticCompositionLocalOf { DarkAppColors }

private val TripPilotDarkColorScheme = darkColorScheme(
    primary = DarkAccent,
    onPrimary = DarkBg,
    primaryContainer = DarkSurf2,
    onPrimaryContainer = DarkAccent,
    secondary = DarkSuccess,
    onSecondary = DarkBg,
    secondaryContainer = DarkSurf2,
    onSecondaryContainer = DarkSuccess,
    tertiary = DarkWarning,
    error = DarkDanger,
    onError = Color.White,
    background = DarkBg,
    onBackground = DarkTxt,
    surface = DarkSurf,
    onSurface = DarkTxt,
    surfaceVariant = DarkSurf2,
    onSurfaceVariant = DarkTxtSec,
    outline = DarkHairline,
    outlineVariant = DarkHairline
)

private val TripPilotLightColorScheme = lightColorScheme(
    primary = LightAccent,
    onPrimary = Color.White,
    primaryContainer = LightSurf2,
    onPrimaryContainer = LightAccent,
    secondary = LightSuccess,
    onSecondary = Color.White,
    secondaryContainer = LightSurf2,
    onSecondaryContainer = LightSuccess,
    tertiary = LightWarning,
    error = LightDanger,
    onError = Color.White,
    background = LightBg,
    onBackground = LightTxt,
    surface = LightSurf,
    onSurface = LightTxt,
    surfaceVariant = LightSurf2,
    onSurfaceVariant = LightTxtSec,
    outline = LightHairline,
    outlineVariant = LightHairline
)

val TripPilotShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(11.dp),
    medium = RoundedCornerShape(14.dp), // Inputs 14dp, Buttons 14dp
    large = RoundedCornerShape(18.dp),  // Cards 18dp
    extraLarge = RoundedCornerShape(18.dp)
)

@Composable
fun TripPilotTheme(
    themeSetting: String = "dark", // "dark", "light", "system"
    textScale: Float = 1.0f,
    content: @Composable () -> Unit
) {
    val isDark = when (themeSetting.lowercase()) {
        "light" -> false
        "system" -> isSystemInDarkTheme()
        else -> true // Dark default
    }

    val colorScheme = if (isDark) TripPilotDarkColorScheme else TripPilotLightColorScheme
    val appColors = if (isDark) DarkAppColors else LightAppColors

    val currentDensity = androidx.compose.ui.platform.LocalDensity.current
    val customDensity = androidx.compose.ui.unit.Density(
        density = currentDensity.density,
        fontScale = currentDensity.fontScale * textScale
    )

    CompositionLocalProvider(
        LocalAppColors provides appColors,
        androidx.compose.ui.platform.LocalDensity provides customDensity
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = TripPilotShapes,
            content = content
        )
    }
}
