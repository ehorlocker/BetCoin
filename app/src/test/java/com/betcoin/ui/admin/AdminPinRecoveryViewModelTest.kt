package com.betcoin.ui.admin

import com.betcoin.testing.FakeSettingsRepository
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

/** Tests for [AdminPinRecoveryViewModel]: code entry, new PIN, and the new recovery code gate. */
@OptIn(ExperimentalCoroutinesApi::class)
class AdminPinRecoveryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fake: FakeSettingsRepository
    private lateinit var originalCode: String
    private var resetCalls = 0

    private lateinit var viewModel: AdminPinRecoveryViewModel

    @Before
    fun setUp() = runTest(testDispatcher) {
        Dispatchers.setMain(testDispatcher)
        fake = FakeSettingsRepository()
        fake.setAdminPin("1234")
        originalCode = fake.regenerateRecoveryCode()
        val counting = object : SettingsRepository by fake {
            override suspend fun resetAdminPinWithRecoveryCode(code: String, newPin: String): String {
                resetCalls++
                return fake.resetAdminPinWithRecoveryCode(code, newPin)
            }
        }
        viewModel = AdminPinRecoveryViewModel(counting)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val enterCode get() = viewModel.uiState.value as AdminPinRecoveryUiState.EnterCode
    private val enterPin get() = viewModel.uiState.value as AdminPinRecoveryUiState.EnterNewPin
    private val showCode get() = viewModel.uiState.value as AdminPinRecoveryUiState.ShowNewCode

    private fun submitCode(code: String) {
        viewModel.onCodeChanged(code)
        viewModel.onSubmitCode()
    }

    private fun savePin(pin: String, confirm: String = pin) {
        viewModel.onPinChanged(pin)
        viewModel.onConfirmPinChanged(confirm)
        viewModel.onSavePin()
    }

    @Test
    fun submitCode_invalidFormat_showsError() {
        submitCode("ABC")

        assertThat(enterCode.error).isEqualTo("Enter the 12-character code")
    }

    @Test
    fun fullFlow_correctCode_setsNewPin_showsNewCode() = runTest(testDispatcher) {
        submitCode(originalCode)
        assertThat(viewModel.uiState.value).isInstanceOf(AdminPinRecoveryUiState.EnterNewPin::class.java)

        savePin("5678")
        advanceUntilIdle()

        assertThat(fake.verifyAdminPin("5678")).isTrue()
        assertThat(fake.verifyAdminPin("1234")).isFalse()
        assertThat(showCode.recoveryCode).isNotEqualTo(originalCode)
        assertThat(showCode.acknowledged).isFalse()
        // The old code no longer resets.
        val retry = AdminPinRecoveryViewModel(fake)
        retry.onCodeChanged(originalCode)
        retry.onSubmitCode()
        retry.onPinChanged("9999")
        retry.onConfirmPinChanged("9999")
        retry.onSavePin()
        advanceUntilIdle()
        assertThat(retry.uiState.value).isInstanceOf(AdminPinRecoveryUiState.EnterCode::class.java)
        assertThat(fake.verifyAdminPin("5678")).isTrue()
    }

    @Test
    fun lowercaseNoDashes_accepted() = runTest(testDispatcher) {
        submitCode(originalCode.replace("-", "").lowercase())
        savePin("5678")
        advanceUntilIdle()

        assertThat(viewModel.uiState.value).isInstanceOf(AdminPinRecoveryUiState.ShowNewCode::class.java)
    }

    @Test
    fun wrongCode_returnsToEnterCodeWithError_andPinUnchanged() = runTest(testDispatcher) {
        submitCode("AAAA-AAAA-AAAA")
        savePin("5678")
        advanceUntilIdle()

        assertThat(enterCode.error).isEqualTo("Wrong recovery code")
        assertThat(fake.verifyAdminPin("1234")).isTrue()

        submitCode(originalCode)
        savePin("5678")
        advanceUntilIdle()

        assertThat(viewModel.uiState.value).isInstanceOf(AdminPinRecoveryUiState.ShowNewCode::class.java)
        assertThat(fake.verifyAdminPin("5678")).isTrue()
    }

    @Test
    fun pinMismatch_showsError_noRepositoryCall() = runTest(testDispatcher) {
        submitCode(originalCode)
        savePin("5678", "5679")
        advanceUntilIdle()

        assertThat(enterPin.error).isEqualTo("PINs don't match")
        assertThat(resetCalls).isEqualTo(0)
    }

    @Test
    fun savePin_twice_whileSaving_callsRepositoryOnce() = runTest(testDispatcher) {
        submitCode(originalCode)
        savePin("5678")
        viewModel.onSavePin()
        advanceUntilIdle()

        assertThat(resetCalls).isEqualTo(1)
    }

    @Test
    fun continue_beforeAcknowledge_doesNothing() = runTest(testDispatcher) {
        submitCode(originalCode)
        savePin("5678")
        advanceUntilIdle()

        viewModel.onContinue()

        assertThat(viewModel.recovered.value).isFalse()
    }

    @Test
    fun continue_afterAcknowledge_setsRecovered() = runTest(testDispatcher) {
        submitCode(originalCode)
        savePin("5678")
        advanceUntilIdle()

        viewModel.onAcknowledgedChanged(true)
        viewModel.onContinue()

        assertThat(viewModel.recovered.value).isTrue()
    }

    @Test
    fun onBack_fromNewPin_returnsToCodeWithCode() {
        submitCode(originalCode)

        assertThat(viewModel.onBack()).isTrue()

        assertThat(enterCode.code).isEqualTo(originalCode)
    }

    @Test
    fun onBack_fromEnterCode_returnsFalse() {
        assertThat(viewModel.onBack()).isFalse()
    }

    @Test
    fun onBack_whileSaving_returnsTrue_andStaysOnPinStep() = runTest(testDispatcher) {
        submitCode(originalCode)
        savePin("5678")

        assertThat(enterPin.isSaving).isTrue()
        assertThat(viewModel.onBack()).isTrue()
        assertThat(viewModel.uiState.value).isInstanceOf(AdminPinRecoveryUiState.EnterNewPin::class.java)

        advanceUntilIdle()
        assertThat(viewModel.uiState.value).isInstanceOf(AdminPinRecoveryUiState.ShowNewCode::class.java)
    }
}
