package com.ostimate.app.ui.settings

import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ostimate.app.platform.FeedbackHelper
import com.ostimate.app.platform.FileSharer
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Clock

private const val DEV_MODE_TAPS_REQUIRED = 5
private const val DEV_MODE_WINDOW_MS = 2_000L

@Composable
fun SettingsScreen(
    onNavigateToManageSupplies: () -> Unit = {},
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val settings by viewModel.settings.collectAsState()
    val backupState by viewModel.backupState.collectAsState()
    val fileSharer = koinInject<FileSharer>()
    val feedbackHelper = koinInject<FeedbackHelper>()

    var importTrigger by remember { mutableIntStateOf(0) }
    var showImportResult by remember { mutableStateOf(false) }

    // Dev-mode easter egg: 5 taps on the About section header within 2 seconds.
    var devTapCount by remember { mutableIntStateOf(0) }
    var devTapWindowStart by remember { mutableLongStateOf(0L) }
    var showDevModeToast by remember { mutableStateOf(false) }

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

    if (showDevModeToast) {
        AlertDialog(
            onDismissRequest = { showDevModeToast = false },
            title = { Text(if (settings.devMode) "Dev mode ON" else "Dev mode OFF") },
            text = {
                Text(
                    if (settings.devMode) {
                        "Dev mode is active. Data is shared with production — use the dev mode " +
                            "flag to guard test-only UI in future phases."
                    } else {
                        "Dev mode disabled."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = { showDevModeToast = false }) { Text("OK") }
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
            ListItem(
                headlineContent = { Text("Manage Supplies") },
                supportingContent = { Text("View and update on-hand counts") },
                modifier = Modifier.clickable { onNavigateToManageSupplies() },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
            )
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
            ListItem(
                headlineContent = { Text("Send Feedback") },
                supportingContent = { Text("Report a bug or suggest a feature") },
                modifier = Modifier.clickable { feedbackHelper.launch() },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
            )

            HorizontalDivider()
            // Tap 5× in 2 s to toggle dev mode
            SettingsSectionHeader(
                title = "About" + if (settings.devMode) " [DEV]" else "",
                modifier =
                    Modifier.clickable {
                        val now = Clock.System.now().toEpochMilliseconds()
                        if (now - devTapWindowStart > DEV_MODE_WINDOW_MS) {
                            devTapCount = 1
                            devTapWindowStart = now
                        } else {
                            devTapCount++
                        }
                        if (devTapCount >= DEV_MODE_TAPS_REQUIRED) {
                            devTapCount = 0
                            viewModel.setDevMode(!settings.devMode)
                            showDevModeToast = true
                        }
                    },
            )
            SettingsItem(title = "Ostimate", subtitle = "v2.0.0-dev")

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SettingsSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
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
