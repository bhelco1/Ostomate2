package com.ostimate.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ostimate.app.data.db.SupplyTypeEntity
import com.ostimate.app.domain.SupplyKind
import com.ostimate.app.resources.Res
import com.ostimate.app.resources.action_cancel
import com.ostimate.app.resources.action_save
import com.ostimate.app.resources.cd_add_supply
import com.ostimate.app.resources.cd_back
import com.ostimate.app.resources.cd_edit_supply
import com.ostimate.app.resources.manage_supplies_add_button
import com.ostimate.app.resources.manage_supplies_add_title
import com.ostimate.app.resources.manage_supplies_archive
import com.ostimate.app.resources.manage_supplies_box_size_label
import com.ostimate.app.resources.manage_supplies_color_label
import com.ostimate.app.resources.manage_supplies_edit_title
import com.ostimate.app.resources.manage_supplies_name_label
import com.ostimate.app.resources.manage_supplies_on_hand_box
import com.ostimate.app.resources.manage_supplies_on_hand_label
import com.ostimate.app.resources.manage_supplies_set_count_title
import com.ostimate.app.resources.manage_supplies_title
import com.ostimate.app.resources.manage_supplies_warn_days_label
import com.ostimate.app.ui.theme.OstimateColors
import com.ostimate.app.ui.theme.supplyColor
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

private val CUSTOM_COLORS =
    listOf(
        OstimateColors.Custom0,
        OstimateColors.Custom1,
        OstimateColors.Custom2,
        OstimateColors.Custom3,
        OstimateColors.Custom4,
        OstimateColors.Custom5,
        OstimateColors.Custom6,
        OstimateColors.Custom7,
    )

private val CUSTOM_COLORS_DARK =
    listOf(
        OstimateColors.Custom0Dark,
        OstimateColors.Custom1Dark,
        OstimateColors.Custom2Dark,
        OstimateColors.Custom3Dark,
        OstimateColors.Custom4Dark,
        OstimateColors.Custom5Dark,
        OstimateColors.Custom6Dark,
        OstimateColors.Custom7Dark,
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageSuppliesScreen(
    onBack: () -> Unit,
    viewModel: ManageSuppliesViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    // Precise-count edit dialog
    val countTarget = uiState.editCountTarget
    if (countTarget != null) {
        EditCountDialog(target = countTarget, onDismiss = viewModel::dismissEditCount) { count ->
            viewModel.setOnHand(countTarget.id, count)
            viewModel.dismissEditCount()
        }
    }

    // Edit supply details dialog
    val editTarget = uiState.editSupplyTarget
    if (editTarget != null) {
        EditSupplyDialog(
            supply = editTarget,
            onDismiss = viewModel::dismissEditSupply,
            onSave = { name, boxSize, warnDays, colorIndex ->
                viewModel.saveSupplyDetails(editTarget.id, name, boxSize, warnDays, colorIndex)
                viewModel.dismissEditSupply()
            },
            onArchive = { viewModel.archiveSupply(editTarget.id) },
        )
    }

    // Add custom supply dialog
    if (uiState.showAddDialog) {
        AddCustomSupplyDialog(
            onDismiss = viewModel::dismissAddDialog,
            onAdd = { name, boxSize, warnDays, colorIndex ->
                viewModel.addCustomSupply(name, boxSize, warnDays, colorIndex)
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.manage_supplies_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.cd_back))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = viewModel::showAddDialog,
                modifier = Modifier.testTag("addSupplyFab"),
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(Res.string.cd_add_supply))
            }
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
                    bottom = innerPadding.calculateBottomPadding() + 80.dp,
                ),
        ) {
            items(uiState.supplies, key = { it.id }) { supply ->
                SupplyRow(
                    supply = supply,
                    onAdjust = { delta ->
                        viewModel.requestAdjustOnHand(supply.id, supply.onHand, delta)
                    },
                    onEditCount = { viewModel.requestEditCount(supply) },
                    onEditDetails = { viewModel.openEditSupply(supply) },
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            }
        }
    }
}

