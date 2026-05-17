package com.betcoin.ui.players

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for the [PlayerDetailScreen] placeholder composable.
 */
@RunWith(AndroidJUnit4::class)
class PlayerDetailScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun playerDetailScreen_showsTitle() {
        composeTestRule.setContent {
            PlayerDetailScreen(userId = 1L)
        }
        composeTestRule.onNodeWithText("Player Detail - Coming Soon").assertIsDisplayed()
    }

    @Test
    fun playerDetailScreen_backButton_invokesCallback() {
        var backClicked = false
        composeTestRule.setContent {
            PlayerDetailScreen(userId = 1L, onNavigateBack = { backClicked = true })
        }
        composeTestRule.onNodeWithText("Back").assertIsDisplayed().performClick()
        assert(backClicked) { "Back callback was not invoked" }
    }
}
