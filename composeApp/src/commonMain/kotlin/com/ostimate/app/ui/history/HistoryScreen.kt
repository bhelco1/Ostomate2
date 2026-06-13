@file:Suppress(
    "DEPRECATION",
) // kotlinx.datetime.Instant/monthNumber/dayOfMonth deprecated in 0.6.x but replacements not yet stable

package com.ostimate.app.ui.history

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ostimate.app.data.db.ChangeEventEntity
import com.ostimate.app.data.db.ChangeEventWithSupply
import com.ostimate.app.platform.formatTimestamp
import com.ostimate.app.ui.theme.supplyColor
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    supplyId: Long,
    onBack: () -> Unit,
    viewModel: HistoryViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var editEvent by remember { mutableStateOf<ChangeEventEntity?>(null) }

    LaunchedEffect(uiState.pendingUndo) {
        val deleted = uiState.pendingUndo ?: return@LaunchedEffect
        val result =
            snackbarHostState.showSnackbar(
                message = "Event deleted",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short,
            )
        when (result) {
            SnackbarResult.ActionPerformed -> viewModel.undoDelete()
            SnackbarResult.Dismissed -> viewModel.clearUndo()
        }
    }

    editEvent?.let { event ->
        EditEventDialog(
            event = event,
            onDismiss = { editEvent = null },
            onSave = { updated ->
                viewModel.updateEvent(updated)
                editEvent = null
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0),
    ) { innerPadding ->
        if (uiState.events.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "No events yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding =
                    PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = innerPadding.calculateTopPadding() + 8.dp,
                        bottom = innerPadding.calculateBottomPadding() + 16.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(uiState.events, key = { it.event.id }) { row ->
                    SwipeableEventRow(
                        row = row,
                        onDelete = { viewModel.deleteEvent(row.event) },
                        onEdit = { editEvent = row.event },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableEventRow(
    row: ChangeEventWithSupply,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState()

    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
            onDelete()
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(
                            MaterialTheme.colorScheme.errorContainer,
                            RoundedCornerShape(12.dp),
                        )
                        .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
    ) {
        EventCard(row = row, onClick = onEdit)
    }
}

@Composable
private fun EventCard(
    row: ChangeEventWithSupply,
    onClick: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Spacer(
                Modifier
                    .size(10.dp)
                    .background(supplyColor(row.supplyKind), CircleShape),
            )
            Column(Modifier.weight(1f)) {
                Text(row.supplyName, style = MaterialTheme.typography.bodyLarge)
                Text(
                    formatTimestamp(row.event.timestampMillis),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
                if (row.event.editedAtMillis != null) {
                    Text(
                        "Edited",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    )
                }
            }
        }
    }
}

@Composable
internal fun EditEventDialog(
    event: ChangeEventEntity,
    onDismiss: () -> Unit,
    onSave: (ChangeEventEntity) -> Unit,
) {
    val tz = TimeZone.currentSystemDefault()
    val original = Instant.fromEpochMilliseconds(event.timestampMillis).toLocalDateTime(tz)

    fun twoDigit(n: Int) = n.toString().padStart(2, '0')

    var dateInput by rememberSaveable {
        mutableStateOf("${original.year}-${twoDigit(original.monthNumber)}-${twoDigit(original.dayOfMonth)}")
    }
    var timeInput by rememberSaveable {
        mutableStateOf("${twoDigit(original.hour)}:${twoDigit(original.minute)}")
    }
    var parseError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit event time") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = dateInput,
                    onValueChange = {
                        dateInput = it
                        parseError = false
                    },
                    label = { Text("Date (yyyy-MM-dd)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = parseError,
                )
                OutlinedTextField(
                    value = timeInput,
                    onValueChange = {
                        timeInput = it
                        parseError = false
                    },
                    label = { Text("Time (HH:mm)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = parseError,
                )
                if (parseError) {
                    Text(
                        "Invalid date or time",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val newMillis = parseToMillis(dateInput, timeInput, tz)
                    if (newMillis == null) {
                        parseError = true
                    } else {
                        onSave(event.copy(timestampMillis = newMillis))
                    }
                },
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private fun parseToMillis(
    dateStr: String,
    timeStr: String,
    tz: TimeZone,
): Long? =
    runCatching {
        val (year, month, day) = dateStr.trim().split("-").map { it.toInt() }
        val (hour, minute) = timeStr.trim().split(":").map { it.toInt() }
        LocalDateTime(year, month, day, hour, minute).toInstant(tz).toEpochMilliseconds()
    }.getOrNull()
