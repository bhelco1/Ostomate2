package com.ostomate.app.domain.drive

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.number

/** Folder the daily backup writes to. With drive.file the app sees only what it created. */
const val DRIVE_BACKUP_FOLDER_NAME = "Ostomate backups"

/** How many daily backups stay in the folder; older ones the app created are deleted. */
const val DRIVE_BACKUPS_TO_KEEP = 14

/** Why the last daily run did not upload. Persisted by name, so never rename an entry. */
enum class DriveBackupFailure {
    /** Google needs the user to sign in or consent again; a background run cannot ask. */
    NEEDS_SIGN_IN,
    NETWORK,

    /** Drive answered with an error status. */
    DRIVE_ERROR,
    OTHER,
}

/** A file in the backup folder, as Drive lists it. */
data class DriveFile(
    val id: String,
    val name: String,
    /** Null when Drive's createdTime was missing or unreadable. */
    val createdAtMillis: Long?,
)

/** What the regular Settings screen says about the daily backup; null when it is off. */
sealed interface DriveBackupStatus {
    data object NotRunYet : DriveBackupStatus

    data class Succeeded(val atMillis: Long) : DriveBackupStatus

    data class Failed(val atMillis: Long, val reason: DriveBackupFailure) : DriveBackupStatus
}

/** Same name as a manual export (`ostomate_backup_2026-03-15_09-05-07.json`), local time. */
fun backupFileName(at: LocalDateTime): String {
    fun two(value: Int) = value.toString().padStart(2, '0')
    val date = "${at.year}-${two(at.month.number)}-${two(at.day)}"
    return "ostomate_backup_${date}_${two(at.hour)}-${two(at.minute)}-${two(at.second)}.json"
}

private val BACKUP_FILE_NAME = Regex("""ostomate_backup_\d{4}-\d{2}-\d{2}_\d{2}-\d{2}-\d{2}\.json""")

fun isBackupFileName(name: String): Boolean = BACKUP_FILE_NAME.matches(name)

/**
 * The backups to delete so that only the newest [keep] remain, newest by creation time.
 * Only files named like a backup are considered, and a file without a readable creation
 * time is never deleted: the app cannot tell how old it is.
 */
fun backupsToDelete(
    files: List<DriveFile>,
    keep: Int = DRIVE_BACKUPS_TO_KEEP,
): List<DriveFile> =
    files
        .filter { it.createdAtMillis != null && isBackupFileName(it.name) }
        .sortedWith(compareByDescending<DriveFile> { it.createdAtMillis }.thenByDescending { it.name })
        .drop(keep)

fun driveBackupStatus(
    enabled: Boolean,
    lastRunAtMillis: Long?,
    lastFailure: DriveBackupFailure?,
): DriveBackupStatus? =
    when {
        !enabled -> null
        lastRunAtMillis == null -> DriveBackupStatus.NotRunYet
        lastFailure == null -> DriveBackupStatus.Succeeded(lastRunAtMillis)
        else -> DriveBackupStatus.Failed(lastRunAtMillis, lastFailure)
    }

/** A 401 means the token is no good; anything else Drive refuses is Drive's error. */
fun failureForHttpStatus(status: Int): DriveBackupFailure =
    if (status == HTTP_UNAUTHORIZED) DriveBackupFailure.NEEDS_SIGN_IN else DriveBackupFailure.DRIVE_ERROR

private const val HTTP_UNAUTHORIZED = 401
private const val MAX_NETWORK_ATTEMPTS = 2

/**
 * Only a network failure is worth retrying within the same day, and only a few times;
 * anything else waits for tomorrow's run. [runAttemptCount] is WorkManager's, from 0.
 */
fun shouldRetry(
    failure: DriveBackupFailure?,
    runAttemptCount: Int,
): Boolean = failure == DriveBackupFailure.NETWORK && runAttemptCount < MAX_NETWORK_ATTEMPTS

/** A multipart/related boundary that does not occur in any of [parts]. */
fun multipartBoundary(vararg parts: String): String =
    generateSequence(0) { it + 1 }
        .map { "ostomate_backup_part_$it" }
        .first { candidate -> parts.none { it.contains(candidate) } }

/**
 * Body of a Drive multipart upload: the JSON metadata part, then the file content part.
 * Send it with `Content-Type: multipart/related; boundary=<boundary>`.
 */
fun multipartRelatedBody(
    boundary: String,
    metadataJson: String,
    contentType: String,
    content: String,
): String =
    "--$boundary\r\n" +
        "Content-Type: application/json; charset=UTF-8\r\n\r\n" +
        "$metadataJson\r\n" +
        "--$boundary\r\n" +
        "Content-Type: $contentType\r\n\r\n" +
        "$content\r\n" +
        "--$boundary--\r\n"
