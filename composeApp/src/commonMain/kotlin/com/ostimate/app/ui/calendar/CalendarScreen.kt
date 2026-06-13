@file:Suppress("DEPRECATION") // kotlinx-datetime 0.6.x deprecation — see CalendarViewModel

package com.ostimate.app.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ostimate.app.data.db.ChangeEventEntity
import com.ostimate.app.data.db.ChangeEventWithSupply
import com.ostimate.app.data.db.SupplyTypeEntity
import com.ostimate.app.platform.formatTimestamp
import com.ostimate.app.ui.components.Pill
import com.ostimate.app.ui.history.EditEventDialog
import com.ostimate.app.ui.theme.supplyColor
import kotlinx.datetime.LocalDate
import org.koin.compose.viewmodel.koinViewModel

private val WEEKDAY_LABELS = listOf("S", "M", "T", "W", "T", "F", "S")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(viewModel: CalendarViewModel = koinViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var editEvent by remember { mutableStateOf<ChangeEventEntity?>(null) }
    var showAddEntryDialog by remember { mutableStateOf(false) }

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

    if (showAddEntryDialog) {
        AddEntryDialog(
            supplies = uiState.supplies,
            date = uiState.selectedDate,
            onDismiss = { showAddEntryDialog = false },
            onConfirm = { supplyId ->
                val date = uiState.selectedDate ?: return@AddEntryDialog
                viewModel.addEventForDate(supplyId, date)
                showAddEntryDialog = false
            },
        )
    }

    if (uiState.selectedDayEvents != null) {
        ModalBottomSheet(
            onDismissRequest = viewModel::dismissSheet,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            DayDetailSheet(
                label = uiState.selectedDayLabel,
                events = uiState.selectedDayEvents!!,
                onEdit = { editEvent = it },
                onDelete = viewModel::deleteEvent,
                onAddEntry = { showAddEntryDialog = true },
            )
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0),
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 12.dp),
        ) {
            // Month nav header
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = viewModel::prevMonth) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous month")
                }
                Text(
                    text = uiState.monthLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
                IconButton(onClick = viewModel::nextMonth) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next month")
                }
            }

            // Weekday headers — Sunday-first (US convention)
            Row(modifier = Modifier.fillMaxWidth()) {
                WEEKDAY_LABELS.forEach { label ->
                    Text(
                        text = label,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                modifier = Modifier.fillMaxWidth(),
                userScrollEnabled = false,
            ) {
                items(uiState.days, key = { it.date.toString() }) { day ->
                    DayCell(
                        day = day,
                        onClick = { if (day.isCurrentMonth) viewModel.selectDay(day.date) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: CalendarDay,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .aspectRatio(0.75f)
                .clickable(enabled = day.isCurrentMonth, onClick = onClick)
                .padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        Box(
            modifier =
                Modifier
                    .size(26.dp)
                    .then(
                        if (day.isToday) {
                            Modifier
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        } else {
                            Modifier
                        },
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = day.date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodySmall,
                color =
                    when {
                        day.isToday -> MaterialTheme.colorScheme.onPrimary
                        day.isCurrentMonth -> MaterialTheme.colorScheme.onSurface
                        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                    },
                fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal,
            )
        }

        val shown = day.pills.take(2)
        val overflow = day.pills.size - shown.size
        shown.forEach { pill ->
            Pill(
                label = pill.count.toString(),
                kind = pill.kind,
                modifier = Modifier.padding(top = 1.dp),
            )
        }
        if (overflow > 0) {
            Text(
                text = "+$overflow",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            )
        }
    }
}

@Composable
private fun DayDetailSheet(
    label: String,
    events: List<ChangeEventWithSupply>,
    onEdit: (ChangeEventEntity) -> Unit,
    onDelete: (ChangeEventEntity) -> Unit,
    onAddEntry: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        if (events.isEmpty()) {
            Text(
                "No events this day.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        } else {
            events.forEach { row ->
                DayEventCard(row = row, onEdit = { onEdit(row.event) }, onDelete = { onDelete(row.event) })
            }
        }
        TextButton(
            onClick = onAddEntry,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("+ Add an entry for this day")
        }
    }
}

@Composable
private fun DayEventCard(
    row: ChangeEventWithSupply,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(supplyColor(row.supplyKind)),
            )
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(start = 10.dp),
            ) {
                Text(row.supplyName, style = MaterialTheme.typography.bodyLarge)
                Text(
                    formatTimestamp(row.event.timestampMillis),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
                val note = row.event.note
                if (!note.isNullOrBlank()) {
                    Text(
                        note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Delete event",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun AddEntryDialog(
    supplies: List<SupplyTypeEntity>,
    date: LocalDate?,
    onDismiss: () -> Unit,
    onConfirm: (supplyId: Long) -> Unit,
) {
    if (supplies.isEmpty() || date == null) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add entry") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Which supply did you use?",
                    style = MaterialTheme.typography.bodyMedium,
                )
                supplies.forEach { supply ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Spacer(
                            Modifier
                                .size(10.dp)
                                .background(supplyColor(supply.kind), CircleShape),
                        )
                        TextButton(
                            onClick = { onConfirm(supply.id) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(supply.name, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
