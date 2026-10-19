package com.betcoin.navigation

import android.database.sqlite.SQLiteException
import androidx.lifecycle.SavedStateHandle
import com.betcoin.testing.FakePendingBetRepository
import com.betcoin.testing.RecordingErrorLogger
import com.betcoin.ui.onboarding.FakeSettingsRepository
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
import kotlin.coroutines.cancellation.CancellationException

/** Tests for [LaunchViewModel]. */
@OptIn(ExperimentalCoroutinesApi::class)
class LaunchViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var settings: FakeSettingsRepository
    private lateinit var bets: FakePendingBetRepository
    private lateinit var logger: RecordingErrorLogger

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        settings = FakeSettingsRepository()
        bets = FakePendingBetRepository()
        logger = RecordingErrorLogger()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(handle: SavedStateHandle = SavedStateHandle()) =
        LaunchViewModel(StartDestinationResolver(settings, bets, logger), handle, logger)

    @Test
    fun isFirstLaunchThrows_emitsError_andLogs() = runTest {
        val error = SQLiteException("disk I/O error")
        settings.isFirstLaunchError = error
        val vm = viewModel()
        advanceUntilIdle()

        assertThat(vm.uiState.value).isEqualTo(LaunchUiState.Error)
        assertThat(logger.calls).hasSize(1)
        assertThat(logger.calls.single().tag).isEqualTo("BetCoinLaunch")
        assertThat(logger.calls.single().throwable).isSameInstanceAs(error)
    }

    @Test
    fun isFirstLaunchThrowsIllegalState_emitsError() = runTest {
        settings.isFirstLaunchError = IllegalStateException("Migration didn't properly handle")
        val vm = viewModel()
        advanceUntilIdle()

        assertThat(vm.uiState.value).isEqualTo(LaunchUiState.Error)
    }

    @Test
    fun getPendingBetThrows_emitsError_andLogs() = runTest {
        settings.isFirstLaunchResult = false
        val error = SQLiteException()
        bets.getPendingBetError = error
        val vm = viewModel()
        advanceUntilIdle()

        assertThat(vm.uiState.value).isEqualTo(LaunchUiState.Error)
        assertThat(logger.calls).hasSize(1)
        assertThat(logger.calls.single().throwable).isSameInstanceAs(error)
    }

    @Test
    fun firstLaunch_emitsReadyOnboarding() = runTest {
        settings.isFirstLaunchResult = true
        val vm = viewModel()
        advanceUntilIdle()

        assertThat(vm.uiState.value).isEqualTo(LaunchUiState.Ready(Routes.ONBOARDING))
        assertThat(logger.calls).isEmpty()
    }

    @Test
    fun noPendingBet_emitsReadyHome() = runTest {
        settings.isFirstLaunchResult = false
        val vm = viewModel()
        advanceUntilIdle()

        assertThat(vm.uiState.value).isEqualTo(LaunchUiState.Ready(Routes.HOME))
        assertThat(logger.calls).isEmpty()
    }

    @Test
    fun retry_afterFailure_resolvesRoute() = runTest {
        settings.isFirstLaunchError = SQLiteException("disk I/O error")
        val vm = viewModel()
        advanceUntilIdle()
        assertThat(vm.uiState.value).isEqualTo(LaunchUiState.Error)

        settings.isFirstLaunchError = null
        settings.isFirstLaunchResult = true
        vm.retry()
        advanceUntilIdle()

        assertThat(vm.uiState.value).isEqualTo(LaunchUiState.Ready(Routes.ONBOARDING))
    }

    @Test
    fun retry_whileLoading_isIgnored() = runTest {
        val vm = viewModel()
        assertThat(vm.uiState.value).isEqualTo(LaunchUiState.Loading)
        vm.retry()
        advanceUntilIdle()

        assertThat(settings.isFirstLaunchCalls).isEqualTo(1)
    }

    @Test
    fun savedRoute_skipsResolve() = runTest {
        val vm = viewModel(SavedStateHandle(mapOf("launch_route" to Routes.RECOVERY)))
        advanceUntilIdle()

        assertThat(vm.uiState.value).isEqualTo(LaunchUiState.Ready(Routes.RECOVERY))
        assertThat(settings.isFirstLaunchCalls).isEqualTo(0)
    }

    @Test
    fun success_savesRoute() = runTest {
        settings.isFirstLaunchResult = false
        val handle = SavedStateHandle()
        val vm = viewModel(handle)
        advanceUntilIdle()

        assertThat(vm.uiState.value).isEqualTo(LaunchUiState.Ready(Routes.HOME))
        assertThat(handle.get<String>("launch_route")).isEqualTo(Routes.HOME)
    }

    @Test
    fun cancellation_isNotLoggedAsError() = runTest {
        settings.isFirstLaunchError = CancellationException("cancelled")
        val vm = viewModel()
        advanceUntilIdle()

        assertThat(logger.calls).isEmpty()
        assertThat(vm.uiState.value).isNotEqualTo(LaunchUiState.Error)
    }
}
