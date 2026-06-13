package com.ostimate.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ostimate.app.data.ChangeEventRepository
import com.ostimate.app.data.SupplyRepository
import com.ostimate.app.data.db.ChangeEventEntity
import com.ostimate.app.data.db.ChangeEventWithSupply
import com.ostimate.app.data.db.SupplyTypeEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val supplies: List<SupplyTypeEntity> = emptyList(),
    val events: List<ChangeEventWithSupply> = emptyList(),
)

class HomeViewModel(
    private val eventRepository: ChangeEventRepository,
    supplyRepository: SupplyRepository,
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> =
        combine(supplyRepository.observeSupplies(), eventRepository.observeEvents()) { supplies, events ->
            HomeUiState(supplies = supplies, events = events)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun logChange(supply: SupplyTypeEntity) {
        viewModelScope.launch { eventRepository.logChange(supply.id) }
    }

    fun delete(event: ChangeEventEntity) {
        viewModelScope.launch { eventRepository.delete(event) }
    }
}
