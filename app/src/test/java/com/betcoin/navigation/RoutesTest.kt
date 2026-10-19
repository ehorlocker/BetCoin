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
    fun recoveryRoute_isCorrectValue() {
        assertThat(Routes.RECOVERY).isEqualTo("recovery")
    }

    @Test
    fun managePlayersRoute_isCorrectValue() {
        assertThat(Routes.MANAGE_PLAYERS).isEqualTo("manage_players")
    }

    @Test
    fun newBet_withoutBetId_opensStepA() {
        assertThat(Routes.newBet()).isEqualTo("new_bet")
    }

    @Test
    fun newBet_withBetId_passesItAsTheOptionalArgument() {
        assertThat(Routes.newBet(42L)).isEqualTo("new_bet?betId=42")
        assertThat(Routes.NEW_BET_ROUTE).isEqualTo("new_bet?betId={betId}")
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

    @Test
    fun adminPanelRoute_isCorrectValue() {
        assertThat(Routes.ADMIN_PANEL).isEqualTo("admin_panel")
    }

    @Test
    fun adminPinRecoveryRoute_isCorrectValue() {
        assertThat(Routes.ADMIN_PIN_RECOVERY).isEqualTo("admin_pin_recovery")
    }
}
