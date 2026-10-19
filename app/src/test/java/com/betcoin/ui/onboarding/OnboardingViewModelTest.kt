package com.betcoin.ui.onboarding

import com.betcoin.data.repository.RecoveryCode
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
import java.io.IOException

/**
 * Tests for [OnboardingViewModel]: PIN entry and confirmation, saving the PIN, and the
 * one-time recovery code gate.
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
        viewModel = OnboardingViewModel(fakeSettingsRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val enterPinState get() = viewModel.uiState.value as OnboardingUiState.EnterPin
    private val recoveryState get() = viewModel.uiState.value as OnboardingUiState.ShowRecoveryCode

    private fun enterPins(pin: String, confirmPin: String) {
        viewModel.onPinChanged(pin)
        viewModel.onConfirmPinChanged(confirmPin)
    }

    // --- PIN entry ---

    @Test
    fun initialState_isEmptyPinEntry() {
        assertThat(viewModel.uiState.value).isEqualTo(OnboardingUiState.EnterPin())
        assertThat(viewModel.onboardingComplete.value).isFalse()
    }

    @Test
    fun onPinChanged_keepsOnlyFourDigits() {
        viewModel.onPinChanged("12a3456")
        viewModel.onConfirmPinChanged("9 8-7")

        assertThat(enterPinState.pin).isEqualTo("1234")
        assertThat(enterPinState.confirmPin).isEqualTo("987")
    }

    @Test
    fun onSavePin_tooShort_showsLengthError() = runTest {
        enterPins("123", "123")

        viewModel.onSavePin()
        advanceUntilIdle()

        assertThat(enterPinState.error).isEqualTo(OnboardingViewModel.ERROR_PIN_LENGTH)
        assertThat(fakeSettingsRepository.storedHash).isNull()
    }

    @Test
    fun onSavePin_empty_showsLengthError() = runTest {
        viewModel.onSavePin()
        advanceUntilIdle()

        assertThat(enterPinState.error).isEqualTo(OnboardingViewModel.ERROR_PIN_LENGTH)
        assertThat(fakeSettingsRepository.storedHash).isNull()
    }

    @Test
    fun onSavePin_mismatch_showsMismatchErrorAndSavesNothing() = runTest {
        enterPins("1234", "1243")

        viewModel.onSavePin()
        advanceUntilIdle()

        assertThat(enterPinState.error).isEqualTo(OnboardingViewModel.ERROR_PIN_MISMATCH)
        assertThat(fakeSettingsRepository.storedHash).isNull()
        assertThat(fakeSettingsRepository.storedRecoveryCodeHash).isNull()
    }

    @Test
    fun onSavePin_confirmationIncomplete_showsMismatchError() = runTest {
        enterPins("1234", "12")

        viewModel.onSavePin()
        advanceUntilIdle()

        assertThat(enterPinState.error).isEqualTo(OnboardingViewModel.ERROR_PIN_MISMATCH)
    }

    @Test
    fun editingAfterError_clearsError() = runTest {
        enterPins("1234", "1243")
        viewModel.onSavePin()
        assertThat(enterPinState.error).isNotNull()

        viewModel.onConfirmPinChanged("124")

        assertThat(enterPinState.error).isNull()
    }

    // --- Saving ---

    @Test
    fun onSavePin_matchingPins_storesPinAndShowsRecoveryCode() = runTest {
        enterPins("1234", "1234")

        viewModel.onSavePin()
        advanceUntilIdle()

        assertThat(fakeSettingsRepository.verifyAdminPin("1234")).isTrue()
        val code = recoveryState.recoveryCode
        assertThat(code).matches("[A-Z2-9]{4}-[A-Z2-9]{4}-[A-Z2-9]{4}")
        assertThat(RecoveryCode.normalize(code)).isNotNull()
        assertThat(recoveryState.acknowledged).isFalse()
        assertThat(viewModel.onboardingComplete.value).isFalse()
    }

    @Test
    fun onSavePin_storesOnlyTheHashOfTheShownCode() = runTest {
        enterPins("1234", "1234")

        viewModel.onSavePin()
        advanceUntilIdle()

        val code = recoveryState.recoveryCode
        assertThat(fakeSettingsRepository.storedRecoveryCodeHash).isNotEqualTo(code)
        // The shown code is the one that can reset the PIN.
        fakeSettingsRepository.resetAdminPinWithRecoveryCode(code.lowercase(), "5678")
        assertThat(fakeSettingsRepository.verifyAdminPin("5678")).isTrue()
    }

    @Test
    fun onSavePin_whileSaving_isMarkedSavingAndIgnoresEdits() = runTest {
        enterPins("1234", "1234")

        viewModel.onSavePin()
        assertThat(enterPinState.isSaving).isTrue()
        viewModel.onPinChanged("9")
        assertThat(enterPinState.pin).isEqualTo("1234")

        advanceUntilIdle()
        assertThat(viewModel.uiState.value).isInstanceOf(OnboardingUiState.ShowRecoveryCode::class.java)
    }

    @Test
    fun onSavePin_repositoryFails_showsErrorAndStaysOnPinEntry() = runTest {
        val failing = object : SettingsRepository by fakeSettingsRepository {
            override suspend fun setAdminPin(pin: String) = throw IOException("disk full")
        }
        viewModel = OnboardingViewModel(failing)
        enterPins("1234", "1234")

        viewModel.onSavePin()
        advanceUntilIdle()

        assertThat(enterPinState.error).isEqualTo(OnboardingViewModel.ERROR_SAVE_FAILED)
        assertThat(enterPinState.isSaving).isFalse()
        assertThat(enterPinState.pin).isEqualTo("1234")
        assertThat(viewModel.onboardingComplete.value).isFalse()
    }

    @Test
    fun onSavePin_recoveryCodeFails_showsCodeErrorAndRetryShowsCode() = runTest {
        var failCodeGeneration = true
        val flaky = object : SettingsRepository by fakeSettingsRepository {
            override suspend fun regenerateRecoveryCode(): String {
                if (failCodeGeneration) throw IOException("boom")
                return fakeSettingsRepository.regenerateRecoveryCode()
            }
        }
        viewModel = OnboardingViewModel(flaky)
        enterPins("1234", "1234")

        viewModel.onSavePin()
        advanceUntilIdle()
        assertThat(enterPinState.error).isEqualTo(OnboardingViewModel.ERROR_CODE_FAILED)
        assertThat(fakeSettingsRepository.verifyAdminPin("1234")).isTrue()

        failCodeGeneration = false
        viewModel.onSavePin()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value).isInstanceOf(OnboardingUiState.ShowRecoveryCode::class.java)
    }

    // --- Recovery code gate ---

    @Test
    fun onContinue_beforeAcknowledging_doesNotComplete() = runTest {
        enterPins("1234", "1234")
        viewModel.onSavePin()
        advanceUntilIdle()

        viewModel.onContinue()

        assertThat(viewModel.onboardingComplete.value).isFalse()
    }

    @Test
    fun onContinue_afterAcknowledging_completes() = runTest {
        enterPins("1234", "1234")
        viewModel.onSavePin()
        advanceUntilIdle()

        viewModel.onAcknowledgedChanged(true)
        assertThat(recoveryState.acknowledged).isTrue()
        viewModel.onContinue()

        assertThat(viewModel.onboardingComplete.value).isTrue()
    }

    @Test
    fun onContinue_afterUntickingAgain_doesNotComplete() = runTest {
        enterPins("1234", "1234")
        viewModel.onSavePin()
        advanceUntilIdle()

        viewModel.onAcknowledgedChanged(true)
        viewModel.onAcknowledgedChanged(false)
        viewModel.onContinue()

        assertThat(viewModel.onboardingComplete.value).isFalse()
    }

    @Test
    fun onContinue_duringPinEntry_doesNothing() {
        viewModel.onAcknowledgedChanged(true)
        viewModel.onContinue()

        assertThat(viewModel.uiState.value).isEqualTo(OnboardingUiState.EnterPin())
        assertThat(viewModel.onboardingComplete.value).isFalse()
    }
}
