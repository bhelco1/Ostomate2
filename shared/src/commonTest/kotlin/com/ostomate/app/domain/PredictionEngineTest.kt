package com.ostomate.app.domain

import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PredictionEngineTest {
    // --- averageDaysBetween: edge cases ---

    @Test
    fun `averageDaysBetween returns null with no events`() {
        assertNull(PredictionEngine.averageDaysBetween(emptyList()))
    }

    @Test
    fun `averageDaysBetween returns null with one event`() {
        assertNull(PredictionEngine.averageDaysBetween(listOf(1_000_000L)))
    }

    @Test
    fun `averageDaysBetween returns 3 days for two events 3 days apart`() {
        val threeDaysMillis = 3L * 24 * 60 * 60 * 1_000
        val now = 1_000_000_000L
        val result = PredictionEngine.averageDaysBetween(listOf(now, now - threeDaysMillis))
        assertEquals(3.0, result!!, 1e-9)
    }

    @Test
    fun `averageDaysBetween averages multiple intervals correctly`() {
        val dayMillis = 24L * 60 * 60 * 1_000
        val now = 100L * dayMillis
        // Intervals: 2 days, 4 days → average = 3 days
        val ts = listOf(now, now - 2 * dayMillis, now - 6 * dayMillis)
        assertEquals(3.0, PredictionEngine.averageDaysBetween(ts)!!, 1e-9)
    }

    @Test
    fun `averageDaysBetween respects sampleSize`() {
        val dayMillis = 24L * 60 * 60 * 1_000
        // 5 events each 2 days apart; sampleSize=2 should only see the top-2 interval
        val ts = (0L..4L).map { (4 - it) * 2 * dayMillis } // [8d, 6d, 4d, 2d, 0d] millis
        assertEquals(2.0, PredictionEngine.averageDaysBetween(ts, sampleSize = 2)!!, 1e-9)
    }

    @Test
    fun `averageDaysBetween coerces zero-gap bursts to minimum`() {
        // Two events at the same timestamp → raw avg = 0 → must coerce to minimum
        val result = PredictionEngine.averageDaysBetween(listOf(1000L, 1000L))
        assertNotNull(result)
        assertTrue(result > 0.0, "Expected positive, got $result")
    }

    // --- averageDaysBetween: property tests ---

    @Test
    fun `averageDaysBetween result is always positive when not null`() =
        runTest {
            val dayMillis = 24L * 60 * 60 * 1_000
            checkAll(Arb.list(Arb.long(0L..1000L * dayMillis), 2..20)) { ts ->
                val result = PredictionEngine.averageDaysBetween(ts)
                if (result != null) assertTrue(result > 0.0, "Expected positive, got $result")
            }
        }

    @Test
    fun `averageDaysBetween is order-independent`() =
        runTest {
            val dayMillis = 24L * 60 * 60 * 1_000
            // Generate a sorted list then shuffle it — the function must sort internally
            checkAll(Arb.list(Arb.long(0L..1000L * dayMillis), 2..10)) { ts ->
                val sorted = ts.sortedDescending()
                val shuffled = ts.shuffled()
                val fromSorted = PredictionEngine.averageDaysBetween(sorted)
                val fromShuffled = PredictionEngine.averageDaysBetween(shuffled)
                if (fromSorted != null && fromShuffled != null) {
                    assertEquals(
                        fromSorted,
                        fromShuffled,
                        1e-9,
                        "Order changed result: sorted=$fromSorted shuffled=$fromShuffled",
                    )
                }
            }
        }

    // --- daysRemaining: edge cases ---

    @Test
    fun `daysRemaining returns 0 when onHand is 0`() {
        assertEquals(0.0, PredictionEngine.daysRemaining(0, 3.5))
        assertEquals(0.0, PredictionEngine.daysRemaining(0, null))
    }

    @Test
    fun `daysRemaining returns null when no usage history`() {
        assertNull(PredictionEngine.daysRemaining(5, null))
    }

    @Test
    fun `daysRemaining multiplies onHand by average`() {
        assertEquals(12.0, PredictionEngine.daysRemaining(4, 3.0)!!, 1e-9)
    }

    // --- daysRemaining: property tests ---

    @Test
    fun `daysRemaining is never negative`() =
        runTest {
            checkAll(Arb.int(0..10_000), Arb.long(1L..10_000L)) { onHand, avgMillis ->
                val avgDays = avgMillis / (24.0 * 60 * 60 * 1_000)
                val result = PredictionEngine.daysRemaining(onHand, avgDays)
                if (result != null) assertTrue(result >= 0.0, "Expected non-negative, got $result")
            }
        }

    @Test
    fun `daysRemaining is 0 when onHand is 0 for any avgDays`() =
        runTest {
            checkAll(Arb.long(1L..100_000L)) { avgMillis ->
                val avgDays = avgMillis / (24.0 * 60 * 60 * 1_000)
                assertEquals(0.0, PredictionEngine.daysRemaining(0, avgDays))
            }
        }
}
