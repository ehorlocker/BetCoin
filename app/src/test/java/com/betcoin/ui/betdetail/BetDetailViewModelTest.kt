package com.betcoin.ui.betdetail

import android.database.sqlite.SQLiteException
import androidx.lifecycle.SavedStateHandle
import com.betcoin.data.database.entity.Bet
import com.betcoin.data.database.entity.BetOutcome
import com.betcoin.data.database.entity.BetParticipant
import com.betcoin.data.database.entity.User
import com.betcoin.data.model.BetStatus
import com.betcoin.data.model.BetWithDetails
import com.betcoin.data.model.InsufficientBalanceException
import com.betcoin.data.model.InvalidBetStateException
import com.betcoin.data.model.InvalidInputException
import com.betcoin.data.model.ParticipantWithUser
import com.betcoin.data.model.UnanimousPickException
import com.betcoin.data.repository.BetRepository
import com.betcoin.data.repository.UserRepository
import com.betcoin.navigation.Routes
import com.betcoin.testing.FakeUserRepository
import com.betcoin.ui.common.GENERIC_ERROR_MESSAGE
import com.betcoin.ui.common.toUserMessage
import com.betcoin.ui.home.FakeBetRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.IOException

/** Observes one bet; every other [BetRepository] method comes from [FakeBetRepository]. */
private class SingleBetRepository(
    private val bet: Flow<BetWithDetails?>,
) : BetRepository by FakeBetRepository() {
    var requestedBetId: Long? = null

    override fun getBetWithDetails(betId: Long): Flow<BetWithDetails?> {
        requestedBetId = betId
        return bet
    }
}

