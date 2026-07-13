package com.ostomate.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ostomate.app.data.ChangeEventRepository
import com.ostomate.app.data.SupplyRepository
import com.ostomate.app.data.db.ChangeEventEntity
import com.ostomate.app.data.db.SupplyTypeEntity
import com.ostomate.app.domain.NotificationScheduler
import com.ostomate.app.domain.PredictionEngine
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

data class LogConfirmation(val supplyId: Long, val supplyName: String, val minutesAgo: Int)

data class HomeUiState(
    val supplies: List<SupplyRow> = emptyList(),
    val pendingUndo: ChangeEventEntity? = null,
    val undoSupplyName: String = "",
    val undoOnHand: Int? = null,
    val pendingConfirmation: LogConfirmation? = null,
)

class HomeViewModel(
    private val eventRepository: ChangeEventRepository,
    private val supplyRepository: SupplyRepository,
    private val notificationScheduler: NotificationScheduler,
) : ViewModel() {
    private data class PendingUndo(
        val event: ChangeEventEntity,
        val supplyName: String,
        val onHandAfter: Int,
        // Captured so undo restores the exact prior count. The log's decrement clamps at zero,
        // so its inverse (+1) would invent a unit whenever the count was already 0.
        val onHandBefore: Int,
    )

    private data class PendingConfirm(val supply: SupplyTypeEntity, val minutesAgo: Int)

    private val _pendingUndo = MutableStateFlow<PendingUndo?>(null)
    private val _pendingConfirmation = MutableStateFlow<PendingConfirm?>(null)

    val uiState: StateFlow<HomeUiState> =
        combine(
            supplyRepository.observeSupplies(),
            eventRepository.observeEvents(),
            _pendingUndo,
            _pendingConfirmation,
        ) { supplies, events, pendingUndo, pendingConfirmation ->
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
                pendingUndo = pendingUndo?.event,
                undoSupplyName = pendingUndo?.supplyName ?: "",
                undoOnHand = pendingUndo?.onHandAfter,
                pendingConfirmation =
                    pendingConfirmation?.let {
                        LogConfirmation(it.supply.id, it.supply.name, it.minutesAgo)
                    },
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
            val minutesAgo = eventRepository.wasLoggedWithinWindow(supply.id)
            if (minutesAgo != null) {
                _pendingConfirmation.value = PendingConfirm(supply, minutesAgo)
                return@launch
            }
            recordLog(supply)
        }
    }

    private suspend fun recordLog(supply: SupplyTypeEntity) {
        val event = eventRepository.logChange(supply.id)
        _pendingUndo.value =
            PendingUndo(
                event = event,
                supplyName = supply.name,
                onHandAfter = maxOf(0, supply.onHand - 1),
                onHandBefore = supply.onHand,
            )
    }

    fun confirmPendingLog() {
        val pending = _pendingConfirmation.value ?: return
        _pendingConfirmation.value = null
        viewModelScope.launch { recordLog(pending.supply) }
    }

    fun dismissPendingConfirmation() {
        _pendingConfirmation.value = null
    }

    fun undoLog() {
        viewModelScope.launch {
            val pending = _pendingUndo.value ?: return@launch
            eventRepository.undoLog(pending.event, pending.onHandBefore)
            _pendingUndo.value = null
        }
    }

    fun clearUndo() {
        _pendingUndo.value = null
    }
}
