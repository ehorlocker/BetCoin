package com.betcoin.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.betcoin.BuildConfig

/**
 * A consistent DEV MODE indicator shown on all screens when running the dev flavor.
 */
@Composable
fun DevModeIndicator(modifier: Modifier = Modifier) {
    if (BuildConfig.FLAVOR == "dev") {
        Text(
            text = "DEV MODE",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.error,
            modifier = modifier,
        )
    }
}
