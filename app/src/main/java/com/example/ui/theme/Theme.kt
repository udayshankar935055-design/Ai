package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

enum class ThemeMode(val label: String) {
    DARK("Dark Obsidian"),
    LIGHT("Light Daylight"),
    SYSTEM("Follow System")
}

private val SmartDarkColorScheme = darkColorScheme(
    primary = ElectricBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF1D357A),
    onPrimaryContainer = Color(0xFFDBEAFE),
    secondary = RoyalPurple,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF33206B),
    onSecondaryContainer = Color(0xFFEDE9FE),
    tertiary = NeuralCyan,
    onTertiary = ObsidianBlack,
    background = ObsidianBlack,
    onBackground = DarkOnBackground,
    surface = MidnightNavy,
    onSurface = DarkOnBackground,
    surfaceVariant = DeepSlateBlue,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceContainerLow = MidnightNavy,
    surfaceContainer = DeepSlateBlue,
    surfaceContainerHigh = ElevatedIndigo,
    outline = BorderIndigo,
    outlineVariant = Color(0xFF1F2A52),
    error = DarkError,
    onError = Color(0xFF2B050B)
)

private val SmartLightColorScheme = lightColorScheme(
    primary = DeepCobalt,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = Color(0xFF1E3A8A),
    secondary = DeepViolet,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDE9FE),
    onSecondaryContainer = Color(0xFF4C1D95),
    tertiary = DeepTeal,
    onTertiary = Color.White,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnBackground,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceContainerLow = Color(0xFFFDFDFF),
    surfaceContainer = LightSurfaceVariant,
    surfaceContainerHigh = Color(0xFFE0E7FF),
    outline = LightBorder,
    outlineVariant = Color(0xFFE2E8F0),
    error = LightError,
    onError = Color.White
)

private val SmartAiShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun SmartAiTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = if (darkTheme) SmartDarkColorScheme else SmartLightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SmartAiTypography,
        shapes = SmartAiShapes,
        content = content
    )
}
