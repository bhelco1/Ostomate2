package com.ostomate.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ostomate.app.data.BackupRepository
import com.ostomate.app.data.ImportSummary
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
    val exportedCsv: String? = null,
    val lastImportSummary: ImportSummary? = null,
    val isBusy: Boolean = false,
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val backupRepository: BackupRepository,
    private val crashReporter: CrashReporting,
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

    fun exportCsv(onReady: (content: String, fileName: String) -> Unit) {
        viewModelScope.launch {
            _backupState.value = _backupState.value.copy(isBusy = true)
            val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            val ts = "${now.year}-${now.monthNumber.toString().padStart(
                2,
                '0',
            )}-${now.dayOfMonth.toString().padStart(
                2,
                '0',
            )}_${now.hour.toString().padStart(
                2,
                '0',
            )}-${now.minute.toString().padStart(2, '0')}-${now.second.toString().padStart(2, '0')}"
            val csv = backupRepository.exportCsv()
            _backupState.value = _backupState.value.copy(isBusy = false)
            onReady(csv, "ostomate_backup_$ts.csv")
        }
    }

    fun importCsv(csvContent: String) {
        viewModelScope.launch {
            _backupState.value = _backupState.value.copy(isBusy = true)
            val summary = backupRepository.importCsv(csvContent)
            _backupState.value =
                _backupState.value.copy(
                    isBusy = false,
                    lastImportSummary = summary,
                )
        }
    }

    fun clearImportSummary() {
        _backupState.value = _backupState.value.copy(lastImportSummary = null)
    }
}
