@file:Suppress("DEPRECATION") // kotlinx-datetime 0.6.x monthNumber/dayOfMonth/dayOfWeek deprecation

package com.ostomate.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ostomate.app.resources.Res
import com.ostomate.app.resources.action_undo
import com.ostomate.app.resources.confirm_repeat_confirm
import com.ostomate.app.resources.confirm_repeat_dismiss
import com.ostomate.app.resources.confirm_repeat_message
import com.ostomate.app.resources.confirm_repeat_title
import com.ostomate.app.resources.home_no_supplies
import com.ostomate.app.resources.home_overflow_view_history
import com.ostomate.app.resources.home_snackbar_logged
import com.ostomate.app.resources.home_snackbar_logged_count
import com.ostomate.app.ui.components.SupplyCard
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Clock

private val MONTH_NAMES =
    arrayOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
    )

@Composable
fun HomeScreen(
    onNavigateToHistory: (supplyId: Long) -> Unit = {},
    viewModel: HomeViewModel = koinViewModel(),
    // Injectable so screenshot tests pin the header date; production reads the wall clock.
    today: LocalDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val loggedCountFmt = stringResource(Res.string.home_snackbar_logged_count)
    val loggedFmt = stringResource(Res.string.home_snackbar_logged)
    val undoLabel = stringResource(Res.string.action_undo)

    LaunchedEffect(uiState.pendingUndo) {
        val event = uiState.pendingUndo ?: return@LaunchedEffect
        val name = uiState.undoSupplyName
        val onHand = uiState.undoOnHand
        val message =
            if (onHand != null) {
                loggedCountFmt.replace("%1\$s", name).replace("%2\$d", onHand.toString())
            } else {
                loggedFmt.replace("%1\$s", name)
            }
        val result =
            snackbarHostState.showSnackbar(
                message = message,
                actionLabel = undoLabel,
                duration = SnackbarDuration.Short,
            )
        when (result) {
            SnackbarResult.ActionPerformed -> viewModel.undoLog()
            SnackbarResult.Dismissed -> viewModel.clearUndo()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0),
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = innerPadding.calculateTopPadding() + 16.dp,
                    bottom = innerPadding.calculateBottomPadding() + 16.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column {
                    Text("Ostomate", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        todayDateLabel(today),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (uiState.supplies.isEmpty()) {
                item {
                    Text(
                        stringResource(Res.string.home_no_supplies),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(uiState.supplies, key = { it.supply.id }) { row ->
                    SupplyCard(
                        name = row.supply.name,
                        kind = row.supply.kind,
                        onHand = row.supply.onHand,
                        daysRemaining = row.daysRemaining,
                        sampleCount = row.sampleCount,
                        warnThresholdDays = row.supply.warnThresholdDays,
                        onLogClick = { viewModel.logChange(row.supply) },
                        overflowMenuContent = { dismiss ->
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.home_overflow_view_history)) },
                                onClick = {
                                    dismiss()
                                    onNavigateToHistory(row.supply.id)
                                },
                            )
                        },
                    )
                }
            }
        }
    }

    uiState.pendingConfirmation?.let { confirmation ->
        val message =
            stringResource(Res.string.confirm_repeat_message)
                .replace("%1\$s", confirmation.supplyName)
                .replace("%2\$d", confirmation.minutesAgo.toString())
        AlertDialog(
            onDismissRequest = { viewModel.dismissPendingConfirmation() },
            title = { Text(stringResource(Res.string.confirm_repeat_title)) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmPendingLog() }) {
                    Text(stringResource(Res.string.confirm_repeat_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissPendingConfirmation() }) {
                    Text(stringResource(Res.string.confirm_repeat_dismiss))
                }
            },
        )
    }
}

private fun todayDateLabel(date: LocalDate): String {
    val dow =
        date.dayOfWeek.name
            .lowercase()
            .replaceFirstChar { it.uppercase() }
    val month = MONTH_NAMES[date.monthNumber - 1]
    return "$dow, $month ${date.dayOfMonth}"
}
