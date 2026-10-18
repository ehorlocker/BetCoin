package com.betcoin.data.repository.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.betcoin.data.database.BetCoinDatabase
import com.betcoin.data.database.entity.Bet
import com.betcoin.data.database.entity.User
import com.betcoin.data.model.BetCoinException
import com.betcoin.data.model.BetStatus
import com.betcoin.data.model.BetWithDetails
import com.betcoin.data.model.InsufficientBalanceException
import com.betcoin.data.model.InvalidBetStateException
import com.betcoin.data.model.InvalidInputException
import com.betcoin.data.model.UnanimousPickException
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for [LocalBetRepository] (create, participants, lock, discard, queries).
 *
 * Uses an in-memory Room database. Every failure case checks that the database is unchanged.
 */
@RunWith(AndroidJUnit4::class)
class LocalBetRepositoryTest {

  private lateinit var database: BetCoinDatabase
  private lateinit var repository: LocalBetRepository

  @Before
  fun setUp() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    database = Room.inMemoryDatabaseBuilder(context, BetCoinDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    repository = LocalBetRepository(database)
  }

  @After
  fun tearDown() {
    database.close()
  }

  // --- Helpers ---

  private suspend fun insertUser(username: String, balance: Long = 1000L): Long =
    database.userDao().insert(
      User(username = username, pinHash = "hash", balance = balance, createdAt = 0L)
    )

  private suspend fun balanceOf(userId: Long): Long = database.userDao().getById(userId)!!.balance

  private suspend fun details(betId: Long): BetWithDetails =
    database.betDao().getByIdWithDetails(betId).first()!!

  private suspend fun outcomeIds(betId: Long): List<Long> =
    database.betOutcomeDao().getByBetId(betId).map { it.id }.sorted()

  /** Everything a repository call could change: users, bets, outcomes and participants. */
  private suspend fun snapshot(): Any = listOf(
    database.userDao().getAll().first().sortedBy { it.id },
    database.betDao().getAllWithDetails().first().map { bet ->
      bet.copy(
        outcomes = bet.outcomes.sortedBy { it.id },
        participants = bet.participants.sortedBy { it.participant.id },
      )
    },
  )

  /** Asserts [block] throws [T] and leaves the database exactly as it was. */
  private suspend inline fun <reified T : BetCoinException> assertRejected(
    crossinline block: suspend () -> Unit,
  ): T {
    val before = snapshot()
    val error = runCatching { block() }.exceptionOrNull()
    assertThat(error).isInstanceOf(T::class.java)
    assertThat(snapshot()).isEqualTo(before)
    return error as T
  }

  /** A pending bet with two outcomes and two users who each picked a different one. */
  private suspend fun pendingBetWithTwoPlayers(
    aliceWager: Long = 100L,
    bobWager: Long = 200L,
  ): Triple<Long, Long, Long> {
    val alice = insertUser("Alice")
    val bob = insertUser("Bob")
    val betId = repository.createBet("Who wins?", listOf("Alice", "Bob"))
    val (first, second) = outcomeIds(betId)
    repository.addParticipant(betId, alice, first, aliceWager)
    repository.addParticipant(betId, bob, second, bobWager)
    return Triple(betId, alice, bob)
  }

  private suspend fun setStatus(betId: Long, status: BetStatus) {
    val bet = database.betDao().getById(betId)!!
    database.betDao().update(bet.copy(status = status))
  }

  // --- createBet ---

  @Test
  fun createBet_storesTrimmedPromptAndOutcomesAsPending() = runTest {
    val betId = repository.createBet("  Who wins?  ", listOf(" Jay ", "Austin", "Mike  "))

    val bet = details(betId)
    assertThat(bet.bet.prompt).isEqualTo("Who wins?")
    assertThat(bet.bet.status).isEqualTo(BetStatus.PENDING_CONFIRMATION)
    assertThat(bet.outcomes.map { it.outcomeText }).containsExactly("Jay", "Austin", "Mike")
    assertThat(bet.participants).isEmpty()
  }

