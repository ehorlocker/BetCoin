package com.betcoin.navigation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Tests for navigation route constants.
 */
class RoutesTest {

    @Test
    fun onboardingRoute_isCorrectValue() {
        assertThat(Routes.ONBOARDING).isEqualTo("onboarding")
    }

    @Test
    fun homeRoute_isCorrectValue() {
        assertThat(Routes.HOME).isEqualTo("home")
    }
}
