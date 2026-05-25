package com.betcoin.ui.leaderboard

import com.betcoin.data.database.entity.User
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LeaderboardViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepository: FakeUserRepository
    private lateinit var viewModel: LeaderboardViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeUserRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_isLoading() = runTest {
        viewModel = LeaderboardViewModel(fakeRepository)
        assertThat(viewModel.uiState.value).isInstanceOf(LeaderboardUiState.Loading::class.java)
    }

    @Test
    fun usersEmitted_computesRanksCorrectly() = runTest {
        fakeRepository.emitLeaderboard(
            listOf(
                user(balance = 8000),
                user(balance = 5000),
                user(balance = 3000),
            )
        )
        viewModel = LeaderboardViewModel(fakeRepository)
        advanceUntilIdle()

        val state = viewModel.uiState.value as LeaderboardUiState.Success
        assertThat(state.items).hasSize(3)
        assertThat(state.items[0].rank).isEqualTo(1)
        assertThat(state.items[0].balance).isEqualTo(8000)
        assertThat(state.items[1].rank).isEqualTo(2)
        assertThat(state.items[1].balance).isEqualTo(5000)
        assertThat(state.items[2].rank).isEqualTo(3)
        assertThat(state.items[2].balance).isEqualTo(3000)
    }

    @Test
    fun emptyList_emitsEmpty() = runTest {
        fakeRepository.emitLeaderboard(emptyList())
        viewModel = LeaderboardViewModel(fakeRepository)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value).isEqualTo(LeaderboardUiState.Empty)
    }

    private fun user(balance: Long): User = User(
        id = 0,
        username = "User",
        pinHash = "hash",
        balance = balance,
        createdAt = 0,
    )
}
