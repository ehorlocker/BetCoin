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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for [BetDao].
 *
 * Uses an in-memory Room database so tests are hermetic and fast. All timestamps are explicit so
 * ordering assertions are deterministic.
 */
@RunWith(AndroidJUnit4::class)
class BetDaoTest {

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

  private fun createBet(
    prompt: String = "Will it rain?",
    status: BetStatus = BetStatus.ACTIVE,
    winningOutcomeId: Long? = null,
    createdAt: Long = 1_000L,
    resolvedAt: Long? = null,
  ): Bet = Bet(
    prompt = prompt,
    status = status,
    winningOutcomeId = winningOutcomeId,
    createdAt = createdAt,
    resolvedAt = resolvedAt,
  )

  private fun createOutcome(betId: Long, text: String = "Yes"): BetOutcome =
    BetOutcome(betId = betId, outcomeText = text)

  private fun createUser(username: String = "alice"): User =
    User(username = username, pinHash = "hashed_pin", createdAt = 1_000L)

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

  /** Inserts [bet] and returns it with its generated id. */
  private suspend fun insertBet(bet: Bet): Bet = bet.copy(id = betDao.insert(bet))

  // --- insert ---

  @Test
  fun insert_returnsPositiveId() = runTest {
    val id = betDao.insert(createBet())

    assertThat(id).isGreaterThan(0L)
  }

  // --- getById ---

  @Test
  fun getById_returnsInsertedBet() = runTest {
    val bet = insertBet(createBet(prompt = "Who wins?"))

    assertThat(betDao.getById(bet.id)).isEqualTo(bet)
  }

  @Test
  fun getById_unknownId_returnsNull() = runTest {
    assertThat(betDao.getById(999L)).isNull()
  }

  // --- getByStatus ---

  @Test
  fun getByStatus_returnsOnlyMatchingStatus_newestFirst() = runTest {
    val first = insertBet(createBet(status = BetStatus.ACTIVE, createdAt = 1_000L))
    insertBet(createBet(status = BetStatus.RESOLVED, createdAt = 2_000L))
    val third = insertBet(createBet(status = BetStatus.ACTIVE, createdAt = 3_000L))

    val result = betDao.getByStatus(BetStatus.ACTIVE).first()

    assertThat(result.map { it.id }).containsExactly(third.id, first.id).inOrder()
  }

  // --- getAll ---

  @Test
  fun getAll_returnsAllBetsNewestFirst() = runTest {
    val first = insertBet(createBet(status = BetStatus.ACTIVE, createdAt = 1_000L))
    val second = insertBet(createBet(status = BetStatus.RESOLVED, createdAt = 2_000L))
    val third = insertBet(createBet(status = BetStatus.CANCELLED, createdAt = 3_000L))

    val result = betDao.getAll().first()

    assertThat(result.map { it.id }).containsExactly(third.id, second.id, first.id).inOrder()
  }

  // --- getPendingBets ---

  @Test
  fun getPendingBets_returnsOnlyPendingConfirmation() = runTest {
    val pending = insertBet(createBet(status = BetStatus.PENDING_CONFIRMATION))
    insertBet(createBet(status = BetStatus.ACTIVE))
    insertBet(createBet(status = BetStatus.RESOLVED))
    insertBet(createBet(status = BetStatus.CANCELLED))

    val result = betDao.getPendingBets()

    assertThat(result.map { it.id }).containsExactly(pending.id)
  }

  // --- update ---

  @Test
  fun update_persistsChanges() = runTest {
    val bet = insertBet(createBet(status = BetStatus.ACTIVE))
    val outcomeId = betOutcomeDao.insert(createOutcome(bet.id))

    val updated = bet.copy(
      status = BetStatus.RESOLVED,
      winningOutcomeId = outcomeId,
      resolvedAt = 5_000L,
    )
    betDao.update(updated)

    assertThat(betDao.getById(bet.id)).isEqualTo(updated)
  }

  // --- delete ---

  @Test
  fun delete_removesBet() = runTest {
    val bet = insertBet(createBet())

    betDao.delete(bet)

    assertThat(betDao.getById(bet.id)).isNull()
  }

  // --- getByIdWithDetails ---

