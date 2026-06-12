package com.ostimate.app.data.db

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.ostimate.app.data.ChangeEventRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChangeEventDaoTest {

    private val db: OstimateDatabase = Room.inMemoryDatabaseBuilder<OstimateDatabase>()
        .setDriver(BundledSQLiteDriver())
        .build()
    private val dao = db.changeEventDao()

    @AfterTest
    fun tearDown() {
        db.close()
    }

    @Test
    fun insertAndReadBack() = runTest {
        dao.insert(ChangeEventEntity(supply = "bag", timestampMillis = 1_000))
        dao.insert(ChangeEventEntity(supply = "flange", timestampMillis = 2_000))

        val events = dao.observeAll().first()
        assertEquals(2, events.size)
        // newest first
        assertEquals("flange", events[0].supply)
        assertEquals("bag", events[1].supply)
    }

    @Test
    fun deleteRemovesRow() = runTest {
        val id = dao.insert(ChangeEventEntity(supply = "bag", timestampMillis = 1_000))
        dao.delete(ChangeEventEntity(id = id, supply = "bag", timestampMillis = 1_000))
        assertEquals(0, dao.count())
    }

    @Test
    fun repositoryLogsChangeWithCurrentTime() = runTest {
        val repository = ChangeEventRepository(dao)
        val event = repository.logChange("bag")
        assertTrue(event.id > 0)
        assertTrue(event.timestampMillis > 0)
        assertEquals(1, dao.count())
    }

    @Test
    fun repositoryHandlesValidDeepLink() = runTest {
        val repository = ChangeEventRepository(dao)
        assertEquals("flange", repository.handleDeepLink("ostimate://log?item=flange"))
        assertEquals(1, dao.count())
    }

    @Test
    fun repositoryIgnoresInvalidDeepLink() = runTest {
        val repository = ChangeEventRepository(dao)
        assertNull(repository.handleDeepLink("ostimate://log?item=evil"))
        assertEquals(0, dao.count())
    }
}
