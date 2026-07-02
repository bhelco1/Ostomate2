package com.ostomate.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ostomate.app.data.SupplyRepository
import com.ostomate.app.data.db.SupplyTypeEntity
import com.ostomate.app.data.settings.SettingsRepository
import com.ostomate.app.platform.BiometricAuth
import com.ostomate.app.platform.BiometricResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ManageSuppliesUiState(
    val supplies: List<SupplyTypeEntity> = emptyList(),
    val editCountTarget: SupplyTypeEntity? = null,
    val editSupplyTarget: SupplyTypeEntity? = null,
    val showAddDialog: Boolean = false,
    /** True once biometric passed this session; inline +/- no longer re-prompt. */
    val sessionUnlocked: Boolean = false,
)

class ManageSuppliesViewModel(
    private val supplyRepository: SupplyRepository,
    private val settingsRepository: SettingsRepository,
    private val biometricAuth: BiometricAuth,
) : ViewModel() {
    private val _editCountTarget = MutableStateFlow<SupplyTypeEntity?>(null)
    private val _editSupplyTarget = MutableStateFlow<SupplyTypeEntity?>(null)
    private val _showAddDialog = MutableStateFlow(false)
    private val _sessionUnlocked = MutableStateFlow(false)

    val uiState: StateFlow<ManageSuppliesUiState> =
        combine(
            supplyRepository.observeSupplies(),
            _editCountTarget,
            _editSupplyTarget,
            _showAddDialog,
            _sessionUnlocked,
        ) { supplies, editCount, editSupply, showAdd, unlocked ->
            ManageSuppliesUiState(
                supplies = supplies,
                editCountTarget = editCount,
                editSupplyTarget = editSupply,
                showAddDialog = showAdd,
                sessionUnlocked = unlocked,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ManageSuppliesUiState())

    /** Called by the precise-count edit dialog. Prompts biometric if lockSettings is on. */
    fun requestEditCount(supply: SupplyTypeEntity) {
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            if (!settings.lockSettings || _sessionUnlocked.value) {
                _editCountTarget.value = supply
                return@launch
            }
            biometricAuth.authenticate("Authenticate to edit supply count") { result ->
                when (result) {
                    BiometricResult.Success, BiometricResult.NotEnrolled -> {
                        _sessionUnlocked.value = true
                        _editCountTarget.value = supply
                    }
                    BiometricResult.Failed -> {}
                }
            }
        }
    }

    fun dismissEditCount() {
        _editCountTarget.value = null
    }

    fun resetSession() {
        _sessionUnlocked.value = false
    }

    fun setOnHand(
        id: Long,
        count: Int,
    ) {
        viewModelScope.launch { supplyRepository.setOnHand(id, maxOf(0, count)) }
    }

    /**
     * Called by the +/- box buttons. Prompts biometric on first use if lockSettings is on;
     * subsequent taps in the same screen session skip the prompt.
     */
    fun requestAdjustOnHand(
        id: Long,
        currentOnHand: Int,
        delta: Int,
    ) {
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            if (!settings.lockSettings || _sessionUnlocked.value) {
                supplyRepository.setOnHand(id, maxOf(0, currentOnHand + delta))
                return@launch
            }
            biometricAuth.authenticate("Authenticate to edit supply count") { result ->
                when (result) {
                    BiometricResult.Success, BiometricResult.NotEnrolled -> {
                        _sessionUnlocked.value = true
                        viewModelScope.launch {
                            supplyRepository.setOnHand(id, maxOf(0, currentOnHand + delta))
                        }
                    }
                    BiometricResult.Failed -> {}
                }
            }
        }
    }

    fun openEditSupply(supply: SupplyTypeEntity) {
        _editSupplyTarget.value = supply
    }

    fun dismissEditSupply() {
        _editSupplyTarget.value = null
    }

    fun saveSupplyDetails(
        id: Long,
        name: String,
        boxSize: Int,
        warnThresholdDays: Int,
        colorIndex: Int?,
    ) {
        viewModelScope.launch {
            val current = uiState.value.supplies.find { it.id == id } ?: return@launch
            supplyRepository.update(
                current.copy(
                    name = name.trim().ifEmpty { current.name },
                    boxSize = maxOf(1, boxSize),
                    warnThresholdDays = maxOf(1, warnThresholdDays),
                    colorIndex = colorIndex,
                ),
            )
        }
    }

    fun archiveSupply(id: Long) {
        viewModelScope.launch {
            supplyRepository.archive(id)
            _editSupplyTarget.value = null
        }
    }

    fun showAddDialog() {
        _showAddDialog.value = true
    }

    fun dismissAddDialog() {
        _showAddDialog.value = false
    }

    fun addCustomSupply(
        name: String,
        boxSize: Int,
        warnThresholdDays: Int,
        colorIndex: Int,
    ) {
        viewModelScope.launch {
            supplyRepository.addCustomSupply(
                name = name.trim(),
                boxSize = maxOf(1, boxSize),
                warnThresholdDays = maxOf(1, warnThresholdDays),
                colorIndex = colorIndex,
            )
            _showAddDialog.value = false
        }
    }
}
