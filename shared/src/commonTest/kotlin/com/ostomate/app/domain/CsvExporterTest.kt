package com.ostomate.app.domain

import com.ostomate.app.data.db.ChangeEventEntity
import com.ostomate.app.data.db.ChangeEventWithSupply
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CsvExporterTest {
    private fun event(
        supplyTypeId: Long,
        supplyName: String,
        supplyKind: SupplyKind,
        timestampMillis: Long,
        editedAtMillis: Long? = null,
        note: String? = null,
    ) = ChangeEventWithSupply(
        event =
            ChangeEventEntity(
                id = 1,
                supplyTypeId = supplyTypeId,
                timestampMillis = timestampMillis,
                note = note,
                createdAtMillis = timestampMillis,
                editedAtMillis = editedAtMillis,
            ),
        supplyName = supplyName,
        supplyKind = supplyKind,
    )

    @Test
    fun `empty list produces header-only output`() {
        val csv = CsvExporter.buildCsv(emptyList())
        val lines = csv.trim().lines()
        assertEquals(1, lines.size)
        assertEquals("supply_id,supply_name,supply_kind,timestamp_millis,edited_at_millis,note", lines[0])
    }

    @Test
    fun `single event produces header plus one data row`() {
        val csv = CsvExporter.buildCsv(listOf(event(1L, "Bag", SupplyKind.BAG, 1_700_000_000_000L)))
        assertEquals(2, csv.trim().lines().size)
    }

    @Test
    fun `data row encodes supply_id and name and kind and timestamp`() {
        val csv = CsvExporter.buildCsv(listOf(event(42L, "Coloplast Bag", SupplyKind.BAG, 1_700_000_000_000L)))
        val dataRow = csv.trim().lines()[1]
        assertTrue(dataRow.startsWith("42,Coloplast Bag,BAG,1700000000000"))
    }

    @Test
    fun `note is written as last column`() {
        val csv = CsvExporter.buildCsv(listOf(event(1L, "Bag", SupplyKind.BAG, 1_700_000_000_000L, note = "routine")))
        val dataRow = csv.trim().lines()[1]
        assertTrue(dataRow.endsWith(",routine"))
    }

    @Test
    fun `note with comma is quoted`() {
        val csv =
            CsvExporter.buildCsv(
                listOf(event(1L, "Bag", SupplyKind.BAG, 1_700_000_000_000L, note = "leak, replaced early")),
            )
        assertContains(csv, "\"leak, replaced early\"")
    }

    @Test
    fun `note with double-quote is escaped`() {
        val csv =
            CsvExporter.buildCsv(
                listOf(event(1L, "Bag", SupplyKind.BAG, 1_700_000_000_000L, note = "said \"ouch\"")),
            )
        assertContains(csv, "\"said \"\"ouch\"\"\"")
    }

    @Test
    fun `editedAtMillis column is empty when null`() {
        val csv =
            CsvExporter.buildCsv(
                listOf(event(1L, "Bag", SupplyKind.BAG, 1_700_000_000_000L, editedAtMillis = null)),
            )
        val dataRow = csv.trim().lines()[1]
        val parts = dataRow.split(",")
        assertEquals("", parts[4])
    }

    @Test
    fun `editedAtMillis column is present when set`() {
        val csv =
            CsvExporter.buildCsv(
                listOf(event(1L, "Bag", SupplyKind.BAG, 1_700_000_000_000L, editedAtMillis = 1_700_000_001_000L)),
            )
        assertContains(csv, "1700000001000")
    }

    @Test
    fun `events are sorted by timestamp ascending`() {
        val events =
            listOf(
                event(1L, "Bag", SupplyKind.BAG, 2_000L),
                event(2L, "Flange", SupplyKind.FLANGE, 1_000L),
            )
        val lines = CsvExporter.buildCsv(events).trim().lines()
        assertTrue(lines[1].contains(",1000,"), "Earlier timestamp should appear first")
        assertTrue(lines[2].contains(",2000,"), "Later timestamp should appear second")
    }
}
