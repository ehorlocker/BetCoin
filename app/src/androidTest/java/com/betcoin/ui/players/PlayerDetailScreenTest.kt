package com.betcoin.ui.players

import com.betcoin.data.avatar.FakeAvatarStore
import com.betcoin.data.avatar.FakeCameraCaptureStore
import com.betcoin.data.model.ThemePreference
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.betcoin.navigation.Routes
import com.betcoin.ui.components.PIN_INPUT_TAG
import com.betcoin.ui.theme.BetCoinTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose UI tests for [PlayerDetailScreen].
 */
@RunWith(AndroidJUnit4::class)
class PlayerDetailScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val repository = PlayersTestRepository()

    private fun showScreen(playerId: Long) {
        val viewModel = PlayerDetailViewModel(
            SavedStateHandle(mapOf(Routes.PLAYER_ID_ARG to playerId)),
            repository,
            FakeAvatarStore(),
            FakeCameraCaptureStore(),
        )
        composeTestRule.setContent {
            BetCoinTheme(themePreference = ThemePreference.DARK) {
                PlayerDetailScreen(viewModel = viewModel)
            }
        }
    }

    private fun assertStat(label: String, value: String) {
        composeTestRule.onNode(hasText(label) and hasText(value)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun showsEveryStat() {
        val id = repository.addPlayer("Alice", balance = 700, totalDebt = 1000)
        repository.users.value = repository.users.value.map {
            it.copy(totalWins = 5, totalLosses = 2, totalEarnings = 640, totalLost = 300, bailoutCount = 1)
        }
        repository.lockedBalances.value = mapOf(id to 250L)
        showScreen(id)

        composeTestRule.onNodeWithText("Alice").assertIsDisplayed()
        assertStat("Balance", "700")
        assertStat("Net worth", "-300")
        assertStat("W-L", "5-2")
        assertStat("Total earnings", "640")
        assertStat("Total lost", "300")
        assertStat("Bailouts", "1")
        assertStat("Total debt", "1,000")
        assertStat("Locked in active bets", "250")
    }

    @Test
    fun notEligible_hidesBailoutButton() {
        val id = repository.addPlayer("Alice", balance = 60)
        repository.lockedBalances.value = mapOf(id to 40L)
        showScreen(id)

        composeTestRule.onNodeWithText("Alice").assertIsDisplayed()
        composeTestRule.onNodeWithText("Bailout").assertDoesNotExist()
    }

    @Test
    fun unknownPlayer_showsNotFound() {
        showScreen(99L)

        composeTestRule.onNodeWithText("This player no longer exists.").assertIsDisplayed()
    }

    @Test
    fun bailout_cancelledPin_changesNothing() {
        val id = repository.addPlayer("Alice", pin = "2468", balance = 50)
        showScreen(id)

        composeTestRule.onNodeWithText("Bailout").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Alice, enter your PIN").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").performClick()

        composeTestRule.onNodeWithText("Alice, enter your PIN").assertDoesNotExist()
        assertThat(repository.users.value.single().bailoutCount).isEqualTo(0)
    }

    @Test
    fun bailout_wrongPin_showsErrorAndChangesNothing() {
        val id = repository.addPlayer("Alice", pin = "2468", balance = 50)
        showScreen(id)

        composeTestRule.onNodeWithText("Bailout").performScrollTo().performClick()
        composeTestRule.onNodeWithTag(PIN_INPUT_TAG).performTextInput("1111")

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodes(hasText("Wrong PIN")).fetchSemanticsNodes().isNotEmpty()
        }
        assertThat(repository.users.value.single().bailoutCount).isEqualTo(0)
    }

    @Test
    fun bailout_correctPin_grantsBailoutAndHidesButton() {
        val id = repository.addPlayer("Alice", pin = "2468", balance = 50)
        showScreen(id)

        composeTestRule.onNodeWithText("Bailout").performScrollTo().performClick()
        composeTestRule.onNodeWithTag(PIN_INPUT_TAG).performTextInput("2468")

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            repository.users.value.single().bailoutCount == 1
        }
        assertStat("Balance", "1,050")
        assertStat("Total debt", "1,000")
        composeTestRule.onNodeWithText("Bailout").assertDoesNotExist()
    }

    private fun loadedState(isUpdatingPicture: Boolean = false) = PlayerDetailViewModel.UiState.Loaded(
        stats = PlayerDetailViewModel.PlayerStats(
            username = "Alice",
            balance = 500,
            totalWins = 0,
            totalLosses = 0,
            totalEarnings = 0,
            totalLost = 0,
            bailoutCount = 0,
            totalDebt = 0,
            lockedBalance = 0,
        ),
        isUpdatingPicture = isUpdatingPicture,
    )

    private fun showContent(state: PlayerDetailViewModel.UiState.Loaded, onChangePicture: () -> Unit = {}) {
        composeTestRule.setContent {
            BetCoinTheme(themePreference = ThemePreference.DARK) {
                PlayerDetailContent(state = state, onBailout = {}, onChangePicture = onChangePicture)
            }
        }
    }

    private fun showSheet(
        hasPicture: Boolean,
        canTakePhoto: Boolean,
        onGallery: () -> Unit = {},
        onTakePhoto: () -> Unit = {},
        onRemove: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            BetCoinTheme(themePreference = ThemePreference.DARK) {
                ProfilePictureOptionsSheet(
                    hasProfilePicture = hasPicture,
                    canTakePhoto = canTakePhoto,
                    onGallery = onGallery,
                    onTakePhoto = onTakePhoto,
                    onRemove = onRemove,
                    onDismiss = {},
                )
            }
        }
    }

    @Test
    fun content_showsAvatarAndChangePictureButton() {
        var clicked = false
        showContent(loadedState()) { clicked = true }

        composeTestRule.onNodeWithText("Change Profile Picture").assertIsDisplayed().performClick()
        assertThat(clicked).isTrue()
    }

    @Test
    fun changePictureButton_disabledWhileUpdating() {
        showContent(loadedState(isUpdatingPicture = true))

        composeTestRule.onNodeWithText("Change Profile Picture").assertIsNotEnabled()
    }

    @Test
    fun changePictureButton_enabledWhenIdle() {
        showContent(loadedState())

        composeTestRule.onNodeWithText("Change Profile Picture").assertIsEnabled()
    }

    @Test
    fun sheet_withPictureAndCamera_showsAllThreeOptions() {
        showSheet(hasPicture = true, canTakePhoto = true)

        composeTestRule.onNodeWithText("Select from Gallery").assertIsDisplayed()
        composeTestRule.onNodeWithText("Take Photo").assertIsDisplayed()
        composeTestRule.onNodeWithText("Remove Photo").assertIsDisplayed()
    }

    @Test
    fun sheet_noPicture_hidesRemovePhoto() {
        showSheet(hasPicture = false, canTakePhoto = true)

        composeTestRule.onNodeWithText("Select from Gallery").assertIsDisplayed()
        composeTestRule.onNodeWithText("Remove Photo").assertDoesNotExist()
    }

    @Test
    fun sheet_noCamera_hidesTakePhoto() {
        showSheet(hasPicture = true, canTakePhoto = false)

        composeTestRule.onNodeWithText("Select from Gallery").assertIsDisplayed()
        composeTestRule.onNodeWithText("Take Photo").assertDoesNotExist()
    }

    @Test
    fun sheet_optionClicks_invokeCallbacks() {
        var gallery = 0
        var photo = 0
        var remove = 0
        showSheet(
            hasPicture = true,
            canTakePhoto = true,
            onGallery = { gallery++ },
            onTakePhoto = { photo++ },
            onRemove = { remove++ },
        )

        composeTestRule.onNodeWithText("Select from Gallery").performClick()
        composeTestRule.onNodeWithText("Take Photo").performClick()
        composeTestRule.onNodeWithText("Remove Photo").performClick()

        assertThat(listOf(gallery, photo, remove)).containsExactly(1, 1, 1)
    }
}
