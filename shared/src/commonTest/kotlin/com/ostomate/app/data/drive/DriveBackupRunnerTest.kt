package com.ostomate.app.data.drive

import com.ostomate.app.data.settings.DriveBackupState
import com.ostomate.app.data.settings.SettingsRepository
import com.ostomate.app.data.settings.createSettingsDataStore
import com.ostomate.app.domain.drive.DriveBackupFailure
import com.ostomate.app.platform.DriveAuthorization
import com.ostomate.app.platform.DriveBackupPlatform
import com.ostomate.app.platform.UnsupportedDriveBackupPlatform
import com.ostomate.app.testTempDir
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import okio.FileSystem
import okio.Path.Companion.toPath
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FakeDriveBackupPlatform(
    var silent: DriveAuthorization = DriveAuthorization.Authorized("tok"),
    var interactive: DriveAuthorization = DriveAuthorization.Authorized("tok"),
) : DriveBackupPlatform {
    override val isSupported = true
    var scheduled = 0
    var cancelled = 0
    var interactiveCalls = 0

    override suspend fun authorizeInteractively(): DriveAuthorization {
        interactiveCalls++
        return interactive
    }

    override suspend fun authorizeSilently(): DriveAuthorization = silent

    override fun scheduleDaily() {
        scheduled++
    }

    override fun cancelDaily() {
        cancelled++
    }
}

class DriveBackupRunnerTest {
    private val path =
        (testTempDir().toPath() / "drive-runner-${Random.nextLong()}.preferences_pb").toString()
    private val settings = SettingsRepository(createSettingsDataStore { path })
    private val platform = FakeDriveBackupPlatform()
    private val transport = ScriptedTransport()
    private var exported = BACKUP_JSON
    private var exportFails = false

    private val runner =
        DriveBackupRunner(
            settingsRepository = settings,
            platform = platform,
            client = DriveClient(transport),
            exportBackup = {
                check(!exportFails) { "database gone" }
                exported
            },
            clock = { NOW },
            timeZone = { TimeZone.UTC },
        )

    @AfterTest
    fun tearDown() {
        FileSystem.SYSTEM.delete(path.toPath(), mustExist = false)
    }

    @Test
    fun doesNothingWhileTheFeatureIsOff() =
        runTest {
            assertEquals(DriveBackupRunResult.Skipped, runner.run())
            assertTrue(transport.requests.isEmpty())
            assertEquals(DriveBackupState(), settings.driveBackup.first())
        }

