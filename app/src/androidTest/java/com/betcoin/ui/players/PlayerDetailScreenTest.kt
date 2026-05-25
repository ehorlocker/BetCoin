package com.betcoin.ui.players

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.betcoin.data.database.entity.User
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for the [PlayerDetailScreen] composable.
 */
@RunWith(AndroidJUnit4::class)
class PlayerDetailScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun playerDetailScreen_showsUsername() {
        composeTestRule.setContent {
            PlayerDetailScreen(uiState = PlayerDetailUiState.Success(testUser()))
        }
        composeTestRule.onNodeWithText("Alice").assertIsDisplayed()
    }

    @Test
    fun playerDetailScreen_showsStats() {
        composeTestRule.setContent {
            PlayerDetailScreen(uiState = PlayerDetailUiState.Success(testUser()))
        }
        composeTestRule.onNodeWithText("Total Earnings").assertIsDisplayed()
        composeTestRule.onNodeWithText("5000").assertIsDisplayed()
        composeTestRule.onNodeWithText("Total Losses").assertIsDisplayed()
        composeTestRule.onNodeWithText("Bailouts").assertIsDisplayed()
        composeTestRule.onNodeWithText("Total Debt").assertIsDisplayed()
    }

    @Test
    fun playerDetailScreen_loadingState() {
        composeTestRule.setContent {
            PlayerDetailScreen(uiState = PlayerDetailUiState.Loading)
        }
        composeTestRule.onNodeWithText("Loading...").assertIsDisplayed()
    }

    @Test
    fun playerDetailScreen_notFoundState() {
        composeTestRule.setContent {
            PlayerDetailScreen(uiState = PlayerDetailUiState.NotFound)
        }
        composeTestRule.onNodeWithText("Player not found").assertIsDisplayed()
    }

    @Test
    fun playerDetailScreen_backButton_invokesCallback() {
        var backClicked = false
        composeTestRule.setContent {
            PlayerDetailScreen(
                uiState = PlayerDetailUiState.Success(testUser()),
                onNavigateBack = { backClicked = true },
            )
        }
        composeTestRule.onNodeWithText("Back").assertIsDisplayed().performClick()
        assertTrue("Back callback was not invoked", backClicked)
    }

    private fun testUser(): User = User(
        id = 42L,
        username = "Alice",
        pinHash = "",
        balance = 3000,
        totalWins = 5,
        totalLosses = 2,
        totalEarnings = 5000,
        totalLost = 2000,
        bailoutCount = 1,
        totalDebt = 1000,
        createdAt = 0L,
    )
}
