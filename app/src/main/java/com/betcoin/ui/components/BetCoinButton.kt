package com.betcoin.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.betcoin.ui.theme.BetCoinOnPrimary
import com.betcoin.ui.theme.BetCoinPrimary
import com.betcoin.ui.theme.BetCoinSuccess

enum class ButtonVariant {
    Primary,
    Secondary,
    BettingAction,
}

@Composable
fun BetCoinButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Primary,
    enabled: Boolean = true,
) {
    val (containerColor, contentColor, borderColor) = when (variant) {
        ButtonVariant.Primary -> Triple(
            BetCoinPrimary,
            BetCoinOnPrimary,
            Color.Transparent,
        )
        ButtonVariant.Secondary -> Triple(
            Color.Transparent,
            BetCoinPrimary,
            BetCoinPrimary,
        )
        ButtonVariant.BettingAction -> Triple(
            BetCoinSuccess,
            BetCoinOnPrimary,
            Color.Transparent,
        )
    }

    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled,
        shape = RoundedCornerShape(9999.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor.copy(alpha = 0.38f),
            disabledContentColor = contentColor.copy(alpha = 0.38f),
        ),
        border = if (variant == ButtonVariant.Secondary) {
            BorderStroke(2.dp, borderColor)
        } else {
            null
        },
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
