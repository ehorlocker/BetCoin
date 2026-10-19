package com.betcoin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import com.betcoin.ui.theme.BetCoinSecondary
import com.betcoin.ui.theme.BetCoinTertiary
import com.betcoin.ui.theme.BetCoinPrimary
import com.betcoin.ui.theme.BetCoinAvatarSlate
import com.betcoin.ui.theme.BetCoinAvatarIndigo

private val avatarBackgroundColors = listOf(
    BetCoinPrimary,
    BetCoinSecondary,
    BetCoinTertiary,
    BetCoinAvatarSlate,
    BetCoinAvatarIndigo,
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
 * @param imageModel Optional opaque image model (e.g. from `AvatarStore.load`). The picture is
 *   shown while it loads successfully; the initials are shown when null, while loading, and
 *   when the image can't be loaded.
 */
@Composable
fun BetCoinAvatar(
    initials: String,
    modifier: Modifier = Modifier,
    size: Int = 48,
    imageModel: Any? = null,
) {
    val backgroundColor = avatarColorFor(initials)

    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(backgroundColor)
            .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        val initialsText: @Composable () -> Unit = {
            Text(
                text = initials.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
            )
        }
        if (imageModel == null) {
            initialsText()
        } else {
            SubcomposeAsyncImage(
                model = imageModel,
                contentDescription = "Profile picture",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = { initialsText() },
                error = { initialsText() },
                success = { SubcomposeAsyncImageContent() },
            )
        }
    }
}


