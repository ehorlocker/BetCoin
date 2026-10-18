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
     * inserted alongside the bet. The prompt must be 1–120 characters and there
     * must be 2–8 outcomes of 1–40 characters each, unique ignoring case (all
     * measured after trimming); otherwise throws `InvalidInputException`.
     *
     * @return the new bet's row ID.
     */
    suspend fun createBet(prompt: String, outcomes: List<String>): Long

    /**
     * Adds a participant to [betId] who picks [outcomeId] with [wagerAmount].
     *
     * The bet must be `PENDING_CONFIRMATION`, the user can join it only once,
     * and the wager must be at least 1 and at most the user's current balance.
     * No balance changes until [lockBet].
     */
    suspend fun addParticipant(betId: Long, userId: Long, outcomeId: Long, wagerAmount: Long)

    /**
     * Removes a participant before the bet is locked. Throws
     * `InvalidBetStateException` if the bet isn't `PENDING_CONFIRMATION`.
     */
    suspend fun removeParticipant(participantId: Long)

    /**
     * Observes a single bet with its outcomes and participants. Emits null if
     * the bet doesn't exist, including after it is deleted or discarded.
     */
    fun getBetWithDetails(betId: Long): Flow<BetWithDetails?>

    /**
     * Observes all bets with `ACTIVE` status.
     */
    fun getActiveBets(): Flow<List<BetWithDetails>>

    /**
     * Observes all bets except `PENDING_CONFIRMATION` ones, newest first.
     */
    fun getAllBets(): Flow<List<BetWithDetails>>

    /**
     * Observes the `PENDING_CONFIRMATION` bet left by an interrupted New Bet
     * flow, or null if there is none. If there are several, emits the newest.
     */
    fun getPendingBet(): Flow<BetWithDetails?>

    /**
     * Deletes a `PENDING_CONFIRMATION` bet with its participants and outcomes.
     * Throws `InvalidBetStateException` if the bet isn't pending.
     *
     * Implementation must run inside a transaction.
     */
    suspend fun discardPendingBet(betId: Long)

    /**
     * Locks the bet: validates participants, deducts wagers from balances,
     * and transitions status to `ACTIVE`.
     *
     * Requires at least 2 participants not all on the same outcome
     * (`UnanimousPickException`), and every wager must still be covered by the
     * user's current balance (`InsufficientBalanceException`).
     *
     * Implementation must run inside a transaction.
     */
    suspend fun lockBet(betId: Long)

    /**
     * Resolves the bet: calculates payouts, distributes winnings, updates
     * user stats, and transitions status to `RESOLVED`.
     *
     * The bet must be `ACTIVE` (`InvalidBetStateException`) and
     * [winningOutcomeId] must be one of its outcomes, picked or not. Each
     * participant's `payout` is credited (winners' share of the pot, 0 for
     * losers). If nobody picked [winningOutcomeId] it's a push: every wager is
     * refunded and no W/L stats change. Sets `winningOutcomeId` and `resolvedAt`.
     *
     * Implementation must run inside a transaction.
     */
    suspend fun resolveBet(betId: Long, winningOutcomeId: Long)

    /**
     * Cancels the bet: refunds all wagers and transitions status to `CANCELLED`.
     *
     * The bet must be `ACTIVE` (`InvalidBetStateException`). Sets `resolvedAt`
     * and changes no W/L stats.
     *
     * Implementation must run inside a transaction.
     */
    suspend fun cancelBet(betId: Long)

    /**
     * Admin-only cancel: same as [cancelBet] but requires no participant consent.
     *
     * Only `ACTIVE` bets can be cancelled; any other status throws
     * [com.betcoin.data.model.InvalidBetStateException]. An unknown bet or a deleted participant
     * throws [com.betcoin.data.model.InvalidInputException].
     *
     * Implementation must run inside a transaction.
     */
    suspend fun forceCancelBet(betId: Long)

    /**
     * Reopens a resolved or cancelled bet: reverses payouts/refunds, clears
     * resolution data, and transitions status back to `ACTIVE`.
     *
     * From `RESOLVED`, deducts each participant's `payout` (losers' is 0),
     * reverses the W/L stats the resolution applied, and nulls every `payout`,
     * `winningOutcomeId` and `resolvedAt`. From `CANCELLED`, re-deducts every
     * wager. Throws `InsufficientBalanceException` if a participant's balance
     * can't cover their payout (or wager), `InvalidInputException` if a
     * participant has been deleted, and `InvalidBetStateException` for any other
     * status. Participant PINs must be verified beforehand.
     *
     * Implementation must run inside a transaction.
     */
    suspend fun reopenBet(betId: Long)

    /**
     * Admin-only resolve: same as [resolveBet] but requires no participant PINs.
     *
     * Only `ACTIVE` bets can be resolved ([com.betcoin.data.model.InvalidBetStateException]
     * otherwise). An unknown bet, or an outcome that belongs to another bet, throws
     * [com.betcoin.data.model.InvalidInputException].
     *
     * Implementation must run inside a transaction.
     */
    suspend fun adminResolveBet(betId: Long, winningOutcomeId: Long)

    /**
     * Admin operation: edits the bet's prompt text. Allowed in any status.
     *
     * The prompt is trimmed and must be 1-120 characters, and the bet must exist; otherwise
     * [com.betcoin.data.model.InvalidInputException] is thrown.
     */
    suspend fun updateBetPrompt(betId: Long, newPrompt: String)

    /**
     * Admin operation: removes a participant from an ACTIVE bet and refunds
     * their wager.
     *
     * Throws [InvalidInputException] if the bet, participant or user is missing, the
     * participant belongs to another bet, or fewer than 2 participants would remain;
     * [InvalidBetStateException] if the bet is not ACTIVE; and [UnanimousPickException]
     * if everyone left would be on the same outcome.
     *
     * Implementation must run inside a transaction.
     */
    suspend fun adminRemoveParticipant(betId: Long, participantId: Long)

    /**
     * Admin operation: adjusts a participant's wager to [newWagerAmount], refunding
     * or deducting the balance difference.
     *
     * Throws [InvalidInputException] if the participant or user is missing or the
     * wager is below 1, [InvalidBetStateException] if the bet is not ACTIVE, and
     * [InsufficientBalanceException] if an increase exceeds the user's balance.
     *
     * Implementation must run inside a transaction.
     */
    suspend fun adminEditWager(participantId: Long, newWagerAmount: Long)

    /**
     * Deletes a bet and its outcomes and participants. Only allowed for
     * `RESOLVED` or `CANCELLED` bets; any other status throws
     * [com.betcoin.data.model.InvalidBetStateException]. An unknown bet throws
     * [com.betcoin.data.model.InvalidInputException]. User balances and stats are untouched.
     */
    suspend fun deleteBet(betId: Long)

    /**
     * Creates a new bet with the same prompt and outcomes as [betId], but no
     * participants. The new bet is `PENDING_CONFIRMATION` and the original is
     * unchanged. Throws `InvalidBetStateException` (naming the pending bet) if a
     * `PENDING_CONFIRMATION` bet already exists.
     *
     * @return the new bet's row ID.
     */
    suspend fun repeatBet(betId: Long): Long
}
