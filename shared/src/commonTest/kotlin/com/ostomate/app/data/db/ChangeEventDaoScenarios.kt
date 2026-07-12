package com.ostomate.app.data.db

import com.ostomate.app.data.ChangeEventRepository
import com.ostomate.app.data.DeepLinkOutcome
import com.ostomate.app.data.diagnostics.DeepLinkEntryPoint
import com.ostomate.app.data.diagnostics.DiagnosticLog
import com.ostomate.app.data.diagnostics.InMemoryDiagnosticLogStore
import com.ostomate.app.data.diagnostics.ScanAuditEntry
import com.ostomate.app.data.diagnostics.ScanDecision
import com.ostomate.app.domain.SupplyKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlin.test.assertEquals
import kotlin.test.assertIs
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
        val outcome = repository.handleDeepLink("ostomate://log?item=flange")
        assertEquals(DeepLinkOutcome.Logged("Flange"), outcome)
        assertEquals(1, db.changeEventDao().count())
    }

    suspend fun repositoryIgnoresInvalidDeepLink(db: OstomateDatabase) {
        val repository = ChangeEventRepository(db.changeEventDao(), db.supplyTypeDao())
        assertEquals(DeepLinkOutcome.Invalid, repository.handleDeepLink("ostomate://log?item=evil"))
        assertEquals(0, db.changeEventDao().count())
    }

    /**
     * Two deep links for the same item, delivered concurrently within the debounce window
     * (the Android intent-replay race in BUG-09), must collapse to a single change event.
     * The atomic [ChangeEventRepository] mutex guarantees the check-and-set is not
     * interleaved even under real parallelism on [Dispatchers.Default].
     */
    suspend fun repositoryDeepLinkIsAtomicUnderConcurrency(db: OstomateDatabase) {
        val repository = ChangeEventRepository(db.changeEventDao(), db.supplyTypeDao())
        val bag = assertNotNull(db.supplyTypeDao().getByKind(SupplyKind.BAG))
        assertEquals(0, bag.onHand)
        db.supplyTypeDao().update(bag.copy(onHand = 5))

        val results =
            coroutineScope {
                awaitAll(
                    async(Dispatchers.Default) { repository.handleDeepLink("ostomate://log?item=bag") },
                    async(Dispatchers.Default) { repository.handleDeepLink("ostomate://log?item=bag") },
                )
            }

        // Exactly one call is allowed through; the other is debounced to Suppressed.
        assertEquals(1, results.count { it is DeepLinkOutcome.Logged })
        assertEquals(1, results.count { it == DeepLinkOutcome.Suppressed })
        assertEquals(1, db.changeEventDao().count())
        assertEquals(4, assertNotNull(db.supplyTypeDao().getByKind(SupplyKind.BAG)).onHand)
    }

    /**
     * A rescan inside the 3 s debounce is Suppressed; past the debounce but inside the
     * 10-min confirm window it becomes NeedsConfirmation (no insert); only once past the
     * confirm window does it log again. A test clock advances without waiting.
     */
    suspend fun repositoryDeepLinkAllowsSecondScanAfterWindow(db: OstomateDatabase) {
        var nowMillis = 100_000L
        val repository = ChangeEventRepository(db.changeEventDao(), db.supplyTypeDao()) { nowMillis }
        val bag = assertNotNull(db.supplyTypeDao().getByKind(SupplyKind.BAG))
        db.supplyTypeDao().update(bag.copy(onHand = 5))

        assertEquals(DeepLinkOutcome.Logged("Bag"), repository.handleDeepLink("ostomate://log?item=bag"))
        // Still inside the 3 s window: debounced.
        nowMillis += 2_000L
        assertEquals(DeepLinkOutcome.Suppressed, repository.handleDeepLink("ostomate://log?item=bag"))
        // Past the debounce but within the 10-min confirm window: needs confirmation, no insert.
        nowMillis += 1_500L
        assertIs<DeepLinkOutcome.NeedsConfirmation>(repository.handleDeepLink("ostomate://log?item=bag"))
        assertEquals(1, db.changeEventDao().count())
        // Past the confirm window: logs again.
        nowMillis += 11 * 60 * 1000L
        assertEquals(DeepLinkOutcome.Logged("Bag"), repository.handleDeepLink("ostomate://log?item=bag"))

        assertEquals(2, db.changeEventDao().count())
        assertEquals(3, assertNotNull(db.supplyTypeDao().getByKind(SupplyKind.BAG)).onHand)
    }

    /**
     * A genuine second scan of the same supply within the 10-min window asks for
     * confirmation instead of logging; confirming it (a plain logChange) inserts the
     * second event.
     */
    suspend fun repositoryDeepLinkNeedsConfirmationWithinWindow(db: OstomateDatabase) {
        var nowMillis = 1_000_000L
        val repository = ChangeEventRepository(db.changeEventDao(), db.supplyTypeDao()) { nowMillis }
        val bag = assertNotNull(db.supplyTypeDao().getByKind(SupplyKind.BAG))
        db.supplyTypeDao().update(bag.copy(onHand = 5))

        assertEquals(DeepLinkOutcome.Logged("Bag"), repository.handleDeepLink("ostomate://log?item=bag"))
        // 4 minutes later (past debounce, inside the 10-min window).
        nowMillis += 4 * 60 * 1000L
        val outcome = repository.handleDeepLink("ostomate://log?item=bag")
        val confirmation = assertIs<DeepLinkOutcome.NeedsConfirmation>(outcome)
        assertEquals(bag.id, confirmation.supplyId)
        assertEquals("Bag", confirmation.supplyName)
        assertEquals(4, confirmation.minutesAgo)
        assertEquals(1, db.changeEventDao().count())

        // Confirming the repeat logs the second event.
        repository.logChange(confirmation.supplyId)
        assertEquals(2, db.changeEventDao().count())
        assertEquals(3, assertNotNull(db.supplyTypeDao().getByKind(SupplyKind.BAG)).onHand)
    }

    /**
     * FEAT-02 scan audit: every handleDeepLink call, across every debounce outcome, appends a
     * well-formed entry to the diagnostic log carrying the uri, entry point, savedInstanceState
     * flag, decision, and (only when logged) the resulting event id — the data needed to trace a
     * BUG-09 duplicate after the fact.
     */
    suspend fun repositoryWritesScanAuditForEveryOutcome(db: OstomateDatabase) {
        val store = InMemoryDiagnosticLogStore()
        var now = 1_000_000L
        val log = DiagnosticLog(store, clock = { now })
        val repository = ChangeEventRepository(db.changeEventDao(), db.supplyTypeDao(), log, { now })
        val bag = assertNotNull(db.supplyTypeDao().getByKind(SupplyKind.BAG))
        db.supplyTypeDao().update(bag.copy(onHand = 5))

        assertEquals(
            DeepLinkOutcome.Logged("Bag"),
            repository.handleDeepLink(
                "ostomate://log?item=bag",
                DeepLinkEntryPoint.ANDROID_ON_CREATE,
                savedInstanceStateWasNull = true,
            ),
        )
        // Within the 3 s debounce: suppressed.
        now += 1_000L
        assertEquals(
            DeepLinkOutcome.Suppressed,
            repository.handleDeepLink(
                "ostomate://log?item=bag",
                DeepLinkEntryPoint.ANDROID_ON_NEW_INTENT,
                savedInstanceStateWasNull = null,
            ),
        )
        // Past the debounce, inside the 10-min window: needs confirmation, no insert.
        now += 5_000L
        assertIs<DeepLinkOutcome.NeedsConfirmation>(
            repository.handleDeepLink(
                "ostomate://log?item=bag",
                DeepLinkEntryPoint.IOS_ON_OPEN_URL,
                savedInstanceStateWasNull = null,
            ),
        )
        // Unrecognized link: invalid.
        assertEquals(
            DeepLinkOutcome.Invalid,
            repository.handleDeepLink(
                "ostomate://log?item=evil",
                DeepLinkEntryPoint.ANDROID_ON_CREATE,
                savedInstanceStateWasNull = true,
            ),
        )

        val entries =
            store.read()
                .split('\n')
                .filter { it.isNotBlank() }
                .map { Json.decodeFromString<ScanAuditEntry>(it) }
        assertEquals(4, entries.size)

        assertEquals(ScanDecision.LOGGED, entries[0].decision)
        assertEquals(DeepLinkEntryPoint.ANDROID_ON_CREATE, entries[0].entryPoint)
        assertEquals(true, entries[0].savedInstanceStateWasNull)
        assertEquals("ostomate://log?item=bag", entries[0].uri)
        assertNotNull(entries[0].eventId)

        assertEquals(ScanDecision.SUPPRESSED, entries[1].decision)
        assertEquals(DeepLinkEntryPoint.ANDROID_ON_NEW_INTENT, entries[1].entryPoint)
        assertNull(entries[1].savedInstanceStateWasNull)
        assertNull(entries[1].eventId)

        assertEquals(ScanDecision.NEEDS_CONFIRMATION, entries[2].decision)
        assertEquals(DeepLinkEntryPoint.IOS_ON_OPEN_URL, entries[2].entryPoint)
        assertNull(entries[2].eventId)

        assertEquals(ScanDecision.INVALID, entries[3].decision)
        assertNull(entries[3].eventId)

        assertEquals(1, db.changeEventDao().count())
    }

    /** wasLoggedWithinWindow flips exactly at the 10-min boundary. */
    suspend fun repositoryWasLoggedWithinWindowBoundary(db: OstomateDatabase) {
        val windowMs = 10 * 60 * 1000L
        val logged = 1_000_000L
        var nowMillis = logged
        val repository = ChangeEventRepository(db.changeEventDao(), db.supplyTypeDao()) { nowMillis }
        val bag = assertNotNull(db.supplyTypeDao().getByKind(SupplyKind.BAG))
        repository.logChange(bag.id)

        // Just inside the window.
        nowMillis = logged + windowMs - 1
        assertEquals(9, assertNotNull(repository.wasLoggedWithinWindow(bag.id)))
        // Exactly at / just outside the window.
        nowMillis = logged + windowMs
        assertNull(repository.wasLoggedWithinWindow(bag.id))
        nowMillis = logged + windowMs + 1
        assertNull(repository.wasLoggedWithinWindow(bag.id))
        // No history for an unknown supply.
        assertNull(repository.wasLoggedWithinWindow(supplyId = 9_999L))
    }
}
