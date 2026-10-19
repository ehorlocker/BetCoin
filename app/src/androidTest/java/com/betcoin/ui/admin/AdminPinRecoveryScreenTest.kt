package com.betcoin.ui.admin

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Compose UI tests for [AdminPinRecoveryContent]. */
@RunWith(AndroidJUnit4::class)
class AdminPinRecoveryScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun setContent(state: AdminPinRecoveryUiState) {
        composeRule.setContent {
            AdminPinRecoveryContent(
                uiState = state,
                onBack = {},
                onCodeChanged = {},
                onSubmitCode = {},
                onPinChanged = {},
                onConfirmPinChanged = {},
                onSavePin = {},
                onAcknowledgedChanged = {},
                onContinue = {},
            )
        }
    }

    @Test
    fun enterCodeStep_showsCopy() {
        setContent(AdminPinRecoveryUiState.EnterCode())

        composeRule.onNodeWithText("Reset Admin PIN").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Enter the recovery code you wrote down when you set up the admin PIN.",
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Recovery code").assertIsDisplayed()
        composeRule.onNodeWithText("Next").assertIsDisplayed()
    }

    @Test
    fun enterCodeStep_showsError() {
        setContent(AdminPinRecoveryUiState.EnterCode(error = "Wrong recovery code"))

        composeRule.onNodeWithText("Wrong recovery code").assertIsDisplayed()
    }

    @Test
    fun enterNewPinStep_showsCopy() {
        setContent(AdminPinRecoveryUiState.EnterNewPin())

        composeRule.onNodeWithText("Choose a new admin PIN").assertIsDisplayed()
        composeRule.onNodeWithText("New PIN").assertIsDisplayed()
        composeRule.onNodeWithText("Confirm PIN").assertIsDisplayed()
        composeRule.onNodeWithText("Save PIN").assertIsDisplayed()
    }

    @Test
    fun showNewCodeStep_continueDisabledUntilChecked() {
        val acknowledged = androidx.compose.runtime.mutableStateOf(false)
        composeRule.setContent {
            AdminPinRecoveryContent(
                uiState = AdminPinRecoveryUiState.ShowNewCode("ABCD-2345-EFGH", acknowledged.value),
                onBack = {},
                onCodeChanged = {},
                onSubmitCode = {},
                onPinChanged = {},
                onConfirmPinChanged = {},
                onSavePin = {},
                onAcknowledgedChanged = { acknowledged.value = it },
                onContinue = {},
            )
        }

        composeRule.onNodeWithTag(ADMIN_RECOVERY_CODE_TAG).assertTextEquals("ABCD-2345-EFGH")
        composeRule.onNodeWithText("Your old recovery code no longer works.").assertIsDisplayed()
        composeRule.onNodeWithText("Continue").assertIsNotEnabled()

        composeRule.onNodeWithText("I've written this down").performClick()

        composeRule.onNodeWithText("Continue").assertIsEnabled()
    }
}
