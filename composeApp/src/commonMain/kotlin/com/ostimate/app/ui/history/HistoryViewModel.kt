package com.ostimate.app.ui.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ostimate.app.data.ChangeEventRepository
import com.ostimate.app.data.db.ChangeEventEntity
import com.ostimate.app.data.db.ChangeEventWithSupply
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HistoryUiState(
    val events: List<ChangeEventWithSupply> = emptyList(),
    val title: String = "History",
    val pendingUndo: ChangeEventEntity? = null,
)

class HistoryViewModel(
    private val eventRepository: ChangeEventRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    // Populated by nav-compose from HistoryDestination(supplyId). -1 means "all events".
    private val supplyId: Long = savedStateHandle.get<Long>("supplyId") ?: -1L

    private val _pendingUndo = MutableStateFlow<ChangeEventEntity?>(null)

    private val eventsFlow =
        if (supplyId < 0) {
            eventRepository.observeEvents()
        } else {
            eventRepository.observeBySupply(supplyId)
        }

    val uiState: StateFlow<HistoryUiState> =
        combine(eventsFlow, _pendingUndo) { events, pendingUndo ->
            val title =
                when {
                    supplyId < 0 -> "History"
                    events.isNotEmpty() -> "${events.first().supplyName} history"
                    else -> "History"
                }
            HistoryUiState(events = events, title = title, pendingUndo = pendingUndo)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

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
