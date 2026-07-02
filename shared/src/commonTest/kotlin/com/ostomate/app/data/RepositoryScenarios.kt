package com.ostomate.app.data

import com.ostomate.app.data.db.OstomateDatabase
import com.ostomate.app.domain.SupplyKind
import kotlinx.coroutines.flow.first
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Database-agnostic assertions for the repository layer (2.5.4), shared between the
 * JVM-host and iOS-simulator runs like ChangeEventDaoScenarios. Backup round-trip
 * comes first: it is the data-loss-risk path.
 */
object RepositoryScenarios {
    // --- BackupRepository ---

    suspend fun backupRoundTripPreservesEvents(
        source: OstomateDatabase,
        target: OstomateDatabase,
    ) {
        val sourceEvents = ChangeEventRepository(source.changeEventDao(), source.supplyTypeDao())
        val bag = assertNotNull(source.supplyTypeDao().getByKind(SupplyKind.BAG))
        val flange = assertNotNull(source.supplyTypeDao().getByKind(SupplyKind.FLANGE))
        sourceEvents.logChangeAt(bag.id, 1_000)
        sourceEvents.logChangeAt(bag.id, 2_000)
        sourceEvents.logChangeAt(flange.id, 3_000)

        val csv = BackupRepository(source.changeEventDao(), source.supplyTypeDao()).exportCsv()
        val summary = BackupRepository(target.changeEventDao(), target.supplyTypeDao()).importCsv(csv)

        assertEquals(3, summary.inserted)
        assertEquals(0, summary.skipped)
        assertEquals(0, summary.parseErrors)

        val restored = target.changeEventDao().observeAllWithSupply().first()
        assertEquals(
            listOf(3_000L to SupplyKind.FLANGE, 2_000L to SupplyKind.BAG, 1_000L to SupplyKind.BAG),
            restored.map { it.event.timestampMillis to it.supplyKind },
        )
    }

    suspend fun reimportingOwnExportIsIdempotent(db: OstomateDatabase) {
        val events = ChangeEventRepository(db.changeEventDao(), db.supplyTypeDao())
        val backup = BackupRepository(db.changeEventDao(), db.supplyTypeDao())
        val bag = assertNotNull(db.supplyTypeDao().getByKind(SupplyKind.BAG))
        events.logChangeAt(bag.id, 1_000)
        events.logChangeAt(bag.id, 2_000)

        val summary = backup.importCsv(backup.exportCsv())

        assertEquals(0, summary.inserted)
        assertEquals(2, summary.skipped)
        assertEquals(2, db.changeEventDao().count().toInt())
    }

    suspend fun importV1MapsKindsToSeededSupplies(db: OstomateDatabase) {
        val backup = BackupRepository(db.changeEventDao(), db.supplyTypeDao())
        val v1 =
            """
            id,type,timestamp_iso8601
            1,BAG,2023-11-01T00:00:00Z
            2,BAG,2023-11-02T00:00:00Z
            3,FLANGE,2023-11-03T00:00:00Z
            """.trimIndent()

        val summary = backup.importCsv(v1)

        assertEquals(3, summary.inserted)
        assertEquals(0, summary.parseErrors)
        val bag = assertNotNull(db.supplyTypeDao().getByKind(SupplyKind.BAG))
        assertEquals(2, db.changeEventDao().getBySupplyType(bag.id).size)
    }

    suspend fun importCountsParseErrorsWithoutLosingGoodRows(db: OstomateDatabase) {
        val backup = BackupRepository(db.changeEventDao(), db.supplyTypeDao())
        val v2 =
            """
            supply_id,supply_name,supply_kind,timestamp_millis,edited_at_millis,note
            1,Bag,BAG,1000,,
            garbage line without enough columns
            1,Bag,BAG,not-a-number,,
            2,Flange,FLANGE,2000,,
            """.trimIndent()

        val summary = backup.importCsv(v2)

        assertEquals(2, summary.inserted)
        assertEquals(2, summary.parseErrors)
        assertEquals(2, db.changeEventDao().count().toInt())
    }

    suspend fun oversizedImportIsRejectedUntouched(db: OstomateDatabase) {
        val backup = BackupRepository(db.changeEventDao(), db.supplyTypeDao())
        val oversized = "x".repeat(10_000_001)

        val summary = backup.importCsv(oversized)

        assertTrue(summary.oversized)
        assertEquals(0, summary.inserted)
        assertEquals(0, db.changeEventDao().count().toInt())
    }

