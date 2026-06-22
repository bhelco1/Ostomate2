package com.ostomate.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ostomate.app.data.SupplyRepository
import com.ostomate.app.data.settings.SettingsRepository
import com.ostomate.app.domain.ApplianceType
import com.ostomate.app.domain.SupplyKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class OnboardingStep { APPLIANCE_TYPE, SUPPLIES, COUNTS, QR_EXPLAINER }

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.APPLIANCE_TYPE,
    val applianceType: ApplianceType = ApplianceType.TWO_PIECE,
    val selectedKinds: Set<SupplyKind> = setOf(SupplyKind.BAG, SupplyKind.FLANGE),
    val bagCount: String = "",
    val flangeCount: String = "",
) {
    val totalSteps: Int get() = if (applianceType == ApplianceType.ONE_PIECE) 3 else 4
    val displayStep: Int get() =
        when (applianceType) {
            ApplianceType.ONE_PIECE ->
                when (step) {
                    OnboardingStep.APPLIANCE_TYPE -> 1
                    OnboardingStep.SUPPLIES -> 2
                    OnboardingStep.COUNTS -> 2
                    OnboardingStep.QR_EXPLAINER -> 3
                }
            ApplianceType.TWO_PIECE ->
                when (step) {
                    OnboardingStep.APPLIANCE_TYPE -> 1
                    OnboardingStep.SUPPLIES -> 2
                    OnboardingStep.COUNTS -> 3
                    OnboardingStep.QR_EXPLAINER -> 4
                }
        }
}

class OnboardingViewModel(
    private val supplyRepository: SupplyRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun setApplianceType(type: ApplianceType) {
        _uiState.value =
            _uiState.value.copy(
                applianceType = type,
                selectedKinds =
                    if (type == ApplianceType.ONE_PIECE) {
                        setOf(SupplyKind.BAG)
                    } else {
                        setOf(SupplyKind.BAG, SupplyKind.FLANGE)
                    },
            )
    }

    fun toggleKind(kind: SupplyKind) {
        val current = _uiState.value.selectedKinds
        _uiState.value =
            _uiState.value.copy(
                selectedKinds = if (kind in current) current - kind else current + kind,
            )
    }

    fun setBagCount(value: String) {
        val raw = value.filter { it.isDigit() }.take(4)
        _uiState.value = _uiState.value.copy(bagCount = raw.toIntOrNull()?.toString() ?: "")
    }

    fun setFlangeCount(value: String) {
        val raw = value.filter { it.isDigit() }.take(4)
        _uiState.value = _uiState.value.copy(flangeCount = raw.toIntOrNull()?.toString() ?: "")
    }

    fun nextStep() {
        val state = _uiState.value
        val next =
            when (state.step) {
                OnboardingStep.APPLIANCE_TYPE ->
                    if (state.applianceType == ApplianceType.ONE_PIECE) {
                        OnboardingStep.COUNTS
                    } else {
                        OnboardingStep.SUPPLIES
                    }
                OnboardingStep.SUPPLIES -> OnboardingStep.COUNTS
                OnboardingStep.COUNTS -> OnboardingStep.QR_EXPLAINER
                OnboardingStep.QR_EXPLAINER -> return
            }
        _uiState.value = state.copy(step = next)
    }

    fun prevStep() {
        val state = _uiState.value
        val prev =
            when (state.step) {
                OnboardingStep.APPLIANCE_TYPE -> return
                OnboardingStep.SUPPLIES -> OnboardingStep.APPLIANCE_TYPE
                OnboardingStep.COUNTS ->
                    if (state.applianceType == ApplianceType.ONE_PIECE) {
                        OnboardingStep.APPLIANCE_TYPE
                    } else {
                        OnboardingStep.SUPPLIES
                    }
                OnboardingStep.QR_EXPLAINER -> OnboardingStep.COUNTS
            }
        _uiState.value = state.copy(step = prev)
    }

    fun finish() {
        viewModelScope.launch {
            val state = _uiState.value
            val supplies = supplyRepository.observeSupplies().first()

            supplies.forEach { supply ->
                val newCount =
                    when {
                        supply.kind == SupplyKind.BAG && SupplyKind.BAG in state.selectedKinds ->
                            state.bagCount.toIntOrNull() ?: 0
                        supply.kind == SupplyKind.FLANGE && SupplyKind.FLANGE in state.selectedKinds ->
                            state.flangeCount.toIntOrNull() ?: 0
                        else -> return@forEach
                    }
                supplyRepository.setOnHand(supply.id, newCount)
            }

            settingsRepository.setApplianceType(state.applianceType)
            settingsRepository.setOnboardingDone(true)
        }
    }

    fun skip() {
        viewModelScope.launch {
            settingsRepository.setOnboardingDone(true)
        }
    }
}