  @Test
  fun createBet_acceptsBoundaryLengthsAndCounts() = runTest {
    val betId = repository.createBet("p".repeat(120), List(8) { "o$it".padEnd(40, 'x') })

    assertThat(details(betId).outcomes).hasSize(8)
    assertThat(repository.createBet("p", listOf("a", "b"))).isGreaterThan(0L)
  }

  @Test
  fun createBet_rejectsBlankPrompt() = runTest {
    assertRejected<InvalidInputException> { repository.createBet("   ", listOf("a", "b")) }
  }

  @Test
  fun createBet_rejectsPromptOver120Chars() = runTest {
    assertRejected<InvalidInputException> {
      repository.createBet("p".repeat(121), listOf("a", "b"))
    }
  }

  @Test
  fun createBet_rejectsFewerThanTwoOutcomes() = runTest {
    assertRejected<InvalidInputException> { repository.createBet("Who?", listOf("a")) }
  }

  @Test
  fun createBet_rejectsMoreThanEightOutcomes() = runTest {
    assertRejected<InvalidInputException> {
      repository.createBet("Who?", List(9) { "o$it" })
    }
  }

  @Test
  fun createBet_rejectsBlankOutcome() = runTest {
    assertRejected<InvalidInputException> { repository.createBet("Who?", listOf("a", "  ")) }
  }

  @Test
  fun createBet_rejectsOutcomeOver40Chars() = runTest {
    assertRejected<InvalidInputException> {
      repository.createBet("Who?", listOf("a", "o".repeat(41)))
    }
  }

  @Test
  fun createBet_rejectsOutcomesDifferingOnlyInCaseOrWhitespace() = runTest {
    assertRejected<InvalidInputException> {
      repository.createBet("Who?", listOf("Jay", " jay ", "Mike"))
    }
  }

  // --- addParticipant ---

  @Test
  fun addParticipant_storesParticipantWithoutChangingBalance() = runTest {
    val alice = insertUser("Alice")
    val betId = repository.createBet("Who?", listOf("a", "b"))
    val outcome = outcomeIds(betId).first()

    repository.addParticipant(betId, alice, outcome, 1000L)

    val participant = details(betId).participants.single().participant
    assertThat(participant.userId).isEqualTo(alice)
    assertThat(participant.outcomeId).isEqualTo(outcome)
    assertThat(participant.wagerAmount).isEqualTo(1000L)
    assertThat(balanceOf(alice)).isEqualTo(1000L)
  }

  @Test
  fun addParticipant_rejectsSameUserTwice() = runTest {
    val alice = insertUser("Alice")
    val betId = repository.createBet("Who?", listOf("a", "b"))
    val (first, second) = outcomeIds(betId)
    repository.addParticipant(betId, alice, first, 10L)

    assertRejected<InvalidInputException> {
      repository.addParticipant(betId, alice, second, 10L)
    }
  }

  @Test
  fun addParticipant_rejectsZeroOrNegativeWager() = runTest {
    val alice = insertUser("Alice")
    val betId = repository.createBet("Who?", listOf("a", "b"))
    val outcome = outcomeIds(betId).first()

    assertRejected<InvalidInputException> { repository.addParticipant(betId, alice, outcome, 0L) }
    assertRejected<InvalidInputException> { repository.addParticipant(betId, alice, outcome, -5L) }
  }

  @Test
  fun addParticipant_rejectsWagerAboveBalance() = runTest {
    val alice = insertUser("Alice", balance = 50L)
    val betId = repository.createBet("Who?", listOf("a", "b"))
    val outcome = outcomeIds(betId).first()

    val error = assertRejected<InsufficientBalanceException> {
      repository.addParticipant(betId, alice, outcome, 51L)
    }
    assertThat(error.userId).isEqualTo(alice)
    assertThat(error.required).isEqualTo(51L)
    assertThat(error.available).isEqualTo(50L)
  }

