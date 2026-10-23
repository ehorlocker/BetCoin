package com.betcoin.devtools

import com.betcoin.data.repository.BetRepository
import com.betcoin.data.repository.SettingsRepository
import com.betcoin.data.repository.UserRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Dev-only seeder that fills the database with a fixed sample data set, written through the
 * repositories so every invariant (hashing, payouts, stats) holds.
 */
class DevDataSeeder @Inject constructor(
    private val userRepository: UserRepository,
    private val betRepository: BetRepository,
    private val settingsRepository: SettingsRepository,
    private val databaseTools: DevDatabaseTools,
) {

    /**
     * Wipes the database, then seeds the fixed sample set.
     *
     * @return the plain admin recovery code.
     */
    suspend fun seedSampleData(): String {
        databaseTools.wipe()
        settingsRepository.setAdminPin(ADMIN_PIN)
        val recoveryCode = settingsRepository.regenerateRecoveryCode()

        val ids = (SAMPLE_PLAYERS + TEMP_PLAYER).associate { (name, pin) ->
            name to userRepository.createUser(name, pin)
        }
        val austin = ids.getValue("Austin")
        val sam = ids.getValue("Sam")
        val jay = ids.getValue("Jay")
        val eric = ids.getValue("Eric")
        val mike = ids.getValue("Mike")
        val taylor = ids.getValue(TEMP_PLAYER.first)

        // Bet A: resolved.
        val betA = betRepository.createBet("Who wins Friday's chess match?", listOf("Austin", "Sam"))
        join(betA, austin, "Austin", 200)
        join(betA, sam, "Sam", 150)
        betRepository.lockBet(betA)
        betRepository.resolveBet(betA, outcomeId(betA, "Austin"))

        // Bet B: cancelled.
        val betB = betRepository.createBet("Will it rain on Saturday?", listOf("Yes", "No"))
        join(betB, jay, "Yes", 100)
        join(betB, eric, "No", 100)
        betRepository.lockBet(betB)
        betRepository.cancelBet(betB)

        // Bet C: resolved, then one participant is deleted.
        val betC = betRepository.createBet("Who sets the Tetris high score?", listOf("Taylor", "Eric"))
        join(betC, taylor, "Taylor", 100)
        join(betC, eric, "Eric", 100)
        betRepository.lockBet(betC)
        betRepository.resolveBet(betC, outcomeId(betC, "Eric"))
        userRepository.deleteUser(taylor)

        // Bet D: active, with one unpicked outcome.
        val betD = betRepository.createBet("Who wins the pool tournament?", listOf("Austin", "Sam", "Jay"))
        join(betD, austin, "Austin", 100)
        join(betD, sam, "Sam", 100)
        betRepository.lockBet(betD)

        // Mike: two bailouts, then drained so he is bailout-eligible.
        userRepository.forceBailout(mike)
        userRepository.forceBailout(mike)
        userRepository.setBalance(mike, MIKE_BALANCE)

        return recoveryCode
    }

    /**
     * Creates a `PENDING_CONFIRMATION` bet with two participants on different outcomes.
     *
     * @throws IllegalStateException with a user-facing message if fewer than two players have
     *   a balance of at least 1, or a pending bet already exists. Nothing is written then.
     */
    suspend fun seedPendingBet() {
        check(betRepository.getPendingBet().first() == null) {
            "A pending bet already exists. Resolve or discard it first."
        }
        val players = userRepository.getAllUsers().first().filter { it.balance >= 1 }
        check(players.size >= 2) { "Need at least 2 players with a balance to seed a pending bet." }
        val (first, second) = players

        val betId = betRepository.createBet("Who wins the next coin flip?", listOf("Heads", "Tails"))
        try {
            join(betId, first.id, "Heads", minOf(PENDING_WAGER, first.balance))
            join(betId, second.id, "Tails", minOf(PENDING_WAGER, second.balance))
        } catch (e: Exception) {
            // Also runs when this coroutine was cancelled, so no malformed bet is left behind.
            withContext(NonCancellable) {
                try {
                    betRepository.discardPendingBet(betId)
                } catch (cleanup: CancellationException) {
                    throw cleanup
                } catch (_: Exception) {
                    // Keep the original failure.
                }
            }
            throw e
        }
    }

    private suspend fun outcomeId(betId: Long, label: String): Long =
        betRepository.getBetWithDetails(betId).first()!!
            .outcomes.first { it.outcomeText == label }.id

    private suspend fun join(betId: Long, userId: Long, label: String, wager: Long) =
        betRepository.addParticipant(betId, userId, outcomeId(betId, label), wager)

    companion object {
        private const val ADMIN_PIN = "0000"
        private const val MIKE_BALANCE = 40L
        private const val PENDING_WAGER = 50L

        /** The five permanent sample players as name to PIN. */
        val SAMPLE_PLAYERS: List<Pair<String, String>> = listOf(
            "Austin" to "1111",
            "Sam" to "2222",
            "Jay" to "3333",
            "Eric" to "4444",
            "Mike" to "5555",
        )

        private val TEMP_PLAYER = "Taylor" to "6666"
    }
}
