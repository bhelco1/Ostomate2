package com.ostimate.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ostimate.app.data.SupplyRepository
import com.ostimate.app.data.db.SupplyTypeEntity
import com.ostimate.app.data.settings.SettingsRepository
import com.ostimate.app.platform.BiometricAuthenticator
import com.ostimate.app.platform.BiometricResult
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
)

class ManageSuppliesViewModel(
    private val supplyRepository: SupplyRepository,
    private val settingsRepository: SettingsRepository,
    private val biometricAuth: BiometricAuthenticator,
) : ViewModel() {
    private val _editCountTarget = MutableStateFlow<SupplyTypeEntity?>(null)

    val uiState: StateFlow<ManageSuppliesUiState> =
        combine(
            supplyRepository.observeSupplies(),
            _editCountTarget,
        ) { supplies, editCountTarget ->
            ManageSuppliesUiState(supplies = supplies, editCountTarget = editCountTarget)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ManageSuppliesUiState())

    fun requestEditCount(supply: SupplyTypeEntity) {
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            if (!settings.lockSettings) {
                _editCountTarget.value = supply
                return@launch
            }
            biometricAuth.authenticate("Authenticate to edit supply count") { result ->
                when (result) {
                    BiometricResult.Success, BiometricResult.NotEnrolled -> {
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

    fun setOnHand(
        id: Long,
        count: Int,
    ) {
        viewModelScope.launch { supplyRepository.setOnHand(id, count) }
    }
}
