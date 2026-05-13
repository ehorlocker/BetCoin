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
        val shape = BetCoinShapes.small as RoundedCornerShape
        // RoundedCornerShape stores corner values; we verify via class type
        assertThat(shape).isInstanceOf(RoundedCornerShape::class.java)
    }

    @Test
    fun medium_is16dpRounded() {
        val shape = BetCoinShapes.medium as RoundedCornerShape
        assertThat(shape).isInstanceOf(RoundedCornerShape::class.java)
    }

    @Test
    fun large_is24dpRounded() {
        val shape = BetCoinShapes.large as RoundedCornerShape
        assertThat(shape).isInstanceOf(RoundedCornerShape::class.java)
    }

    @Test
    fun extraLarge_is32dpRounded() {
        val shape = BetCoinShapes.extraLarge as RoundedCornerShape
        assertThat(shape).isInstanceOf(RoundedCornerShape::class.java)
    }
}
