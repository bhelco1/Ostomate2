package com.ostomate.app.data.drive

import com.ostomate.app.data.settings.SettingsRepository
import com.ostomate.app.domain.drive.DRIVE_BACKUP_FOLDER_NAME
import com.ostomate.app.domain.drive.DriveBackupFailure
import com.ostomate.app.domain.drive.backupFileName
import com.ostomate.app.domain.drive.backupsToDelete
import com.ostomate.app.domain.drive.failureForHttpStatus
import com.ostomate.app.platform.DriveAuthorization
import com.ostomate.app.platform.DriveBackupPlatform
import com.ostomate.app.platform.currentTimeMillis
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.SerializationException
import kotlin.time.Instant

sealed interface DriveBackupRunResult {
    /** The feature is off; nothing was sent or recorded. */
    data object Skipped : DriveBackupRunResult

    data object Succeeded : DriveBackupRunResult

    data class Failed(val reason: DriveBackupFailure) : DriveBackupRunResult
}

/**
 * One daily run: get a token without UI, upload the same JSON a manual export produces into
 * the "Ostomate backups" folder, prune to the newest 14, and record the outcome for the
 * Settings status line. Never throws for an expected failure, so the job cannot crash.
 */
class DriveBackupRunner(
    private val settingsRepository: SettingsRepository,
    private val platform: DriveBackupPlatform,
    private val client: DriveClient,
    private val exportBackup: suspend () -> String,
    private val clock: () -> Long = ::currentTimeMillis,
    private val timeZone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) {
    suspend fun run(): DriveBackupRunResult {
        if (!settingsRepository.driveBackup.first().enabled) return DriveBackupRunResult.Skipped
        val failure = attempt()
        settingsRepository.recordDriveBackupRun(atMillis = clock(), failure = failure)
        return if (failure == null) DriveBackupRunResult.Succeeded else DriveBackupRunResult.Failed(failure)
    }

    /**
     * Null on success. Anything the export or a Drive call throws must end as a recorded
     * failure, not a crash of the background job; cancellation is rethrown so WorkManager can
     * still stop the run.
     */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun attempt(): DriveBackupFailure? =
        try {
            when (val auth = platform.authorizeSilently()) {
                is DriveAuthorization.Authorized -> {
                    upload(auth.accessToken)
                    null
                }
                DriveAuthorization.NeedsUserInteraction,
                DriveAuthorization.Cancelled,
                -> DriveBackupFailure.NEEDS_SIGN_IN
                DriveAuthorization.Failed -> DriveBackupFailure.OTHER
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: DriveNetworkException) {
            DriveBackupFailure.NETWORK
        } catch (e: DriveApiException) {
            failureForHttpStatus(e.status)
        } catch (e: SerializationException) {
            DriveBackupFailure.DRIVE_ERROR
        } catch (e: Exception) {
            DriveBackupFailure.OTHER
        }

    private suspend fun upload(token: String) {
        val json = exportBackup()
        val folderId =
            client.findFolder(token, DRIVE_BACKUP_FOLDER_NAME)
                ?: client.createFolder(token, DRIVE_BACKUP_FOLDER_NAME)
        val now = Instant.fromEpochMilliseconds(clock()).toLocalDateTime(timeZone())
        client.uploadJson(token, folderId, backupFileName(now), json)
        prune(token, folderId)
    }

    /**
     * The backup is already uploaded, so a failed prune is not a failed run; the next run
     * lists the folder again and deletes whatever is still over the limit.
     */
    private suspend fun prune(
        token: String,
        folderId: String,
    ) {
        try {
            backupsToDelete(client.listFiles(token, folderId)).forEach { client.delete(token, it.id) }
        } catch (e: DriveApiException) {
            return
        } catch (e: DriveNetworkException) {
            return
        } catch (e: SerializationException) {
            return
        }
    }
}

/** Re-schedules the daily job at app start when the feature is on (scheduling keeps an existing job). */
suspend fun ensureDriveBackupScheduled(
    settingsRepository: SettingsRepository,
    platform: DriveBackupPlatform,
) {
    if (settingsRepository.driveBackup.first().enabled) platform.scheduleDaily()
}