  @Test
  fun addParticipant_rejectsUserWithZeroOrNegativeBalance() = runTest {
    val broke = insertUser("Broke", balance = 0L)
    val inDebt = insertUser("InDebt", balance = -100L)
    val betId = repository.createBet("Who?", listOf("a", "b"))
    val outcome = outcomeIds(betId).first()

    assertRejected<InsufficientBalanceException> {
      repository.addParticipant(betId, broke, outcome, 1L)
    }
    assertRejected<InsufficientBalanceException> {
      repository.addParticipant(betId, inDebt, outcome, 1L)
    }
  }

  @Test
  fun addParticipant_rejectsOutcomeFromAnotherBet() = runTest {
    val alice = insertUser("Alice")
    val betId = repository.createBet("Who?", listOf("a", "b"))
    val otherBetOutcome = outcomeIds(repository.createBet("Other?", listOf("c", "d"))).first()

    assertRejected<InvalidInputException> {
      repository.addParticipant(betId, alice, otherBetOutcome, 10L)
    }
  }

  @Test
  fun addParticipant_rejectsUnknownUserOrBet() = runTest {
    val alice = insertUser("Alice")
    val betId = repository.createBet("Who?", listOf("a", "b"))
    val outcome = outcomeIds(betId).first()

    assertRejected<InvalidInputException> { repository.addParticipant(betId, 999L, outcome, 10L) }
    assertRejected<InvalidInputException> { repository.addParticipant(999L, alice, outcome, 10L) }
  }

  @Test
  fun addParticipant_rejectsNonPendingBet() = runTest {
    val (betId, _, _) = pendingBetWithTwoPlayers()
    repository.lockBet(betId)
    val carol = insertUser("Carol")
    val outcome = outcomeIds(betId).first()

    assertRejected<InvalidBetStateException> {
      repository.addParticipant(betId, carol, outcome, 10L)
    }
  }

  // --- removeParticipant ---

  @Test
  fun removeParticipant_deletesParticipantFromPendingBet() = runTest {
    val (betId, alice, bob) = pendingBetWithTwoPlayers()
    val aliceRow = details(betId).participants.first { it.participant.userId == alice }

    repository.removeParticipant(aliceRow.participant.id)

    assertThat(details(betId).participants.map { it.participant.userId }).containsExactly(bob)
    assertThat(balanceOf(alice)).isEqualTo(1000L)
  }

  @Test
  fun removeParticipant_rejectsActiveBet() = runTest {
    val (betId, _, _) = pendingBetWithTwoPlayers()
    repository.lockBet(betId)
    val participantId = details(betId).participants.first().participant.id

    assertRejected<InvalidBetStateException> { repository.removeParticipant(participantId) }
  }

  @Test
  fun removeParticipant_rejectsResolvedAndCancelledBets() = runTest {
    val (betId, _, _) = pendingBetWithTwoPlayers()
    val participantId = details(betId).participants.first().participant.id

    for (status in listOf(BetStatus.RESOLVED, BetStatus.CANCELLED)) {
      setStatus(betId, status)
      assertRejected<InvalidBetStateException> { repository.removeParticipant(participantId) }
    }
  }

  @Test
  fun removeParticipant_rejectsUnknownParticipant() = runTest {
    assertRejected<InvalidInputException> { repository.removeParticipant(999L) }
  }

  // --- lockBet ---

  @Test
  fun lockBet_deductsAllWagersAndSetsActive() = runTest {
    val (betId, alice, bob) = pendingBetWithTwoPlayers(aliceWager = 100L, bobWager = 1000L)

    repository.lockBet(betId)

    assertThat(details(betId).bet.status).isEqualTo(BetStatus.ACTIVE)
    assertThat(balanceOf(alice)).isEqualTo(900L)
    assertThat(balanceOf(bob)).isEqualTo(0L)
  }

