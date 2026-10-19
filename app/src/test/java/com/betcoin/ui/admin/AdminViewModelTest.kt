package com.betcoin.ui.admin

import com.betcoin.data.model.WrongRecoveryCodeException
import com.betcoin.data.repository.SettingsRepository
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
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

/** Tests for [AdminViewModel]: App Settings (change PIN, regenerate recovery code). */
@OptIn(ExperimentalCoroutinesApi::class)
class AdminViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fake: FakeSettingsRepository
    private lateinit var originalCode: String
    private var setPinCalls = 0
    private var failRegenerate = false

    private lateinit var viewModel: AdminViewModel

    @Before
    fun setUp() = runTest(testDispatcher) {
        Dispatchers.setMain(testDispatcher)
        fake = FakeSettingsRepository()
        fake.setAdminPin("1234")
        originalCode = fake.regenerateRecoveryCode()
        val wrapped = object : SettingsRepository by fake {
            override suspend fun setAdminPin(pin: String) {
                setPinCalls++
                fake.setAdminPin(pin)
            }

            override suspend fun regenerateRecoveryCode(): String {
                if (failRegenerate) error("boom")
                return fake.regenerateRecoveryCode()
            }
        }
        viewModel = AdminViewModel(wrapped, com.betcoin.testing.FakeUserRepository())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val state get() = viewModel.uiState.value

    private fun savePin(pin: String, confirm: String = pin) {
        viewModel.onChangePinClicked()
        viewModel.onChangePinPinChanged(pin)
        viewModel.onChangePinConfirmChanged(confirm)
        viewModel.onChangePinSave()
    }

    @Test
    fun changePin_valid_updatesPin_andShowsMessage() = runTest(testDispatcher) {
        savePin("5678")
        advanceUntilIdle()

        assertThat(fake.verifyAdminPin("5678")).isTrue()
        assertThat(state.changePin).isNull()
        assertThat(state.message).isEqualTo("Admin PIN changed")
    }

    @Test
    fun changePin_mismatch_error_pinUnchanged() = runTest(testDispatcher) {
        savePin("5678", "5679")
        advanceUntilIdle()

        assertThat(state.changePin!!.error).isEqualTo("PINs don't match")
        assertThat(fake.verifyAdminPin("1234")).isTrue()
    }

    @Test
    fun changePin_tooShort_error() = runTest(testDispatcher) {
        savePin("12")
        advanceUntilIdle()

        assertThat(state.changePin!!.error).isEqualTo("PIN must be exactly 4 digits")
        assertThat(setPinCalls).isEqualTo(0)
    }

    @Test
    fun changePin_saveTwice_callsOnce() = runTest(testDispatcher) {
        savePin("5678")
        viewModel.onChangePinSave()
        advanceUntilIdle()

        assertThat(setPinCalls).isEqualTo(1)
    }

    @Test
    fun changePin_dismissWhileSaving_isIgnored() = runTest(testDispatcher) {
        savePin("5678")
        viewModel.onChangePinDismiss()

        assertThat(state.changePin).isNotNull()
        advanceUntilIdle()
        assertThat(state.message).isEqualTo("Admin PIN changed")
    }

    @Test
    fun regenerate_confirm_showsNewCode_oldCodeStopsWorking() = runTest(testDispatcher) {
        viewModel.onRegenerateClicked()
        assertThat(state.recoveryCode).isEqualTo(RecoveryCodeDialogState.ConfirmRegenerate)
        viewModel.onRegenerateConfirm()
        assertThat(state.recoveryCode).isEqualTo(RecoveryCodeDialogState.Generating)
        advanceUntilIdle()

        val newCode = (state.recoveryCode as RecoveryCodeDialogState.Show).code
        assertThat(newCode).isNotEqualTo(originalCode)
        assertThrows(WrongRecoveryCodeException::class.java) {
            kotlinx.coroutines.runBlocking { fake.resetAdminPinWithRecoveryCode(originalCode, "4321") }
        }
        kotlinx.coroutines.runBlocking { fake.resetAdminPinWithRecoveryCode(newCode, "4321") }
    }

    @Test
    fun regenerate_cancel_noNewCode() = runTest(testDispatcher) {
        viewModel.onRegenerateClicked()
        viewModel.onRegenerateCancel()
        advanceUntilIdle()

        assertThat(state.recoveryCode).isNull()
        fake.resetAdminPinWithRecoveryCode(originalCode, "4321")
    }

    @Test
    fun regenerate_cancelWhileGenerating_doesNothing() = runTest(testDispatcher) {
        viewModel.onRegenerateClicked()
        viewModel.onRegenerateConfirm()
        viewModel.onRegenerateCancel()

        assertThat(state.recoveryCode).isEqualTo(RecoveryCodeDialogState.Generating)
        advanceUntilIdle()
        assertThat(state.recoveryCode).isInstanceOf(RecoveryCodeDialogState.Show::class.java)
    }

    @Test
    fun recoveryCodeDone_beforeAcknowledge_keepsDialog() = runTest(testDispatcher) {
        viewModel.onRegenerateClicked()
        viewModel.onRegenerateConfirm()
        advanceUntilIdle()

        viewModel.onRecoveryCodeDone()
        assertThat(state.recoveryCode).isInstanceOf(RecoveryCodeDialogState.Show::class.java)

        viewModel.onRecoveryCodeAcknowledged(true)
        viewModel.onRecoveryCodeDone()
        assertThat(state.recoveryCode).isNull()
        assertThat(state.message).isEqualTo("New recovery code saved")
    }

    @Test
    fun regenerate_repositoryFails_showsMessage() = runTest(testDispatcher) {
        failRegenerate = true
        viewModel.onRegenerateClicked()
        viewModel.onRegenerateConfirm()
        advanceUntilIdle()

        assertThat(state.recoveryCode).isNull()
        assertThat(state.message).isEqualTo("Couldn't create the recovery code. Try again.")
    }

    @Test
    fun onMessageShown_clearsMessage() = runTest(testDispatcher) {
        savePin("5678")
        advanceUntilIdle()
        viewModel.onMessageShown()

        assertThat(state.message).isNull()
    }
}
