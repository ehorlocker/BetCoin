package com.betcoin.ui.admin

import androidx.compose.foundation.lazy.LazyListScope

/** Bet Management section of the Admin Panel; filled in by BET-31. */
fun LazyListScope.adminBetSection() {
    item { AdminSectionHeader("Bet Management") }
    item { AdminPlaceholderLine("No bet actions yet") }
}
