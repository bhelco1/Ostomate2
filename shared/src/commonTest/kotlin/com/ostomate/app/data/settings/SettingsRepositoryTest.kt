package com.ostomate.app.data.settings

import androidx.datastore.preferences.core.stringPreferencesKey
import com.ostomate.app.domain.drive.DriveBackupFailure
import com.ostomate.app.testTempDir
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path.Companion.toPath
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsRepositoryTest {
    // Unique file per test: DataStore allows only one active instance per file.
    private val path =
        (testTempDir().toPath() / "settings-test-${Random.nextLong()}.preferences_pb").toString()
    private val dataStore = createSettingsDataStore { path }
    private val repository = SettingsRepository(dataStore)

    @AfterTest
    fun tearDown() {
        FileSystem.SYSTEM.delete(path.toPath(), mustExist = false)
    }

    @Test
    fun defaultsMatchFreshInstall() =
        runTest {
            assertEquals(AppSettings(), repository.settings.first())
        }

    @Test
    fun settingsPersistAndRoundTrip() =
        runTest {
            repository.setDevMode(true)
            repository.setOnboardingDone(true)
            repository.setLockSettings(true)
            repository.setLocaleOverride("es")

            assertEquals(
                AppSettings(devMode = true, onboardingDone = true, lockSettings = true, localeOverride = "es"),
                repository.settings.first(),
            )
        }

    @Test
    fun clearingLocaleOverrideFallsBackToSystem() =
        runTest {
            repository.setLocaleOverride("es")
            repository.setLocaleOverride(null)
            assertNull(repository.settings.first().localeOverride)
        }

    @Test
    fun driveBackupIsOffAndNeverRunOnAFreshInstall() =
        runTest {
            assertEquals(DriveBackupState(), repository.driveBackup.first())
        }

    @Test
    fun driveBackupSwitchPersists() =
        runTest {
            repository.setDriveBackupEnabled(true)
            assertTrue(repository.driveBackup.first().enabled)

            repository.setDriveBackupEnabled(false)
            assertEquals(DriveBackupState(enabled = false), repository.driveBackup.first())
        }

    @Test
    fun aFailedRunIsRecordedAndASuccessfulOneClearsTheFailure() =
        runTest {
            repository.recordDriveBackupRun(atMillis = 100L, failure = DriveBackupFailure.NETWORK)
            assertEquals(
                DriveBackupState(lastRunAtMillis = 100L, lastFailure = DriveBackupFailure.NETWORK),
                repository.driveBackup.first(),
            )

            repository.recordDriveBackupRun(atMillis = 200L, failure = null)
            assertEquals(DriveBackupState(lastRunAtMillis = 200L), repository.driveBackup.first())
        }

    @Test
    fun restoringABackupLeavesThisDevicesDriveBackupAlone() =
        runTest {
            repository.setDriveBackupEnabled(true)
            repository.recordDriveBackupRun(atMillis = 100L, failure = DriveBackupFailure.NEEDS_SIGN_IN)

            repository.restore(AppSettings(devMode = true))

            assertEquals(
                DriveBackupState(
                    enabled = true,
                    lastRunAtMillis = 100L,
                    lastFailure = DriveBackupFailure.NEEDS_SIGN_IN,
                ),
                repository.driveBackup.first(),
            )
            assertEquals(AppSettings(devMode = true), repository.settings.first())
        }

    @Test
    fun anUnknownStoredFailureReadsAsOther() =
        runTest {
            repository.recordDriveBackupRun(atMillis = 1L, failure = DriveBackupFailure.NETWORK)
            dataStore.updateData { prefs ->
                prefs.toMutablePreferences().apply {
                    set(stringPreferencesKey("driveBackupLastFailure"), "GONE")
                }
            }

            assertEquals(DriveBackupFailure.OTHER, repository.driveBackup.first().lastFailure)
        }
}
