package com.ostimate.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ostimate.app.ui.components.SupplyCard
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun HomeScreen(
    onNavigateToHistory: (supplyId: Long) -> Unit = {},
    viewModel: HomeViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var editCountInput by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(uiState.editCountTarget) {
        editCountInput = uiState.editCountTarget?.onHand?.toString() ?: ""
    }

    LaunchedEffect(uiState.pendingUndo) {
        val event = uiState.pendingUndo ?: return@LaunchedEffect
        val name = uiState.undoSupplyName
        val result =
            snackbarHostState.showSnackbar(
                message = "$name logged",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short,
            )
        when (result) {
            SnackbarResult.ActionPerformed -> viewModel.undoLog()
            SnackbarResult.Dismissed -> viewModel.clearUndo()
        }
    }

    val target = uiState.editCountTarget
    if (target != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissEditCount,
            title = { Text("Edit ${target.name} count") },
            text = {
                OutlinedTextField(
                    value = editCountInput,
                    onValueChange = { editCountInput = it.filter { c -> c.isDigit() } },
                    label = { Text("On hand") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val count = editCountInput.toIntOrNull() ?: return@TextButton
                        viewModel.setOnHand(target.id, count)
                        viewModel.dismissEditCount()
                    },
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissEditCount) { Text("Cancel") }
            },
        )
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
                Text("Ostimate", style = MaterialTheme.typography.headlineMedium)
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
                                text = { Text("Edit count") },
                                onClick = {
                                    dismiss()
                                    viewModel.requestEditCount(row.supply)
                                },
                            )
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
