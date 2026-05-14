package com.betcoin.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

/**
 * Compose UI tests for BetCoinTheme.
 *
 * Verifies that the theme composable correctly applies
 * colors, typography, and shapes to MaterialTheme.
 */
class ThemeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun betCoinTheme_appliesDarkColorScheme() {
        var primary: Color? = null
        var secondary: Color? = null
        var tertiary: Color? = null
        var background: Color? = null
        var surface: Color? = null

        composeTestRule.setContent {
            BetCoinTheme {
                primary = MaterialTheme.colorScheme.primary
                secondary = MaterialTheme.colorScheme.secondary
                tertiary = MaterialTheme.colorScheme.tertiary
                background = MaterialTheme.colorScheme.background
                surface = MaterialTheme.colorScheme.surface
            }
        }

        assertThat(primary).isEqualTo(BetCoinPurple)
        assertThat(secondary).isEqualTo(BetCoinCyan)
        assertThat(tertiary).isEqualTo(BetCoinMagenta)
        assertThat(background).isEqualTo(BetCoinBackground)
        assertThat(surface).isEqualTo(BetCoinSurface)
    }

    @Test
    fun betCoinTheme_appliesTypography() {
        var displayLarge: TextStyle? = null
        var headlineLarge: TextStyle? = null
        var bodyLarge: TextStyle? = null

        composeTestRule.setContent {
            BetCoinTheme {
                displayLarge = MaterialTheme.typography.displayLarge
                headlineLarge = MaterialTheme.typography.headlineLarge
                bodyLarge = MaterialTheme.typography.bodyLarge
            }
        }

        assertThat(displayLarge?.fontSize).isEqualTo(40.sp)
        assertThat(headlineLarge?.fontSize).isEqualTo(32.sp)
        assertThat(bodyLarge?.fontSize).isEqualTo(18.sp)
    }

    @Test
    fun betCoinTheme_appliesShapes() {
        var small: androidx.compose.ui.graphics.Shape? = null
        var large: androidx.compose.ui.graphics.Shape? = null

        composeTestRule.setContent {
            BetCoinTheme {
                small = MaterialTheme.shapes.small
                large = MaterialTheme.shapes.large
            }
        }

        assertThat(small).isEqualTo(BetCoinShapes.small)
        assertThat(large).isEqualTo(BetCoinShapes.large)
    }
}
