package com.betcoin.ui.components

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.test.assertCountEquals
import com.betcoin.data.model.ThemePreference
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import com.betcoin.ui.theme.BetCoinTheme
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.EventListener
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Compose UI tests for BetCoinAvatar.
 */
class BetCoinAvatarTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun avatar_displaysInitials() {
        composeTestRule.setContent {
            BetCoinTheme(themePreference = ThemePreference.DARK) {
                BetCoinAvatar(initials = "JD")
            }
        }

        composeTestRule.onNodeWithText("JD")
            .assertIsDisplayed()
    }

    @Test
    fun avatar_displaysSingleInitial() {
        composeTestRule.setContent {
            BetCoinTheme(themePreference = ThemePreference.DARK) {
                BetCoinAvatar(initials = "A")
            }
        }

        composeTestRule.onNodeWithText("A")
            .assertIsDisplayed()
    }

    @Test
    fun avatar_displaysUppercaseInitials() {
        composeTestRule.setContent {
            BetCoinTheme(themePreference = ThemePreference.DARK) {
                BetCoinAvatar(initials = "ab")
            }
        }

        composeTestRule.onNodeWithText("AB")
            .assertIsDisplayed()
    }

    @After
    fun resetImageLoader() {
        SingletonImageLoader.reset()
    }

    @Test
    fun avatar_nullModel_showsInitials() {
        composeTestRule.setContent {
            BetCoinTheme {
                BetCoinAvatar(initials = "JD", imageModel = null)
            }
        }

        composeTestRule.onNodeWithText("JD").assertIsDisplayed()
    }

    @Test
    fun avatar_unloadableModel_showsInitials() {
        val errored = AtomicBoolean(false)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        SingletonImageLoader.setUnsafe {
            ImageLoader.Builder(context)
                .eventListener(object : EventListener() {
                    override fun onError(request: ImageRequest, result: ErrorResult) {
                        errored.set(true)
                    }
                })
                .build()
        }
        composeTestRule.setContent {
            BetCoinTheme {
                BetCoinAvatar(initials = "JD", imageModel = File("/nonexistent.jpg"))
            }
        }

        composeTestRule.waitUntil(5_000) { errored.get() }
        composeTestRule.waitForIdle()
        composeTestRule.onAllNodesWithContentDescription("Profile picture").assertCountEquals(0)
        composeTestRule.onNodeWithText("JD").assertIsDisplayed()
    }

    @Test
    fun avatar_loadedImage_showsImage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "avatar_test.jpg")
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.RED)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }

        composeTestRule.setContent {
            BetCoinTheme {
                BetCoinAvatar(initials = "JD", imageModel = file)
            }
        }

        composeTestRule.waitUntil(5_000) {
            composeTestRule.onAllNodesWithContentDescription("Profile picture")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("JD").assertDoesNotExist()
    }
}