/**
 * Tests for [BetDetailViewModel] and the mapping from [BetWithDetails] to [BetDetail].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BetDetailViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- Fixtures: "Who wins?" with outcomes Jay (10) and Kay (11) ---

    private val jay = BetOutcome(id = 10, betId = BET_ID, outcomeText = "Jay")
    private val kay = BetOutcome(id = 11, betId = BET_ID, outcomeText = "Kay")
    private val alice = User(id = 1, username = "Alice", pinHash = "hash", createdAt = 0L)
    private val bob = User(id = 2, username = "Bob", pinHash = "hash", createdAt = 0L)

    private fun participant(
        id: Long,
        user: User?,
        outcome: BetOutcome,
        wager: Long,
        payout: Long? = null,
        deletedUsername: String? = null,
    ) = ParticipantWithUser(
        participant = BetParticipant(
            id = id,
            betId = BET_ID,
            userId = user?.id,
            outcomeId = outcome.id,
            wagerAmount = wager,
            payout = payout,
            deletedUsername = deletedUsername,
        ),
        user = user,
        outcome = outcome,
    )

    private fun bet(
        status: BetStatus,
        participants: List<ParticipantWithUser>,
        winningOutcomeId: Long? = null,
        resolvedAt: Long? = null,
    ) = BetWithDetails(
        bet = Bet(
            id = BET_ID,
            prompt = "Who wins?",
            status = status,
            winningOutcomeId = winningOutcomeId,
            createdAt = CREATED_AT,
            resolvedAt = resolvedAt,
        ),
        outcomes = listOf(kay, jay),
        participants = participants,
    )

    private fun viewModel(repository: BetRepository, users: UserRepository = FakeUserRepository()) =
        BetDetailViewModel(SavedStateHandle(mapOf(Routes.BET_ID_ARG to BET_ID)), repository, users)

    /** Collects [BetDetailViewModel.uiState] (a no-op now that it's shared eagerly, but keeps the flow active) and returns its value. */
    private fun TestScope.stateOf(viewModel: BetDetailViewModel): BetDetailUiState {
        backgroundScope.launch { viewModel.uiState.collect {} }
        return viewModel.uiState.value
    }

    private fun TestScope.detailOf(details: BetWithDetails): BetDetail {
        val state = stateOf(viewModel(SingleBetRepository(MutableStateFlow(details))))
        assertThat(state).isInstanceOf(BetDetailUiState.Success::class.java)
        return (state as BetDetailUiState.Success).detail
    }

    // --- State ---

    @Test
    fun uiState_startsLoadingUntilTheBetEmits() = runTest(testDispatcher) {
        val repository = SingleBetRepository(flow { awaitCancellation() })

        assertThat(stateOf(viewModel(repository))).isEqualTo(BetDetailUiState.Loading)
    }

    @Test
    fun uiState_observesTheBetIdArgument() = runTest(testDispatcher) {
        val repository = SingleBetRepository(MutableStateFlow(null))
        stateOf(viewModel(repository))

        assertThat(repository.requestedBetId).isEqualTo(BET_ID)
    }

    @Test
    fun uiState_isNotFoundWhenTheBetDoesNotExist() = runTest(testDispatcher) {
        val repository = SingleBetRepository(MutableStateFlow(null))

        assertThat(stateOf(viewModel(repository))).isEqualTo(BetDetailUiState.NotFound)
    }

    @Test
    fun uiState_becomesNotFoundWhenTheBetIsDeleted() = runTest(testDispatcher) {
        val source = MutableStateFlow<BetWithDetails?>(
            bet(BetStatus.CANCELLED, listOf(participant(1, alice, jay, 100))),
        )
        val viewModel = viewModel(SingleBetRepository(source))
        assertThat(stateOf(viewModel)).isInstanceOf(BetDetailUiState.Success::class.java)

        source.value = null

        assertThat(viewModel.uiState.value).isEqualTo(BetDetailUiState.NotFound)
    }

    @Test
    fun uiState_isErrorWhenTheFlowFails() = runTest(testDispatcher) {
        val repository = SingleBetRepository(flow { throw IllegalStateException("boom") })

        assertThat(stateOf(viewModel(repository))).isEqualTo(BetDetailUiState.Error)
    }

    // --- Mapping ---

    @Test
    fun activeBet_mapsHeaderOutcomesAndPendingParticipants() = runTest(testDispatcher) {
        val detail = detailOf(
            bet(BetStatus.ACTIVE, listOf(participant(2, bob, kay, 200), participant(1, alice, jay, 150))),
        )

        assertThat(detail.prompt).isEqualTo("Who wins?")
        assertThat(detail.status).isEqualTo(BetStatus.ACTIVE)
        assertThat(detail.createdAt).isEqualTo(CREATED_AT)
        assertThat(detail.outcomes).containsExactly(
            OutcomeRow(id = 10, text = "Jay", isWinner = false),
            OutcomeRow(id = 11, text = "Kay", isWinner = false),
        ).inOrder()
        assertThat(detail.participants).containsExactly(
            ParticipantRow(1, 1, "Alice", false, "Jay", 150, ParticipantResult.PENDING, net = null),
            ParticipantRow(2, 2, "Bob", false, "Kay", 200, ParticipantResult.PENDING, net = null),
        ).inOrder()
        assertThat(detail.potSize).isEqualTo(350)
        assertThat(detail.winningOutcome).isNull()
        assertThat(detail.isPush).isFalse()
        assertThat(detail.isCapped).isFalse()
    }

    @Test
    fun resolvedBet_marksWinnerAndShowsNetPayouts() = runTest(testDispatcher) {
        val detail = detailOf(
            bet(
                BetStatus.RESOLVED,
                listOf(participant(1, alice, jay, 150, payout = 350), participant(2, bob, kay, 200, payout = 0)),
                winningOutcomeId = jay.id,
                resolvedAt = RESOLVED_AT,
            ),
        )

        assertThat(detail.outcomes.single { it.isWinner }.text).isEqualTo("Jay")
        assertThat(detail.winningOutcome).isEqualTo("Jay")
        assertThat(detail.resolvedAt).isEqualTo(RESOLVED_AT)
        assertThat(detail.isPush).isFalse()
        assertThat(detail.participants.map { it.result to it.net })
            .containsExactly(ParticipantResult.WIN to 200L, ParticipantResult.LOSS to -200L)
            .inOrder()
    }

    @Test
    fun cappedResolvedBet_showsLoserRealNetAndIsCapped() = runTest(testDispatcher) {
        val detail = detailOf(
            bet(
                BetStatus.RESOLVED,
                listOf(participant(1, alice, jay, 200, payout = 100), participant(2, bob, kay, 100, payout = 200)),
                winningOutcomeId = kay.id,
                resolvedAt = RESOLVED_AT,
            ),
        )

        assertThat(detail.participants.map { it.result to it.net })
            .containsExactly(ParticipantResult.LOSS to -100L, ParticipantResult.WIN to 100L)
            .inOrder()
        assertThat(detail.isCapped).isTrue()
    }

    @Test
    fun legacyResolvedBet_withZeroLoserPayout_isNotCapped() = runTest(testDispatcher) {
        val detail = detailOf(
            bet(
                BetStatus.RESOLVED,
                listOf(participant(1, alice, jay, 200, payout = 0), participant(2, bob, kay, 100, payout = 300)),
                winningOutcomeId = kay.id,
                resolvedAt = RESOLVED_AT,
            ),
        )

        assertThat(detail.participants.map { it.net }).containsExactly(-200L, 200L).inOrder()
        assertThat(detail.isCapped).isFalse()
    }

    @Test
    fun resolvedBetWithNoOneOnTheWinner_isAPushWithZeroNet() = runTest(testDispatcher) {
        val nobody = BetOutcome(id = 12, betId = BET_ID, outcomeText = "Nobody")
        val detail = detailOf(
            bet(
                BetStatus.RESOLVED,
                listOf(participant(1, alice, jay, 150, payout = 150), participant(2, bob, kay, 200, payout = 200)),
                winningOutcomeId = nobody.id,
                resolvedAt = RESOLVED_AT,
            ).let { it.copy(outcomes = it.outcomes + nobody) },
        )

        assertThat(detail.isPush).isTrue()
        assertThat(detail.isCapped).isFalse()
        assertThat(detail.winningOutcome).isEqualTo("Nobody")
        assertThat(detail.participants.map { it.result to it.net })
            .containsExactly(ParticipantResult.PUSH to 0L, ParticipantResult.PUSH to 0L)
    }

    @Test
    fun cancelledBet_marksEveryoneRefundedWithNoNet() = runTest(testDispatcher) {
        val detail = detailOf(
            bet(BetStatus.CANCELLED, listOf(participant(1, alice, jay, 150), participant(2, bob, kay, 200))),
        )

        assertThat(detail.outcomes.none { it.isWinner }).isTrue()
        assertThat(detail.winningOutcome).isNull()
        assertThat(detail.participants.map { it.result }).containsExactly(
            ParticipantResult.REFUNDED,
            ParticipantResult.REFUNDED,
        )
        assertThat(detail.participants.map { it.net }).containsExactly(null, null)
    }

    @Test
    fun deletedUser_showsTheUsernameSnapshot() = runTest(testDispatcher) {
        val detail = detailOf(
            bet(
                BetStatus.RESOLVED,
                listOf(
                    participant(1, null, jay, 150, payout = 350, deletedUsername = "Carol"),
                    participant(2, null, kay, 200, payout = 0),
                ),
                winningOutcomeId = jay.id,
            ),
        )

        assertThat(detail.participants.map { it.name to it.isDeletedUser })
            .containsExactly("Carol" to true, DELETED_PLAYER to true)
            .inOrder()
    }

    @Test
    fun participantRow_carriesTheUserIdOrNullWhenDeleted() = runTest(testDispatcher) {
        val detail = detailOf(
            bet(
                BetStatus.RESOLVED,
                listOf(participant(1, alice, jay, 150, payout = 350), participant(2, null, kay, 200, payout = 0)),
                winningOutcomeId = jay.id,
            ),
        )

        assertThat(detail.participants.map { it.userId }).containsExactly(1L, null).inOrder()
    }

    // --- Actions: bet 7 with outcomes Jay(1), Kim(2), Draw(3), users Jay(10) and Kim(11) ---

    private val aJay = BetOutcome(id = 1, betId = BET_ID, outcomeText = "Jay")
    private val aKim = BetOutcome(id = 2, betId = BET_ID, outcomeText = "Kim")
    private val aDraw = BetOutcome(id = 3, betId = BET_ID, outcomeText = "Draw")
    private val jayUser = User(id = 10, username = "Jay", pinHash = "hash", createdAt = 0L)
    private val kimUser = User(id = 11, username = "Kim", pinHash = "hash", createdAt = 0L)

    private fun actionBet(
        status: BetStatus,
        kimDeleted: Boolean = false,
        jayDeleted: Boolean = false,
    ): BetWithDetails {
        fun row(id: Long, user: User, outcome: BetOutcome, wager: Long, deleted: Boolean) =
            ParticipantWithUser(
                participant = BetParticipant(
                    id = id,
                    betId = BET_ID,
                    userId = if (deleted) null else user.id,
                    outcomeId = outcome.id,
                    wagerAmount = wager,
                    deletedUsername = if (deleted) user.username else null,
                ),
                user = if (deleted) null else user,
                outcome = outcome,
            )
        return BetWithDetails(
            bet = Bet(
                id = BET_ID,
                prompt = "Who wins?",
                status = status,
                winningOutcomeId = if (status == BetStatus.RESOLVED) aJay.id else null,
                createdAt = CREATED_AT,
            ),
            outcomes = listOf(aJay, aKim, aDraw),
            participants = listOf(
                row(1, jayUser, aJay, 100, jayDeleted),
                row(2, kimUser, aKim, 50, kimDeleted),
            ),
        )
    }

    private class Fixture(val repo: RecordingBetRepository, val vm: BetDetailViewModel) {
        val state get() = vm.actionState.value
    }

    private fun fixture(
        status: BetStatus = BetStatus.ACTIVE,
        users: UserRepository = FakeUserRepository(),
        kimDeleted: Boolean = false,
        jayDeleted: Boolean = false,
    ): Fixture {
        val repo = RecordingBetRepository(actionBet(status, kimDeleted, jayDeleted))
        return Fixture(repo, viewModel(repo, users))
    }

    @Test
    fun resolve_picksOutcomeThenConsentThenCallsResolveBet() = runTest(testDispatcher) {
        val f = fixture()

        f.vm.onResolveRequested()
        assertThat(f.state.pickingOutcome).isTrue()
        f.vm.onOutcomePicked(1)
        assertThat(f.state.pickingOutcome).isFalse()
        assertThat(f.state.consent).isEqualTo(PendingConsent(BetAction.Resolve(1, "Jay"), "Resolve: Jay wins"))
        f.vm.onAllPinsConfirmed()

        assertThat(f.repo.calls).containsExactly("resolve(7, 1)")
        assertThat(f.state.consent).isNull()
        assertThat(f.state.isBusy).isFalse()
    }

    @Test
    fun resolve_toUnpickedOutcomeIsAPushAndStillCallsResolveBet() = runTest(testDispatcher) {
        val f = fixture()

        f.vm.onResolveRequested()
        f.vm.onOutcomePicked(3)
        assertThat(f.state.consent?.title).isEqualTo("Resolve: Draw wins")
        f.vm.onAllPinsConfirmed()

        assertThat(f.repo.calls).containsExactly("resolve(7, 3)")
    }

    @Test
    fun dismissingTheOutcomePickerStartsNothing() = runTest(testDispatcher) {
        val f = fixture()

        f.vm.onResolveRequested()
        f.vm.onOutcomePickerDismissed()

        assertThat(f.state.pickingOutcome).isFalse()
        assertThat(f.state.consent).isNull()
        assertThat(f.repo.calls).isEmpty()
    }

    @Test
    fun cancel_runsConsentThenCancelBet() = runTest(testDispatcher) {
        val f = fixture()

        f.vm.onCancelRequested()
        assertThat(f.state.consent)
            .isEqualTo(PendingConsent(BetAction.Cancel, "Cancel bet: all wagers refunded"))
        f.vm.onAllPinsConfirmed()

        assertThat(f.repo.calls).containsExactly("cancel(7)")
        assertThat(f.state.consent).isNull()
    }

    @Test
    fun abortingConsentChangesNothing() = runTest(testDispatcher) {
        val active = fixture()
        active.vm.onResolveRequested()
        active.vm.onOutcomePicked(1)
        active.vm.onConsentAborted()
        assertThat(active.state).isEqualTo(BetActionState())
        active.vm.onCancelRequested()
        active.vm.onConsentAborted()
        assertThat(active.state).isEqualTo(BetActionState())
        assertThat(active.repo.calls).isEmpty()

        val resolved = fixture(BetStatus.RESOLVED)
        resolved.vm.onReopenRequested()
        assertThat(resolved.state.consent).isNotNull()
        resolved.vm.onConsentAborted()
        assertThat(resolved.state).isEqualTo(BetActionState())
        assertThat(resolved.repo.calls).isEmpty()
    }

    @Test
    fun reopen_resolvedBetCallsReopenBet() = runTest(testDispatcher) {
        val f = fixture(BetStatus.RESOLVED)

        f.vm.onReopenRequested()
        assertThat(f.state.consent).isEqualTo(PendingConsent(BetAction.Reopen, "Reopen bet"))
        f.vm.onAllPinsConfirmed()

        assertThat(f.repo.calls).containsExactly("reopen(7)")
    }

    @Test
    fun reopen_cancelledBetCallsReopenBet() = runTest(testDispatcher) {
        val f = fixture(BetStatus.CANCELLED)

        f.vm.onReopenRequested()
        assertThat(f.state.consent?.title).isEqualTo("Reopen bet")
        f.vm.onAllPinsConfirmed()

        assertThat(f.repo.calls).containsExactly("reopen(7)")
    }

    @Test
    fun resolveAndCancelAreIgnoredWhenNotActive() = runTest(testDispatcher) {
        for (status in listOf(BetStatus.RESOLVED, BetStatus.CANCELLED)) {
            val f = fixture(status)
            f.vm.onResolveRequested()
            f.vm.onCancelRequested()
            assertThat(f.state).isEqualTo(BetActionState())
        }
    }

    @Test
    fun reopenIsIgnoredWhenActive() = runTest(testDispatcher) {
        val f = fixture(BetStatus.ACTIVE)

        f.vm.onReopenRequested()

        assertThat(f.state).isEqualTo(BetActionState())
    }

    @Test
    fun reopenIsBlockedWithAReasonWhenAParticipantWasDeleted() = runTest(testDispatcher) {
        val f = fixture(BetStatus.RESOLVED, kimDeleted = true)

        f.vm.onReopenRequested()

        assertThat(f.state.consent).isNull()
        val detail = (f.vm.uiState.value as BetDetailUiState.Success).detail
        assertThat(detail.reopenBlockedReason).isEqualTo("Can't reopen: Kim was deleted")
    }

    @Test
    fun reopenBlockedReasonListsEveryDeletedParticipant() = runTest(testDispatcher) {
        val f = fixture(BetStatus.CANCELLED, kimDeleted = true, jayDeleted = true)

        val detail = (f.vm.uiState.value as BetDetailUiState.Success).detail
        assertThat(detail.reopenBlockedReason).isEqualTo("Can't reopen: Jay, Kim were deleted")
    }

    @Test
    fun reopenBlockedReasonIsNullForActiveBetsAndWhenNobodyWasDeleted() = runTest(testDispatcher) {
        for ((status, kimDeleted) in listOf(BetStatus.ACTIVE to true, BetStatus.RESOLVED to false)) {
            val detail = (fixture(status, kimDeleted = kimDeleted).vm.uiState.value as BetDetailUiState.Success).detail
            assertThat(detail.reopenBlockedReason).isNull()
        }
    }

    @Test
    fun reopenShortfallShowsMessageFromException() = runTest(testDispatcher) {
        val f = fixture(BetStatus.RESOLVED)
        f.repo.failure = InsufficientBalanceException(userId = 10, required = 150, available = 20)

        f.vm.onReopenRequested()
        f.vm.onAllPinsConfirmed()

        assertThat(f.state.errorMessage).isEqualTo("Jay needs 150 BetCoin but has 20")
        assertThat(f.state.consent).isNull()
        assertThat(f.state.isBusy).isFalse()
    }

    @Test
    fun invalidStateErrorMapsToMessage() = runTest(testDispatcher) {
        val f = fixture()
        f.repo.failure = InvalidBetStateException(7, BetStatus.RESOLVED)

        f.vm.onCancelRequested()
        f.vm.onAllPinsConfirmed()

        assertThat(f.state.errorMessage).isEqualTo("This bet has changed. Nothing was updated.")
        assertThat(f.state.consent).isNull()
    }

    @Test
    fun newActionClearsPreviousError() = runTest(testDispatcher) {
        val f = fixture()
        f.repo.failure = InvalidBetStateException(7, BetStatus.RESOLVED)
        f.vm.onCancelRequested()
        f.vm.onAllPinsConfirmed()
        assertThat(f.state.errorMessage).isNotNull()

        f.vm.onCancelRequested()

        assertThat(f.state.errorMessage).isNull()
    }

    @Test
    fun onAllPinsConfirmedTwiceCallsTheRepositoryOnce() = runTest(testDispatcher) {
        val f = fixture()
        f.repo.gate = CompletableDeferred()
        f.vm.onCancelRequested()

        f.vm.onAllPinsConfirmed()
        f.vm.onAllPinsConfirmed()

        assertThat(f.state.isBusy).isTrue()
        assertThat(f.repo.calls).containsExactly("cancel(7)")
        f.repo.gate!!.complete(Unit)
        assertThat(f.state.isBusy).isFalse()
    }

    @Test
    fun requestsAreIgnoredWhileBusy() = runTest(testDispatcher) {
        val f = fixture()
        f.repo.gate = CompletableDeferred()
        f.vm.onCancelRequested()
        f.vm.onAllPinsConfirmed()
        val busyState = f.state

        f.vm.onCancelRequested()
        f.vm.onResolveRequested()

        assertThat(f.state).isEqualTo(busyState)
        f.repo.gate!!.complete(Unit)
    }

    @Test
    fun verifyParticipantPin_trueForCorrectPinFalseForWrong() = runTest(testDispatcher) {
        val users = FakeUserRepository().apply { nextCreatedUserId = 10 }
        users.createUser("Jay", "1234")
        val f = fixture(users = users)

        assertThat(f.vm.verifyParticipantPin(10, "1234")).isTrue()
        assertThat(f.vm.verifyParticipantPin(10, "9999")).isFalse()
    }

    @Test
    fun verifyParticipantPin_falseWhenTheRepositoryThrows() = runTest(testDispatcher) {
        val users = object : UserRepository by FakeUserRepository() {
            override suspend fun verifyPin(userId: Long, pin: String): Boolean = throw IOException("db down")
        }
        val f = fixture(users = users)

        assertThat(f.vm.verifyParticipantPin(10, "1234")).isFalse()
    }

    @Test
    fun verifyParticipantPin_propagatesUnexpectedExceptions() = runTest(testDispatcher) {
        val users = object : UserRepository by FakeUserRepository() {
            override suspend fun verifyPin(userId: Long, pin: String): Boolean = error("bug")
        }
        val f = fixture(users = users)

        val result = runCatching { f.vm.verifyParticipantPin(10, "1234") }

        assertThat(result.exceptionOrNull()).isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun consentClosesWhenBetStatusChangesUnderneath() = runTest(testDispatcher) {
        val f = fixture()
        f.vm.onCancelRequested()
        assertThat(f.state.consent).isNotNull()

        f.repo.bet.value = actionBet(BetStatus.CANCELLED)

        assertThat(f.state.consent).isNull()
    }

    @Test
    fun outcomePickerAndConsentCloseWhenTheBetIsDeleted() = runTest(testDispatcher) {
        val f = fixture()
        f.vm.onResolveRequested()
        assertThat(f.state.pickingOutcome).isTrue()

        f.repo.bet.value = null

        assertThat(f.state.pickingOutcome).isFalse()
    }

    @Test
    fun consentClosesWhenTheBetIsDeleted() = runTest(testDispatcher) {
        val f = fixture()
        f.vm.onCancelRequested()
        assertThat(f.state.consent).isNotNull()

        f.repo.bet.value = null

        assertThat(f.state.consent).isNull()
    }

    @Test
    fun errorClearsWhenTheBetStatusChangesUnderneath() = runTest(testDispatcher) {
        val f = fixture(BetStatus.RESOLVED)
        f.repo.failure = InsufficientBalanceException(userId = 10, required = 150, available = 20)
        f.vm.onReopenRequested()
        f.vm.onAllPinsConfirmed()
        assertThat(f.state.errorMessage).isNotNull()

        f.repo.bet.value = actionBet(BetStatus.ACTIVE)

        assertThat(f.state.errorMessage).isNull()
    }

    @Test
    fun invalidInputErrorMapsToAdminPanelMessage() = runTest(testDispatcher) {
        val f = fixture()
        f.repo.failure = InvalidInputException("participant gone")

        f.vm.onCancelRequested()
        f.vm.onAllPinsConfirmed()

        assertThat(f.state.errorMessage)
            .isEqualTo("A participant was deleted, so this can't be done here. Use the Admin Panel.")
    }

    @Test
    fun shortfallForUnknownUserFallsBackToAParticipant() = runTest(testDispatcher) {
        val f = fixture(BetStatus.RESOLVED)
        f.repo.failure = InsufficientBalanceException(userId = 99, required = 150, available = 20)

        f.vm.onReopenRequested()
        f.vm.onAllPinsConfirmed()

        assertThat(f.state.errorMessage).isEqualTo("A participant needs 150 BetCoin but has 20")
    }

    @Test
    fun otherBetCoinExceptionsShowTheMapperMessage() = runTest(testDispatcher) {
        val f = fixture()
        f.repo.failure = UnanimousPickException()

        f.vm.onCancelRequested()
        f.vm.onAllPinsConfirmed()

        assertThat(f.state.errorMessage).isEqualTo("Everyone picked the same outcome. Change at least one pick.")
    }

    @Test
    fun ioFailureShowsTheGenericMessageAndClosesConsent() = runTest(testDispatcher) {
        val f = fixture()
        f.repo.failure = IOException("disk")

        f.vm.onCancelRequested()
        f.vm.onAllPinsConfirmed()

        assertThat(f.state.errorMessage).isEqualTo(GENERIC_ERROR_MESSAGE)
        assertThat(f.state.consent).isNull()
        assertThat(f.state.isBusy).isFalse()
    }

    // --- Repeat ---

    @Test
    fun repeatOnResolvedBetCallsRepeatBetAndExposesNewId() = runTest(testDispatcher) {
        val f = fixture(BetStatus.RESOLVED)

        f.vm.onRepeatRequested()

        assertThat(f.repo.calls).containsExactly("repeat(7)")
        assertThat(f.state.repeatedBetId).isEqualTo(99L)
        assertThat(f.state.isBusy).isFalse()
        assertThat(f.state.errorMessage).isNull()
    }

    @Test
    fun repeatOnCancelledBetCallsRepeatBet() = runTest(testDispatcher) {
        val f = fixture(BetStatus.CANCELLED)

        f.vm.onRepeatRequested()

        assertThat(f.repo.calls).containsExactly("repeat(7)")
        assertThat(f.state.repeatedBetId).isEqualTo(99L)
        assertThat(f.state.isBusy).isFalse()
        assertThat(f.state.errorMessage).isNull()
    }

    @Test
    fun repeatOnActiveBetIsIgnored() = runTest(testDispatcher) {
        val f = fixture(BetStatus.ACTIVE)

        f.vm.onRepeatRequested()

        assertThat(f.repo.calls).isEmpty()
        assertThat(f.state.repeatedBetId).isNull()
    }

    @Test
    fun repeatWhenAPendingBetExistsShowsError() = runTest(testDispatcher) {
        val f = fixture(BetStatus.RESOLVED)
        f.repo.failure = InvalidBetStateException(55, BetStatus.PENDING_CONFIRMATION)

        f.vm.onRepeatRequested()

        assertThat(f.state.errorMessage).isEqualTo("Finish or discard the bet that's still being set up first.")
        assertThat(f.state.repeatedBetId).isNull()
        assertThat(f.state.isBusy).isFalse()
    }

    @Test
    fun repeatWithIoFailureShowsGenericError() = runTest(testDispatcher) {
        val f = fixture(BetStatus.RESOLVED)
        f.repo.failure = IOException("disk")

        f.vm.onRepeatRequested()

        assertThat(f.state.errorMessage).isEqualTo(GENERIC_ERROR_MESSAGE)
        assertThat(f.state.repeatedBetId).isNull()
        assertThat(f.state.isBusy).isFalse()
    }

    @Test
    fun `repeat while consent is open is ignored`() = runTest(testDispatcher) {
        val f = fixture(BetStatus.RESOLVED)
        f.vm.onReopenRequested()
        assertThat(f.state.consent).isNotNull()

        f.vm.onRepeatRequested()

        assertThat(f.repo.calls).isEmpty()
        assertThat(f.state.repeatedBetId).isNull()
        assertThat(f.state.consent).isNotNull()
    }

    @Test
    fun repeatWithBetCoinExceptionShowsItsUserMessage() = runTest(testDispatcher) {
        val f = fixture(BetStatus.RESOLVED)
        val failure = InvalidInputException("bad input")
        f.repo.failure = failure

        f.vm.onRepeatRequested()

        assertThat(f.state.errorMessage).isEqualTo(failure.toUserMessage())
        assertThat(f.state.repeatedBetId).isNull()
        assertThat(f.state.isBusy).isFalse()
    }

    @Test
    fun repeatWithSqliteFailureShowsGenericError() = runTest(testDispatcher) {
        val f = fixture(BetStatus.RESOLVED)
        f.repo.failure = SQLiteException("db")

        f.vm.onRepeatRequested()

        assertThat(f.state.errorMessage).isEqualTo(GENERIC_ERROR_MESSAGE)
        assertThat(f.state.repeatedBetId).isNull()
        assertThat(f.state.isBusy).isFalse()
    }

    @Test
    fun repeatWhileBusyIsIgnored() = runTest(testDispatcher) {
        val f = fixture(BetStatus.RESOLVED)
        f.repo.gate = CompletableDeferred()

        f.vm.onRepeatRequested()
        f.vm.onRepeatRequested()

        assertThat(f.state.isBusy).isTrue()
        assertThat(f.repo.calls).containsExactly("repeat(7)")
        f.repo.gate!!.complete(Unit)
        assertThat(f.state.isBusy).isFalse()
    }

    @Test
    fun onRepeatNavigatedClearsRepeatedBetId() = runTest(testDispatcher) {
        val f = fixture(BetStatus.RESOLVED)
        f.vm.onRepeatRequested()
        assertThat(f.state.repeatedBetId).isEqualTo(99L)

        f.vm.onRepeatNavigated()

        assertThat(f.state.repeatedBetId).isNull()
    }

    @Test
    fun repeatClearsAPreviousErrorMessage() = runTest(testDispatcher) {
        val f = fixture(BetStatus.RESOLVED)
        f.repo.failure = IOException("disk")
        f.vm.onRepeatRequested()
        assertThat(f.state.errorMessage).isNotNull()
        f.repo.failure = null

        f.vm.onRepeatRequested()

        assertThat(f.state.errorMessage).isNull()
        assertThat(f.state.repeatedBetId).isEqualTo(99L)
    }

    @Test
    fun formatNet_usesPlusMinusSignsAndPlusMinusZero() {
        assertThat(formatNet(150)).isEqualTo("+150")
        assertThat(formatNet(-200)).isEqualTo("\u2212200")
        assertThat(formatNet(0)).isEqualTo("\u00B10")
    }

    private companion object {
        const val BET_ID = 7L
        const val CREATED_AT = 1_700_000_000_000L
        const val RESOLVED_AT = 1_700_000_500_000L
    }
}
