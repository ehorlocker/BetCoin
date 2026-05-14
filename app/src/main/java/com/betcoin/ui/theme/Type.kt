package com.betcoin.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.betcoin.R

val Quicksand = FontFamily(
    Font(R.font.quicksand_variable, FontWeight.Normal),
    Font(R.font.quicksand_variable, FontWeight.Medium),
    Font(R.font.quicksand_variable, FontWeight.SemiBold),
    Font(R.font.quicksand_variable, FontWeight.Bold),
)

val BetCoinTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = Quicksand,
        fontSize = 40.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 48.sp,
        letterSpacing = (-0.02).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = Quicksand,
        fontSize = 32.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 38.4.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = Quicksand,
        fontSize = 24.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 31.2.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = Quicksand,
        fontSize = 18.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 27.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Quicksand,
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 24.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = Quicksand,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 19.6.sp,
        letterSpacing = 0.01.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = Quicksand,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 16.8.sp,
        letterSpacing = 0.03.sp,
    ),
)
