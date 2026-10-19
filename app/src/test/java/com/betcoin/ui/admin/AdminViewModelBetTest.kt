package com.betcoin.ui.admin

import com.betcoin.data.database.entity.Bet
import com.betcoin.data.database.entity.BetOutcome
import com.betcoin.data.database.entity.BetParticipant
import com.betcoin.data.database.entity.User
import com.betcoin.data.model.BetStatus
import com.betcoin.data.model.BetWithDetails
import com.betcoin.data.model.InsufficientBalanceException
import com.betcoin.data.model.InvalidBetStateException
import com.betcoin.data.model.ParticipantWithUser
import com.betcoin.data.model.UnanimousPickException
import com.betcoin.data.repository.BetRepository
import com.betcoin.testing.FakeUserRepository
import com.betcoin.ui.common.GENERIC_ERROR_MESSAGE
import com.betcoin.ui.common.toUserMessage
import com.betcoin.ui.home.FakeBetRepository
import com.betcoin.ui.onboarding.FakeSettingsRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.IOException

/** Tests for the Bet Management actions of [AdminViewModel]. */
@OptIn(ExperimentalCoroutinesApi::class)
class AdminViewModelBetTest {

    private val testDispatcher = StandardTestDispatcher()
    private val betsFlow = MutableStateFlow<List<BetWithDetails>>(emptyList())
    private val calls = mutableListOf<String>()
    private var gate: CompletableDeferred<Unit>? = null
    private var failWith: Throwable? = null
    private lateinit var viewModel: AdminViewModel

    private val aliceUser = User(id = 1, username = "Alice", pinHash = FakeUserRepository.PLACEHOLDER_HASH, createdAt = 0L, balance = 500)
    private val bobUser = User(id = 2, username = "Bob", pinHash = FakeUserRepository.PLACEHOLDER_HASH, createdAt = 0L, balance = 500)
    private val caraUser = User(id = 3, username = "Cara", pinHash = FakeUserRepository.PLACEHOLDER_HASH, createdAt = 0L, balance = 500)

    private val outcomeA = BetOutcome(id = 10, betId = 100, outcomeText = "A")
    private val outcomeB = BetOutcome(id = 11, betId = 100, outcomeText = "B")

    private fun participant(id: Long, user: User, outcome: BetOutcome, wager: Long) = ParticipantWithUser(
        participant = BetParticipant(id = id, betId = 100, userId = user.id, outcomeId = outcome.id, wagerAmount = wager),
        user = user,
        outcome = outcome,
    )

    private val alice = participant(1000, aliceUser, outcomeA, 100)
    private val bob = participant(1001, bobUser, outcomeB, 200)
    private val cara = participant(1002, caraUser, outcomeB, 50)

    private val active = BetWithDetails(
        bet = Bet(id = 100, prompt = "Who wins?", status = BetStatus.ACTIVE, createdAt = 0L),
        outcomes = listOf(outcomeA, outcomeB),
        participants = listOf(alice, bob, cara),
    )
    private val twoPlayer = active.copy(participants = listOf(alice, bob))
    private val resolved = active.copy(bet = active.bet.copy(id = 101, status = BetStatus.RESOLVED, winningOutcomeId = 10))
    private val cancelled = active.copy(bet = active.bet.copy(id = 102, status = BetStatus.CANCELLED))

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        betsFlow.value = listOf(active, resolved, cancelled)
        val users = FakeUserRepository().apply { users = listOf(aliceUser, bobUser, caraUser) }
        val repo = object : BetRepository by FakeBetRepository() {
            override fun getAllBets(): Flow<List<BetWithDetails>> = betsFlow

            override suspend fun forceCancelBet(betId: Long) = record("forceCancel($betId)")
            override suspend fun adminResolveBet(betId: Long, winningOutcomeId: Long) =
                record("resolve($betId, $winningOutcomeId)")
            override suspend fun updateBetPrompt(betId: Long, newPrompt: String) = record("prompt($betId, $newPrompt)")
            override suspend fun adminRemoveParticipant(betId: Long, participantId: Long) =
                record("remove($betId, $participantId)")
            override suspend fun adminEditWager(participantId: Long, newWagerAmount: Long) =
                record("wager($participantId, $newWagerAmount)")
            override suspend fun deleteBet(betId: Long) = record("delete($betId)")

            private suspend fun record(call: String) {
                calls += call
                gate?.await()
                failWith?.let { throw it }
            }
        }
        viewModel = AdminViewModel(FakeSettingsRepository(), users, repo)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val state get() = viewModel.uiState.value

