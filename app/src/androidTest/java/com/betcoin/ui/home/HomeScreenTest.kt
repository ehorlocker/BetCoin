package com.betcoin.ui.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.betcoin.testing.FakeBetRepository
import com.betcoin.testing.FakeUserRepository
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose UI tests for [HomeScreen].
 */
@RunWith(AndroidJUnit4::class)
class HomeScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun homeScreen_showsTitleAndCounts() {
        val users = FakeUserRepository().apply {
            addPlayer("Alice")
            addPlayer("Bob")
        }
        val homeViewModel = HomeViewModel(FakeBetRepository(users), users)
        composeTestRule.setContent {
            HomeScreen(viewModel = homeViewModel)
        }

        composeTestRule.onNodeWithText("BetCoin").assertIsDisplayed()
        composeTestRule.onNodeWithText("Active bets: 0").assertIsDisplayed()
        composeTestRule.onNodeWithText("Players: 2").assertIsDisplayed()
    }

    @Test
    fun homeScreen_newBetButton_triggersCallback() {
        val users = FakeUserRepository().apply {
            addPlayer("Alice")
            addPlayer("Bob")
        }
        var newBetClicked = false
        val homeViewModel = HomeViewModel(FakeBetRepository(users), users)
        composeTestRule.setContent {
            HomeScreen(
                viewModel = homeViewModel,
                onNewBet = { newBetClicked = true },
            )
        }

        composeTestRule.onNodeWithText("New Bet").performClick()
        assert(newBetClicked) { "New Bet callback was not invoked" }
    }

    @Test
    fun homeScreen_leaderboardButton_triggersCallback() {
        val users = FakeUserRepository()
        var leaderboardClicked = false
        val homeViewModel = HomeViewModel(FakeBetRepository(users), users)
        composeTestRule.setContent {
            HomeScreen(
                viewModel = homeViewModel,
                onLeaderboard = { leaderboardClicked = true },
            )
        }

        composeTestRule.onNodeWithText("Leaderboard").performClick()
        assert(leaderboardClicked) { "Leaderboard callback was not invoked" }
    }

    @Test
    fun homeScreen_betHistoryButton_triggersCallback() {
        val users = FakeUserRepository()
        var betHistoryClicked = false
        val homeViewModel = HomeViewModel(FakeBetRepository(users), users)
        composeTestRule.setContent {
            HomeScreen(
                viewModel = homeViewModel,
                onBetHistory = { betHistoryClicked = true },
            )
        }

        composeTestRule.onNodeWithText("Bet History").performClick()
        assert(betHistoryClicked) { "Bet History callback was not invoked" }
    }

    @Test
    fun gearIcon_callsOnSettings() {
        val users = FakeUserRepository()
        val homeViewModel = HomeViewModel(FakeBetRepository(users), users)
        var settingsClicked = false
        composeTestRule.setContent {
            HomeScreen(viewModel = homeViewModel, onSettings = { settingsClicked = true })
        }

        composeTestRule.onNodeWithContentDescription("Settings").performClick()
        assert(settingsClicked) { "Settings callback was not invoked" }
    }
}
