package com.ostimate.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ostimate.app.ui.theme.supplyColor
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageSuppliesScreen(
    onBack: () -> Unit,
    viewModel: ManageSuppliesViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var editCountInput by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(uiState.editCountTarget) {
        editCountInput = uiState.editCountTarget?.onHand?.toString() ?: ""
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
                    modifier = Modifier.testTag("editCountField"),
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
        topBar = {
            TopAppBar(
                title = { Text("Manage Supplies") },
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(uiState.supplies, key = { it.id }) { supply ->
                SupplyInventoryRow(
                    name = supply.name,
                    kind = supply.kind,
                    onHand = supply.onHand,
                    onEditClick = { viewModel.requestEditCount(supply) },
                )
            }
        }
    }
}

@Composable
private fun SupplyInventoryRow(
    name: String,
    kind: com.ostimate.app.domain.SupplyKind,
    onHand: Int,
    onEditClick: () -> Unit,
) {
    val accent = supplyColor(kind)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleMedium)
            Text(
                "$onHand on hand",
                style = MaterialTheme.typography.bodyMedium,
                color = accent,
            )
        }
        IconButton(
            onClick = onEditClick,
            modifier = Modifier.testTag("editSupplyButton"),
        ) {
            Icon(
                Icons.Filled.Edit,
                contentDescription = "Edit $name count",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
