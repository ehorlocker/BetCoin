package com.betcoin.ui.theme

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit tests for BetCoin typography definitions.
 *
 * Verifies that text styles from dark-mode-design.md are configured
 * with the correct sizes, weights, and line heights.
 */
class TypeTest {

    @Test
    fun displayLarge_is40spBoldWeight700() {
        val style = BetCoinTypography.displayLarge
        assertThat(style.fontSize).isEqualTo(40.sp)
        assertThat(style.fontWeight).isEqualTo(FontWeight.Bold)
        assertThat(style.lineHeight).isEqualTo(48.sp)
        assertThat(style.letterSpacing).isEqualTo((-0.02).sp)
    }

    @Test
    fun headlineLarge_is32spBoldWeight700() {
        val style = BetCoinTypography.headlineLarge
        assertThat(style.fontSize).isEqualTo(32.sp)
        assertThat(style.fontWeight).isEqualTo(FontWeight.Bold)
        assertThat(style.lineHeight).isEqualTo(38.4.sp)
    }

    @Test
    fun headlineMedium_is24spSemiBoldWeight600() {
        val style = BetCoinTypography.headlineMedium
        assertThat(style.fontSize).isEqualTo(24.sp)
        assertThat(style.fontWeight).isEqualTo(FontWeight.SemiBold)
        assertThat(style.lineHeight).isEqualTo(31.2.sp)
    }

    @Test
    fun bodyLarge_is18spMediumWeight500() {
        val style = BetCoinTypography.bodyLarge
        assertThat(style.fontSize).isEqualTo(18.sp)
        assertThat(style.fontWeight).isEqualTo(FontWeight.Medium)
        assertThat(style.lineHeight).isEqualTo(27.sp)
    }

    @Test
    fun bodyMedium_is16spMediumWeight500() {
        val style = BetCoinTypography.bodyMedium
        assertThat(style.fontSize).isEqualTo(16.sp)
        assertThat(style.fontWeight).isEqualTo(FontWeight.Medium)
        assertThat(style.lineHeight).isEqualTo(24.sp)
    }

    @Test
    fun labelLarge_is14spSemiBoldWeight600() {
        val style = BetCoinTypography.labelLarge
        assertThat(style.fontSize).isEqualTo(14.sp)
        assertThat(style.fontWeight).isEqualTo(FontWeight.SemiBold)
        assertThat(style.lineHeight).isEqualTo(19.6.sp)
        assertThat(style.letterSpacing).isEqualTo(0.01.sp)
    }

    @Test
    fun labelSmall_is12spBoldWeight700() {
        val style = BetCoinTypography.labelSmall
        assertThat(style.fontSize).isEqualTo(12.sp)
        assertThat(style.fontWeight).isEqualTo(FontWeight.Bold)
        assertThat(style.lineHeight).isEqualTo(16.8.sp)
        assertThat(style.letterSpacing).isEqualTo(0.03.sp)
    }
}
