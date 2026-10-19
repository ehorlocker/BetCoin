package com.betcoin.ui.theme

import com.betcoin.data.model.ThemePreference
import android.database.sqlite.SQLiteException
import com.betcoin.navigation.LAUNCH_LOG_TAG
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

/** Tests for [AppThemeViewModel]. */
@OptIn(ExperimentalCoroutinesApi::class)
class AppThemeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fake: FakeSettingsRepository
    private lateinit var errorLogger: RecordingErrorLogger

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fake = FakeSettingsRepository()
        errorLogger = RecordingErrorLogger()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun themePreference_isNullBeforeFirstEmission_thenStoredValue() = runTest(testDispatcher) {
        fake.themePreference.value = ThemePreference.DARK
        val viewModel = AppThemeViewModel(fake, errorLogger)

        assertThat(viewModel.themePreference.value).isNull()

        advanceUntilIdle()

        assertThat(viewModel.themePreference.value).isEqualTo(ThemePreference.DARK)
    }

    @Test
    fun themePreference_propagatesRepositoryChange() = runTest(testDispatcher) {
        val viewModel = AppThemeViewModel(fake, errorLogger)
        advanceUntilIdle()
        assertThat(viewModel.themePreference.value).isEqualTo(ThemePreference.SYSTEM)

        fake.setThemePreference(ThemePreference.LIGHT)
        advanceUntilIdle()

        assertThat(viewModel.themePreference.value).isEqualTo(ThemePreference.LIGHT)
    }

    @Test
    fun themePreference_fallsBackToSystemAndLogs_whenRepositoryReadThrows() = runTest(testDispatcher) {
        val error = SQLiteException("disk I/O error")
        fake.themePreferenceError = error
        val viewModel = AppThemeViewModel(fake, errorLogger)

        advanceUntilIdle()

        assertThat(viewModel.themePreference.value).isEqualTo(ThemePreference.SYSTEM)
        assertThat(errorLogger.calls).hasSize(1)
        assertThat(errorLogger.calls[0].tag).isEqualTo(LAUNCH_LOG_TAG)
        assertThat(errorLogger.calls[0].throwable).isSameInstanceAs(error)
    }
}
