package com.ostomate.app.data

import androidx.room.Room
import com.ostomate.app.data.db.OstomateDatabase
import com.ostomate.app.data.db.buildDatabase
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test

// iOS run: bundled native SQLite driver (the real iOS path). Assertions are shared
// with the JVM-host run via RepositoryScenarios.
class RepositoryTest {
    private fun newDatabase(): OstomateDatabase = buildDatabase(Room.inMemoryDatabaseBuilder<OstomateDatabase>())

    private val db = newDatabase()
    private val extraDbs = mutableListOf<OstomateDatabase>()

    @AfterTest
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
