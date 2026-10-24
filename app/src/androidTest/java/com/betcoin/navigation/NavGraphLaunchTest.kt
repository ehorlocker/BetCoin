package com.betcoin.navigation

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/** Spec §4.1 launch branches, driven through the real MainActivity and NavGraph. */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class NavGraphLaunchTest : NavGraphTestBase() {

    @Test
    fun noAdminPin_startsOnOnboarding_thenCompletingItReplacesItWithHome() {
        launchApp()

        waitForText("Welcome to BetCoin")
        completeOnboarding()
        awaitHome()

        // Onboarding was popped: back from Home leaves the app instead of returning to it.
        pressBackAndAwaitAppExit()
    }

    @Test
    fun adminPinSetAndNoPendingBet_startsOnHome() {
        seedAdminPin()

        launchApp()

        awaitHome()
        assertTextGone("Welcome to BetCoin")
        assertTextGone("You have an unfinished bet")
    }

    @Test
    fun adminPinSetAndPendingBet_startsOnRecovery() {
        seedAdminPin()
        runBlocking { betRepository.createBet("Who wins Smash?", listOf("Jay", "Austin")) }

        launchApp()

        waitForText("You have an unfinished bet")
        waitForText("Who wins Smash?")
    }

    @Test
    fun recovery_discardBet_goesHomeAndBackLeavesTheApp() {
        seedAdminPin()
        runBlocking { betRepository.createBet("Who wins Smash?", listOf("Jay", "Austin")) }
        launchApp()

        clickText("Discard Bet")

        awaitHome()
        assertThat(runBlocking { betRepository.getPendingBet().first() }).isNull()
        pressBackAndAwaitAppExit()
    }

    @Test
    fun recovery_returnToBet_opensNewBetStepB_andBackReturnsToHome() {
        seedAdminPin()
        runBlocking { betRepository.createBet("Who wins Smash?", listOf("Jay", "Austin")) }
        launchApp()

        clickText("Return to Bet")

        waitForText("Step B", substring = true)
        waitForText("Who wins Smash?")
        // Recovery was popped and Home sits underneath New Bet; closing asks before discarding.
        clickText("Close")
        clickText("Discard")
        awaitHome()
        assertTextGone("You have an unfinished bet")
    }
}
