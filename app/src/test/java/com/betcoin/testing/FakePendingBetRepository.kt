package com.betcoin.testing

import com.betcoin.data.database.entity.Bet
import com.betcoin.data.database.entity.BetOutcome
import com.betcoin.data.database.entity.BetParticipant
import com.betcoin.data.model.BetCoinException
import com.betcoin.data.model.BetStatus
import com.betcoin.data.model.BetWithDetails
import com.betcoin.data.model.ParticipantWithUser
import com.betcoin.data.repository.BetRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * [BetRepository] fake for pending-bet (launch routing and Recovery) tests. The newest pending
 * bet is the one with the highest `createdAt`, ties broken by the highest id.
 */
class FakePendingBetRepository : BetRepository {
    val pendingBets = MutableStateFlow<List<BetWithDetails>>(emptyList())

    /** Number of [discardOlderPendingBets] calls. */
    var discardOlderCalls = 0
        private set

    /** When set, [discardOlderPendingBets] throws it. */
    var discardOlderError: Throwable? = null

    /** When set, the [getPendingBet] flow throws it on collection. */
    var getPendingBetError: Throwable? = null

    /** IDs passed to [discardPendingBet], in call order. */
    val discardedBetIds = mutableListOf<Long>()

    /** When set, [discardPendingBet] throws it instead of discarding. */
    var discardError: Throwable? = null

    /** Builds a pending bet with [participantCount] participants. */
    fun pendingBet(id: Long, prompt: String, participantCount: Int, createdAt: Long = id): BetWithDetails {
        val outcomes = listOf(
            BetOutcome(id = id * 100 + 1, betId = id, outcomeText = "Yes"),
            BetOutcome(id = id * 100 + 2, betId = id, outcomeText = "No"),
        )
        return BetWithDetails(
            bet = Bet(id = id, prompt = prompt, status = BetStatus.PENDING_CONFIRMATION, createdAt = createdAt),
            outcomes = outcomes,
            participants = (1..participantCount).map { n ->
                ParticipantWithUser(
                    participant = BetParticipant(
                        id = id * 1000 + n,
                        betId = id,
                        userId = null,
                        outcomeId = outcomes[n % 2].id,
                        wagerAmount = 10L,
                        deletedUsername = "Player $n",
                    ),
                    user = null,
                    outcome = outcomes[n % 2],
                )
            },
        )
    }

    private fun List<BetWithDetails>.newest(): BetWithDetails? =
        maxWithOrNull(compareBy({ it.bet.createdAt }, { it.bet.id }))

    override fun getPendingBet(): Flow<BetWithDetails?> {
        val error = getPendingBetError
        return if (error != null) flow { throw error } else pendingBets.map { it.newest() }
    }

    override suspend fun discardOlderPendingBets() {
        discardOlderCalls++
        discardOlderError?.let { throw it }
        pendingBets.value = listOfNotNull(pendingBets.value.newest())
    }

    override suspend fun discardPendingBet(betId: Long) {
        discardError?.let { throw it }
        discardedBetIds += betId
        pendingBets.value = pendingBets.value.filterNot { it.bet.id == betId }
    }

    override suspend fun createBet(prompt: String, outcomes: List<String>): Long = error("Not used")
    override suspend fun addParticipant(betId: Long, userId: Long, outcomeId: Long, wagerAmount: Long) = error("Not used")
    override suspend fun removeParticipant(participantId: Long) = error("Not used")
    override fun getBetWithDetails(betId: Long): Flow<BetWithDetails?> =
        pendingBets.map { list -> list.find { it.bet.id == betId } }
    override fun getActiveBets(): Flow<List<BetWithDetails>> = flowOf(emptyList())
    override fun getAllBets(): Flow<List<BetWithDetails>> = flowOf(emptyList())
    override suspend fun lockBet(betId: Long) = error("Not used")
    override suspend fun resolveBet(betId: Long, winningOutcomeId: Long) = error("Not used")
    override suspend fun cancelBet(betId: Long) = error("Not used")
    override suspend fun forceCancelBet(betId: Long) = error("Not used")
    override suspend fun reopenBet(betId: Long) = error("Not used")
    override suspend fun adminResolveBet(betId: Long, winningOutcomeId: Long) = error("Not used")
    override suspend fun updateBetPrompt(betId: Long, newPrompt: String) = error("Not used")
    override suspend fun adminRemoveParticipant(betId: Long, participantId: Long) = error("Not used")
    override suspend fun adminEditWager(participantId: Long, newWagerAmount: Long) = error("Not used")
    override suspend fun deleteBet(betId: Long) = error("Not used")
    override suspend fun repeatBet(betId: Long): Long = error("Not used")
}
