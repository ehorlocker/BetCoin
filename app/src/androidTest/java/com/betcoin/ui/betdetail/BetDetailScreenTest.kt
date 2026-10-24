package com.betcoin.ui.betdetail

import com.betcoin.data.model.ThemePreference
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
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
        isCapped: Boolean = false,
        reopenBlockedReason: String? = null,
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
        isCapped = isCapped,
        reopenBlockedReason = reopenBlockedReason,
    )

    private fun row(id: Long, name: String, pick: String, wager: Long, result: ParticipantResult, net: Long? = null) =
        ParticipantRow(id, userId = id, name = name, isDeletedUser = false, pick = pick, wager = wager, result = result, net = net)

    private fun show(
        state: BetDetailUiState,
        onNavigateBack: () -> Unit = {},
        actionState: BetActionState = BetActionState(),
        onOutcomePicked: (Long) -> Unit = {},
        onRepeat: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            BetCoinTheme(themePreference = ThemePreference.DARK) {
                BetDetailContent(
                    uiState = state,
                    actionState = actionState,
                    onNavigateBack = onNavigateBack,
                    onOutcomePicked = onOutcomePicked,
                    onRepeat = onRepeat,
                )
            }
        }
    }

    private fun twoRows(result: ParticipantResult) = listOf(
        row(1, "Alice", "Jay", 150, result),
        row(2, "Bob", "Kay", 200, result),
    )

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
        composeTestRule.onNodeWithText("Unmatched wagers refunded").assertDoesNotExist()
    }

    @Test
    fun resolvedCappedBet_showsUnmatchedWagersRefunded() {
        show(
            BetDetailUiState.Success(
                detail(
                    BetStatus.RESOLVED,
                    listOf(
                        row(1, "Alice", "Jay", 100, ParticipantResult.WIN, net = 100),
                        row(2, "Bob", "Kay", 200, ParticipantResult.LOSS, net = -100),
                    ),
                    winningOutcomeId = 10,
                    isCapped = true,
                ),
            ),
        )

        scrollTo("Bob")
        composeTestRule.onNodeWithText("\u2212100").assertIsDisplayed()
        scrollTo("Unmatched wagers refunded")
        composeTestRule.onNodeWithText("Unmatched wagers refunded").assertIsDisplayed()
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

    @Test
    fun activeBet_showsResolveAndCancel() {
        show(BetDetailUiState.Success(detail(BetStatus.ACTIVE, twoRows(ParticipantResult.PENDING))))

        // Scroll to each button: on a smaller screen (the CI emulator) Cancel sits below Resolve.
        scrollTo("Resolve")
        composeTestRule.onNodeWithTag(BET_DETAIL_RESOLVE_TAG).assertIsDisplayed()
        composeTestRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag(BET_DETAIL_CANCEL_TAG))
        composeTestRule.onNodeWithTag(BET_DETAIL_CANCEL_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(BET_DETAIL_REOPEN_TAG).assertDoesNotExist()
    }

    @Test
    fun resolvedBet_showsReopenEnabled() {
        show(
            BetDetailUiState.Success(
                detail(BetStatus.RESOLVED, twoRows(ParticipantResult.LOSS), winningOutcomeId = 10),
            ),
        )

        scrollTo("Reopen Bet")
        composeTestRule.onNodeWithTag(BET_DETAIL_REOPEN_TAG).assertIsDisplayed().assertIsEnabled()
        composeTestRule.onNodeWithTag(BET_DETAIL_RESOLVE_TAG).assertDoesNotExist()
        composeTestRule.onNodeWithTag(BET_DETAIL_REOPEN_REASON_TAG).assertDoesNotExist()
    }

    @Test
    fun resolvedBet_showsRepeatEnabled() {
        show(
            BetDetailUiState.Success(
                detail(BetStatus.RESOLVED, twoRows(ParticipantResult.LOSS), winningOutcomeId = 10),
            ),
        )

        scrollTo("Repeat Bet")
        composeTestRule.onNodeWithTag(BET_DETAIL_REPEAT_TAG).assertIsDisplayed().assertIsEnabled()
    }

    @Test
    fun cancelledBet_showsRepeatEnabled() {
        show(BetDetailUiState.Success(detail(BetStatus.CANCELLED, twoRows(ParticipantResult.REFUNDED))))

        scrollTo("Repeat Bet")
        composeTestRule.onNodeWithTag(BET_DETAIL_REPEAT_TAG).assertIsDisplayed().assertIsEnabled()
    }

    @Test
    fun activeBet_hidesRepeat() {
        show(BetDetailUiState.Success(detail(BetStatus.ACTIVE, twoRows(ParticipantResult.PENDING))))

        scrollTo("Resolve")
        composeTestRule.onNodeWithTag(BET_DETAIL_REPEAT_TAG).assertDoesNotExist()
    }

    @Test
    fun repeatClick_invokesCallback() {
        var repeated = 0
        show(
            BetDetailUiState.Success(detail(BetStatus.RESOLVED, twoRows(ParticipantResult.LOSS), winningOutcomeId = 10)),
            onRepeat = { repeated++ },
        )

        scrollTo("Repeat Bet")
        composeTestRule.onNodeWithTag(BET_DETAIL_REPEAT_TAG).performClick()

        assertThat(repeated).isEqualTo(1)
    }

    @Test
    fun repeatIsDisabledWhileBusy() {
        show(
            BetDetailUiState.Success(detail(BetStatus.RESOLVED, twoRows(ParticipantResult.LOSS), winningOutcomeId = 10)),
            actionState = BetActionState(isBusy = true),
        )

        scrollTo("Repeat Bet")
        composeTestRule.onNodeWithTag(BET_DETAIL_REPEAT_TAG).assertIsNotEnabled()
    }

    @Test
    fun repeatStaysEnabledWhenReopenIsBlocked() {
        show(
            BetDetailUiState.Success(
                detail(
                    BetStatus.CANCELLED,
                    twoRows(ParticipantResult.REFUNDED),
                    reopenBlockedReason = "Can't reopen: Kim was deleted",
                ),
            ),
        )

        scrollTo("Repeat Bet")
        composeTestRule.onNodeWithTag(BET_DETAIL_REPEAT_TAG).assertIsDisplayed().assertIsEnabled()
        composeTestRule.onNodeWithTag(BET_DETAIL_REOPEN_TAG).assertIsNotEnabled()
    }

    @Test
    fun deletedParticipant_disablesReopenWithReason() {
        show(
            BetDetailUiState.Success(
                detail(
                    BetStatus.CANCELLED,
                    twoRows(ParticipantResult.REFUNDED),
                    reopenBlockedReason = "Can't reopen: Kim was deleted",
                ),
            ),
        )

        scrollTo("Reopen Bet")
        composeTestRule.onNodeWithTag(BET_DETAIL_REOPEN_TAG).assertIsNotEnabled()
        composeTestRule.onNodeWithText("Can't reopen: Kim was deleted").assertIsDisplayed()
    }

    @Test
    fun outcomePicker_continueSendsPickedId() {
        var picked: Long? = null
        show(
            BetDetailUiState.Success(detail(BetStatus.ACTIVE, twoRows(ParticipantResult.PENDING))),
            actionState = BetActionState(pickingOutcome = true),
            onOutcomePicked = { picked = it },
        )

        composeTestRule.onNodeWithText("Pick the winning outcome").assertIsDisplayed()
        composeTestRule.onNodeWithText("Continue").assertIsNotEnabled()
        composeTestRule.onNode(hasText("Kay") and hasClickAction()).performClick()
        composeTestRule.onNodeWithText("Continue").assertIsEnabled().performClick()

        assertThat(picked).isEqualTo(11L)
    }

    @Test
    fun actionError_isShown() {
        show(
            BetDetailUiState.Success(detail(BetStatus.ACTIVE, twoRows(ParticipantResult.PENDING))),
            actionState = BetActionState(errorMessage = "This bet has changed. Nothing was updated."),
        )

        composeTestRule.onNode(hasScrollAction())
            .performScrollToNode(hasTestTag(BET_DETAIL_ACTION_ERROR_TAG))
        composeTestRule.onNodeWithTag(BET_DETAIL_ACTION_ERROR_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("This bet has changed. Nothing was updated.").assertIsDisplayed()
    }
}
