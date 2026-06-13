package com.ostimate.app.data.settings

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path.Companion.toPath
import platform.Foundation.NSTemporaryDirectory
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SettingsRepositoryTest {
    // Unique file per test: DataStore allows only one active instance per file.
    private val path = NSTemporaryDirectory() + "settings-test-${Random.nextLong()}.preferences_pb"
    private val repository = SettingsRepository(createSettingsDataStore { path })

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
}
