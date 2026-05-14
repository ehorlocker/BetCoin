package com.betcoin.data.repository.local

import com.betcoin.data.database.entity.User
import com.betcoin.data.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

/**
 * Room-backed implementation of [UserRepository].
 *
 * Methods will be implemented in a later ticket (KAN-12).
 */
class LocalUserRepository @Inject constructor() : UserRepository {
    private val users = mutableListOf<User>()
    private var nextId = 1L

    override suspend fun createUser(username: String, pin: String): Long {
        val user = User(
            id = nextId++,
            username = username,
            pinHash = pin,
            createdAt = System.currentTimeMillis(),
        )
        users.add(user)
        return user.id
    }
    override suspend fun verifyPin(userId: Long, pin: String): Boolean = false
    override suspend fun getUser(userId: Long): User? = users.find { it.id == userId }
    override fun getAllUsers(): Flow<List<User>> = flowOf(users.toList())
    override fun getLeaderboard(): Flow<List<User>> = flowOf(users.sortedByDescending { it.balance })
    override suspend fun bailout(userId: Long) {
        val index = users.indexOfFirst { it.id == userId }
        if (index != -1) {
            val user = users[index]
            users[index] = user.copy(
                balance = user.balance + 1000,
                bailoutCount = user.bailoutCount + 1,
                totalDebt = user.totalDebt + 1000,
            )
        }
    }
    override suspend fun deleteUser(userId: Long) {
        users.removeAll { it.id == userId }
    }
    override suspend fun resetPin(userId: Long, newPin: String) {}
    override suspend fun updateBalance(userId: Long, delta: Long) {
        val index = users.indexOfFirst { it.id == userId }
        if (index != -1) {
            val user = users[index]
            users[index] = user.copy(balance = user.balance + delta)
        }
    }
    override suspend fun setBalance(userId: Long, newBalance: Long) {
        val index = users.indexOfFirst { it.id == userId }
        if (index != -1) {
            val user = users[index]
            users[index] = user.copy(balance = newBalance)
        }
    }
    override suspend fun updateUsername(userId: Long, newUsername: String) {
        val index = users.indexOfFirst { it.id == userId }
        if (index != -1) {
            val user = users[index]
            users[index] = user.copy(username = newUsername)
        }
    }
}
