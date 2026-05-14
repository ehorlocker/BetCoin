package com.betcoin.ui.onboarding

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose UI tests for [OnboardingScreen].
 */
@RunWith(AndroidJUnit4::class)
class OnboardingScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun onboardingScreen_showsWelcomeText() {
        val fakeViewModel = FakeOnboardingViewModel()
        composeTestRule.setContent {
            OnboardingScreen(
                viewModel = fakeViewModel,
                onOnboardingComplete = {},
            )
        }

        composeTestRule.onNodeWithText("Welcome to BetCoin").assertIsDisplayed()
        composeTestRule.onNodeWithText("Set up your master admin PIN").assertIsDisplayed()
    }

    @Test
    fun onboardingScreen_enterShortPin_showsError() {
        val fakeViewModel = FakeOnboardingViewModel()
        composeTestRule.setContent {
            OnboardingScreen(
                viewModel = fakeViewModel,
                onOnboardingComplete = {},
            )
        }

        composeTestRule.onNodeWithText("4-digit PIN").performTextInput("12")
        composeTestRule.onNodeWithText("Confirm").performClick()

        composeTestRule.onNodeWithText("PIN must be at least 4 digits").assertIsDisplayed()
    }

    @Test
    fun onboardingScreen_enterValidPin_callsSetAdminPin() {
        val fakeViewModel = FakeOnboardingViewModel()
        composeTestRule.setContent {
            OnboardingScreen(
                viewModel = fakeViewModel,
                onOnboardingComplete = {},
            )
        }

        composeTestRule.onNodeWithText("4-digit PIN").performTextInput("1234")
        composeTestRule.onNodeWithText("Confirm").performClick()

        assert(fakeViewModel.setAdminPinCalled)
        assert(fakeViewModel.lastPin == "1234")
    }
}

/**
 * Fake OnboardingViewModel for UI testing.
 */
class FakeOnboardingViewModel : OnboardingViewModel(
    settingsRepository = object : com.betcoin.data.repository.SettingsRepository {
        override suspend fun getAdminPinHash(): String? = null
        override suspend fun setAdminPin(pin: String) {}
        override suspend fun verifyAdminPin(pin: String): Boolean = false
        override suspend fun isFirstLaunch(): Boolean = true
    }
) {
    var setAdminPinCalled = false
    var lastPin: String? = null

    override fun setAdminPin(pin: String) {
        setAdminPinCalled = true
        lastPin = pin
        if (pin.length >= 4 && pin.all { it.isDigit() }) {
            _onboardingComplete.value = true
        } else if (pin.length < 4) {
            _errorMessage.value = "PIN must be at least 4 digits"
        } else {
            _errorMessage.value = "PIN must be numeric"
        }
    }
}
