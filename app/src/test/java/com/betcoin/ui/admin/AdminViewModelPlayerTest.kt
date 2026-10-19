package com.betcoin.ui.admin

import com.betcoin.data.database.entity.User
import com.betcoin.data.model.DuplicateUsernameException
import com.betcoin.data.model.InvalidInputException
import com.betcoin.data.repository.UserRepository
import com.betcoin.testing.FakeUserRepository
import com.betcoin.ui.common.GENERIC_ERROR_MESSAGE
import com.betcoin.ui.common.toUserMessage
import com.betcoin.ui.components.formatCoins
import com.betcoin.ui.onboarding.FakeSettingsRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

/** Tests for the Player Management actions of [AdminViewModel]. */
@OptIn(ExperimentalCoroutinesApi::class)
class AdminViewModelPlayerTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fake: FakeUserRepository
    private lateinit var viewModel: AdminViewModel

    private var deleteCalls = 0
    private var bailoutCalls = 0
    private var setBalanceCalls = 0
    private var renameCalls = 0
    private var resetPinCalls = 0
    private var gate: CompletableDeferred<Unit>? = null
    private var failWith: Throwable? = null

    private lateinit var alice: User
    private lateinit var bob: User

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fake = FakeUserRepository()
        alice = User(id = 1, username = "Alice", pinHash = FakeUserRepository.PLACEHOLDER_HASH, createdAt = 0L)
        bob = User(id = 2, username = "Bob", pinHash = FakeUserRepository.PLACEHOLDER_HASH, createdAt = 0L)
        fake.users = listOf(alice, bob)
        val wrapped = object : UserRepository by fake {
            override suspend fun deleteUser(userId: Long) {
                deleteCalls++
                enter()
                fake.deleteUser(userId)
            }

            override suspend fun forceBailout(userId: Long) {
                bailoutCalls++
                enter()
                fake.forceBailout(userId)
            }

            override suspend fun setBalance(userId: Long, newBalance: Long) {
                setBalanceCalls++
                enter()
                fake.setBalance(userId, newBalance)
            }

            override suspend fun updateUsername(userId: Long, newUsername: String) {
                renameCalls++
                enter()
                fake.updateUsername(userId, newUsername)
            }

            override suspend fun resetPin(userId: Long, newPin: String) {
                resetPinCalls++
                enter()
                fake.resetPin(userId, newPin)
            }

            private suspend fun enter() {
                gate?.await()
                failWith?.let { throw it }
            }
        }
        viewModel = AdminViewModel(FakeSettingsRepository(), wrapped)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val state get() = viewModel.uiState.value

    /** Keeps [AdminViewModel.players] subscribed so its value is live. */
    private fun TestScope.collectPlayers() {
        backgroundScope.launch(testDispatcher) { viewModel.players.collect {} }
    }

    private fun user(id: Long) = fake.users.first { it.id == id }

    @Test
    fun players_emitsRepositoryUsers() = runTest(testDispatcher) {
        collectPlayers()
        advanceUntilIdle()

        assertThat(viewModel.players.value.map { it.username }).containsExactly("Alice", "Bob").inOrder()
    }

    @Test
    fun delete_confirm_deletesUser_closesDialog_postsMessage() = runTest(testDispatcher) {
        viewModel.onDeletePlayerClicked(alice)
        viewModel.onPlayerDialogConfirm()
        advanceUntilIdle()

        assertThat(fake.users.map { it.username }).containsExactly("Bob")
        assertThat(state.playerDialog).isNull()
        assertThat(state.message).isEqualTo("Deleted Alice")
    }

    @Test
    fun delete_withActiveBet_keepsUser_closesDialog_postsSpecificMessage() = runTest(testDispatcher) {
        fake.usersInActiveBets = setOf(alice.id)
        viewModel.onDeletePlayerClicked(alice)
        viewModel.onPlayerDialogConfirm()
        advanceUntilIdle()

        assertThat(fake.users).hasSize(2)
        assertThat(state.playerDialog).isNull()
        assertThat(state.message).isEqualTo("Alice is in an active bet. Resolve or cancel it first.")
    }

    @Test
    fun resetBalance_confirm_addsBailout() = runTest(testDispatcher) {
        viewModel.onResetBalanceClicked(alice)
        viewModel.onPlayerDialogConfirm()
        advanceUntilIdle()

        val updated = user(alice.id)
        assertThat(updated.balance).isEqualTo(alice.balance + 1000)
        assertThat(updated.bailoutCount).isEqualTo(alice.bailoutCount + 1)
        assertThat(updated.totalDebt).isEqualTo(alice.totalDebt + 1000)
        assertThat(state.playerDialog).isNull()
        assertThat(state.message).isEqualTo("Alice got a 1,000 BetCoin bailout")
    }

    @Test
    fun setBalance_acceptsNegativeValue() = runTest(testDispatcher) {
        viewModel.onSetBalanceClicked(alice)
        assertThat((state.playerDialog as PlayerDialogState.SetBalance).input).isEqualTo("1000")
        viewModel.onPlayerDialogInputChanged("-250")
        viewModel.onPlayerDialogConfirm()
        advanceUntilIdle()

        assertThat(user(alice.id).balance).isEqualTo(-250)
        assertThat(state.playerDialog).isNull()
        assertThat(state.message).isEqualTo("Alice's balance set to -250")
    }

    @Test
    fun setBalance_invalidInput_showsError_noCall() = runTest(testDispatcher) {
        for (input in listOf("", "-")) {
            viewModel.onSetBalanceClicked(alice)
            viewModel.onPlayerDialogInputChanged(input)
            viewModel.onPlayerDialogConfirm()
            advanceUntilIdle()

            assertThat((state.playerDialog as PlayerDialogState.SetBalance).error)
                .isEqualTo(AdminViewModel.ERROR_BALANCE_INVALID)
        }
        assertThat(setBalanceCalls).isEqualTo(0)
    }

    @Test
    fun setBalance_inputIsSanitized() = runTest(testDispatcher) {
        viewModel.onSetBalanceClicked(alice)
        viewModel.onPlayerDialogInputChanged("12a-3")
        assertThat((state.playerDialog as PlayerDialogState.SetBalance).input).isEqualTo("123")
        viewModel.onPlayerDialogInputChanged("--5")
        assertThat((state.playerDialog as PlayerDialogState.SetBalance).input).isEqualTo("-5")
        viewModel.onPlayerDialogInputChanged("1234567890123456789")
        assertThat((state.playerDialog as PlayerDialogState.SetBalance).input).hasLength(15)
    }

    private fun rename(user: User, input: String) {
        viewModel.onEditUsernameClicked(user)
        viewModel.onPlayerDialogInputChanged(input)
        viewModel.onPlayerDialogConfirm()
    }

    @Test
    fun editUsername_trimsAndSaves() = runTest(testDispatcher) {
        collectPlayers()
        advanceUntilIdle()
        rename(alice, "  Alicia  ")
        advanceUntilIdle()

        assertThat(user(alice.id).username).isEqualTo("Alicia")
        assertThat(state.playerDialog).isNull()
        assertThat(state.message).isEqualTo("Renamed to Alicia")
    }

    @Test
    fun editUsername_tooLong_showsLengthError() = runTest(testDispatcher) {
        collectPlayers()
        advanceUntilIdle()
        rename(alice, "A".repeat(21))
        advanceUntilIdle()

        assertThat((state.playerDialog as PlayerDialogState.EditUsername).error)
            .isEqualTo(AdminViewModel.ERROR_USERNAME_LENGTH)
        assertThat(renameCalls).isEqualTo(0)
    }

    @Test
    fun editUsername_blank_showsLengthError() = runTest(testDispatcher) {
        collectPlayers()
        advanceUntilIdle()
        rename(alice, "   ")
        advanceUntilIdle()

        assertThat((state.playerDialog as PlayerDialogState.EditUsername).error)
            .isEqualTo(AdminViewModel.ERROR_USERNAME_LENGTH)
        assertThat(renameCalls).isEqualTo(0)
    }

    @Test
    fun editUsername_otherUsersNameInOtherCase_showsTaken_noCall() = runTest(testDispatcher) {
        collectPlayers()
        advanceUntilIdle()
        rename(alice, "bob")
        advanceUntilIdle()

        assertThat((state.playerDialog as PlayerDialogState.EditUsername).error)
            .isEqualTo("Username already exists")
        assertThat(renameCalls).isEqualTo(0)
    }

    @Test
    fun editUsername_ownNameCaseChange_isSaved() = runTest(testDispatcher) {
        collectPlayers()
        advanceUntilIdle()
        rename(alice, "ALICE")
        advanceUntilIdle()

        assertThat(user(alice.id).username).isEqualTo("ALICE")
        assertThat(renameCalls).isEqualTo(1)
        assertThat(state.playerDialog).isNull()
    }

    @Test
    fun editUsername_unchanged_closesWithoutCall() = runTest(testDispatcher) {
        collectPlayers()
        advanceUntilIdle()
        rename(alice, " Alice ")
        advanceUntilIdle()

        assertThat(renameCalls).isEqualTo(0)
        assertThat(state.playerDialog).isNull()
    }

    @Test
    fun editUsername_repositoryDuplicate_mapsInline() = runTest(testDispatcher) {
        // players not collected, so the pre-check cannot catch it
        failWith = DuplicateUsernameException("Bob")
        rename(alice, "Bobby")
        advanceUntilIdle()

        val dialog = state.playerDialog as PlayerDialogState.EditUsername
        assertThat(dialog.error).isEqualTo("Username already exists")
        assertThat(dialog.isSaving).isFalse()
    }

    private fun resetPin(pin: String, confirm: String) {
        viewModel.onResetPinClicked(alice)
        viewModel.onPlayerDialogInputChanged(pin)
        viewModel.onPlayerDialogConfirmPinChanged(confirm)
        viewModel.onPlayerDialogConfirm()
    }

    @Test
    fun resetPin_requiresFourDigits() = runTest(testDispatcher) {
        resetPin("12", "12")
        advanceUntilIdle()

        assertThat((state.playerDialog as PlayerDialogState.ResetPin).error)
            .isEqualTo(AdminViewModel.ERROR_PIN_LENGTH)
        assertThat(resetPinCalls).isEqualTo(0)
    }

    @Test
    fun resetPin_mismatch_showsError() = runTest(testDispatcher) {
        resetPin("1234", "1235")
        advanceUntilIdle()

        assertThat((state.playerDialog as PlayerDialogState.ResetPin).error)
            .isEqualTo(AdminViewModel.ERROR_PIN_MISMATCH)
        assertThat(resetPinCalls).isEqualTo(0)
    }

    @Test
    fun resetPin_match_resetsPin() = runTest(testDispatcher) {
        resetPin("a9876", "9876")
        advanceUntilIdle()

        assertThat(fake.verifyPin(alice.id, "9876")).isTrue()
        assertThat(state.playerDialog).isNull()
        assertThat(state.message).isEqualTo("Alice's PIN reset")
    }

    @Test
    fun confirmWhileSaving_isIgnored_oneRepositoryCall() = runTest(testDispatcher) {
        gate = CompletableDeferred()
        viewModel.onResetBalanceClicked(alice)
        viewModel.onPlayerDialogConfirm()
        viewModel.onPlayerDialogConfirm()
        advanceUntilIdle()
        assertThat(bailoutCalls).isEqualTo(1)

        gate!!.complete(Unit)
        advanceUntilIdle()
        assertThat(bailoutCalls).isEqualTo(1)
        assertThat(state.playerDialog).isNull()
    }

    @Test
    fun confirmWhileSaving_form_oneRepositoryCall() = runTest(testDispatcher) {
        gate = CompletableDeferred()
        viewModel.onSetBalanceClicked(alice)
        viewModel.onPlayerDialogInputChanged("5")
        viewModel.onPlayerDialogConfirm()
        viewModel.onPlayerDialogConfirm()
        advanceUntilIdle()
        gate!!.complete(Unit)
        advanceUntilIdle()

        assertThat(setBalanceCalls).isEqualTo(1)
    }

    @Test
    fun dismissWhileSaving_isIgnored() = runTest(testDispatcher) {
        gate = CompletableDeferred()
        viewModel.onDeletePlayerClicked(alice)
        viewModel.onPlayerDialogConfirm()
        viewModel.onPlayerDialogDismiss()
        assertThat(state.playerDialog).isNotNull()

        gate!!.complete(Unit)
        advanceUntilIdle()
        assertThat(state.playerDialog).isNull()
    }

    @Test
    fun dismiss_closesDialog() = runTest(testDispatcher) {
        viewModel.onDeletePlayerClicked(alice)
        viewModel.onPlayerDialogDismiss()

        assertThat(state.playerDialog).isNull()
    }

    @Test
    fun ioException_mapsToGenericError() = runTest(testDispatcher) {
        failWith = IOException("disk")
        viewModel.onResetBalanceClicked(alice)
        viewModel.onPlayerDialogConfirm()
        advanceUntilIdle()
        assertThat(state.playerDialog).isNull()
        assertThat(state.message).isEqualTo(GENERIC_ERROR_MESSAGE)

        viewModel.onSetBalanceClicked(alice)
        viewModel.onPlayerDialogInputChanged("5")
        viewModel.onPlayerDialogConfirm()
        advanceUntilIdle()
        assertThat((state.playerDialog as PlayerDialogState.SetBalance).error).isEqualTo(GENERIC_ERROR_MESSAGE)
    }

    @Test
    fun staleUser_deletedBeforeConfirm_mapsError_noCrash() = runTest(testDispatcher) {
        viewModel.onSetBalanceClicked(alice)
        fake.users = fake.users.filterNot { it.id == alice.id }
        viewModel.onPlayerDialogInputChanged("5")
        viewModel.onPlayerDialogConfirm()
        advanceUntilIdle()

        val dialog = state.playerDialog as PlayerDialogState.SetBalance
        assertThat(dialog.error).isEqualTo(InvalidInputException("x").toUserMessage())
        assertThat(dialog.isSaving).isFalse()
    }

    @Test
    fun staleUser_deleted_closesDialog_postsMappedMessage() = runTest(testDispatcher) {
        viewModel.onDeletePlayerClicked(alice)
        fake.users = fake.users.filterNot { it.id == alice.id }
        viewModel.onPlayerDialogConfirm()
        advanceUntilIdle()

        assertThat(state.playerDialog).isNull()
        assertThat(state.message).isEqualTo(InvalidInputException("x").toUserMessage())
    }

    @Test
    fun setBalance_message_usesGrouping() = runTest(testDispatcher) {
        viewModel.onSetBalanceClicked(alice)
        viewModel.onPlayerDialogInputChanged("1234567")
        viewModel.onPlayerDialogConfirm()
        advanceUntilIdle()

        assertThat(state.message).isEqualTo("Alice's balance set to ${formatCoins(1234567)}")
    }

    @Test
    fun confirmWhileSaving_delete_oneRepositoryCall() = runTest(testDispatcher) {
        gate = CompletableDeferred()
        viewModel.onDeletePlayerClicked(alice)
        viewModel.onPlayerDialogConfirm()
        viewModel.onPlayerDialogConfirm()
        advanceUntilIdle()
        gate!!.complete(Unit)
        advanceUntilIdle()

        assertThat(deleteCalls).isEqualTo(1)
    }

    @Test
    fun confirmWhileSaving_editUsername_oneRepositoryCall() = runTest(testDispatcher) {
        collectPlayers()
        advanceUntilIdle()
        gate = CompletableDeferred()
        rename(alice, "Alicia")
        viewModel.onPlayerDialogConfirm()
        advanceUntilIdle()
        gate!!.complete(Unit)
        advanceUntilIdle()

        assertThat(renameCalls).isEqualTo(1)
    }

    @Test
    fun confirmWhileSaving_resetPin_oneRepositoryCall() = runTest(testDispatcher) {
        gate = CompletableDeferred()
        resetPin("1234", "1234")
        viewModel.onPlayerDialogConfirm()
        advanceUntilIdle()
        gate!!.complete(Unit)
        advanceUntilIdle()

        assertThat(resetPinCalls).isEqualTo(1)
    }

    @Test
    fun openDialogWhileBusy_isIgnored() = runTest(testDispatcher) {
        gate = CompletableDeferred()
        viewModel.onDeletePlayerClicked(alice)
        viewModel.onPlayerDialogConfirm()
        viewModel.onResetBalanceClicked(bob)

        assertThat(state.playerDialog).isInstanceOf(PlayerDialogState.ConfirmDelete::class.java)
        gate!!.complete(Unit)
        advanceUntilIdle()
    }
}
