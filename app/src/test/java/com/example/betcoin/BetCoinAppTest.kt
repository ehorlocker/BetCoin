package com.example.betcoin

import android.app.Application
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Tests for [BetCoinApp] class structure.
 *
 * Note: [dagger.hilt.android.HiltAndroidApp] has CLASS retention so it cannot be
 * verified via runtime reflection. Its presence is verified at compile time by the
 * Hilt annotation processor (KSP). We verify the class hierarchy instead.
 */
class BetCoinAppTest {

    @Test
    fun betCoinApp_extendsApplication() {
        assertThat(Application::class.java.isAssignableFrom(BetCoinApp::class.java)).isTrue()
    }

    @Test
    fun betCoinApp_isNotAbstract() {
        assertThat(
            java.lang.reflect.Modifier.isAbstract(BetCoinApp::class.java.modifiers)
        ).isFalse()
    }
}
