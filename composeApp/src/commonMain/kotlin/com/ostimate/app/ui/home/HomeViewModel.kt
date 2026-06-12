package com.ostimate.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ostimate.app.data.ChangeEventRepository
import com.ostimate.app.data.db.ChangeEventEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(private val repository: ChangeEventRepository) : ViewModel() {

    val events: StateFlow<List<ChangeEventEntity>> =
        repository.observeEvents()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun logChange(supply: String) {
        viewModelScope.launch { repository.logChange(supply) }
    }

    fun delete(event: ChangeEventEntity) {
        viewModelScope.launch { repository.delete(event) }
    }
}