@Composable
private fun SupplyRow(
    supply: SupplyTypeEntity,
    onAdjust: (delta: Int) -> Unit,
    onEditCount: () -> Unit,
    onEditDetails: () -> Unit,
) {
    val accent = supplyColor(supply.kind, supply.colorIndex)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Color dot
        Box(
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
                stringResource(Res.string.manage_supplies_on_hand_box, supply.onHand, supply.boxSize),
                style = MaterialTheme.typography.bodySmall,
                color = accent,
            )
        }

        // − / count / + inline controls
        IconButton(
            onClick = { onAdjust(-supply.boxSize) },
            modifier = Modifier.testTag("decrementBox_${supply.id}"),
        ) {
            Text(
                "−",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            supply.onHand.toString(),
            style = MaterialTheme.typography.titleMedium,
            modifier =
                Modifier
                    .clickable(onClick = onEditCount)
                    .padding(horizontal = 4.dp)
                    .testTag("supplyCount_${supply.id}"),
        )
        IconButton(
            onClick = { onAdjust(+supply.boxSize) },
            modifier = Modifier.testTag("incrementBox_${supply.id}"),
        ) {
            Text(
                "+",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        IconButton(
            onClick = onEditDetails,
            modifier = Modifier.testTag("editSupplyButton_${supply.id}"),
        ) {
            Icon(
                Icons.Filled.Edit,
                contentDescription = stringResource(Res.string.cd_edit_supply, supply.name),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EditCountDialog(
    target: SupplyTypeEntity,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit,
) {
    var input by rememberSaveable { mutableStateOf(target.onHand.toString()) }
    LaunchedEffect(target.id) { input = target.onHand.toString() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.manage_supplies_set_count_title, target.name)) },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it.filter { c -> c.isDigit() } },
                label = { Text(stringResource(Res.string.manage_supplies_on_hand_label)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.testTag("editCountField"),
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val count = input.toIntOrNull() ?: return@TextButton
                    onSave(count)
                },
            ) { Text(stringResource(Res.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
        },
    )
}

@Composable
private fun EditSupplyDialog(
    supply: SupplyTypeEntity,
    onDismiss: () -> Unit,
    onSave: (name: String, boxSize: Int, warnThresholdDays: Int, colorIndex: Int?) -> Unit,
    onArchive: () -> Unit,
) {
    var nameInput by rememberSaveable(supply.id) { mutableStateOf(supply.name) }
    var boxSizeInput by rememberSaveable(supply.id) { mutableStateOf(supply.boxSize.toString()) }
    var warnInput by rememberSaveable(supply.id) { mutableStateOf(supply.warnThresholdDays.toString()) }
    var selectedColor by rememberSaveable(supply.id) { mutableIntStateOf(supply.colorIndex ?: 0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.manage_supplies_edit_title, supply.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text(stringResource(Res.string.manage_supplies_name_label)) },
                    singleLine = true,
                )

                OutlinedTextField(
                    value = boxSizeInput,
                    onValueChange = { boxSizeInput = it.filter { c -> c.isDigit() } },
                    label = { Text(stringResource(Res.string.manage_supplies_box_size_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )

                OutlinedTextField(
                    value = warnInput,
                    onValueChange = { warnInput = it.filter { c -> c.isDigit() } },
                    label = { Text(stringResource(Res.string.manage_supplies_warn_days_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )

                if (supply.kind == SupplyKind.CUSTOM) {
                    Text(stringResource(Res.string.manage_supplies_color_label), style = MaterialTheme.typography.labelMedium)
                    ColorPickerRow(selected = selectedColor, onSelect = { selectedColor = it })
                }

                if (supply.kind == SupplyKind.CUSTOM) {
                    Spacer(Modifier.height(4.dp))
                    TextButton(
                        onClick = onArchive,
                        modifier = Modifier.testTag("archiveSupplyButton"),
                    ) {
                        Text(stringResource(Res.string.manage_supplies_archive), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val boxSize = boxSizeInput.toIntOrNull() ?: return@TextButton
                    val warn = warnInput.toIntOrNull() ?: return@TextButton
                    val colorIndex = if (supply.kind == SupplyKind.CUSTOM) selectedColor else supply.colorIndex
                    onSave(nameInput, boxSize, warn, colorIndex)
                },
            ) { Text(stringResource(Res.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
        },
    )
}

@Composable
private fun AddCustomSupplyDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, boxSize: Int, warnThresholdDays: Int, colorIndex: Int) -> Unit,
) {
    var nameInput by rememberSaveable { mutableStateOf("") }
    var boxSizeInput by rememberSaveable { mutableStateOf("10") }
    var warnInput by rememberSaveable { mutableStateOf("14") }
    var selectedColor by rememberSaveable { mutableIntStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.manage_supplies_add_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text(stringResource(Res.string.manage_supplies_name_label)) },
                    singleLine = true,
                    modifier = Modifier.testTag("addSupplyNameField"),
                )

                OutlinedTextField(
                    value = boxSizeInput,
                    onValueChange = { boxSizeInput = it.filter { c -> c.isDigit() } },
                    label = { Text(stringResource(Res.string.manage_supplies_box_size_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )

                OutlinedTextField(
                    value = warnInput,
                    onValueChange = { warnInput = it.filter { c -> c.isDigit() } },
                    label = { Text(stringResource(Res.string.manage_supplies_warn_days_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )

                Text(stringResource(Res.string.manage_supplies_color_label), style = MaterialTheme.typography.labelMedium)
                ColorPickerRow(selected = selectedColor, onSelect = { selectedColor = it })
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (nameInput.isBlank()) return@TextButton
                    val boxSize = boxSizeInput.toIntOrNull() ?: return@TextButton
                    val warn = warnInput.toIntOrNull() ?: return@TextButton
                    onAdd(nameInput, boxSize, warn, selectedColor)
                },
                modifier = Modifier.testTag("confirmAddSupply"),
            ) { Text(stringResource(Res.string.manage_supplies_add_button)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
        },
    )
}

@Composable
private fun ColorPickerRow(
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        CUSTOM_COLORS.forEachIndexed { index, color ->
            ColorSwatch(
                color = color,
                isSelected = index == selected,
                onClick = { onSelect(index) },
            )
        }
    }
}

@Composable
private fun ColorSwatch(
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(color)
                .then(
                    if (isSelected) {
                        Modifier.border(2.dp, Color.White, CircleShape)
                    } else {
                        Modifier
                    },
                )
                .clickable(onClick = onClick),
    ) {
        if (isSelected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
