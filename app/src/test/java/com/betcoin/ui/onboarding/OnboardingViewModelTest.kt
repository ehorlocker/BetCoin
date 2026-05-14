package com.betcoin.ui.onboarding

import com.betcoin.data.repository.SettingsRepository
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

/**
 * Tests for [OnboardingViewModel].
 *
 * Verifies first-launch detection, PIN validation, and admin PIN setup.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeSettingsRepository: FakeSettingsRepository
    private lateinit var viewModel: OnboardingViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeSettingsRepository = FakeSettingsRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- isFirstLaunch ---

    @Test
    fun checkFirstLaunch_noPinSet_emitsTrue() = runTest {
        fakeSettingsRepository.isFirstLaunchResult = true
        viewModel = OnboardingViewModel(fakeSettingsRepository)
        advanceUntilIdle()

        assertThat(viewModel.isFirstLaunch.value).isTrue()
    }

    @Test
    fun checkFirstLaunch_pinAlreadySet_emitsFalse() = runTest {
        fakeSettingsRepository.isFirstLaunchResult = false
        viewModel = OnboardingViewModel(fakeSettingsRepository)
        advanceUntilIdle()

        assertThat(viewModel.isFirstLaunch.value).isFalse()
    }

    // --- setAdminPin ---

    @Test
    fun setAdminPin_validPin_storesPinAndCompletes() = runTest {
        viewModel = OnboardingViewModel(fakeSettingsRepository)
        advanceUntilIdle()

        viewModel.setAdminPin("1234")
        advanceUntilIdle()

        assertThat(fakeSettingsRepository.storedPin).isEqualTo("1234")
        assertThat(viewModel.onboardingComplete.value).isTrue()
    }

    @Test
    fun setAdminPin_tooShort_showsError() = runTest {
        viewModel = OnboardingViewModel(fakeSettingsRepository)
        advanceUntilIdle()

        viewModel.setAdminPin("12")
        advanceUntilIdle()

        assertThat(viewModel.errorMessage.value).isEqualTo("PIN must be at least 4 digits")
        assertThat(viewModel.onboardingComplete.value).isFalse()
    }

    @Test
    fun setAdminPin_empty_showsError() = runTest {
        viewModel = OnboardingViewModel(fakeSettingsRepository)
        advanceUntilIdle()

        viewModel.setAdminPin("")
        advanceUntilIdle()

        assertThat(viewModel.errorMessage.value).isEqualTo("PIN must be at least 4 digits")
        assertThat(viewModel.onboardingComplete.value).isFalse()
    }

    @Test
    fun setAdminPin_nonNumeric_showsError() = runTest {
        viewModel = OnboardingViewModel(fakeSettingsRepository)
        advanceUntilIdle()

        viewModel.setAdminPin("abcd")
        advanceUntilIdle()

        assertThat(viewModel.errorMessage.value).isEqualTo("PIN must be numeric")
        assertThat(viewModel.onboardingComplete.value).isFalse()
    }

    // --- clearError ---

    @Test
    fun clearError_clearsErrorMessage() = runTest {
        viewModel = OnboardingViewModel(fakeSettingsRepository)
        advanceUntilIdle()

        viewModel.setAdminPin("12")
        advanceUntilIdle()
        assertThat(viewModel.errorMessage.value).isNotNull()

        viewModel.clearError()
        assertThat(viewModel.errorMessage.value).isNull()
    }
}
