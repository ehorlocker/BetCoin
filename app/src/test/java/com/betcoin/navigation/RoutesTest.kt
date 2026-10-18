package com.betcoin.navigation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Tests for navigation route constants.
 */
class RoutesTest {

    @Test
    fun onboardingRoute_isCorrectValue() {
        assertThat(Routes.ONBOARDING).isEqualTo("onboarding")
    }

    @Test
    fun homeRoute_isCorrectValue() {
        assertThat(Routes.HOME).isEqualTo("home")
    }

    @Test
    fun managePlayersRoute_isCorrectValue() {
        assertThat(Routes.MANAGE_PLAYERS).isEqualTo("manage_players")
    }

    @Test
    fun playerDetailRoute_takesPlayerIdArgument() {
        assertThat(Routes.PLAYER_DETAIL).isEqualTo("player_detail/{playerId}")
        assertThat(Routes.playerDetail(42L)).isEqualTo("player_detail/42")
    }

    @Test
    fun betDetailRoute_takesTheBetIdArgument() {
        assertThat(Routes.BET_DETAIL).isEqualTo("bet_detail/{betId}")
        assertThat(Routes.betDetail(42L)).isEqualTo("bet_detail/42")
    }
}
