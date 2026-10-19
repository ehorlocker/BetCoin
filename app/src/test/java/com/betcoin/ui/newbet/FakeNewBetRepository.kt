package com.betcoin.ui.newbet

import com.betcoin.data.database.entity.Bet
import com.betcoin.data.database.entity.BetOutcome
import com.betcoin.data.database.entity.BetParticipant
import com.betcoin.data.model.BetStatus
import com.betcoin.data.model.BetWithDetails
import com.betcoin.data.model.InsufficientBalanceException
import com.betcoin.data.model.InvalidBetStateException
import com.betcoin.data.model.InvalidInputException
import com.betcoin.data.model.ParticipantWithUser
import com.betcoin.data.model.UnanimousPickException
import com.betcoin.data.repository.BetRepository
import com.betcoin.testing.FakeUserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapNotNull

/**
 * In-memory [BetRepository] for [NewBetViewModel] tests. Implements the pending-bet operations
 * with the same rules as `LocalBetRepository`, reading users and balances from [users].
 */
class FakeNewBetRepository(private val users: FakeUserRepository) : BetRepository {
    private val bets = MutableStateFlow<List<Bet>>(emptyList())
    private val outcomes = MutableStateFlow<List<BetOutcome>>(emptyList())
    private val participants = MutableStateFlow<List<BetParticipant>>(emptyList())
    private var nextId = 1L

    /** Bets passed to [discardPendingBet], in call order. */
    val discardedBetIds = mutableListOf<Long>()
    /** When set, [createBet] throws it. */
    var createBetError: Throwable? = null

    /** When set, [discardPendingBet] throws it. */
    var discardError: Throwable? = null
    var createBetCalls = 0
        private set

    val allBets: List<Bet> get() = bets.value
    fun outcomesOf(betId: Long): List<BetOutcome> = outcomes.value.filter { it.betId == betId }
    fun participantsOf(betId: Long): List<BetParticipant> = participants.value.filter { it.betId == betId }

    override suspend fun createBet(prompt: String, outcomes: List<String>): Long {
        createBetError?.let { throw it }
        createBetCalls++
        if (prompt.trim().length !in 1..120) throw InvalidInputException("Prompt must be 1–120 characters")
        if (outcomes.size !in 2..8) throw InvalidInputException("A bet needs 2–8 outcomes")
        val betId = nextId++
        bets.value = bets.value + Bet(
            id = betId,
            prompt = prompt.trim(),
            status = BetStatus.PENDING_CONFIRMATION,
            createdAt = 0L,
        )
        this.outcomes.value = this.outcomes.value + outcomes.map {
            BetOutcome(id = nextId++, betId = betId, outcomeText = it.trim())
        }
        return betId
    }

    /** Seeds a pending bet, as Recovery or Repeat would leave one, and returns its ID. */
    suspend fun seedPendingBet(prompt: String, outcomes: List<String>): Long {
        val id = createBet(prompt, outcomes)
        createBetCalls--
        return id
    }

    override suspend fun addParticipant(betId: Long, userId: Long, outcomeId: Long, wagerAmount: Long) {
        requirePending(betId)
        if (outcomesOf(betId).none { it.id == outcomeId }) throw InvalidInputException("Outcome not in bet")
        val user = users.getUser(userId) ?: throw InvalidInputException("User $userId not found")
        if (participantsOf(betId).any { it.userId == userId }) throw InvalidInputException("Already joined")
        if (wagerAmount < 1) throw InvalidInputException("Wager must be at least 1")
        if (wagerAmount > user.balance) throw InsufficientBalanceException(userId, wagerAmount, user.balance)
        participants.value = participants.value + BetParticipant(
            id = nextId++,
            betId = betId,
            userId = userId,
            outcomeId = outcomeId,
            wagerAmount = wagerAmount,
        )
    }

    override suspend fun removeParticipant(participantId: Long) {
        val participant = participants.value.find { it.id == participantId }
            ?: throw InvalidInputException("Participant $participantId not found")
        requirePending(participant.betId)
        participants.value = participants.value - participant
    }

    override fun getBetWithDetails(betId: Long): Flow<BetWithDetails> =
        combine(bets, outcomes, participants, users.getAllUsers()) { bets, outcomes, participants, users ->
            val bet = bets.find { it.id == betId } ?: return@combine null
            val betOutcomes = outcomes.filter { it.betId == betId }
            BetWithDetails(
                bet = bet,
                outcomes = betOutcomes,
                participants = participants.filter { it.betId == betId }.map { p ->
                    ParticipantWithUser(
                        participant = p,
                        user = users.find { it.id == p.userId },
                        outcome = betOutcomes.first { it.id == p.outcomeId },
                    )
                },
            )
        }.mapNotNull { it }

    override suspend fun discardOlderPendingBets() {}

    override suspend fun discardPendingBet(betId: Long) {
        discardError?.let { throw it }
        requirePending(betId)
        discardedBetIds += betId
        participants.value = participants.value.filterNot { it.betId == betId }
        outcomes.value = outcomes.value.filterNot { it.betId == betId }
        bets.value = bets.value.filterNot { it.id == betId }
    }

    private fun requirePending(betId: Long): Bet {
        val bet = bets.value.find { it.id == betId } ?: throw InvalidInputException("Bet $betId not found")
        if (bet.status != BetStatus.PENDING_CONFIRMATION) throw InvalidBetStateException(betId, bet.status)
        return bet
    }

    override fun getActiveBets(): Flow<List<BetWithDetails>> = flowOf(emptyList())
    override fun getAllBets(): Flow<List<BetWithDetails>> = flowOf(emptyList())
    override fun getPendingBet(): Flow<BetWithDetails?> = flowOf(null)
    /** Bets passed to [lockBet], in call order. */
    val lockedBetIds = mutableListOf<Long>()

    /** When set, [lockBet] throws it instead of locking, to simulate a failure after Step C's checks. */
    var lockFailure: Exception? = null

    override suspend fun lockBet(betId: Long) {
        lockFailure?.let { throw it }
        val bet = requirePending(betId)
        val members = participantsOf(betId)
        if (members.size < 2) throw InvalidInputException("A bet needs at least 2 participants")
        if (members.distinctBy { it.outcomeId }.size == 1) throw UnanimousPickException()
        members.forEach { p ->
            val user = users.getUser(p.userId!!)!!
            if (p.wagerAmount > user.balance) throw InsufficientBalanceException(user.id, p.wagerAmount, user.balance)
        }
        members.forEach { users.updateBalance(it.userId!!, -it.wagerAmount) }
        lockedBetIds += betId
        bets.value = bets.value.map { if (it.id == bet.id) it.copy(status = BetStatus.ACTIVE) else it }
    }
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
