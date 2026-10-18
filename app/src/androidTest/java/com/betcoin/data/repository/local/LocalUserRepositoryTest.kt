package com.betcoin.data.repository.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.betcoin.data.database.BetCoinDatabase
import com.betcoin.data.database.entity.Bet
import com.betcoin.data.database.entity.BetOutcome
import com.betcoin.data.database.entity.BetParticipant
import com.betcoin.data.database.entity.User
import com.betcoin.data.model.ActiveBetConflictException
import com.betcoin.data.model.BetStatus
import com.betcoin.data.model.DuplicateUsernameException
import com.betcoin.data.model.InvalidInputException
import com.betcoin.data.model.NotEligibleForBailoutException
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for [LocalUserRepository] against an in-memory Room database.
 *
 * Every rule is covered, and each failure case checks that the database is left unchanged.
 */
@RunWith(AndroidJUnit4::class)
class LocalUserRepositoryTest {

  private lateinit var database: BetCoinDatabase
  private lateinit var repository: LocalUserRepository

  @Before
  fun setUp() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    database = Room.inMemoryDatabaseBuilder(context, BetCoinDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    repository = LocalUserRepository(database)
  }

  @After
  fun tearDown() {
    database.close()
  }

  // --- Helpers ---

  /** Everything a user operation can touch, for "database unchanged" checks. */
  private data class Snapshot(val users: List<User>, val participants: List<BetParticipant>)

  private suspend fun snapshot(): Snapshot {
    val users = database.userDao().getAll().first().sortedBy { it.id }
    val betIds = database.betDao().getAll().first().map { it.id }
    val participants = betIds.flatMap { database.betParticipantDao().getByBetId(it) }
      .sortedBy { it.id }
    return Snapshot(users, participants)
  }

  /** Inserts a user directly (skipping bcrypt) and returns its ID. */
  private suspend fun insertUser(
    username: String,
    balance: Long = 1000L,
    totalDebt: Long = 0L,
  ): Long = database.userDao().insert(
    User(username = username, pinHash = "x", balance = balance, totalDebt = totalDebt, createdAt = 0L)
  )

  /** Creates a bet in [status] with the given (userId, wager) participants; returns the bet ID. */
  private suspend fun insertBet(status: BetStatus, vararg wagers: Pair<Long, Long>): Long {
    val betId = database.betDao().insert(Bet(prompt = "Who wins?", status = status, createdAt = 0L))
    val outcomeId = database.betOutcomeDao().insert(BetOutcome(betId = betId, outcomeText = "A"))
    wagers.forEach { (userId, wager) ->
      database.betParticipantDao().insert(
        BetParticipant(betId = betId, userId = userId, outcomeId = outcomeId, wagerAmount = wager)
      )
    }
    return betId
  }

  private suspend fun user(id: Long): User = repository.getUser(id)!!

  private suspend inline fun <reified T : Throwable> assertFails(block: suspend () -> Unit) {
    val thrown = try {
      block()
      null
    } catch (e: Throwable) {
      e
    }
    if (thrown == null) fail("Expected ${T::class.java.simpleName} but nothing was thrown")
    assertThat(thrown).isInstanceOf(T::class.java)
  }

  // --- createUser ---

  @Test
  fun createUser_storesTrimmedNameStartingBalanceAndBcryptHash() = runTest {
    val id = repository.createUser("  Alice  ", "1234")

    val user = user(id)
    assertThat(user.username).isEqualTo("Alice")
    assertThat(user.balance).isEqualTo(1000L)
    assertThat(user.pinHash).startsWith("\$2a\$10\$")
    assertThat(user.pinHash).doesNotContain("1234")
  }

  @Test
  fun createUser_acceptsTwentyCharacterName() = runTest {
    val id = repository.createUser("a".repeat(20), "1234")
    assertThat(user(id).username).hasLength(20)
  }

  @Test
  fun createUser_blankName_throwsAndWritesNothing() = runTest {
    assertFails<InvalidInputException> { repository.createUser("   ", "1234") }
    assertThat(snapshot().users).isEmpty()
  }

  @Test
  fun createUser_nameOverTwentyChars_throwsAndWritesNothing() = runTest {
    assertFails<InvalidInputException> { repository.createUser("a".repeat(21), "1234") }
    assertThat(snapshot().users).isEmpty()
  }

  @Test
  fun createUser_invalidPins_throwAndWriteNothing() = runTest {
    listOf("123", "12345", "12a4", "", "１２３４").forEach { pin ->
      assertFails<InvalidInputException> { repository.createUser("Alice", pin) }
    }
    assertThat(snapshot().users).isEmpty()
  }

  @Test
  fun createUser_duplicateIgnoringCase_throwsAndWritesNothing() = runTest {
    repository.createUser("Jay", "1234")
    val before = snapshot()

    assertFails<DuplicateUsernameException> { repository.createUser(" jAY ", "5678") }
    assertThat(snapshot()).isEqualTo(before)
  }

