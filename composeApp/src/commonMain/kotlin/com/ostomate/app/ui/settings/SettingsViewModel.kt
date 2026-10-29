package com.ostomate.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ostomate.app.data.BackupRepository
import com.ostomate.app.data.RestoreResult
import com.ostomate.app.data.diagnostics.DiagnosticLog
import com.ostomate.app.data.settings.AppSettings
import com.ostomate.app.data.settings.SettingsRepository
import com.ostomate.app.domain.drive.DriveBackupStatus
import com.ostomate.app.domain.drive.driveBackupStatus
import com.ostomate.app.platform.CrashReporting
import com.ostomate.app.platform.DriveAuthorization
import com.ostomate.app.platform.DriveBackupPlatform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/** Why turning the daily Drive backup on did not take; shown under the switch. */
enum class DriveBackupEnableError {
    /** The user backed out of Google's sign-in or consent screen. */
    CANCELLED,
    FAILED,
}

data class DriveBackupUiState(
    /** False on iOS: neither the switch nor the status line is shown. */
    val supported: Boolean = false,
    val enabled: Boolean = false,
    /** Google's consent screen is up; the switch ignores taps until it answers. */
    val authorizing: Boolean = false,
    /** Null while the feature is off, which hides the status line. */
    val status: DriveBackupStatus? = null,
    val enableError: DriveBackupEnableError? = null,
)

data class BackupUiState(
    val lastRestore: RestoreResult? = null,
    val isBusy: Boolean = false,
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val backupRepository: BackupRepository,
    private val crashReporter: CrashReporting,
    private val diagnosticLog: DiagnosticLog,
    private val driveBackupPlatform: DriveBackupPlatform,
) : ViewModel() {
    val settings: StateFlow<AppSettings> =
        settingsRepository.settings.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            AppSettings(),
        )

    private val _backupState = MutableStateFlow(BackupUiState())
    val backupState: StateFlow<BackupUiState> = _backupState

    private val driveAuthorizing = MutableStateFlow(false)
    private val driveEnableError = MutableStateFlow<DriveBackupEnableError?>(null)

    val driveBackup: StateFlow<DriveBackupUiState> =
        combine(settingsRepository.driveBackup, driveAuthorizing, driveEnableError) { stored, authorizing, error ->
            DriveBackupUiState(
                supported = driveBackupPlatform.isSupported,
                enabled = stored.enabled,
                authorizing = authorizing,
                status = driveBackupStatus(stored.enabled, stored.lastRunAtMillis, stored.lastFailure),
                enableError = error,
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            DriveBackupUiState(supported = driveBackupPlatform.isSupported),
        )

    /**
     * On: Google sign-in/consent first, and only on success is the feature stored as on and
     * the daily job scheduled. Off: the job is cancelled and the feature stored as off.
     */
    fun setDriveBackupEnabled(enabled: Boolean) {
        if (!driveBackupPlatform.isSupported || driveAuthorizing.value) return
        driveEnableError.value = null
        if (!enabled) {
            viewModelScope.launch {
                driveBackupPlatform.cancelDaily()
                settingsRepository.setDriveBackupEnabled(false)
            }
            return
        }
        driveAuthorizing.value = true
        viewModelScope.launch {
            when (driveBackupPlatform.authorizeInteractively()) {
                is DriveAuthorization.Authorized -> {
                    settingsRepository.setDriveBackupEnabled(true)
                    driveBackupPlatform.scheduleDaily()
                }
                DriveAuthorization.Cancelled -> driveEnableError.value = DriveBackupEnableError.CANCELLED
                DriveAuthorization.NeedsUserInteraction,
                DriveAuthorization.Failed,
                -> driveEnableError.value = DriveBackupEnableError.FAILED
            }
            driveAuthorizing.value = false
        }
    }

    fun setLockSettings(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setLockSettings(enabled) }
    }

    fun setDevMode(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setDevMode(enabled) }
    }

    fun setCrashReporting(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setCrashReportingEnabled(enabled)
            crashReporter.setEnabled(enabled)
        }
    }

    fun exportBackup(onReady: (content: String, fileName: String) -> Unit) {
        viewModelScope.launch {
            _backupState.value = _backupState.value.copy(isBusy = true)
            val json = backupRepository.exportBackup()
            _backupState.value = _backupState.value.copy(isBusy = false)
            onReady(json, "ostomate_backup_${fileTimestamp()}.json")
        }
    }

    /** Exports the rolling on-device diagnostic log (local-first; user-initiated share only). */
    fun exportDiagnosticLog(onReady: (content: String, fileName: String) -> Unit) {
        val content = diagnosticLog.export()
        onReady(content, "ostomate_diagnostics_${fileTimestamp()}.log")
    }

    private fun fileTimestamp(): String {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        return "${now.year}-${now.monthNumber.toString().padStart(
            2,
            '0',
        )}-${now.dayOfMonth.toString().padStart(
            2,
            '0',
        )}_${now.hour.toString().padStart(
            2,
            '0',
        )}-${now.minute.toString().padStart(2, '0')}-${now.second.toString().padStart(2, '0')}"
    }

    fun restoreBackup(json: String) {
        viewModelScope.launch {
            _backupState.value = _backupState.value.copy(isBusy = true)
            val result = backupRepository.restoreBackup(json)
            _backupState.value =
                _backupState.value.copy(
                    isBusy = false,
                    lastRestore = result,
                )
        }
    }

    fun clearRestoreResult() {
        _backupState.value = _backupState.value.copy(lastRestore = null)
    }
}
