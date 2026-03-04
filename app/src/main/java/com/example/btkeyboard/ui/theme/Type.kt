package com.example.btkeyboard.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.btkeyboard.R

private val AppSans = FontFamily(
    Font(R.font.dejavu_sans_regular, FontWeight.Normal),
    Font(R.font.dejavu_sans_bold, FontWeight.Bold),
)

val AppMono = FontFamily(
    Font(R.font.dejavu_sans_mono_regular, FontWeight.Normal),
    Font(R.font.dejavu_sans_mono_bold, FontWeight.Bold),
)

val AppTypography = Typography(
    headlineSmall = TextStyle(
        fontFamily = AppSans,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        letterSpacing = 0.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = AppSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        letterSpacing = 0.1.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = AppSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        letterSpacing = 0.2.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = AppSans,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = AppSans,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        letterSpacing = 0.1.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = AppSans,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        letterSpacing = 0.1.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = AppSans,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        letterSpacing = 0.3.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = AppMono,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        letterSpacing = 0.2.sp,
    ),
)
