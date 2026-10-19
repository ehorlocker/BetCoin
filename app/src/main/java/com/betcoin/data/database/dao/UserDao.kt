package com.betcoin.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.betcoin.data.database.entity.User
import com.betcoin.data.model.BetStatus
import kotlinx.coroutines.flow.Flow

/** Data access object for [User] operations. */
@Dao
interface UserDao {

  /** Inserts a new user and returns the auto-generated row ID. */
  @Insert
  suspend fun insert(user: User): Long

  /** Returns the user with the given [userId], or null if not found. */
  @Query("SELECT * FROM users WHERE id = :userId")
  suspend fun getById(userId: Long): User?

  /** Returns the user with the given [username] (case-insensitive), or null if not found. */
  @Query("SELECT * FROM users WHERE username = :username")
  suspend fun getByUsername(username: String): User?

  /** Returns a [Flow] of all users. */
  @Query("SELECT * FROM users")
  fun getAll(): Flow<List<User>>

  /**
   * Returns a [Flow] of all users ranked for the leaderboard: net worth (`balance - total_debt`)
   * descending, then balance descending, then username A–Z ignoring case.
   */
  @Query(
    """
    SELECT * FROM users
    ORDER BY (balance - total_debt) DESC, balance DESC, username COLLATE NOCASE ASC
    """
  )
  fun getLeaderboard(): Flow<List<User>>

  /** Updates an existing user (matched by primary key). */
  @Update
  suspend fun update(user: User)

  /** Deletes the given user (matched by primary key). */
  @Delete
  suspend fun delete(user: User)

  /** Sets the balance for the user with the given [userId] to [newBalance]. */
  @Query("UPDATE users SET balance = :newBalance WHERE id = :userId")
  suspend fun updateBalance(userId: Long, newBalance: Long)

  /**
   * Sets the avatar reference of the user with [userId] (null clears it).
   *
   * @return the number of rows updated (0 if the user doesn't exist).
   */
  @Query("UPDATE users SET avatar_ref = :avatarRef WHERE id = :userId")
  suspend fun updateAvatarRef(userId: Long, avatarRef: String?): Int

  /**
   * Adds signed [delta] to the balance of the user with [userId].
   *
   * @return the number of rows updated (0 if the user doesn't exist).
   */
  @Query("UPDATE users SET balance = balance + :delta WHERE id = :userId")
  suspend fun addToBalance(userId: Long, delta: Long): Int

  /**
   * Grants a bailout of [amount]: adds it to balance and total debt and increments the bailout
   * count.
   *
   * @return the number of rows updated (0 if the user doesn't exist).
   */
  @Query(
    """
    UPDATE users
    SET balance = balance + :amount, total_debt = total_debt + :amount,
        bailout_count = bailout_count + 1
    WHERE id = :userId
    """
  )
  suspend fun applyBailout(userId: Long, amount: Long): Int

  /**
   * Returns a [Flow] of the sum of the user's wagers in bets with [status] (ACTIVE by default),
   * or 0 if there are none.
   */
  @Query(
    """
    SELECT COALESCE(SUM(p.wager_amount), 0) FROM bet_participants p
    INNER JOIN bets b ON b.id = p.bet_id
    WHERE p.user_id = :userId AND b.status = :status
    """
  )
  fun observeLockedBalance(userId: Long, status: BetStatus = BetStatus.ACTIVE): Flow<Long>

  /** One-shot version of [observeLockedBalance], for checks inside a transaction. */
  @Query(
    """
    SELECT COALESCE(SUM(p.wager_amount), 0) FROM bet_participants p
    INNER JOIN bets b ON b.id = p.bet_id
    WHERE p.user_id = :userId AND b.status = :status
    """
  )
  suspend fun getLockedBalance(userId: Long, status: BetStatus = BetStatus.ACTIVE): Long

  /** Returns how many bets with [status] (ACTIVE by default) the user is a participant in. */
  @Query(
    """
    SELECT COUNT(DISTINCT p.bet_id) FROM bet_participants p
    INNER JOIN bets b ON b.id = p.bet_id
    WHERE p.user_id = :userId AND b.status = :status
    """
  )
  suspend fun countBetsWithStatus(userId: Long, status: BetStatus = BetStatus.ACTIVE): Int
}
