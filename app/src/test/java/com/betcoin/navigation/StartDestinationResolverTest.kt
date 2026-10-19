package com.betcoin.navigation

import android.database.sqlite.SQLiteException
import com.betcoin.testing.FakePendingBetRepository
import com.betcoin.testing.RecordingErrorLogger
import com.betcoin.ui.onboarding.FakeSettingsRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class StartDestinationResolverTest {
    private val settings = FakeSettingsRepository()
    private val bets = FakePendingBetRepository()
    private val logger = RecordingErrorLogger()
    private val resolver = StartDestinationResolver(settings, bets, logger)

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

    @Test
    fun discardOlderThrows_stillRoutesToRecovery_andLogs() = runTest {
        settings.isFirstLaunchResult = false
        bets.pendingBets.value = listOf(bets.pendingBet(1L, "Who wins?", 2))
        val error = SQLiteException("locked")
        bets.discardOlderError = error

        assertThat(resolver.resolve()).isEqualTo(Routes.RECOVERY)
        assertThat(logger.calls).hasSize(1)
        assertThat(logger.calls.single().tag).isEqualTo("BetCoinLaunch")
        assertThat(logger.calls.single().throwable).isSameInstanceAs(error)
    }

    @Test
    fun discardOlderThrows_noPendingBet_routesHome() = runTest {
        settings.isFirstLaunchResult = false
        bets.discardOlderError = SQLiteException("locked")

        assertThat(resolver.resolve()).isEqualTo(Routes.HOME)
        assertThat(logger.calls).hasSize(1)
    }

    @Test
    fun isFirstLaunchThrows_propagates() = runTest {
        settings.isFirstLaunchError = SQLiteException("disk I/O error")

        var thrown: Throwable? = null
        try {
            resolver.resolve()
        } catch (e: SQLiteException) {
            thrown = e
        }
        assertThat(thrown).isNotNull()
        assertThat(bets.discardOlderCalls).isEqualTo(0)
    }
}
