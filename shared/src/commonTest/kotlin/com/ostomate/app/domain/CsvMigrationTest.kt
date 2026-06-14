@file:Suppress("DEPRECATION")

package com.ostomate.app.domain

import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Journey #6: import v1 CSV fixture → counts and predictions match v1.
 * Fixture: 36 BAG events (~1/day) + 7 FLANGE events (~1/5 days).
 */
class CsvMigrationTest {
    private val bagStart = 1_698_796_800_000L // 2023-11-01T00:00:00Z
    private val flangeStart = 1_698_796_800_000L

    private val v1Fixture =
        buildString {
            appendLine("id,type,timestamp_iso8601")
            repeat(36) { i ->
                val millis = bagStart + i * 86_400_000L
                appendLine("${i + 1},BAG,${Instant.fromEpochMilliseconds(millis)}")
            }
            repeat(7) { i ->
                val millis = flangeStart + i * 432_000_000L
                appendLine("${37 + i},FLANGE,${Instant.fromEpochMilliseconds(millis)}")
            }
        }

    @Test
    fun `fixture parses without errors`() {
        val result = CsvV1Importer.parse(v1Fixture)
        assertEquals(0, result.parseErrors, "Expected 0 parse errors")
    }

    @Test
    fun `fixture contains expected BAG and FLANGE counts`() {
        val result = CsvV1Importer.parse(v1Fixture)
        val bags = result.rows.count { it.kind == "BAG" }
        val flanges = result.rows.count { it.kind == "FLANGE" }
        assertEquals(36, bags)
        assertEquals(7, flanges)
    }

    @Test
    fun `BAG average interval is approximately 1 day`() {
        val result = CsvV1Importer.parse(v1Fixture)
        val bagTimestamps = result.rows.filter { it.kind == "BAG" }.map { it.timestampMillis }
        val avgDays = PredictionEngine.averageDaysBetween(bagTimestamps)
        assertTrue(avgDays != null, "Expected non-null average for BAG")
        assertTrue(avgDays!! in 0.9..1.1, "Expected avg ~1 day, got $avgDays")
    }

    @Test
    fun `FLANGE average interval is approximately 5 days`() {
        val result = CsvV1Importer.parse(v1Fixture)
        val flangeTimestamps = result.rows.filter { it.kind == "FLANGE" }.map { it.timestampMillis }
        val avgDays = PredictionEngine.averageDaysBetween(flangeTimestamps)
        assertTrue(avgDays != null, "Expected non-null average for FLANGE")
        assertTrue(avgDays!! in 4.9..5.1, "Expected avg ~5 days, got $avgDays")
    }

    @Test
    fun `total event count matches fixture`() {
        val result = CsvV1Importer.parse(v1Fixture)
        assertEquals(43, result.rows.size)
    }
}
