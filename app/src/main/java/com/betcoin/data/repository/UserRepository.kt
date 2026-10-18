package com.betcoin.data.repository

import com.betcoin.data.database.entity.User
import kotlinx.coroutines.flow.Flow

/**
 * Contract for user management operations.
 *
 * Implementations handle player creation, PIN verification, bailouts,
 * deletion, and balance management. Rule violations throw a
 * [BetCoinException][com.betcoin.data.model.BetCoinException] subclass and
 * leave the stored data unchanged.
 */
interface UserRepository {

    /**
     * Creates a new user with the given [username] and [pin].
     *
     * The username is trimmed and must be 1–20 characters and unique ignoring
     * case. The PIN must be exactly 4 digits. The implementation hashes the PIN
     * before storage. The new user receives a starting balance of 1000.
     *
     * @return the new user's row ID.
     * @throws com.betcoin.data.model.InvalidInputException if the username or PIN is invalid.
     * @throws com.betcoin.data.model.DuplicateUsernameException if the username is taken.
     */
    suspend fun createUser(username: String, pin: String): Long

    /**
     * Verifies [pin] against the stored hash for [userId].
     *
     * @return `true` if the PIN matches, `false` otherwise or if the user doesn't exist.
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
     * Observes all users ranked by net worth (`balance − totalDebt`)
     * descending, then balance descending, then username A–Z.
     */
    fun getLeaderboard(): Flow<List<User>>

    /**
     * Observes the sum of [userId]'s wagers in ACTIVE bets (0 if none).
     */
    fun getLockedBalance(userId: Long): Flow<Long>

    /**
     * Grants the user a bailout: adds 1000 to balance, increments
     * [User.bailoutCount], and adds 1000 to [User.totalDebt].
     *
     * @throws com.betcoin.data.model.NotEligibleForBailoutException unless
     *   balance plus [getLockedBalance] is below 100.
     */
    suspend fun bailout(userId: Long)

    /**
     * Admin bailout: same effect as [bailout] without the eligibility check.
     */
    suspend fun forceBailout(userId: Long)

    /**
     * Deletes a user.
     *
     * Refuses to delete if the user is a participant in any ACTIVE bet. Before
     * deletion, snapshots the username into
     * [BetParticipant.deletedUsername][com.betcoin.data.database.entity.BetParticipant.deletedUsername]
     * and nulls the foreign key on all related participants, all in one transaction.
     *
     * @throws com.betcoin.data.model.ActiveBetConflictException if the user is in an ACTIVE bet.
     */
    suspend fun deleteUser(userId: Long)

    /**
     * Resets the user's PIN to [newPin] (admin operation — no old PIN required).
     * The PIN must be exactly 4 digits. The implementation hashes it before storage.
     */
    suspend fun resetPin(userId: Long, newPin: String)

    /**
     * Adjusts the user's balance by signed delta [delta].
     */
    suspend fun updateBalance(userId: Long, delta: Long)

    /**
     * Sets the user's balance to the exact [newBalance] value (admin operation).
     * Negative values are allowed.
     */
    suspend fun setBalance(userId: Long, newBalance: Long)

    /**
     * Renames the user (admin operation). [newUsername] is trimmed, must be
     * 1–20 characters and must stay unique ignoring case.
     *
     * @throws com.betcoin.data.model.DuplicateUsernameException if another user has the name.
     */
    suspend fun updateUsername(userId: Long, newUsername: String)
}
