package com.betcoin.ui.theme

import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit tests for BetCoin color constants.
 *
 * These tests verify that the design system colors from DESIGN.md
 * are defined with the exact hex values.
 */
class ColorTest {

    @Test
    fun primary_isElectricPurple() {
        assertThat(BetCoinPurple).isEqualTo(Color(0xFF8A2BE2))
    }

    @Test
    fun secondary_isElectricCyan() {
        assertThat(BetCoinCyan).isEqualTo(Color(0xFF00F5FF))
    }

    @Test
    fun tertiary_isMagenta() {
        assertThat(BetCoinMagenta).isEqualTo(Color(0xFFFF007A))
    }

    @Test
    fun background_isTrueDark() {
        assertThat(BetCoinBackground).isEqualTo(Color(0xFF131313))
    }

    @Test
    fun surface_isDarkElevated() {
        assertThat(BetCoinSurface).isEqualTo(Color(0xFF1E1E1E))
    }

    @Test
    fun surfaceHigh_isFloating() {
        assertThat(BetCoinSurfaceHigh).isEqualTo(Color(0xFF2A2A2A))
    }

    @Test
    fun success_isRadioactiveGreen() {
        assertThat(BetCoinSuccess).isEqualTo(Color(0xFF39FF14))
    }

    @Test
    fun error_isSharpRed() {
        assertThat(BetCoinError).isEqualTo(Color(0xFFFF3131))
    }

    @Test
    fun onSurface_isLightGray() {
        assertThat(BetCoinOnSurface).isEqualTo(Color(0xFFE5E2E1))
    }

    @Test
    fun outline_isMutedPurple() {
        assertThat(BetCoinOutline).isEqualTo(Color(0xFF988CA0))
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
