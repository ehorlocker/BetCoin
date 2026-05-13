package com.betcoin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.betcoin.ui.theme.BetCoinPurple

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
    val backgroundColor = if (selected) BetCoinPurple else Color(0xFF2A2A2A)
    val borderColor = if (selected) BetCoinPurple else Color(0xFF4C4354)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(9999.dp))
            .background(backgroundColor)
            .border(1.dp, borderColor, RoundedCornerShape(9999.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
        )
    }
}
