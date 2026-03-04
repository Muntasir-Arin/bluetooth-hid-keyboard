package com.example.btkeyboard.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.example.btkeyboard.data.AppThemeMode

private val DarkAppColors = darkColorScheme(
    primary = AppAccent,
    onPrimary = DarkSemanticColors.textPrimary,
    primaryContainer = Color(0xFF43211D),
    onPrimaryContainer = DarkSemanticColors.textPrimary,
    secondary = DarkSemanticColors.textSecondary,
    onSecondary = DarkSemanticColors.textPrimary,
    tertiary = AppAccent,
    background = DarkSemanticColors.surfaceBase,
    onBackground = DarkSemanticColors.textPrimary,
    surface = DarkSemanticColors.surfaceRaised,
    onSurface = DarkSemanticColors.textPrimary,
    surfaceVariant = DarkSemanticColors.surfacePressed,
    onSurfaceVariant = DarkSemanticColors.textSecondary,
    outline = DarkSemanticColors.strokeSubtle,
    outlineVariant = DarkSemanticColors.strokeSubtle,
    error = DarkSemanticColors.error,
    onError = DarkSemanticColors.textPrimary,
)

private val LightAppColors = lightColorScheme(
    primary = AppAccent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE0DC),
    onPrimaryContainer = LightSemanticColors.textPrimary,
    secondary = LightSemanticColors.textSecondary,
    onSecondary = LightSemanticColors.textPrimary,
    tertiary = AppAccent,
    background = LightSemanticColors.surfaceBase,
    onBackground = LightSemanticColors.textPrimary,
    surface = LightSemanticColors.surfaceRaised,
    onSurface = LightSemanticColors.textPrimary,
    surfaceVariant = LightSemanticColors.surfacePressed,
    onSurfaceVariant = LightSemanticColors.textSecondary,
    outline = LightSemanticColors.strokeSubtle,
    outlineVariant = LightSemanticColors.strokeSubtle,
    error = LightSemanticColors.error,
    onError = Color.White,
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun BtKeyboardTheme(
    themeMode: AppThemeMode,
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    val isDarkTheme = when (themeMode) {
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
    }
    val colorScheme = if (isDarkTheme) DarkAppColors else LightAppColors
    val semanticColors = if (isDarkTheme) DarkSemanticColors else LightSemanticColors

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.setDecorFitsSystemWindows(window, false)
            @Suppress("DEPRECATION")
            window.statusBarColor = Color.Transparent.toArgb()
            @Suppress("DEPRECATION")
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !isDarkTheme
                isAppearanceLightNavigationBars = !isDarkTheme
            }
        }
    }

    CompositionLocalProvider(LocalAppSemanticColors provides semanticColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
