package com.ostimate.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ostimate.app.data.db.ChangeEventWithSupply
import com.ostimate.app.platform.BiometricAuthenticator
import com.ostimate.app.platform.Notifier
import com.ostimate.app.platform.formatTimestamp
import com.ostimate.app.ui.theme.supplyColor
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun HomeScreen(viewModel: HomeViewModel = koinViewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Ostimate", style = MaterialTheme.typography.headlineMedium)

        uiState.supplies.forEach { supply ->
            LogButton(
                label = "Log ${supply.name.lowercase()} change",
                color = supplyColor(supply.kind),
                onClick = { viewModel.logChange(supply) },
            )
        }

        Text(
            "${uiState.events.size} change${if (uiState.events.size == 1) "" else "s"} logged",
            style = MaterialTheme.typography.titleMedium,
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(uiState.events, key = { it.event.id }) { row ->
                EventRow(row = row, onDelete = { viewModel.delete(row.event) })
            }
        }

        SpikeChecks()
    }
}

// Phase 0 spike checks — exercises the notification and biometric expect/actuals
// on a real device. Remove in Phase 1.
@Composable
private fun SpikeChecks() {
    val notifier = koinInject<Notifier>()
    val biometric = koinInject<BiometricAuthenticator>()
    var biometricResult by remember { mutableStateOf("") }

    Column {
        Text("Phase 0 spike checks", style = MaterialTheme.typography.labelSmall)
        Row {
            TextButton(onClick = {
                notifier.schedule(10, "Ostimate", "Test reminder — notifications work")
            }) { Text("Test notification (10 s)") }
            TextButton(onClick = {
                biometric.authenticate("Unlock Ostimate") {
                    biometricResult = it.toString().substringAfterLast('.')
                }
            }) { Text("Test biometric") }
        }
        if (biometricResult.isNotEmpty()) {
            Text("Biometric: $biometricResult", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun LogButton(label: String, color: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White),
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun EventRow(row: ChangeEventWithSupply, onDelete: () -> Unit) {
    Card(shape = RoundedCornerShape(12.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(
                Modifier
                    .size(10.dp)
                    .background(supplyColor(row.supplyKind), CircleShape)
            )
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(row.supplyName, style = MaterialTheme.typography.bodyLarge)
                Text(
                    formatTimestamp(row.event.timestampMillis),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            TextButton(onClick = onDelete) { Text("Delete") }
        }
    }
}