  @Test
  fun lockBet_allowsSharedOutcomeAsLongAsNotUnanimous() = runTest {
    val (betId, _, _) = pendingBetWithTwoPlayers()
    val carol = insertUser("Carol")
    repository.addParticipant(betId, carol, outcomeIds(betId).first(), 10L)

    repository.lockBet(betId)

    assertThat(details(betId).bet.status).isEqualTo(BetStatus.ACTIVE)
    assertThat(balanceOf(carol)).isEqualTo(990L)
  }

  @Test
  fun lockBet_rejectsFewerThanTwoParticipants() = runTest {
    val alice = insertUser("Alice")
    val betId = repository.createBet("Who?", listOf("a", "b"))
    assertRejected<InvalidInputException> { repository.lockBet(betId) }

    repository.addParticipant(betId, alice, outcomeIds(betId).first(), 10L)
    assertRejected<InvalidInputException> { repository.lockBet(betId) }
  }

  @Test
  fun lockBet_rejectsUnanimousPick() = runTest {
    val alice = insertUser("Alice")
    val bob = insertUser("Bob")
    val betId = repository.createBet("Who?", listOf("a", "b"))
    val outcome = outcomeIds(betId).first()
    repository.addParticipant(betId, alice, outcome, 10L)
    repository.addParticipant(betId, bob, outcome, 20L)

    assertRejected<UnanimousPickException> { repository.lockBet(betId) }
  }

  @Test
  fun lockBet_rejectsWagerAboveCurrentBalanceAndDeductsNothing() = runTest {
    val (betId, _, bob) = pendingBetWithTwoPlayers(aliceWager = 100L, bobWager = 500L)
    // Bob's balance drops after he joined (e.g. an admin adjustment).
    database.userDao().updateBalance(bob, 499L)

    val error = assertRejected<InsufficientBalanceException> { repository.lockBet(betId) }
    assertThat(error.userId).isEqualTo(bob)
    assertThat(error.required).isEqualTo(500L)
    assertThat(error.available).isEqualTo(499L)
  }

  @Test
  fun lockBet_rejectsNonPendingBet() = runTest {
    val (betId, _, _) = pendingBetWithTwoPlayers()
    repository.lockBet(betId)

    assertRejected<InvalidBetStateException> { repository.lockBet(betId) }
  }

  @Test
  fun lockBet_rejectsParticipantWhoseUserWasDeleted() = runTest {
    val (betId, alice, _) = pendingBetWithTwoPlayers()
    val aliceRow = details(betId).participants.first { it.participant.userId == alice }
    database.betParticipantDao().update(
      aliceRow.participant.copy(userId = null, deletedUsername = "Alice")
    )

    assertRejected<InvalidInputException> { repository.lockBet(betId) }
  }

  @Test
  fun lockBet_rejectsUnknownBet() = runTest {
    assertRejected<InvalidInputException> { repository.lockBet(999L) }
  }

  // --- discardPendingBet ---

  @Test
  fun discardPendingBet_deletesBetOutcomesAndParticipants() = runTest {
    val (betId, alice, bob) = pendingBetWithTwoPlayers()
    val otherBetId = repository.createBet("Other?", listOf("c", "d"))

    repository.discardPendingBet(betId)

    assertThat(database.betDao().getById(betId)).isNull()
    assertThat(database.betOutcomeDao().getByBetId(betId)).isEmpty()
    assertThat(database.betParticipantDao().getByBetId(betId)).isEmpty()
    assertThat(database.betDao().getById(otherBetId)).isNotNull()
    assertThat(balanceOf(alice)).isEqualTo(1000L)
    assertThat(balanceOf(bob)).isEqualTo(1000L)
  }

  @Test
  fun discardPendingBet_rejectsNonPendingBets() = runTest {
    val (betId, _, _) = pendingBetWithTwoPlayers()
    repository.lockBet(betId)

    for (status in listOf(BetStatus.ACTIVE, BetStatus.RESOLVED, BetStatus.CANCELLED)) {
      setStatus(betId, status)
      assertRejected<InvalidBetStateException> { repository.discardPendingBet(betId) }
    }
  }

