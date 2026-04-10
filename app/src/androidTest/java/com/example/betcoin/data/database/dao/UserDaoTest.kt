package com.example.betcoin.data.database.dao

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.betcoin.data.database.BetCoinDatabase
import com.example.betcoin.data.database.entity.User
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for [UserDao].
 *
 * Uses an in-memory Room database so tests are hermetic and fast.
 */
@RunWith(AndroidJUnit4::class)
class UserDaoTest {

  private lateinit var database: BetCoinDatabase
  private lateinit var userDao: UserDao

  @Before
  fun setUp() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    database = Room.inMemoryDatabaseBuilder(context, BetCoinDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    userDao = database.userDao()
  }

  @After
  fun tearDown() {
    database.close()
  }

  // --- Helper ---

  private fun createUser(
    username: String = "alice",
    pinHash: String = "hashed_pin",
    balance: Long = 1000L,
    createdAt: Long = System.currentTimeMillis(),
  ): User = User(
    username = username,
    pinHash = pinHash,
    balance = balance,
    createdAt = createdAt,
  )

  // --- insert ---

  @Test
  fun insert_returnsPositiveId() = runTest {
    val user = createUser()
    val id = userDao.insert(user)
    assertThat(id).isGreaterThan(0L)
  }

  @Test
  fun insert_secondUser_returnsDistinctId() = runTest {
    val id1 = userDao.insert(createUser(username = "alice"))
    val id2 = userDao.insert(createUser(username = "bob"))
    assertThat(id1).isNotEqualTo(id2)
  }

  // --- getById ---

  @Test
  fun getById_returnsInsertedUser() = runTest {
    val user = createUser(username = "alice")
    val id = userDao.insert(user)

    val retrieved = userDao.getById(id)
    assertThat(retrieved).isNotNull()
    assertThat(retrieved!!.username).isEqualTo("alice")
    assertThat(retrieved.pinHash).isEqualTo("hashed_pin")
    assertThat(retrieved.balance).isEqualTo(1000L)
  }

  @Test
  fun getById_nonExistentId_returnsNull() = runTest {
    val retrieved = userDao.getById(999L)
    assertThat(retrieved).isNull()
  }

  // --- getByUsername ---

  @Test
  fun getByUsername_returnsMatchingUser() = runTest {
    userDao.insert(createUser(username = "alice"))

    val retrieved = userDao.getByUsername("alice")
    assertThat(retrieved).isNotNull()
    assertThat(retrieved!!.username).isEqualTo("alice")
  }

  @Test
  fun getByUsername_nonExistent_returnsNull() = runTest {
    val retrieved = userDao.getByUsername("nobody")
    assertThat(retrieved).isNull()
  }

  // --- getAll ---

  @Test
  fun getAll_emptyDatabase_returnsEmptyList() = runTest {
    val users = userDao.getAll().first()
    assertThat(users).isEmpty()
  }

  @Test
  fun getAll_returnsAllInsertedUsers() = runTest {
    userDao.insert(createUser(username = "alice"))
    userDao.insert(createUser(username = "bob"))
    userDao.insert(createUser(username = "charlie"))

    val users = userDao.getAll().first()
    assertThat(users).hasSize(3)
    assertThat(users.map { it.username }).containsExactly("alice", "bob", "charlie")
  }

  // --- getLeaderboard ---

  @Test
  fun getLeaderboard_returnsSortedByBalanceDescending() = runTest {
    userDao.insert(createUser(username = "poor", balance = 100L))
    userDao.insert(createUser(username = "rich", balance = 5000L))
    userDao.insert(createUser(username = "mid", balance = 1000L))

    val leaderboard = userDao.getLeaderboard().first()
    assertThat(leaderboard).hasSize(3)
    assertThat(leaderboard[0].username).isEqualTo("rich")
    assertThat(leaderboard[1].username).isEqualTo("mid")
    assertThat(leaderboard[2].username).isEqualTo("poor")
  }

  @Test
  fun getLeaderboard_emptyDatabase_returnsEmptyList() = runTest {
    val leaderboard = userDao.getLeaderboard().first()
    assertThat(leaderboard).isEmpty()
  }

  // --- update ---

  @Test
  fun update_changesUserFields() = runTest {
    val id = userDao.insert(createUser(username = "alice", balance = 1000L))
    val user = userDao.getById(id)!!

    userDao.update(user.copy(balance = 2000L, totalWins = 5))

    val updated = userDao.getById(id)!!
    assertThat(updated.balance).isEqualTo(2000L)
    assertThat(updated.totalWins).isEqualTo(5)
    assertThat(updated.username).isEqualTo("alice")
  }

  // --- delete ---

  @Test
  fun delete_removesUser() = runTest {
    val id = userDao.insert(createUser(username = "alice"))
    val user = userDao.getById(id)!!

    userDao.delete(user)

    val retrieved = userDao.getById(id)
    assertThat(retrieved).isNull()
  }

  @Test
  fun delete_onlyRemovesTargetUser() = runTest {
    val id1 = userDao.insert(createUser(username = "alice"))
    val id2 = userDao.insert(createUser(username = "bob"))

    userDao.delete(userDao.getById(id1)!!)

    assertThat(userDao.getById(id1)).isNull()
    assertThat(userDao.getById(id2)).isNotNull()
  }

  // --- updateBalance ---

  @Test
  fun updateBalance_setsNewBalance() = runTest {
    val id = userDao.insert(createUser(username = "alice", balance = 1000L))

    userDao.updateBalance(id, 2500L)

    val updated = userDao.getById(id)!!
    assertThat(updated.balance).isEqualTo(2500L)
  }

  @Test
  fun updateBalance_allowsNegativeBalance() = runTest {
    val id = userDao.insert(createUser(username = "alice", balance = 1000L))

    userDao.updateBalance(id, -500L)

    val updated = userDao.getById(id)!!
    assertThat(updated.balance).isEqualTo(-500L)
  }

  // --- Default values preserved ---

  @Test
  fun insert_preservesDefaultValues() = runTest {
    val id = userDao.insert(createUser())
    val user = userDao.getById(id)!!

    assertThat(user.balance).isEqualTo(1000L)
    assertThat(user.totalWins).isEqualTo(0)
    assertThat(user.totalLosses).isEqualTo(0)
    assertThat(user.totalEarnings).isEqualTo(0L)
    assertThat(user.totalLost).isEqualTo(0L)
    assertThat(user.bailoutCount).isEqualTo(0)
    assertThat(user.totalDebt).isEqualTo(0L)
  }
}
