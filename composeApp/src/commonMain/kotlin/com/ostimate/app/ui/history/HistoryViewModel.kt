package com.ostimate.app.ui.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ostimate.app.data.ChangeEventRepository
import com.ostimate.app.data.SupplyRepository
import com.ostimate.app.data.db.ChangeEventEntity
import com.ostimate.app.data.db.ChangeEventWithSupply
import com.ostimate.app.data.db.SupplyTypeEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HistoryUiState(
    val events: List<ChangeEventWithSupply> = emptyList(),
    val supplies: List<SupplyTypeEntity> = emptyList(),
    val filterSupplyId: Long = -1L,
    val title: String = "History",
    val pendingUndo: ChangeEventEntity? = null,
)

class HistoryViewModel(
    private val eventRepository: ChangeEventRepository,
    private val supplyRepository: SupplyRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    // Initial filter from nav — -1 means "all", any positive ID means filter to that supply.
    private val _filterSupplyId =
        MutableStateFlow(savedStateHandle.get<Long>("supplyId") ?: -1L)

    private val _pendingUndo = MutableStateFlow<ChangeEventEntity?>(null)

    val uiState: StateFlow<HistoryUiState> =
        combine(
            supplyRepository.observeSupplies(),
            eventRepository.observeEvents(),
            _filterSupplyId,
            _pendingUndo,
        ) { supplies, allEvents, filterSupplyId, pendingUndo ->
            val events =
                if (filterSupplyId < 0) allEvents else allEvents.filter { it.event.supplyTypeId == filterSupplyId }
            val title =
                when {
                    filterSupplyId < 0 -> "History"
                    else -> supplies.find { it.id == filterSupplyId }?.name?.let { "$it history" } ?: "History"
                }
            HistoryUiState(
                events = events,
                supplies = supplies,
                filterSupplyId = filterSupplyId,
                title = title,
                pendingUndo = pendingUndo,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun setFilter(supplyId: Long) {
        _filterSupplyId.value = supplyId
    }

    fun deleteEvent(event: ChangeEventEntity) {
        viewModelScope.launch {
            eventRepository.delete(event)
            _pendingUndo.value = event
        }
    }

    fun undoDelete() {
        viewModelScope.launch {
            val pending = _pendingUndo.value ?: return@launch
            eventRepository.reinsert(pending)
            _pendingUndo.value = null
        }
    }

    fun clearUndo() {
        _pendingUndo.value = null
    }

    fun updateEvent(event: ChangeEventEntity) {
        viewModelScope.launch { eventRepository.update(event) }
    }
}
