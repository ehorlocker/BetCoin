package com.betcoin.ui.leaderboard

import com.betcoin.testing.FakeUserRepository
import com.betcoin.testing.FakeAvatarStore
import com.betcoin.data.model.ThemePreference
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.betcoin.ui.components.formatCoins
import com.betcoin.ui.theme.BetCoinTheme
import com.google.common.truth.Truth.assertThat
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

    private val repository = FakeUserRepository()

    private fun showScreen(
        onNavigateBack: () -> Unit = {},
        onPlayerClick: (Long) -> Unit = {},
    ) {
        val viewModel = LeaderboardViewModel(repository, FakeAvatarStore())
        composeTestRule.setContent {
            BetCoinTheme(themePreference = ThemePreference.DARK) {
                LeaderboardScreen(
                    viewModel = viewModel,
                    onNavigateBack = onNavigateBack,
                    onPlayerClick = onPlayerClick,
                )
            }
        }
    }

    private fun topOf(text: String): Float =
        composeTestRule.onNodeWithText(text).fetchSemanticsNode().boundsInRoot.top

    @Test
    fun showsColumnHeaders() {
        repository.addPlayer("Amy")
        showScreen()

        composeTestRule.onNodeWithText("Rank").assertIsDisplayed()
        composeTestRule.onNodeWithText("Username").assertIsDisplayed()
        composeTestRule.onNodeWithText("Net Worth").assertIsDisplayed()
        composeTestRule.onNodeWithText("W-L").assertIsDisplayed()
    }

    @Test
    fun rows_orderedByNetWorth() {
        repository.addPlayer("Amy", balance = 1000)
        repository.addPlayer("Bob", balance = 3000, totalDebt = 1000)
        repository.addPlayer("Cal", balance = 500)
        showScreen()

        assertThat(topOf("Bob")).isLessThan(topOf("Amy"))
        assertThat(topOf("Amy")).isLessThan(topOf("Cal"))
        composeTestRule.onNodeWithText(formatCoins(2000)).assertIsDisplayed()
    }

    @Test
    fun negativeNetWorth_displaysMinusSign() {
        repository.addPlayer("Dan", balance = 100, totalDebt = 1000)
        showScreen()

        composeTestRule.onNodeWithText(formatCoins(-900)).assertIsDisplayed()
    }

    @Test
    fun winLoss_displayed() {
        repository.addPlayer("Eve", totalWins = 4, totalLosses = 2)
        showScreen()

        composeTestRule.onNodeWithText("4-2").assertIsDisplayed()
    }

    @Test
    fun longUsername_keepsAllColumnsVisible() {
        repository.addPlayer("A".repeat(20), balance = 1234567)
        showScreen()

        composeTestRule.onNodeWithText(formatCoins(1_234_567)).assertIsDisplayed()
        composeTestRule.onNodeWithText("0-0").assertIsDisplayed()
    }

    @Test
    fun emptyState_showsMessage() {
        showScreen()

        composeTestRule.onNodeWithText("No players yet. Add players from Manage Players.").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Rank").assertCountEquals(0)
    }

    @Test
    fun tapRow_opensPlayerDetailForThatPlayer() {
        repository.addPlayer("Alice")
        val bobId = repository.addPlayer("Bob")
        var clickedId: Long? = null
        showScreen(onPlayerClick = { clickedId = it })

        composeTestRule.onNodeWithText("Bob").performClick()

        assertThat(clickedId).isEqualTo(bobId)
    }

    @Test
    fun backButton_invokesCallback() {
        var backClicked = false
        showScreen(onNavigateBack = { backClicked = true })

        composeTestRule.onNodeWithText("Back").assertIsDisplayed().performClick()

        assertThat(backClicked).isTrue()
    }
}
