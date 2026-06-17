package com.ostomate.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ostomate.app.platform.FeedbackHelper
import com.ostomate.app.platform.FileSharer
import com.ostomate.app.resources.Res
import com.ostomate.app.resources.action_ok
import com.ostomate.app.resources.settings_app_version
import com.ostomate.app.resources.settings_backup
import com.ostomate.app.resources.settings_backup_sub
import com.ostomate.app.resources.settings_biometric_failed
import com.ostomate.app.resources.settings_biometric_lock
import com.ostomate.app.resources.settings_biometric_lock_sub
import com.ostomate.app.resources.settings_crash_reporting
import com.ostomate.app.resources.settings_crash_reporting_sub
import com.ostomate.app.resources.settings_dev_off_body
import com.ostomate.app.resources.settings_dev_off_title
import com.ostomate.app.resources.settings_dev_on_body
import com.ostomate.app.resources.settings_dev_on_title
import com.ostomate.app.resources.settings_export
import com.ostomate.app.resources.settings_feedback
import com.ostomate.app.resources.settings_feedback_sub
import com.ostomate.app.resources.settings_import
import com.ostomate.app.resources.settings_import_complete_title
import com.ostomate.app.resources.settings_import_result
import com.ostomate.app.resources.settings_import_too_large
import com.ostomate.app.resources.settings_import_v1
import com.ostomate.app.resources.settings_import_v1_sub
import com.ostomate.app.resources.settings_locked_prompt
import com.ostomate.app.resources.settings_manage_supplies
import com.ostomate.app.resources.settings_manage_supplies_sub
import com.ostomate.app.resources.settings_print_qr
import com.ostomate.app.resources.settings_print_qr_sub
import com.ostomate.app.resources.settings_privacy_policy
import com.ostomate.app.resources.settings_reorder_warnings
import com.ostomate.app.resources.settings_reorder_warnings_sub
import com.ostomate.app.resources.settings_section_about
import com.ostomate.app.resources.settings_section_about_dev
import com.ostomate.app.resources.settings_section_data
import com.ostomate.app.resources.settings_section_inventory
import com.ostomate.app.resources.settings_section_security
import com.ostomate.app.resources.settings_section_support
import com.ostomate.app.resources.settings_unlock
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Clock

private const val DEV_MODE_TAPS_REQUIRED = 5
private const val DEV_MODE_WINDOW_MS = 2_000L

