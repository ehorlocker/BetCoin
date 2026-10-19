package com.betcoin.ui.betdetail

import androidx.lifecycle.SavedStateHandle
import com.betcoin.data.database.entity.Bet
import com.betcoin.data.database.entity.BetOutcome
import com.betcoin.data.database.entity.BetParticipant
import com.betcoin.data.database.entity.User
import com.betcoin.data.model.BetStatus
import com.betcoin.data.model.BetWithDetails
import com.betcoin.data.model.ParticipantWithUser
import com.betcoin.data.repository.BetRepository
import com.betcoin.navigation.Routes
import com.betcoin.ui.home.FakeBetRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

    private fun viewModel(repository: BetRepository) =
        BetDetailViewModel(SavedStateHandle(mapOf(Routes.BET_ID_ARG to BET_ID)), repository)

    /** Collects [BetDetailViewModel.uiState] (it's WhileSubscribed) and returns its value. */
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
            ParticipantRow(1, "Alice", false, "Jay", 150, ParticipantResult.PENDING, net = null),
            ParticipantRow(2, "Bob", false, "Kay", 200, ParticipantResult.PENDING, net = null),
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
