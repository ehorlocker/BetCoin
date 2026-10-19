package com.betcoin.data.repository.local

import androidx.room.withTransaction
import com.betcoin.data.database.BetCoinDatabase
import com.betcoin.data.database.entity.Bet
import com.betcoin.data.database.entity.BetOutcome
import com.betcoin.data.database.entity.BetParticipant
import com.betcoin.data.database.entity.User
import com.betcoin.data.model.BetStatus
import com.betcoin.data.model.BetWithDetails
import com.betcoin.data.model.InsufficientBalanceException
import com.betcoin.data.model.InvalidBetStateException
import com.betcoin.data.model.InvalidInputException
import com.betcoin.data.model.UnanimousPickException
import com.betcoin.data.payout.PayoutCalculator
import com.betcoin.data.payout.PayoutEntry
import com.betcoin.data.repository.BetRepository
import kotlinx.coroutines.flow.Flow
import java.util.Locale
import javax.inject.Inject

/**
 * Room-backed implementation of [BetRepository].
 *
 * Every mutation checks all of its rules inside a Room transaction before writing anything, so a
 * thrown [com.betcoin.data.model.BetCoinException] leaves the database unchanged.
 */
class LocalBetRepository @Inject constructor(
    private val database: BetCoinDatabase,
) : BetRepository {

    private val betDao = database.betDao()
    private val outcomeDao = database.betOutcomeDao()
    private val participantDao = database.betParticipantDao()
    private val userDao = database.userDao()

    override suspend fun createBet(prompt: String, outcomes: List<String>): Long {
        val trimmedPrompt = prompt.trim()
        if (trimmedPrompt.length !in PROMPT_LENGTH) {
            throw InvalidInputException("Prompt must be 1–120 characters")
        }
        if (outcomes.size !in OUTCOME_COUNT) {
            throw InvalidInputException("A bet needs 2–8 outcomes")
        }
        val trimmedOutcomes = outcomes.map { it.trim() }
        if (trimmedOutcomes.any { it.length !in OUTCOME_LENGTH }) {
            throw InvalidInputException("Each outcome must be 1–40 characters")
        }
        if (trimmedOutcomes.distinctBy { it.lowercase(Locale.ROOT) }.size != trimmedOutcomes.size) {
            throw InvalidInputException("Outcomes must be unique")
        }

        return database.withTransaction {
            val betId = betDao.insert(
                Bet(
                    prompt = trimmedPrompt,
                    status = BetStatus.PENDING_CONFIRMATION,
                    createdAt = System.currentTimeMillis(),
                )
            )
            outcomeDao.insertAll(trimmedOutcomes.map { BetOutcome(betId = betId, outcomeText = it) })
            betId
        }
    }

    override suspend fun addParticipant(betId: Long, userId: Long, outcomeId: Long, wagerAmount: Long) {
        database.withTransaction {
            requirePendingBet(betId)
            if (outcomeDao.getByBetId(betId).none { it.id == outcomeId }) {
                throw InvalidInputException("Outcome $outcomeId doesn't belong to bet $betId")
            }
            val user = userDao.getById(userId)
                ?: throw InvalidInputException("User $userId not found")
            if (participantDao.getByUserIdAndBetId(userId, betId) != null) {
                throw InvalidInputException("User $userId has already joined bet $betId")
            }
            if (wagerAmount < 1) {
                throw InvalidInputException("Wager must be at least 1")
            }
            // A balance of 0 or less can't cover even the minimum wager, so this also keeps
            // users with no money out of the bet.
            if (wagerAmount > user.balance) {
                throw InsufficientBalanceException(userId, wagerAmount, user.balance)
            }
            participantDao.insert(
                BetParticipant(
                    betId = betId,
                    userId = userId,
                    outcomeId = outcomeId,
                    wagerAmount = wagerAmount,
                )
            )
        }
    }

    override suspend fun removeParticipant(participantId: Long) {
        database.withTransaction {
            val participant = participantDao.getById(participantId)
                ?: throw InvalidInputException("Participant $participantId not found")
            requirePendingBet(participant.betId)
            participantDao.delete(participant)
        }
    }

    override fun getBetWithDetails(betId: Long): Flow<BetWithDetails?> =
        betDao.getByIdWithDetails(betId)

    override fun getActiveBets(): Flow<List<BetWithDetails>> =
        betDao.getByStatusWithDetails(BetStatus.ACTIVE)

    override fun getAllBets(): Flow<List<BetWithDetails>> =
        betDao.getAllExcludingStatusWithDetails(BetStatus.PENDING_CONFIRMATION)

    override fun getPendingBet(): Flow<BetWithDetails?> =
        betDao.getNewestByStatusWithDetails(BetStatus.PENDING_CONFIRMATION)

    override suspend fun discardPendingBet(betId: Long) {
        database.withTransaction {
            val bet = requirePendingBet(betId)
            participantDao.deleteByBetId(betId)
            outcomeDao.deleteByBetId(betId)
            betDao.delete(bet)
        }
    }

    override suspend fun discardOlderPendingBets() {
        database.withTransaction {
            betDao.getPendingBets()
                .sortedWith(compareByDescending<Bet> { it.createdAt }.thenByDescending { it.id })
                .drop(1)
                .forEach { bet ->
                    participantDao.deleteByBetId(bet.id)
                    outcomeDao.deleteByBetId(bet.id)
                    betDao.delete(bet)
                }
        }
    }

    override suspend fun lockBet(betId: Long) {
        database.withTransaction {
            val bet = requirePendingBet(betId)
            val participants = participantDao.getByBetId(betId)
            if (participants.size < MIN_PARTICIPANTS) {
                throw InvalidInputException("A bet needs at least 2 participants")
            }
            if (participants.distinctBy { it.outcomeId }.size == 1) {
                throw UnanimousPickException()
            }

            // Check every wager before deducting any, so a failure writes nothing.
            val deductions = participants.map { participant ->
                val userId = participant.userId
                    ?: throw InvalidInputException("Participant ${participant.id} no longer exists")
                val user = userDao.getById(userId)
                    ?: throw InvalidInputException("User $userId not found")
                if (participant.wagerAmount > user.balance) {
                    throw InsufficientBalanceException(userId, participant.wagerAmount, user.balance)
                }
                user to participant.wagerAmount
            }

            deductions.forEach { (user, wager) -> userDao.updateBalance(user.id, user.balance - wager) }
            betDao.update(bet.copy(status = BetStatus.ACTIVE))
        }
    }

    /** Returns the bet, or throws unless it exists and is `PENDING_CONFIRMATION`. */
    private suspend fun requirePendingBet(betId: Long): Bet =
        requireBetInStatus(betId, BetStatus.PENDING_CONFIRMATION)

    override suspend fun resolveBet(betId: Long, winningOutcomeId: Long) {
        database.withTransaction {
            val bet = requireBetInStatus(betId, BetStatus.ACTIVE)
            if (outcomeDao.getByBetId(betId).none { it.id == winningOutcomeId }) {
                throw InvalidInputException("Outcome $winningOutcomeId doesn't belong to bet $betId")
            }
            val participants = participantDao.getByBetId(betId)
            val users = requireParticipantUsers(participants)
            val result = PayoutCalculator.calculate(
                participants.map { PayoutEntry(it.id, it.outcomeId, it.wagerAmount) },
                winningOutcomeId,
            )

            participants.forEach { participant ->
                val payout = result.payouts.getValue(participant.id)
                val user = users.getValue(participant.id)
                val won = participant.outcomeId == winningOutcomeId
                userDao.update(
                    when {
                        result.isPush -> user.copy(balance = user.balance + payout)
                        won -> user.copy(
                            balance = user.balance + payout,
                            totalWins = user.totalWins + 1,
                            totalEarnings = user.totalEarnings + payout - participant.wagerAmount,
                        )
                        else -> user.copy(
                            totalLosses = user.totalLosses + 1,
                            totalLost = user.totalLost + participant.wagerAmount,
                        )
                    }
                )
                participantDao.update(participant.copy(payout = payout))
            }
            betDao.update(
                bet.copy(
                    status = BetStatus.RESOLVED,
                    winningOutcomeId = winningOutcomeId,
                    resolvedAt = System.currentTimeMillis(),
                )
            )
        }
    }

    override suspend fun cancelBet(betId: Long) {
        database.withTransaction {
            val bet = requireBetInStatus(betId, BetStatus.ACTIVE)
            val participants = participantDao.getByBetId(betId)
            val users = requireParticipantUsers(participants)

            participants.forEach { participant ->
                val user = users.getValue(participant.id)
                userDao.updateBalance(user.id, user.balance + participant.wagerAmount)
            }
            betDao.update(
                bet.copy(status = BetStatus.CANCELLED, resolvedAt = System.currentTimeMillis())
            )
        }
    }

    /** Same rules and exceptions as [cancelBet]; consent is the caller's concern. */
    override suspend fun forceCancelBet(betId: Long) = cancelBet(betId)

    override suspend fun reopenBet(betId: Long) {
        database.withTransaction {
            val bet = betDao.getById(betId) ?: throw InvalidInputException("Bet $betId not found")
            when (bet.status) {
                BetStatus.RESOLVED -> reopenResolved(bet)
                BetStatus.CANCELLED -> reopenCancelled(bet)
                else -> throw InvalidBetStateException(betId, bet.status)
            }
        }
    }

    /**
     * Takes back every payout (losers' is 0) and reverses the §5.8 stats that [resolveBet]
     * applied, so the bet's coins are locked in the pot again. Must run inside a transaction.
     */
    private suspend fun reopenResolved(bet: Bet) {
        val winningOutcomeId =
            checkNotNull(bet.winningOutcomeId) { "Resolved bet ${bet.id} has no winner" }
        val participants = participantDao.getByBetId(bet.id)
        val users = requireParticipantUsers(participants)
        val payouts = participants.associate { participant ->
            participant.id to checkNotNull(participant.payout) {
                "Participant ${participant.id} of resolved bet ${bet.id} has no payout"
            }
        }
        // Check every balance before deducting any, so a failure writes nothing.
        participants.forEach { participant ->
            val user = users.getValue(participant.id)
            val payout = payouts.getValue(participant.id)
            if (user.balance < payout) {
                throw InsufficientBalanceException(user.id, payout, user.balance)
            }
        }

        val isPush = participants.none { it.outcomeId == winningOutcomeId }
        participants.forEach { participant ->
            val payout = payouts.getValue(participant.id)
            val user = users.getValue(participant.id)
            val won = participant.outcomeId == winningOutcomeId
            userDao.update(
                when {
                    isPush -> user.copy(balance = user.balance - payout)
                    won -> user.copy(
                        balance = user.balance - payout,
                        totalWins = user.totalWins - 1,
                        totalEarnings = user.totalEarnings - (payout - participant.wagerAmount),
                    )
                    else -> user.copy(
                        totalLosses = user.totalLosses - 1,
                        totalLost = user.totalLost - participant.wagerAmount,
                    )
                }
            )
            participantDao.update(participant.copy(payout = null))
        }
        betDao.update(bet.copy(status = BetStatus.ACTIVE, winningOutcomeId = null, resolvedAt = null))
    }

    /** Re-deducts every wager refunded by [cancelBet]. Must run inside a transaction. */
    private suspend fun reopenCancelled(bet: Bet) {
        val participants = participantDao.getByBetId(bet.id)
        val users = requireParticipantUsers(participants)
        // Check every balance before deducting any, so a failure writes nothing.
        participants.forEach { participant ->
            val user = users.getValue(participant.id)
            if (user.balance < participant.wagerAmount) {
                throw InsufficientBalanceException(user.id, participant.wagerAmount, user.balance)
            }
        }

        participants.forEach { participant ->
            val user = users.getValue(participant.id)
            userDao.updateBalance(user.id, user.balance - participant.wagerAmount)
        }
        betDao.update(bet.copy(status = BetStatus.ACTIVE, resolvedAt = null))
    }

    /** Returns the bet, or throws unless it exists and is in [status]. */
    private suspend fun requireBetInStatus(betId: Long, status: BetStatus): Bet {
        val bet = betDao.getById(betId) ?: throw InvalidInputException("Bet $betId not found")
        if (bet.status != status) {
            throw InvalidBetStateException(betId, bet.status)
        }
        return bet
    }

    /**
     * Returns each participant's user keyed by participant id, or throws if any of them has been
     * deleted (their PIN can't be entered, so only the admin can act on the bet).
     */
    private suspend fun requireParticipantUsers(
        participants: List<BetParticipant>,
    ): Map<Long, User> = participants.associate { participant ->
        val userId = participant.userId
            ?: throw InvalidInputException("Participant ${participant.id} no longer exists")
        val user = userDao.getById(userId) ?: throw InvalidInputException("User $userId not found")
        participant.id to user
    }

    /** Same rules and exceptions as [resolveBet]; PIN checks are the caller's concern. */
    override suspend fun adminResolveBet(betId: Long, winningOutcomeId: Long) =
        resolveBet(betId, winningOutcomeId)

    override suspend fun updateBetPrompt(betId: Long, newPrompt: String) {
        val trimmedPrompt = newPrompt.trim()
        if (trimmedPrompt.length !in PROMPT_LENGTH) {
            throw InvalidInputException("Prompt must be 1–120 characters")
        }
        database.withTransaction {
            val bet = betDao.getById(betId) ?: throw InvalidInputException("Bet $betId not found")
            betDao.update(bet.copy(prompt = trimmedPrompt))
        }
    }

    override suspend fun adminRemoveParticipant(betId: Long, participantId: Long) {
        database.withTransaction {
            requireBetInStatus(betId, BetStatus.ACTIVE)
            val participant = participantDao.getById(participantId)
            if (participant == null || participant.betId != betId) {
                throw InvalidInputException("Participant $participantId not found in bet $betId")
            }
            val remaining = participantDao.getByBetId(betId).filter { it.id != participantId }
            if (remaining.size < MIN_PARTICIPANTS) {
                throw InvalidInputException("A bet needs at least 2 participants; force cancel it instead")
            }
            if (remaining.distinctBy { it.outcomeId }.size == 1) {
                throw UnanimousPickException()
            }
            val userId = participant.userId
                ?: throw InvalidInputException("User for participant $participantId no longer exists")
            val user = userDao.getById(userId) ?: throw InvalidInputException("User $userId not found")
            userDao.updateBalance(user.id, user.balance + participant.wagerAmount)
            participantDao.delete(participant)
        }
    }

    override suspend fun adminEditWager(participantId: Long, newWagerAmount: Long) {
        database.withTransaction {
            val participant = participantDao.getById(participantId)
                ?: throw InvalidInputException("Participant $participantId not found")
            requireBetInStatus(participant.betId, BetStatus.ACTIVE)
            if (newWagerAmount < 1) {
                throw InvalidInputException("Wager must be at least 1")
            }
            val userId = participant.userId
                ?: throw InvalidInputException("User for participant $participantId no longer exists")
            val user = userDao.getById(userId) ?: throw InvalidInputException("User $userId not found")
            val diff = newWagerAmount - participant.wagerAmount
            // Only increases can be unaffordable; a decrease is always allowed.
            if (diff > 0 && diff > user.balance) {
                throw InsufficientBalanceException(user.id, diff, user.balance)
            }
            userDao.updateBalance(user.id, user.balance - diff)
            participantDao.update(participant.copy(wagerAmount = newWagerAmount))
        }
    }

    override suspend fun deleteBet(betId: Long) {
        database.withTransaction {
            val bet = betDao.getById(betId) ?: throw InvalidInputException("Bet $betId not found")
            if (bet.status != BetStatus.RESOLVED && bet.status != BetStatus.CANCELLED) {
                throw InvalidBetStateException(betId, bet.status)
            }
            participantDao.deleteByBetId(betId)
            outcomeDao.deleteByBetId(betId)
            betDao.delete(bet)
        }
    }

    override suspend fun repeatBet(betId: Long): Long = database.withTransaction {
        val original = betDao.getById(betId) ?: throw InvalidInputException("Bet $betId not found")
        betDao.getPendingBets().firstOrNull()?.let { pending ->
            throw InvalidBetStateException(pending.id, pending.status)
        }
        val newBetId = betDao.insert(
            Bet(
                prompt = original.prompt,
                status = BetStatus.PENDING_CONFIRMATION,
                createdAt = System.currentTimeMillis(),
            )
        )
        outcomeDao.insertAll(
            outcomeDao.getByBetId(betId)
                .sortedBy { it.id }
                .map { BetOutcome(betId = newBetId, outcomeText = it.outcomeText) }
        )
        newBetId
    }

    private companion object {
        val PROMPT_LENGTH = 1..120
        val OUTCOME_COUNT = 2..8
        val OUTCOME_LENGTH = 1..40
        const val MIN_PARTICIPANTS = 2
    }
}
