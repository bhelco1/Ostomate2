package com.ostimate.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ostimate.app.data.ChangeEventRepository
import com.ostimate.app.data.SupplyRepository
import com.ostimate.app.data.db.ChangeEventEntity
import com.ostimate.app.data.db.SupplyTypeEntity
import com.ostimate.app.domain.NotificationScheduler
import com.ostimate.app.domain.PredictionEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
)

class HomeViewModel(
    private val eventRepository: ChangeEventRepository,
    private val supplyRepository: SupplyRepository,
    private val notificationScheduler: NotificationScheduler,
) : ViewModel() {
    private val _pendingUndo = MutableStateFlow<Pair<ChangeEventEntity, String>?>(null)

    val uiState: StateFlow<HomeUiState> =
        combine(
            supplyRepository.observeSupplies(),
            eventRepository.observeEvents(),
            _pendingUndo,
        ) { supplies, events, pendingUndo ->
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
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
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
}
