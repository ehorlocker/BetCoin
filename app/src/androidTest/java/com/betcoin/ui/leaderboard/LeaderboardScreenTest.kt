package com.betcoin.ui.leaderboard

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LeaderboardScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun allColumnHeadersDisplayed() {
        composeTestRule.setContent {
            LeaderboardScreen(uiState = LeaderboardUiState.Success(emptyList()))
        }
        composeTestRule.onNodeWithText("Rank").assertIsDisplayed()
        composeTestRule.onNodeWithText("Username").assertIsDisplayed()
        composeTestRule.onNodeWithText("Balance").assertIsDisplayed()
        composeTestRule.onNodeWithText("W-L").assertIsDisplayed()
        composeTestRule.onNodeWithText("Earnings").assertIsDisplayed()
        composeTestRule.onNodeWithText("Losses").assertIsDisplayed()
        composeTestRule.onNodeWithText("Bailouts").assertIsDisplayed()
        composeTestRule.onNodeWithText("Debt").assertIsDisplayed()
    }

    @Test
    fun rendersPlayerUsernames() {
        val items = listOf(
            leaderboardItem(rank = 1, username = "Alice"),
            leaderboardItem(rank = 2, username = "Bob"),
            leaderboardItem(rank = 3, username = "Carol"),
        )
        composeTestRule.setContent {
            LeaderboardScreen(uiState = LeaderboardUiState.Success(items))
        }
        composeTestRule.onNodeWithText("Alice").assertIsDisplayed()
        composeTestRule.onNodeWithText("Bob").assertIsDisplayed()
        composeTestRule.onNodeWithText("Carol").assertIsDisplayed()
    }

    @Test
    fun loadingState_showsLoadingText() {
        composeTestRule.setContent {
            LeaderboardScreen(uiState = LeaderboardUiState.Loading)
        }
        composeTestRule.onNodeWithText("Loading...").assertIsDisplayed()
    }

    @Test
    fun rowTap_invokesCallbackWithUserId() {
        var clickedUserId: Long? = null
        val items = listOf(
            leaderboardItem(rank = 1, username = "Alice", userId = 42L),
        )
        composeTestRule.setContent {
            LeaderboardScreen(
                uiState = LeaderboardUiState.Success(items),
                onPlayerClick = { clickedUserId = it },
            )
        }
        composeTestRule.onNodeWithText("Alice").performClick()
        assertEquals(42L, clickedUserId)
    }

    @Test
    fun backButton_invokesCallback() {
        var backClicked = false
        composeTestRule.setContent {
            LeaderboardScreen(
                uiState = LeaderboardUiState.Loading,
                onNavigateBack = { backClicked = true },
            )
        }
        composeTestRule.onNodeWithText("Back").assertIsDisplayed().performClick()
        assertTrue("Back callback was not invoked", backClicked)
    }

    @Test
    fun emptyState_showsMessage() {
        composeTestRule.setContent {
            LeaderboardScreen(uiState = LeaderboardUiState.Empty)
        }
        composeTestRule.onNodeWithText("No players yet").assertIsDisplayed()
    }

    private fun leaderboardItem(
        rank: Int,
        username: String,
        userId: Long = 0L,
    ): LeaderboardItem = LeaderboardItem(
        userId = userId,
        rank = rank,
        username = username,
        balance = 1000,
        totalWins = 0,
        totalLosses = 0,
        totalEarnings = 0,
        totalLost = 0,
        bailoutCount = 0,
        totalDebt = 0,
    )
}
