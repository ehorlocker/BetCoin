package com.betcoin.ui.home

import com.betcoin.data.database.entity.Bet
import com.betcoin.data.database.entity.BetOutcome
import com.betcoin.data.model.BetStatus
import com.betcoin.data.model.BetWithDetails
import com.betcoin.data.model.ParticipantWithUser
import com.betcoin.data.repository.BetRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

private fun mockBetWithDetails(): BetWithDetails = BetWithDetails(
    bet = Bet(
        id = 1,
        prompt = "Mock",
        status = BetStatus.PENDING_CONFIRMATION,
        createdAt = System.currentTimeMillis()
    ),
    outcomes = emptyList<BetOutcome>(),
    participants = emptyList<ParticipantWithUser>()
)

/**
 * Fake implementation of [BetRepository] for testing.
 */
class FakeBetRepository : BetRepository {
    private val activeBets = MutableStateFlow<List<BetWithDetails>>(emptyList())

    var activeBetCount: Int
        get() = activeBets.value.size
        set(value) { activeBets.value = List(value) { mockBetWithDetails() } }

    /** Number of collectors currently subscribed to [getActiveBets]. */
    val activeBetsSubscriberCount: Int
        get() = activeBets.subscriptionCount.value

    override fun getActiveBets(): Flow<List<BetWithDetails>> = activeBets

    override fun getAllBets(): Flow<List<BetWithDetails>> = flowOf(emptyList())
    override fun getPendingBet(): Flow<BetWithDetails?> = flowOf(null)
    override suspend fun discardPendingBet(betId: Long) {}
    override suspend fun discardOlderPendingBets() {}
    override fun getBetWithDetails(betId: Long): Flow<BetWithDetails> = flowOf(mockBetWithDetails())
    override suspend fun createBet(prompt: String, outcomes: List<String>): Long = 0L
    override suspend fun addParticipant(betId: Long, userId: Long, outcomeId: Long, wagerAmount: Long) {}
    override suspend fun removeParticipant(participantId: Long) {}
    override suspend fun lockBet(betId: Long) {}
    override suspend fun resolveBet(betId: Long, winningOutcomeId: Long) {}
    override suspend fun cancelBet(betId: Long) {}
    override suspend fun forceCancelBet(betId: Long) {}
    override suspend fun reopenBet(betId: Long) {}
    override suspend fun adminResolveBet(betId: Long, winningOutcomeId: Long) {}
    override suspend fun updateBetPrompt(betId: Long, newPrompt: String) {}
    override suspend fun adminRemoveParticipant(betId: Long, participantId: Long) {}
    override suspend fun adminEditWager(participantId: Long, newWagerAmount: Long) {}
    override suspend fun deleteBet(betId: Long) {}
    override suspend fun repeatBet(betId: Long): Long = 0L
}
