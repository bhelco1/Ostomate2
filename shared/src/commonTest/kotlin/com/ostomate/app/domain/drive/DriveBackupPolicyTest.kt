package com.ostomate.app.domain.drive

import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DriveBackupPolicyTest {
    @Test
    fun fileNameMatchesTheManualExportAndZeroPadsEveryField() {
        assertEquals(
            "ostomate_backup_2026-03-05_09-04-07.json",
            backupFileName(LocalDateTime(2026, 3, 5, 9, 4, 7)),
        )
        assertEquals(
            "ostomate_backup_2026-12-25_23-59-58.json",
            backupFileName(LocalDateTime(2026, 12, 25, 23, 59, 58)),
        )
    }

    @Test
    fun recognisesOnlyBackupFileNames() {
        assertTrue(isBackupFileName("ostomate_backup_2026-03-05_09-04-07.json"))
        assertFalse(isBackupFileName("ostomate_backup_2026-03-05_09-04-07.json.bak"))
        assertFalse(isBackupFileName("notes.json"))
        assertFalse(isBackupFileName("ostomate_backup_.json"))
        assertFalse(isBackupFileName("my ostomate_backup_2026-03-05_09-04-07.json"))
    }

    @Test
    fun keepsTheNewestFourteenAndDeletesTheRestOldestLast() {
        val files = (1..17).map { day -> backup("id$day", createdAt = day * DAY) }.shuffled()

        val doomed = backupsToDelete(files)

        assertEquals(listOf("id3", "id2", "id1"), doomed.map { it.id })
    }

    @Test
    fun deletesNothingAtOrBelowTheLimit() {
        assertEquals(emptyList(), backupsToDelete((1..14).map { backup("id$it", it * DAY) }))
        assertEquals(emptyList(), backupsToDelete(listOf(backup("only", DAY))))
        assertEquals(emptyList(), backupsToDelete(emptyList()))
    }

    @Test
    fun respectsACustomLimit() {
        val files = (1..4).map { backup("id$it", it * DAY) }
        assertEquals(listOf("id2", "id1"), backupsToDelete(files, keep = 2).map { it.id })
        assertEquals(listOf("id4", "id3", "id2", "id1"), backupsToDelete(files, keep = 0).map { it.id })
    }

    @Test
    fun neverDeletesFilesItDidNotNameOrCannotDate() {
        val backups = (1..15).map { backup("id$it", it * DAY) }
        val foreign = DriveFile("foreign", "photo.jpg", createdAtMillis = 0L)
        val undated = DriveFile("undated", "ostomate_backup_2020-01-01_00-00-00.json", createdAtMillis = null)

        val doomed = backupsToDelete(backups + foreign + undated)

        assertEquals(listOf("id1"), doomed.map { it.id })
    }

    @Test
    fun equalCreationTimesBreakTiesByNameSoTheChoiceIsStable() {
        val a = DriveFile("a", "ostomate_backup_2026-01-01_00-00-00.json", createdAtMillis = DAY)
        val b = DriveFile("b", "ostomate_backup_2026-01-02_00-00-00.json", createdAtMillis = DAY)

        assertEquals(listOf("a"), backupsToDelete(listOf(a, b), keep = 1).map { it.id })
        assertEquals(listOf("a"), backupsToDelete(listOf(b, a), keep = 1).map { it.id })
    }

    @Test
    fun statusIsHiddenWhileTheFeatureIsOff() {
        assertNull(driveBackupStatus(enabled = false, lastRunAtMillis = null, lastFailure = null))
        assertNull(driveBackupStatus(enabled = false, lastRunAtMillis = 5L, lastFailure = DriveBackupFailure.NETWORK))
    }

    @Test
    fun statusReportsNeverRunSuccessAndFailure() {
        assertEquals(
            DriveBackupStatus.NotRunYet,
            driveBackupStatus(enabled = true, lastRunAtMillis = null, lastFailure = null),
        )
        assertEquals(
            DriveBackupStatus.Succeeded(42L),
            driveBackupStatus(enabled = true, lastRunAtMillis = 42L, lastFailure = null),
        )
        assertEquals(
            DriveBackupStatus.Failed(42L, DriveBackupFailure.NEEDS_SIGN_IN),
            driveBackupStatus(enabled = true, lastRunAtMillis = 42L, lastFailure = DriveBackupFailure.NEEDS_SIGN_IN),
        )
        // The screen reads the time through the accessor, not through equals().
        assertEquals(7L, DriveBackupStatus.Succeeded(7L).atMillis)
        assertEquals(9L, DriveBackupStatus.Failed(9L, DriveBackupFailure.NETWORK).atMillis)
    }

    @Test
    fun onlyUnauthorizedMeansSignInAgain() {
        assertEquals(DriveBackupFailure.NEEDS_SIGN_IN, failureForHttpStatus(401))
        assertEquals(DriveBackupFailure.DRIVE_ERROR, failureForHttpStatus(400))
        assertEquals(DriveBackupFailure.DRIVE_ERROR, failureForHttpStatus(403))
        assertEquals(DriveBackupFailure.DRIVE_ERROR, failureForHttpStatus(500))
    }

    @Test
    fun retriesOnlyNetworkFailuresAndOnlyTwice() {
        assertTrue(shouldRetry(DriveBackupFailure.NETWORK, runAttemptCount = 0))
        assertTrue(shouldRetry(DriveBackupFailure.NETWORK, runAttemptCount = 1))
        assertFalse(shouldRetry(DriveBackupFailure.NETWORK, runAttemptCount = 2))
        assertFalse(shouldRetry(null, runAttemptCount = 0))
        assertFalse(shouldRetry(DriveBackupFailure.NEEDS_SIGN_IN, runAttemptCount = 0))
        assertFalse(shouldRetry(DriveBackupFailure.DRIVE_ERROR, runAttemptCount = 0))
        assertFalse(shouldRetry(DriveBackupFailure.OTHER, runAttemptCount = 0))
    }

    @Test
    fun boundaryAvoidsEveryPart() {
        val plain = multipartBoundary("{}", "content")
        assertEquals("ostomate_backup_part_0", plain)

        val clash = multipartBoundary("{}", "x ostomate_backup_part_0 ostomate_backup_part_1 y")
        assertEquals("ostomate_backup_part_2", clash)

        val clashInFirstPart = multipartBoundary("ostomate_backup_part_0", "content")
        assertEquals("ostomate_backup_part_1", clashInFirstPart)
    }

    @Test
    fun multipartBodyHasMetadataThenContentWithCrlfLines() {
        val body =
            multipartRelatedBody(
                boundary = "b0",
                metadataJson = """{"name":"f.json"}""",
                contentType = "application/json",
                content = """{"a":1}""",
            )

        assertEquals(
            "--b0\r\n" +
                "Content-Type: application/json; charset=UTF-8\r\n" +
                "\r\n" +
                "{\"name\":\"f.json\"}\r\n" +
                "--b0\r\n" +
                "Content-Type: application/json\r\n" +
                "\r\n" +
                "{\"a\":1}\r\n" +
                "--b0--\r\n",
            body,
        )
    }

    private fun backup(
        id: String,
        createdAt: Long,
    ) = DriveFile(id, "ostomate_backup_2026-01-01_00-00-00.json", createdAtMillis = createdAt)

    private companion object {
        const val DAY = 86_400_000L
    }
}