  @Test
  fun getByIdWithDetails_loadsOutcomesAndParticipantsWithUsers() = runTest {
    val bet = insertBet(createBet())
    val yesId = betOutcomeDao.insert(createOutcome(bet.id, "Yes"))
    val noId = betOutcomeDao.insert(createOutcome(bet.id, "No"))
    val aliceId = userDao.insert(createUser("alice"))
    val bobId = userDao.insert(createUser("bob"))
    betParticipantDao.insert(createParticipant(bet.id, aliceId, yesId))
    betParticipantDao.insert(createParticipant(bet.id, bobId, noId))

    val details = betDao.getByIdWithDetails(bet.id).first()

    assertThat(details).isNotNull()
    assertThat(details!!.bet).isEqualTo(bet)
    assertThat(details.outcomes).hasSize(2)
    assertThat(details.participants).hasSize(2)
    val byUsername = details.participants.associateBy { it.user!!.username }
    assertThat(byUsername["alice"]!!.outcome.outcomeText).isEqualTo("Yes")
    assertThat(byUsername["bob"]!!.outcome.outcomeText).isEqualTo("No")
  }

  @Test
  fun getByIdWithDetails_unknownId_emitsNull() = runTest {
    assertThat(betDao.getByIdWithDetails(999L).first()).isNull()
  }

  @Test
  fun getByIdWithDetails_deletedUser_hasNullUserAndKeepsDeletedUsername() = runTest {
    val bet = insertBet(createBet())
    val outcomeId = betOutcomeDao.insert(createOutcome(bet.id, "Yes"))
    val user = createUser("alice").copy(id = userDao.insert(createUser("alice")))
    betParticipantDao.insert(createParticipant(bet.id, user.id, outcomeId))

    betParticipantDao.detachUser(user.id, "alice")
    userDao.delete(user)

    val participant = betDao.getByIdWithDetails(bet.id).first()!!.participants.single()
    assertThat(participant.user).isNull()
    assertThat(participant.participant.deletedUsername).isEqualTo("alice")
    assertThat(participant.participant.userId).isNull()
    assertThat(participant.outcome.outcomeText).isEqualTo("Yes")
  }

  // --- getAllWithDetails ---

  @Test
  fun getAllWithDetails_newestFirst() = runTest {
    val first = insertBet(createBet(createdAt = 1_000L))
    val second = insertBet(createBet(createdAt = 2_000L))

    val result = betDao.getAllWithDetails().first()

    assertThat(result.map { it.bet.id }).containsExactly(second.id, first.id).inOrder()
  }

  // --- getByStatusWithDetails ---

  @Test
  fun getByStatusWithDetails_filtersAndOrdersNewestFirst() = runTest {
    val first = insertBet(createBet(status = BetStatus.RESOLVED, createdAt = 1_000L))
    insertBet(createBet(status = BetStatus.ACTIVE, createdAt = 2_000L))
    val third = insertBet(createBet(status = BetStatus.RESOLVED, createdAt = 3_000L))

    val result = betDao.getByStatusWithDetails(BetStatus.RESOLVED).first()

    assertThat(result.map { it.bet.id }).containsExactly(third.id, first.id).inOrder()
  }

  // --- getAllExcludingStatusWithDetails ---

  @Test
  fun getAllExcludingStatusWithDetails_excludesStatus_newestFirst_tiesByIdDesc() = runTest {
    val oldest = insertBet(createBet(status = BetStatus.ACTIVE, createdAt = 1_000L))
    val tieLow = insertBet(createBet(status = BetStatus.RESOLVED, createdAt = 2_000L))
    val tieHigh = insertBet(createBet(status = BetStatus.CANCELLED, createdAt = 2_000L))
    insertBet(createBet(status = BetStatus.PENDING_CONFIRMATION, createdAt = 3_000L))

    val result =
      betDao.getAllExcludingStatusWithDetails(BetStatus.PENDING_CONFIRMATION).first()

    assertThat(result.map { it.bet.id })
      .containsExactly(tieHigh.id, tieLow.id, oldest.id)
      .inOrder()
  }

  // --- getNewestByStatusWithDetails ---

  @Test
  fun getNewestByStatusWithDetails_returnsNewest_tiesByIdDesc() = runTest {
    insertBet(createBet(status = BetStatus.ACTIVE, createdAt = 1_000L))
    insertBet(createBet(status = BetStatus.ACTIVE, createdAt = 2_000L))
    val tieHigh = insertBet(createBet(status = BetStatus.ACTIVE, createdAt = 2_000L))
    insertBet(createBet(status = BetStatus.RESOLVED, createdAt = 9_000L))

    val result = betDao.getNewestByStatusWithDetails(BetStatus.ACTIVE).first()

    assertThat(result!!.bet.id).isEqualTo(tieHigh.id)
  }

  @Test
  fun getNewestByStatusWithDetails_none_emitsNull() = runTest {
    insertBet(createBet(status = BetStatus.RESOLVED))

    assertThat(betDao.getNewestByStatusWithDetails(BetStatus.ACTIVE).first()).isNull()
  }
}
