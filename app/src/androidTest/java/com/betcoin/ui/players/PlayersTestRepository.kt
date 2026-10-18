package com.betcoin.ui.players

import com.betcoin.data.database.entity.User
import com.betcoin.data.model.DuplicateUsernameException
import com.betcoin.data.model.NotEligibleForBailoutException
import com.betcoin.data.repository.UserRepository
import com.betcoin.data.security.PinHasher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Minimal in-memory [UserRepository] for the Manage Players and Player Detail UI tests.
 *
 * PINs are bcrypt-hashed with [PinHasher], like the real repository, so no plain-text PIN is kept.
 * Admin operations these screens never call throw.
 */
class PlayersTestRepository : UserRepository {
    val users = MutableStateFlow<List<User>>(emptyList())
    val lockedBalances = MutableStateFlow<Map<Long, Long>>(emptyMap())

    /** Adds a player with [pin] and [balance] and returns their ID. */
    fun addPlayer(username: String, pin: String = "1234", balance: Long = 1000L, totalDebt: Long = 0L): Long {
        val id = (users.value.maxOfOrNull { it.id } ?: 0L) + 1
        users.value += User(
            id = id,
            username = username,
            pinHash = PinHasher.hashBlocking(pin),
            balance = balance,
            totalDebt = totalDebt,
            createdAt = 0L,
        )
        return id
    }

    override suspend fun createUser(username: String, pin: String): Long {
        val name = username.trim()
        if (users.value.any { it.username.equals(name, ignoreCase = true) }) {
            throw DuplicateUsernameException(name)
        }
        return addPlayer(name, pin)
    }

    override suspend fun verifyPin(userId: Long, pin: String): Boolean {
        val user = users.value.find { it.id == userId } ?: return false
        return PinHasher.verifyBlocking(pin, user.pinHash)
    }

    override suspend fun getUser(userId: Long): User? = users.value.find { it.id == userId }

    override fun getAllUsers(): Flow<List<User>> = users

    override fun getLeaderboard(): Flow<List<User>> = users

    override fun getLockedBalance(userId: Long): Flow<Long> = lockedBalances.map { it[userId] ?: 0L }

    override suspend fun bailout(userId: Long) {
        val user = users.value.first { it.id == userId }
        if (user.balance + (lockedBalances.value[userId] ?: 0L) >= 100) {
            throw NotEligibleForBailoutException(userId)
        }
        forceBailout(userId)
    }

    override suspend fun forceBailout(userId: Long) {
        users.value = users.value.map {
            if (it.id == userId) {
                it.copy(balance = it.balance + 1000, bailoutCount = it.bailoutCount + 1, totalDebt = it.totalDebt + 1000)
            } else {
                it
            }
        }
    }

    override suspend fun deleteUser(userId: Long) = unsupported()
    override suspend fun resetPin(userId: Long, newPin: String) = unsupported()
    override suspend fun updateBalance(userId: Long, delta: Long) = unsupported()
    override suspend fun setBalance(userId: Long, newBalance: Long) = unsupported()
    override suspend fun updateUsername(userId: Long, newUsername: String) = unsupported()

    private fun unsupported(): Nothing = throw UnsupportedOperationException("Not used by these screens")
}
