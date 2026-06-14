package com.ostimate.app.domain

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

data class EventPoint(val timestampMillis: Long, val supplyTypeId: Long)

object CalendarAggregator {
    /**
     * Groups [events] that fall in [year]/[month] (month is 1-based) by day of month,
     * then by supply type ID. Events outside the target month are excluded.
     * Returns a map of dayOfMonth → (supplyTypeId → count).
     */
    fun countsByDay(
        events: List<EventPoint>,
        year: Int,
        month: Int,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): Map<Int, Map<Long, Int>> {
        val result = mutableMapOf<Int, MutableMap<Long, Int>>()
        for (event in events) {
            val local =
                Instant.fromEpochMilliseconds(event.timestampMillis)
                    .toLocalDateTime(timeZone)
            if (local.year != year || local.monthNumber != month) continue
            val dayMap = result.getOrPut(local.dayOfMonth) { mutableMapOf() }
            dayMap[event.supplyTypeId] = (dayMap[event.supplyTypeId] ?: 0) + 1
        }
        return result
    }
}
