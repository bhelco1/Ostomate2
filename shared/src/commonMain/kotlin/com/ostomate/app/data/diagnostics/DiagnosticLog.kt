package com.ostomate.app.data.diagnostics

import com.ostomate.app.platform.currentTimeMillis
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Where a deep link entered the app; recorded in the scan audit to diagnose BUG-09 replays. */
enum class DeepLinkEntryPoint {
    ANDROID_ON_CREATE,
    ANDROID_ON_NEW_INTENT,
    IOS_ON_OPEN_URL,
}

/** The debounce decision handleDeepLink reached for a scan; mirrors [DeepLinkOutcome]. */
enum class ScanDecision {
    LOGGED,
    SUPPRESSED,
    NEEDS_CONFIRMATION,
    INVALID,
}

/**
 * One line in the rolling diagnostic log. Serialized as a single compact JSON object so the
 * exported file is JSON-lines: human-scannable and machine-parseable, one entry per line.
 */
@Serializable
data class ScanAuditEntry(
    val time: Long,
    val uri: String,
    val entryPoint: DeepLinkEntryPoint,
    val savedInstanceStateWasNull: Boolean?,
    val decision: ScanDecision,
    val eventId: Long?,
)

/**
 * Raw byte-store seam for the diagnostic file. The read-modify-write rolling logic lives in
 * [DiagnosticLog] (commonMain); only locating and reading/writing the app-private file is
 * platform-specific (see the androidMain / iosMain factories), mirroring SettingsDataStore.
 */
interface DiagnosticLogStore {
    fun read(): String

    fun write(content: String)
}

/** Non-persistent store used as a safe default and in tests. */
class InMemoryDiagnosticLogStore(
    initial: String = "",
) : DiagnosticLogStore {
    private var content: String = initial

    override fun read(): String = content

    override fun write(content: String) {
        this.content = content
    }
}

/**
 * A rolling, on-device diagnostic log the user can export and share on request (local-first —
 * nothing is ever phoned home; see 06-security-privacy.md and FEAT-02).
 *
 * Storage is JSON-lines in a single app-private file. It is bounded: only the most recent
 * [maxEntries] lines are retained; older lines are dropped on each append. With a ~200-byte
 * ceiling per line this caps the file near 100 KB, so it never grows without limit.
 *
 * Appends serialize a read-modify-write behind a [Mutex] so concurrent scans (the BUG-09
 * intent-replay race) cannot interleave and corrupt or lose lines.
 */
class DiagnosticLog(
    private val store: DiagnosticLogStore,
    private val clock: () -> Long = { currentTimeMillis() },
    private val maxEntries: Int = MAX_ENTRIES,
) {
    private val mutex = Mutex()

    /** Records a deep-link scan audit entry with the wall-clock time from the clock. */
    suspend fun recordScanAudit(
        uri: String,
        entryPoint: DeepLinkEntryPoint,
        savedInstanceStateWasNull: Boolean?,
        decision: ScanDecision,
        eventId: Long?,
    ) {
        val entry =
            ScanAuditEntry(
                time = clock(),
                uri = uri,
                entryPoint = entryPoint,
                savedInstanceStateWasNull = savedInstanceStateWasNull,
                decision = decision,
                eventId = eventId,
            )
        append(json.encodeToString(entry))
    }

    /** Current log contents (JSON-lines). Empty string when nothing has been recorded. */
    fun export(): String = store.read()

    /** Clears the log. */
    suspend fun clear() {
        mutex.withLock { store.write("") }
    }

    private suspend fun append(line: String) {
        mutex.withLock {
            val existing =
                store.read()
                    .split('\n')
                    .filter { it.isNotBlank() }
            val rolled = (existing + line).takeLast(maxEntries)
            store.write(rolled.joinToString("\n"))
        }
    }

    companion object {
        /** Rolling cap: the most recent 500 entries are kept; older ones are dropped. */
        const val MAX_ENTRIES = 500

        private val json = Json { encodeDefaults = true }
    }
}
