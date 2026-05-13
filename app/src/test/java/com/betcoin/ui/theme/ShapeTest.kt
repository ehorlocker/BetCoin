package com.betcoin.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit tests for BetCoin shape definitions.
 */
class ShapeTest {

    @Test
    fun small_is8dpRounded() {
        val shape = BetCoinShapes.small
        assertThat(shape).isEqualTo(RoundedCornerShape(8.dp))
    }

    @Test
    fun medium_is16dpRounded() {
        val shape = BetCoinShapes.medium
        assertThat(shape).isEqualTo(RoundedCornerShape(16.dp))
    }

    @Test
    fun large_is24dpRounded() {
        val shape = BetCoinShapes.large
        assertThat(shape).isEqualTo(RoundedCornerShape(24.dp))
    }

    @Test
    fun extraLarge_is32dpRounded() {
        val shape = BetCoinShapes.extraLarge
        assertThat(shape).isEqualTo(RoundedCornerShape(32.dp))
    }
}
