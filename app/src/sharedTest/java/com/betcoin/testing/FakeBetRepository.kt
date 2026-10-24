package com.betcoin.testing

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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import java.util.concurrent.atomic.AtomicInteger

/**
 * Shared in-memory fake of [BetRepository] for unit and instrumented tests.
 *
 * Holds every bet in [bets]. The pending-bet operations ([createBet], [addParticipant],
 * [removeParticipant], [lockBet], [discardPendingBet], [discardOlderPendingBets]) follow the rules of
 * `LocalBetRepository` and read users and balances from [users]. The later lifecycle and admin operations
 * (resolve, cancel, reopen, repeat, delete, edits) don't change state: they are recorded in [calls] and
 * honour [failure] and [gate], so tests can check what a ViewModel asked for.
 */
class FakeBetRepository(private val users: FakeUserRepository = FakeUserRepository()) : BetRepository {

    /** Every bet with its outcomes and participants. Assign it to seed or replace the whole store. */
    val bets = MutableStateFlow<List<BetWithDetails>>(emptyList())
    private var nextId = 1L
    private val activeBetsSubscribers = AtomicInteger()

    /** Calls in order, e.g. `resolve(7, 1)`, `cancel(7)`, `reopen(7)`, `repeat(7)`. */
    val calls = mutableListOf<String>()

    /** The id returned by [repeatBet]. */
    var repeatedId: Long = 99L

    /** Thrown by the next resolve/cancel/reopen/repeat/admin call. */
    var failure: Throwable? = null

    /** When set, resolve/cancel/reopen/repeat/admin calls suspend until it completes. */
    var gate: CompletableDeferred<Unit>? = null

    /** When set, [createBet] throws it. */
    var createBetError: Throwable? = null
    var createBetCalls = 0
        private set

    /** When set, [discardPendingBet] throws it. */
    var discardError: Throwable? = null

    /** IDs passed to [discardPendingBet], in call order. */
    val discardedBetIds = mutableListOf<Long>()

    /** Number of [discardOlderPendingBets] calls. */
    var discardOlderCalls = 0
        private set

    /** When set, [discardOlderPendingBets] throws it. */
    var discardOlderError: Throwable? = null

    /** When set, the [getPendingBet] flow throws it on collection. */
    var getPendingBetError: Throwable? = null

    /** IDs passed to [lockBet] that succeeded, in call order. */
    val lockedBetIds = mutableListOf<Long>()

    /** When set, [lockBet] throws it instead of locking, to simulate a failure after the validation. */
    var lockFailure: Exception? = null

    /** Number of ACTIVE bets. Setting it replaces the active bets with that many placeholders. */
    var activeBetCount: Int
        get() = bets.value.count { it.bet.status == BetStatus.ACTIVE }
        set(value) {
            bets.value = bets.value.filterNot { it.bet.status == BetStatus.ACTIVE } +
                List(value) { index -> placeholderBet(id = -(index + 1L), status = BetStatus.ACTIVE) }
        }

    /** Number of collectors currently subscribed to [getActiveBets]. */
    val activeBetsSubscriberCount: Int get() = activeBetsSubscribers.get()

    val allBets: List<Bet> get() = bets.value.map { it.bet }
    fun outcomesOf(betId: Long): List<BetOutcome> = detailsOf(betId)?.outcomes.orEmpty()
    fun participantsOf(betId: Long): List<BetParticipant> =
        detailsOf(betId)?.participants.orEmpty().map { it.participant }

    /** Makes [details] the only bet, or leaves the store empty for null. */
    fun serve(details: BetWithDetails?) {
        bets.value = listOfNotNull(details)
    }

    /** Builds a pending bet with [participantCount] participants whose users are deleted. */
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

    /** Seeds a pending bet, as Recovery or Repeat would leave one, and returns its ID. */
    suspend fun seedPendingBet(prompt: String, outcomes: List<String>): Long {
        val id = createBet(prompt, outcomes)
        createBetCalls--
        return id
    }

    override suspend fun createBet(prompt: String, outcomes: List<String>): Long {
        createBetError?.let { throw it }
        createBetCalls++
        if (prompt.trim().length !in 1..120) throw InvalidInputException("Prompt must be 1–120 characters")
        if (outcomes.size !in 2..8) throw InvalidInputException("A bet needs 2–8 outcomes")
        val betId = nextId++
        val betOutcomes = outcomes.map { BetOutcome(id = nextId++, betId = betId, outcomeText = it.trim()) }
        bets.value = bets.value + BetWithDetails(
            bet = Bet(
                id = betId,
                prompt = prompt.trim(),
                status = BetStatus.PENDING_CONFIRMATION,
                createdAt = 0L,
            ),
            outcomes = betOutcomes,
            participants = emptyList(),
        )
        return betId
    }

    override suspend fun addParticipant(betId: Long, userId: Long, outcomeId: Long, wagerAmount: Long) {
        val details = requirePending(betId)
        val outcome = details.outcomes.find { it.id == outcomeId } ?: throw InvalidInputException("Outcome not in bet")
        val user = users.getUser(userId) ?: throw InvalidInputException("User $userId not found")
        if (details.participants.any { it.participant.userId == userId }) throw InvalidInputException("Already joined")
        if (wagerAmount < 1) throw InvalidInputException("Wager must be at least 1")
        if (wagerAmount > user.balance) throw InsufficientBalanceException(userId, wagerAmount, user.balance)
        val participant = BetParticipant(
            id = nextId++,
            betId = betId,
            userId = userId,
            outcomeId = outcomeId,
            wagerAmount = wagerAmount,
        )
        update(betId) { it.copy(participants = it.participants + ParticipantWithUser(participant, user, outcome)) }
    }