    @Test
    fun createsTheFolderUploadsTheManualExportAndRecordsSuccess() =
        runTest {
            settings.setDriveBackupEnabled(true)
            transport.enqueue(
                HttpResponse(200, """{"files":[]}"""),
                HttpResponse(200, """{"id":"folder1"}"""),
                HttpResponse(200, """{"id":"new"}"""),
                HttpResponse(200, """{"files":[]}"""),
            )

            assertEquals(DriveBackupRunResult.Succeeded, runner.run())

            val (find, create, upload, list) = transport.requests
            assertTrue(find.url.contains("Ostomate%20backups"))
            assertEquals("""{"name":"Ostomate backups","mimeType":"application/vnd.google-apps.folder"}""", create.body)
            val body = upload.body.orEmpty()
            assertTrue(body.contains(""""name":"ostomate_backup_2026-03-15_12-00-05.json""""), body)
            assertTrue(body.contains(""""parents":["folder1"]"""), body)
            assertTrue(body.contains(BACKUP_JSON), body)
            assertTrue(list.url.contains("folder1"))
            assertEquals(4, transport.requests.size)
            assertEquals(DriveBackupState(enabled = true, lastRunAtMillis = NOW), settings.driveBackup.first())
        }

    @Test
    fun reusesTheExistingFolderAndPrunesToTheNewestFourteen() =
        runTest {
            settings.setDriveBackupEnabled(true)
            val listed =
                (1..16).joinToString(",") { day ->
                    val dd = day.toString().padStart(2, '0')
                    """{"id":"f$day","name":"ostomate_backup_2026-03-${dd}_00-00-00.json",""" +
                        """"createdTime":"2026-03-${dd}T00:00:00Z"}"""
                }
            transport.enqueue(
                HttpResponse(200, """{"files":[{"id":"folder1","name":"Ostomate backups"}]}"""),
                HttpResponse(200, """{"id":"f16"}"""),
                HttpResponse(200, """{"files":[$listed]}"""),
                HttpResponse(204, ""),
                HttpResponse(204, ""),
            )

            assertEquals(DriveBackupRunResult.Succeeded, runner.run())

            val deletes = transport.requests.filter { it.method == "DELETE" }.map { it.url.substringAfterLast('/') }
            assertEquals(listOf("f2", "f1"), deletes)
            assertTrue(transport.requests.none { it.body?.contains("vnd.google-apps.folder") == true })
        }

    @Test
    fun aFailedPruneDoesNotFailAnUploadedBackup() =
        runTest {
            settings.setDriveBackupEnabled(true)
            val listed =
                (1..15).joinToString(",") { day ->
                    val dd = day.toString().padStart(2, '0')
                    """{"id":"f$day","name":"ostomate_backup_2026-03-${dd}_00-00-00.json",""" +
                        """"createdTime":"2026-03-${dd}T00:00:00Z"}"""
                }
            transport.enqueue(
                HttpResponse(200, """{"files":[{"id":"folder1","name":"Ostomate backups"}]}"""),
                HttpResponse(200, """{"id":"f15"}"""),
                HttpResponse(200, """{"files":[$listed]}"""),
                HttpResponse(500, "boom"),
            )

            assertEquals(DriveBackupRunResult.Succeeded, runner.run())
            assertEquals(null, settings.driveBackup.first().lastFailure)
        }

    @Test
    fun aBackgroundRunThatNeedsConsentFailsWithoutTouchingTheNetwork() =
        runTest {
            settings.setDriveBackupEnabled(true)
            platform.silent = DriveAuthorization.NeedsUserInteraction

            assertEquals(DriveBackupRunResult.Failed(DriveBackupFailure.NEEDS_SIGN_IN), runner.run())

            assertTrue(transport.requests.isEmpty())
            assertEquals(0, platform.interactiveCalls)
            assertEquals(
                DriveBackupState(enabled = true, lastRunAtMillis = NOW, lastFailure = DriveBackupFailure.NEEDS_SIGN_IN),
                settings.driveBackup.first(),
            )
        }

    @Test
    fun otherAuthorizationOutcomesMapToAFailure() =
        runTest {
            settings.setDriveBackupEnabled(true)

            platform.silent = DriveAuthorization.Cancelled
            assertEquals(DriveBackupRunResult.Failed(DriveBackupFailure.NEEDS_SIGN_IN), runner.run())

            platform.silent = DriveAuthorization.Failed
            assertEquals(DriveBackupRunResult.Failed(DriveBackupFailure.OTHER), runner.run())
            assertEquals(DriveBackupFailure.OTHER, settings.driveBackup.first().lastFailure)
        }

    @Test
    fun networkAndDriveErrorsAreRecordedNotThrown() =
        runTest {
            settings.setDriveBackupEnabled(true)
            val offline =
                DriveBackupRunner(
                    settings,
                    platform,
                    DriveClient { throw DriveNetworkException("offline") },
                    { "{}" },
                    { NOW },
                )
            assertEquals(DriveBackupRunResult.Failed(DriveBackupFailure.NETWORK), offline.run())
            assertEquals(DriveBackupFailure.NETWORK, settings.driveBackup.first().lastFailure)

            transport.enqueue(HttpResponse(401, ""))
            assertEquals(DriveBackupRunResult.Failed(DriveBackupFailure.NEEDS_SIGN_IN), runner.run())

            transport.enqueue(HttpResponse(403, ""))
            assertEquals(DriveBackupRunResult.Failed(DriveBackupFailure.DRIVE_ERROR), runner.run())
            assertEquals(DriveBackupFailure.DRIVE_ERROR, settings.driveBackup.first().lastFailure)
        }

    @Test
    fun anUnexpectedErrorIsRecordedAsOther() =
        runTest {
            settings.setDriveBackupEnabled(true)
            exportFails = true

            assertEquals(DriveBackupRunResult.Failed(DriveBackupFailure.OTHER), runner.run())

            assertTrue(transport.requests.isEmpty())
            assertEquals(DriveBackupFailure.OTHER, settings.driveBackup.first().lastFailure)
        }

    @Test
    fun aGarbledDriveResponseIsADriveError() =
        runTest {
            settings.setDriveBackupEnabled(true)
            transport.enqueue(HttpResponse(200, "<html>not json</html>"))

            assertEquals(DriveBackupRunResult.Failed(DriveBackupFailure.DRIVE_ERROR), runner.run())
        }

    @Test
    fun aSuccessAfterAFailureClearsIt() =
        runTest {
            settings.setDriveBackupEnabled(true)
            settings.recordDriveBackupRun(1L, DriveBackupFailure.NETWORK)
            transport.enqueue(
                HttpResponse(200, """{"files":[{"id":"folder1","name":"Ostomate backups"}]}"""),
                HttpResponse(200, """{"id":"x"}"""),
                HttpResponse(200, """{"files":[]}"""),
            )

            runner.run()

            assertEquals(DriveBackupState(enabled = true, lastRunAtMillis = NOW), settings.driveBackup.first())
        }

    @Test
    fun appStartSchedulesOnlyWhenEnabled() =
        runTest {
            ensureDriveBackupScheduled(settings, platform)
            assertEquals(0, platform.scheduled)

            settings.setDriveBackupEnabled(true)
            ensureDriveBackupScheduled(settings, platform)
            assertEquals(1, platform.scheduled)
            assertEquals(0, platform.cancelled)
        }

    @Test
    fun unsupportedPlatformNeverAuthorizes() =
        runTest {
            val p = UnsupportedDriveBackupPlatform
            assertFalse(p.isSupported)
            assertEquals(DriveAuthorization.Failed, p.authorizeInteractively())
            assertEquals(DriveAuthorization.Failed, p.authorizeSilently())
            p.scheduleDaily()
            p.cancelDaily()
        }

    @Test
    fun aManualUploadAuthorizesInteractivelyAndRecordsLikeADailyRun() =
        runTest {
            settings.setDriveBackupEnabled(true)
            platform.silent = DriveAuthorization.NeedsUserInteraction
            transport.enqueue(
                HttpResponse(200, """{"files":[{"id":"folder1","name":"Ostomate backups"}]}"""),
                HttpResponse(200, """{"id":"new"}"""),
                HttpResponse(200, """{"files":[]}"""),
            )

            assertEquals(DriveBackupRunResult.Succeeded, runner.uploadNow())

            assertEquals(1, platform.interactiveCalls)
            val upload = transport.requests[1]
            assertTrue(upload.body.orEmpty().contains(""""name":"ostomate_backup_2026-03-15_12-00-05.json""""))
            assertTrue(upload.body.orEmpty().contains(BACKUP_JSON))
            assertEquals(DriveBackupState(enabled = true, lastRunAtMillis = NOW), settings.driveBackup.first())
        }

    @Test
    fun aManualUploadCountsTowardTheNewestFourteen() =
        runTest {
            settings.setDriveBackupEnabled(true)
            val listed =
                (1..15).joinToString(",") { day ->
                    val dd = day.toString().padStart(2, '0')
                    """{"id":"f$day","name":"ostomate_backup_2026-03-${dd}_00-00-00.json",""" +
                        """"createdTime":"2026-03-${dd}T00:00:00Z"}"""
                }
            transport.enqueue(
                HttpResponse(200, """{"files":[{"id":"folder1","name":"Ostomate backups"}]}"""),
                HttpResponse(200, """{"id":"f15"}"""),
                HttpResponse(200, """{"files":[$listed]}"""),
                HttpResponse(204, ""),
            )

            assertEquals(DriveBackupRunResult.Succeeded, runner.uploadNow())

            assertEquals(
                listOf("f1"),
                transport.requests.filter { it.method == "DELETE" }.map { it.url.substringAfterLast('/') },
            )
        }

    @Test
    fun aManualUploadWhoseConsentIsCancelledFailsAndIsRecorded() =
        runTest {
            settings.setDriveBackupEnabled(true)
            platform.interactive = DriveAuthorization.Cancelled

            assertEquals(DriveBackupRunResult.Failed(DriveBackupFailure.NEEDS_SIGN_IN), runner.uploadNow())

            assertTrue(transport.requests.isEmpty())
            assertEquals(DriveBackupFailure.NEEDS_SIGN_IN, settings.driveBackup.first().lastFailure)
            assertEquals(NOW, settings.driveBackup.first().lastRunAtMillis)
        }

    @Test
    fun theUnsupportedUploaderNeverUploads() =
        runTest {
            assertEquals(
                DriveBackupRunResult.Failed(DriveBackupFailure.OTHER),
                UnsupportedDriveBackupUploader.uploadNow(),
            )
        }

    private companion object {
        // 2026-03-15T12:00:05Z
        const val NOW = 1_773_576_005_000L
        const val BACKUP_JSON = """{"formatVersion":1,"marker":"exported"}"""
    }
}
