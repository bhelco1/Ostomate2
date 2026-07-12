package com.ostomate.app.data.db

import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// JVM host run (gates every PR). Robolectric supplies the Context + a desktop-capable
// SQLite via AndroidSQLiteDriver, since the bundled driver's native is Android-ABI only.
// Assertions are shared with the iOS run via ChangeEventDaoScenarios.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChangeEventDaoTest {
    private val db =
        buildDatabase(
            Room.inMemoryDatabaseBuilder<OstomateDatabase>(ApplicationProvider.getApplicationContext<Context>()),
            AndroidSQLiteDriver(),
        )

    @After
    fun tearDown() = db.close()

    @Test
    fun freshDatabaseSeedsV1DefaultSupplies() =
        runTest { ChangeEventDaoScenarios.freshDatabaseSeedsV1DefaultSupplies(db) }

    @Test
    fun insertAndReadBackJoinedWithSupply() = runTest { ChangeEventDaoScenarios.insertAndReadBackJoinedWithSupply(db) }

    @Test
    fun deleteRemovesRow() = runTest { ChangeEventDaoScenarios.deleteRemovesRow(db) }

    @Test
    fun archivedSuppliesAreExcludedFromActiveList() =
        runTest { ChangeEventDaoScenarios.archivedSuppliesAreExcludedFromActiveList(db) }

    @Test
    fun repositoryLogsChangeWithCurrentTime() =
        runTest { ChangeEventDaoScenarios.repositoryLogsChangeWithCurrentTime(db) }

    @Test
    fun repositoryHandlesValidDeepLink() = runTest { ChangeEventDaoScenarios.repositoryHandlesValidDeepLink(db) }

    @Test
    fun repositoryIgnoresInvalidDeepLink() = runTest { ChangeEventDaoScenarios.repositoryIgnoresInvalidDeepLink(db) }

    @Test
    fun repositoryDeepLinkIsAtomicUnderConcurrency() =
        runTest { ChangeEventDaoScenarios.repositoryDeepLinkIsAtomicUnderConcurrency(db) }

    @Test
    fun repositoryDeepLinkAllowsSecondScanAfterWindow() =
        runTest { ChangeEventDaoScenarios.repositoryDeepLinkAllowsSecondScanAfterWindow(db) }

    @Test
    fun repositoryDeepLinkNeedsConfirmationWithinWindow() =
        runTest { ChangeEventDaoScenarios.repositoryDeepLinkNeedsConfirmationWithinWindow(db) }

    @Test
    fun repositoryWasLoggedWithinWindowBoundary() =
        runTest { ChangeEventDaoScenarios.repositoryWasLoggedWithinWindowBoundary(db) }

    @Test
    fun repositoryWritesScanAuditForEveryOutcome() =
        runTest { ChangeEventDaoScenarios.repositoryWritesScanAuditForEveryOutcome(db) }
}
