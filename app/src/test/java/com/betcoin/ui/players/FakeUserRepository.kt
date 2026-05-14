package com.betcoin.ui.players

import com.betcoin.data.database.entity.User
import com.betcoin.data.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Fake implementation of [UserRepository] for testing the players screen.
 */
class FakeUserRepository : UserRepository {
    var users: List<User> = emptyList()
    var shouldThrowOnCreate: Throwable? = null
    var shouldThrowOnDelete: Throwable? = null
    var shouldThrowOnBailout: Throwable? = null
    var shouldThrowOnRename: Throwable? = null
    var nextCreatedUserId: Long = 1L

    override fun getAllUsers(): Flow<List<User>> = flowOf(users)
    override fun getLeaderboard(): Flow<List<User>> = flowOf(users)

    override suspend fun createUser(username: String, pin: String): Long {
        shouldThrowOnCreate?.let { throw it }
        return nextCreatedUserId
    }

    override suspend fun verifyPin(userId: Long, pin: String): Boolean = false
    override suspend fun getUser(userId: Long): User? = users.find { it.id == userId }

    override suspend fun bailout(userId: Long) {
        shouldThrowOnBailout?.let { throw it }
    }

    override suspend fun deleteUser(userId: Long) {
        shouldThrowOnDelete?.let { throw it }
    }

    override suspend fun resetPin(userId: Long, newPin: String) {}
    override suspend fun updateBalance(userId: Long, delta: Long) {}
    override suspend fun setBalance(userId: Long, newBalance: Long) {}

    override suspend fun updateUsername(userId: Long, newUsername: String) {
        shouldThrowOnRename?.let { throw it }
    }
}
