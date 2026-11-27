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

private val LightColorScheme = lightColorScheme(
    primary = KelMiEmerald,
    onPrimary = Color.White,
    primaryContainer = KelMiEmeraldContainer,
    onPrimaryContainer = KelMiOnEmeraldContainer,
    secondary = KelMiTeal,
    onSecondary = Color.White,
    secondaryContainer = KelMiTealContainer,
    onSecondaryContainer = KelMiOnTealContainer,
    tertiary = VetnyIndigo,
    onTertiary = Color.White,
    tertiaryContainer = VetnyContainer,
    onTertiaryContainer = VetnyOnContainer,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    error = ErrorRed,
    onError = Color.White
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF34D399),
    onPrimary = Color(0xFF022C22),
    primaryContainer = KelMiOnEmeraldContainer,
    onPrimaryContainer = KelMiEmeraldContainer,
    secondary = Color(0xFF2DD4BF),
    onSecondary = Color(0xFF042F2E),
    secondaryContainer = KelMiOnTealContainer,
    onSecondaryContainer = KelMiTealContainer,
    tertiary = VetnyIndigoLight,
    onTertiary = Color(0xFF1E1B4B),
    tertiaryContainer = VetnyOnContainer,
    onTertiaryContainer = VetnyContainer,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A)
)

private val KelMiShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun KelMiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = KelMiTypography,
        shapes = KelMiShapes,
        content = content
    )
}
