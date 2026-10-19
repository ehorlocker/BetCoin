package com.betcoin.ui.settings

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Tests for [appVersionLabel]. */
class AppVersionLabelTest {

    @Test
    fun prod_showsVersionOnly() {
        assertThat(appVersionLabel("1.0", "prod")).isEqualTo("1.0")
    }

    @Test
    fun dev_appendsFlavor() {
        assertThat(appVersionLabel("1.0", "dev")).isEqualTo("1.0 (dev)")
    }
}
