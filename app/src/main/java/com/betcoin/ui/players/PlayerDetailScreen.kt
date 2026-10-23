package com.betcoin.ui.players

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.betcoin.ui.components.BetCoinAvatar
import com.betcoin.ui.components.BetCoinButton
import com.betcoin.ui.components.BetCoinCard
import com.betcoin.ui.components.ButtonVariant
import com.betcoin.ui.components.PinEntryDialog
import com.betcoin.ui.components.formatCoins
import com.betcoin.ui.players.PlayerDetailViewModel.PlayerStats
import com.betcoin.ui.players.PlayerDetailViewModel.UiState

/**
 * Player Detail screen (§4.2): every stat for one player, and a Bailout button gated by that
 * player's PIN, shown only while they're eligible (§5.4).
 *
 * @param viewModel the [PlayerDetailViewModel] that manages state
 * @param onNavigateBack called when the user wants to go back
 */
@Composable
fun PlayerDetailScreen(
    viewModel: PlayerDetailViewModel,
    onNavigateBack: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var isNavigatingBack by remember { mutableStateOf(false) }

    BackHandler(enabled = !isNavigatingBack) {
        isNavigatingBack = true
        onNavigateBack()
    }

    val context = LocalContext.current
    val canTakePhoto = remember {
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).resolveActivity(context.packageManager) != null
    }
    val pickImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
        viewModel::onImagePicked,
    )
    val takePictureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
        viewModel::onPhotoCaptured,
    )

    val launchCamera = (uiState as? UiState.Loaded)?.launchCamera
    LaunchedEffect(launchCamera) {
        launchCamera?.let {
            val launched = try {
                takePictureLauncher.launch(it)
                true
            } catch (e: ActivityNotFoundException) {
                false
            }
            viewModel.onCameraLaunched()
            if (!launched) viewModel.onCameraUnavailable()
        }
    }

    val message = (uiState as? UiState.Loaded)?.message
    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onMessageShown()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                    )
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Player Detail",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onNavigateBack) {
                    Text("Back")
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            when (val state = uiState) {
                UiState.Loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                UiState.NotFound -> Text(
                    text = "This player no longer exists.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center),
                )
                is UiState.Loaded -> PlayerDetailContent(
                    state = state,
                    onBailout = viewModel::onBailoutClicked,
                    onChangePicture = viewModel::onChangePictureClicked,
                )
            }
        }
    }

    val loaded = uiState as? UiState.Loaded
    if (loaded != null && loaded.showBailoutPin) {
        PinEntryDialog(
            label = loaded.stats.username,
            title = "Bailout: +1,000 BetCoin",
            verifier = viewModel::verifyPin,
            onVerified = viewModel::onBailoutPinVerified,
            onDismiss = viewModel::onBailoutPinDismissed,
        )
    }
    if (loaded != null && loaded.showPicturePin) {
        PinEntryDialog(
            label = loaded.stats.username,
            title = "Change Profile Picture",
            verifier = viewModel::verifyPin,
            onVerified = viewModel::onPicturePinVerified,
            onDismiss = viewModel::onPicturePinDismissed,
        )
    }
    if (loaded != null && loaded.showPictureOptions) {
        ProfilePictureOptionsSheet(
            hasProfilePicture = loaded.hasProfilePicture,
            canTakePhoto = canTakePhoto,
            onGallery = {
                viewModel.onGallerySelected()
                pickImageLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
            onTakePhoto = viewModel::onTakePhotoSelected,
            onRemove = viewModel::onRemovePhotoSelected,
            onDismiss = viewModel::onPictureOptionsDismissed,
        )
    }
}

@Composable
internal fun PlayerDetailContent(
    state: UiState.Loaded,
    onBailout: () -> Unit,
    onChangePicture: () -> Unit,
) {
    val stats = state.stats
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BetCoinAvatar(
            initials = initialsOf(stats.username),
            size = 96,
            imageModel = state.avatarModel,
        )
        Spacer(modifier = Modifier.height(8.dp))
        BetCoinButton(
            text = "Change Profile Picture",
            onClick = onChangePicture,
            variant = ButtonVariant.Secondary,
            enabled = !state.isUpdatingPicture,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = stats.username, style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(24.dp))

        BetCoinCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                statRows(stats).forEach { (label, value) -> StatRow(label = label, value = value) }
            }
        }

        if (stats.isEligibleForBailout) {
            Spacer(modifier = Modifier.height(24.dp))
            BetCoinButton(
                text = "Bailout",
                onClick = onBailout,
                enabled = !state.isBailingOut,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

/** Label and formatted value for each stat, in display order (§4.2 Player Detail). */
internal fun statRows(stats: PlayerStats): List<Pair<String, String>> = listOf(
    "Balance" to formatCoins(stats.balance),
    "Net worth" to formatCoins(stats.netWorth),
    "W-L" to "${stats.totalWins}-${stats.totalLosses}",
    "Total earnings" to formatCoins(stats.totalEarnings),
    "Total lost" to formatCoins(stats.totalLost),
    "Bailouts" to stats.bailoutCount.toString(),
    "Total debt" to formatCoins(stats.totalDebt),
    "Locked in active bets" to formatCoins(stats.lockedBalance),
)

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.titleMedium)
    }
}
