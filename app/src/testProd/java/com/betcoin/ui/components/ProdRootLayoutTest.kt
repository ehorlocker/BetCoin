package com.betcoin.ui.components

import com.betcoin.BuildConfig
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Checks that the prod flavor's root layout draws nothing: [AppRootLayout] defaults to this
 * label and, when it's `null`, calls its content directly with no banner and no reserved space.
 */
class ProdRootLayoutTest {

    @Test
    fun prodBuild_rootLayoutHasNoBanner() {
        assertThat(BuildConfig.FLAVOR).isEqualTo("prod")
        assertThat(devBuildBannerLabel(BuildConfig.FLAVOR, BuildConfig.DB_NAME)).isNull()
    }
}
