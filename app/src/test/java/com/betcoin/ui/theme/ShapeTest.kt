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
    fun small_is16dpRounded() {
        val shape = BetCoinShapes.small
        assertThat(shape).isEqualTo(RoundedCornerShape(16.dp))
    }

    @Test
    fun medium_is32dpRounded() {
        val shape = BetCoinShapes.medium
        assertThat(shape).isEqualTo(RoundedCornerShape(32.dp))
    }

    @Test
    fun large_is48dpRounded() {
        val shape = BetCoinShapes.large
        assertThat(shape).isEqualTo(RoundedCornerShape(48.dp))
    }

    @Test
    fun extraLarge_is48dpRounded() {
        val shape = BetCoinShapes.extraLarge
        assertThat(shape).isEqualTo(RoundedCornerShape(48.dp))
    }
}
