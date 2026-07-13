package com.ostomate.app.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import com.ostomate.app.data.db.OstomateDatabase
import com.ostomate.app.data.db.buildDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// JVM host run (gates every PR). Same Robolectric + AndroidSQLiteDriver setup as
// ChangeEventDaoTest; assertions are shared with the iOS run via RepositoryScenarios.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RepositoryTest {
    private fun newDatabase(): OstomateDatabase =
        buildDatabase(
            Room.inMemoryDatabaseBuilder<OstomateDatabase>(ApplicationProvider.getApplicationContext<Context>()),
            AndroidSQLiteDriver(),
        )

    private val db = newDatabase()
    private val extraDbs = mutableListOf<OstomateDatabase>()

    @After
    fun tearDown() {
        db.close()
        extraDbs.forEach { it.close() }
    }

    @Test
    fun fullBackupRoundTripClonesEverything() =
        runTest {
            val target = newDatabase().also { extraDbs += it }
            RepositoryScenarios.fullBackupRoundTripClonesEverything(db, target)
        }

    @Test
    fun malformedBackupIsRejectedLeavingDataUntouched() =
        runTest { RepositoryScenarios.malformedBackupIsRejectedLeavingDataUntouched(db) }

    @Test
    fun wrongSchemaVersionIsRejectedLeavingDataUntouched() =
        runTest { RepositoryScenarios.wrongSchemaVersionIsRejectedLeavingDataUntouched(db) }

    @Test
    fun oversizedBackupIsRejectedLeavingDataUntouched() =
        runTest { RepositoryScenarios.oversizedBackupIsRejectedLeavingDataUntouched(db) }

    @Test
    fun deleteRestocksAndReinsertConsumesInventory() =
        runTest { RepositoryScenarios.deleteRestocksAndReinsertConsumesInventory(db) }

    @Test
    fun loggingAtZeroOnHandNeverGoesNegative() =
        runTest { RepositoryScenarios.loggingAtZeroOnHandNeverGoesNegative(db) }

    @Test
    fun undoingALogAtZeroDoesNotInventInventory() =
        runTest { RepositoryScenarios.undoingALogAtZeroDoesNotInventInventory(db) }

    @Test
    fun undoLogRestoresThePriorCount() = runTest { RepositoryScenarios.undoLogRestoresThePriorCount(db) }

    @Test
    fun updateStampsEditedAt() = runTest { RepositoryScenarios.updateStampsEditedAt(db) }

    @Test
    fun deepLinkScansAreDebounced() = runTest { RepositoryScenarios.deepLinkScansAreDebounced(db) }

    @Test
    fun customSupplyDeepLinkLogsById() = runTest { RepositoryScenarios.customSupplyDeepLinkLogsById(db) }

    @Test
    fun addCustomSupplyAppendsAfterHighestSortOrder() =
        runTest { RepositoryScenarios.addCustomSupplyAppendsAfterHighestSortOrder(db) }

    @Test
    fun archivedSupplyIsSkippedByKindLookup() = runTest { RepositoryScenarios.archivedSupplyIsSkippedByKindLookup(db) }

    @Test
    fun onHandAdjustmentsPersist() = runTest { RepositoryScenarios.onHandAdjustmentsPersist(db) }
}
