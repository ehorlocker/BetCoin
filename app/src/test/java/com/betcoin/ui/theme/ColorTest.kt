package com.betcoin.ui.theme

import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit tests for BetCoin color constants.
 *
 * These tests verify that the design system colors from dark-mode-design.md
 * are defined with the exact hex values.
 */
class ColorTest {

    @Test
    fun primary_isMintGlow() {
        assertThat(BetCoinPurple).isEqualTo(Color(0xFF7EF2D5))
    }

    @Test
    fun secondary_isCoralGlow() {
        assertThat(BetCoinCyan).isEqualTo(Color(0xFFFFB2B7))
    }

    @Test
    fun tertiary_isSkyGlow() {
        assertThat(BetCoinMagenta).isEqualTo(Color(0xFFA4E8FF))
    }

    @Test
    fun background_isMidnightCharcoal() {
        assertThat(BetCoinBackground).isEqualTo(Color(0xFF111316))
    }

    @Test
    fun surface_isDarkElevated() {
        assertThat(BetCoinSurface).isEqualTo(Color(0xFF1E2023))
    }

    @Test
    fun surfaceHigh_isFloating() {
        assertThat(BetCoinSurfaceHigh).isEqualTo(Color(0xFF282A2D))
    }

    @Test
    fun success_isMintGreen() {
        assertThat(BetCoinSuccess).isEqualTo(Color(0xFF7EF2D5))
    }

    @Test
    fun error_isSoftRed() {
        assertThat(BetCoinError).isEqualTo(Color(0xFFFFB4AB))
    }

    @Test
    fun onSurface_isLightGray() {
        assertThat(BetCoinOnSurface).isEqualTo(Color(0xFFE2E2E6))
    }

    @Test
    fun outline_isMutedTeal() {
        assertThat(BetCoinOutline).isEqualTo(Color(0xFF86948E))
    }

    @Test
    fun darkColorScheme_usesBetCoinColors() {
        val scheme = BetCoinDarkColorScheme

        assertThat(scheme.primary).isEqualTo(BetCoinPurple)
        assertThat(scheme.secondary).isEqualTo(BetCoinCyan)
        assertThat(scheme.tertiary).isEqualTo(BetCoinMagenta)
        assertThat(scheme.background).isEqualTo(BetCoinBackground)
        assertThat(scheme.surface).isEqualTo(BetCoinSurface)
        assertThat(scheme.error).isEqualTo(BetCoinError)
        assertThat(scheme.onSurface).isEqualTo(BetCoinOnSurface)
        assertThat(scheme.outline).isEqualTo(BetCoinOutline)
    }
}
