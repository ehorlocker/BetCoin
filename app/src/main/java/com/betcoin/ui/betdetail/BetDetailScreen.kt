package com.betcoin.ui.betdetail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.betcoin.data.model.BetStatus
import com.betcoin.ui.components.ConsentSequence
import com.betcoin.ui.components.ConsentStep
import java.text.DateFormat
import java.util.Date

/** Test tags for the Bet Detail action buttons and messages. */
const val BET_DETAIL_RESOLVE_TAG = "bet_detail_resolve"
const val BET_DETAIL_CANCEL_TAG = "bet_detail_cancel"
const val BET_DETAIL_REOPEN_TAG = "bet_detail_reopen"
const val BET_DETAIL_REOPEN_REASON_TAG = "bet_detail_reopen_reason"
const val BET_DETAIL_ACTION_ERROR_TAG = "bet_detail_action_error"

/**
 * Bet Detail screen — the read-only breakdown of one bet (§4.2): header, outcomes,
 * participants with their results, pot summary, and resolution or cancellation info.
 *
 * Resolve, Cancel and Reopen are driven by [BetDetailViewModel.actionState]: the outcome picker
 * and the PIN consent sequence are shown from here because the PIN verifiers call the ViewModel.
 *
 * @param viewModel the [BetDetailViewModel] that observes the bet
 * @param onNavigateBack callback invoked when the user wants to go back
 */
@Composable
fun BetDetailScreen(
    viewModel: BetDetailViewModel,
    onNavigateBack: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val actionState by viewModel.actionState.collectAsStateWithLifecycle()
    BetDetailContent(
        uiState = uiState,
        actionState = actionState,
        onNavigateBack = onNavigateBack,
        onResolve = viewModel::onResolveRequested,
        onCancel = viewModel::onCancelRequested,
        onReopen = viewModel::onReopenRequested,
        onOutcomePicked = viewModel::onOutcomePicked,
        onOutcomePickerDismissed = viewModel::onOutcomePickerDismissed,
    )

    val consent = actionState.consent
    val detail = (uiState as? BetDetailUiState.Success)?.detail
    // Not while busy: the ViewModel doesn't guard onConsentAborted then.
    if (consent != null && detail != null && !actionState.isBusy) {
        val steps = detail.participants.mapNotNull { row ->
            row.userId?.let { userId ->
                ConsentStep(label = row.name, verifier = { pin -> viewModel.verifyParticipantPin(userId, pin) })
            }
        }
        if (steps.isNotEmpty()) {
            ConsentSequence(
                steps = steps,
                onAllConfirmed = viewModel::onAllPinsConfirmed,
                onAbort = viewModel::onConsentAborted,
                title = consent.title,
            )
        }
    }
}

/**
 * Stateless Bet Detail content for [uiState].
 *
 * @param uiState the state to render
 * @param actionState the picker, busy and error state of the bet actions
 * @param onNavigateBack callback invoked when the user wants to go back
 * @param onResolve callback invoked when Resolve is tapped
 * @param onCancel callback invoked when Cancel Bet is tapped
 * @param onReopen callback invoked when Reopen Bet is tapped
 * @param onOutcomePicked callback invoked with the chosen winning outcome's id
 * @param onOutcomePickerDismissed callback invoked when the outcome picker is dismissed
 */
@Composable
fun BetDetailContent(
    uiState: BetDetailUiState,
    actionState: BetActionState = BetActionState(),
    onNavigateBack: () -> Unit = {},
    onResolve: () -> Unit = {},
    onCancel: () -> Unit = {},
    onReopen: () -> Unit = {},
    onOutcomePicked: (Long) -> Unit = {},
    onOutcomePickerDismissed: () -> Unit = {},
) {
    BackHandler { onNavigateBack() }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                    )
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Bet Detail",
                    style = MaterialTheme.typography.headlineMedium,
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
                .padding(padding),
        ) {
            when (uiState) {
                BetDetailUiState.Loading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                )
                BetDetailUiState.NotFound -> CenteredMessage("This bet no longer exists.")
                BetDetailUiState.Error -> CenteredMessage("Couldn't load this bet.")
                is BetDetailUiState.Success -> {
                    BetDetailBody(uiState.detail, actionState, onResolve, onCancel, onReopen)
                    if (actionState.pickingOutcome) {
                        OutcomePickerDialog(uiState.detail.outcomes, onOutcomePicked, onOutcomePickerDismissed)
                    }
                }
            }
        }
    }
}

@Composable
private fun CenteredMessage(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BetDetailBody(
    detail: BetDetail,
    actionState: BetActionState,
    onResolve: () -> Unit,
    onCancel: () -> Unit,
    onReopen: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Header(detail) }

        item { SectionTitle("Outcomes") }
        items(detail.outcomes, key = { "outcome_${it.id}" }) { outcome ->
            OutcomeItem(outcome)
        }

        item { SectionTitle("Participants") }
        items(detail.participants, key = { "participant_${it.id}" }) { participant ->
            ParticipantItem(participant)
        }

        item { PotSummary(detail) }

        when (detail.status) {
            BetStatus.RESOLVED -> item { ResolutionInfo(detail) }
            BetStatus.CANCELLED -> item { InfoCard(listOf("Cancelled — all wagers refunded")) }
            BetStatus.ACTIVE, BetStatus.PENDING_CONFIRMATION -> Unit
        }

        item(key = "actions") { Actions(detail, actionState, onResolve, onCancel, onReopen) }
    }
}

