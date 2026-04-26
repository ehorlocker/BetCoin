package com.betcoin.data.repository

import com.betcoin.data.database.entity.User
import kotlinx.coroutines.flow.Flow

/**
 * Contract for user management operations.
 *
 * Implementations handle player creation, PIN verification, bailouts,
 * deletion, and balance management.
 */
interface UserRepository {

    /**
     * Creates a new user with the given [username] and [pin].
     *
     * The implementation hashes the PIN before storage. The new user
     * receives a starting balance of 1000.
     *
     * @return the new user's row ID.
     */
    suspend fun createUser(username: String, pin: String): Long

    /**
     * Verifies [pin] against the stored hash for [userId].
     *
     * @return `true` if the PIN matches.
     */
    suspend fun verifyPin(userId: Long, pin: String): Boolean

    /**
     * Retrieves a single user by [userId].
     *
     * @return the [User], or `null` if not found.
     */
    suspend fun getUser(userId: Long): User?

    /**
     * Observes all users.
     */
    fun getAllUsers(): Flow<List<User>>

    /**
     * Observes all users sorted by balance descending.
     */
    fun getLeaderboard(): Flow<List<User>>

    /**
     * Grants the user a bailout: adds 1000 to balance, increments
     * [User.bailoutCount], and adds 1000 to [User.totalDebt].
     */
    suspend fun bailout(userId: Long)

    /**
     * Deletes a user.
     *
     * Refuses to delete if the user is a participant in any ACTIVE bet. Before
     * deletion, snapshots the username into
     * [BetParticipant.deletedUsername][com.betcoin.data.database.entity.BetParticipant.deletedUsername]
     * and nulls the foreign key on all related participants.
     */
    suspend fun deleteUser(userId: Long)

    /**
     * Resets the user's PIN to [newPin] (admin operation — no old PIN required).
     * The implementation hashes the new PIN before storage.
     */
    suspend fun resetPin(userId: Long, newPin: String)

    /**
     * Adjusts the user's balance by signed delta [delta].
     */
    suspend fun updateBalance(userId: Long, delta: Long)

    /**
     * Sets the user's balance to the exact [newBalance] value (admin operation).
     */
    suspend fun setBalance(userId: Long, newBalance: Long)

    /**
     * Renames the user (admin operation). [newUsername] must be unique.
     */
    suspend fun updateUsername(userId: Long, newUsername: String)
}
