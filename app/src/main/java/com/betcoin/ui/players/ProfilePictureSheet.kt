package com.betcoin.ui.players

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * Bottom sheet offering the ways to change a player's profile picture. Stateless: "Take Photo" is
 * hidden when no camera app is available and "Remove Photo" when no picture is set.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilePictureOptionsSheet(
    hasProfilePicture: Boolean,
    canTakePhoto: Boolean,
    onGallery: () -> Unit,
    onTakePhoto: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            SheetOption(text = "Select from Gallery", onClick = onGallery)
            if (canTakePhoto) SheetOption(text = "Take Photo", onClick = onTakePhoto)
            if (hasProfilePicture) SheetOption(text = "Remove Photo", onClick = onRemove)
        }
    }
}

@Composable
private fun SheetOption(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
    )
}
