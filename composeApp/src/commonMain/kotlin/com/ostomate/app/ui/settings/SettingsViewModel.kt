package com.ostomate.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ostomate.app.data.BackupRepository
import com.ostomate.app.data.RestoreResult
import com.ostomate.app.data.diagnostics.DiagnosticLog
import com.ostomate.app.data.settings.AppSettings
import com.ostomate.app.data.settings.SettingsRepository
import com.ostomate.app.platform.CrashReporting
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

data class BackupUiState(
    val lastRestore: RestoreResult? = null,
    val isBusy: Boolean = false,
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val backupRepository: BackupRepository,
    private val crashReporter: CrashReporting,
    private val diagnosticLog: DiagnosticLog,
) : ViewModel() {
    val settings: StateFlow<AppSettings> =
        settingsRepository.settings.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            AppSettings(),
        )

    private val _backupState = MutableStateFlow(BackupUiState())
    val backupState: StateFlow<BackupUiState> = _backupState

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
