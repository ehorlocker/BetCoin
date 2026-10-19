package com.betcoin.navigation

import com.betcoin.data.repository.BetRepository
import com.betcoin.data.repository.SettingsRepository
import com.betcoin.util.ErrorLogger
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/**
 * Decides which route the app opens on at launch.
 *
 * First launch goes to Onboarding. Otherwise, a bet left pending by an interrupted New Bet flow
 * goes to Recovery (after discarding any older pending bets), and everything else goes to Home.
 *
 * A failure to discard older pending bets is logged and routing continues. Failures reading the
 * first-launch flag or the pending bet are thrown to the caller, which must handle them.
 */
class StartDestinationResolver @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val betRepository: BetRepository,
    private val errorLogger: ErrorLogger,
) {
    /**
     * Returns [Routes.ONBOARDING], [Routes.RECOVERY] or [Routes.HOME].
     *
     * @throws Exception if reading the first-launch flag or the pending bet fails.
     */
    suspend fun resolve(): String {
        if (settingsRepository.isFirstLaunch()) return Routes.ONBOARDING
        try {
            betRepository.discardOlderPendingBets()
        } catch (e: Exception) {
            // A stray CancellationException (e.g. a timeout inside the repository) is a failure
            // like any other; if the calling scope was cancelled this rethrows instead.
            if (e is CancellationException) currentCoroutineContext().ensureActive()
            errorLogger.logError(LAUNCH_LOG_TAG, "Failed to discard older pending bets at launch", e)
        }
        return if (betRepository.getPendingBet().first() != null) Routes.RECOVERY else Routes.HOME
    }
}
