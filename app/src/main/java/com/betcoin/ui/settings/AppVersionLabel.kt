package com.betcoin.ui.settings

/**
 * Label for the Settings Version row.
 *
 * @param versionName the app version, normally `BuildConfig.VERSION_NAME`
 * @param flavor the build flavor, normally `BuildConfig.FLAVOR`
 * @return the version alone (`1.0`), or with the flavor appended on dev builds (`1.0 (dev)`)
 */
fun appVersionLabel(versionName: String, flavor: String): String =
    if (flavor == "dev") "$versionName ($flavor)" else versionName
