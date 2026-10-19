package com.betcoin.navigation

import com.betcoin.data.repository.BetRepository
import com.betcoin.data.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Decides which route the app opens on at launch.
 *
 * First launch goes to Onboarding. Otherwise, a bet left pending by an interrupted New Bet flow
 * goes to Recovery (after discarding any older pending bets), and everything else goes to Home.
 */
class StartDestinationResolver @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val betRepository: BetRepository,
) {
    /** Returns [Routes.ONBOARDING], [Routes.RECOVERY] or [Routes.HOME]. */
    suspend fun resolve(): String {
        if (settingsRepository.isFirstLaunch()) return Routes.ONBOARDING
        betRepository.discardOlderPendingBets()
        return if (betRepository.getPendingBet().first() != null) Routes.RECOVERY else Routes.HOME
    }
}
