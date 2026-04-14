package com.betcoin.data.repository.local

import com.betcoin.data.model.BetWithDetails
import com.betcoin.data.repository.BetRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Room-backed implementation of [BetRepository].
 *
 * Methods will be implemented in later tickets (KAN-13, KAN-14).
 */
class LocalBetRepository @Inject constructor() : BetRepository {
    override suspend fun createBet(prompt: String, outcomes: List<String>): Long = TODO()
    override suspend fun addParticipant(betId: Long, userId: Long, outcomeId: Long, wager: Long): Unit = TODO()
    override suspend fun removeParticipant(participantId: Long): Unit = TODO()
    override fun getBetWithDetails(betId: Long): Flow<BetWithDetails> = TODO()
    override fun getActiveBets(): Flow<List<BetWithDetails>> = TODO()
    override fun getAllBets(): Flow<List<BetWithDetails>> = TODO()
    override suspend fun lockBet(betId: Long): Unit = TODO()
    override suspend fun resolveBet(betId: Long, winningOutcomeId: Long): Unit = TODO()
    override suspend fun cancelBet(betId: Long): Unit = TODO()
    override suspend fun forceCancelBet(betId: Long): Unit = TODO()
    override suspend fun reopenBet(betId: Long): Unit = TODO()
    override suspend fun adminResolveBet(betId: Long, winningOutcomeId: Long): Unit = TODO()
    override suspend fun updateBetPrompt(betId: Long, newPrompt: String): Unit = TODO()
    override suspend fun adminRemoveParticipant(betId: Long, participantId: Long): Unit = TODO()
    override suspend fun adminEditWager(participantId: Long, newWager: Long): Unit = TODO()
    override suspend fun deleteBet(betId: Long): Unit = TODO()
    override suspend fun repeatBet(betId: Long): Long = TODO()
}
