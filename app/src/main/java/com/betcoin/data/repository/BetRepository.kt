package com.betcoin.data.repository

import com.betcoin.data.model.BetWithDetails
import kotlinx.coroutines.flow.Flow

/**
 * Contract for bet lifecycle operations.
 *
 * Implementations handle bet creation, locking, resolution, cancellation,
 * admin operations, and history queries.
 */
interface BetRepository {

    /**
     * Creates a new bet with the given [prompt] and [outcomes] (text labels).
     *
     * The bet starts in `PENDING_CONFIRMATION` status. Outcome rows are
     * inserted alongside the bet.
     *
     * @return the new bet's row ID.
     */
    suspend fun createBet(prompt: String, outcomes: List<String>): Long

    /**
     * Adds a participant to [betId] who picks [outcomeId] with [wager].
     */
    suspend fun addParticipant(betId: Long, userId: Long, outcomeId: Long, wager: Long)

    /**
     * Removes a participant before the bet is locked.
     */
    suspend fun removeParticipant(participantId: Long)

    /**
     * Observes a single bet with its outcomes and participants.
     */
    fun getBetWithDetails(betId: Long): Flow<BetWithDetails>

    /**
     * Observes all bets with `ACTIVE` status.
     */
    fun getActiveBets(): Flow<List<BetWithDetails>>

    /**
     * Observes all bets regardless of status.
     */
    fun getAllBets(): Flow<List<BetWithDetails>>

    /**
     * Locks the bet: validates participants, deducts wagers from balances,
     * and transitions status to `ACTIVE`.
     *
     * Implementation must run inside a transaction.
     */
    suspend fun lockBet(betId: Long)

    /**
     * Resolves the bet: calculates payouts, distributes winnings, updates
     * user stats, and transitions status to `RESOLVED`.
     *
     * Implementation must run inside a transaction.
     */
    suspend fun resolveBet(betId: Long, winningOutcomeId: Long)

    /**
     * Cancels the bet: refunds all wagers and transitions status to `CANCELLED`.
     *
     * Implementation must run inside a transaction.
     */
    suspend fun cancelBet(betId: Long)

    /**
     * Admin-only cancel: same as [cancelBet] but requires no participant consent.
     *
     * Implementation must run inside a transaction.
     */
    suspend fun forceCancelBet(betId: Long)

    /**
     * Reopens a resolved or cancelled bet: reverses payouts/refunds, clears
     * resolution data, and transitions status back to `ACTIVE`.
     *
     * Implementation must run inside a transaction.
     */
    suspend fun reopenBet(betId: Long)

    /**
     * Admin-only resolve: same as [resolveBet] but requires no participant PINs.
     *
     * Implementation must run inside a transaction.
     */
    suspend fun adminResolveBet(betId: Long, winningOutcomeId: Long)

    /**
     * Admin operation: edits the bet's prompt text.
     */
    suspend fun updateBetPrompt(betId: Long, newPrompt: String)

    /**
     * Admin operation: removes a participant from an ACTIVE bet and refunds
     * their wager.
     *
     * Implementation must run inside a transaction.
     */
    suspend fun adminRemoveParticipant(betId: Long, participantId: Long)

    /**
     * Admin operation: adjusts a participant's wager to [newWager], refunding
     * or deducting the balance difference.
     *
     * Implementation must run inside a transaction.
     */
    suspend fun adminEditWager(participantId: Long, newWager: Long)

    /**
     * Deletes a bet and its outcomes and participants. Only allowed for
     * `RESOLVED` or `CANCELLED` bets.
     */
    suspend fun deleteBet(betId: Long)

    /**
     * Creates a new bet with the same prompt and outcomes as [betId], but no
     * participants.
     *
     * @return the new bet's row ID.
     */
    suspend fun repeatBet(betId: Long): Long
}
