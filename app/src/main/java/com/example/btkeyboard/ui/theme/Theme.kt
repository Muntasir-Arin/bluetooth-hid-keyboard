package com.example.btkeyboard.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

private val AppColors = darkColorScheme(
    primary = AppAccent,
    onPrimary = AppBlack,
    primaryContainer = AppSurfaceRaised,
    onPrimaryContainer = AppTextPrimary,
    secondary = AppTextSecondary,
    onSecondary = AppTextPrimary,
    tertiary = AppAccent,
    background = AppBlack,
    onBackground = AppTextPrimary,
    surface = AppSurface,
    onSurface = AppTextPrimary,
    surfaceVariant = AppSurfaceRaised,
    onSurfaceVariant = AppTextSecondary,
    outline = AppStroke,
    outlineVariant = AppStroke,
    error = AppAccent,
    onError = AppBlack,
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
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = AppColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
