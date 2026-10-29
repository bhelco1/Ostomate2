package com.ostomate.app.ui.settings

import com.ostomate.app.data.BackupRepository
import com.ostomate.app.data.ChangeEventRepository
import com.ostomate.app.data.RestoreError
import com.ostomate.app.data.RestoreResult
import com.ostomate.app.data.diagnostics.DeepLinkEntryPoint
import com.ostomate.app.data.diagnostics.DiagnosticLog
import com.ostomate.app.data.diagnostics.InMemoryDiagnosticLogStore
import com.ostomate.app.data.diagnostics.ScanDecision
import com.ostomate.app.data.settings.SettingsRepository
import com.ostomate.app.domain.drive.DriveBackupFailure
import com.ostomate.app.domain.drive.DriveBackupStatus
import com.ostomate.app.platform.DriveAuthorization
import com.ostomate.app.ui.FakeBackupDao
import com.ostomate.app.ui.FakeChangeEventDao
import com.ostomate.app.ui.FakeDriveBackupPlatform
import com.ostomate.app.ui.FakeSupplyTypeDao
import com.ostomate.app.ui.InMemoryDataStore
import com.ostomate.app.ui.MainDispatcherTest
import com.ostomate.app.ui.RecordingCrashReporter
import com.ostomate.app.ui.keepSubscribed
import com.ostomate.app.ui.testSupply
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsViewModelTest : MainDispatcherTest() {
    private val supplyDao = FakeSupplyTypeDao()
    private val eventDao = FakeChangeEventDao(supplyDao)
    private val settingsRepository = SettingsRepository(InMemoryDataStore())
    private val crashReporter = RecordingCrashReporter()

    private val backupDao = FakeBackupDao(supplyDao, eventDao)
    private val diagnosticLog = DiagnosticLog(InMemoryDiagnosticLogStore())
    private val drive = FakeDriveBackupPlatform()

    private fun viewModel(platform: FakeDriveBackupPlatform = drive) =
        SettingsViewModel(
            settingsRepository,
            BackupRepository(backupDao, eventDao, supplyDao, settingsRepository),
            crashReporter,
            diagnosticLog,
            platform,
        )

    @Test
    fun togglesFlowThroughToTheSettingsState() =
        runTest {
            val vm = viewModel()
            keepSubscribed(vm.settings)
            advanceUntilIdle()
            assertFalse(vm.settings.value.lockSettings)

            vm.setLockSettings(true)
            vm.setDevMode(true)
            advanceUntilIdle()

            assertTrue(vm.settings.value.lockSettings)
            assertTrue(vm.settings.value.devMode)
        }

    @Test
    fun crashReportingToggleAlsoDrivesTheReporter() =
        runTest {
            val vm = viewModel()
            keepSubscribed(vm.settings)
            advanceUntilIdle()

            vm.setCrashReporting(true)
            advanceUntilIdle()
            assertTrue(vm.settings.value.crashReportingEnabled)
            assertEquals(listOf(true), crashReporter.setEnabledCalls)

            vm.setCrashReporting(false)
            advanceUntilIdle()
            assertFalse(vm.settings.value.crashReportingEnabled)
            assertEquals(listOf(true, false), crashReporter.setEnabledCalls)
        }

    @Test
    fun exportProducesTimestampedJsonBackup() =
        runTest {
            val (bagId) = supplyDao.seed(testSupply(name = "Bag"))
            ChangeEventRepository(eventDao, supplyDao).logChangeAt(bagId, 1_700_000_000_000L)

            val vm = viewModel()
            var exported: Pair<String, String>? = null
            vm.exportBackup { content, fileName -> exported = content to fileName }
            advanceUntilIdle()

            val (content, fileName) = assertNotNull(exported)
            assertTrue(content.contains("\"formatVersion\""))
            assertTrue(content.contains("1700000000000"))
            assertTrue(fileName.startsWith("ostomate_backup_"))
            assertTrue(fileName.endsWith(".json"))
            assertFalse(vm.backupState.value.isBusy)
        }

    @Test
    fun restoreRoundTripReportsSuccess() =
        runTest {
            val (bagId) = supplyDao.seed(testSupply(name = "Bag"))
            ChangeEventRepository(eventDao, supplyDao).logChangeAt(bagId, 1_700_000_000_000L)

            val vm = viewModel()
            var json = ""
            vm.exportBackup { content, _ -> json = content }
            advanceUntilIdle()

            vm.restoreBackup(json)
            advanceUntilIdle()

            val result = assertIs<RestoreResult.Success>(vm.backupState.value.lastRestore)
            assertEquals(1, result.supplyTypes)
            assertEquals(1, result.events)
            assertEquals(1, eventDao.count().toInt())

            vm.clearRestoreResult()
            assertNull(vm.backupState.value.lastRestore)
        }

    @Test
    fun exportDiagnosticLogProducesTimestampedLogFile() =
        runTest {
            diagnosticLog.recordScanAudit(
                uri = "ostomate://log?item=bag",
                entryPoint = DeepLinkEntryPoint.ANDROID_ON_CREATE,
                savedInstanceStateWasNull = true,
                decision = ScanDecision.LOGGED,
                eventId = 1L,
            )
            val vm = viewModel()

            var exported: Pair<String, String>? = null
            vm.exportDiagnosticLog { content, fileName -> exported = content to fileName }
            advanceUntilIdle()

            val (content, fileName) = assertNotNull(exported)
            assertTrue(content.contains("ostomate://log?item=bag"))
            assertTrue(content.contains("LOGGED"))
            assertTrue(fileName.startsWith("ostomate_diagnostics_"))
            assertTrue(fileName.endsWith(".log"))
        }

    @Test
    fun malformedRestoreReportsFailure() =
        runTest {
            val vm = viewModel()

            vm.restoreBackup("{ not valid json ")
            advanceUntilIdle()

            val result = assertIs<RestoreResult.Failure>(vm.backupState.value.lastRestore)
            assertEquals(RestoreError.MALFORMED, result.error)
        }

    @Test
    fun turningDriveBackupOnAuthorizesThenEnablesAndSchedules() =
        runTest {
            drive.interactiveAnswer = CompletableDeferred()
            val vm = viewModel()
            keepSubscribed(vm.driveBackup)
            advanceUntilIdle()
            assertTrue(vm.driveBackup.value.supported)
            assertFalse(vm.driveBackup.value.enabled)
            assertNull(vm.driveBackup.value.status)

            vm.setDriveBackupEnabled(true)
            advanceUntilIdle()
            // Consent screen is up: nothing is enabled or scheduled yet, and the switch is busy.
            assertTrue(vm.driveBackup.value.authorizing)
            assertFalse(vm.driveBackup.value.enabled)
            assertEquals(0, drive.scheduled)

            drive.interactiveAnswer.complete(DriveAuthorization.Authorized("tok"))
            advanceUntilIdle()

            assertEquals(1, drive.interactiveCalls)
            assertEquals(1, drive.scheduled)
            assertFalse(vm.driveBackup.value.authorizing)
            assertTrue(vm.driveBackup.value.enabled)
            assertNull(vm.driveBackup.value.enableError)
            assertEquals(DriveBackupStatus.NotRunYet, vm.driveBackup.value.status)
        }

    @Test
    fun aTapWhileConsentIsUpIsIgnored() =
        runTest {
            drive.interactiveAnswer = CompletableDeferred()
            val vm = viewModel()
            keepSubscribed(vm.driveBackup)

            vm.setDriveBackupEnabled(true)
            advanceUntilIdle()
            vm.setDriveBackupEnabled(true)
            vm.setDriveBackupEnabled(false)
            advanceUntilIdle()

            assertEquals(1, drive.interactiveCalls)
            assertEquals(0, drive.cancelled)
        }

    @Test
    fun cancellingConsentLeavesItOffWithAMessage() =
        runTest {
            drive.interactiveAnswer = CompletableDeferred(DriveAuthorization.Cancelled)
            val vm = viewModel()
            keepSubscribed(vm.driveBackup)

            vm.setDriveBackupEnabled(true)
            advanceUntilIdle()

            assertFalse(vm.driveBackup.value.enabled)
            assertFalse(vm.driveBackup.value.authorizing)
            assertEquals(DriveBackupEnableError.CANCELLED, vm.driveBackup.value.enableError)
            assertEquals(0, drive.scheduled)
            assertFalse(settingsRepository.driveBackup.first().enabled)
        }

    @Test
    fun anAuthorizationFailureLeavesItOffWithAMessageThatClearsOnRetry() =
        runTest {
            drive.interactiveAnswer = CompletableDeferred(DriveAuthorization.Failed)
            val vm = viewModel()
            keepSubscribed(vm.driveBackup)

            vm.setDriveBackupEnabled(true)
            advanceUntilIdle()
            assertEquals(DriveBackupEnableError.FAILED, vm.driveBackup.value.enableError)
            assertFalse(vm.driveBackup.value.enabled)

            drive.interactiveAnswer = CompletableDeferred(DriveAuthorization.NeedsUserInteraction)
            vm.setDriveBackupEnabled(true)
            advanceUntilIdle()
            assertEquals(DriveBackupEnableError.FAILED, vm.driveBackup.value.enableError)

            drive.interactiveAnswer = CompletableDeferred(DriveAuthorization.Authorized("tok"))
            vm.setDriveBackupEnabled(true)
            advanceUntilIdle()
            assertNull(vm.driveBackup.value.enableError)
            assertTrue(vm.driveBackup.value.enabled)
        }

    @Test
    fun turningDriveBackupOffCancelsTheJobAndDisables() =
        runTest {
            settingsRepository.setDriveBackupEnabled(true)
            val vm = viewModel()
            keepSubscribed(vm.driveBackup)
            advanceUntilIdle()
            assertTrue(vm.driveBackup.value.enabled)

            vm.setDriveBackupEnabled(false)
            advanceUntilIdle()

            assertEquals(1, drive.cancelled)
            assertEquals(0, drive.interactiveCalls)
            assertFalse(vm.driveBackup.value.enabled)
            assertNull(vm.driveBackup.value.status)
            assertFalse(settingsRepository.driveBackup.first().enabled)
        }

    @Test
    fun statusFollowsTheLastRecordedRun() =
        runTest {
            settingsRepository.setDriveBackupEnabled(true)
            val vm = viewModel()
            keepSubscribed(vm.driveBackup)

            settingsRepository.recordDriveBackupRun(atMillis = 100L, failure = null)
            advanceUntilIdle()
            assertEquals(DriveBackupStatus.Succeeded(100L), vm.driveBackup.value.status)

            settingsRepository.recordDriveBackupRun(atMillis = 200L, failure = DriveBackupFailure.NEEDS_SIGN_IN)
            advanceUntilIdle()
            assertEquals(DriveBackupStatus.Failed(200L, DriveBackupFailure.NEEDS_SIGN_IN), vm.driveBackup.value.status)
        }

    @Test
    fun withoutPlatformSupportTheFeatureCannotBeTurnedOn() =
        runTest {
            val unsupported = FakeDriveBackupPlatform(isSupported = false)
            val vm = viewModel(unsupported)
            keepSubscribed(vm.driveBackup)
            advanceUntilIdle()
            assertFalse(vm.driveBackup.value.supported)

            vm.setDriveBackupEnabled(true)
            advanceUntilIdle()

            assertEquals(0, unsupported.interactiveCalls)
            assertFalse(vm.driveBackup.value.enabled)
        }
}
