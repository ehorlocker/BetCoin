package com.betcoin.ui.components

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Tests for [devBuildBannerLabel], which decides whether [AppRootLayout] draws the dev banner.
 */
class DevBuildBannerLabelTest {

    @Test
    fun devFlavor_returnsLabelWithDatabaseName() {
        assertThat(devBuildBannerLabel("dev", "betcoin_dev.db")).isEqualTo("DEV BUILD · betcoin_dev.db")
    }

    @Test
    fun prodFlavor_returnsNull() {
        assertThat(devBuildBannerLabel("prod", "betcoin.db")).isNull()
    }

    @Test
    fun unknownFlavor_returnsNull() {
        assertThat(devBuildBannerLabel("staging", "betcoin.db")).isNull()
    }
}