  // --- verifyPin ---

  @Test
  fun verifyPin_matchesOnlyTheRightPin() = runTest {
    val id = repository.createUser("Alice", "1234")

    assertThat(repository.verifyPin(id, "1234")).isTrue()
    assertThat(repository.verifyPin(id, "4321")).isFalse()
    assertThat(repository.verifyPin(id, "")).isFalse()
  }

  @Test
  fun verifyPin_unknownUser_returnsFalse() = runTest {
    assertThat(repository.verifyPin(99L, "1234")).isFalse()
  }

  // --- getLeaderboard ---

  @Test
  fun getLeaderboard_ordersByNetWorthThenBalanceThenName() = runTest {
    insertUser("carol", balance = 1500, totalDebt = 1000) // net 500
    insertUser("Bob", balance = 900) // net 900
    insertUser("alice", balance = 900) // net 900, ties Bob on balance
    insertUser("Dave", balance = 2000, totalDebt = 1100) // net 900, higher balance
    insertUser("eve", balance = -50) // net -50

    val names = repository.getLeaderboard().first().map { it.username }

    assertThat(names).containsExactly("Dave", "alice", "Bob", "carol", "eve").inOrder()
  }

  // --- getLockedBalance ---

  @Test
  fun getLockedBalance_sumsOnlyActiveBetWagers() = runTest {
    val alice = insertUser("alice")
    val bob = insertUser("bob")
    insertBet(BetStatus.ACTIVE, alice to 100, bob to 50)
    insertBet(BetStatus.ACTIVE, alice to 25)
    insertBet(BetStatus.PENDING_CONFIRMATION, alice to 300)
    insertBet(BetStatus.RESOLVED, alice to 400)
    insertBet(BetStatus.CANCELLED, alice to 500)

    assertThat(repository.getLockedBalance(alice).first()).isEqualTo(125L)
    assertThat(repository.getLockedBalance(bob).first()).isEqualTo(50L)
  }

  @Test
  fun getLockedBalance_noBets_isZero() = runTest {
    val alice = insertUser("alice")
    assertThat(repository.getLockedBalance(alice).first()).isEqualTo(0L)
  }

  // --- bailout / forceBailout ---

  @Test
  fun bailout_eligible_addsCoinsCountAndDebt() = runTest {
    val alice = insertUser("alice", balance = 99)

    repository.bailout(alice)

    val user = user(alice)
    assertThat(user.balance).isEqualTo(1099L)
    assertThat(user.bailoutCount).isEqualTo(1)
    assertThat(user.totalDebt).isEqualTo(1000L)
  }

  @Test
  fun bailout_negativeBalance_isEligible() = runTest {
    val alice = insertUser("alice", balance = -500)
    repository.bailout(alice)
    assertThat(user(alice).balance).isEqualTo(500L)
  }

  @Test
  fun bailout_balanceAtThreshold_throwsAndWritesNothing() = runTest {
    val alice = insertUser("alice", balance = 100)
    val before = snapshot()

    assertFails<NotEligibleForBailoutException> { repository.bailout(alice) }
    assertThat(snapshot()).isEqualTo(before)
  }

  @Test
  fun bailout_lockedWagersCountTowardThreshold_throwsAndWritesNothing() = runTest {
    val alice = insertUser("alice", balance = 0)
    val bob = insertUser("bob")
    insertBet(BetStatus.ACTIVE, alice to 100, bob to 100)
    val before = snapshot()

    assertFails<NotEligibleForBailoutException> { repository.bailout(alice) }
    assertThat(snapshot()).isEqualTo(before)
  }

  @Test
  fun bailout_nonActiveWagersDontCount() = runTest {
    val alice = insertUser("alice", balance = 10)
    insertBet(BetStatus.RESOLVED, alice to 500)
    insertBet(BetStatus.PENDING_CONFIRMATION, alice to 500)

    repository.bailout(alice)

    assertThat(user(alice).bailoutCount).isEqualTo(1)
  }

  @Test
  fun forceBailout_skipsEligibilityCheck() = runTest {
    val alice = insertUser("alice", balance = 5000, totalDebt = 1000)

    repository.forceBailout(alice)

    val user = user(alice)
    assertThat(user.balance).isEqualTo(6000L)
    assertThat(user.bailoutCount).isEqualTo(1)
    assertThat(user.totalDebt).isEqualTo(2000L)
  }

  @Test
  fun bailoutAndForceBailout_unknownUser_throw() = runTest {
    assertFails<InvalidInputException> { repository.bailout(99L) }
    assertFails<InvalidInputException> { repository.forceBailout(99L) }
  }

  // --- deleteUser ---

