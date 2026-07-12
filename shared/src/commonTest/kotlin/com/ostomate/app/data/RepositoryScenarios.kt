package com.ostomate.app.data

import com.ostomate.app.data.db.ChangeEventEntity
import com.ostomate.app.data.db.OstomateDatabase
import com.ostomate.app.data.settings.AppSettings
import com.ostomate.app.data.settings.SettingsRepository
import com.ostomate.app.data.settings.createSettingsDataStore
import com.ostomate.app.domain.ApplianceType
import com.ostomate.app.domain.SupplyKind
import com.ostomate.app.testTempDir
import kotlinx.coroutines.flow.first
import okio.FileSystem
import okio.Path.Companion.toPath
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Database-agnostic assertions for the repository layer (2.5.4), shared between the
 * JVM-host and iOS-simulator runs like ChangeEventDaoScenarios. Backup round-trip
 * comes first: it is the data-loss-risk path.
 */
object RepositoryScenarios {
    // --- BackupRepository (full-state clone, FEAT-00) ---

    private fun newSettingsRepository(): Pair<SettingsRepository, String> {
        val path = (testTempDir().toPath() / "backup-settings-${Random.nextLong()}.preferences_pb").toString()
        return SettingsRepository(createSettingsDataStore { path }) to path
    }

    private fun backupRepository(
        db: OstomateDatabase,
        settings: SettingsRepository,
    ) = BackupRepository(db.backupDao(), db.changeEventDao(), db.supplyTypeDao(), settings)

    /**
     * Exports a source DB with an archived supply, a CUSTOM supply, notes/tags, two events at
     * the SAME millisecond, and non-default settings, then restores into a fresh seeded-default
     * DB and asserts every table and every setting is an exact clone (ids and FKs intact).
     */
    suspend fun fullBackupRoundTripClonesEverything(
        source: OstomateDatabase,
        target: OstomateDatabase,
    ) {
        val (sourceSettings, sourcePath) = newSettingsRepository()
        val (targetSettings, targetPath) = newSettingsRepository()
        try {
            val supplyDao = source.supplyTypeDao()
            val eventDao = source.changeEventDao()

            SupplyRepository(supplyDao).addCustomSupply(
                name = "Barrier rings",
                boxSize = 20,
                warnThresholdDays = 10,
                colorIndex = 5,
            )
            val custom = assertNotNull(supplyDao.getAll().find { it.kind == SupplyKind.CUSTOM })
            val bag = assertNotNull(supplyDao.getByKind(SupplyKind.BAG))
            val flange = assertNotNull(supplyDao.getByKind(SupplyKind.FLANGE))
            supplyDao.archive(flange.id)

            // Two events at the same millisecond, one edited and tagged.
            eventDao.insert(
                ChangeEventEntity(
                    supplyTypeId = bag.id,
                    timestampMillis = 5_000,
                    note = "leak overnight",
                    tags = "leak,night",
                    createdAtMillis = 5_000,
                    editedAtMillis = 6_000,
                ),
            )
            eventDao.insert(
                ChangeEventEntity(
                    supplyTypeId = custom.id,
                    timestampMillis = 5_000,
                    createdAtMillis = 5_001,
                ),
            )
            eventDao.insert(
                ChangeEventEntity(
                    supplyTypeId = bag.id,
                    timestampMillis = 9_000,
                    createdAtMillis = 9_000,
                ),
            )

            val sourceStateSettings =
                AppSettings(
                    devMode = true,
                    onboardingDone = true,
                    lockSettings = true,
                    localeOverride = "es",
                    crashReportingEnabled = true,
                    applianceType = ApplianceType.ONE_PIECE,
                )
            sourceSettings.restore(sourceStateSettings)

            val json = backupRepository(source, sourceSettings).exportBackup()
            val result = backupRepository(target, targetSettings).restoreBackup(json)

            val success = assertIs<RestoreResult.Success>(result)
            assertEquals(3, success.supplyTypes)
            assertEquals(3, success.events)

            // Every supply row (incl. the archived + CUSTOM one) is an exact clone.
            assertEquals(source.supplyTypeDao().getAll(), target.supplyTypeDao().getAll())
            // Every event row — all columns, ids, and both same-ms rows — is an exact clone.
            assertEquals(
                source.changeEventDao().getAllRaw().sortedBy { it.id },
                target.changeEventDao().getAllRaw().sortedBy { it.id },
            )
            assertEquals(2, target.changeEventDao().getAllRaw().count { it.timestampMillis == 5_000L })
            // Settings match the source exactly.
            assertEquals(sourceStateSettings, targetSettings.settings.first())
        } finally {
            FileSystem.SYSTEM.delete(sourcePath.toPath(), mustExist = false)
            FileSystem.SYSTEM.delete(targetPath.toPath(), mustExist = false)
        }
    }

    suspend fun malformedBackupIsRejectedLeavingDataUntouched(db: OstomateDatabase) {
        val (settings, path) = newSettingsRepository()
        try {
            val events = ChangeEventRepository(db.changeEventDao(), db.supplyTypeDao())
            val bag = assertNotNull(db.supplyTypeDao().getByKind(SupplyKind.BAG))
            events.logChangeAt(bag.id, 1_000)
            val suppliesBefore = db.supplyTypeDao().getAll()

            val result = backupRepository(db, settings).restoreBackup("{ not valid json ")

            val failure = assertIs<RestoreResult.Failure>(result)
            assertEquals(RestoreError.MALFORMED, failure.error)
            assertEquals(suppliesBefore, db.supplyTypeDao().getAll())
            assertEquals(1, db.changeEventDao().count().toInt())
        } finally {
            FileSystem.SYSTEM.delete(path.toPath(), mustExist = false)
        }
    }

    suspend fun wrongSchemaVersionIsRejectedLeavingDataUntouched(db: OstomateDatabase) {
        val (settings, path) = newSettingsRepository()
        try {
            val events = ChangeEventRepository(db.changeEventDao(), db.supplyTypeDao())
            val bag = assertNotNull(db.supplyTypeDao().getByKind(SupplyKind.BAG))
            events.logChangeAt(bag.id, 1_000)
            val backup = backupRepository(db, settings)

            // A structurally valid document whose schemaVersion the app cannot accept.
            val bumped = backup.exportBackup().replace("\"schemaVersion\": 3", "\"schemaVersion\": 999")

            val result = backup.restoreBackup(bumped)

            val failure = assertIs<RestoreResult.Failure>(result)
            assertEquals(RestoreError.UNSUPPORTED_SCHEMA_VERSION, failure.error)
            assertEquals(1, db.changeEventDao().count().toInt())
            assertNotNull(db.supplyTypeDao().getByKind(SupplyKind.BAG))
        } finally {
            FileSystem.SYSTEM.delete(path.toPath(), mustExist = false)
        }
    }

    suspend fun oversizedBackupIsRejectedLeavingDataUntouched(db: OstomateDatabase) {
        val (settings, path) = newSettingsRepository()
        try {
            val result = backupRepository(db, settings).restoreBackup("x".repeat(10_000_001))

            val failure = assertIs<RestoreResult.Failure>(result)
            assertEquals(RestoreError.OVERSIZED, failure.error)
            assertNotNull(db.supplyTypeDao().getByKind(SupplyKind.BAG))
            assertEquals(0, db.changeEventDao().count().toInt())
        } finally {
            FileSystem.SYSTEM.delete(path.toPath(), mustExist = false)
        }
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