@Composable
private fun Actions(
    detail: BetDetail,
    actionState: BetActionState,
    onResolve: () -> Unit,
    onCancel: () -> Unit,
    onReopen: () -> Unit,
) {
    val idle = !actionState.isBusy
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (detail.status) {
            BetStatus.ACTIVE -> {
                Button(
                    onClick = onResolve,
                    enabled = idle,
                    modifier = Modifier.fillMaxWidth().testTag(BET_DETAIL_RESOLVE_TAG),
                ) {
                    Text("Resolve")
                }
                OutlinedButton(
                    onClick = onCancel,
                    enabled = idle,
                    modifier = Modifier.fillMaxWidth().testTag(BET_DETAIL_CANCEL_TAG),
                ) {
                    Text("Cancel Bet")
                }
            }
            BetStatus.RESOLVED, BetStatus.CANCELLED -> {
                Button(
                    onClick = onReopen,
                    enabled = detail.reopenBlockedReason == null && idle,
                    modifier = Modifier.fillMaxWidth().testTag(BET_DETAIL_REOPEN_TAG),
                ) {
                    Text("Reopen Bet")
                }
                detail.reopenBlockedReason?.let { reason ->
                    Text(
                        text = reason,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag(BET_DETAIL_REOPEN_REASON_TAG),
                    )
                }
            }
            BetStatus.PENDING_CONFIRMATION -> Unit
        }
        actionState.errorMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag(BET_DETAIL_ACTION_ERROR_TAG),
            )
        }
    }
}

@Composable
private fun OutcomePickerDialog(
    outcomes: List<OutcomeRow>,
    onPicked: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pick the winning outcome") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                outcomes.forEach { outcome ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selectedId == outcome.id,
                                onClick = { selectedId = outcome.id },
                                role = Role.RadioButton,
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selectedId == outcome.id, onClick = null)
                        Text(
                            text = outcome.text,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { selectedId?.let(onPicked) }, enabled = selectedId != null) {
                Text("Continue")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Back")
            }
        },
    )
}

@Composable
private fun Header(detail: BetDetail) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = detail.prompt,
            style = MaterialTheme.typography.headlineSmall,
        )
        StatusBadge(detail.status)
        Text(
            text = "Created ${formatDate(detail.createdAt)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatusBadge(status: BetStatus) {
    val color = when (status) {
        BetStatus.ACTIVE -> MaterialTheme.colorScheme.tertiary
        BetStatus.RESOLVED -> MaterialTheme.colorScheme.primary
        BetStatus.CANCELLED -> MaterialTheme.colorScheme.onSurfaceVariant
        BetStatus.PENDING_CONFIRMATION -> MaterialTheme.colorScheme.secondary
    }
    val label = when (status) {
        BetStatus.PENDING_CONFIRMATION -> "PENDING"
        else -> status.name
    }
    Surface(
        shape = RoundedCornerShape(9999.dp),
        color = Color.Transparent,
        modifier = Modifier
            .testTag("status_badge")
            .border(1.dp, color, RoundedCornerShape(9999.dp)),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = color,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun OutcomeItem(outcome: OutcomeRow) {
    val highlight = MaterialTheme.colorScheme.primary
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (outcome.isWinner) {
                    Modifier
                        .testTag("winning_outcome")
                        .border(2.dp, highlight, CardDefaults.shape)
                } else {
                    Modifier
                },
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = outcome.text,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (outcome.isWinner) FontWeight.Bold else null,
                color = if (outcome.isWinner) highlight else Color.Unspecified,
                modifier = Modifier.weight(1f),
            )
            if (outcome.isWinner) {
                Text(
                    text = "Winner",
                    style = MaterialTheme.typography.labelLarge,
                    color = highlight,
                )
            }
        }
    }
}

@Composable
private fun ParticipantItem(participant: ParticipantRow) {
    Card(modifier = Modifier.fillMaxWidth().testTag("participant_${participant.id}")) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (participant.isDeletedUser) "${participant.name} (deleted)" else participant.name,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Picked ${participant.pick}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Wager ${participant.wager}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                ResultLabel(participant.result)
                participant.net?.let { net ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = formatNet(net),
                        style = MaterialTheme.typography.titleMedium,
                        color = netColor(net),
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultLabel(result: ParticipantResult) {
    val color = when (result) {
        ParticipantResult.WIN -> MaterialTheme.colorScheme.primary
        ParticipantResult.LOSS -> MaterialTheme.colorScheme.error
        ParticipantResult.PENDING,
        ParticipantResult.PUSH,
        ParticipantResult.REFUNDED,
        -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text = result.name,
        style = MaterialTheme.typography.labelLarge,
        color = color,
    )
}

@Composable
private fun netColor(net: Long): Color = when {
    net > 0 -> MaterialTheme.colorScheme.primary
    net < 0 -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun PotSummary(detail: BetDetail) {
    val count = detail.participants.size
    InfoCard(
        listOf(
            "Pot: ${detail.potSize} BetCoin",
            if (count == 1) "1 participant" else "$count participants",
        ),
    )
}

@Composable
private fun ResolutionInfo(detail: BetDetail) {
    InfoCard(
        listOfNotNull(
            detail.winningOutcome?.let { "Winning outcome: $it" },
            detail.resolvedAt?.let { "Resolved ${formatDate(it)}" },
            "No winners — all wagers refunded".takeIf { detail.isPush },
            "Unmatched wagers refunded".takeIf { detail.isCapped },
        ),
    )
}

@Composable
private fun InfoCard(lines: List<String>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            lines.forEach { line ->
                Text(text = line, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

private fun formatDate(epochMillis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(epochMillis))
