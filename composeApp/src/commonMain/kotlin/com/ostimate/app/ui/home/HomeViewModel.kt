package com.ostimate.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ostimate.app.data.ChangeEventRepository
import com.ostimate.app.data.SupplyRepository
import com.ostimate.app.data.db.ChangeEventEntity
import com.ostimate.app.data.db.SupplyTypeEntity
import com.ostimate.app.data.settings.SettingsRepository
import com.ostimate.app.domain.NotificationScheduler
import com.ostimate.app.domain.PredictionEngine
import com.ostimate.app.platform.BiometricAuthenticator
import com.ostimate.app.platform.BiometricResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SupplyRow(
    val supply: SupplyTypeEntity,
    val daysRemaining: Double?,
    val sampleCount: Int,
)

data class HomeUiState(
    val supplies: List<SupplyRow> = emptyList(),
    val pendingUndo: ChangeEventEntity? = null,
    val undoSupplyName: String = "",
    val editCountTarget: SupplyTypeEntity? = null,
)

class HomeViewModel(
    private val eventRepository: ChangeEventRepository,
    private val supplyRepository: SupplyRepository,
    private val notificationScheduler: NotificationScheduler,
    private val settingsRepository: SettingsRepository,
    private val biometricAuth: BiometricAuthenticator,
) : ViewModel() {
    private val _pendingUndo = MutableStateFlow<Pair<ChangeEventEntity, String>?>(null)
    private val _editCountTarget = MutableStateFlow<SupplyTypeEntity?>(null)

    val uiState: StateFlow<HomeUiState> =
        combine(
            supplyRepository.observeSupplies(),
            eventRepository.observeEvents(),
            _pendingUndo,
            _editCountTarget,
        ) { supplies, events, pendingUndo, editCountTarget ->
            val eventsBySupply = events.groupBy { it.event.supplyTypeId }
            val rows =
                supplies.map { supply ->
                    val timestamps =
                        eventsBySupply[supply.id]?.map { it.event.timestampMillis } ?: emptyList()
                    SupplyRow(
                        supply = supply,
                        daysRemaining = PredictionEngine.daysRemainingFromHistory(supply.onHand, timestamps),
                        sampleCount = minOf(timestamps.size, 10),
                    )
                }
            HomeUiState(
                supplies = rows,
                pendingUndo = pendingUndo?.first,
                undoSupplyName = pendingUndo?.second ?: "",
                editCountTarget = editCountTarget,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        // Notification scheduling is a side effect — kept separate from the UI state
        // transform so a Notifier exception can't silently kill the uiState flow.
        viewModelScope.launch {
            combine(
                supplyRepository.observeSupplies(),
                eventRepository.observeEvents(),
            ) { supplies, events ->
                val eventsBySupply = events.groupBy { it.event.supplyTypeId }
                notificationScheduler.reschedule(supplies, eventsBySupply)
            }.collect {}
        }
    }

    fun logChange(supply: SupplyTypeEntity) {
        viewModelScope.launch {
            val event = eventRepository.logChange(supply.id)
            _pendingUndo.value = Pair(event, supply.name)
        }
    }

    fun undoLog() {
        viewModelScope.launch {
            val pending = _pendingUndo.value ?: return@launch
            eventRepository.delete(pending.first)
            _pendingUndo.value = null
        }
    }

    fun clearUndo() {
        _pendingUndo.value = null
    }

    /** Opens the edit-count dialog, gated by biometric if lockSettings is enabled. */
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
                    BiometricResult.Failed -> { /* denied — leave dialog closed */ }
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
