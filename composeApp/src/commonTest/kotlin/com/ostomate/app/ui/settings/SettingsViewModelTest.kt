package com.ostomate.app.ui.settings

import com.ostomate.app.data.BackupRepository
import com.ostomate.app.data.ChangeEventRepository
import com.ostomate.app.data.settings.SettingsRepository
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsViewModelTest : MainDispatcherTest() {
    private val supplyDao = FakeSupplyTypeDao()
    private val eventDao = FakeChangeEventDao(supplyDao)
    private val settingsRepository = SettingsRepository(InMemoryDataStore())
    private val crashReporter = RecordingCrashReporter()

    private fun viewModel() =
        SettingsViewModel(settingsRepository, BackupRepository(eventDao, supplyDao), crashReporter)

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
    fun exportProducesTimestampedCsvOfTheHistory() =
        runTest {
            val (bagId) = supplyDao.seed(testSupply(name = "Bag"))
            ChangeEventRepository(eventDao, supplyDao).logChangeAt(bagId, 1_700_000_000_000L)

            val vm = viewModel()
            var exported: Pair<String, String>? = null
            vm.exportCsv { content, fileName -> exported = content to fileName }
            advanceUntilIdle()

            val (content, fileName) = exported!!
            assertTrue(content.lineSequence().first().startsWith("supply_id,supply_name,supply_kind"))
            assertTrue(content.contains("1700000000000"))
            assertTrue(fileName.startsWith("ostomate_backup_"))
            assertTrue(fileName.endsWith(".csv"))
            assertFalse(vm.backupState.value.isBusy)
        }

    @Test
    fun importRoundTripSkipsDuplicatesAndReportsSummary() =
        runTest {
            val (bagId) = supplyDao.seed(testSupply(name = "Bag"))
            val repository = ChangeEventRepository(eventDao, supplyDao)
            repository.logChangeAt(bagId, 1_700_000_000_000L)
            repository.logChangeAt(bagId, 1_700_086_400_000L)

            val vm = viewModel()
            var csv = ""
            vm.exportCsv { content, _ -> csv = content }
            advanceUntilIdle()

            // Re-importing the export must be idempotent: everything is a duplicate.
            vm.importCsv(csv)
            advanceUntilIdle()

            val summary = vm.backupState.value.lastImportSummary!!
            assertEquals(0, summary.inserted)
            assertEquals(2, summary.skipped)
            assertEquals(2, eventDao.count().toInt())

            vm.clearImportSummary()
            assertNull(vm.backupState.value.lastImportSummary)
        }
}