  @Test
  fun deleteUser_inActiveBet_throwsAndWritesNothing() = runTest {
    val alice = insertUser("alice")
    val bob = insertUser("bob")
    insertBet(BetStatus.RESOLVED, alice to 10, bob to 10)
    insertBet(BetStatus.ACTIVE, alice to 20, bob to 20)
    val before = snapshot()

    assertFails<ActiveBetConflictException> { repository.deleteUser(alice) }
    assertThat(snapshot()).isEqualTo(before)
  }

  @Test
  fun deleteUser_snapshotsUsernameNullsFkAndDeletesRow() = runTest {
    val alice = insertUser("alice")
    val bob = insertUser("bob")
    val resolved = insertBet(BetStatus.RESOLVED, alice to 10, bob to 10)
    val cancelled = insertBet(BetStatus.CANCELLED, alice to 30, bob to 30)

    repository.deleteUser(alice)

    assertThat(repository.getUser(alice)).isNull()
    val participants = listOf(resolved, cancelled)
      .flatMap { database.betParticipantDao().getByBetId(it) }
    assertThat(participants).hasSize(4)
    val detached = participants.filter { it.deletedUsername != null }
    assertThat(detached).hasSize(2)
    detached.forEach {
      assertThat(it.userId).isNull()
      assertThat(it.deletedUsername).isEqualTo("alice")
    }
    assertThat(participants.filter { it.userId == bob }).hasSize(2)
  }

  @Test
  fun deleteUser_withNoBets_deletesRow() = runTest {
    val alice = insertUser("alice")
    repository.deleteUser(alice)
    assertThat(snapshot().users).isEmpty()
  }

  @Test
  fun deleteUser_unknownUser_throwsAndWritesNothing() = runTest {
    insertUser("alice")
    val before = snapshot()

    assertFails<InvalidInputException> { repository.deleteUser(99L) }
    assertThat(snapshot()).isEqualTo(before)
  }

  // --- resetPin ---

  @Test
  fun resetPin_replacesHash() = runTest {
    val id = repository.createUser("Alice", "1234")

    repository.resetPin(id, "9876")

    assertThat(repository.verifyPin(id, "9876")).isTrue()
    assertThat(repository.verifyPin(id, "1234")).isFalse()
    assertThat(user(id).pinHash).startsWith("\$2a\$10\$")
  }

  @Test
  fun resetPin_invalidPin_throwsAndWritesNothing() = runTest {
    val id = repository.createUser("Alice", "1234")
    val before = snapshot()

    assertFails<InvalidInputException> { repository.resetPin(id, "12") }
    assertThat(snapshot()).isEqualTo(before)
  }

  // --- updateBalance / setBalance ---

  @Test
  fun updateBalance_appliesSignedDelta() = runTest {
    val alice = insertUser("alice", balance = 1000)

    repository.updateBalance(alice, 250)
    repository.updateBalance(alice, -1500)

    assertThat(user(alice).balance).isEqualTo(-250L)
  }

  @Test
  fun setBalance_allowsNegative() = runTest {
    val alice = insertUser("alice")

    repository.setBalance(alice, -42)

    assertThat(user(alice).balance).isEqualTo(-42L)
  }

  @Test
  fun updateAndSetBalance_unknownUser_throwAndWriteNothing() = runTest {
    insertUser("alice")
    val before = snapshot()

    assertFails<InvalidInputException> { repository.updateBalance(99L, 10) }
    assertFails<InvalidInputException> { repository.setBalance(99L, 10) }
    assertThat(snapshot()).isEqualTo(before)
  }

  // --- updateUsername ---

  @Test
  fun updateUsername_trimsAndRenames() = runTest {
    val alice = insertUser("alice")

    repository.updateUsername(alice, "  Alicia ")

    assertThat(user(alice).username).isEqualTo("Alicia")
  }

  @Test
  fun updateUsername_caseChangeOfOwnName_isAllowed() = runTest {
    val alice = insertUser("alice")

    repository.updateUsername(alice, "ALICE")

    assertThat(user(alice).username).isEqualTo("ALICE")
  }

  @Test
  fun updateUsername_takenIgnoringCase_throwsAndWritesNothing() = runTest {
    val alice = insertUser("alice")
    insertUser("Bob")
    val before = snapshot()

    assertFails<DuplicateUsernameException> { repository.updateUsername(alice, "bOB") }
    assertThat(snapshot()).isEqualTo(before)
  }

  @Test
  fun updateUsername_invalidLength_throwsAndWritesNothing() = runTest {
    val alice = insertUser("alice")
    val before = snapshot()

    assertFails<InvalidInputException> { repository.updateUsername(alice, " ") }
    assertFails<InvalidInputException> { repository.updateUsername(alice, "b".repeat(21)) }
    assertThat(snapshot()).isEqualTo(before)
  }
}
