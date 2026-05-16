package com.betcoin.ui.leaderboard

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose UI tests for [LeaderboardScreen].
 */
@RunWith(AndroidJUnit4::class)
class LeaderboardScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun leaderboardScreen_showsTitle() {
        composeTestRule.setContent {
            LeaderboardScreen()
        }

        composeTestRule.onNodeWithText("Leaderboard - Coming Soon").assertIsDisplayed()
    }

    @Test
    fun leaderboardScreen_backButton_invokesCallback() {
        var backClicked = false

        composeTestRule.setContent {
            LeaderboardScreen(onNavigateBack = { backClicked = true })
        }

        composeTestRule.onNodeWithText("Back").assertIsDisplayed().performClick()
        assert(backClicked) { "Back callback was not invoked" }
    }
}
