package com.betcoin.data.repository.local

import androidx.room.withTransaction
import com.betcoin.data.database.BetCoinDatabase
import com.betcoin.data.database.entity.Bet
import com.betcoin.data.database.entity.BetOutcome
import com.betcoin.data.database.entity.BetParticipant
import com.betcoin.data.model.BetStatus
import com.betcoin.data.model.BetWithDetails
import com.betcoin.data.model.InsufficientBalanceException
import com.betcoin.data.model.InvalidBetStateException
import com.betcoin.data.model.InvalidInputException
import com.betcoin.data.model.UnanimousPickException
import com.betcoin.data.repository.BetRepository
import kotlinx.coroutines.flow.Flow
import java.util.Locale
import javax.inject.Inject

/**
 * Room-backed implementation of [BetRepository].
 *
 * Every mutation checks all of its rules inside a Room transaction before writing anything, so a
 * thrown [com.betcoin.data.model.BetCoinException] leaves the database unchanged.
 *
 * Resolve, cancel, reopen and repeat are implemented in BET-10, admin operations in BET-11.
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
    private suspend fun requirePendingBet(betId: Long): Bet {
        val bet = betDao.getById(betId) ?: throw InvalidInputException("Bet $betId not found")
        if (bet.status != BetStatus.PENDING_CONFIRMATION) {
            throw InvalidBetStateException(betId, bet.status)
        }
        return bet
    }

    override suspend fun resolveBet(betId: Long, winningOutcomeId: Long): Unit = TODO()
    override suspend fun cancelBet(betId: Long): Unit = TODO()
    override suspend fun forceCancelBet(betId: Long): Unit = TODO()
    override suspend fun reopenBet(betId: Long): Unit = TODO()
    override suspend fun adminResolveBet(betId: Long, winningOutcomeId: Long): Unit = TODO()
    override suspend fun updateBetPrompt(betId: Long, newPrompt: String): Unit = TODO()
    override suspend fun adminRemoveParticipant(betId: Long, participantId: Long): Unit = TODO()
    override suspend fun adminEditWager(participantId: Long, newWagerAmount: Long): Unit = TODO()
    override suspend fun deleteBet(betId: Long): Unit = TODO()
    override suspend fun repeatBet(betId: Long): Long = TODO()

    private companion object {
        val PROMPT_LENGTH = 1..120
        val OUTCOME_COUNT = 2..8
        val OUTCOME_LENGTH = 1..40
        const val MIN_PARTICIPANTS = 2
    }
}
