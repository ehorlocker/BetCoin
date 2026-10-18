package com.betcoin.ui.onboarding

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.betcoin.data.model.ThemePreference
import com.betcoin.data.repository.RecoveryCode
import com.betcoin.data.repository.SettingsRepository
import com.betcoin.data.security.PinHasher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose UI tests for [OnboardingScreen] and [OnboardingContent].
 */
@RunWith(AndroidJUnit4::class)
class OnboardingScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private fun setContent(
        state: OnboardingUiState,
        onSavePin: () -> Unit = {},
        onAcknowledgedChanged: (Boolean) -> Unit = {},
        onContinue: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            OnboardingContent(
                uiState = state,
                onPinChanged = {},
                onConfirmPinChanged = {},
                onSavePin = onSavePin,
                onAcknowledgedChanged = onAcknowledgedChanged,
                onContinue = onContinue,
            )
        }
    }

    // --- PIN entry step ---

    @Test
    fun enterPin_showsWelcomeAndBothPinFields() {
        setContent(OnboardingUiState.EnterPin())

        composeTestRule.onNodeWithText("Welcome to BetCoin").assertIsDisplayed()
        composeTestRule.onNodeWithText("Set up your master admin PIN").assertIsDisplayed()
        composeTestRule.onNodeWithText("4-digit PIN").assertIsDisplayed()
        composeTestRule.onNodeWithText("Confirm PIN").assertIsDisplayed()
        composeTestRule.onNodeWithText("Set PIN").assertIsEnabled()
    }

    @Test
    fun enterPin_error_isShown() {
        setContent(OnboardingUiState.EnterPin(pin = "1234", confirmPin = "1243", error = "PINs don't match"))

        composeTestRule.onNodeWithText("PINs don't match").assertIsDisplayed()
    }

    @Test
    fun enterPin_saving_disablesSetPin() {
        setContent(OnboardingUiState.EnterPin(pin = "1234", confirmPin = "1234", isSaving = true))

        composeTestRule.onNodeWithText("Set PIN").assertIsNotEnabled()
    }

    @Test
    fun enterPin_setPinClick_callsOnSavePin() {
        var saved = false
        setContent(OnboardingUiState.EnterPin(pin = "1234", confirmPin = "1234"), onSavePin = { saved = true })

        composeTestRule.onNodeWithText("Set PIN").performClick()

        assertTrue(saved)
    }

    // --- Recovery code step ---

    @Test
    fun recoveryCode_showsCodeWithContinueDisabled() {
        setContent(OnboardingUiState.ShowRecoveryCode("ABCD-EFGH-JKLM"))

        composeTestRule.onNodeWithText("Save your recovery code").assertIsDisplayed()
        composeTestRule.onNodeWithTag(RECOVERY_CODE_TAG).assertTextEquals("ABCD-EFGH-JKLM")
        composeTestRule.onNodeWithText("I've written this down").assertIsOff()
        composeTestRule.onNodeWithText("Continue").assertIsNotEnabled()
    }

    @Test
    fun recoveryCode_acknowledged_enablesContinue() {
        var continued = false
        setContent(
            OnboardingUiState.ShowRecoveryCode("ABCD-EFGH-JKLM", acknowledged = true),
            onContinue = { continued = true },
        )

        composeTestRule.onNodeWithText("I've written this down").assertIsOn()
        composeTestRule.onNodeWithText("Continue").assertIsEnabled().performClick()

        assertTrue(continued)
    }

    @Test
    fun recoveryCode_tappingCheckboxRow_reportsAcknowledged() {
        var acknowledged: Boolean? = null
        setContent(
            OnboardingUiState.ShowRecoveryCode("ABCD-EFGH-JKLM"),
            onAcknowledgedChanged = { acknowledged = it },
        )

        composeTestRule.onNodeWithText("I've written this down").performClick()

        assertEquals(true, acknowledged)
    }

    @Test
    fun recoveryCode_backIsIgnored() {
        setContent(OnboardingUiState.ShowRecoveryCode("ABCD-EFGH-JKLM"))

        composeTestRule.runOnUiThread { composeTestRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeTestRule.waitForIdle()

        assertFalse(composeTestRule.activity.isFinishing)
        composeTestRule.onNodeWithTag(RECOVERY_CODE_TAG).assertIsDisplayed()
    }

    @Test
    fun enterPin_backIsNotIntercepted() {
        setContent(OnboardingUiState.EnterPin())

        composeTestRule.runOnUiThread { composeTestRule.activity.onBackPressedDispatcher.onBackPressed() }

        assertTrue(composeTestRule.activity.isFinishing)
    }

    // --- Full flow with the real ViewModel ---

    @Test
    fun fullFlow_mismatchThenMatch_showsCodeAndCompletesAfterAcknowledging() {
        val repository = HashingSettingsRepository()
        val viewModel = OnboardingViewModel(repository)
        var completed by mutableStateOf(false)
        composeTestRule.setContent {
            OnboardingScreen(viewModel = viewModel, onOnboardingComplete = { completed = true })
        }

        composeTestRule.onNodeWithText("4-digit PIN").performTextInput("1234")
        composeTestRule.onNodeWithText("Confirm PIN").performTextInput("1243")
        composeTestRule.onNodeWithText("Set PIN").performClick()
        composeTestRule.onNodeWithText("PINs don't match").assertIsDisplayed()
        assertEquals(null, repository.pinHash)

        composeTestRule.onNodeWithText("Confirm PIN").performTextClearance()
        composeTestRule.onNodeWithText("Confirm PIN").performTextInput("1234")
        composeTestRule.onNodeWithText("Set PIN").performClick()
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            composeTestRule.onAllNodes(hasTestTag(RECOVERY_CODE_TAG)).fetchSemanticsNodes().isNotEmpty()
        }

        val shownCode = (viewModel.uiState.value as OnboardingUiState.ShowRecoveryCode).recoveryCode
        composeTestRule.onNodeWithTag(RECOVERY_CODE_TAG).assertTextEquals(shownCode)
        assertTrue(PinHasher.verifyBlocking("1234", repository.pinHash!!))

        composeTestRule.onNodeWithText("Continue").assertIsNotEnabled().performClick()
        composeTestRule.waitForIdle()
        assertEquals(false, completed)

        composeTestRule.onNodeWithText("I've written this down").performClick()
        composeTestRule.onNodeWithText("Continue").assertIsEnabled().performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) { completed }
    }
}

/**
 * Minimal [SettingsRepository] that keeps only bcrypt hashes of the admin PIN and recovery
 * code, like the real implementation (spec §5.5).
 */
private class HashingSettingsRepository : SettingsRepository {
    var pinHash: String? = null
    var recoveryCodeHash: String? = null

    override suspend fun getAdminPinHash(): String? = pinHash
    override suspend fun setAdminPin(pin: String) {
        pinHash = PinHasher.hash(pin)
    }
    override suspend fun verifyAdminPin(pin: String): Boolean =
        pinHash?.let { PinHasher.verify(pin, it) } ?: false
    override suspend fun isFirstLaunch(): Boolean = pinHash == null
    override suspend fun regenerateRecoveryCode(): String {
        val code = RecoveryCode.generate()
        recoveryCodeHash = PinHasher.hash(RecoveryCode.normalize(code)!!)
        return code
    }
    override suspend fun resetAdminPinWithRecoveryCode(code: String, newPin: String): String =
        throw UnsupportedOperationException()
    override fun getThemePreference(): Flow<ThemePreference> = flowOf(ThemePreference.SYSTEM)
    override suspend fun setThemePreference(pref: ThemePreference) {}
}
