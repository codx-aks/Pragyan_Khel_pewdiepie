package com.example.temporaldetector.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryVariant,
    secondary = Secondary,
    onSecondary = OnPrimary,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    onBackground = OnSurface,
    onSurface = OnSurface,
    onSurfaceVariant = TextMuted,
)

private val AppTypography = Typography(
    titleLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        color = OnSurface,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        color = OnSurface,
    ),
    bodyLarge = TextStyle(
        fontSize = 14.sp,
        color = OnSurface,
    ),
    bodyMedium = TextStyle(
        fontSize = 13.sp,
        color = TextMuted,
    ),
    bodySmall = TextStyle(
        fontSize = 11.sp,
        color = TextMuted,
    ),
    labelSmall = TextStyle(
        fontSize = 10.sp,
        fontFamily = FontFamily.Monospace,
        color = TextDimmed,
    ),
)

@Composable
fun TemporalDetectorTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = DarkBackground.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = AppTypography,
        content = content,
    )
}

