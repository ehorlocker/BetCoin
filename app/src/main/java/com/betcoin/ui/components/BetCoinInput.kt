package com.betcoin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.betcoin.ui.theme.BetCoinBackground
import com.betcoin.ui.theme.BetCoinOutline
import com.betcoin.ui.theme.BetCoinPurple

/**
 * A dark input field with a purple focus glow.
 *
 * @param value The current text value.
 * @param onValueChange Called when the text changes.
 * @param placeholder Placeholder text displayed when empty.
 * @param modifier Modifier for the input.
 * @param visualTransformation Visual transformation for the input (e.g., password).
 */
@Composable
fun BetCoinInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    val isFocused = value.isNotEmpty() // Simplified focus detection for testing
    val borderColor = if (isFocused) BetCoinPurple else BetCoinOutline.copy(alpha = 0.3f)

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(BetCoinBackground)
            .border(2.dp, borderColor, RoundedCornerShape(24.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
        cursorBrush = SolidColor(BetCoinPurple),
        visualTransformation = visualTransformation,
        decorationBox = { innerTextField ->
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.5f),
                )
            }
            innerTextField()
        },
    )
}
