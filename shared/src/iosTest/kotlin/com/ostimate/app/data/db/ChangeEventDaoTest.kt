package com.ostimate.app.data.db

import androidx.room.Room
import com.ostimate.app.data.ChangeEventRepository
import com.ostimate.app.domain.SupplyKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChangeEventDaoTest {

    // buildDatabase (not a bare builder) so the seed callback runs, as in production.
    private val db: OstimateDatabase = buildDatabase(Room.inMemoryDatabaseBuilder<OstimateDatabase>())
    private val supplyDao = db.supplyTypeDao()
    private val eventDao = db.changeEventDao()

    @AfterTest
    fun tearDown() {
        db.close()
    }

    @Test
    fun freshDatabaseSeedsV1DefaultSupplies() = runTest {
        val supplies = supplyDao.getAll()
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

    @Test
    fun insertAndReadBackJoinedWithSupply() = runTest {
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

    @Test
    fun deleteRemovesRow() = runTest {
        val bag = assertNotNull(supplyDao.getByKind(SupplyKind.BAG))
        val event = ChangeEventEntity(supplyTypeId = bag.id, timestampMillis = 1_000, createdAtMillis = 1_000)
        val id = eventDao.insert(event)
        eventDao.delete(event.copy(id = id))
        assertEquals(0, eventDao.count())
    }

    @Test
    fun archivedSuppliesAreExcludedFromActiveList() = runTest {
        val flange = assertNotNull(supplyDao.getByKind(SupplyKind.FLANGE))
        supplyDao.update(flange.copy(archived = true))

        val active = supplyDao.observeActive().first()
        assertEquals(listOf("Bag"), active.map { it.name })
    }

    @Test
    fun repositoryLogsChangeWithCurrentTime() = runTest {
        val repository = ChangeEventRepository(eventDao, supplyDao)
        val bag = assertNotNull(supplyDao.getByKind(SupplyKind.BAG))

        val event = repository.logChange(bag.id)
        assertTrue(event.id > 0)
        assertTrue(event.timestampMillis > 0)
        assertEquals(event.timestampMillis, event.createdAtMillis)
        assertNull(event.editedAtMillis)
        assertEquals(1, eventDao.count())
    }

    @Test
    fun repositoryHandlesValidDeepLink() = runTest {
        val repository = ChangeEventRepository(eventDao, supplyDao)
        assertEquals("Flange", repository.handleDeepLink("ostimate://log?item=flange"))
        assertEquals(1, eventDao.count())
    }

    @Test
    fun repositoryIgnoresInvalidDeepLink() = runTest {
        val repository = ChangeEventRepository(eventDao, supplyDao)
        assertNull(repository.handleDeepLink("ostimate://log?item=evil"))
        assertEquals(0, eventDao.count())
    }
}
