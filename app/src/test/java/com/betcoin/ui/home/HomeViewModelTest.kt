package com.betcoin.ui.home

import com.betcoin.data.repository.BetRepository
import com.betcoin.data.repository.UserRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Tests for [HomeViewModel].
 *
 * Verifies active bet count and user count state emission.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeBetRepository: FakeBetRepository
    private lateinit var fakeUserRepository: FakeUserRepository
    private lateinit var viewModel: HomeViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeBetRepository = FakeBetRepository()
        fakeUserRepository = FakeUserRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun activeBetCount_emitsCorrectValue() = runTest {
        fakeBetRepository.activeBetCount = 3
        viewModel = HomeViewModel(fakeBetRepository, fakeUserRepository)
        advanceUntilIdle()

        assertThat(viewModel.activeBetCount.value).isEqualTo(3)
    }

    @Test
    fun userCount_emitsCorrectValue() = runTest {
        fakeUserRepository.userCount = 5
        viewModel = HomeViewModel(fakeBetRepository, fakeUserRepository)
        advanceUntilIdle()

        assertThat(viewModel.userCount.value).isEqualTo(5)
    }

    @Test
    fun activeBetCount_zero_emitsZero() = runTest {
        fakeBetRepository.activeBetCount = 0
        viewModel = HomeViewModel(fakeBetRepository, fakeUserRepository)
        advanceUntilIdle()

        assertThat(viewModel.activeBetCount.value).isEqualTo(0)
    }
}