@Composable
fun SettingsScreen(
    onNavigateToManageSupplies: () -> Unit = {},
    onNavigateToReorderWarnings: () -> Unit = {},
    onNavigateToPrivacyPolicy: () -> Unit = {},
    onNavigateToQrLabels: () -> Unit = {},
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val settings by viewModel.settings.collectAsState()
    val backupState by viewModel.backupState.collectAsState()
    val isLocked by viewModel.isLocked.collectAsState()
    val authError by viewModel.authError.collectAsState()
    val fileSharer = koinInject<FileSharer>()
    val feedbackHelper = koinInject<FeedbackHelper>()

    val lockedPrompt = stringResource(Res.string.settings_locked_prompt)

    // Re-lock whenever this screen resumes (covers tab switching).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.relockIfNeeded()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Hoist all remember state above the lock gate to respect Compose composition rules.
    var importTrigger by remember { mutableIntStateOf(0) }
    var showImportResult by remember { mutableStateOf(false) }

    // Dev-mode easter egg: 5 taps on the About section header within 2 seconds.
    var devTapCount by remember { mutableIntStateOf(0) }
    var devTapWindowStart by remember { mutableLongStateOf(0L) }
    var showDevModeToast by remember { mutableStateOf(false) }

    // Auto-trigger biometric prompt when the lock gate appears.
    LaunchedEffect(isLocked) {
        if (isLocked) viewModel.unlock(lockedPrompt)
    }

    if (isLocked) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(Icons.Filled.Lock, contentDescription = null)
                Text(lockedPrompt, style = MaterialTheme.typography.titleMedium)
                if (authError) {
                    Text(
                        stringResource(Res.string.settings_biometric_failed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Button(onClick = { viewModel.unlock(lockedPrompt) }) {
                    Text(stringResource(Res.string.settings_unlock))
                }
            }
        }
    } else {
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
        val dismiss = {
            showImportResult = false
            viewModel.clearImportSummary()
        }
        AlertDialog(
            onDismissRequest = dismiss,
            title = { Text(stringResource(Res.string.settings_import_complete_title)) },
            text = {
                if (importSummary.oversized) {
                    Text(stringResource(Res.string.settings_import_too_large))
                } else {
                    Text(
                        stringResource(
                            Res.string.settings_import_result,
                            importSummary.inserted,
                            importSummary.skipped,
                            importSummary.parseErrors,
                        ),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = dismiss) { Text(stringResource(Res.string.action_ok)) }
            },
        )
    }

    if (showDevModeToast) {
        AlertDialog(
            onDismissRequest = { showDevModeToast = false },
            title = {
                Text(
                    if (settings.devMode) {
                        stringResource(Res.string.settings_dev_on_title)
                    } else {
                        stringResource(Res.string.settings_dev_off_title)
                    },
                )
            },
            text = {
                Text(
                    if (settings.devMode) {
                        stringResource(Res.string.settings_dev_on_body)
                    } else {
                        stringResource(Res.string.settings_dev_off_body)
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = { showDevModeToast = false }) { Text(stringResource(Res.string.action_ok)) }
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

            SettingsSectionHeader(stringResource(Res.string.settings_section_inventory))
            ListItem(
                headlineContent = { Text(stringResource(Res.string.settings_manage_supplies)) },
                supportingContent = { Text(stringResource(Res.string.settings_manage_supplies_sub)) },
                modifier = Modifier.clickable(
                    onClickLabel = stringResource(Res.string.settings_manage_supplies),
                    onClick = { onNavigateToManageSupplies() },
                ),
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
            )
            ListItem(
                headlineContent = { Text(stringResource(Res.string.settings_print_qr)) },
                supportingContent = { Text(stringResource(Res.string.settings_print_qr_sub)) },
                modifier = Modifier.clickable(
                    onClickLabel = stringResource(Res.string.settings_print_qr),
                    onClick = { onNavigateToQrLabels() },
                ),
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
            )
            ListItem(
                headlineContent = { Text(stringResource(Res.string.settings_reorder_warnings)) },
                supportingContent = { Text(stringResource(Res.string.settings_reorder_warnings_sub)) },
                modifier = Modifier.clickable(
                    onClickLabel = stringResource(Res.string.settings_reorder_warnings),
                    onClick = { onNavigateToReorderWarnings() },
                ),
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
            )

            HorizontalDivider()
            SettingsSectionHeader(stringResource(Res.string.settings_section_security))
            ListItem(
                headlineContent = { Text(stringResource(Res.string.settings_biometric_lock)) },
                supportingContent = { Text(stringResource(Res.string.settings_biometric_lock_sub)) },
                trailingContent = {
                    Switch(
                        checked = settings.lockSettings,
                        onCheckedChange = viewModel::setLockSettings,
                        modifier = Modifier.testTag("biometricLockSwitch"),
                    )
                },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
            )
            ListItem(
                headlineContent = { Text(stringResource(Res.string.settings_crash_reporting)) },
                supportingContent = { Text(stringResource(Res.string.settings_crash_reporting_sub)) },
                trailingContent = {
                    Switch(
                        checked = settings.crashReportingEnabled,
                        onCheckedChange = viewModel::setCrashReporting,
                        modifier = Modifier.testTag("crashReportingSwitch"),
                    )
                },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
            )

            HorizontalDivider()
            SettingsSectionHeader(stringResource(Res.string.settings_section_data))
            ListItem(
                headlineContent = { Text(stringResource(Res.string.settings_backup)) },
                supportingContent = { Text(stringResource(Res.string.settings_backup_sub)) },
                trailingContent = {
                    TextButton(
                        onClick = {
                            viewModel.exportCsv { csv ->
                                fileSharer.shareText(
                                    content = csv,
                                    fileName = "ostomate_backup.csv",
                                    mimeType = "text/csv",
                                )
                            }
                        },
                        enabled = !backupState.isBusy,
                    ) {
                        Text(stringResource(Res.string.settings_export))
                    }
                },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
            )
            ListItem(
                headlineContent = { Text(stringResource(Res.string.settings_import_v1)) },
                supportingContent = { Text(stringResource(Res.string.settings_import_v1_sub)) },
                trailingContent = {
                    TextButton(
                        onClick = { importTrigger++ },
                        enabled = !backupState.isBusy,
                    ) {
                        Text(stringResource(Res.string.settings_import))
                    }
                },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
            )

            HorizontalDivider()
            SettingsSectionHeader(stringResource(Res.string.settings_section_support))
            ListItem(
                headlineContent = { Text(stringResource(Res.string.settings_feedback)) },
                supportingContent = { Text(stringResource(Res.string.settings_feedback_sub)) },
                modifier = Modifier.clickable(
                    onClickLabel = stringResource(Res.string.settings_feedback),
                    onClick = { feedbackHelper.launch() },
                ),
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
            )

            HorizontalDivider()
            // Tap 5× in 2 s to toggle dev mode
            SettingsSectionHeader(
                title =
                    if (settings.devMode) {
                        stringResource(Res.string.settings_section_about_dev)
                    } else {
                        stringResource(Res.string.settings_section_about)
                    },
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
            SettingsItem(title = "Ostomate", subtitle = stringResource(Res.string.settings_app_version))
            ListItem(
                headlineContent = {
                    Text(
                        stringResource(Res.string.settings_privacy_policy),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                modifier = Modifier.clickable(
                    onClickLabel = stringResource(Res.string.settings_privacy_policy),
                    onClick = { onNavigateToPrivacyPolicy() },
                ),
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
            )

            Spacer(Modifier.height(16.dp))
        }
    }
    } // end else (not locked)
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
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).semantics { heading() },
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
    )
}
