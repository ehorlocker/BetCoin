package com.betcoin.ui.admin

import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import com.betcoin.data.database.entity.Bet
import com.betcoin.data.database.entity.BetOutcome
import com.betcoin.data.database.entity.BetParticipant
import com.betcoin.data.database.entity.User
import com.betcoin.data.model.BetStatus
import com.betcoin.data.model.BetWithDetails
import com.betcoin.data.model.ParticipantWithUser
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Compose UI tests for [AdminPanelContent]. */
@RunWith(AndroidJUnit4::class)
class AdminPanelScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val noPlayerActions = AdminPlayerActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {})

    private val noBetActions = AdminBetActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})

    private val outcomeA = BetOutcome(id = 10, betId = 100, outcomeText = "Red")
    private val outcomeB = BetOutcome(id = 11, betId = 100, outcomeText = "Blue")

    private fun bet(id: Long, status: BetStatus) = BetWithDetails(
        bet = Bet(id = id, prompt = "Who wins $id?", status = status, createdAt = 0L),
        outcomes = listOf(outcomeA, outcomeB),
        participants = listOf(
            ParticipantWithUser(
                BetParticipant(id = id * 10, betId = id, userId = 1, outcomeId = 10, wagerAmount = 100),
                User(id = 1, username = "Alice", pinHash = "x", createdAt = 0L),
                outcomeA,
            ),
            ParticipantWithUser(
                BetParticipant(id = id * 10 + 1, betId = id, userId = 2, outcomeId = 11, wagerAmount = 50),
                User(id = 2, username = "Bob", pinHash = "x", createdAt = 0L),
                outcomeB,
            ),
        ),
    )

    private var changePinClicks = 0
    private var regenerateClicks = 0
    private var backCalls = 0

    private fun setContent(
        state: AdminUiState = AdminUiState(),
        players: List<User> = emptyList(),
        playerActions: AdminPlayerActions = noPlayerActions,
        bets: List<BetWithDetails> = emptyList(),
        betActions: AdminBetActions = noBetActions,
    ) {
        composeRule.setContent {
            AdminPanelContent(
                uiState = state,
                players = players,
                playerActions = playerActions,
                bets = bets,
                betActions = betActions,
                onNavigateBack = { backCalls++ },
                onChangePinClicked = { changePinClicks++ },
                onChangePinPinChanged = {},
                onChangePinConfirmChanged = {},
                onChangePinSave = {},
                onChangePinDismiss = {},
                onRegenerateClicked = { regenerateClicks++ },
                onRegenerateConfirm = {},
                onRegenerateCancel = {},
                onRecoveryCodeAcknowledged = {},
                onRecoveryCodeDone = {},
                onMessageShown = {},
            )
        }
    }

    @Test
    fun playerRow_menuDelete_callsOnDelete() {
        val user = User(id = 7, username = "Alice", pinHash = "x", createdAt = 0L)
        var deleted: User? = null
        setContent(
            players = listOf(user),
            playerActions = noPlayerActions.copy(onDelete = { deleted = it }),
        )

        composeRule.onNodeWithTag(ADMIN_PLAYER_ROW_TAG_PREFIX + user.id).performScrollTo().performClick()
        composeRule.onNodeWithText("Delete Player").performClick()

        assertEquals(user, deleted)
    }

    @Test
    fun deleteDialogWorking_confirmDisabled() {
        val user = User(id = 7, username = "Alice", pinHash = "x", createdAt = 0L)
        setContent(
            state = AdminUiState(playerDialog = PlayerDialogState.ConfirmDelete(user, isWorking = true)),
            players = listOf(user),
        )

        composeRule.onNodeWithTag(ADMIN_PLAYER_DIALOG_CONFIRM_TAG).assertIsNotEnabled()
    }

    @Test
    fun showsThreeSectionHeaders() {
        setContent()

        composeRule.onNodeWithText("Admin Panel").assertIsDisplayed()
        listOf("Player Management", "Bet Management", "App Settings").forEach {
            composeRule.onNodeWithText(it).performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun clickingSettingsRows_firesCallbacks() {
        setContent()

        composeRule.onNodeWithText("Change Admin PIN").performScrollTo().performClick()
        composeRule.onNodeWithText("Regenerate Recovery Code").performScrollTo().performClick()

        assertEquals(1, changePinClicks)
        assertEquals(1, regenerateClicks)
    }

    @Test
    fun recoveryCodeShown_doneScrollableIntoView_atLargeFontScale() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                AdminPanelContent(
                    uiState = AdminUiState(recoveryCode = RecoveryCodeDialogState.Show("ABCD-2345-EFGH")),
                    players = emptyList(),
                    playerActions = noPlayerActions,
                    bets = emptyList(),
                    betActions = noBetActions,
                    onNavigateBack = {},
                    onChangePinClicked = {},
                    onChangePinPinChanged = {},
                    onChangePinConfirmChanged = {},
                    onChangePinSave = {},
                    onChangePinDismiss = {},
                    onRegenerateClicked = {},
                    onRegenerateConfirm = {},
                    onRegenerateCancel = {},
                    onRecoveryCodeAcknowledged = {},
                    onRecoveryCodeDone = {},
                    onMessageShown = {},
                )
            }
        }

        composeRule.onNodeWithText("Done").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun recoveryCodeShown_doneDisabledUntilAcknowledged() {
        setContent(AdminUiState(recoveryCode = RecoveryCodeDialogState.Show("ABCD-2345-EFGH")))

        composeRule.onNodeWithTag(ADMIN_SETTINGS_RECOVERY_CODE_TAG).assertTextEquals("ABCD-2345-EFGH")
        composeRule.onNodeWithText("Done").assertIsNotEnabled()
    }

    @Test
    fun recoveryCodeAcknowledged_doneEnabled() {
        setContent(AdminUiState(recoveryCode = RecoveryCodeDialogState.Show("ABCD-2345-EFGH", acknowledged = true)))

        composeRule.onNodeWithText("Done").assertIsEnabled()
    }

    @Test
    fun confirmRegenerate_showsWarning() {
        setContent(AdminUiState(recoveryCode = RecoveryCodeDialogState.ConfirmRegenerate))

        composeRule.onNodeWithText("Your current recovery code will stop working.").assertIsDisplayed()
    }

    @Test
    fun changePinError_isShown() {
        setContent(AdminUiState(changePin = ChangePinDialogState(error = "PINs don't match")))

        composeRule.onNodeWithText("PINs don't match").assertIsDisplayed()
    }

    @Test
    fun back_whenIdle_navigatesBack() {
        setContent()

        pressBack()

        assertEquals(1, backCalls)
    }

    @Test
    fun back_whileRecoveryCodeShown_doesNotLeaveOrDismiss() {
        setContent(AdminUiState(recoveryCode = RecoveryCodeDialogState.Show("ABCD-2345-EFGH")))

        pressBack()

        assertEquals(0, backCalls)
        composeRule.onNodeWithTag(ADMIN_SETTINGS_RECOVERY_CODE_TAG).assertIsDisplayed()
    }

    @Test
    fun back_whileGenerating_doesNotLeave() {
        setContent(AdminUiState(recoveryCode = RecoveryCodeDialogState.Generating))

        pressBack()

        assertEquals(0, backCalls)
        composeRule.onNodeWithText("Creating recovery code...").assertIsDisplayed()
    }

    @Test
    fun betSection_empty_showsPlaceholder() {
        setContent()

        composeRule.onNodeWithText("No bets yet").performScrollTo().assertIsDisplayed()
    }

    private fun openBetMenu(bet: BetWithDetails) {
        composeRule.onNodeWithTag(ADMIN_BET_ROW_TAG_PREFIX + bet.bet.id).performScrollTo().performClick()
    }

    @Test
    fun betRow_active_menuShowsActiveActions() {
        val bet = bet(1, BetStatus.ACTIVE)
        setContent(bets = listOf(bet))

        openBetMenu(bet)

        listOf(
            ADMIN_BET_MENU_RESOLVE_TAG,
            ADMIN_BET_MENU_EDIT_PROMPT_TAG,
            ADMIN_BET_MENU_EDIT_WAGER_TAG,
            ADMIN_BET_MENU_REMOVE_PARTICIPANT_TAG,
            ADMIN_BET_MENU_FORCE_CANCEL_TAG,
        ).forEach { composeRule.onNodeWithTag(it).assertExists() }
        composeRule.onNodeWithTag(ADMIN_BET_MENU_DELETE_TAG).assertDoesNotExist()
        composeRule.onNodeWithText("Active · 2 players · 150 BetCoin pot").assertExists()
    }

    @Test
    fun betRow_resolved_menuShowsEditPromptAndDelete() {
        val bet = bet(2, BetStatus.RESOLVED)
        setContent(bets = listOf(bet))

        openBetMenu(bet)

        composeRule.onNodeWithTag(ADMIN_BET_MENU_EDIT_PROMPT_TAG).assertExists()
        composeRule.onNodeWithTag(ADMIN_BET_MENU_DELETE_TAG).assertExists()
        composeRule.onNodeWithTag(ADMIN_BET_MENU_FORCE_CANCEL_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(ADMIN_BET_MENU_RESOLVE_TAG).assertDoesNotExist()
    }

    @Test
    fun betMenu_forceCancel_invokesCallbackWithBet() {
        val bet = bet(1, BetStatus.ACTIVE)
        var cancelled: BetWithDetails? = null
        setContent(bets = listOf(bet), betActions = noBetActions.copy(onForceCancel = { cancelled = it }))

        openBetMenu(bet)
        composeRule.onNodeWithTag(ADMIN_BET_MENU_FORCE_CANCEL_TAG).performClick()

        assertEquals(bet, cancelled)
    }

    @Test
    fun confirmForceCancelDialog_confirm_invokesOnConfirm() {
        var confirms = 0
        setContent(
            state = AdminUiState(betDialog = BetDialogState.ConfirmForceCancel(bet(1, BetStatus.ACTIVE))),
            betActions = noBetActions.copy(onConfirm = { confirms++ }),
        )

        composeRule.onNodeWithText("Force cancel bet?").assertIsDisplayed()
        composeRule.onNodeWithTag(ADMIN_BET_DIALOG_CONFIRM_TAG).performClick()

        assertEquals(1, confirms)
    }

    @Test
    fun resolveDialog_confirmDisabledUntilOutcomeSelected() {
        val bet = bet(1, BetStatus.ACTIVE)
        setContent(state = AdminUiState(betDialog = BetDialogState.Resolve(bet)))
        composeRule.onNodeWithTag(ADMIN_BET_DIALOG_CONFIRM_TAG).assertIsNotEnabled()
        composeRule.onNodeWithText("Red (1 picked)").assertIsDisplayed()
    }

    @Test
    fun resolveDialog_withSelection_confirmEnabled() {
        val bet = bet(1, BetStatus.ACTIVE)
        setContent(state = AdminUiState(betDialog = BetDialogState.Resolve(bet, selectedOutcomeId = 10)))
        composeRule.onNodeWithTag(ADMIN_BET_DIALOG_CONFIRM_TAG).assertIsEnabled()
    }

    @Test
    fun removeParticipantDialog_showsError() {
        val bet = bet(1, BetStatus.ACTIVE)
        setContent(
            state = AdminUiState(
                betDialog = BetDialogState.RemoveParticipant(bet, error = AdminViewModel.ERROR_REMOVE_TOO_FEW),
            ),
        )

        composeRule.onNodeWithText(AdminViewModel.ERROR_REMOVE_TOO_FEW).assertIsDisplayed()
        composeRule.onNodeWithText("Alice · Red · 100").assertIsDisplayed()
        composeRule.onNodeWithText("Their wager is refunded.").assertIsDisplayed()
    }

    @Test
    fun back_whileBetDialogBusy_doesNotNavigate() {
        setContent(AdminUiState(betDialog = BetDialogState.ConfirmForceCancel(bet(1, BetStatus.ACTIVE), isWorking = true)))

        pressBack()

        assertEquals(0, backCalls)
        composeRule.onNodeWithText("Force cancel bet?").assertIsDisplayed()
    }

    /**
     * Sends a hardware Back key to the focused window. Espresso.pressBack waits for a settled root view,
     * which never happens while the Generating dialog's indeterminate indicator keeps requesting layout.
     */
    private fun pressBack() {
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        composeRule.waitForIdle()
    }
}
