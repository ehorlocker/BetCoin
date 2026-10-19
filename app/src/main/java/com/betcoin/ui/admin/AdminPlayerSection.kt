package com.betcoin.ui.admin

import androidx.compose.foundation.lazy.LazyListScope

/** Player Management section of the Admin Panel; filled in by BET-30. */
fun LazyListScope.adminPlayerSection() {
    item { AdminSectionHeader("Player Management") }
    item { AdminPlaceholderLine("No player actions yet") }
}
