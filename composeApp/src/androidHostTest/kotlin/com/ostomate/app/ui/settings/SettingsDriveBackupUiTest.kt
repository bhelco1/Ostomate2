package com.ostomate.app.ui.settings

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import androidx.test.core.app.ApplicationProvider
import com.ostomate.app.data.BackupRepository
import com.ostomate.app.data.diagnostics.DiagnosticLog
import com.ostomate.app.data.diagnostics.InMemoryDiagnosticLogStore
import com.ostomate.app.data.settings.SettingsRepository
import com.ostomate.app.platform.FeedbackHelper
import com.ostomate.app.platform.FileSharer
import com.ostomate.app.ui.FakeBackupDao
import com.ostomate.app.ui.FakeDriveBackupPlatform
import com.ostomate.app.ui.InMemoryDataStore
import com.ostomate.app.ui.RecordingCrashReporter
import com.ostomate.app.ui.screenshot.ScreenshotTest
import com.ostomate.app.ui.theme.OstomateTheme
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.assertEquals

/**
 * Where the daily Drive backup shows up in Settings: the switch only in dev mode, the status
 * line whenever the feature is on, and neither where the platform has no Drive backup (iOS).
 * Extends [ScreenshotTest] for its Robolectric setup and fake DAOs only; nothing is captured.
 */
@OptIn(ExperimentalTestApi::class)
class SettingsDriveBackupUiTest : ScreenshotTest() {
    private val settingsRepository = SettingsRepository(InMemoryDataStore())

    @Before
    fun startKoinForSettings() {
        startKoin {
            modules(
                module {
                    single { FileSharer() }
                    single { FeedbackHelper(ApplicationProvider.getApplicationContext()) }
                },
            )
        }
    }

    @After
    fun stopKoinAfter() = stopKoin()

    private fun viewModel(platform: FakeDriveBackupPlatform) =
        SettingsViewModel(
            settingsRepository = settingsRepository,
            backupRepository =
                BackupRepository(FakeBackupDao(supplyDao, eventDao), eventDao, supplyDao, settingsRepository),
            crashReporter = RecordingCrashReporter(),
            diagnosticLog = DiagnosticLog(InMemoryDiagnosticLogStore()),
            driveBackupPlatform = platform,
        )

    @Test
    fun switchIsHiddenUntilDevModeAndStatusIsHiddenWhileOff() {
        val vm = viewModel(FakeDriveBackupPlatform())
        runComposeUiTest {
            setContent { OstomateTheme { SettingsScreen(viewModel = vm) } }
            onNodeWithTag("driveBackupSwitch").assertDoesNotExist()
            onNodeWithTag("driveBackupStatus").assertDoesNotExist()
        }
    }

    @Test
    fun devModeShowsALabelledSwitchThatAuthorizesThenTurnsOn() {
        seed { settingsRepository.setDevMode(true) }
        val platform = FakeDriveBackupPlatform()
        val vm = viewModel(platform)
        runComposeUiTest {
            setContent { OstomateTheme { SettingsScreen(viewModel = vm) } }
            onNodeWithTag("driveBackupSwitch")
                .performScrollTo()
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
                .assertIsOff()
                .performClick()
            waitForIdle()

            onNodeWithTag("driveBackupSwitch").assertIsOn()
            onNodeWithTag("driveBackupStatus").assertExists()
            assertEquals(1, platform.interactiveCalls)
            assertEquals(1, platform.scheduled)
        }
    }

    @Test
    fun statusLineStaysWithDevModeOff() {
        seed {
            settingsRepository.setDriveBackupEnabled(true)
            settingsRepository.recordDriveBackupRun(atMillis = 0L, failure = null)
        }
        val vm = viewModel(FakeDriveBackupPlatform())
        runComposeUiTest {
            setContent { OstomateTheme { SettingsScreen(viewModel = vm) } }
            onNodeWithTag("driveBackupSwitch").assertDoesNotExist()
            onNodeWithTag("driveBackupStatus").assertExists()
        }
    }

    @Test
    fun nothingShowsWhereThePlatformHasNoDriveBackup() {
        seed {
            settingsRepository.setDevMode(true)
            settingsRepository.setDriveBackupEnabled(true)
        }
        val vm = viewModel(FakeDriveBackupPlatform(isSupported = false))
        runComposeUiTest {
            setContent { OstomateTheme { SettingsScreen(viewModel = vm) } }
            onNodeWithTag("driveBackupSwitch").assertDoesNotExist()
            onNodeWithTag("driveBackupStatus").assertDoesNotExist()
        }
    }
}
