package com.betcoin.data.database.dao

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.betcoin.data.database.BetCoinDatabase
import com.betcoin.data.database.entity.Bet
import com.betcoin.data.database.entity.BetOutcome
import com.betcoin.data.database.entity.BetParticipant
import com.betcoin.data.database.entity.User
import com.betcoin.data.model.BetStatus
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for [BetParticipantDao].
 *
 * Uses an in-memory Room database so tests are hermetic and fast.
 */
@RunWith(AndroidJUnit4::class)
class BetParticipantDaoTest {

  private lateinit var database: BetCoinDatabase
  private lateinit var betDao: BetDao
  private lateinit var betOutcomeDao: BetOutcomeDao
  private lateinit var betParticipantDao: BetParticipantDao
  private lateinit var userDao: UserDao

  @Before
  fun setUp() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    database = Room.inMemoryDatabaseBuilder(context, BetCoinDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    betDao = database.betDao()
    betOutcomeDao = database.betOutcomeDao()
    betParticipantDao = database.betParticipantDao()
    userDao = database.userDao()
  }

  @After
  fun tearDown() {
    database.close()
  }

  // --- Helpers ---

  private suspend fun insertBet(): Long = betDao.insert(
    Bet(prompt = "Will it rain?", status = BetStatus.ACTIVE, createdAt = 1_000L),
  )

  private suspend fun insertOutcome(betId: Long, text: String = "Yes"): Long =
    betOutcomeDao.insert(BetOutcome(betId = betId, outcomeText = text))

  private suspend fun insertUser(username: String = "alice"): User {
    val user = User(username = username, pinHash = "hashed_pin", createdAt = 1_000L)
    return user.copy(id = userDao.insert(user))
  }

  private fun createParticipant(
    betId: Long,
    userId: Long?,
    outcomeId: Long,
    wagerAmount: Long = 100L,
  ): BetParticipant = BetParticipant(
    betId = betId,
    userId = userId,
    outcomeId = outcomeId,
    wagerAmount = wagerAmount,
  )

  // --- insert ---

  @Test
  fun insert_returnsPositiveId() = runTest {
    val betId = insertBet()
    val id = betParticipantDao.insert(
      createParticipant(betId, insertUser().id, insertOutcome(betId)),
    )

    assertThat(id).isGreaterThan(0L)
  }

  // --- getById ---

  @Test
  fun getById_returnsInserted() = runTest {
    val betId = insertBet()
    val participant = createParticipant(betId, insertUser().id, insertOutcome(betId), 250L)
    val id = betParticipantDao.insert(participant)

    assertThat(betParticipantDao.getById(id)).isEqualTo(participant.copy(id = id))
  }

  @Test
  fun getById_unknownId_returnsNull() = runTest {
    assertThat(betParticipantDao.getById(999L)).isNull()
  }

  // --- getByBetId ---

  @Test
  fun getByBetId_returnsOnlyThatBetsParticipants() = runTest {
    val betA = insertBet()
    val betB = insertBet()
    val user = insertUser()
    val inA = betParticipantDao.insert(createParticipant(betA, user.id, insertOutcome(betA)))
    betParticipantDao.insert(createParticipant(betB, user.id, insertOutcome(betB)))

    val result = betParticipantDao.getByBetId(betA)

    assertThat(result.map { it.id }).containsExactly(inA)
  }

  // --- getByBetIdWithUser ---

  @Test
  fun getByBetIdWithUser_loadsUserAndOutcome() = runTest {
    val betId = insertBet()
    val user = insertUser("alice")
    val outcomeId = insertOutcome(betId, "Yes")
    betParticipantDao.insert(createParticipant(betId, user.id, outcomeId))

    val result = betParticipantDao.getByBetIdWithUser(betId).single()

    assertThat(result.user).isEqualTo(user)
    assertThat(result.outcome.id).isEqualTo(outcomeId)
    assertThat(result.outcome.outcomeText).isEqualTo("Yes")
  }

  @Test
  fun getByBetIdWithUser_deletedUser_hasNullUserAndKeepsDeletedUsername() = runTest {
    val betId = insertBet()
    val user = insertUser("alice")
    betParticipantDao.insert(createParticipant(betId, user.id, insertOutcome(betId, "Yes")))

    betParticipantDao.detachUser(user.id, "alice")
    userDao.delete(user)

    val result = betParticipantDao.getByBetIdWithUser(betId).single()
    assertThat(result.user).isNull()
    assertThat(result.participant.userId).isNull()
    assertThat(result.participant.deletedUsername).isEqualTo("alice")
    assertThat(result.outcome.outcomeText).isEqualTo("Yes")
  }

