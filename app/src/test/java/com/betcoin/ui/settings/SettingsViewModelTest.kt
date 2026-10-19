package com.betcoin.ui.settings

import android.database.sqlite.SQLiteException
import com.betcoin.data.model.ThemePreference
import com.betcoin.ui.common.GENERIC_ERROR_MESSAGE
import com.betcoin.ui.onboarding.FakeSettingsRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/** Tests for [SettingsViewModel]. */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fake: FakeSettingsRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fake = FakeSettingsRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_reflectsStoredPreference() = runTest(testDispatcher) {
        fake.themePreference.value = ThemePreference.DARK
        val viewModel = SettingsViewModel(fake)
        val job = viewModel.uiState.onEach { }.launchIn(backgroundScope)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.themePreference).isEqualTo(ThemePreference.DARK)
        job.cancel()
    }

    @Test
    fun onThemeSelected_persistsAndUpdatesState() = runTest(testDispatcher) {
        val viewModel = SettingsViewModel(fake)
        viewModel.uiState.onEach { }.launchIn(backgroundScope)
        advanceUntilIdle()

        viewModel.onThemeSelected(ThemePreference.LIGHT)
        advanceUntilIdle()

        assertThat(fake.themePreference.value).isEqualTo(ThemePreference.LIGHT)
        assertThat(viewModel.uiState.value.themePreference).isEqualTo(ThemePreference.LIGHT)
    }

    @Test
    fun onThemeSelected_failure_setsErrorAndKeepsSelection() = runTest(testDispatcher) {
        fake.themePreference.value = ThemePreference.DARK
        val viewModel = SettingsViewModel(fake)
        viewModel.uiState.onEach { }.launchIn(backgroundScope)
        advanceUntilIdle()
        fake.failNextSetTheme = SQLiteException("disk full")

        viewModel.onThemeSelected(ThemePreference.LIGHT)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.errorMessage).isEqualTo(GENERIC_ERROR_MESSAGE)
        assertThat(viewModel.uiState.value.themePreference).isEqualTo(ThemePreference.DARK)
    }

    @Test
    fun onErrorShown_clearsError() = runTest(testDispatcher) {
        val viewModel = SettingsViewModel(fake)
        viewModel.uiState.onEach { }.launchIn(backgroundScope)
        fake.failNextSetTheme = SQLiteException("disk full")
        viewModel.onThemeSelected(ThemePreference.LIGHT)
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.errorMessage).isNotNull()

        viewModel.onErrorShown()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.errorMessage).isNull()
    }
}
