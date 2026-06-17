package com.ostomate.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ostomate.app.data.BackupRepository
import com.ostomate.app.data.ImportSummary
import com.ostomate.app.data.settings.AppSettings
import com.ostomate.app.data.settings.SettingsRepository
import com.ostomate.app.platform.BiometricAuthenticator
import com.ostomate.app.platform.BiometricResult
import com.ostomate.app.platform.CrashReporter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BackupUiState(
    val exportedCsv: String? = null,
    val lastImportSummary: ImportSummary? = null,
    val isBusy: Boolean = false,
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val backupRepository: BackupRepository,
    private val crashReporter: CrashReporter,
    private val biometricAuthenticator: BiometricAuthenticator,
) : ViewModel() {
    val settings: StateFlow<AppSettings> =
        settingsRepository.settings.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            AppSettings(),
        )

    private val _backupState = MutableStateFlow(BackupUiState())
    val backupState: StateFlow<BackupUiState> = _backupState

    private val _isLocked = MutableStateFlow(false)
    val isLocked: StateFlow<Boolean> = _isLocked

    private val _authError = MutableStateFlow(false)
    val authError: StateFlow<Boolean> = _authError

    init {
        viewModelScope.launch {
            if (settingsRepository.settings.first().lockSettings) {
                _isLocked.value = true
            }
        }
    }

    fun relockIfNeeded() {
        if (settings.value.lockSettings) {
            _isLocked.value = true
            _authError.value = false
        }
    }

    fun unlock(reason: String) {
        biometricAuthenticator.authenticate(reason) { result ->
            when (result) {
                BiometricResult.Success -> {
                    _isLocked.value = false
                    _authError.value = false
                }
                BiometricResult.NotEnrolled -> {
                    _isLocked.value = false
                    _authError.value = false
                }
                BiometricResult.Failed -> {
                    _authError.value = true
                }
            }
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

    /** Generates the v2 CSV and emits it for the platform layer to share. */
    fun exportCsv(onReady: (String) -> Unit) {
        viewModelScope.launch {
            _backupState.value = _backupState.value.copy(isBusy = true)
            val csv = backupRepository.exportCsv()
            _backupState.value = _backupState.value.copy(isBusy = false)
            onReady(csv)
        }
    }

    /** Imports a v1 CSV string from the platform file picker. */
    fun importV1Csv(csvContent: String) {
        viewModelScope.launch {
            _backupState.value = _backupState.value.copy(isBusy = true)
            val summary = backupRepository.importV1Csv(csvContent)
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
