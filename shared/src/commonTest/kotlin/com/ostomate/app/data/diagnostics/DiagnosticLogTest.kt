package com.ostomate.app.data.diagnostics

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DiagnosticLogTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun lines(store: InMemoryDiagnosticLogStore) = store.read().split('\n').filter { it.isNotBlank() }

    private fun parse(line: String): ScanAuditEntry = json.decodeFromString(line)

    @Test
    fun recordsScanAuditAsOneJsonLinePerEntry() =
        runTest {
            val store = InMemoryDiagnosticLogStore()
            val log = DiagnosticLog(store, clock = { 1_000L })

            log.recordScanAudit(
                uri = "ostomate://log?item=bag",
                entryPoint = DeepLinkEntryPoint.ANDROID_ON_CREATE,
                savedInstanceStateWasNull = true,
                decision = ScanDecision.LOGGED,
                eventId = 42L,
            )
            log.recordScanAudit(
                uri = "ostomate://log?item=flange",
                entryPoint = DeepLinkEntryPoint.IOS_ON_OPEN_URL,
                savedInstanceStateWasNull = null,
                decision = ScanDecision.SUPPRESSED,
                eventId = null,
            )

            val recorded = lines(store)
            assertEquals(2, recorded.size)
        }

    @Test
    fun serializesEveryScanAuditFieldFaithfully() =
        runTest {
            val store = InMemoryDiagnosticLogStore()
            val log = DiagnosticLog(store, clock = { 1_700_000_000_000L })

            log.recordScanAudit(
                uri = "ostomate://log?item=bag",
                entryPoint = DeepLinkEntryPoint.ANDROID_ON_NEW_INTENT,
                savedInstanceStateWasNull = false,
                decision = ScanDecision.NEEDS_CONFIRMATION,
                eventId = 7L,
            )

            val entry = parse(lines(store).single())
            assertEquals(1_700_000_000_000L, entry.time)
            assertEquals("ostomate://log?item=bag", entry.uri)
            assertEquals(DeepLinkEntryPoint.ANDROID_ON_NEW_INTENT, entry.entryPoint)
            assertEquals(false, entry.savedInstanceStateWasNull)
            assertEquals(ScanDecision.NEEDS_CONFIRMATION, entry.decision)
            assertEquals(7L, entry.eventId)
        }

    @Test
    fun preservesNullEventIdAndNullSavedInstanceState() =
        runTest {
            val store = InMemoryDiagnosticLogStore()
            val log = DiagnosticLog(store, clock = { 5L })

            log.recordScanAudit(
                uri = "ostomate://log?item=evil",
                entryPoint = DeepLinkEntryPoint.IOS_ON_OPEN_URL,
                savedInstanceStateWasNull = null,
                decision = ScanDecision.INVALID,
                eventId = null,
            )

            val entry = parse(lines(store).single())
            assertNull(entry.eventId)
            assertNull(entry.savedInstanceStateWasNull)
            assertEquals(ScanDecision.INVALID, entry.decision)
        }

    @Test
    fun rollingCapDropsOldestEntriesOverTheCap() =
        runTest {
            val store = InMemoryDiagnosticLogStore()
            var now = 0L
            val log = DiagnosticLog(store, clock = { now++ }, maxEntries = 3)

            repeat(5) {
                log.recordScanAudit(
                    uri = "ostomate://log?item=bag",
                    entryPoint = DeepLinkEntryPoint.ANDROID_ON_CREATE,
                    savedInstanceStateWasNull = true,
                    decision = ScanDecision.LOGGED,
                    eventId = it.toLong(),
                )
            }

            val recorded = lines(store).map { parse(it) }
            assertEquals(3, recorded.size)
            // The two oldest (eventId 0 and 1) were dropped; the newest three remain in order.
            assertEquals(listOf(2L, 3L, 4L), recorded.map { it.eventId })
        }

    @Test
    fun appendsToAPreExistingFileWithoutLosingHistory() =
        runTest {
            val store = InMemoryDiagnosticLogStore(initial = "{\"legacy\":true}\n")
            val log = DiagnosticLog(store, clock = { 9L }, maxEntries = 10)

            log.recordScanAudit(
                uri = "ostomate://log?item=bag",
                entryPoint = DeepLinkEntryPoint.ANDROID_ON_CREATE,
                savedInstanceStateWasNull = true,
                decision = ScanDecision.LOGGED,
                eventId = 1L,
            )

            val recorded = lines(store)
            assertEquals(2, recorded.size)
            assertTrue(recorded.first().contains("legacy"))
        }

    @Test
    fun exportReflectsClear() =
        runTest {
            val store = InMemoryDiagnosticLogStore()
            val log = DiagnosticLog(store, clock = { 1L })
            log.recordScanAudit(
                uri = "ostomate://log?item=bag",
                entryPoint = DeepLinkEntryPoint.ANDROID_ON_CREATE,
                savedInstanceStateWasNull = true,
                decision = ScanDecision.LOGGED,
                eventId = 1L,
            )
            assertTrue(log.export().isNotBlank())

            log.clear()
            assertEquals("", log.export())
        }
}
