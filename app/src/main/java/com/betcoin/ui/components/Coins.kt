package com.betcoin.ui.components

import java.text.NumberFormat

/** Formats a coin [amount] with locale grouping separators; negative amounts keep their minus sign. */
internal fun formatCoins(amount: Long): String = NumberFormat.getIntegerInstance().format(amount)
