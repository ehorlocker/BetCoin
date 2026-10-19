package com.betcoin.ui.bethistory

import com.betcoin.data.database.entity.Bet
import com.betcoin.data.database.entity.BetOutcome
import com.betcoin.data.database.entity.BetParticipant
import com.betcoin.data.model.BetStatus
import com.betcoin.data.model.BetWithDetails
import com.betcoin.data.model.ParticipantWithUser
import com.betcoin.data.repository.BetRepository
import com.betcoin.ui.home.FakeBetRepository
import com.google.common.truth.Truth.assertThat
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

/** Serves [bets] from [getAllBets]; every other [BetRepository] method comes from [FakeBetRepository]. */
private class AllBetsRepository(
    private val bets: Flow<List<BetWithDetails>>,
) : BetRepository by FakeBetRepository() {
    override fun getAllBets(): Flow<List<BetWithDetails>> = bets
}

/** Tests for [BetHistoryViewModel]. */
@OptIn(ExperimentalCoroutinesApi::class)
class BetHistoryViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun bet(
        id: Long,
        status: BetStatus,
        createdAt: Long,
        prompt: String = "Bet $id",
        wagers: List<Long> = emptyList(),
    ): BetWithDetails {
        val outcome = BetOutcome(id = id * 10, betId = id, outcomeText = "Yes")
        return BetWithDetails(
            bet = Bet(id = id, prompt = prompt, status = status, createdAt = createdAt),
            outcomes = listOf(outcome),
            participants = wagers.mapIndexed { index, wager ->
                ParticipantWithUser(
                    participant = BetParticipant(
                        id = id * 100 + index,
                        betId = id,
                        userId = null,
                        outcomeId = outcome.id,
                        wagerAmount = wager,
                        deletedUsername = "p$index",
                    ),
                    user = null,
                    outcome = outcome,
                )
            },
        )
    }

    private fun TestScope.viewModelOf(bets: Flow<List<BetWithDetails>>): BetHistoryViewModel {
        val viewModel = BetHistoryViewModel(AllBetsRepository(bets))
        backgroundScope.launch { viewModel.uiState.collect {} }
        return viewModel
    }

    private val mixed = listOf(
        bet(1, BetStatus.ACTIVE, createdAt = 100),
        bet(2, BetStatus.RESOLVED, createdAt = 300),
        bet(3, BetStatus.PENDING_CONFIRMATION, createdAt = 400),
        bet(4, BetStatus.CANCELLED, createdAt = 200),
    )

    @Test
    fun initialState_isLoading() = runTest(testDispatcher) {
        val viewModel = viewModelOf(flow { awaitCancellation() })

        assertThat(viewModel.uiState.value.isLoading).isTrue()
    }

    @Test
    fun allFilter_excludesPending_sortedNewestFirst() = runTest(testDispatcher) {
        val viewModel = viewModelOf(MutableStateFlow(mixed))

        val state = viewModel.uiState.value
        assertThat(state.isLoading).isFalse()
        assertThat(state.rows.map { it.betId }).containsExactly(2L, 4L, 1L).inOrder()
    }

    @Test
    fun row_mapsPromptStatusDateCountAndPot() = runTest(testDispatcher) {
        val viewModel = viewModelOf(
            MutableStateFlow(
                listOf(bet(7, BetStatus.ACTIVE, createdAt = 555, prompt = "Who wins?", wagers = listOf(50, 100, 25))),
            ),
        )

        assertThat(viewModel.uiState.value.rows).containsExactly(
            BetHistoryRow(
                betId = 7,
                prompt = "Who wins?",
                status = BetStatus.ACTIVE,
                createdAt = 555,
                participantCount = 3,
                pot = 175,
            ),
        )
    }

    @Test
    fun row_withNoParticipants_hasZeroPotAndCount() = runTest(testDispatcher) {
        val viewModel = viewModelOf(MutableStateFlow(listOf(bet(1, BetStatus.ACTIVE, createdAt = 1))))

        val row = viewModel.uiState.value.rows.single()
        assertThat(row.participantCount).isEqualTo(0)
        assertThat(row.pot).isEqualTo(0L)
    }

    @Test
    fun selectFilter_active_showsOnlyActive() = runTest(testDispatcher) {
        val viewModel = viewModelOf(MutableStateFlow(mixed))

        viewModel.selectFilter(BetHistoryFilter.ACTIVE)

        assertThat(viewModel.uiState.value.filter).isEqualTo(BetHistoryFilter.ACTIVE)
        assertThat(viewModel.uiState.value.rows.map { it.betId }).containsExactly(1L)
    }

    @Test
    fun selectFilter_resolved_showsOnlyResolved() = runTest(testDispatcher) {
        val viewModel = viewModelOf(MutableStateFlow(mixed))

        viewModel.selectFilter(BetHistoryFilter.RESOLVED)

        assertThat(viewModel.uiState.value.filter).isEqualTo(BetHistoryFilter.RESOLVED)
        assertThat(viewModel.uiState.value.rows.map { it.betId }).containsExactly(2L)
    }

    @Test
    fun selectFilter_cancelled_showsOnlyCancelled() = runTest(testDispatcher) {
        val viewModel = viewModelOf(MutableStateFlow(mixed))

        viewModel.selectFilter(BetHistoryFilter.CANCELLED)

        assertThat(viewModel.uiState.value.filter).isEqualTo(BetHistoryFilter.CANCELLED)
        assertThat(viewModel.uiState.value.rows.map { it.betId }).containsExactly(4L)
    }

    @Test
    fun selectFilter_persistsAcrossRepositoryEmissions() = runTest(testDispatcher) {
        val source = MutableStateFlow(mixed)
        val viewModel = viewModelOf(source)
        viewModel.selectFilter(BetHistoryFilter.RESOLVED)

        source.value = mixed + bet(5, BetStatus.RESOLVED, createdAt = 500) + bet(6, BetStatus.ACTIVE, createdAt = 600)

        assertThat(viewModel.uiState.value.filter).isEqualTo(BetHistoryFilter.RESOLVED)
        assertThat(viewModel.uiState.value.rows.map { it.betId }).containsExactly(5L, 2L).inOrder()
    }

    @Test
    fun statusChange_movesBetBetweenTabs() = runTest(testDispatcher) {
        val source = MutableStateFlow(listOf(bet(1, BetStatus.ACTIVE, createdAt = 100)))
        val viewModel = viewModelOf(source)
        viewModel.selectFilter(BetHistoryFilter.ACTIVE)
        assertThat(viewModel.uiState.value.rows.map { it.betId }).containsExactly(1L)

        source.value = listOf(bet(1, BetStatus.RESOLVED, createdAt = 100))

        assertThat(viewModel.uiState.value.rows).isEmpty()
        viewModel.selectFilter(BetHistoryFilter.RESOLVED)
        assertThat(viewModel.uiState.value.rows.map { it.betId }).containsExactly(1L)
    }

    @Test
    fun repositoryError_setsIsError() = runTest(testDispatcher) {
        val viewModel = viewModelOf(flow { throw RuntimeException("boom") })

        assertThat(viewModel.uiState.value.isError).isTrue()
        assertThat(viewModel.uiState.value.isLoading).isFalse()
    }

    @Test
    fun filterEmptyMessages_matchSpecCopy() {
        assertThat(BetHistoryFilter.entries.map { it.label to it.emptyMessage }).containsExactly(
            "All" to "No bets yet",
            "Active" to "No active bets",
            "Resolved" to "No resolved bets",
            "Cancelled" to "No cancelled bets",
        ).inOrder()
    }
}
