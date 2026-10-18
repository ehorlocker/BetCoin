package com.betcoin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.betcoin.BuildConfig

/** Test tag on the dev build banner, used by UI tests to find it. */
const val DEV_BUILD_BANNER_TAG = "dev_build_banner"

/**
 * Returns the dev build banner text for [flavor], or `null` when no banner should be drawn
 * (every flavor other than `dev`).
 *
 * @param flavor the build flavor, normally [BuildConfig.FLAVOR]
 * @param dbName the database file name, normally [BuildConfig.DB_NAME]
 */
fun devBuildBannerLabel(flavor: String, dbName: String): String? =
    if (flavor == "dev") "DEV BUILD · $dbName" else null

/**
 * Root layout that wraps the whole navigation graph (see spec §14 Dev Build Indicator).
 *
 * On the dev flavor it draws [DevBuildBanner] directly under the status bar and pushes [content]
 * below it. The status bar inset is consumed there, so screens applying
 * `WindowInsets.safeDrawing` don't add it a second time. On every other flavor it draws nothing
 * and reserves no space, calling [content] directly.
 *
 * @param bannerLabel the banner text, or `null` for no banner
 */
@Composable
fun AppRootLayout(
    bannerLabel: String? = devBuildBannerLabel(BuildConfig.FLAVOR, BuildConfig.DB_NAME),
    content: @Composable () -> Unit,
) {
    if (bannerLabel == null) {
        content()
        return
    }
    Column(modifier = Modifier.fillMaxSize()) {
        DevBuildBanner(label = bannerLabel)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .consumeWindowInsets(WindowInsets.statusBars),
        ) {
            content()
        }
    }
}

/**
 * Full-width, non-clickable strip marking a dev build. Its background also fills the status bar
 * area above it, so screenshots and the recent-apps view show it's a dev build.
 */
@Composable
fun DevBuildBanner(label: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer)
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(24.dp)
            .testTag(DEV_BUILD_BANNER_TAG),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}
