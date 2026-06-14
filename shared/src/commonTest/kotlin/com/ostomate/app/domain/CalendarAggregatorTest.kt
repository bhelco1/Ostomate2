package com.ostimate.app.domain

import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CalendarAggregatorTest {
    private val utc = TimeZone.UTC

    // --- basic correctness ---

    @Test
    fun `empty list returns empty map`() {
        assertEquals(emptyMap(), CalendarAggregator.countsByDay(emptyList(), 2024, 1, utc))
    }

    @Test
    fun `event on Jan 15 appears in day 15 bucket`() {
        val jan15 = LocalDate(2024, Month.JANUARY, 15).atStartOfDayIn(utc).toEpochMilliseconds()
        val events = listOf(EventPoint(jan15, supplyTypeId = 1L))
        val result = CalendarAggregator.countsByDay(events, 2024, 1, utc)
        assertEquals(mapOf(1L to 1), result[15])
    }

    @Test
    fun `events in different months are excluded`() {
        val jan15 = LocalDate(2024, Month.JANUARY, 15).atStartOfDayIn(utc).toEpochMilliseconds()
        val feb10 = LocalDate(2024, Month.FEBRUARY, 10).atStartOfDayIn(utc).toEpochMilliseconds()
        val events = listOf(EventPoint(jan15, 1L), EventPoint(feb10, 1L))
        val result = CalendarAggregator.countsByDay(events, 2024, 1, utc)
        val total = result.values.sumOf { it.values.sum() }
        assertEquals(1, total)
    }

    @Test
    fun `multiple supply types accumulate independently per day`() {
        val ts = LocalDate(2024, Month.MARCH, 5).atStartOfDayIn(utc).toEpochMilliseconds()
        val events =
            listOf(
                EventPoint(ts, supplyTypeId = 1L),
                EventPoint(ts, supplyTypeId = 1L),
                EventPoint(ts, supplyTypeId = 2L),
            )
        val result = CalendarAggregator.countsByDay(events, 2024, 3, utc)
        assertEquals(2, result[5]!![1L])
        assertEquals(1, result[5]!![2L])
    }

    @Test
    fun `leap year Feb 29 is counted correctly`() {
        val feb29 = LocalDate(2024, Month.FEBRUARY, 29).atStartOfDayIn(utc).toEpochMilliseconds()
        val events = listOf(EventPoint(feb29, 1L))
        val result = CalendarAggregator.countsByDay(events, 2024, 2, utc)
        assertEquals(mapOf(1L to 1), result[29])
    }

    // --- property tests ---

    @Test
    fun `total count equals events falling in that year-month`() =
        runTest {
            // year, month, random timestamps (epoch ms up to ~2100), supplyTypeIds
            checkAll(
                Arb.int(2020..2030),
                Arb.int(1..12),
                Arb.list(Arb.long(0L..4_102_444_800_000L), 0..50),
                Arb.list(Arb.long(1L..5L), 0..50),
            ) { year, month, timestamps, typeIds ->
                if (timestamps.isEmpty()) return@checkAll
                val paired = timestamps.zip(typeIds.take(timestamps.size).ifEmpty { List(timestamps.size) { 1L } })
                val events = paired.map { (ts, id) -> EventPoint(ts, id) }

                val result = CalendarAggregator.countsByDay(events, year, month, utc)
                val resultTotal = result.values.sumOf { it.values.sum() }

                // Count how many events actually fall in year/month
                val expectedTotal =
                    events.count { ep ->
                        val local =
                            Instant.fromEpochMilliseconds(ep.timestampMillis)
                                .toLocalDateTime(utc)
                        local.year == year && local.monthNumber == month
                    }
                assertEquals(expectedTotal, resultTotal)
            }
        }

    @Test
    fun `all day-of-month keys are within valid range for the month`() =
        runTest {
            checkAll(
                Arb.int(2020..2030),
                Arb.int(1..12),
                Arb.list(Arb.long(0L..4_102_444_800_000L), 1..30),
            ) { year, month, timestamps ->
                val events = timestamps.mapIndexed { i, ts -> EventPoint(ts, (i % 3 + 1).toLong()) }
                val result = CalendarAggregator.countsByDay(events, year, month, utc)
                result.keys.forEach { day ->
                    assertTrue(day in 1..31, "Day $day out of range for $year-$month")
                }
            }
        }
}
