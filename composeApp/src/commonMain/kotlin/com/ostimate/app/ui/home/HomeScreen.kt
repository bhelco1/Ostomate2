@file:Suppress("DEPRECATION") // kotlinx-datetime 0.6.x monthNumber/dayOfMonth/dayOfWeek deprecation

package com.ostimate.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ostimate.app.ui.components.SupplyCard
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.viewmodel.koinViewModel

private val MONTH_NAMES =
    arrayOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
    )

@Composable
fun HomeScreen(
    onNavigateToHistory: (supplyId: Long) -> Unit = {},
    viewModel: HomeViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.pendingUndo) {
        val event = uiState.pendingUndo ?: return@LaunchedEffect
        val name = uiState.undoSupplyName
        val onHand = uiState.undoOnHand
        val message = if (onHand != null) "$name logged · $onHand left" else "$name logged"
        val result =
            snackbarHostState.showSnackbar(
                message = message,
                actionLabel = "Undo",
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
                    Text("Ostimate", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        todayDateLabel(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    )
                }
            }
            if (uiState.supplies.isEmpty()) {
                item {
                    Text(
                        "No supplies yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
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
                                text = { Text("View history") },
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
}

private fun todayDateLabel(): String {
    val tz = TimeZone.currentSystemDefault()
    val now = Clock.System.now().toLocalDateTime(tz)
    val dow =
        now.dayOfWeek.name
            .lowercase()
            .replaceFirstChar { it.uppercase() }
    val month = MONTH_NAMES[now.monthNumber - 1]
    return "$dow, $month ${now.dayOfMonth}"
}
