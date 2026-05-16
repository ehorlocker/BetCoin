package com.betcoin.ui.bethistory

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose UI tests for [BetHistoryScreen].
 */
@RunWith(AndroidJUnit4::class)
class BetHistoryScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun betHistoryScreen_showsTitle() {
        composeTestRule.setContent {
            BetHistoryScreen()
        }

        composeTestRule.onNodeWithText("Bet History - Coming Soon").assertIsDisplayed()
    }

    @Test
    fun betHistoryScreen_backButton_invokesCallback() {
        var backClicked = false

        composeTestRule.setContent {
            BetHistoryScreen(onNavigateBack = { backClicked = true })
        }

        composeTestRule.onNodeWithText("Back").assertIsDisplayed().performClick()
        assert(backClicked) { "Back callback was not invoked" }
    }
}
