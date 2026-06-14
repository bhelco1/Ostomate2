package com.ostomate.app.data

import com.ostomate.app.data.db.ChangeEventDao
import com.ostomate.app.data.db.ChangeEventEntity
import com.ostomate.app.data.db.SupplyTypeDao
import com.ostomate.app.domain.CsvExporter
import com.ostomate.app.domain.CsvV1Importer
import com.ostomate.app.domain.SupplyKind
import com.ostomate.app.platform.currentTimeMillis
import kotlinx.coroutines.flow.first

data class ImportSummary(
    val inserted: Int,
    val skipped: Int,
    val parseErrors: Int,
    val oversized: Boolean = false,
)

private const val MAX_IMPORT_CHARS = 10_000_000 // ~10 MB; guards against oversized inputs

class BackupRepository(
    private val eventDao: ChangeEventDao,
    private val supplyTypeDao: SupplyTypeDao,
) {
    /** Returns the full event history as a v2 CSV string. */
    suspend fun exportCsv(): String {
        val events = eventDao.observeAllWithSupply().first()
        return CsvExporter.buildCsv(events)
    }

    /**
     * Imports a v1 Ostomate CSV file. Maps BAG→first BAG supply, FLANGE→first FLANGE supply.
     * Events whose timestamp already exists in the DB are skipped (idempotent).
     */
    suspend fun importV1Csv(csv: String): ImportSummary {
        if (csv.length > MAX_IMPORT_CHARS) {
            return ImportSummary(inserted = 0, skipped = 0, parseErrors = 0, oversized = true)
        }
        val result = CsvV1Importer.parse(csv)

        val bagSupply = supplyTypeDao.getByKind(SupplyKind.BAG)
        val flangeSupply = supplyTypeDao.getByKind(SupplyKind.FLANGE)

        var inserted = 0
        var skipped = 0
        val now = currentTimeMillis()

        for (row in result.rows) {
            val supply =
                when (row.kind) {
                    "BAG" -> bagSupply
                    "FLANGE" -> flangeSupply
                    else -> null
                } ?: continue

            val alreadyExists = eventDao.countByTimestamp(row.timestampMillis) > 0
            if (alreadyExists) {
                skipped++
                continue
            }

            eventDao.insert(
                ChangeEventEntity(
                    supplyTypeId = supply.id,
                    timestampMillis = row.timestampMillis,
                    createdAtMillis = now,
                ),
            )
            supplyTypeDao.decrementOnHand(supply.id)
            inserted++
        }

        return ImportSummary(
            inserted = inserted,
            skipped = skipped,
            parseErrors = result.parseErrors,
        )
    }
}
