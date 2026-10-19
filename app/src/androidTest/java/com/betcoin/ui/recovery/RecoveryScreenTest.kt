package com.betcoin.ui.recovery

import com.betcoin.data.model.ThemePreference
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.betcoin.ui.theme.BetCoinTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Compose UI tests for the stateless [RecoveryScreen]. */
@RunWith(AndroidJUnit4::class)
class RecoveryScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun show(
        state: RecoveryUiState,
        onReturnToBet: (Long) -> Unit = {},
        onDiscard: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            BetCoinTheme(themePreference = ThemePreference.DARK) {
                RecoveryScreen(
                    uiState = state,
                    onReturnToBet = onReturnToBet,
                    onDiscard = onDiscard,
                )
            }
        }
    }

    private fun pending(count: Int = 3, isDiscarding: Boolean = false) =
        RecoveryUiState.Pending(7L, "Who wins Smash?", count, isDiscarding)

    @Test
    fun pending_showsHeadingPromptAndCount() {
        show(pending())
        composeTestRule.onNodeWithText("You have an unfinished bet").assertIsDisplayed()
        composeTestRule.onNodeWithText("Who wins Smash?").assertIsDisplayed()
        composeTestRule.onNodeWithText("3 participants added so far").assertIsDisplayed()
    }

    @Test
    fun pending_singleParticipant_usesSingular() {
        show(pending(count = 1))
        composeTestRule.onNodeWithText("1 participant added so far").assertIsDisplayed()
    }

    @Test
    fun returnToBet_invokesCallbackWithBetId() {
        var received: Long? = null
        show(pending(), onReturnToBet = { received = it })
        composeTestRule.onNodeWithText("Return to Bet").performClick()
        assertEquals(7L, received)
    }

    @Test
    fun discard_invokesCallback() {
        var calls = 0
        show(pending(), onDiscard = { calls++ })
        composeTestRule.onNodeWithText("Discard Bet").performClick()
        assertEquals(1, calls)
    }

    @Test
    fun discarding_disablesButtons() {
        show(pending(isDiscarding = true))
        composeTestRule.onNodeWithText("Return to Bet").assertIsNotEnabled()
        composeTestRule.onNodeWithText("Discard Bet").assertIsNotEnabled()
    }

    @Test
    fun loading_showsProgress() {
        show(RecoveryUiState.Loading)
        composeTestRule.onNodeWithTag("recovery_loading").assertExists()
        composeTestRule.onNodeWithText("Return to Bet").assertDoesNotExist()
    }

    @Test
    fun noBackOrNavigationAffordances() {
        show(pending())
        composeTestRule.onNodeWithContentDescription("Back").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("Navigate up").assertDoesNotExist()
    }
}
