package com.betcoin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.betcoin.ui.theme.BetCoinCyan
import com.betcoin.ui.theme.BetCoinMagenta
import com.betcoin.ui.theme.BetCoinPurple

private val avatarBackgroundColors = listOf(
    BetCoinPurple,
    BetCoinCyan,
    BetCoinMagenta,
    Color(0xFF6B7280), // Slate
    Color(0xFF4F46E5), // Indigo
)

/**
 * Returns a deterministic background color for the given initials.
 * The same initials will always produce the same color.
 */
internal fun avatarColorFor(initials: String): Color {
    val index = initials.sumOf { it.code } % avatarBackgroundColors.size
    return avatarBackgroundColors[index]
}

/**
 * A circular avatar displaying user initials.
 *
 * Background color is selected deterministically based on the initials
 * so the same user always gets the same color.
 *
 * @param initials The initials to display (e.g., "JD").
 * @param modifier Modifier for the avatar.
 * @param size The size of the avatar in dp.
 */
@Composable
fun BetCoinAvatar(
    initials: String,
    modifier: Modifier = Modifier,
    size: Int = 48,
) {
    val backgroundColor = avatarColorFor(initials)

    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(backgroundColor)
            .border(2.dp, BetCoinPurple, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
        )
    }
}


