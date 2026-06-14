package com.ostomate.app.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class CsvImporterTest {
    private val sampleV1Csv =
        """
        id,type,timestamp_iso8601
        1,BAG,2023-11-14T22:13:20Z
        2,FLANGE,2023-11-28T10:00:00Z
        3,BAG,2023-12-12T08:30:00Z
        """.trimIndent()

    @Test
    fun `parse empty csv returns empty result`() {
        val result = CsvV1Importer.parse("id,type,timestamp_iso8601")
        assertEquals(0, result.rows.size)
        assertEquals(0, result.parseErrors)
    }

    @Test
    fun `parse three rows returns correct count`() {
        val result = CsvV1Importer.parse(sampleV1Csv)
        assertEquals(3, result.rows.size)
        assertEquals(0, result.parseErrors)
    }

    @Test
    fun `BAG row is parsed correctly`() {
        val result = CsvV1Importer.parse(sampleV1Csv)
        val row = result.rows.first { it.kind == "BAG" }
        assertEquals(1L, row.originalId)
        assertEquals(1_700_000_000_000L, row.timestampMillis)
    }

    @Test
    fun `FLANGE row is parsed correctly`() {
        val result = CsvV1Importer.parse(sampleV1Csv)
        val row = result.rows.first { it.kind == "FLANGE" }
        assertEquals(2L, row.originalId)
        assertEquals("FLANGE", row.kind)
    }

    @Test
    fun `malformed row increments parse error count`() {
        val csv = "id,type,timestamp_iso8601\nbadline"
        val result = CsvV1Importer.parse(csv)
        assertEquals(0, result.rows.size)
        assertEquals(1, result.parseErrors)
    }

    @Test
    fun `bad timestamp increments parse error count`() {
        val csv = "id,type,timestamp_iso8601\n1,BAG,not-a-timestamp"
        val result = CsvV1Importer.parse(csv)
        assertEquals(0, result.rows.size)
        assertEquals(1, result.parseErrors)
    }

    @Test
    fun `blank lines are silently skipped`() {
        val csv = "id,type,timestamp_iso8601\n\n1,BAG,2023-11-14T22:13:20Z\n\n"
        val result = CsvV1Importer.parse(csv)
        assertEquals(1, result.rows.size)
        assertEquals(0, result.parseErrors)
    }

    @Test
    fun `timestamp order is preserved`() {
        val result = CsvV1Importer.parse(sampleV1Csv)
        val millis = result.rows.map { it.timestampMillis }
        assertEquals(millis.sorted(), millis)
    }
}
