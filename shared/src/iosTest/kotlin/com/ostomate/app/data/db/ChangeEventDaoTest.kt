package com.ostomate.app.data.db

import androidx.room.Room
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test

// iOS run: bundled native SQLite driver (the real iOS path). Assertions are shared
// with the JVM-host run via ChangeEventDaoScenarios. buildDatabase runs the seed
// callback + migrations, as in production.
class ChangeEventDaoTest {
    private val db: OstomateDatabase = buildDatabase(Room.inMemoryDatabaseBuilder<OstomateDatabase>())

    @AfterTest
    fun tearDown() = db.close()

    @Test
    fun freshDatabaseSeedsV1DefaultSupplies() =
        runTest { ChangeEventDaoScenarios.freshDatabaseSeedsV1DefaultSupplies(db) }

    @Test
    fun insertAndReadBackJoinedWithSupply() =
        runTest { ChangeEventDaoScenarios.insertAndReadBackJoinedWithSupply(db) }

    @Test
    fun deleteRemovesRow() = runTest { ChangeEventDaoScenarios.deleteRemovesRow(db) }

    @Test
    fun archivedSuppliesAreExcludedFromActiveList() =
        runTest { ChangeEventDaoScenarios.archivedSuppliesAreExcludedFromActiveList(db) }

    @Test
    fun repositoryLogsChangeWithCurrentTime() =
        runTest { ChangeEventDaoScenarios.repositoryLogsChangeWithCurrentTime(db) }

    @Test
    fun repositoryHandlesValidDeepLink() =
        runTest { ChangeEventDaoScenarios.repositoryHandlesValidDeepLink(db) }

    @Test
    fun repositoryIgnoresInvalidDeepLink() =
        runTest { ChangeEventDaoScenarios.repositoryIgnoresInvalidDeepLink(db) }

    @Test
    fun repositoryDeepLinkIsAtomicUnderConcurrency() =
        runTest { ChangeEventDaoScenarios.repositoryDeepLinkIsAtomicUnderConcurrency(db) }

    @Test
    fun repositoryDeepLinkAllowsSecondScanAfterWindow() =
        runTest { ChangeEventDaoScenarios.repositoryDeepLinkAllowsSecondScanAfterWindow(db) }
}
