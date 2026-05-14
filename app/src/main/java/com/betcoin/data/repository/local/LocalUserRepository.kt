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
    override suspend fun createUser(username: String, pin: String): Long = TODO()
    override suspend fun verifyPin(userId: Long, pin: String): Boolean = TODO()
    override suspend fun getUser(userId: Long): User? = TODO()
    override fun getAllUsers(): Flow<List<User>> = flowOf(emptyList())
    override fun getLeaderboard(): Flow<List<User>> = flowOf(emptyList())
    override suspend fun bailout(userId: Long): Unit = TODO()
    override suspend fun deleteUser(userId: Long): Unit = TODO()
    override suspend fun resetPin(userId: Long, newPin: String): Unit = TODO()
    override suspend fun updateBalance(userId: Long, delta: Long): Unit = TODO()
    override suspend fun setBalance(userId: Long, newBalance: Long): Unit = TODO()
    override suspend fun updateUsername(userId: Long, newUsername: String): Unit = TODO()
}
