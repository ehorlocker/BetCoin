package com.betcoin.devtools

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/** Tests for [DevToolsViewModel]. */
@OptIn(ExperimentalCoroutinesApi::class)
class DevToolsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fake: FakeDevToolsActions

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fake = FakeDevToolsActions()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.collectEvents(viewModel: DevToolsViewModel): List<DevToolsEvent> {
        val events = mutableListOf<DevToolsEvent>()
        viewModel.events.onEach { events.add(it) }.launchIn(backgroundScope)
        return events
    }

    @Test
    fun init_loadsInfo() = runTest(testDispatcher) {
        val viewModel = DevToolsViewModel(fake)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.info).isEqualTo(fake.info)
    }

    @Test
    fun request_setsPendingConfirmation_andDismissClears() = runTest(testDispatcher) {
        val viewModel = DevToolsViewModel(fake)
        advanceUntilIdle()

        viewModel.request(DevToolsAction.WIPE)
        assertThat(viewModel.uiState.value.pendingConfirmation).isEqualTo(DevToolsAction.WIPE)

        viewModel.dismissConfirmation()
        assertThat(viewModel.uiState.value.pendingConfirmation).isNull()
        assertThat(fake.calls).isEmpty()
    }

    @Test
    fun confirm_seed_setsRecoveryCodeAndMessage_andRefreshesInfo() = runTest(testDispatcher) {
        val viewModel = DevToolsViewModel(fake)
        advanceUntilIdle()
        fake.info = fake.info.copy(appVersion = "after")

        viewModel.request(DevToolsAction.SEED_SAMPLE_DATA)
        viewModel.confirm()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(fake.calls).containsExactly("seedSampleData")
        assertThat(state.recoveryCode).isEqualTo("ABCD-EFGH")
        assertThat(state.message).isEqualTo("Sample data seeded")
        assertThat(state.info?.appVersion).isEqualTo("after")
        assertThat(state.pendingConfirmation).isNull()
        assertThat(state.busy).isFalse()

        viewModel.onRecoveryCodeShown()
        viewModel.onMessageShown()
        assertThat(viewModel.uiState.value.recoveryCode).isNull()
        assertThat(viewModel.uiState.value.message).isNull()
    }

    @Test
    fun confirm_pendingBet_success_emitsCloseAppAfterMessage() = runTest(testDispatcher) {
        val viewModel = DevToolsViewModel(fake)
        val events = collectEvents(viewModel)
        advanceUntilIdle()

        viewModel.request(DevToolsAction.SEED_PENDING_BET)
        viewModel.confirm()
        advanceTimeBy(1_000)
        runCurrent()

        assertThat(viewModel.uiState.value.message).isEqualTo("Pending bet added. Closing app…")
        assertThat(events).isEmpty()

        advanceTimeBy(600)
        runCurrent()
        assertThat(events).containsExactly(DevToolsEvent.CloseApp)
        assertThat(viewModel.uiState.value.busy).isFalse()
    }

    @Test
    fun confirm_wipe_emitsRestartApp() = runTest(testDispatcher) {
        val viewModel = DevToolsViewModel(fake)
        val events = collectEvents(viewModel)
        advanceUntilIdle()

        viewModel.request(DevToolsAction.WIPE)
        viewModel.confirm()
        advanceTimeBy(1_000)
        runCurrent()
        assertThat(viewModel.uiState.value.message).isEqualTo("Dev database wiped. Restarting…")
        assertThat(events).isEmpty()

        advanceTimeBy(600)
        runCurrent()
        assertThat(events).containsExactly(DevToolsEvent.RestartApp)
    }

    @Test
    fun confirm_resetPin_showsMessage_noEvent() = runTest(testDispatcher) {
        val viewModel = DevToolsViewModel(fake)
        val events = collectEvents(viewModel)
        advanceUntilIdle()

        viewModel.request(DevToolsAction.RESET_ADMIN_PIN)
        viewModel.confirm()
        advanceUntilIdle()

        assertThat(fake.calls).containsExactly("resetAdminPin")
        assertThat(viewModel.uiState.value.message)
            .isEqualTo("Admin PIN cleared. Next launch opens Onboarding")
        assertThat(events).isEmpty()
    }

    @Test
    fun confirm_failure_showsErrorMessage_noEvent_notBusy() = runTest(testDispatcher) {
        fake.failure = IllegalStateException("Need at least 2 players")
        val viewModel = DevToolsViewModel(fake)
        val events = collectEvents(viewModel)
        advanceUntilIdle()

        viewModel.request(DevToolsAction.SEED_PENDING_BET)
        viewModel.confirm()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.message)
            .isEqualTo("Couldn't seed pending bet: Need at least 2 players")
        assertThat(viewModel.uiState.value.busy).isFalse()
        assertThat(events).isEmpty()
    }

    @Test
    fun confirm_failure_messagePerAction() = runTest(testDispatcher) {
        fake.failure = IllegalStateException("boom")
        val viewModel = DevToolsViewModel(fake)
        advanceUntilIdle()

        val expected = mapOf(
            DevToolsAction.SEED_SAMPLE_DATA to "Couldn't seed sample data: boom",
            DevToolsAction.WIPE to "Couldn't wipe the dev database: boom",
            DevToolsAction.RESET_ADMIN_PIN to "Couldn't reset the admin PIN: boom",
        )
        for ((action, message) in expected) {
            viewModel.request(action)
            viewModel.confirm()
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.message).isEqualTo(message)
        }
    }

    @Test
    fun request_whileBusy_isIgnored() = runTest(testDispatcher) {
        val gate = CompletableDeferred<Unit>()
        fake.gate = gate
        val viewModel = DevToolsViewModel(fake)
        advanceUntilIdle()

        viewModel.request(DevToolsAction.RESET_ADMIN_PIN)
        viewModel.confirm()
        runCurrent()
        assertThat(viewModel.uiState.value.busy).isTrue()

        viewModel.request(DevToolsAction.WIPE)
        assertThat(viewModel.uiState.value.pendingConfirmation).isNull()
        viewModel.confirm()
        gate.complete(Unit)
        advanceUntilIdle()

        assertThat(fake.calls).containsExactly("resetAdminPin")
    }
}

private class FakeDevToolsActions : DevToolsActions {
    var info = DevDatabaseInfo("db", 1, listOf("users" to 0L), "1.0")
    var failure: Exception? = null
    var gate: CompletableDeferred<Unit>? = null
    val calls = mutableListOf<String>()

    private suspend fun run(name: String) {
        calls.add(name)
        gate?.await()
        failure?.let { throw it }
    }

    override suspend fun seedSampleData(): String {
        run("seedSampleData")
        return "ABCD-EFGH"
    }

    override suspend fun seedPendingBet() = run("seedPendingBet")

    override suspend fun wipe() = run("wipe")

    override suspend fun resetAdminPin() = run("resetAdminPin")

    override suspend fun info(): DevDatabaseInfo = info
}
