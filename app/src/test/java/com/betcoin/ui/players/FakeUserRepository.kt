package com.betcoin.ui.players

import com.betcoin.data.database.entity.User
import com.betcoin.data.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Fake implementation of [UserRepository] for testing the players screen.
 *
 * Uses [MutableStateFlow] so that ViewModel collectors receive updates
 * when data changes, matching production behavior.
 */
class FakeUserRepository : UserRepository {
    private val _users = MutableStateFlow<List<User>>(emptyList())
    var shouldThrowOnCreate: Throwable? = null
    var shouldThrowOnDelete: Throwable? = null
    var shouldThrowOnBailout: Throwable? = null
    var shouldThrowOnRename: Throwable? = null
    var nextCreatedUserId: Long = 1L

    var users: List<User>
        get() = _users.value
        set(value) { _users.value = value }

    override fun getAllUsers(): Flow<List<User>> = _users
    override fun getLeaderboard(): Flow<List<User>> = _users

    override suspend fun createUser(username: String, pin: String): Long {
        shouldThrowOnCreate?.let { throw it }
        val id = nextCreatedUserId
        _users.value = _users.value + User(
            id = id,
            username = username,
            pinHash = "hash",
            balance = 1000,
            totalWins = 0,
            totalLosses = 0,
            totalEarnings = 0,
            totalLost = 0,
            totalDebt = 0,
            bailoutCount = 0,
            createdAt = System.currentTimeMillis(),
        )
        return id
    }

    override suspend fun verifyPin(userId: Long, pin: String): Boolean = false
    override suspend fun getUser(userId: Long): User? = _users.value.find { it.id == userId }

    override suspend fun bailout(userId: Long) {
        shouldThrowOnBailout?.let { throw it }
    }

    override suspend fun deleteUser(userId: Long) {
        shouldThrowOnDelete?.let { throw it }
        _users.value = _users.value.filterNot { it.id == userId }
    }

    override suspend fun resetPin(userId: Long, newPin: String) {}
    override suspend fun updateBalance(userId: Long, delta: Long) {}
    override suspend fun setBalance(userId: Long, newBalance: Long) {}

    override suspend fun updateUsername(userId: Long, newUsername: String) {
        shouldThrowOnRename?.let { throw it }
        _users.value = _users.value.map { user ->
            if (user.id == userId) user.copy(username = newUsername) else user
        }
    }
}
