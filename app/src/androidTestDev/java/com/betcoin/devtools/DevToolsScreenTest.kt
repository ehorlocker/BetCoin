package com.betcoin.devtools

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.betcoin.data.model.ThemePreference
import com.betcoin.ui.theme.BetCoinTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Compose UI tests for the stateless [DevToolsScreen]. */
@RunWith(AndroidJUnit4::class)
class DevToolsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val info = DevDatabaseInfo(
        databaseName = "betcoin.db",
        schemaVersion = 2,
        rowCounts = listOf(
            "users" to 5L,
            "bets" to 4L,
            "bet_outcomes" to 9L,
            "bet_participants" to 8L,
            "app_settings" to 1L,
        ),
        appVersion = "1.0 (dev)",
    )

    private fun show(
        state: DevToolsUiState = DevToolsUiState(info = info),
        onRequest: (DevToolsAction) -> Unit = {},
        onConfirm: () -> Unit = {},
        onDismiss: () -> Unit = {},
        onRecoveryCodeShown: () -> Unit = {},
        onNavigateBack: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            BetCoinTheme(themePreference = ThemePreference.DARK) {
                DevToolsScreen(
                    state = state,
                    snackbarHostState = SnackbarHostState(),
                    onNavigateBack = onNavigateBack,
                    onRequest = onRequest,
                    onConfirm = onConfirm,
                    onDismissConfirmation = onDismiss,
                    onRecoveryCodeShown = onRecoveryCodeShown,
                )
            }
        }
    }

    @Test
    fun showsInfoPanel() {
        show()

        composeTestRule.onNodeWithText("betcoin.db", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Schema version 2", substring = true).assertIsDisplayed()
        info.rowCounts.forEach { (table, count) ->
            composeTestRule.onNodeWithText("$table: $count").assertExists()
        }
        composeTestRule.onNodeWithText("1.0 (dev)", substring = true).assertExists()
    }

    @Test
    fun showsLoadingWhileInfoIsNull() {
        show(DevToolsUiState(info = null))

        composeTestRule.onNodeWithText("Loading", substring = true).assertIsDisplayed()
    }

    @Test
    fun tappingAction_callsOnRequest() {
        val requested = mutableListOf<DevToolsAction>()
        show(onRequest = { requested += it })

        composeTestRule.onNodeWithText("Seed sample data").performClick()
        composeTestRule.onNodeWithText("Seed pending bet").performClick()
        composeTestRule.onNodeWithText("Wipe dev database").performClick()
        composeTestRule.onNodeWithText("Reset admin PIN").performClick()

        assertThat(requested).containsExactly(
            DevToolsAction.SEED_SAMPLE_DATA,
            DevToolsAction.SEED_PENDING_BET,
            DevToolsAction.WIPE,
            DevToolsAction.RESET_ADMIN_PIN,
        ).inOrder()
    }

    @Test
    fun confirmationDialog_confirmAndCancel() {
        var confirmed = 0
        var dismissed = 0
        show(
            state = DevToolsUiState(info = info, pendingConfirmation = DevToolsAction.WIPE),
            onConfirm = { confirmed++ },
            onDismiss = { dismissed++ },
        )

        composeTestRule.onNodeWithText("Wipe dev database?").assertIsDisplayed()
        composeTestRule.onNodeWithText("Wipe").performClick()
        composeTestRule.onNodeWithText("Cancel").performClick()

        assertThat(confirmed).isEqualTo(1)
        assertThat(dismissed).isEqualTo(1)
    }

    @Test
    fun recoveryCodeDialog_shownAndDismissed() {
        var shown = 0
        show(
            state = DevToolsUiState(info = info, recoveryCode = "ABCD-1234"),
            onRecoveryCodeShown = { shown++ },
        )

        composeTestRule.onNodeWithText("Recovery code").assertIsDisplayed()
        composeTestRule.onNodeWithText("ABCD-1234").assertIsDisplayed()
        composeTestRule.onNodeWithText("Done").performClick()

        assertThat(shown).isEqualTo(1)
    }

    @Test
    fun busy_disablesActions() {
        show(DevToolsUiState(info = info, busy = true))

        composeTestRule.onNodeWithText("Seed sample data").assertIsNotEnabled()
        composeTestRule.onNodeWithText("Wipe dev database").assertIsNotEnabled()
    }

    @Test
    fun idle_enablesActions() {
        show()

        composeTestRule.onNodeWithText("Seed sample data").assertIsEnabled()
    }

    @Test
    fun busy_disablesBackButton() {
        var backs = 0
        show(DevToolsUiState(info = info, busy = true), onNavigateBack = { backs++ })

        composeTestRule.onNodeWithContentDescription("Back").assertIsNotEnabled()
        composeTestRule.onNodeWithContentDescription("Back").performClick()

        assertThat(backs).isEqualTo(0)
    }

    @Test
    fun idle_backButtonNavigates() {
        var backs = 0
        show(onNavigateBack = { backs++ })

        composeTestRule.onNodeWithContentDescription("Back").performClick()

        assertThat(backs).isEqualTo(1)
    }
}