    override suspend fun removeParticipant(participantId: Long) {
        val details = bets.value.find { d -> d.participants.any { it.participant.id == participantId } }
            ?: throw InvalidInputException("Participant $participantId not found")
        requirePending(details.bet.id)
        update(details.bet.id) { d -> d.copy(participants = d.participants.filterNot { it.participant.id == participantId }) }
    }

    /** Emits the bet with each participant's user refreshed from [users], or null if it isn't there. */
    override fun getBetWithDetails(betId: Long): Flow<BetWithDetails?> =
        combine(bets, users.getAllUsers()) { bets, currentUsers ->
            bets.find { it.bet.id == betId }?.let { details ->
                details.copy(
                    participants = details.participants.map { p ->
                        val live = currentUsers.find { it.id == p.participant.userId }
                        if (live != null) p.copy(user = live) else p
                    },
                )
            }
        }

    override fun getActiveBets(): Flow<List<BetWithDetails>> =
        bets.map { list -> list.filter { it.bet.status == BetStatus.ACTIVE } }
            .onStart { activeBetsSubscribers.incrementAndGet() }
            .onCompletion { activeBetsSubscribers.decrementAndGet() }

    override fun getAllBets(): Flow<List<BetWithDetails>> = bets.map { list ->
        list.filter { it.bet.status != BetStatus.PENDING_CONFIRMATION }
            .sortedWith(compareByDescending<BetWithDetails> { it.bet.createdAt }.thenByDescending { it.bet.id })
    }

    /** The newest pending bet is the one with the highest `createdAt`, ties broken by the highest id. */
    override fun getPendingBet(): Flow<BetWithDetails?> {
        val error = getPendingBetError
        return if (error != null) flow { throw error } else bets.map { it.newestPending() }
    }

    override suspend fun discardPendingBet(betId: Long) {
        discardError?.let { throw it }
        requirePending(betId)
        discardedBetIds += betId
        bets.value = bets.value.filterNot { it.bet.id == betId }
    }

    override suspend fun discardOlderPendingBets() {
        discardOlderCalls++
        discardOlderError?.let { throw it }
        val newest = bets.value.newestPending() ?: return
        bets.value = bets.value.filterNot {
            it.bet.status == BetStatus.PENDING_CONFIRMATION && it.bet.id != newest.bet.id
        }
    }

    override suspend fun lockBet(betId: Long) {
        lockFailure?.let { throw it }
        val details = requirePending(betId)
        val members = details.participants.map { it.participant }
        if (members.size < 2) throw InvalidInputException("A bet needs at least 2 participants")
        if (members.distinctBy { it.outcomeId }.size == 1) throw UnanimousPickException()
        members.forEach { p ->
            val user = users.getUser(p.userId!!)!!
            if (p.wagerAmount > user.balance) throw InsufficientBalanceException(user.id, p.wagerAmount, user.balance)
        }
        members.forEach { users.updateBalance(it.userId!!, -it.wagerAmount) }
        lockedBetIds += betId
        update(betId) { it.copy(bet = it.bet.copy(status = BetStatus.ACTIVE)) }
    }

    override suspend fun resolveBet(betId: Long, winningOutcomeId: Long) = record("resolve($betId, $winningOutcomeId)")
    override suspend fun cancelBet(betId: Long) = record("cancel($betId)")
    override suspend fun forceCancelBet(betId: Long) = record("forceCancel($betId)")
    override suspend fun reopenBet(betId: Long) = record("reopen($betId)")
    override suspend fun adminResolveBet(betId: Long, winningOutcomeId: Long) =
        record("adminResolve($betId, $winningOutcomeId)")
    override suspend fun updateBetPrompt(betId: Long, newPrompt: String) = record("updatePrompt($betId, $newPrompt)")
    override suspend fun adminRemoveParticipant(betId: Long, participantId: Long) =
        record("adminRemoveParticipant($betId, $participantId)")
    override suspend fun adminEditWager(participantId: Long, newWagerAmount: Long) =
        record("adminEditWager($participantId, $newWagerAmount)")
    override suspend fun deleteBet(betId: Long) = record("delete($betId)")

    override suspend fun repeatBet(betId: Long): Long {
        record("repeat($betId)")
        return repeatedId
    }

    private suspend fun record(call: String) {
        calls += call
        gate?.await()
        failure?.let { throw it }
    }

    private fun detailsOf(betId: Long): BetWithDetails? = bets.value.find { it.bet.id == betId }

    private fun update(betId: Long, transform: (BetWithDetails) -> BetWithDetails) {
        bets.value = bets.value.map { if (it.bet.id == betId) transform(it) else it }
    }

    private fun requirePending(betId: Long): BetWithDetails {
        val details = detailsOf(betId) ?: throw InvalidInputException("Bet $betId not found")
        if (details.bet.status != BetStatus.PENDING_CONFIRMATION) throw InvalidBetStateException(betId, details.bet.status)
        return details
    }

    private fun List<BetWithDetails>.newestPending(): BetWithDetails? =
        filter { it.bet.status == BetStatus.PENDING_CONFIRMATION }
            .maxWithOrNull(compareBy({ it.bet.createdAt }, { it.bet.id }))

    private fun placeholderBet(id: Long, status: BetStatus) = BetWithDetails(
        bet = Bet(id = id, prompt = "Mock", status = status, createdAt = System.currentTimeMillis()),
        outcomes = emptyList(),
        participants = emptyList(),
    )
}
