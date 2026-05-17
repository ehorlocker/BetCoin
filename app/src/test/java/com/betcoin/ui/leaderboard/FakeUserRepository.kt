package com.betcoin.ui.leaderboard

import com.betcoin.data.database.entity.User
import com.betcoin.data.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

class FakeUserRepository : UserRepository {
    private val _leaderboard = MutableStateFlow<List<User>>(emptyList())

    fun emitLeaderboard(users: List<User>) {
        _leaderboard.value = users.sortedByDescending { it.balance }
    }

    override fun getLeaderboard(): Flow<List<User>> = _leaderboard

    override suspend fun createUser(username: String, pin: String): Long = 0L
    override suspend fun verifyPin(userId: Long, pin: String): Boolean = false
    override suspend fun getUser(userId: Long): User? = null
    override fun getAllUsers(): Flow<List<User>> = flowOf(emptyList())
    override suspend fun bailout(userId: Long) {}
    override suspend fun deleteUser(userId: Long) {}
    override suspend fun resetPin(userId: Long, newPin: String) {}
    override suspend fun updateBalance(userId: Long, delta: Long) {}
    override suspend fun setBalance(userId: Long, newBalance: Long) {}
    override suspend fun updateUsername(userId: Long, newUsername: String) {}
}
