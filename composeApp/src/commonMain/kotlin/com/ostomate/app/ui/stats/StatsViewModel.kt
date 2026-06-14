@file:Suppress("DEPRECATION") // kotlinx-datetime 0.6.x deprecation — monthNumber/dayOfMonth

package com.ostomate.app.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ostomate.app.data.ChangeEventRepository
import com.ostomate.app.data.SupplyRepository
import com.ostomate.app.domain.PredictionEngine
import com.ostomate.app.domain.SupplyKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlin.time.Clock

enum class StatsPeriod(val label: String, val days: Long) {
    WEEK("Week", 7),
    MONTH("Month", 30),
    YEAR("Year", 365),
}

data class SupplyStats(
    val supplyId: Long,
    val supplyName: String,
    val kind: SupplyKind,
    val countInPeriod: Int,
    val avgDaysBetween: Double?,
    /** Timestamps within the selected period, sorted ASC — used to draw the sparkline. */
    val periodTimestamps: List<Long> = emptyList(),
)

data class StatsUiState(
    val period: StatsPeriod = StatsPeriod.MONTH,
    val rows: List<SupplyStats> = emptyList(),
    /** Plain-language summary, e.g. "You change bags every 2.1 days on average." */
    val summaryLine: String? = null,
)

class StatsViewModel(
    private val eventRepository: ChangeEventRepository,
    supplyRepository: SupplyRepository,
) : ViewModel() {
    private val _period = MutableStateFlow(StatsPeriod.MONTH)

    val uiState: StateFlow<StatsUiState> =
        combine(
            _period,
            supplyRepository.observeSupplies(),
            eventRepository.observeEvents(),
        ) { period, supplies, allEvents ->
            val cutoff = Clock.System.now().toEpochMilliseconds() - period.days * 86_400_000L
            val supplyMap = supplies.associateBy { it.id }

            val rows =
                supplies.mapNotNull { supply ->
                    val supplyEvents = allEvents.filter { it.event.supplyTypeId == supply.id }
                    val inPeriod = supplyEvents.filter { it.event.timestampMillis >= cutoff }
                    if (supplyEvents.isEmpty()) return@mapNotNull null
                    SupplyStats(
                        supplyId = supply.id,
                        supplyName = supply.name,
                        kind = supply.kind,
                        countInPeriod = inPeriod.size,
                        avgDaysBetween =
                            PredictionEngine.averageDaysBetween(
                                supplyEvents.map { it.event.timestampMillis },
                            ),
                        periodTimestamps = inPeriod.map { it.event.timestampMillis }.reversed(),
                    )
                }.sortedByDescending { it.countInPeriod }

            val summary = buildSummaryLine(rows)
            StatsUiState(period = period, rows = rows, summaryLine = summary)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    fun selectPeriod(period: StatsPeriod) {
        _period.value = period
    }
}

private fun buildSummaryLine(rows: List<SupplyStats>): String? {
    val withAvg = rows.filter { it.avgDaysBetween != null }
    if (withAvg.isEmpty()) return null
    return withAvg.joinToString(" and ") { row ->
        val avg = row.avgDaysBetween!!
        val formatted = if (avg == avg.toLong().toDouble()) "${avg.toLong()}" else "%.1f".format(avg)
        "${row.supplyName.lowercase()} every $formatted days"
    }.let { "You change $it on average." }
}
