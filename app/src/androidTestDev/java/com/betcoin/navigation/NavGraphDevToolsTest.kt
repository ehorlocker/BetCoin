package com.betcoin.navigation

import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Test
import org.junit.runner.RunWith

/** Dev flavor only (spec §4.1, §14): Settings → Dev Tools and back. */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class NavGraphDevToolsTest : NavGraphTestBase() {

    @Test
    fun settings_devTools_andBackEachStep() {
        seedAdminPin()
        launchApp()
        awaitHome()

        compose.onNodeWithContentDescription("Settings").performClick()
        waitForText("Theme")
        clickText("Dev Tools")
        waitForText("Dev Tools")
        assertTextGone("Theme")

        pressBack()
        waitForText("Theme")
        pressBack()
        awaitHome()
    }
}
