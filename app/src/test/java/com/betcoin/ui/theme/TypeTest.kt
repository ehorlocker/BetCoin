package com.betcoin.ui.theme

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit tests for BetCoin typography definitions.
 *
 * Verifies that text styles from DESIGN.md are configured
 * with the correct sizes, weights, and line heights.
 */
class TypeTest {

    @Test
    fun displayLarge_is48spBlackWeight900() {
        val style = BetCoinTypography.displayLarge
        assertThat(style.fontSize).isEqualTo(48.sp)
        assertThat(style.fontWeight).isEqualTo(FontWeight.Black)
        assertThat(style.lineHeight).isEqualTo(52.sp)
    }

    @Test
    fun headlineLarge_is32spExtraBoldWeight800() {
        val style = BetCoinTypography.headlineLarge
        assertThat(style.fontSize).isEqualTo(32.sp)
        assertThat(style.fontWeight).isEqualTo(FontWeight.ExtraBold)
        assertThat(style.lineHeight).isEqualTo(40.sp)
    }

    @Test
    fun headlineMedium_is24spBoldWeight700() {
        val style = BetCoinTypography.headlineMedium
        assertThat(style.fontSize).isEqualTo(24.sp)
        assertThat(style.fontWeight).isEqualTo(FontWeight.Bold)
        assertThat(style.lineHeight).isEqualTo(32.sp)
    }

    @Test
    fun bodyLarge_is18spMediumWeight500() {
        val style = BetCoinTypography.bodyLarge
        assertThat(style.fontSize).isEqualTo(18.sp)
        assertThat(style.fontWeight).isEqualTo(FontWeight.Medium)
        assertThat(style.lineHeight).isEqualTo(28.sp)
    }

    @Test
    fun bodyMedium_is16spNormalWeight400() {
        val style = BetCoinTypography.bodyMedium
        assertThat(style.fontSize).isEqualTo(16.sp)
        assertThat(style.fontWeight).isEqualTo(FontWeight.Normal)
        assertThat(style.lineHeight).isEqualTo(24.sp)
    }

    @Test
    fun labelLarge_is14spBoldWeight700() {
        val style = BetCoinTypography.labelLarge
        assertThat(style.fontSize).isEqualTo(14.sp)
        assertThat(style.fontWeight).isEqualTo(FontWeight.Bold)
        assertThat(style.lineHeight).isEqualTo(20.sp)
    }

    @Test
    fun labelSmall_is12spSemiBoldWeight600() {
        val style = BetCoinTypography.labelSmall
        assertThat(style.fontSize).isEqualTo(12.sp)
        assertThat(style.fontWeight).isEqualTo(FontWeight.SemiBold)
        assertThat(style.lineHeight).isEqualTo(16.sp)
    }
}
