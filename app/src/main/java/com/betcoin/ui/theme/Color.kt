package com.betcoin.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

// Brand Accents (Midnight Playground — Mint, Coral, Sky)
val BetCoinPurple = Color(0xFF7EF2D5)   // Primary: Mint Glow
val BetCoinCyan = Color(0xFFFFB2B7)     // Secondary: Coral Glow
val BetCoinMagenta = Color(0xFFA4E8FF)  // Tertiary: Sky Glow

// Semantic Colors
val BetCoinSuccess = Color(0xFF7EF2D5)
val BetCoinError = Color(0xFFFFB4AB)

// Surfaces
val BetCoinBackground = Color(0xFF111316)
val BetCoinSurface = Color(0xFF1E2023)
val BetCoinSurfaceHigh = Color(0xFF282A2D)
val BetCoinSurfaceContainerLowest = Color(0xFF0C0E11)
val BetCoinSurfaceContainerLow = Color(0xFF1A1C1F)
val BetCoinSurfaceContainer = Color(0xFF1E2023)
val BetCoinSurfaceContainerHigh = Color(0xFF282A2D)
val BetCoinSurfaceContainerHighest = Color(0xFF333538)
val BetCoinSurfaceVariant = Color(0xFF333538)

// Content Colors
val BetCoinOnSurface = Color(0xFFE2E2E6)
val BetCoinOnSurfaceVariant = Color(0xFFBCCAC4)
val BetCoinOnPrimary = Color(0xFF00382D)
val BetCoinOnSecondary = Color(0xFF5B1822)
val BetCoinOnTertiary = Color(0xFF003642)
val BetCoinOnBackground = Color(0xFFE2E2E6)

// Outlines
val BetCoinOutline = Color(0xFF86948E)
val BetCoinOutlineVariant = Color(0xFF3D4945)

// Inverse
val BetCoinInverseSurface = Color(0xFFE2E2E6)
val BetCoinInverseOnSurface = Color(0xFF2F3034)
val BetCoinInversePrimary = Color(0xFF006B59)

// Fixed
val BetCoinPrimaryFixed = Color(0xFF83F7DA)
val BetCoinPrimaryFixedDim = Color(0xFF65DABE)
val BetCoinOnPrimaryFixed = Color(0xFF002019)
val BetCoinOnPrimaryFixedVariant = Color(0xFF005142)
val BetCoinSecondaryFixed = Color(0xFFFFDADB)
val BetCoinSecondaryFixedDim = Color(0xFFFFB2B7)
val BetCoinOnSecondaryFixed = Color(0xFF3F020F)
val BetCoinOnSecondaryFixedVariant = Color(0xFF782E37)
val BetCoinTertiaryFixed = Color(0xFFB3EBFF)
val BetCoinTertiaryFixedDim = Color(0xFF8AD1E8)
val BetCoinOnTertiaryFixed = Color(0xFF001F27)
val BetCoinOnTertiaryFixedVariant = Color(0xFF004E5F)

// Containers
val BetCoinPrimaryContainer = Color(0xFF7EF2D5)
val BetCoinOnPrimaryContainer = Color(0xFF006E5C)
val BetCoinSecondaryContainer = Color(0xFF7B3039)
val BetCoinOnSecondaryContainer = Color(0xFFFF9DA5)
val BetCoinTertiaryContainer = Color(0xFFA4E8FF)
val BetCoinOnTertiaryContainer = Color(0xFF166A7F)
val BetCoinErrorContainer = Color(0xFF93000A)
val BetCoinOnErrorContainer = Color(0xFFFFDAD6)

// Surface Tint
val BetCoinSurfaceTint = Color(0xFF65DABE)

val BetCoinDarkColorScheme = darkColorScheme(
    primary = BetCoinPurple,
    onPrimary = BetCoinOnPrimary,
    primaryContainer = BetCoinPrimaryContainer,
    onPrimaryContainer = BetCoinOnPrimaryContainer,
    inversePrimary = BetCoinInversePrimary,
    secondary = BetCoinCyan,
    onSecondary = BetCoinOnSecondary,
    secondaryContainer = BetCoinSecondaryContainer,
    onSecondaryContainer = BetCoinOnSecondaryContainer,
    tertiary = BetCoinMagenta,
    onTertiary = BetCoinOnTertiary,
    tertiaryContainer = BetCoinTertiaryContainer,
    onTertiaryContainer = BetCoinOnTertiaryContainer,
    background = BetCoinBackground,
    onBackground = BetCoinOnBackground,
    surface = BetCoinSurface,
    onSurface = BetCoinOnSurface,
    surfaceVariant = BetCoinSurfaceVariant,
    onSurfaceVariant = BetCoinOnSurfaceVariant,
    surfaceTint = BetCoinSurfaceTint,
    inverseSurface = BetCoinInverseSurface,
    inverseOnSurface = BetCoinInverseOnSurface,
    error = BetCoinError,
    onError = Color(0xFF690005),
    errorContainer = BetCoinErrorContainer,
    onErrorContainer = BetCoinOnErrorContainer,
    outline = BetCoinOutline,
    outlineVariant = BetCoinOutlineVariant,
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF37393D),
    surfaceDim = BetCoinBackground,
    surfaceContainer = BetCoinSurfaceContainer,
    surfaceContainerHigh = BetCoinSurfaceContainerHigh,
    surfaceContainerHighest = BetCoinSurfaceContainerHighest,
    surfaceContainerLow = BetCoinSurfaceContainerLow,
    surfaceContainerLowest = BetCoinSurfaceContainerLowest,
)
