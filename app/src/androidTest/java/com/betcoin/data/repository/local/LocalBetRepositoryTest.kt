package com.betcoin.data.repository.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.betcoin.data.database.BetCoinDatabase
import com.betcoin.data.database.entity.Bet
import com.betcoin.data.database.entity.BetOutcome
import com.betcoin.data.database.entity.BetParticipant
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
 * Instrumented tests for [LocalBetRepository] (create, participants, lock, discard, queries,
 * resolve, cancel, reopen, repeat, and the admin
 * force cancel, resolve, prompt edit and delete).
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

  private suspend fun userOf(userId: Long): User = database.userDao().getById(userId)!!

  private suspend fun totalBalance(): Long = database.userDao().getAll().first().sumOf { it.balance }

  private suspend fun payoutOf(betId: Long, userId: Long): Long? =
    details(betId).participants.first { it.participant.userId == userId }.participant.payout

  /**
   * An ACTIVE bet with three outcomes (A, B, C): Alice 100 and Carol 300 on A, Bob 200 on B.
   * Nobody picked C. Resolving to A pays Alice 150 and Carol 450 from the 600 pot.
   */
  private suspend fun activeBetWithThreePlayers(prefix: String = ""): ActiveBet {
    val alice = insertUser("${prefix}Alice")
    val bob = insertUser("${prefix}Bob")
    val carol = insertUser("${prefix}Carol")
    val betId = repository.createBet("Who wins?", listOf("A", "B", "C"))
    val (a, b, c) = outcomeIds(betId)
    repository.addParticipant(betId, alice, a, 100L)
    repository.addParticipant(betId, bob, b, 200L)
    repository.addParticipant(betId, carol, a, 300L)
    repository.lockBet(betId)
    return ActiveBet(betId, alice, bob, carol, a, b, c)
  }

  private data class ActiveBet(
    val betId: Long,
    val alice: Long,
    val bob: Long,
    val carol: Long,
    val outcomeA: Long,
    val outcomeB: Long,
    val outcomeC: Long,
  )

  /** Deletes [userId] the way user deletion does: snapshot the name, null the FK, drop the row. */
  private suspend fun deleteUser(userId: Long) {
    val user = userOf(userId)
    database.betParticipantDao().detachUser(userId, user.username)
    database.userDao().delete(user)
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

  // --- discardOlderPendingBets ---

  @Test
  fun discardOlderPendingBets_keepsOnlyNewest() = runTest {
    val betDao = database.betDao()
    val alice = insertUser("Alice")
    val oldest = betDao.insert(
      Bet(prompt = "oldest", status = BetStatus.PENDING_CONFIRMATION, createdAt = 1_000L)
    )
    val middle = betDao.insert(
      Bet(prompt = "middle", status = BetStatus.PENDING_CONFIRMATION, createdAt = 2_000L)
    )
    val newest = betDao.insert(
      Bet(prompt = "newest", status = BetStatus.PENDING_CONFIRMATION, createdAt = 3_000L)
    )
    val olderIds = listOf(oldest, middle)
    for (betId in olderIds + newest) {
      val outcomeId = database.betOutcomeDao().insert(BetOutcome(betId = betId, outcomeText = "a"))
      database.betOutcomeDao().insert(BetOutcome(betId = betId, outcomeText = "b"))
      if (betId != newest) {
        database.betParticipantDao().insert(
          BetParticipant(betId = betId, userId = alice, outcomeId = outcomeId, wagerAmount = 10L)
        )
      }
    }

    repository.discardOlderPendingBets()

    assertThat(repository.getPendingBet().first()?.bet?.id).isEqualTo(newest)
    assertThat(betDao.getPendingBets()).hasSize(1)
    for (betId in olderIds) {
      assertThat(betDao.getById(betId)).isNull()
      assertThat(database.betOutcomeDao().getByBetId(betId)).isEmpty()
      assertThat(database.betParticipantDao().getByBetId(betId)).isEmpty()
    }
    assertThat(database.betOutcomeDao().getByBetId(newest)).hasSize(2)
  }

  @Test
  fun discardOlderPendingBets_tieOnCreatedAt_keepsHighestId() = runTest {
    val betDao = database.betDao()
    val lower = betDao.insert(
      Bet(prompt = "lower", status = BetStatus.PENDING_CONFIRMATION, createdAt = 5_000L)
    )
    val higher = betDao.insert(
      Bet(prompt = "higher", status = BetStatus.PENDING_CONFIRMATION, createdAt = 5_000L)
    )

    repository.discardOlderPendingBets()

    assertThat(higher).isGreaterThan(lower)
    assertThat(betDao.getPendingBets().map { it.id }).containsExactly(higher)
    assertThat(repository.getPendingBet().first()?.bet?.id).isEqualTo(higher)
  }

  @Test
  fun discardOlderPendingBets_leavesNonPendingBetsUntouched() = runTest {
    val (activeId, _, _) = pendingBetWithTwoPlayers()
    repository.lockBet(activeId)
    val pendingId = repository.createBet("Pending?", listOf("c", "d"))

    repository.discardOlderPendingBets()

    assertThat(database.betDao().getById(activeId)?.status).isEqualTo(BetStatus.ACTIVE)
    assertThat(database.betDao().getById(pendingId)).isNotNull()
  }

  @Test
  fun discardOlderPendingBets_noPending_isNoOp() = runTest {
    val (activeId, _, _) = pendingBetWithTwoPlayers()
    repository.lockBet(activeId)
    val before = snapshot()

    repository.discardOlderPendingBets()

    assertThat(snapshot()).isEqualTo(before)
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

  // --- resolveBet ---

  @Test
  fun resolveBet_paysWinnersRecordsStatsAndSetsResolved() = runTest {
    val bet = activeBetWithThreePlayers()

    repository.resolveBet(bet.betId, bet.outcomeA)

    val resolved = details(bet.betId).bet
    assertThat(resolved.status).isEqualTo(BetStatus.RESOLVED)
    assertThat(resolved.winningOutcomeId).isEqualTo(bet.outcomeA)
    assertThat(resolved.resolvedAt).isNotNull()
    assertThat(payoutOf(bet.betId, bet.alice)).isEqualTo(150L)
    assertThat(payoutOf(bet.betId, bet.carol)).isEqualTo(450L)
    assertThat(payoutOf(bet.betId, bet.bob)).isEqualTo(0L)

    val alice = userOf(bet.alice)
    assertThat(alice.balance).isEqualTo(1050L)
    assertThat(alice.totalWins).isEqualTo(1)
    assertThat(alice.totalEarnings).isEqualTo(50L)
    assertThat(alice.totalLosses).isEqualTo(0)
    val carol = userOf(bet.carol)
    assertThat(carol.balance).isEqualTo(1150L)
    assertThat(carol.totalWins).isEqualTo(1)
    assertThat(carol.totalEarnings).isEqualTo(150L)
    val bob = userOf(bet.bob)
    assertThat(bob.balance).isEqualTo(800L)
    assertThat(bob.totalLosses).isEqualTo(1)
    assertThat(bob.totalLost).isEqualTo(200L)
    assertThat(bob.totalWins).isEqualTo(0)
    assertThat(totalBalance()).isEqualTo(3000L)
  }

  @Test
  fun resolveBet_givesRoundingRemainderToLargestWager() = runTest {
    val alice = insertUser("Alice")
    val bob = insertUser("Bob")
    val carol = insertUser("Carol")
    val betId = repository.createBet("Who?", listOf("a", "b"))
    val (a, b) = outcomeIds(betId)
    repository.addParticipant(betId, alice, a, 1L)
    repository.addParticipant(betId, bob, a, 2L)
    repository.addParticipant(betId, carol, b, 4L)
    repository.lockBet(betId)

    repository.resolveBet(betId, a)

    // Pot 7 over winning wagers 3: Alice 7/3 = 2, Bob 14/3 = 4, remainder 1 to Bob.
    assertThat(payoutOf(betId, alice)).isEqualTo(2L)
    assertThat(payoutOf(betId, bob)).isEqualTo(5L)
    assertThat(totalBalance()).isEqualTo(3000L)
  }

  @Test
  fun resolveBet_toOutcomeNobodyPickedIsPushWithNoStats() = runTest {
    val bet = activeBetWithThreePlayers()

    repository.resolveBet(bet.betId, bet.outcomeC)

    val resolved = details(bet.betId)
    assertThat(resolved.bet.status).isEqualTo(BetStatus.RESOLVED)
    assertThat(resolved.bet.winningOutcomeId).isEqualTo(bet.outcomeC)
    assertThat(resolved.participants.map { it.participant.payout })
      .containsExactlyElementsIn(resolved.participants.map { it.participant.wagerAmount })
    for (userId in listOf(bet.alice, bet.bob, bet.carol)) {
      val user = userOf(userId)
      assertThat(user.balance).isEqualTo(1000L)
      assertThat(user.totalWins).isEqualTo(0)
      assertThat(user.totalLosses).isEqualTo(0)
      assertThat(user.totalEarnings).isEqualTo(0L)
      assertThat(user.totalLost).isEqualTo(0L)
    }
  }

  @Test
  fun resolveBet_rejectsBetThatIsNotActive() = runTest {
    val (betId, _, _) = pendingBetWithTwoPlayers()
    val outcome = outcomeIds(betId).first()
    assertRejected<InvalidBetStateException> { repository.resolveBet(betId, outcome) }

    repository.lockBet(betId)
    repository.resolveBet(betId, outcome)
    assertRejected<InvalidBetStateException> { repository.resolveBet(betId, outcome) }

    repository.reopenBet(betId)
    repository.cancelBet(betId)
    assertRejected<InvalidBetStateException> { repository.resolveBet(betId, outcome) }
  }

  @Test
  fun resolveBet_rejectsOutcomeFromAnotherBetOrUnknown() = runTest {
    val bet = activeBetWithThreePlayers()
    val otherOutcome = outcomeIds(repository.createBet("Other?", listOf("c", "d"))).first()

    assertRejected<InvalidInputException> { repository.resolveBet(bet.betId, otherOutcome) }
    assertRejected<InvalidInputException> { repository.resolveBet(bet.betId, 999L) }
  }

  @Test
  fun resolveBet_rejectsUnknownBet() = runTest {
    assertRejected<InvalidInputException> { repository.resolveBet(999L, 1L) }
  }

  // --- cancelBet ---

  @Test
  fun cancelBet_refundsAllWagersWithoutStatsAndSetsCancelled() = runTest {
    val bet = activeBetWithThreePlayers()

    repository.cancelBet(bet.betId)

    val cancelled = details(bet.betId)
    assertThat(cancelled.bet.status).isEqualTo(BetStatus.CANCELLED)
    assertThat(cancelled.bet.resolvedAt).isNotNull()
    assertThat(cancelled.bet.winningOutcomeId).isNull()
    assertThat(cancelled.participants.map { it.participant.payout }).containsExactly(null, null, null)
    for (userId in listOf(bet.alice, bet.bob, bet.carol)) {
      val user = userOf(userId)
      assertThat(user.balance).isEqualTo(1000L)
      assertThat(user.totalWins).isEqualTo(0)
      assertThat(user.totalLosses).isEqualTo(0)
      assertThat(user.totalEarnings).isEqualTo(0L)
      assertThat(user.totalLost).isEqualTo(0L)
    }
  }

  @Test
  fun cancelBet_rejectsBetThatIsNotActive() = runTest {
    val (betId, _, _) = pendingBetWithTwoPlayers()
    assertRejected<InvalidBetStateException> { repository.cancelBet(betId) }

    repository.lockBet(betId)
    repository.cancelBet(betId)
    assertRejected<InvalidBetStateException> { repository.cancelBet(betId) }

    repository.reopenBet(betId)
    repository.resolveBet(betId, outcomeIds(betId).first())
    assertRejected<InvalidBetStateException> { repository.cancelBet(betId) }
  }

  @Test
  fun cancelBet_rejectsUnknownBet() = runTest {
    assertRejected<InvalidInputException> { repository.cancelBet(999L) }
  }

  // --- reopenBet (RESOLVED) ---

  @Test
  fun reopenBet_fromResolvedTakesBackPayoutsAndRevertsStatsExactly() = runTest {
    val bet = activeBetWithThreePlayers()
    val afterLock = snapshot()
    repository.resolveBet(bet.betId, bet.outcomeA)

    repository.reopenBet(bet.betId)

    // Balances, stats, payouts, winningOutcomeId, resolvedAt and status all match the
    // freshly locked bet: winners gave back their share, Bob's loss stayed in the pot.
    assertThat(snapshot()).isEqualTo(afterLock)
    assertThat(details(bet.betId).bet.status).isEqualTo(BetStatus.ACTIVE)
    assertThat(balanceOf(bet.alice)).isEqualTo(900L)
    assertThat(balanceOf(bet.bob)).isEqualTo(800L)
    assertThat(balanceOf(bet.carol)).isEqualTo(700L)
  }

  @Test
  fun reopenBet_fromResolvedPushLocksWagersAgainWithoutStats() = runTest {
    val bet = activeBetWithThreePlayers()
    val afterLock = snapshot()
    repository.resolveBet(bet.betId, bet.outcomeC)

    repository.reopenBet(bet.betId)

    assertThat(snapshot()).isEqualTo(afterLock)
  }

  @Test
  fun reopenBet_fromResolvedKeepsOtherStatsAndBalanceChanges() = runTest {
    val bet = activeBetWithThreePlayers()
    repository.resolveBet(bet.betId, bet.outcomeA)
    // Alice earned more elsewhere after the resolution.
    val alice = userOf(bet.alice)
    database.userDao().update(
      alice.copy(balance = alice.balance + 500L, totalWins = 4, totalEarnings = 1_000L)
    )

    repository.reopenBet(bet.betId)

    val reopened = userOf(bet.alice)
    assertThat(reopened.balance).isEqualTo(1050L + 500L - 150L)
    assertThat(reopened.totalWins).isEqualTo(3)
    assertThat(reopened.totalEarnings).isEqualTo(950L)
  }

  @Test
  fun reopenBet_fromResolvedRejectsWinnerWhoSpentWinnings() = runTest {
    val bet = activeBetWithThreePlayers()
    repository.resolveBet(bet.betId, bet.outcomeA)
    // Carol's payout is 450 but she has spent down to 449.
    database.userDao().updateBalance(bet.carol, 449L)

    val error = assertRejected<InsufficientBalanceException> { repository.reopenBet(bet.betId) }
    assertThat(error.userId).isEqualTo(bet.carol)
    assertThat(error.required).isEqualTo(450L)
    assertThat(error.available).isEqualTo(449L)
  }

  @Test
  fun reopenBet_fromResolvedAllowsBrokeLoserSinceTheirPayoutIsZero() = runTest {
    val bet = activeBetWithThreePlayers()
    repository.resolveBet(bet.betId, bet.outcomeA)
    database.userDao().updateBalance(bet.bob, 0L)

    repository.reopenBet(bet.betId)

    assertThat(details(bet.betId).bet.status).isEqualTo(BetStatus.ACTIVE)
    assertThat(balanceOf(bet.bob)).isEqualTo(0L)
    assertThat(userOf(bet.bob).totalLosses).isEqualTo(0)
  }

  @Test
  fun reopenBet_fromResolvedPushRejectsParticipantWhoCantCoverRefund() = runTest {
    val bet = activeBetWithThreePlayers()
    repository.resolveBet(bet.betId, bet.outcomeC)
    database.userDao().updateBalance(bet.bob, 199L)

    val error = assertRejected<InsufficientBalanceException> { repository.reopenBet(bet.betId) }
    assertThat(error.userId).isEqualTo(bet.bob)
    assertThat(error.required).isEqualTo(200L)
  }

  @Test
  fun reopenBet_fromResolvedRejectsDeletedParticipant() = runTest {
    val bet = activeBetWithThreePlayers()
    repository.resolveBet(bet.betId, bet.outcomeA)
    deleteUser(bet.bob)

    assertRejected<InvalidInputException> { repository.reopenBet(bet.betId) }
  }

  @Test
  fun resolveThenReopenThenResolve_leavesTotalCoinsUnchanged() = runTest {
    val bet = activeBetWithThreePlayers()
    val pot = 600L
    assertThat(totalBalance()).isEqualTo(3000L - pot)

    repository.resolveBet(bet.betId, bet.outcomeA)
    assertThat(totalBalance()).isEqualTo(3000L)
    val afterFirstResolve = snapshot()

    repository.reopenBet(bet.betId)
    assertThat(totalBalance()).isEqualTo(3000L - pot)

    repository.resolveBet(bet.betId, bet.outcomeA)
    assertThat(totalBalance()).isEqualTo(3000L)
    // Same winner again: balances, stats and payouts match the first resolution (only the
    // resolvedAt timestamp may differ).
    val users = database.userDao().getAll().first().sortedBy { it.id }
    assertThat(users).isEqualTo((afterFirstResolve as List<*>)[0])

    // Re-resolving to a different winner still moves coins without creating any.
    repository.reopenBet(bet.betId)
    repository.resolveBet(bet.betId, bet.outcomeB)
    assertThat(totalBalance()).isEqualTo(3000L)
    assertThat(balanceOf(bet.bob)).isEqualTo(1400L)
    assertThat(userOf(bet.bob).totalWins).isEqualTo(1)
    assertThat(userOf(bet.bob).totalLosses).isEqualTo(0)
    assertThat(userOf(bet.alice).totalWins).isEqualTo(0)
    assertThat(userOf(bet.alice).totalLosses).isEqualTo(1)
  }

  // --- reopenBet (CANCELLED) ---

  @Test
  fun reopenBet_fromCancelledReDeductsWagersAndSetsActive() = runTest {
    val bet = activeBetWithThreePlayers()
    val afterLock = snapshot()
    repository.cancelBet(bet.betId)

    repository.reopenBet(bet.betId)

    assertThat(snapshot()).isEqualTo(afterLock)
    assertThat(details(bet.betId).bet.status).isEqualTo(BetStatus.ACTIVE)
    assertThat(details(bet.betId).bet.resolvedAt).isNull()
  }

  @Test
  fun reopenBet_fromCancelledRejectsParticipantWhoCantCoverWager() = runTest {
    val bet = activeBetWithThreePlayers()
    repository.cancelBet(bet.betId)
    database.userDao().updateBalance(bet.carol, 299L)

    val error = assertRejected<InsufficientBalanceException> { repository.reopenBet(bet.betId) }
    assertThat(error.userId).isEqualTo(bet.carol)
    assertThat(error.required).isEqualTo(300L)
    assertThat(error.available).isEqualTo(299L)
  }

  @Test
  fun reopenBet_fromCancelledRejectsDeletedParticipant() = runTest {
    val bet = activeBetWithThreePlayers()
    repository.cancelBet(bet.betId)
    deleteUser(bet.alice)

    assertRejected<InvalidInputException> { repository.reopenBet(bet.betId) }
  }

  // --- reopenBet (other states) ---

  @Test
  fun reopenBet_rejectsPendingAndActiveBets() = runTest {
    val (betId, _, _) = pendingBetWithTwoPlayers()
    assertRejected<InvalidBetStateException> { repository.reopenBet(betId) }

    repository.lockBet(betId)
    assertRejected<InvalidBetStateException> { repository.reopenBet(betId) }
  }

  @Test
  fun reopenBet_rejectsUnknownBet() = runTest {
    assertRejected<InvalidInputException> { repository.reopenBet(999L) }
  }

  // --- repeatBet ---

  @Test
  fun repeatBet_createsPendingCopyWithoutParticipantsAndLeavesOriginal() = runTest {
    val bet = activeBetWithThreePlayers()
    repository.resolveBet(bet.betId, bet.outcomeA)
    val original = details(bet.betId)
    val usersBefore = database.userDao().getAll().first()

    val newBetId = repository.repeatBet(bet.betId)

    assertThat(newBetId).isNotEqualTo(bet.betId)
    val repeated = details(newBetId)
    assertThat(repeated.bet.prompt).isEqualTo("Who wins?")
    assertThat(repeated.bet.status).isEqualTo(BetStatus.PENDING_CONFIRMATION)
    assertThat(repeated.bet.winningOutcomeId).isNull()
    assertThat(repeated.bet.resolvedAt).isNull()
    assertThat(repeated.outcomes.sortedBy { it.id }.map { it.outcomeText })
      .containsExactly("A", "B", "C")
      .inOrder()
    assertThat(repeated.outcomes.map { it.id }).containsNoneIn(original.outcomes.map { it.id })
    assertThat(repeated.participants).isEmpty()
    assertThat(details(bet.betId)).isEqualTo(original)
    assertThat(database.userDao().getAll().first()).isEqualTo(usersBefore)
  }

  @Test
  fun repeatBet_worksForActiveAndCancelledBets() = runTest {
    val bet = activeBetWithThreePlayers()
    val repeatedFromActive = repository.repeatBet(bet.betId)
    repository.discardPendingBet(repeatedFromActive)

    repository.cancelBet(bet.betId)
    val repeatedFromCancelled = repository.repeatBet(bet.betId)

    assertThat(details(repeatedFromCancelled).outcomes).hasSize(3)
    assertThat(details(bet.betId).bet.status).isEqualTo(BetStatus.CANCELLED)
  }

  @Test
  fun repeatBet_rejectsWhenAPendingBetAlreadyExists() = runTest {
    val bet = activeBetWithThreePlayers()
    val pendingId = repository.createBet("Pending?", listOf("a", "b"))

    val error = assertRejected<InvalidBetStateException> { repository.repeatBet(bet.betId) }
    assertThat(error.betId).isEqualTo(pendingId)
    assertThat(error.status).isEqualTo(BetStatus.PENDING_CONFIRMATION)
  }

  @Test
  fun repeatBet_rejectsUnknownBet() = runTest {
    assertRejected<InvalidInputException> { repository.repeatBet(999L) }
  }

  // --- forceCancelBet ---

  @Test
  fun forceCancelBet_refundsAllWagersAndSetsCancelled() = runTest {
    val bet = activeBetWithThreePlayers()

    repository.forceCancelBet(bet.betId)

    val cancelled = details(bet.betId).bet
    assertThat(cancelled.status).isEqualTo(BetStatus.CANCELLED)
    assertThat(cancelled.resolvedAt).isNotNull()
    for (userId in listOf(bet.alice, bet.bob, bet.carol)) {
      val user = userOf(userId)
      assertThat(user.balance).isEqualTo(1000L)
      assertThat(user.totalWins).isEqualTo(0)
      assertThat(user.totalLosses).isEqualTo(0)
      assertThat(user.totalEarnings).isEqualTo(0L)
      assertThat(user.totalLost).isEqualTo(0L)
    }
  }

  @Test
  fun forceCancelBet_rejectsBetThatIsNotActive() = runTest {
    val (betId, _, _) = pendingBetWithTwoPlayers()
    assertRejected<InvalidBetStateException> { repository.forceCancelBet(betId) }

    repository.lockBet(betId)
    repository.cancelBet(betId)
    assertRejected<InvalidBetStateException> { repository.forceCancelBet(betId) }

    repository.reopenBet(betId)
    repository.resolveBet(betId, outcomeIds(betId).first())
    assertRejected<InvalidBetStateException> { repository.forceCancelBet(betId) }
  }

  @Test
  fun forceCancelBet_rejectsUnknownBet() = runTest {
    assertRejected<InvalidInputException> { repository.forceCancelBet(999L) }
  }

  // --- adminResolveBet ---

  @Test
  fun adminResolveBet_paysWinnersRecordsStatsAndSetsResolved() = runTest {
    val bet = activeBetWithThreePlayers()

    repository.adminResolveBet(bet.betId, bet.outcomeA)

    val resolved = details(bet.betId).bet
    assertThat(resolved.status).isEqualTo(BetStatus.RESOLVED)
    assertThat(resolved.winningOutcomeId).isEqualTo(bet.outcomeA)
    assertThat(userOf(bet.alice).balance).isEqualTo(1050L)
    assertThat(userOf(bet.alice).totalWins).isEqualTo(1)
    assertThat(userOf(bet.carol).balance).isEqualTo(1150L)
    assertThat(userOf(bet.carol).totalWins).isEqualTo(1)
    assertThat(userOf(bet.bob).balance).isEqualTo(800L)
    assertThat(userOf(bet.bob).totalLosses).isEqualTo(1)
  }

  @Test
  fun adminResolveBet_toOutcomeNobodyPickedIsPush() = runTest {
    val bet = activeBetWithThreePlayers()

    repository.adminResolveBet(bet.betId, bet.outcomeC)

    assertThat(details(bet.betId).bet.status).isEqualTo(BetStatus.RESOLVED)
    for (userId in listOf(bet.alice, bet.bob, bet.carol)) {
      val user = userOf(userId)
      assertThat(user.balance).isEqualTo(1000L)
      assertThat(user.totalWins).isEqualTo(0)
      assertThat(user.totalLosses).isEqualTo(0)
      assertThat(user.totalEarnings).isEqualTo(0L)
      assertThat(user.totalLost).isEqualTo(0L)
    }
  }

  @Test
  fun adminResolveBet_rejectsBetThatIsNotActive() = runTest {
    val (betId, _, _) = pendingBetWithTwoPlayers()
    val winner = outcomeIds(betId).first()
    assertRejected<InvalidBetStateException> { repository.adminResolveBet(betId, winner) }

    repository.lockBet(betId)
    repository.adminResolveBet(betId, winner)
    assertRejected<InvalidBetStateException> { repository.adminResolveBet(betId, winner) }
  }

  @Test
  fun adminResolveBet_rejectsOutcomeFromAnotherBet() = runTest {
    val bet = activeBetWithThreePlayers()
    val otherBetId = repository.createBet("Other?", listOf("x", "y"))
    val foreignOutcome = outcomeIds(otherBetId).first()

    assertRejected<InvalidInputException> { repository.adminResolveBet(bet.betId, foreignOutcome) }
  }

  @Test
  fun adminResolveBet_rejectsUnknownBet() = runTest {
    assertRejected<InvalidInputException> { repository.adminResolveBet(999L, 1L) }
  }

  // --- updateBetPrompt ---

  @Test
  fun updateBetPrompt_trimsAndStoresNewPromptInAnyStatus() = runTest {
    val (betId, _, _) = pendingBetWithTwoPlayers()
    val winner = outcomeIds(betId).first()

    suspend fun assertPromptUpdates() {
      val before = details(betId).bet
      repository.updateBetPrompt(betId, "  New prompt  ")
      val after = details(betId).bet
      assertThat(after.prompt).isEqualTo("New prompt")
      assertThat(after).isEqualTo(before.copy(prompt = "New prompt"))
      // Reset so the next status change is checked against a different old prompt.
      repository.updateBetPrompt(betId, "Old prompt")
    }

    assertThat(details(betId).bet.status).isEqualTo(BetStatus.PENDING_CONFIRMATION)
    assertPromptUpdates()

    repository.lockBet(betId)
    assertThat(details(betId).bet.status).isEqualTo(BetStatus.ACTIVE)
    assertPromptUpdates()

    repository.resolveBet(betId, winner)
    assertThat(details(betId).bet.status).isEqualTo(BetStatus.RESOLVED)
    assertPromptUpdates()

    repository.reopenBet(betId)
    repository.cancelBet(betId)
    assertThat(details(betId).bet.status).isEqualTo(BetStatus.CANCELLED)
    assertPromptUpdates()
  }

  @Test
  fun updateBetPrompt_rejectsBlankOrOverlongPrompt() = runTest {
    val (betId, _, _) = pendingBetWithTwoPlayers()

    assertRejected<InvalidInputException> { repository.updateBetPrompt(betId, "   ") }
    assertRejected<InvalidInputException> { repository.updateBetPrompt(betId, "x".repeat(121)) }

    repository.updateBetPrompt(betId, "x".repeat(120))
    assertThat(details(betId).bet.prompt).isEqualTo("x".repeat(120))
  }

  @Test
  fun updateBetPrompt_rejectsUnknownBet() = runTest {
    assertRejected<InvalidInputException> { repository.updateBetPrompt(999L, "Hello") }
  }

  // --- deleteBet ---

  @Test
  fun deleteBet_removesResolvedBetWithOutcomesAndParticipantsButKeepsBalancesAndStats() = runTest {
    val bet = activeBetWithThreePlayers()
    val otherBetId = repository.createBet("Other?", listOf("x", "y"))
    repository.resolveBet(bet.betId, bet.outcomeA)
    val usersBefore = database.userDao().getAll().first().sortedBy { it.id }

    repository.deleteBet(bet.betId)

    assertThat(database.betDao().getById(bet.betId)).isNull()
    assertThat(database.betOutcomeDao().getByBetId(bet.betId)).isEmpty()
    assertThat(database.betParticipantDao().getByBetId(bet.betId)).isEmpty()
    assertThat(database.userDao().getAll().first().sortedBy { it.id }).isEqualTo(usersBefore)
    assertThat(database.betDao().getById(otherBetId)).isNotNull()
    assertThat(database.betOutcomeDao().getByBetId(otherBetId)).hasSize(2)
  }

  @Test
  fun deleteBet_removesCancelledBet() = runTest {
    val bet = activeBetWithThreePlayers()
    repository.forceCancelBet(bet.betId)

    repository.deleteBet(bet.betId)

    assertThat(database.betDao().getById(bet.betId)).isNull()
  }

  @Test
  fun deleteBet_worksWhenAParticipantWasDeleted() = runTest {
    val bet = activeBetWithThreePlayers()
    repository.resolveBet(bet.betId, bet.outcomeA)
    deleteUser(bet.bob)

    repository.deleteBet(bet.betId)

    assertThat(database.betDao().getById(bet.betId)).isNull()
    assertThat(database.betParticipantDao().getByBetId(bet.betId)).isEmpty()
  }

  @Test
  fun deleteBet_rejectsPendingAndActiveBets() = runTest {
    val (betId, _, _) = pendingBetWithTwoPlayers()
    assertRejected<InvalidBetStateException> { repository.deleteBet(betId) }

    repository.lockBet(betId)
    assertRejected<InvalidBetStateException> { repository.deleteBet(betId) }
  }

  @Test
  fun deleteBet_rejectsUnknownBet() = runTest {
    assertRejected<InvalidInputException> { repository.deleteBet(999L) }
  }

  // --- adminRemoveParticipant ---

  private data class FourPlayerBet(
    val betId: Long,
    val alice: Long,
    val bob: Long,
    val carol: Long,
    val dave: Long,
    val outcomeA: Long,
    val outcomeB: Long,
  )

  /** Alice 100 and Carol 300 on A, Bob 200 and Dave 400 on B. Locked unless [lock] is false. */
  private suspend fun betWithFourPlayers(lock: Boolean = true, prefix: String = ""): FourPlayerBet {
    val alice = insertUser("${prefix}Alice")
    val bob = insertUser("${prefix}Bob")
    val carol = insertUser("${prefix}Carol")
    val dave = insertUser("${prefix}Dave")
    val betId = repository.createBet("Who wins?", listOf("A", "B"))
    val (a, b) = outcomeIds(betId)
    repository.addParticipant(betId, alice, a, 100L)
    repository.addParticipant(betId, bob, b, 200L)
    repository.addParticipant(betId, carol, a, 300L)
    repository.addParticipant(betId, dave, b, 400L)
    if (lock) repository.lockBet(betId)
    return FourPlayerBet(betId, alice, bob, carol, dave, a, b)
  }

  private suspend fun participantIdOf(betId: Long, userId: Long): Long =
    database.betParticipantDao().getByBetId(betId).first { it.userId == userId }.id

  @Test
  fun adminRemoveParticipant_refundsWagerAndRemovesRow() = runTest {
    val bet = betWithFourPlayers()

    repository.adminRemoveParticipant(bet.betId, participantIdOf(bet.betId, bet.carol))

    assertThat(balanceOf(bet.carol)).isEqualTo(1000L)
    val rows = database.betParticipantDao().getByBetId(bet.betId)
    assertThat(rows.map { it.userId }).containsExactly(bet.alice, bet.bob, bet.dave)
    assertThat(rows.map { it.wagerAmount }).containsExactly(100L, 200L, 400L)
    assertThat(balanceOf(bet.alice)).isEqualTo(900L)
    assertThat(balanceOf(bet.bob)).isEqualTo(800L)
    assertThat(balanceOf(bet.dave)).isEqualTo(600L)
    assertThat(details(bet.betId).bet.status).isEqualTo(BetStatus.ACTIVE)
  }

  @Test
  fun adminRemoveParticipant_rejectsWhenFewerThanTwoWouldRemain() = runTest {
    val (betId, alice, bob) = pendingBetWithTwoPlayers()
    repository.lockBet(betId)

    assertRejected<InvalidInputException> {
      repository.adminRemoveParticipant(betId, participantIdOf(betId, alice))
    }
    assertRejected<InvalidInputException> {
      repository.adminRemoveParticipant(betId, participantIdOf(betId, bob))
    }
  }

  @Test
  fun adminRemoveParticipant_rejectsWhenEveryoneLeftIsOnOneOutcome() = runTest {
    val bet = activeBetWithThreePlayers()

    assertRejected<UnanimousPickException> {
      repository.adminRemoveParticipant(bet.betId, participantIdOf(bet.betId, bet.bob))
    }
  }

  @Test
  fun adminRemoveParticipant_rejectsBetThatIsNotActive() = runTest {
    val bet = betWithFourPlayers(lock = false)
    val participantId = participantIdOf(bet.betId, bet.carol)
    assertRejected<InvalidBetStateException> {
      repository.adminRemoveParticipant(bet.betId, participantId)
    }

    repository.lockBet(bet.betId)
    repository.resolveBet(bet.betId, bet.outcomeA)
    assertRejected<InvalidBetStateException> {
      repository.adminRemoveParticipant(bet.betId, participantId)
    }

    val other = betWithFourPlayers(prefix = "X")
    repository.forceCancelBet(other.betId)
    assertRejected<InvalidBetStateException> {
      repository.adminRemoveParticipant(other.betId, participantIdOf(other.betId, other.carol))
    }
  }

  @Test
  fun adminRemoveParticipant_rejectsParticipantFromAnotherBetOrUnknown() = runTest {
    val x = betWithFourPlayers()
    val y = betWithFourPlayers(prefix = "Y")
    val participantOfX = participantIdOf(x.betId, x.carol)

    assertRejected<InvalidInputException> { repository.adminRemoveParticipant(y.betId, participantOfX) }
    assertRejected<InvalidInputException> { repository.adminRemoveParticipant(x.betId, 999L) }
    assertRejected<InvalidInputException> { repository.adminRemoveParticipant(999L, participantOfX) }
  }

  // --- adminEditWager ---

  @Test
  fun adminEditWager_increaseDeductsDifference() = runTest {
    val bet = activeBetWithThreePlayers()
    val participantId = participantIdOf(bet.betId, bet.alice)

    repository.adminEditWager(participantId, 250L)

    assertThat(balanceOf(bet.alice)).isEqualTo(750L)
    assertThat(database.betParticipantDao().getById(participantId)!!.wagerAmount).isEqualTo(250L)
  }

  @Test
  fun adminEditWager_decreaseRefundsDifference() = runTest {
    val bet = activeBetWithThreePlayers()
    val participantId = participantIdOf(bet.betId, bet.carol)

    repository.adminEditWager(participantId, 50L)

    assertThat(balanceOf(bet.carol)).isEqualTo(950L)
    assertThat(database.betParticipantDao().getById(participantId)!!.wagerAmount).isEqualTo(50L)
  }

  @Test
  fun adminEditWager_sameAmountChangesNothing() = runTest {
    val bet = activeBetWithThreePlayers()
    val before = snapshot()

    repository.adminEditWager(participantIdOf(bet.betId, bet.alice), 100L)

    assertThat(snapshot()).isEqualTo(before)
  }

  @Test
  fun adminEditWager_allowsIncreaseExactlyCoveredByBalance() = runTest {
    val bet = activeBetWithThreePlayers()
    val participantId = participantIdOf(bet.betId, bet.alice)

    repository.adminEditWager(participantId, 1000L)

    assertThat(balanceOf(bet.alice)).isEqualTo(0L)
    assertThat(database.betParticipantDao().getById(participantId)!!.wagerAmount).isEqualTo(1000L)
  }

  @Test
  fun adminEditWager_rejectsIncreaseUserCantCover() = runTest {
    val bet = activeBetWithThreePlayers()
    val participantId = participantIdOf(bet.betId, bet.alice)

    val error = assertRejected<InsufficientBalanceException> {
      repository.adminEditWager(participantId, 1001L)
    }

    assertThat(error.required).isEqualTo(901L)
    assertThat(error.available).isEqualTo(900L)
  }

  @Test
  fun adminEditWager_rejectsWagerBelowOne() = runTest {
    val bet = activeBetWithThreePlayers()
    val participantId = participantIdOf(bet.betId, bet.alice)

    assertRejected<InvalidInputException> { repository.adminEditWager(participantId, 0L) }
    assertRejected<InvalidInputException> { repository.adminEditWager(participantId, -5L) }
  }

  @Test
  fun adminEditWager_rejectsBetThatIsNotActive() = runTest {
    val (pendingId, alice, _) = pendingBetWithTwoPlayers()
    assertRejected<InvalidBetStateException> {
      repository.adminEditWager(participantIdOf(pendingId, alice), 50L)
    }

    val resolved = activeBetWithThreePlayers(prefix = "R")
    val resolvedParticipant = participantIdOf(resolved.betId, resolved.alice)
    repository.resolveBet(resolved.betId, resolved.outcomeA)
    assertRejected<InvalidBetStateException> { repository.adminEditWager(resolvedParticipant, 50L) }

    val cancelled = betWithFourPlayers(prefix = "X")
    repository.forceCancelBet(cancelled.betId)
    assertRejected<InvalidBetStateException> {
      repository.adminEditWager(participantIdOf(cancelled.betId, cancelled.alice), 50L)
    }
  }

  @Test
  fun adminEditWager_rejectsUnknownParticipant() = runTest {
    activeBetWithThreePlayers()

    assertRejected<InvalidInputException> { repository.adminEditWager(999L, 50L) }
  }
}
