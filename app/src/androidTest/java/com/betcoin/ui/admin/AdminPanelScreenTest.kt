package com.betcoin.ui.admin

import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Compose UI tests for [AdminPanelContent]. */
@RunWith(AndroidJUnit4::class)
class AdminPanelScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var changePinClicks = 0
    private var regenerateClicks = 0
    private var backCalls = 0

    private fun setContent(state: AdminUiState = AdminUiState()) {
        composeRule.setContent {
            AdminPanelContent(
                uiState = state,
                onNavigateBack = { backCalls++ },
                onChangePinClicked = { changePinClicks++ },
                onChangePinPinChanged = {},
                onChangePinConfirmChanged = {},
                onChangePinSave = {},
                onChangePinDismiss = {},
                onRegenerateClicked = { regenerateClicks++ },
                onRegenerateConfirm = {},
                onRegenerateCancel = {},
                onRecoveryCodeAcknowledged = {},
                onRecoveryCodeDone = {},
                onMessageShown = {},
            )
        }
    }

    @Test
    fun showsThreeSectionHeaders() {
        setContent()

        composeRule.onNodeWithText("Admin Panel").assertIsDisplayed()
        listOf("Player Management", "Bet Management", "App Settings").forEach {
            composeRule.onNodeWithText(it).performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun clickingSettingsRows_firesCallbacks() {
        setContent()

        composeRule.onNodeWithText("Change Admin PIN").performScrollTo().performClick()
        composeRule.onNodeWithText("Regenerate Recovery Code").performScrollTo().performClick()

        assertEquals(1, changePinClicks)
        assertEquals(1, regenerateClicks)
    }

    @Test
    fun recoveryCodeShown_doneScrollableIntoView_atLargeFontScale() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                AdminPanelContent(
                    uiState = AdminUiState(recoveryCode = RecoveryCodeDialogState.Show("ABCD-2345-EFGH")),
                    onNavigateBack = {},
                    onChangePinClicked = {},
                    onChangePinPinChanged = {},
                    onChangePinConfirmChanged = {},
                    onChangePinSave = {},
                    onChangePinDismiss = {},
                    onRegenerateClicked = {},
                    onRegenerateConfirm = {},
                    onRegenerateCancel = {},
                    onRecoveryCodeAcknowledged = {},
                    onRecoveryCodeDone = {},
                    onMessageShown = {},
                )
            }
        }

        composeRule.onNodeWithText("Done").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun recoveryCodeShown_doneDisabledUntilAcknowledged() {
        setContent(AdminUiState(recoveryCode = RecoveryCodeDialogState.Show("ABCD-2345-EFGH")))

        composeRule.onNodeWithTag(ADMIN_SETTINGS_RECOVERY_CODE_TAG).assertTextEquals("ABCD-2345-EFGH")
        composeRule.onNodeWithText("Done").assertIsNotEnabled()
    }

    @Test
    fun recoveryCodeAcknowledged_doneEnabled() {
        setContent(AdminUiState(recoveryCode = RecoveryCodeDialogState.Show("ABCD-2345-EFGH", acknowledged = true)))

        composeRule.onNodeWithText("Done").assertIsEnabled()
    }

    @Test
    fun confirmRegenerate_showsWarning() {
        setContent(AdminUiState(recoveryCode = RecoveryCodeDialogState.ConfirmRegenerate))

        composeRule.onNodeWithText("Your current recovery code will stop working.").assertIsDisplayed()
    }

    @Test
    fun changePinError_isShown() {
        setContent(AdminUiState(changePin = ChangePinDialogState(error = "PINs don't match")))

        composeRule.onNodeWithText("PINs don't match").assertIsDisplayed()
    }

    @Test
    fun back_whenIdle_navigatesBack() {
        setContent()

        pressBack()

        assertEquals(1, backCalls)
    }

    @Test
    fun back_whileRecoveryCodeShown_doesNotLeaveOrDismiss() {
        setContent(AdminUiState(recoveryCode = RecoveryCodeDialogState.Show("ABCD-2345-EFGH")))

        pressBack()

        assertEquals(0, backCalls)
        composeRule.onNodeWithTag(ADMIN_SETTINGS_RECOVERY_CODE_TAG).assertIsDisplayed()
    }

    @Test
    fun back_whileGenerating_doesNotLeave() {
        setContent(AdminUiState(recoveryCode = RecoveryCodeDialogState.Generating))

        pressBack()

        assertEquals(0, backCalls)
        composeRule.onNodeWithText("Creating recovery code...").assertIsDisplayed()
    }

    /**
     * Sends a hardware Back key to the focused window. Espresso.pressBack waits for a settled root view,
     * which never happens while the Generating dialog's indeterminate indicator keeps requesting layout.
     */
    private fun pressBack() {
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        composeRule.waitForIdle()
    }
}
