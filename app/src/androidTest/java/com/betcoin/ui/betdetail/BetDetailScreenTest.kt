package com.betcoin.ui.betdetail

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.betcoin.data.model.BetStatus
import com.betcoin.ui.theme.BetCoinTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose UI tests for [BetDetailContent], one per bet status plus loading, missing and error.
 */
@RunWith(AndroidJUnit4::class)
class BetDetailScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val outcomes = listOf(
        OutcomeRow(id = 10, text = "Jay", isWinner = false),
        OutcomeRow(id = 11, text = "Kay", isWinner = false),
        OutcomeRow(id = 12, text = "Nobody", isWinner = false),
    )

    private fun detail(
        status: BetStatus,
        participants: List<ParticipantRow>,
        winningOutcomeId: Long? = null,
        isPush: Boolean = false,
    ) = BetDetail(
        prompt = "Who wins the race?",
        status = status,
        createdAt = 1_700_000_000_000L,
        resolvedAt = if (status == BetStatus.ACTIVE) null else 1_700_000_500_000L,
        outcomes = outcomes.map { it.copy(isWinner = it.id == winningOutcomeId) },
        participants = participants,
        potSize = participants.sumOf { it.wager },
        winningOutcome = outcomes.firstOrNull { it.id == winningOutcomeId }?.text,
        isPush = isPush,
    )

    private fun row(id: Long, name: String, pick: String, wager: Long, result: ParticipantResult, net: Long? = null) =
        ParticipantRow(id, name, isDeletedUser = false, pick = pick, wager = wager, result = result, net = net)

    private fun show(state: BetDetailUiState, onNavigateBack: () -> Unit = {}) {
        composeTestRule.setContent {
            BetCoinTheme {
                BetDetailContent(uiState = state, onNavigateBack = onNavigateBack)
            }
        }
    }

    /** Asserts the node tagged [tag] has a descendant showing [text]. */
    private fun assertTagContains(tag: String, text: String) {
        composeTestRule.onNode(hasTestTag(tag) and hasAnyDescendant(hasText(text))).assertExists()
    }

    private fun scrollTo(text: String) {
        composeTestRule.onNode(hasScrollAction()).performScrollToNode(hasText(text, substring = true))
    }

    @Test
    fun activeBet_showsHeaderOutcomesPendingParticipantsAndPot() {
        show(
            BetDetailUiState.Success(
                detail(
                    BetStatus.ACTIVE,
                    listOf(
                        row(1, "Alice", "Jay", 150, ParticipantResult.PENDING),
                        row(2, "Bob", "Kay", 200, ParticipantResult.PENDING),
                    ),
                ),
            ),
        )

        composeTestRule.onNodeWithText("Who wins the race?").assertIsDisplayed()
        assertTagContains("status_badge", "ACTIVE")
        composeTestRule.onNodeWithText("Created", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Jay").assertIsDisplayed()
        composeTestRule.onNodeWithText("Kay").assertIsDisplayed()
        composeTestRule.onNodeWithTag("winning_outcome").assertDoesNotExist()

        scrollTo("Alice")
        assertTagContains("participant_1", "PENDING")
        composeTestRule.onNodeWithText("Picked Jay").assertIsDisplayed()
        composeTestRule.onNodeWithText("Wager 150").assertIsDisplayed()

        scrollTo("Pot:")
        composeTestRule.onNodeWithText("Pot: 350 BetCoin").assertIsDisplayed()
        composeTestRule.onNodeWithText("2 participants").assertIsDisplayed()
        composeTestRule.onNodeWithText("Winning outcome", substring = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("Cancelled", substring = true).assertDoesNotExist()
    }

    @Test
    fun resolvedBet_highlightsWinnerAndShowsResultsNetAndResolution() {
        show(
            BetDetailUiState.Success(
                detail(
                    BetStatus.RESOLVED,
                    listOf(
                        row(1, "Alice", "Jay", 150, ParticipantResult.WIN, net = 200),
                        row(2, "Bob", "Kay", 200, ParticipantResult.LOSS, net = -200),
                    ),
                    winningOutcomeId = 10,
                ),
            ),
        )

        assertTagContains("status_badge", "RESOLVED")
        assertTagContains("winning_outcome", "Jay")
        assertTagContains("winning_outcome", "Winner")

        scrollTo("Bob")
        composeTestRule.onNodeWithText("WIN").assertIsDisplayed()
        composeTestRule.onNodeWithText("+200").assertIsDisplayed()
        composeTestRule.onNodeWithText("LOSS").assertIsDisplayed()
        composeTestRule.onNodeWithText("\u2212200").assertIsDisplayed()

        scrollTo("Winning outcome")
        composeTestRule.onNodeWithText("Winning outcome: Jay").assertIsDisplayed()
        composeTestRule.onNodeWithText("Resolved", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("No winners", substring = true).assertDoesNotExist()
    }

    @Test
    fun resolvedPush_showsNoWinnersRefundAndZeroNet() {
        show(
            BetDetailUiState.Success(
                detail(
                    BetStatus.RESOLVED,
                    listOf(
                        row(1, "Alice", "Jay", 150, ParticipantResult.PUSH, net = 0),
                        row(2, "Bob", "Kay", 200, ParticipantResult.PUSH, net = 0),
                    ),
                    winningOutcomeId = 12,
                    isPush = true,
                ),
            ),
        )

        assertTagContains("winning_outcome", "Nobody")
        scrollTo("Bob")
        assertThat(composeTestRule.onAllNodes(hasText("\u00B10")).fetchSemanticsNodes()).hasSize(2)

        scrollTo("No winners")
        composeTestRule.onNodeWithText("Winning outcome: Nobody").assertIsDisplayed()
        composeTestRule.onNodeWithText("No winners — all wagers refunded").assertIsDisplayed()
    }

    @Test
    fun cancelledBet_showsCancelledRefundAndNoWinner() {
        show(
            BetDetailUiState.Success(
                detail(
                    BetStatus.CANCELLED,
                    listOf(
                        row(1, "Alice", "Jay", 150, ParticipantResult.REFUNDED),
                        row(2, "Bob", "Kay", 200, ParticipantResult.REFUNDED),
                    ),
                ),
            ),
        )

        assertTagContains("status_badge", "CANCELLED")
        composeTestRule.onNodeWithTag("winning_outcome").assertDoesNotExist()

        scrollTo("Cancelled —")
        composeTestRule.onNodeWithText("Cancelled — all wagers refunded").assertIsDisplayed()
        composeTestRule.onNodeWithText("Winning outcome", substring = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("+", substring = true).assertDoesNotExist()
    }

    @Test
    fun deletedParticipant_showsUsernameSnapshot() {
        show(
            BetDetailUiState.Success(
                detail(
                    BetStatus.RESOLVED,
                    listOf(
                        row(1, "Carol", "Jay", 150, ParticipantResult.WIN, net = 200).copy(isDeletedUser = true),
                        row(2, "Bob", "Kay", 200, ParticipantResult.LOSS, net = -200),
                    ),
                    winningOutcomeId = 10,
                ),
            ),
        )

        scrollTo("Carol")
        composeTestRule.onNodeWithText("Carol (deleted)").assertIsDisplayed()
    }

    @Test
    fun loading_showsNoBetContent() {
        show(BetDetailUiState.Loading)

        composeTestRule.onNodeWithText("Bet Detail").assertIsDisplayed()
        composeTestRule.onNodeWithTag("status_badge").assertDoesNotExist()
    }

    @Test
    fun missingBet_showsNotFoundMessage() {
        show(BetDetailUiState.NotFound)

        composeTestRule.onNodeWithText("This bet no longer exists.").assertIsDisplayed()
    }

    @Test
    fun error_showsErrorMessage() {
        show(BetDetailUiState.Error)

        composeTestRule.onNodeWithText("Couldn't load this bet.").assertIsDisplayed()
    }

    @Test
    fun backButton_navigatesBack() {
        var navigatedBack = false
        show(BetDetailUiState.NotFound, onNavigateBack = { navigatedBack = true })

        composeTestRule.onNodeWithText("Back").performClick()

        assertThat(navigatedBack).isTrue()
    }
}
