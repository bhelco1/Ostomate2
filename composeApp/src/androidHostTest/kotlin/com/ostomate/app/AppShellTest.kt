package com.ostomate.app

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.AndroidComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runAndroidComposeUiTest
import androidx.test.core.app.ApplicationProvider
import com.ostomate.app.data.BackupRepository
import com.ostomate.app.data.ChangeEventRepository
import com.ostomate.app.data.SupplyRepository
import com.ostomate.app.data.diagnostics.DiagnosticLog
import com.ostomate.app.data.diagnostics.InMemoryDiagnosticLogStore
import com.ostomate.app.data.drive.DriveBackupUploader
import com.ostomate.app.data.settings.SettingsRepository
import com.ostomate.app.di.uiModule
import com.ostomate.app.domain.NotificationScheduler
import com.ostomate.app.platform.BiometricAuth
import com.ostomate.app.platform.CrashReporting
import com.ostomate.app.platform.DriveBackupPlatform
import com.ostomate.app.platform.FeedbackHelper
import com.ostomate.app.platform.FileSharer
import com.ostomate.app.ui.FakeBackupDao
import com.ostomate.app.ui.FakeBiometricAuth
import com.ostomate.app.ui.FakeDriveBackupPlatform
import com.ostomate.app.ui.FakeDriveBackupUploader
import com.ostomate.app.ui.InMemoryDataStore
import com.ostomate.app.ui.RecordingCrashReporter
import com.ostomate.app.ui.RecordingNotifier
import com.ostomate.app.ui.screenshot.ScreenshotTest
import com.ostomate.app.ui.testSupply
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

/**
 * The real [App] shell — outer Scaffold, bottom bar, NavHost and every screen on Koin — over
 * the fake data layer, for what no single-screen test can cover: tab navigation.
 *
 * Extends [ScreenshotTest] for its Robolectric setup and fake DAOs only; nothing is captured.
 */
@OptIn(ExperimentalTestApi::class)
class AppShellTest : ScreenshotTest() {
    private val settingsRepository = SettingsRepository(InMemoryDataStore())

    @Before
    fun startKoinWithFakes() {
        seed {
            settingsRepository.setOnboardingDone(true)
            supplyDao.seed(testSupply(name = "Bag", onHand = 24))
        }
        startKoin {
            modules(
                uiModule,
                module {
                    single<SupplyRepository> { supplyRepository }
                    single<ChangeEventRepository> { eventRepository }
                    single { settingsRepository }
                    single { NotificationScheduler(RecordingNotifier()) }
                    single<BiometricAuth> { FakeBiometricAuth() }
                    single<CrashReporting> { RecordingCrashReporter() }
                    single { DiagnosticLog(InMemoryDiagnosticLogStore()) }
                    single<DriveBackupPlatform> { FakeDriveBackupPlatform() }
                    single<DriveBackupUploader> { FakeDriveBackupUploader() }
                    single {
                        BackupRepository(FakeBackupDao(supplyDao, eventDao), eventDao, supplyDao, settingsRepository)
                    }
                    single { FileSharer() }
                    single { FeedbackHelper(ApplicationProvider.getApplicationContext()) }
                },
            )
        }
    }

    @After
    fun stopKoinAfter() = stopKoin()

    @Test
    fun homeTabLeavesHistoryOpenedFromHome() =
        runAndroidComposeUiTest<ComponentActivity> {
            setContent { App() }
            openHistoryFromHome()

            onNodeWithTag("nav_home").performClick()
            waitForIdle()

            onNodeWithContentDescription(BACK).assertDoesNotExist()
            onNodeWithText(HOME_HEADER).assertExists()
        }

    @Test
    fun settingsTabLeavesManageSupplies() =
        runAndroidComposeUiTest<ComponentActivity> {
            setContent { App() }
            onNodeWithTag("nav_settings").performClick()
            onNodeWithText("Manage Supplies").performClick()
            waitForIdle()
            onNodeWithContentDescription(BACK).assertExists()

            onNodeWithTag("nav_settings").performClick()
            waitForIdle()

            onNodeWithContentDescription(BACK).assertDoesNotExist()
            onNodeWithText("Manage Supplies").assertExists()
        }

    @Test
    fun switchingTabsStillRestoresHistoryUntilHomeIsReselected() =
        runAndroidComposeUiTest<ComponentActivity> {
            setContent { App() }
            openHistoryFromHome()

            onNodeWithTag("nav_calendar").performClick()
            waitForIdle()
            onNodeWithContentDescription(BACK).assertDoesNotExist()

            // Coming back from another tab restores where Home was left: History.
            onNodeWithTag("nav_home").performClick()
            waitForIdle()
            onNodeWithContentDescription(BACK).assertExists()

            // Tapping Home again, from inside Home's section, goes to Home itself.
            onNodeWithTag("nav_home").performClick()
            waitForIdle()
            onNodeWithContentDescription(BACK).assertDoesNotExist()
            onNodeWithText(HOME_HEADER).assertExists()
        }

    private fun AndroidComposeUiTest<ComponentActivity>.openHistoryFromHome() {
        waitForIdle()
        onNodeWithTag("overflowButton").performClick()
        onNodeWithText("View history").performClick()
        waitForIdle()
        onNodeWithContentDescription(BACK).assertExists()
    }

    private companion object {
        const val BACK = "Back"
        const val HOME_HEADER = "Ostomate"
    }
}
