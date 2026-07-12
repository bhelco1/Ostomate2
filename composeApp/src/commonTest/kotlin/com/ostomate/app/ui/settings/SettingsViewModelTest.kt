package com.ostomate.app.ui.settings

import com.ostomate.app.data.BackupRepository
import com.ostomate.app.data.ChangeEventRepository
import com.ostomate.app.data.RestoreError
import com.ostomate.app.data.RestoreResult
import com.ostomate.app.data.settings.SettingsRepository
import com.ostomate.app.ui.FakeBackupDao
import com.ostomate.app.ui.FakeChangeEventDao
import com.ostomate.app.ui.FakeSupplyTypeDao
import com.ostomate.app.ui.InMemoryDataStore
import com.ostomate.app.ui.MainDispatcherTest
import com.ostomate.app.ui.RecordingCrashReporter
import com.ostomate.app.ui.keepSubscribed
import com.ostomate.app.ui.testSupply
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

    private fun viewModel() =
        SettingsViewModel(
            settingsRepository,
            BackupRepository(backupDao, eventDao, supplyDao, settingsRepository),
            crashReporter,
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
    fun malformedRestoreReportsFailure() =
        runTest {
            val vm = viewModel()

            vm.restoreBackup("{ not valid json ")
            advanceUntilIdle()

            val result = assertIs<RestoreResult.Failure>(vm.backupState.value.lastRestore)
            assertEquals(RestoreError.MALFORMED, result.error)
        }
}
