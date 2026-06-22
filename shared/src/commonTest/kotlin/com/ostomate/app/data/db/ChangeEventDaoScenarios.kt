package com.ostomate.app.data.db

import com.ostomate.app.data.ChangeEventRepository
import com.ostomate.app.domain.SupplyKind
import kotlinx.coroutines.flow.first
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Database-agnostic assertions for the ChangeEvent DAO + repository. The per-platform
 * test classes (iosTest with the bundled native driver, androidHostTest with Robolectric
 * + AndroidSQLiteDriver) build a seeded in-memory [OstomateDatabase] and delegate here,
 * so the logic is written once and exercised on both targets.
 */
object ChangeEventDaoScenarios {
    suspend fun freshDatabaseSeedsV1DefaultSupplies(db: OstomateDatabase) {
        val supplies = db.supplyTypeDao().getAll()
        assertEquals(listOf("Bag", "Flange"), supplies.map { it.name })

        val bag = supplies[0]
        assertEquals(SupplyKind.BAG, bag.kind)
        assertEquals(30, bag.boxSize)
        assertEquals(14, bag.warnThresholdDays)
        assertEquals(0, bag.onHand)

        val flange = supplies[1]
        assertEquals(SupplyKind.FLANGE, flange.kind)
        assertEquals(5, flange.boxSize)
        assertEquals(14, flange.warnThresholdDays)
    }

    suspend fun insertAndReadBackJoinedWithSupply(db: OstomateDatabase) {
        val supplyDao = db.supplyTypeDao()
        val eventDao = db.changeEventDao()
        val bag = assertNotNull(supplyDao.getByKind(SupplyKind.BAG))
        val flange = assertNotNull(supplyDao.getByKind(SupplyKind.FLANGE))
        eventDao.insert(ChangeEventEntity(supplyTypeId = bag.id, timestampMillis = 1_000, createdAtMillis = 1_000))
        eventDao.insert(ChangeEventEntity(supplyTypeId = flange.id, timestampMillis = 2_000, createdAtMillis = 2_000))

        val rows = eventDao.observeAllWithSupply().first()
        assertEquals(2, rows.size)
        // newest first
        assertEquals("Flange", rows[0].supplyName)
        assertEquals(SupplyKind.FLANGE, rows[0].supplyKind)
        assertEquals("Bag", rows[1].supplyName)
    }

    suspend fun deleteRemovesRow(db: OstomateDatabase) {
        val supplyDao = db.supplyTypeDao()
        val eventDao = db.changeEventDao()
        val bag = assertNotNull(supplyDao.getByKind(SupplyKind.BAG))
        val event = ChangeEventEntity(supplyTypeId = bag.id, timestampMillis = 1_000, createdAtMillis = 1_000)
        val id = eventDao.insert(event)
        eventDao.delete(event.copy(id = id))
        assertEquals(0, eventDao.count())
    }

    suspend fun archivedSuppliesAreExcludedFromActiveList(db: OstomateDatabase) {
        val supplyDao = db.supplyTypeDao()
        val flange = assertNotNull(supplyDao.getByKind(SupplyKind.FLANGE))
        supplyDao.update(flange.copy(archived = true))

        val active = supplyDao.observeActive().first()
        assertEquals(listOf("Bag"), active.map { it.name })
    }

    suspend fun repositoryLogsChangeWithCurrentTime(db: OstomateDatabase) {
        val repository = ChangeEventRepository(db.changeEventDao(), db.supplyTypeDao())
        val bag = assertNotNull(db.supplyTypeDao().getByKind(SupplyKind.BAG))

        val event = repository.logChange(bag.id)
        assertTrue(event.id > 0)
        assertTrue(event.timestampMillis > 0)
        assertEquals(event.timestampMillis, event.createdAtMillis)
        assertNull(event.editedAtMillis)
        assertEquals(1, db.changeEventDao().count())
    }

    suspend fun repositoryHandlesValidDeepLink(db: OstomateDatabase) {
        val repository = ChangeEventRepository(db.changeEventDao(), db.supplyTypeDao())
        assertEquals("Flange", repository.handleDeepLink("ostomate://log?item=flange"))
        assertEquals(1, db.changeEventDao().count())
    }

    suspend fun repositoryIgnoresInvalidDeepLink(db: OstomateDatabase) {
        val repository = ChangeEventRepository(db.changeEventDao(), db.supplyTypeDao())
        assertNull(repository.handleDeepLink("ostomate://log?item=evil"))
        assertEquals(0, db.changeEventDao().count())
    }
}