    private fun TestScope.collectAll() {
        backgroundScope.launch(testDispatcher) { viewModel.players.collect {} }
        backgroundScope.launch(testDispatcher) { viewModel.bets.collect {} }
    }

    private fun TestScope.confirm() {
        viewModel.onBetDialogConfirm()
        advanceUntilIdle()
    }

    private fun resolveDialog() = state.betDialog as BetDialogState.Resolve
    private fun promptDialog() = state.betDialog as BetDialogState.EditPrompt
    private fun removeDialog() = state.betDialog as BetDialogState.RemoveParticipant
    private fun wagerDialog() = state.betDialog as BetDialogState.EditWager

    @Test
    fun bets_exposesRepositoryBets() = runTest(testDispatcher) {
        collectAll()
        advanceUntilIdle()
        assertThat(viewModel.bets.value).containsExactly(active, resolved, cancelled).inOrder()
    }

    @Test
    fun forceCancel_confirm_callsRepositoryAndShowsMessage() = runTest(testDispatcher) {
        viewModel.onForceCancelClicked(active)
        confirm()
        assertThat(calls).containsExactly("forceCancel(100)")
        assertThat(state.betDialog).isNull()
        assertThat(state.message).isEqualTo(AdminViewModel.MESSAGE_BET_CANCELLED)
    }

    @Test
    fun forceCancel_ignoredForResolvedBet() = runTest(testDispatcher) {
        viewModel.onForceCancelClicked(resolved)
        assertThat(state.betDialog).isNull()
    }

    @Test
    fun forceCancel_failure_closesDialogAndShowsReason() = runTest(testDispatcher) {
        failWith = InvalidBetStateException(100, BetStatus.RESOLVED)
        viewModel.onForceCancelClicked(active)
        confirm()
        assertThat(state.betDialog).isNull()
        assertThat(state.message).isEqualTo(InvalidBetStateException(100, BetStatus.RESOLVED).toUserMessage())
    }

    @Test
    fun confirm_whileInFlight_callsRepositoryOnce() = runTest(testDispatcher) {
        gate = CompletableDeferred()
        viewModel.onForceCancelClicked(active)
        viewModel.onBetDialogConfirm()
        advanceUntilIdle()
        viewModel.onBetDialogConfirm()
        viewModel.onBetDialogDismiss()
        advanceUntilIdle()
        assertThat(state.betDialog).isNotNull()
        assertThat(calls).hasSize(1)

        gate!!.complete(Unit)
        advanceUntilIdle()
        assertThat(calls).hasSize(1)
        assertThat(state.betDialog).isNull()
    }

    @Test
    fun resolve_withoutOutcome_showsError_noCall() = runTest(testDispatcher) {
        viewModel.onAdminResolveClicked(active)
        confirm()
        assertThat(resolveDialog().error).isEqualTo(AdminViewModel.ERROR_PICK_OUTCOME)
        assertThat(calls).isEmpty()
    }

    @Test
    fun resolve_withOutcome_callsAdminResolve() = runTest(testDispatcher) {
        viewModel.onAdminResolveClicked(active)
        viewModel.onBetDialogOutcomeSelected(10)
        confirm()
        assertThat(calls).containsExactly("resolve(100, 10)")
        assertThat(state.betDialog).isNull()
        assertThat(state.message).isEqualTo(AdminViewModel.messageBetResolved("A"))
    }

    @Test
    fun resolve_staleBet_showsChangedMessageInline() = runTest(testDispatcher) {
        failWith = InvalidBetStateException(100, BetStatus.CANCELLED)
        viewModel.onAdminResolveClicked(active)
        viewModel.onBetDialogOutcomeSelected(10)
        confirm()
        assertThat(resolveDialog().error).isEqualTo(InvalidBetStateException(100, BetStatus.CANCELLED).toUserMessage())
        assertThat(resolveDialog().isSaving).isFalse()
    }

    @Test
    fun editPrompt_prefillsPrompt() = runTest(testDispatcher) {
        viewModel.onEditPromptClicked(active)
        assertThat(promptDialog().input).isEqualTo("Who wins?")
    }

