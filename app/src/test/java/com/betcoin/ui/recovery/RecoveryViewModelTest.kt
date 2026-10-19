package com.betcoin.ui.recovery

import com.betcoin.data.model.BetStatus
import com.betcoin.data.model.InvalidBetStateException
import com.betcoin.testing.FakePendingBetRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/** Tests for [RecoveryViewModel]. */
@OptIn(ExperimentalCoroutinesApi::class)
class RecoveryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: FakePendingBetRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = FakePendingBetRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun pendingBet_emitsPendingWithPromptAndParticipantCount() = runTest {
        repository.pendingBets.value = listOf(repository.pendingBet(7, "Who wins Smash?", 3))
        val viewModel = RecoveryViewModel(repository)
        backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }
        advanceUntilIdle()

        assertThat(viewModel.uiState.value)
            .isEqualTo(RecoveryUiState.Pending(betId = 7, prompt = "Who wins Smash?", participantCount = 3))
    }

    @Test
    fun noPendingBet_emitsGoHome() = runTest {
        val viewModel = RecoveryViewModel(repository)
        backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }
        advanceUntilIdle()

        assertThat(viewModel.uiState.value).isEqualTo(RecoveryUiState.GoHome)
    }

    @Test
    fun pendingBetDisappears_navigatesHome() = runTest {
        repository.pendingBets.value = listOf(repository.pendingBet(7, "Who wins Smash?", 3))
        val viewModel = RecoveryViewModel(repository)
        backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }
        advanceUntilIdle()
        assertThat(viewModel.uiState.value).isInstanceOf(RecoveryUiState.Pending::class.java)

        repository.pendingBets.value = emptyList()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value).isEqualTo(RecoveryUiState.GoHome)
    }

    @Test
    fun discard_callsDiscardPendingBet_thenGoHome() = runTest {
        repository.pendingBets.value = listOf(repository.pendingBet(7, "Who wins Smash?", 3))
        val viewModel = RecoveryViewModel(repository)
        backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.discard()
        advanceUntilIdle()

        assertThat(repository.discardedBetIds).containsExactly(7L)
        assertThat(viewModel.uiState.value).isEqualTo(RecoveryUiState.GoHome)
    }

    @Test
    fun discard_twice_callsRepositoryOnce() = runTest {
        repository.pendingBets.value = listOf(repository.pendingBet(7, "Who wins Smash?", 3))
        val viewModel = RecoveryViewModel(repository)
        backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.discard()
        viewModel.discard()
        advanceUntilIdle()

        assertThat(repository.discardedBetIds).containsExactly(7L)
        assertThat(viewModel.uiState.value).isEqualTo(RecoveryUiState.GoHome)
    }

    @Test
    fun discard_whenRepositoryThrows_navigatesHome() = runTest {
        repository.pendingBets.value = listOf(repository.pendingBet(7, "Who wins Smash?", 3))
        repository.discardError = InvalidBetStateException(7, BetStatus.ACTIVE)
        val viewModel = RecoveryViewModel(repository)
        backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.discard()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value).isEqualTo(RecoveryUiState.GoHome)
    }

    @Test
    fun discard_onIoFailure_staysPendingAndCanRetry() = runTest {
        repository.pendingBets.value = listOf(repository.pendingBet(7, "Who wins Smash?", 3))
        repository.discardError = java.io.IOException("disk")
        val viewModel = RecoveryViewModel(repository)
        backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.discard()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value)
            .isEqualTo(RecoveryUiState.Pending(betId = 7, prompt = "Who wins Smash?", participantCount = 3, isDiscarding = false))

        repository.discardError = null
        viewModel.discard()
        advanceUntilIdle()

        assertThat(repository.discardedBetIds).containsExactly(7L)
        assertThat(viewModel.uiState.value).isEqualTo(RecoveryUiState.GoHome)
    }

    @Test
    fun staysGoHomeWhenNewPendingBetAppears() = runTest {
        val viewModel = RecoveryViewModel(repository)
        backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }
        advanceUntilIdle()
        assertThat(viewModel.uiState.value).isEqualTo(RecoveryUiState.GoHome)

        repository.pendingBets.value = listOf(repository.pendingBet(9, "Late bet", 2))
        advanceUntilIdle()

        assertThat(viewModel.uiState.value).isEqualTo(RecoveryUiState.GoHome)
    }
}
