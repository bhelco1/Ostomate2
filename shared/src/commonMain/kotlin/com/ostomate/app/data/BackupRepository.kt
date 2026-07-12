package com.ostomate.app.data

import com.ostomate.app.data.db.BackupDao
import com.ostomate.app.data.db.ChangeEventDao
import com.ostomate.app.data.db.OstomateDatabase
import com.ostomate.app.data.db.SupplyTypeDao
import com.ostomate.app.data.settings.SettingsRepository
import com.ostomate.app.platform.currentTimeMillis
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

/** Why a restore was refused; the UI maps each to an externalized, user-facing message. */
enum class RestoreError {
    OVERSIZED,
    MALFORMED,
    UNSUPPORTED_FORMAT_VERSION,
    UNSUPPORTED_SCHEMA_VERSION,
}

sealed interface RestoreResult {
    /** Data was replaced; counts are what was written. */
    data class Success(val supplyTypes: Int, val events: Int) : RestoreResult

    /** Nothing was touched; existing data is intact. */
    data class Failure(val error: RestoreError) : RestoreResult
}

/** Current backup document version; bump when the JSON envelope shape changes. */
const val BACKUP_FORMAT_VERSION = 1

// ~10 MB char guard against pathological inputs, matching the previous CSV import guard.
private const val MAX_BACKUP_CHARS = 10_000_000

private val backupJson =
    Json {
        prettyPrint = true
        encodeDefaults = true
    }

/**
 * Full-state backup: exports every persisted row (ids and FKs intact) plus AppSettings as a
 * single versioned JSON document, and restores it destructively so a new phone is an exact
 * clone of the old one. Reads through the DAOs (never a raw .db copy) so uncheckpointed WAL
 * data is included. See FEAT-00.
 */
class BackupRepository(
    private val backupDao: BackupDao,
    private val eventDao: ChangeEventDao,
    private val supplyTypeDao: SupplyTypeDao,
    private val settingsRepository: SettingsRepository,
) {
    /** Serializes all supply types, all raw events, and the current settings to pretty JSON. */
    suspend fun exportBackup(): String {
        val envelope =
            BackupEnvelope(
                formatVersion = BACKUP_FORMAT_VERSION,
                schemaVersion = OstomateDatabase.SCHEMA_VERSION,
                exportedAt = currentTimeMillis(),
                settings = settingsRepository.settings.first().toDto(),
                supplyTypes = supplyTypeDao.getAll().map { it.toDto() },
                events = eventDao.getAllRaw().map { it.toDto() },
            )
        return backupJson.encodeToString(envelope)
    }

    /**
     * REPLACE restore. Validates the entire document in memory first; only if it is valid does
     * it wipe and re-insert in a single DB transaction. On any failure the existing data is
     * left untouched — never wipe first and fail second.
     */
    suspend fun restoreBackup(json: String): RestoreResult {
        if (json.length > MAX_BACKUP_CHARS) return RestoreResult.Failure(RestoreError.OVERSIZED)

        val envelope =
            runCatching { backupJson.decodeFromString<BackupEnvelope>(json) }
                .getOrElse { return RestoreResult.Failure(RestoreError.MALFORMED) }

        if (envelope.formatVersion != BACKUP_FORMAT_VERSION) {
            return RestoreResult.Failure(RestoreError.UNSUPPORTED_FORMAT_VERSION)
        }
        if (envelope.schemaVersion != OstomateDatabase.SCHEMA_VERSION) {
            return RestoreResult.Failure(RestoreError.UNSUPPORTED_SCHEMA_VERSION)
        }

        // Map (and thereby validate enums) fully before touching the DB.
        val supplies =
            runCatching { envelope.supplyTypes.map { it.toEntity() } }
                .getOrElse { return RestoreResult.Failure(RestoreError.MALFORMED) }
        val events =
            runCatching { envelope.events.map { it.toEntity() } }
                .getOrElse { return RestoreResult.Failure(RestoreError.MALFORMED) }
        val settings =
            runCatching { envelope.settings.toAppSettings() }
                .getOrElse { return RestoreResult.Failure(RestoreError.MALFORMED) }

        backupDao.replaceAll(supplies, events)
        // Settings live in DataStore, a separate store from the SQLite DB, so they are written
        // after the DB transaction commits rather than inside it.
        settingsRepository.restore(settings)

        return RestoreResult.Success(supplyTypes = supplies.size, events = events.size)
    }
}
