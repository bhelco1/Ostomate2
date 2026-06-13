package com.ostimate.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.unit.dp
import com.ostimate.app.data.db.SupplyTypeEntity
import com.ostimate.app.ui.theme.supplyColor
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReorderWarningsScreen(
    onBack: () -> Unit,
    viewModel: ManageSuppliesViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var editTarget by remember { mutableStateOf<SupplyTypeEntity?>(null) }

    editTarget?.let { supply ->
        EditThresholdDialog(
            supply = supply,
            onDismiss = { editTarget = null },
            onSave = { days ->
                viewModel.saveSupplyDetails(
                    supply.id,
                    supply.name,
                    supply.boxSize,
                    days,
                    supply.colorIndex,
                )
                editTarget = null
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reorder Warnings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets(0),
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = innerPadding.calculateTopPadding() + 8.dp,
                    bottom = innerPadding.calculateBottomPadding() + 16.dp,
                ),
        ) {
            item {
                Text(
                    "Warn when you haven't changed a supply in this many days.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
            items(uiState.supplies, key = { it.id }) { supply ->
                ReorderWarningRow(supply = supply, onEdit = { editTarget = supply })
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            }
        }
    }
}

@Composable
private fun ReorderWarningRow(
    supply: SupplyTypeEntity,
    onEdit: () -> Unit,
) {
    val accent = supplyColor(supply.kind, supply.colorIndex)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.layout.Box(
            modifier =
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(accent),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(supply.name, style = MaterialTheme.typography.titleMedium)
            Text(
                "Warn after ${supply.warnThresholdDays} days",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
        IconButton(onClick = onEdit) {
            Icon(
                Icons.Filled.Edit,
                contentDescription = "Edit ${supply.name} threshold",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            )
        }
    }
}

@Composable
private fun EditThresholdDialog(
    supply: SupplyTypeEntity,
    onDismiss: () -> Unit,
    onSave: (days: Int) -> Unit,
) {
    var input by rememberSaveable(supply.id) { mutableStateOf(supply.warnThresholdDays.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${supply.name} warning") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.filter { c -> c.isDigit() } },
                    label = { Text("Warn after (days)") },
                    keyboardOptions =
                        KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    singleLine = true,
                    suffix = { Text("days") },
                )
                Text(
                    "You'll see a warning on the home screen when this supply hasn't been changed in that many days.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val days = input.toIntOrNull() ?: return@TextButton
                    if (days < 1) return@TextButton
                    onSave(days)
                },
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