    @Test
    fun editPrompt_blank_showsLengthError() = runTest(testDispatcher) {
        viewModel.onEditPromptClicked(active)
        viewModel.onBetDialogInputChanged("   ")
        confirm()
        assertThat(promptDialog().error).isEqualTo(AdminViewModel.ERROR_PROMPT_LENGTH)
        assertThat(calls).isEmpty()
    }

    @Test
    fun editPrompt_121chars_showsLengthError() = runTest(testDispatcher) {
        viewModel.onEditPromptClicked(active)
        viewModel.onBetDialogInputChanged("x".repeat(121))
        confirm()
        assertThat(promptDialog().error).isEqualTo(AdminViewModel.ERROR_PROMPT_LENGTH)
        assertThat(calls).isEmpty()
    }

    @Test
    fun editPrompt_unchanged_closesWithoutCall() = runTest(testDispatcher) {
        viewModel.onEditPromptClicked(active)
        viewModel.onBetDialogInputChanged("  Who wins?  ")
        confirm()
        assertThat(state.betDialog).isNull()
        assertThat(calls).isEmpty()
    }

    @Test
    fun editPrompt_trimsAndSaves() = runTest(testDispatcher) {
        viewModel.onEditPromptClicked(active)
        viewModel.onBetDialogInputChanged("  New prompt  ")
        confirm()
        assertThat(calls).containsExactly("prompt(100, New prompt)")
        assertThat(state.betDialog).isNull()
        assertThat(state.message).isEqualTo(AdminViewModel.MESSAGE_PROMPT_UPDATED)
    }

    @Test
    fun editPrompt_allowedOnResolvedBet() = runTest(testDispatcher) {
        viewModel.onEditPromptClicked(resolved)
        viewModel.onBetDialogInputChanged("Changed")
        confirm()
        assertThat(calls).containsExactly("prompt(101, Changed)")
    }

    @Test
    fun removeParticipant_none_showsPickError() = runTest(testDispatcher) {
        viewModel.onRemoveParticipantClicked(active)
        confirm()
        assertThat(removeDialog().error).isEqualTo(AdminViewModel.ERROR_PICK_PARTICIPANT)
        assertThat(calls).isEmpty()
    }

    @Test
    fun removeParticipant_leavingSameOutcome_blockedBeforeCall() = runTest(testDispatcher) {
        viewModel.onRemoveParticipantClicked(active)
        viewModel.onBetDialogParticipantSelected(alice.participant.id)
        confirm()
        assertThat(removeDialog().error).isEqualTo(AdminViewModel.ERROR_REMOVE_UNANIMOUS)
        assertThat(calls).isEmpty()
    }

    @Test
    fun removeParticipant_fromTwoPlayerBet_blockedTooFew() = runTest(testDispatcher) {
        viewModel.onRemoveParticipantClicked(twoPlayer)
        viewModel.onBetDialogParticipantSelected(bob.participant.id)
        confirm()
        assertThat(removeDialog().error).isEqualTo(AdminViewModel.ERROR_REMOVE_TOO_FEW)
        assertThat(calls).isEmpty()
    }

    @Test
    fun removeParticipant_valid_callsRepository() = runTest(testDispatcher) {
        viewModel.onRemoveParticipantClicked(active)
        viewModel.onBetDialogParticipantSelected(cara.participant.id)
        confirm()
        assertThat(calls).containsExactly("remove(100, 1002)")
        assertThat(state.betDialog).isNull()
        assertThat(state.message).isEqualTo(AdminViewModel.messageParticipantRemoved("Cara"))
    }

    @Test
    fun removeParticipant_repositoryUnanimous_showsUnanimousReason() = runTest(testDispatcher) {
        failWith = UnanimousPickException()
        viewModel.onRemoveParticipantClicked(active)
        viewModel.onBetDialogParticipantSelected(cara.participant.id)
        confirm()
        assertThat(removeDialog().error).isEqualTo(AdminViewModel.ERROR_REMOVE_UNANIMOUS)
        assertThat(removeDialog().isSaving).isFalse()
    }

    @Test
    fun editWager_selectPrefillsWager() = runTest(testDispatcher) {
        viewModel.onEditWagerClicked(active)
        viewModel.onBetDialogParticipantSelected(bob.participant.id)
        assertThat(wagerDialog().input).isEqualTo("200")
        assertThat(wagerDialog().selectedParticipantId).isEqualTo(1001)
    }

