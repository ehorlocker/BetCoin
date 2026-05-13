package com.betcoin.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.betcoin.R

val Montserrat = FontFamily(
    Font(R.font.montserrat_regular, FontWeight.Normal),
    Font(R.font.montserrat_regular, FontWeight.Medium),
    Font(R.font.montserrat_regular, FontWeight.SemiBold),
    Font(R.font.montserrat_regular, FontWeight.Bold),
    Font(R.font.montserrat_regular, FontWeight.ExtraBold),
    Font(R.font.montserrat_regular, FontWeight.Black),
)

val BetCoinTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = Montserrat,
        fontSize = 48.sp,
        fontWeight = FontWeight.Black,
        lineHeight = 52.sp,
        letterSpacing = (-0.02).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = Montserrat,
        fontSize = 32.sp,
        fontWeight = FontWeight.ExtraBold,
        lineHeight = 40.sp,
        letterSpacing = (-0.01).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = Montserrat,
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 32.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = Montserrat,
        fontSize = 18.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 28.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Montserrat,
        fontSize = 16.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 24.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = Montserrat,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 20.sp,
        letterSpacing = 0.05.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = Montserrat,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 16.sp,
    ),
)