  @Test
  fun discardPendingBet_rejectsUnknownBet() = runTest {
    assertRejected<InvalidInputException> { repository.discardPendingBet(999L) }
  }

  // --- Queries ---

  @Test
  fun getBetWithDetails_emitsBetWithOutcomesAndParticipants() = runTest {
    val (betId, alice, bob) = pendingBetWithTwoPlayers()

    val bet = repository.getBetWithDetails(betId).first()!!

    assertThat(bet.bet.id).isEqualTo(betId)
    assertThat(bet.outcomes).hasSize(2)
    assertThat(bet.participants.map { it.user?.id }).containsExactly(alice, bob)
    assertThat(bet.participants.map { it.outcome.id }).containsExactlyElementsIn(outcomeIds(betId))
  }

  @Test
  fun getBetWithDetails_emitsNullForUnknownBet() = runTest {
    assertThat(repository.getBetWithDetails(999L).first()).isNull()
  }

  @Test
  fun getBetWithDetails_emitsNullAfterBetIsDiscarded() = runTest {
    val (betId, _, _) = pendingBetWithTwoPlayers()
    repository.discardPendingBet(betId)

    assertThat(repository.getBetWithDetails(betId).first()).isNull()
  }

  @Test
  fun getActiveBets_emitsOnlyActiveBets() = runTest {
    val (activeId, _, _) = pendingBetWithTwoPlayers()
    repository.lockBet(activeId)
    repository.createBet("Pending?", listOf("a", "b"))
    val resolvedId = repository.createBet("Resolved?", listOf("a", "b"))
    setStatus(resolvedId, BetStatus.RESOLVED)

    assertThat(repository.getActiveBets().first().map { it.bet.id }).containsExactly(activeId)
  }

  @Test
  fun getAllBets_excludesPendingAndSortsNewestFirst() = runTest {
    val betDao = database.betDao()
    val old = betDao.insert(Bet(prompt = "old", status = BetStatus.RESOLVED, createdAt = 1_000L))
    val newest = betDao.insert(Bet(prompt = "new", status = BetStatus.ACTIVE, createdAt = 3_000L))
    val middle = betDao.insert(Bet(prompt = "mid", status = BetStatus.CANCELLED, createdAt = 2_000L))
    betDao.insert(Bet(prompt = "pending", status = BetStatus.PENDING_CONFIRMATION, createdAt = 4_000L))

    assertThat(repository.getAllBets().first().map { it.bet.id })
      .containsExactly(newest, middle, old)
      .inOrder()
  }

  @Test
  fun getPendingBet_emitsPendingBetOrNull() = runTest {
    assertThat(repository.getPendingBet().first()).isNull()

    val (betId, _, _) = pendingBetWithTwoPlayers()
    val pending = repository.getPendingBet().first()
    assertThat(pending?.bet?.id).isEqualTo(betId)
    assertThat(pending?.participants).hasSize(2)

    repository.lockBet(betId)
    assertThat(repository.getPendingBet().first()).isNull()
  }

  @Test
  fun getPendingBet_emitsNewestWhenSeveralExist() = runTest {
    val betDao = database.betDao()
    betDao.insert(Bet(prompt = "old", status = BetStatus.PENDING_CONFIRMATION, createdAt = 1_000L))
    val newest =
      betDao.insert(Bet(prompt = "new", status = BetStatus.PENDING_CONFIRMATION, createdAt = 2_000L))

    assertThat(repository.getPendingBet().first()?.bet?.id).isEqualTo(newest)
  }

  @Test
  fun getPendingBet_emitsNullAfterDiscard() = runTest {
    val (betId, _, _) = pendingBetWithTwoPlayers()

    repository.discardPendingBet(betId)

    assertThat(repository.getPendingBet().first()).isNull()
  }
}
