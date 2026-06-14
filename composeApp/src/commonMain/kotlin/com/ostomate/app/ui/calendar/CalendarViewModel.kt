@file:Suppress("DEPRECATION") // kotlinx-datetime 0.6.x deprecates monthNumber/dayOfMonth but replacements don't compile

package com.ostimate.app.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ostimate.app.data.ChangeEventRepository
import com.ostimate.app.data.SupplyRepository
import com.ostimate.app.data.db.ChangeEventEntity
import com.ostimate.app.data.db.ChangeEventWithSupply
import com.ostimate.app.domain.CalendarAggregator
import com.ostimate.app.domain.EventPoint
import com.ostimate.app.domain.SupplyKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

data class SupplyPill(
    val supplyId: Long,
    val supplyName: String,
    val kind: SupplyKind,
    val count: Int,
)

data class CalendarDay(
    val date: LocalDate,
    val isCurrentMonth: Boolean,
    val isToday: Boolean,
    val pills: List<SupplyPill>,
)

private val MONTH_NAMES =
    arrayOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
    )

data class CalendarUiState(
    val monthLabel: String = "",
    val days: List<CalendarDay> = emptyList(),
    val selectedDayEvents: List<ChangeEventWithSupply>? = null,
    val selectedDayLabel: String = "",
    val selectedDate: LocalDate? = null,
    val pendingUndo: ChangeEventEntity? = null,
    val supplies: List<com.ostimate.app.data.db.SupplyTypeEntity> = emptyList(),
)

class CalendarViewModel(
    private val eventRepository: ChangeEventRepository,
    supplyRepository: SupplyRepository,
) : ViewModel() {
    private val tz = TimeZone.currentSystemDefault()
    private val today = Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds()).toLocalDateTime(tz).date

    private val _currentMonth = MutableStateFlow(LocalDate(today.year, today.monthNumber, 1))
    private val _selectedDate = MutableStateFlow<LocalDate?>(null)
    private val _pendingUndo = MutableStateFlow<ChangeEventEntity?>(null)

    val uiState: StateFlow<CalendarUiState> =
        combine(
            _currentMonth,
            supplyRepository.observeSupplies(),
            eventRepository.observeEvents(),
            _selectedDate,
            _pendingUndo,
        ) { month, supplies, events, selectedDate, pendingUndo ->
            val supplyMap = supplies.associateBy { it.id }
            val eventPoints = events.map { EventPoint(it.event.timestampMillis, it.event.supplyTypeId) }
            val countsByDay = CalendarAggregator.countsByDay(eventPoints, month.year, month.monthNumber, tz)

            val firstDay = LocalDate(month.year, month.monthNumber, 1)
            val lastDay = firstDay.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY)
            val startPadding = (firstDay.dayOfWeek.ordinal + 1) % 7 // 0=Sun (US convention)
            val totalCells = ((startPadding + lastDay.dayOfMonth + 6) / 7) * 7

            val days =
                (0 until totalCells).map { idx ->
                    val date = firstDay.minus(startPadding - idx, DateTimeUnit.DAY)
                    val isCurrentMonth = date.monthNumber == month.monthNumber && date.year == month.year
                    val pills =
                        if (isCurrentMonth) {
                            (countsByDay[date.dayOfMonth] ?: emptyMap())
                                .entries
                                .mapNotNull { (supplyId, count) ->
                                    val supply = supplyMap[supplyId] ?: return@mapNotNull null
                                    SupplyPill(supplyId, supply.name, supply.kind, count)
                                }
                                .sortedBy { it.supplyId }
                        } else {
                            emptyList()
                        }
                    CalendarDay(date = date, isCurrentMonth = isCurrentMonth, isToday = date == today, pills = pills)
                }

            val selectedDayEvents =
                selectedDate?.let { sel ->
                    events.filter { row ->
                        Instant
                            .fromEpochMilliseconds(row.event.timestampMillis)
                            .toLocalDateTime(tz)
                            .date == sel
                    }
                }

            val selectedDayLabel =
                selectedDate?.let {
                    "${MONTH_NAMES[it.monthNumber - 1]} ${it.dayOfMonth}, ${it.year}"
                } ?: ""

            CalendarUiState(
                monthLabel = "${MONTH_NAMES[month.monthNumber - 1]} ${month.year}",
                days = days,
                selectedDayEvents = selectedDayEvents,
                selectedDayLabel = selectedDayLabel,
                selectedDate = selectedDate,
                pendingUndo = pendingUndo,
                supplies = supplies,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarUiState())

    fun prevMonth() {
        _currentMonth.value = _currentMonth.value.minus(1, DateTimeUnit.MONTH)
    }

    fun nextMonth() {
        _currentMonth.value = _currentMonth.value.plus(1, DateTimeUnit.MONTH)
    }

    fun selectDay(date: LocalDate) {
        _selectedDate.value = date
    }

    fun dismissSheet() {
        _selectedDate.value = null
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

    fun addEventForDate(supplyId: Long, date: LocalDate) {
        viewModelScope.launch {
            val noon =
                LocalDateTime(date.year, date.monthNumber, date.dayOfMonth, 12, 0)
                    .toInstant(tz)
                    .toEpochMilliseconds()
            eventRepository.logChangeAt(supplyId, noon)
        }
    }
}
