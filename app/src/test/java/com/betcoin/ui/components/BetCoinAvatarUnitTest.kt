package com.betcoin.ui.components

import androidx.compose.ui.graphics.Color
import com.betcoin.ui.theme.BetCoinSecondary
import com.betcoin.ui.theme.BetCoinTertiary
import com.betcoin.ui.theme.BetCoinPrimary
import com.betcoin.ui.theme.BetCoinAvatarSlate
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit tests for BetCoinAvatar color logic.
 */
class BetCoinAvatarUnitTest {

    @Test
    fun avatarColor_isDeterministic() {
        val color1 = avatarColorFor("JD")
        val color2 = avatarColorFor("JD")
        assertThat(color1).isEqualTo(color2)
    }

    @Test
    fun avatarColor_differentInitials_differentColors() {
        val color1 = avatarColorFor("AA")
        val color2 = avatarColorFor("BB")
        assertThat(color1).isNotEqualTo(color2)
    }

    @Test
    fun avatarColor_knownInitials_matchExpected() {
        // "JD" -> J(74) + D(68) = 142 % 5 = 2 -> Tertiary
        assertThat(avatarColorFor("JD")).isEqualTo(BetCoinTertiary)
        // "BC" -> B(66) + C(67) = 133 % 5 = 3 -> Slate
        assertThat(avatarColorFor("BC")).isEqualTo(BetCoinAvatarSlate)
    }
}
