package com.betcoin.data.repository.local

import com.betcoin.data.database.entity.User
import com.betcoin.data.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory implementation of [UserRepository].
 *
 * Uses [MutableStateFlow] so that collectors automatically receive updates
 * when data changes, mirroring how Room DAOs behave in production.
 */
@Singleton
class LocalUserRepository @Inject constructor() : UserRepository {
    private val _users = MutableStateFlow<List<User>>(emptyList())
    private var nextId = 1L

    override fun getAllUsers(): Flow<List<User>> = _users
    override fun getLeaderboard(): Flow<List<User>> = _users.map { list ->
        list.sortedByDescending { it.totalEarnings }
    }

    override suspend fun createUser(username: String, pin: String): Long {
        val user = User(
            id = nextId++,
            username = username,
            pinHash = pin,
            createdAt = System.currentTimeMillis(),
        )
        _users.value = _users.value + user
        return user.id
    }

    override suspend fun getUser(userId: Long): User? =
        _users.value.find { it.id == userId }

    override suspend fun verifyPin(userId: Long, pin: String): Boolean = false

    override suspend fun deleteUser(userId: Long) {
        _users.value = _users.value.filterNot { it.id == userId }
    }

    override suspend fun updateUsername(userId: Long, newUsername: String) {
        _users.value = _users.value.map { user ->
            if (user.id == userId) user.copy(username = newUsername) else user
        }
    }

    override suspend fun bailout(userId: Long) {
        _users.value = _users.value.map { user ->
            if (user.id == userId) {
                user.copy(
                    balance = user.balance + 1000,
                    bailoutCount = user.bailoutCount + 1,
                    totalDebt = user.totalDebt + 1000,
                )
            } else user
        }
    }

    override suspend fun updateBalance(userId: Long, delta: Long) {
        _users.value = _users.value.map { user ->
            if (user.id == userId) user.copy(balance = user.balance + delta) else user
        }
    }

    override suspend fun setBalance(userId: Long, newBalance: Long) {
        _users.value = _users.value.map { user ->
            if (user.id == userId) user.copy(balance = newBalance) else user
        }
    }

    override suspend fun resetPin(userId: Long, newPin: String) {}
}