    @Test
    fun editWager_inputKeepsDigitsOnly() = runTest(testDispatcher) {
        viewModel.onEditWagerClicked(active)
        viewModel.onBetDialogInputChanged("1a2-3")
        assertThat(wagerDialog().input).isEqualTo("123")
        viewModel.onBetDialogInputChanged("1".repeat(20))
        assertThat(wagerDialog().input).hasLength(15)
    }

    @Test
    fun editWager_zero_showsInvalid() = runTest(testDispatcher) {
        viewModel.onEditWagerClicked(active)
        viewModel.onBetDialogParticipantSelected(alice.participant.id)
        viewModel.onBetDialogInputChanged("0")
        confirm()
        assertThat(wagerDialog().error).isEqualTo(AdminViewModel.ERROR_WAGER_INVALID)
        assertThat(calls).isEmpty()
    }

    @Test
    fun editWager_unchanged_closesWithoutCall() = runTest(testDispatcher) {
        viewModel.onEditWagerClicked(active)
        viewModel.onBetDialogParticipantSelected(alice.participant.id)
        confirm()
        assertThat(state.betDialog).isNull()
        assertThat(calls).isEmpty()
    }

    @Test
    fun editWager_increaseEqualToBalance_allowed() = runTest(testDispatcher) {
        collectAll()
        advanceUntilIdle()
        viewModel.onEditWagerClicked(active)
        viewModel.onBetDialogParticipantSelected(alice.participant.id)
        viewModel.onBetDialogInputChanged("600") // +500 == balance
        confirm()
        assertThat(calls).containsExactly("wager(1000, 600)")
        assertThat(state.message).isEqualTo(AdminViewModel.messageWagerUpdated("Alice", 600))
    }

    @Test
    fun editWager_increaseAboveBalance_blockedWithReason() = runTest(testDispatcher) {
        collectAll()
        advanceUntilIdle()
        viewModel.onEditWagerClicked(active)
        viewModel.onBetDialogParticipantSelected(alice.participant.id)
        viewModel.onBetDialogInputChanged("601") // +501 > balance
        confirm()
        assertThat(wagerDialog().error).isEqualTo(AdminViewModel.messageWagerTooHigh("Alice", 501, 500))
        assertThat(calls).isEmpty()
    }

    @Test
    fun editWager_decrease_callsRepository() = runTest(testDispatcher) {
        viewModel.onEditWagerClicked(active)
        viewModel.onBetDialogParticipantSelected(bob.participant.id)
        viewModel.onBetDialogInputChanged("1")
        confirm()
        assertThat(calls).containsExactly("wager(1001, 1)")
        assertThat(state.betDialog).isNull()
    }

    @Test
    fun editWager_repositoryInsufficientBalance_showsReason() = runTest(testDispatcher) {
        failWith = InsufficientBalanceException(userId = 1, required = 50, available = 20)
        viewModel.onEditWagerClicked(active)
        viewModel.onBetDialogParticipantSelected(alice.participant.id)
        viewModel.onBetDialogInputChanged("150")
        confirm()
        assertThat(wagerDialog().error).isEqualTo(AdminViewModel.messageWagerTooHigh("Alice", 50, 20))
        assertThat(wagerDialog().isSaving).isFalse()
    }

    @Test
    fun deleteBet_onResolved_confirmDeletes() = runTest(testDispatcher) {
        viewModel.onDeleteBetClicked(resolved)
        confirm()
        assertThat(calls).containsExactly("delete(101)")
        assertThat(state.betDialog).isNull()
        assertThat(state.message).isEqualTo(AdminViewModel.MESSAGE_BET_DELETED)
    }

    @Test
    fun deleteBet_ignoredForActiveBet() = runTest(testDispatcher) {
        viewModel.onDeleteBetClicked(active)
        assertThat(state.betDialog).isNull()
    }

    @Test
    fun ioFailure_showsGenericMessage() = runTest(testDispatcher) {
        failWith = IOException("disk")
        viewModel.onForceCancelClicked(active)
        confirm()
        assertThat(state.betDialog).isNull()
        assertThat(state.message).isEqualTo(GENERIC_ERROR_MESSAGE)
    }
}