  // --- getByUserIdAndBetId ---

  @Test
  fun getByUserIdAndBetId_returnsMatch() = runTest {
    val betId = insertBet()
    val user = insertUser()
    val id = betParticipantDao.insert(createParticipant(betId, user.id, insertOutcome(betId)))

    assertThat(betParticipantDao.getByUserIdAndBetId(user.id, betId)?.id).isEqualTo(id)
  }

  @Test
  fun getByUserIdAndBetId_userInOtherBet_returnsNull() = runTest {
    val betA = insertBet()
    val betB = insertBet()
    val user = insertUser()
    betParticipantDao.insert(createParticipant(betA, user.id, insertOutcome(betA)))

    assertThat(betParticipantDao.getByUserIdAndBetId(user.id, betB)).isNull()
  }

  // --- getByUserId ---

  @Test
  fun getByUserId_returnsAcrossBets() = runTest {
    val betA = insertBet()
    val betB = insertBet()
    val alice = insertUser("alice")
    val bob = insertUser("bob")
    val a1 = betParticipantDao.insert(createParticipant(betA, alice.id, insertOutcome(betA)))
    val a2 = betParticipantDao.insert(createParticipant(betB, alice.id, insertOutcome(betB)))
    betParticipantDao.insert(createParticipant(betB, bob.id, insertOutcome(betB, "No")))

    val result = betParticipantDao.getByUserId(alice.id)

    assertThat(result.map { it.id }).containsExactly(a1, a2)
  }

  // --- update ---

  @Test
  fun update_persistsPayout() = runTest {
    val betId = insertBet()
    val participant = createParticipant(betId, insertUser().id, insertOutcome(betId))
    val id = betParticipantDao.insert(participant)

    val updated = participant.copy(id = id, payout = 200L)
    betParticipantDao.update(updated)

    assertThat(betParticipantDao.getById(id)).isEqualTo(updated)
  }

  // --- delete ---

  @Test
  fun delete_removesParticipant() = runTest {
    val betId = insertBet()
    val participant = createParticipant(betId, insertUser().id, insertOutcome(betId))
    val id = betParticipantDao.insert(participant)

    betParticipantDao.delete(participant.copy(id = id))

    assertThat(betParticipantDao.getById(id)).isNull()
  }

  // --- deleteByBetId ---

  @Test
  fun deleteByBetId_removesOnlyThatBetsParticipants() = runTest {
    val betA = insertBet()
    val betB = insertBet()
    val user = insertUser()
    betParticipantDao.insert(createParticipant(betA, user.id, insertOutcome(betA)))
    val inB = betParticipantDao.insert(createParticipant(betB, user.id, insertOutcome(betB)))

    betParticipantDao.deleteByBetId(betA)

    assertThat(betParticipantDao.getByBetId(betA)).isEmpty()
    assertThat(betParticipantDao.getByBetId(betB).map { it.id }).containsExactly(inB)
  }

  // --- detachUser ---

  @Test
  fun detachUser_snapshotsUsernameAndNullsUserId_onlyForThatUser() = runTest {
    val betA = insertBet()
    val betB = insertBet()
    val alice = insertUser("alice")
    val bob = insertUser("bob")
    val a1 = betParticipantDao.insert(createParticipant(betA, alice.id, insertOutcome(betA)))
    val a2 = betParticipantDao.insert(createParticipant(betB, alice.id, insertOutcome(betB)))
    val b1 = betParticipantDao.insert(createParticipant(betB, bob.id, insertOutcome(betB, "No")))

    betParticipantDao.detachUser(alice.id, "alice")

    for (id in listOf(a1, a2)) {
      val detached = betParticipantDao.getById(id)!!
      assertThat(detached.userId).isNull()
      assertThat(detached.deletedUsername).isEqualTo("alice")
    }
    val untouched = betParticipantDao.getById(b1)!!
    assertThat(untouched.userId).isEqualTo(bob.id)
    assertThat(untouched.deletedUsername).isNull()
  }
}