    // --- ChangeEventRepository inventory bookkeeping ---

    suspend fun deleteRestocksAndReinsertConsumesInventory(db: OstomateDatabase) {
        val events = ChangeEventRepository(db.changeEventDao(), db.supplyTypeDao())
        val supplyDao = db.supplyTypeDao()
        val bag = assertNotNull(supplyDao.getByKind(SupplyKind.BAG))
        supplyDao.setOnHand(bag.id, 10)

        val logged = events.logChangeAt(bag.id, 1_000)
        assertEquals(9, supplyDao.getById(bag.id)?.onHand)

        events.delete(logged)
        assertEquals(10, supplyDao.getById(bag.id)?.onHand)
        assertEquals(0, db.changeEventDao().count().toInt())

        events.reinsert(logged)
        assertEquals(9, supplyDao.getById(bag.id)?.onHand)
        assertEquals(1, db.changeEventDao().count().toInt())
    }

    suspend fun updateStampsEditedAt(db: OstomateDatabase) {
        val events = ChangeEventRepository(db.changeEventDao(), db.supplyTypeDao())
        val bag = assertNotNull(db.supplyTypeDao().getByKind(SupplyKind.BAG))
        val logged = events.logChangeAt(bag.id, 1_000)
        assertNull(logged.editedAtMillis)

        events.update(logged.copy(note = "edited"))

        val stored = db.changeEventDao().observeAllWithSupply().first().single().event
        assertEquals("edited", stored.note)
        assertNotNull(stored.editedAtMillis)
    }

    suspend fun deepLinkScansAreDebounced(db: OstomateDatabase) {
        val events = ChangeEventRepository(db.changeEventDao(), db.supplyTypeDao())

        assertEquals("Bag", events.handleDeepLink("ostomate://log?item=bag"))
        // Same sticker scanned again within the 3 s window: ignored.
        assertNull(events.handleDeepLink("ostomate://log?item=bag"))
        assertEquals(1, db.changeEventDao().count().toInt())
    }

    suspend fun customSupplyDeepLinkLogsById(db: OstomateDatabase) {
        val supplies = SupplyRepository(db.supplyTypeDao())
        val events = ChangeEventRepository(db.changeEventDao(), db.supplyTypeDao())
        supplies.addCustomSupply(name = "Barrier rings", boxSize = 20, warnThresholdDays = 10, colorIndex = 1)
        val custom = assertNotNull(db.supplyTypeDao().getAll().find { it.kind == SupplyKind.CUSTOM })

        assertEquals("Barrier rings", events.handleDeepLink("ostomate://log?item=id:${custom.id}"))
        assertEquals(1, db.changeEventDao().getBySupplyType(custom.id).size)
    }

    // --- SupplyRepository / SupplyTypeDao ---

    suspend fun addCustomSupplyAppendsAfterHighestSortOrder(db: OstomateDatabase) {
        val supplies = SupplyRepository(db.supplyTypeDao())
        val maxBefore = db.supplyTypeDao().maxSortOrder()

        supplies.addCustomSupply(name = "Paste", boxSize = 5, warnThresholdDays = 14, colorIndex = 2)

        val added = assertNotNull(db.supplyTypeDao().getAll().find { it.name == "Paste" })
        assertEquals(SupplyKind.CUSTOM, added.kind)
        assertEquals(maxBefore + 1, added.sortOrder)
        assertEquals(0, added.onHand)
    }

    suspend fun archivedSupplyIsSkippedByKindLookup(db: OstomateDatabase) {
        val supplies = SupplyRepository(db.supplyTypeDao())
        val bag = assertNotNull(db.supplyTypeDao().getByKind(SupplyKind.BAG))

        supplies.archive(bag.id)

        assertNull(db.supplyTypeDao().getByKind(SupplyKind.BAG))
        assertTrue(supplies.observeSupplies().first().none { it.id == bag.id })
    }

    suspend fun onHandAdjustmentsPersist(db: OstomateDatabase) {
        val dao = db.supplyTypeDao()
        val bag = assertNotNull(dao.getByKind(SupplyKind.BAG))

        dao.setOnHand(bag.id, 5)
        dao.incrementOnHand(bag.id)
        dao.incrementOnHand(bag.id)
        dao.decrementOnHand(bag.id)
        assertEquals(6, dao.getById(bag.id)?.onHand)

        dao.setWarnThreshold(bag.id, 21)
        assertEquals(21, dao.getById(bag.id)?.warnThresholdDays)
    }
}
