package com.example.betcoin.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.betcoin.data.database.entity.User
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

  /** Returns the user with the given [username], or null if not found. */
  @Query("SELECT * FROM users WHERE username = :username")
  suspend fun getByUsername(username: String): User?

  /** Returns a [Flow] of all users. */
  @Query("SELECT * FROM users")
  fun getAll(): Flow<List<User>>

  /** Returns a [Flow] of all users sorted by balance descending (leaderboard). */
  @Query("SELECT * FROM users ORDER BY balance DESC")
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
}
