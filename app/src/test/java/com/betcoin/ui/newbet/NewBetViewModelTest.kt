package com.betcoin.ui.newbet

import androidx.lifecycle.SavedStateHandle
import com.betcoin.data.model.InsufficientBalanceException
import com.betcoin.data.model.UnanimousPickException
import com.betcoin.navigation.Routes
import com.betcoin.testing.FakeUserRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Tests for [NewBetViewModel]: Step A validation and createBet, Step B participants with PIN
 * confirmation, and leaving the flow (§4.2, §10).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NewBetViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var users: FakeUserRepository
    private lateinit var bets: FakeNewBetRepository
    private lateinit var savedStateHandle: SavedStateHandle
    private lateinit var viewModel: NewBetViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        users = FakeUserRepository()
        bets = FakeNewBetRepository(users)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val state get() = viewModel.uiState.value

    /** Builds the ViewModel, optionally for an existing pending bet, and keeps uiState collected. */
    private fun TestScope.createViewModel(betId: Long? = null) {
        savedStateHandle = SavedStateHandle(
            betId?.let { mapOf(Routes.NEW_BET_BET_ID_ARG to it) } ?: emptyMap()
        )
        viewModel = NewBetViewModel(savedStateHandle, bets, users)
        backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }
    }

    private fun fillStepA(prompt: String = "Who wins Smash?", vararg outcomes: String = arrayOf("Jay", "Austin")) {
        viewModel.onPromptChanged(prompt)
        while (state.outcomes.size < outcomes.size) viewModel.onAddOutcome()
        outcomes.forEachIndexed { index, text -> viewModel.onOutcomeChanged(index, text) }
    }

    /** Creates two players and a pending bet, and opens Step B for it. */
    private suspend fun TestScope.openStepBWithPlayers(): Pair<Long, Long> {
        val alice = users.createUser("Alice", "1111")
        val bob = users.createUser("Bob", "2222")
        createViewModel()
        fillStepA()
        viewModel.onNext()
        advanceUntilIdle()
        return alice to bob
    }

    private fun TestScope.join(userId: Long, outcomeIndex: Int, wager: String) {
        viewModel.onUserSelected(userId)
        viewModel.onOutcomeSelected(state.betOutcomes[outcomeIndex].id)
        viewModel.onWagerChanged(wager)
        viewModel.onAddParticipant()
        viewModel.onPinVerified()
        advanceUntilIdle()
    }

    // --- Step A ---

    @Test
    fun initialState_isStepAWithTwoEmptyOutcomesAndNextDisabled() = runTest {
        createViewModel()

        assertThat(state.step).isEqualTo(NewBetStep.A)
        assertThat(state.outcomes).containsExactly("", "")
        assertThat(state.canProceedToStepB).isFalse()
    }

    @Test
    fun validPromptAndOutcomes_enableNext() = runTest {
        createViewModel()
        fillStepA()

        assertThat(state.canProceedToStepB).isTrue()
    }

    @Test
    fun prompt_mustBe1To120CharactersAfterTrimming() = runTest {
        createViewModel()
        fillStepA(prompt = "   ")
        assertThat(state.canProceedToStepB).isFalse()

        viewModel.onPromptChanged("  " + "a".repeat(120) + "  ")
        assertThat(state.promptError).isNull()
        assertThat(state.canProceedToStepB).isTrue()

        viewModel.onPromptChanged("a".repeat(121))
        assertThat(state.promptError).isNotNull()
        assertThat(state.canProceedToStepB).isFalse()
    }

    @Test
    fun outcome_over40Characters_showsErrorAndBlocksNext() = runTest {
        createViewModel()
        fillStepA(outcomes = arrayOf("Jay", "a".repeat(41)))

        assertThat(state.outcomeErrors[1]).isNotNull()
        assertThat(state.canProceedToStepB).isFalse()

        viewModel.onOutcomeChanged(1, "a".repeat(40))
        assertThat(state.outcomeErrors[1]).isNull()
        assertThat(state.canProceedToStepB).isTrue()
    }

    @Test
    fun outcome_blank_blocksNextWithoutError() = runTest {
        createViewModel()
        fillStepA(outcomes = arrayOf("Jay", "  "))

        assertThat(state.outcomeErrors).containsExactly(null, null)
        assertThat(state.canProceedToStepB).isFalse()
    }

    @Test
    fun outcome_duplicateIgnoringCase_showsErrorOnTheRepeat() = runTest {
        createViewModel()
        fillStepA(outcomes = arrayOf("Jay", " jay "))

        assertThat(state.outcomeErrors[0]).isNull()
        assertThat(state.outcomeErrors[1]).isNotNull()
        assertThat(state.canProceedToStepB).isFalse()
    }

    @Test
    fun addOutcome_isDisabledAtEight() = runTest {
        createViewModel()
        repeat(6) { viewModel.onAddOutcome() }

        assertThat(state.outcomes).hasSize(8)
        assertThat(state.canAddOutcome).isFalse()

        viewModel.onAddOutcome()
        assertThat(state.outcomes).hasSize(8)
    }

    @Test
    fun removeOutcome_onlyAboveTwo() = runTest {
        createViewModel()
        fillStepA(outcomes = arrayOf("Jay", "Austin", "Mike"))

        viewModel.onRemoveOutcome(1)
        assertThat(state.outcomes).containsExactly("Jay", "Mike").inOrder()
        assertThat(state.canRemoveOutcome).isFalse()

        viewModel.onRemoveOutcome(0)
        assertThat(state.outcomes).containsExactly("Jay", "Mike").inOrder()
    }

    @Test
    fun next_createsBetWithTrimmedValuesAndOpensStepB() = runTest {
        createViewModel()
        fillStepA(prompt = "  Who wins Smash?  ", outcomes = arrayOf(" Jay ", "Austin"))

        viewModel.onNext()
        advanceUntilIdle()

        val bet = bets.allBets.single()
        assertThat(bet.prompt).isEqualTo("Who wins Smash?")
        assertThat(bets.outcomesOf(bet.id).map { it.outcomeText }).containsExactly("Jay", "Austin").inOrder()
        assertThat(state.step).isEqualTo(NewBetStep.B)
        assertThat(state.betId).isEqualTo(bet.id)
        assertThat(state.betPrompt).isEqualTo("Who wins Smash?")
        assertThat(state.betOutcomes.map { it.outcomeText }).containsExactly("Jay", "Austin").inOrder()
        assertThat(savedStateHandle.get<Long>(Routes.NEW_BET_BET_ID_ARG)).isEqualTo(bet.id)
    }

    @Test
    fun next_withInvalidInput_doesNotCreateBet() = runTest {
        createViewModel()
        fillStepA(outcomes = arrayOf("Jay", "jay"))

        viewModel.onNext()
        advanceUntilIdle()

        assertThat(bets.createBetCalls).isEqualTo(0)
        assertThat(state.step).isEqualTo(NewBetStep.A)
    }

    // --- Step B ---

    @Test
    fun betIdArgument_opensStepBForThatPendingBet() = runTest {
        val betId = bets.seedPendingBet("Repeat me", listOf("Yes", "No"))
        createViewModel(betId = betId)

        assertThat(state.step).isEqualTo(NewBetStep.B)
        assertThat(state.betPrompt).isEqualTo("Repeat me")
        assertThat(state.betOutcomes.map { it.outcomeText }).containsExactly("Yes", "No").inOrder()
        assertThat(bets.createBetCalls).isEqualTo(0)
    }

    @Test
    fun correctPin_addsParticipantAndClearsTheForm() = runTest {
        val (alice, _) = openStepBWithPlayers()
        viewModel.onUserSelected(alice)
        viewModel.onOutcomeSelected(state.betOutcomes[0].id)
        viewModel.onWagerChanged("250")
        viewModel.onAddParticipant()

        assertThat(state.pendingJoin?.user?.username).isEqualTo("Alice")
        assertThat(viewModel.verifyPin("9999")).isFalse()
        assertThat(bets.participantsOf(state.betId!!)).isEmpty()

        assertThat(viewModel.verifyPin("1111")).isTrue()
        viewModel.onPinVerified()
        advanceUntilIdle()

        val participant = bets.participantsOf(state.betId!!).single()
        assertThat(participant.userId).isEqualTo(alice)
        assertThat(participant.outcomeId).isEqualTo(state.betOutcomes[0].id)
        assertThat(participant.wagerAmount).isEqualTo(250L)
        assertThat(state.pendingJoin).isNull()
        assertThat(state.selectedUserId).isNull()
        assertThat(state.wager).isEmpty()
        assertThat(state.participants.map { it.user?.username }).containsExactly("Alice")
        // Balances don't move until the bet is locked.
        assertThat(users.getUser(alice)!!.balance).isEqualTo(1000L)
    }

    @Test
    fun dismissingPin_writesNothing() = runTest {
        val (alice, _) = openStepBWithPlayers()
        viewModel.onUserSelected(alice)
        viewModel.onOutcomeSelected(state.betOutcomes[0].id)
        viewModel.onWagerChanged("10")
        viewModel.onAddParticipant()

        viewModel.onPinDismissed()
        advanceUntilIdle()

        assertThat(state.pendingJoin).isNull()
        assertThat(bets.participantsOf(state.betId!!)).isEmpty()
    }

    @Test
    fun userDropdown_excludesUsersAlreadyInTheBet() = runTest {
        val (alice, bob) = openStepBWithPlayers()
        assertThat(state.availableUsers.map { it.id }).containsExactly(alice, bob)

        join(alice, outcomeIndex = 0, wager = "10")

        assertThat(state.availableUsers.map { it.id }).containsExactly(bob)
    }

    @Test
    fun wager_mustBeBetweenOneAndTheUsersBalance() = runTest {
        val (alice, _) = openStepBWithPlayers()
        viewModel.onUserSelected(alice)
        viewModel.onOutcomeSelected(state.betOutcomes[0].id)

        viewModel.onWagerChanged("0")
        assertThat(state.wagerError).isNotNull()
        assertThat(state.canAddParticipant).isFalse()

        viewModel.onWagerChanged("1001")
        assertThat(state.wagerError).contains("1000")
        assertThat(state.canAddParticipant).isFalse()

        viewModel.onWagerChanged("1000")
        assertThat(state.wagerError).isNull()
        assertThat(state.canAddParticipant).isTrue()
    }

    @Test
    fun wager_keepsDigitsOnly() = runTest {
        openStepBWithPlayers()

        viewModel.onWagerChanged("-1.5")

        assertThat(state.wager).isEqualTo("15")
    }

    @Test
    fun userWithNoBalance_cannotJoin() = runTest {
        val (alice, _) = openStepBWithPlayers()
        users.setBalance(alice, 0L)
        viewModel.onUserSelected(alice)
        viewModel.onOutcomeSelected(state.betOutcomes[0].id)
        viewModel.onWagerChanged("1")

        assertThat(state.wagerError).contains("no BetCoin")
        assertThat(state.canAddParticipant).isFalse()
    }

    @Test
    fun addParticipant_needsAnOutcome() = runTest {
        val (alice, _) = openStepBWithPlayers()
        viewModel.onUserSelected(alice)
        viewModel.onWagerChanged("10")

        assertThat(state.canAddParticipant).isFalse()
        viewModel.onAddParticipant()
        assertThat(state.pendingJoin).isNull()
    }

    @Test
    fun addParticipant_repositoryRejection_showsMessage() = runTest {
        val (alice, _) = openStepBWithPlayers()
        viewModel.onUserSelected(alice)
        viewModel.onOutcomeSelected(state.betOutcomes[0].id)
        viewModel.onWagerChanged("500")
        viewModel.onAddParticipant()
        // Balance dropped while the PIN dialog was open.
        users.setBalance(alice, 100L)

        viewModel.onPinVerified()
        advanceUntilIdle()

        assertThat(state.errorMessage).contains("Not enough BetCoin")
        assertThat(bets.participantsOf(state.betId!!)).isEmpty()
    }

    @Test
    fun removeParticipant_removesThemAndReturnsThemToTheDropdown() = runTest {
        val (alice, bob) = openStepBWithPlayers()
        join(alice, outcomeIndex = 0, wager = "10")
        val participantId = state.participants.single().participant.id

        viewModel.onRemoveParticipant(participantId)
        advanceUntilIdle()

        assertThat(state.participants).isEmpty()
        assertThat(bets.participantsOf(state.betId!!)).isEmpty()
        assertThat(state.availableUsers.map { it.id }).containsExactly(alice, bob)
    }

    @Test
    fun continue_needsAtLeastTwoParticipants() = runTest {
        val (alice, bob) = openStepBWithPlayers()
        assertThat(state.canContinue).isFalse()

        join(alice, outcomeIndex = 0, wager = "10")
        assertThat(state.canContinue).isFalse()

        join(bob, outcomeIndex = 1, wager = "20")
        assertThat(state.canContinue).isTrue()
    }

    // --- Leaving the flow ---

    @Test
    fun back_onEmptyStepA_leavesWithoutAsking() = runTest {
        createViewModel()

        viewModel.onBackRequested()

        assertThat(state.showDiscardDialog).isFalse()
        assertThat(state.exited).isTrue()
    }

    @Test
    fun back_onStepAWithText_asksThenLeavesWithoutDiscarding() = runTest {
        createViewModel()
        viewModel.onOutcomeChanged(0, "Jay")

        viewModel.onBackRequested()
        assertThat(state.showDiscardDialog).isTrue()
        assertThat(state.exited).isFalse()

        viewModel.onDiscardConfirmed()
        advanceUntilIdle()

        assertThat(state.exited).isTrue()
        assertThat(bets.discardedBetIds).isEmpty()
    }

    @Test
    fun back_onStepB_dismissKeepsTheBet() = runTest {
        openStepBWithPlayers()

        viewModel.onBackRequested()
        assertThat(state.showDiscardDialog).isTrue()

        viewModel.onDiscardDismissed()

        assertThat(state.showDiscardDialog).isFalse()
        assertThat(state.exited).isFalse()
        assertThat(bets.allBets).hasSize(1)
    }

    @Test
    fun back_onStepB_confirmDiscardsThePendingBet() = runTest {
        val (alice, _) = openStepBWithPlayers()
        join(alice, outcomeIndex = 0, wager = "10")
        val betId = state.betId!!

        viewModel.onBackRequested()
        viewModel.onDiscardConfirmed()
        advanceUntilIdle()

        assertThat(bets.discardedBetIds).containsExactly(betId)
        assertThat(bets.allBets).isEmpty()
        assertThat(bets.participantsOf(betId)).isEmpty()
        assertThat(state.exited).isTrue()
        assertThat(savedStateHandle.contains(Routes.NEW_BET_BET_ID_ARG)).isFalse()
    }

    @Test
    fun back_onStepBOpenedFromArgument_confirmDiscardsThatBet() = runTest {
        val betId = bets.seedPendingBet("Recovered", listOf("Yes", "No"))
        createViewModel(betId = betId)

        viewModel.onBackRequested()
        viewModel.onDiscardConfirmed()
        advanceUntilIdle()

        assertThat(bets.discardedBetIds).containsExactly(betId)
        assertThat(state.exited).isTrue()
    }

    // --- Step C ---

    /** Opens Step C with Alice on Jay (100) and Bob on Austin (50) unless [bobOutcome] says otherwise. */
    private suspend fun TestScope.openStepC(bobOutcome: Int = 1): Pair<Long, Long> {
        val (alice, bob) = openStepBWithPlayers()
        join(alice, outcomeIndex = 0, wager = "100")
        join(bob, outcomeIndex = bobOutcome, wager = "50")
        viewModel.onContinue()
        return alice to bob
    }

    @Test
    fun continue_needsTwoParticipants() = runTest {
        val (alice, _) = openStepBWithPlayers()
        join(alice, outcomeIndex = 0, wager = "100")

        viewModel.onContinue()

        assertThat(state.step).isEqualTo(NewBetStep.B)
    }

    @Test
    fun continue_opensStepCWithTheParticipantsSummary() = runTest {
        openStepC()

        assertThat(state.step).isEqualTo(NewBetStep.C)
        assertThat(state.betPrompt).isEqualTo("Who wins Smash?")
        assertThat(state.participants.map { it.user?.username }).containsExactly("Alice", "Bob").inOrder()
        assertThat(state.lockBlocker).isNull()
        assertThat(state.canLock).isTrue()
    }

    @Test
    fun back_fromStepC_returnsToStepBKeepingParticipants() = runTest {
        openStepC()

        viewModel.onBackRequested()

        assertThat(state.step).isEqualTo(NewBetStep.B)
        assertThat(state.showDiscardDialog).isFalse()
        assertThat(state.participants).hasSize(2)
        assertThat(bets.participantsOf(state.betId!!)).hasSize(2)
        assertThat(state.canContinue).isTrue()
    }

    @Test
    fun close_fromStepC_asksToDiscard() = runTest {
        openStepC()

        viewModel.onCloseRequested()

        assertThat(state.showDiscardDialog).isTrue()
        assertThat(state.step).isEqualTo(NewBetStep.C)
    }

    @Test
    fun unanimousPick_showsErrorAndBlocksLocking() = runTest {
        openStepC(bobOutcome = 0)

        assertThat(state.isUnanimous).isTrue()
        assertThat(state.lockBlocker).contains("same outcome")
        assertThat(state.canLock).isFalse()

        viewModel.onLockRequested()

        assertThat(state.confirmingLock).isFalse()
        assertThat(bets.lockedBetIds).isEmpty()
    }

    @Test
    fun changingAPick_clearsTheUnanimousError() = runTest {
        val (_, bob) = openStepC(bobOutcome = 0)
        viewModel.onBackRequested()
        viewModel.onRemoveParticipant(state.participants.first { it.user?.id == bob }.participant.id)
        advanceUntilIdle()
        join(bob, outcomeIndex = 1, wager = "50")
        viewModel.onContinue()

        assertThat(state.lockBlocker).isNull()
    }

    @Test
    fun balanceDroppedBelowWager_blocksLockingWithAMessage() = runTest {
        val (alice, _) = openStepC()

        users.setBalance(alice, 30L)

        assertThat(state.lockBlocker).contains("Alice")
        assertThat(state.lockBlocker).contains("100")
        assertThat(state.lockBlocker).contains("30")
        assertThat(state.canLock).isFalse()
    }

    @Test
    fun verifyParticipantPin_checksThatParticipantsPin() = runTest {
        val (alice, bob) = openStepC()

        assertThat(viewModel.verifyParticipantPin(alice, "1111")).isTrue()
        assertThat(viewModel.verifyParticipantPin(alice, "2222")).isFalse()
        assertThat(viewModel.verifyParticipantPin(bob, "2222")).isTrue()
    }

    @Test
    fun lockRequested_opensThePinSequenceWithoutLocking() = runTest {
        openStepC()

        viewModel.onLockRequested()

        assertThat(state.confirmingLock).isTrue()
        assertThat(bets.lockedBetIds).isEmpty()
    }

    @Test
    fun abortingThePinSequence_locksNothing() = runTest {
        val (alice, _) = openStepC()
        viewModel.onLockRequested()

        viewModel.onLockAborted()
        advanceUntilIdle()

        assertThat(state.confirmingLock).isFalse()
        assertThat(state.step).isEqualTo(NewBetStep.C)
        assertThat(bets.lockedBetIds).isEmpty()
        assertThat(users.getUser(alice)!!.balance).isEqualTo(1000L)
    }

    @Test
    fun allPinsConfirmed_locksTheBetAndDeductsWagers() = runTest {
        val (alice, bob) = openStepC()
        viewModel.onLockRequested()

        viewModel.onAllPinsConfirmed()
        advanceUntilIdle()

        val betId = state.betId!!
        assertThat(bets.lockedBetIds).containsExactly(betId)
        assertThat(state.lockedBetId).isEqualTo(betId)
        assertThat(state.confirmingLock).isFalse()
        assertThat(users.getUser(alice)!!.balance).isEqualTo(900L)
        assertThat(users.getUser(bob)!!.balance).isEqualTo(950L)
    }

    @Test
    fun allPinsConfirmed_withoutARequest_doesNothing() = runTest {
        openStepC()

        viewModel.onAllPinsConfirmed()
        advanceUntilIdle()

        assertThat(bets.lockedBetIds).isEmpty()
        assertThat(state.lockedBetId).isNull()
    }

    @Test
    fun lockFailure_insufficientBalance_showsMessageAndStaysOnStepC() = runTest {
        val (alice, _) = openStepC()
        viewModel.onLockRequested()
        bets.lockFailure = InsufficientBalanceException(alice, 100L, 30L)

        viewModel.onAllPinsConfirmed()
        advanceUntilIdle()

        assertThat(state.step).isEqualTo(NewBetStep.C)
        assertThat(state.lockedBetId).isNull()
        assertThat(state.confirmingLock).isFalse()
        assertThat(state.isBusy).isFalse()
        assertThat(state.errorMessage).contains("Alice")
        assertThat(state.errorMessage).contains("30")
    }

    @Test
    fun lockFailure_unanimousPick_showsMessage() = runTest {
        openStepC()
        viewModel.onLockRequested()
        bets.lockFailure = UnanimousPickException()

        viewModel.onAllPinsConfirmed()
        advanceUntilIdle()

        assertThat(state.errorMessage).contains("same outcome")
        assertThat(state.lockedBetId).isNull()
    }
}
