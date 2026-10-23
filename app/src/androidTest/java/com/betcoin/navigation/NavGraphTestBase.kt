package com.betcoin.navigation

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import com.betcoin.MainActivity
import com.betcoin.data.repository.BetRepository
import com.betcoin.data.repository.SettingsRepository
import com.betcoin.data.repository.UserRepository
import dagger.hilt.android.testing.HiltAndroidRule
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import javax.inject.Inject

const val ADMIN_PIN = "1234"

/**
 * Base for end-to-end navigation tests: Hilt injects the real repositories (backed by the
 * in-memory database from `TestDatabaseModule`), a test seeds data first, then calls
 * [launchApp] to start the real [MainActivity] and its real NavGraph.
 */
abstract class NavGraphTestBase {

    private val hiltRule = HiltAndroidRule(this)
    private val composeRule = createEmptyComposeRule()

    @get:Rule
    val rules: TestRule = RuleChain.outerRule(hiltRule).around(composeRule)

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var userRepository: UserRepository
    @Inject lateinit var betRepository: BetRepository

    protected val compose get() = composeRule
    protected var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun injectDependencies() {
        hiltRule.inject()
    }

    @After
    fun closeActivity() {
        // Let a popped destination finish its exit transition before the activity is destroyed.
        compose.waitForIdle()
        Thread.sleep(SETTLE_MS)
        scenario?.close()
    }

    protected fun launchApp() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    /** Admin PIN set, so the app does not start on Onboarding. */
    protected fun seedAdminPin() = runBlocking { settingsRepository.setAdminPin(ADMIN_PIN) }

    protected fun seedPlayers(vararg namesAndPins: Pair<String, String>) = runBlocking {
        namesAndPins.map { (name, pin) -> userRepository.createUser(name, pin) }
    }

    protected fun waitForText(text: String, substring: Boolean = false) {
        compose.waitUntil(WAIT_MS) {
            compose.onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    protected fun waitForTag(tag: String) {
        compose.waitUntil(WAIT_MS) {
            compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    protected fun assertTextGone(text: String, substring: Boolean = false) {
        compose.waitUntil(WAIT_MS) {
            compose.onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isEmpty()
        }
    }

    protected fun clickTag(tag: String) {
        waitForTag(tag)
        compose.onNodeWithTag(tag).performClick()
    }

    protected fun clickText(text: String) {
        waitForText(text)
        compose.onNodeWithText(text).performClick()
    }

    protected fun pressBack() {
        Espresso.pressBack()
        compose.waitForIdle()
    }

    /** Back from the root destination finishes the activity; Espresso.pressBack would throw. */
    protected fun pressBackAndAwaitAppExit() {
        Espresso.pressBackUnconditionally()
        compose.waitUntil(WAIT_MS) { scenario!!.state == Lifecycle.State.DESTROYED }
    }

    protected fun awaitHome() = waitForTag("home_new_bet")

    /** Completes Onboarding with [ADMIN_PIN] and acknowledges the recovery code. */
    protected fun completeOnboarding() {
        waitForText("Welcome to BetCoin")
        compose.onNodeWithTag("onboarding_pin").performTextInput(ADMIN_PIN)
        compose.onNodeWithTag("onboarding_pin_confirm").performTextInput(ADMIN_PIN)
        compose.onNodeWithTag("onboarding_set_pin").performClick()
        clickText("I've written this down")
        clickText("Continue")
    }

    companion object {
        const val WAIT_MS = 15_000L
        const val SETTLE_MS = 1_000L
    }
}
