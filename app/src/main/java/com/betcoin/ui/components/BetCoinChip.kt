package com.betcoin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.betcoin.ui.theme.BetCoinOutlineVariant
import com.betcoin.ui.theme.BetCoinPrimary
import com.betcoin.ui.theme.BetCoinSurfaceHigh

/**
 * A selectable chip for odds or categories.
 *
 * Unselected: dark gray background with white text.
 * Selected: electric purple background with white text.
 *
 * @param label The text label for the chip.
 * @param selected Whether the chip is selected.
 * @param onClick Called when the chip is clicked.
 * @param modifier Modifier for the chip.
 */
@Composable
fun BetCoinChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backgroundColor = if (selected) BetCoinPrimary else BetCoinSurfaceHigh
    val borderColor = if (selected) BetCoinPrimary else BetCoinOutlineVariant

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(9999.dp))
            .background(backgroundColor)
            .border(1.dp, borderColor, RoundedCornerShape(9999.dp))
            .selectable(selected = selected, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
        )
    }
}
