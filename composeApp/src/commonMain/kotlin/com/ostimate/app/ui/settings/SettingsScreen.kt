package com.ostimate.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ostimate.app.platform.FileSharer
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = koinViewModel()) {
    val settings by viewModel.settings.collectAsState()
    val backupState by viewModel.backupState.collectAsState()
    val fileSharer = koinInject<FileSharer>()

    var importTrigger by remember { mutableIntStateOf(0) }
    var showImportResult by remember { mutableStateOf(false) }

    FileImportLauncher(
        trigger = importTrigger,
        mimeType = "text/csv",
        onContent = { content ->
            if (content != null) {
                viewModel.importV1Csv(content)
                showImportResult = true
            }
        },
    )

    val importSummary = backupState.lastImportSummary
    if (showImportResult && importSummary != null) {
        AlertDialog(
            onDismissRequest = {
                showImportResult = false
                viewModel.clearImportSummary()
            },
            title = { Text("Import complete") },
            text = {
                Text(
                    "Inserted: ${importSummary.inserted}\n" +
                        "Skipped (duplicates): ${importSummary.skipped}\n" +
                        "Parse errors: ${importSummary.parseErrors}",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showImportResult = false
                    viewModel.clearImportSummary()
                }) { Text("OK") }
            },
        )
    }

    Scaffold(contentWindowInsets = WindowInsets(0)) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(12.dp))

            SettingsSectionHeader("Inventory")
            SettingsItem(title = "Manage Supplies", subtitle = "View and edit your supply catalog")
            SettingsItem(title = "Reorder Warnings", subtitle = "Per-supply threshold days")

            HorizontalDivider()
            SettingsSectionHeader("Security")
            ListItem(
                headlineContent = { Text("Biometric Lock") },
                supportingContent = { Text("Require biometrics to edit counts") },
                trailingContent = {
                    Switch(
                        checked = settings.lockSettings,
                        onCheckedChange = viewModel::setLockSettings,
                        modifier = Modifier.testTag("biometricLockSwitch"),
                    )
                },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
            )

            HorizontalDivider()
            SettingsSectionHeader("Data")
            ListItem(
                headlineContent = { Text("Backup & Restore") },
                supportingContent = { Text("Export your event history as CSV") },
                trailingContent = {
                    TextButton(
                        onClick = {
                            viewModel.exportCsv { csv ->
                                fileSharer.shareText(
                                    content = csv,
                                    fileName = "ostimate_backup.csv",
                                    mimeType = "text/csv",
                                )
                            }
                        },
                        enabled = !backupState.isBusy,
                    ) {
                        Text("Export")
                    }
                },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
            )
            ListItem(
                headlineContent = { Text("Import from v1") },
                supportingContent = { Text("Bring over your Ostomate v1 CSV export") },
                trailingContent = {
                    TextButton(
                        onClick = { importTrigger++ },
                        enabled = !backupState.isBusy,
                    ) {
                        Text("Import")
                    }
                },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
            )

            HorizontalDivider()
            SettingsSectionHeader("Support")
            SettingsItem(title = "Send Feedback", subtitle = "Report a bug or suggest a feature")

            HorizontalDivider()
            SettingsSectionHeader("About")
            SettingsItem(title = "Ostimate", subtitle = "v2.0.0-dev")

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

@Composable
private fun SettingsItem(
    title: String,
    subtitle: String,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = {
            Text(
                subtitle,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
    )
}
