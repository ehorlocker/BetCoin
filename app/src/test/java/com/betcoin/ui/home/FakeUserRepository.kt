package com.betcoin.ui.home

import com.betcoin.data.database.entity.User
import com.betcoin.data.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Fake implementation of [UserRepository] for testing.
 */
class FakeUserRepository : UserRepository {
    var userCount: Int = 0

    override fun getAllUsers(): Flow<List<User>> {
        return flowOf(List(userCount) { index ->
            User(
                id = index.toLong(),
                username = "User$index",
                pinHash = "hash",
                balance = 1000,
                totalWins = 0,
                totalLosses = 0,
                totalEarnings = 0,
                totalLost = 0,
                totalDebt = 0,
                bailoutCount = 0,
                createdAt = System.currentTimeMillis()
            )
        })
    }

    override fun getLeaderboard(): Flow<List<User>> = flowOf(emptyList())
    override suspend fun createUser(username: String, pin: String): Long = 0L
    override suspend fun verifyPin(userId: Long, pin: String): Boolean = false
    override suspend fun getUser(userId: Long): User? = null
    override suspend fun bailout(userId: Long) {}
    override suspend fun deleteUser(userId: Long) {}
    override suspend fun resetPin(userId: Long, newPin: String) {}
    override suspend fun updateBalance(userId: Long, delta: Long) {}
    override suspend fun setBalance(userId: Long, newBalance: Long) {}
    override suspend fun updateUsername(userId: Long, newUsername: String) {}
}
