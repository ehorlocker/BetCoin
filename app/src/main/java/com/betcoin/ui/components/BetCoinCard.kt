package com.betcoin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.betcoin.ui.theme.BetCoinSurface

/**
 * A betting card with 24dp rounded corners and a press-in animation.
 *
 * @param onClick Optional click handler. If provided, the card will be clickable.
 * @param modifier Modifier for the card.
 * @param content The content of the card.
 */
@Composable
fun BetCoinCard(
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale = if (isPressed) 0.98f else 1f

    val cardModifier = modifier
        .clip(RoundedCornerShape(24.dp))
        .background(BetCoinSurface)
        .scale(scale)
        .then(
            if (onClick != null) {
                Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = androidx.compose.material3.ripple(),
                    role = Role.Button,
                    onClick = onClick,
                )
            } else {
                Modifier
            }
        )
        .padding(16.dp)

    Box(modifier = cardModifier) {
        content()
    }
}
