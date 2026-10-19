package com.betcoin.ui.theme

import com.betcoin.data.model.ThemePreference
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
    fun themePreference_isNullBeforeFirstEmission_thenStoredValue() = runTest(testDispatcher) {
        fake.themePreference.value = ThemePreference.DARK
        val viewModel = AppThemeViewModel(fake)

        assertThat(viewModel.themePreference.value).isNull()

        advanceUntilIdle()

        assertThat(viewModel.themePreference.value).isEqualTo(ThemePreference.DARK)
    }

    @Test
    fun themePreference_propagatesRepositoryChange() = runTest(testDispatcher) {
        val viewModel = AppThemeViewModel(fake)
        advanceUntilIdle()
        assertThat(viewModel.themePreference.value).isEqualTo(ThemePreference.SYSTEM)

        fake.setThemePreference(ThemePreference.LIGHT)
        advanceUntilIdle()

        assertThat(viewModel.themePreference.value).isEqualTo(ThemePreference.LIGHT)
    }
}
