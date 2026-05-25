package com.betcoin.ui.players

import androidx.lifecycle.SavedStateHandle
import com.betcoin.data.database.entity.User
import com.betcoin.data.repository.UserRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepository: FakeUserRepository

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeUserRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun userFound_emitsSuccess() = runTest(testDispatcher) {
        val user = user(id = 42L, username = "Alice", balance = 5000)
        fakeRepository.addUser(user)

        val savedStateHandle = SavedStateHandle(mapOf("userId" to 42L))
        val viewModel = PlayerDetailViewModel(savedStateHandle, fakeRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.uiState.value).isEqualTo(PlayerDetailUiState.Success(user))
    }

    @Test
    fun userNotFound_emitsNotFound() = runTest(testDispatcher) {
        val savedStateHandle = SavedStateHandle(mapOf("userId" to 99L))
        val viewModel = PlayerDetailViewModel(savedStateHandle, fakeRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.uiState.value).isEqualTo(PlayerDetailUiState.NotFound)
    }

    @Test
    fun initialState_isLoading() = runTest(testDispatcher) {
        val savedStateHandle = SavedStateHandle(mapOf("userId" to 1L))
        val viewModel = PlayerDetailViewModel(savedStateHandle, fakeRepository)

        assertThat(viewModel.uiState.value).isEqualTo(PlayerDetailUiState.Loading)
    }

    private fun user(
        id: Long,
        username: String,
        balance: Long = 0,
    ): User = User(
        id = id,
        username = username,
        pinHash = "",
        balance = balance,
        totalWins = 0,
        totalLosses = 0,
        totalEarnings = 0,
        totalLost = 0,
        bailoutCount = 0,
        totalDebt = 0,
        createdAt = 0L,
    )

    class FakeUserRepository : UserRepository {
        private val _users = mutableMapOf<Long, User>()

        fun addUser(user: User) {
            _users[user.id] = user
        }

        override suspend fun getUser(userId: Long): User? = _users[userId]

        override fun getLeaderboard(): Flow<List<User>> = flowOf(emptyList())
        override fun getAllUsers(): Flow<List<User>> = flowOf(emptyList())
        override suspend fun createUser(username: String, pin: String): Long = 0L
        override suspend fun verifyPin(userId: Long, pin: String): Boolean = false
        override suspend fun bailout(userId: Long) {}
        override suspend fun deleteUser(userId: Long) {}
        override suspend fun resetPin(userId: Long, newPin: String) {}
        override suspend fun updateBalance(userId: Long, delta: Long) {}
        override suspend fun setBalance(userId: Long, newBalance: Long) {}
        override suspend fun updateUsername(userId: Long, newUsername: String) {}
    }
}
