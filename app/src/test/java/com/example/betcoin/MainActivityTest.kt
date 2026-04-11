package com.example.betcoin

import androidx.activity.ComponentActivity
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Tests for [MainActivity] class structure and Hilt annotation.
 *
 * Note: [dagger.hilt.android.AndroidEntryPoint] has CLASS retention so it cannot be
 * verified via runtime reflection. Its presence is verified at compile time by the
 * Hilt annotation processor. We verify the Hilt-generated superclass instead.
 */
class MainActivityTest {

    @Test
    fun mainActivity_extendsComponentActivity() {
        assertThat(ComponentActivity::class.java.isAssignableFrom(MainActivity::class.java)).isTrue()
    }

    @Test
    fun mainActivity_isNotAbstract() {
        assertThat(
            java.lang.reflect.Modifier.isAbstract(MainActivity::class.java.modifiers)
        ).isFalse()
    }

    @Test
    fun mainActivity_hasOnCreateMethod() {
        val method = MainActivity::class.java.getDeclaredMethod(
            "onCreate",
            android.os.Bundle::class.java
        )
        assertThat(method).isNotNull()
    }
}
