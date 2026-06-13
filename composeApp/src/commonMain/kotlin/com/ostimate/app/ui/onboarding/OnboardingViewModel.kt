package com.ostimate.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ostimate.app.data.SupplyRepository
import com.ostimate.app.data.db.SupplyTypeEntity
import com.ostimate.app.data.settings.SettingsRepository
import com.ostimate.app.domain.SupplyKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class OnboardingStep { SUPPLIES, COUNTS, QR_EXPLAINER }

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.SUPPLIES,
    val selectedKinds: Set<SupplyKind> = setOf(SupplyKind.BAG, SupplyKind.FLANGE),
    val bagCount: String = "0",
    val flangeCount: String = "0",
)

class OnboardingViewModel(
    private val supplyRepository: SupplyRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun toggleKind(kind: SupplyKind) {
        val current = _uiState.value.selectedKinds
        _uiState.value =
            _uiState.value.copy(
                selectedKinds = if (kind in current) current - kind else current + kind,
            )
    }

    fun setBagCount(value: String) {
        _uiState.value = _uiState.value.copy(bagCount = value.filter { it.isDigit() }.take(4))
    }

    fun setFlangeCount(value: String) {
        _uiState.value = _uiState.value.copy(flangeCount = value.filter { it.isDigit() }.take(4))
    }

    fun nextStep() {
        val next =
            when (_uiState.value.step) {
                OnboardingStep.SUPPLIES -> OnboardingStep.COUNTS
                OnboardingStep.COUNTS -> OnboardingStep.QR_EXPLAINER
                OnboardingStep.QR_EXPLAINER -> return
            }
        _uiState.value = _uiState.value.copy(step = next)
    }

    fun prevStep() {
        val prev =
            when (_uiState.value.step) {
                OnboardingStep.SUPPLIES -> return
                OnboardingStep.COUNTS -> OnboardingStep.SUPPLIES
                OnboardingStep.QR_EXPLAINER -> OnboardingStep.COUNTS
            }
        _uiState.value = _uiState.value.copy(step = prev)
    }

    fun finish() {
        viewModelScope.launch {
            val state = _uiState.value
            val supplies = supplyRepository.observeSupplies().first()

            // Update on-hand counts for supplies the user has selected
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

            settingsRepository.setOnboardingDone(true)
        }
    }

    fun skip() {
        viewModelScope.launch {
            settingsRepository.setOnboardingDone(true)
        }
    }
}
