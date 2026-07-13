package com.ostomate.app.ui.settings

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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ostomate.app.BuildInfo
import com.ostomate.app.data.RestoreError
import com.ostomate.app.data.RestoreResult
import com.ostomate.app.platform.FeedbackHelper
import com.ostomate.app.platform.FileSharer
import com.ostomate.app.resources.Res
import com.ostomate.app.resources.action_cancel
import com.ostomate.app.resources.action_ok
import com.ostomate.app.resources.settings_app_build
import com.ostomate.app.resources.settings_app_version
import com.ostomate.app.resources.settings_backup
import com.ostomate.app.resources.settings_backup_sub
import com.ostomate.app.resources.settings_biometric_lock
import com.ostomate.app.resources.settings_biometric_lock_sub
import com.ostomate.app.resources.settings_crash_reporting
import com.ostomate.app.resources.settings_crash_reporting_sub
import com.ostomate.app.resources.settings_dev_off_body
import com.ostomate.app.resources.settings_dev_off_title
import com.ostomate.app.resources.settings_dev_on_body
import com.ostomate.app.resources.settings_dev_on_title
import com.ostomate.app.resources.settings_diagnostics
import com.ostomate.app.resources.settings_diagnostics_export
import com.ostomate.app.resources.settings_diagnostics_sub
import com.ostomate.app.resources.settings_export_backup
import com.ostomate.app.resources.settings_feedback
import com.ostomate.app.resources.settings_feedback_sub
import com.ostomate.app.resources.settings_manage_supplies
import com.ostomate.app.resources.settings_manage_supplies_sub
import com.ostomate.app.resources.settings_print_qr
import com.ostomate.app.resources.settings_print_qr_sub
import com.ostomate.app.resources.settings_privacy_policy
import com.ostomate.app.resources.settings_reorder_warnings
import com.ostomate.app.resources.settings_reorder_warnings_sub
import com.ostomate.app.resources.settings_restore
import com.ostomate.app.resources.settings_restore_button
import com.ostomate.app.resources.settings_restore_complete_title
import com.ostomate.app.resources.settings_restore_confirm_body
import com.ostomate.app.resources.settings_restore_confirm_title
import com.ostomate.app.resources.settings_restore_error_format
import com.ostomate.app.resources.settings_restore_error_malformed
import com.ostomate.app.resources.settings_restore_error_oversized
import com.ostomate.app.resources.settings_restore_error_schema
import com.ostomate.app.resources.settings_restore_failed_title
import com.ostomate.app.resources.settings_restore_sub
import com.ostomate.app.resources.settings_restore_success
import com.ostomate.app.resources.settings_section_about
import com.ostomate.app.resources.settings_section_about_dev
import com.ostomate.app.resources.settings_section_data
import com.ostomate.app.resources.settings_section_inventory
import com.ostomate.app.resources.settings_section_security
import com.ostomate.app.resources.settings_section_support
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
    val fileSharer = koinInject<FileSharer>()
    val feedbackHelper = koinInject<FeedbackHelper>()

    var importTrigger by remember { mutableIntStateOf(0) }
    var showRestoreResult by remember { mutableStateOf(false) }
    var showRestoreConfirm by remember { mutableStateOf(false) }

    // Dev-mode easter egg: 5 taps on the About section header within 2 seconds.
    var devTapCount by remember { mutableIntStateOf(0) }
    var devTapWindowStart by remember { mutableLongStateOf(0L) }
    var showDevModeToast by remember { mutableStateOf(false) }

    FileImportLauncher(
        trigger = importTrigger,
        mimeType = "application/json",
        onContent = { content ->
            if (content != null) {
                viewModel.restoreBackup(content)
                showRestoreResult = true
            }
        },
    )

    if (showRestoreConfirm) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = false },
            title = { Text(stringResource(Res.string.settings_restore_confirm_title)) },
            text = { Text(stringResource(Res.string.settings_restore_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRestoreConfirm = false
                        importTrigger++
                    },
                ) { Text(stringResource(Res.string.settings_restore_button)) }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirm = false }) {
                    Text(stringResource(Res.string.action_cancel))
                }
            },
        )
    }

    val restoreResult = backupState.lastRestore
    if (showRestoreResult && restoreResult != null) {
        val dismiss = {
            showRestoreResult = false
            viewModel.clearRestoreResult()
        }
        AlertDialog(
            onDismissRequest = dismiss,
            title = {
                Text(
                    when (restoreResult) {
                        is RestoreResult.Success -> stringResource(Res.string.settings_restore_complete_title)
                        is RestoreResult.Failure -> stringResource(Res.string.settings_restore_failed_title)
                    },
                )
            },
            text = {
                Text(
                    when (restoreResult) {
                        is RestoreResult.Success ->
                            stringResource(
                                Res.string.settings_restore_success,
                                restoreResult.supplyTypes,
                                restoreResult.events,
                            )
                        is RestoreResult.Failure ->
                            stringResource(
                                when (restoreResult.error) {
                                    RestoreError.OVERSIZED -> Res.string.settings_restore_error_oversized
                                    RestoreError.MALFORMED -> Res.string.settings_restore_error_malformed
                                    RestoreError.UNSUPPORTED_FORMAT_VERSION -> Res.string.settings_restore_error_format
                                    RestoreError.UNSUPPORTED_SCHEMA_VERSION -> Res.string.settings_restore_error_schema
                                },
                            )
                    },
                )
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
                modifier =
                    Modifier.clickable(
                        onClickLabel = stringResource(Res.string.settings_manage_supplies),
                        onClick = { onNavigateToManageSupplies() },
                    ),
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
            )
            ListItem(
                headlineContent = { Text(stringResource(Res.string.settings_print_qr)) },
                supportingContent = { Text(stringResource(Res.string.settings_print_qr_sub)) },
                modifier =
                    Modifier.clickable(
                        onClickLabel = stringResource(Res.string.settings_print_qr),
                        onClick = { onNavigateToQrLabels() },
                    ),
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
            )
            ListItem(
                headlineContent = { Text(stringResource(Res.string.settings_reorder_warnings)) },
                supportingContent = { Text(stringResource(Res.string.settings_reorder_warnings_sub)) },
                modifier =
                    Modifier.clickable(
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
                            viewModel.exportBackup { json, fileName ->
                                fileSharer.shareText(
                                    content = json,
                                    fileName = fileName,
                                    mimeType = "application/json",
                                )
                            }
                        },
                        enabled = !backupState.isBusy,
                        modifier = Modifier.testTag("exportBackupButton"),
                    ) {
                        Text(stringResource(Res.string.settings_export_backup))
                    }
                },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
            )
            ListItem(
                headlineContent = { Text(stringResource(Res.string.settings_restore)) },
                supportingContent = { Text(stringResource(Res.string.settings_restore_sub)) },
                trailingContent = {
                    TextButton(
                        onClick = { showRestoreConfirm = true },
                        enabled = !backupState.isBusy,
                    ) {
                        Text(stringResource(Res.string.settings_restore_button))
                    }
                },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
            )
            ListItem(
                headlineContent = { Text(stringResource(Res.string.settings_diagnostics)) },
                supportingContent = { Text(stringResource(Res.string.settings_diagnostics_sub)) },
                trailingContent = {
                    TextButton(
                        onClick = {
                            viewModel.exportDiagnosticLog { content, fileName ->
                                fileSharer.shareText(
                                    content = content,
                                    fileName = fileName,
                                    mimeType = "text/plain",
                                )
                            }
                        },
                    ) {
                        Text(stringResource(Res.string.settings_diagnostics_export))
                    }
                },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
            )

            HorizontalDivider()
            SettingsSectionHeader(stringResource(Res.string.settings_section_support))
            ListItem(
                headlineContent = { Text(stringResource(Res.string.settings_feedback)) },
                supportingContent = { Text(stringResource(Res.string.settings_feedback_sub)) },
                modifier =
                    Modifier.clickable(
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
            SettingsItem(
                title = "Ostomate",
                subtitle =
                    stringResource(
                        Res.string.settings_app_build,
                        stringResource(Res.string.settings_app_version),
                        BuildInfo.GIT_SHA,
                    ),
            )
            ListItem(
                headlineContent = {
                    Text(
                        stringResource(Res.string.settings_privacy_policy),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                modifier =
                    Modifier.clickable(
                        onClickLabel = stringResource(Res.string.settings_privacy_policy),
                        onClick = { onNavigateToPrivacyPolicy() },
                    ),
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
            )

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
