package com.betcoin.ui.bethistory

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.betcoin.data.model.BetStatus
import com.betcoin.ui.theme.BetCoinTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose UI tests for [BetHistoryContent].
 */
@RunWith(AndroidJUnit4::class)
class BetHistoryScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun row(
        id: Long = 1,
        prompt: String = "Who wins?",
        status: BetStatus = BetStatus.ACTIVE,
        count: Int = 3,
        pot: Long = 175,
    ) = BetHistoryRow(
        betId = id,
        prompt = prompt,
        status = status,
        createdAt = 1_700_000_000_000L,
        participantCount = count,
        pot = pot,
    )

    private fun show(
        uiState: BetHistoryUiState,
        onNavigateBack: () -> Unit = {},
        onFilterSelected: (BetHistoryFilter) -> Unit = {},
        onBetClick: (Long) -> Unit = {},
    ) {
        composeTestRule.setContent {
            BetCoinTheme {
                BetHistoryContent(
                    uiState = uiState,
                    onNavigateBack = onNavigateBack,
                    onFilterSelected = onFilterSelected,
                    onBetClick = onBetClick,
                )
            }
        }
    }

    @Test
    fun showsTitleAndFourTabs() {
        show(BetHistoryUiState(isLoading = false))

        listOf("Bet History", "All", "Active", "Resolved", "Cancelled").forEach {
            composeTestRule.onNodeWithText(it).assertIsDisplayed()
        }
    }

    @Test
    fun rowShowsPromptStatusCountAndPot() {
        show(BetHistoryUiState(isLoading = false, rows = listOf(row())))

        composeTestRule.onNodeWithText("Who wins?").assertIsDisplayed()
        composeTestRule.onNodeWithText("ACTIVE").assertIsDisplayed()
        composeTestRule.onNodeWithText("3 participants", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Pot: 175", substring = true).assertIsDisplayed()
    }

    @Test
    fun singleParticipant_usesSingular() {
        show(BetHistoryUiState(isLoading = false, rows = listOf(row(count = 1))))

        composeTestRule.onNodeWithText("1 participant", substring = true).assertIsDisplayed()
    }

    @Test
    fun emptyState_showsPerFilterMessage() {
        show(BetHistoryUiState(isLoading = false, filter = BetHistoryFilter.ACTIVE))
        composeTestRule.onNodeWithText("No active bets").assertIsDisplayed()
    }

    @Test
    fun emptyState_allFilter_showsNoBetsYet() {
        show(BetHistoryUiState(isLoading = false, filter = BetHistoryFilter.ALL))
        composeTestRule.onNodeWithText("No bets yet").assertIsDisplayed()
    }

    @Test
    fun tappingTab_invokesOnFilterSelected() {
        var selected: BetHistoryFilter? = null
        show(BetHistoryUiState(isLoading = false), onFilterSelected = { selected = it })

        composeTestRule.onNodeWithText("Resolved").performClick()

        assertThat(selected).isEqualTo(BetHistoryFilter.RESOLVED)
    }

    @Test
    fun tappingRow_invokesOnBetClickWithId() {
        var clicked: Long? = null
        show(
            BetHistoryUiState(isLoading = false, rows = listOf(row(id = 7))),
            onBetClick = { clicked = it },
        )

        composeTestRule.onNodeWithTag("bet_history_row_7").performClick()

        assertThat(clicked).isEqualTo(7L)
    }

    @Test
    fun backButton_invokesCallback() {
        var backClicked = false
        show(BetHistoryUiState(isLoading = false), onNavigateBack = { backClicked = true })

        composeTestRule.onNodeWithText("Back").assertIsDisplayed().performClick()

        assertThat(backClicked).isTrue()
    }

    @Test
    fun loading_showsNoEmptyMessage() {
        show(BetHistoryUiState(isLoading = true))

        composeTestRule.onNodeWithText("No bets yet").assertDoesNotExist()
    }

    @Test
    fun error_showsMessageAndHidesTabs() {
        show(BetHistoryUiState(isLoading = false, isError = true))

        composeTestRule.onNodeWithText("Couldn't load bets.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Resolved").assertDoesNotExist()
        composeTestRule.onNodeWithText("No bets yet").assertDoesNotExist()
    }
}
