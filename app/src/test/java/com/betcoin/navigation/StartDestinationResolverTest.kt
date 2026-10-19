package com.betcoin.navigation

import com.betcoin.testing.FakePendingBetRepository
import com.betcoin.ui.onboarding.FakeSettingsRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class StartDestinationResolverTest {
    private val settings = FakeSettingsRepository()
    private val bets = FakePendingBetRepository()
    private val resolver = StartDestinationResolver(settings, bets)

    @Test
    fun firstLaunch_returnsOnboarding_andDoesNotTouchBets() = runTest {
        settings.isFirstLaunchResult = true
        bets.pendingBets.value = listOf(bets.pendingBet(1L, "Who wins?", 2))

        assertThat(resolver.resolve()).isEqualTo(Routes.ONBOARDING)
        assertThat(bets.discardOlderCalls).isEqualTo(0)
    }

    @Test
    fun pendingBetExists_returnsRecovery() = runTest {
        settings.isFirstLaunchResult = false
        bets.pendingBets.value = listOf(bets.pendingBet(1L, "Who wins?", 2))

        assertThat(resolver.resolve()).isEqualTo(Routes.RECOVERY)
    }

    @Test
    fun noPendingBet_returnsHome() = runTest {
        settings.isFirstLaunchResult = false

        assertThat(resolver.resolve()).isEqualTo(Routes.HOME)
    }

    @Test
    fun severalPendingBets_discardsOlderThenReturnsRecovery() = runTest {
        settings.isFirstLaunchResult = false
        bets.pendingBets.value = listOf(
            bets.pendingBet(1L, "Old", 1),
            bets.pendingBet(3L, "Newest", 2),
            bets.pendingBet(2L, "Middle", 0),
        )

        assertThat(resolver.resolve()).isEqualTo(Routes.RECOVERY)
        assertThat(bets.discardOlderCalls).isEqualTo(1)
        assertThat(bets.pendingBets.value.map { it.bet.id }).containsExactly(3L)
    }
}
