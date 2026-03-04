package com.example.btkeyboard.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color

val AppBlack = Color(0xFF000000)
val AppAccent = Color(0xFFE1665B)

@Immutable
data class AppSemanticColors(
    val surfaceBase: Color,
    val surfaceRaised: Color,
    val surfacePressed: Color,
    val strokeSubtle: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val success: Color,
    val warning: Color,
    val error: Color,
    val accent: Color,
)

internal val DarkSemanticColors = AppSemanticColors(
    surfaceBase = Color(0xFF090A0C),
    surfaceRaised = Color(0xFF12151A),
    surfacePressed = Color(0xFF1A1F25),
    strokeSubtle = Color(0xFF262B33),
    textPrimary = Color(0xFFF1F4F8),
    textSecondary = Color(0xFF9AA4B2),
    success = Color(0xFF4CC38A),
    warning = Color(0xFFF5B861),
    error = Color(0xFFFF7A74),
    accent = AppAccent,
)

internal val LightSemanticColors = AppSemanticColors(
    surfaceBase = Color(0xFFF6F8FB),
    surfaceRaised = Color(0xFFFFFFFF),
    surfacePressed = Color(0xFFEFF3F8),
    strokeSubtle = Color(0xFFD7DDE6),
    textPrimary = Color(0xFF121821),
    textSecondary = Color(0xFF596273),
    success = Color(0xFF1B8D5E),
    warning = Color(0xFF986B18),
    error = Color(0xFFCC4A44),
    accent = AppAccent,
)

internal val LocalAppSemanticColors = staticCompositionLocalOf { DarkSemanticColors }

val MaterialTheme.appColors: AppSemanticColors
    @Composable get() = LocalAppSemanticColors.current
