package com.ostomate.app.ui.settings

import com.ostomate.app.data.SupplyRepository
import com.ostomate.app.data.settings.SettingsRepository
import com.ostomate.app.domain.SupplyKind
import com.ostomate.app.platform.BiometricResult
import com.ostomate.app.ui.FakeBiometricAuth
import com.ostomate.app.ui.FakeSupplyTypeDao
import com.ostomate.app.ui.InMemoryDataStore
import com.ostomate.app.ui.MainDispatcherTest
import com.ostomate.app.ui.keepSubscribed
import com.ostomate.app.ui.testSupply
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ManageSuppliesViewModelTest : MainDispatcherTest() {
    private val supplyDao = FakeSupplyTypeDao()
    private val settingsRepository = SettingsRepository(InMemoryDataStore())
    private val biometricAuth = FakeBiometricAuth()

    private fun viewModel() = ManageSuppliesViewModel(SupplyRepository(supplyDao), settingsRepository, biometricAuth)

    @Test
    fun archivedSuppliesAreHiddenFromTheList() =
        runTest {
            supplyDao.seed(
                testSupply(name = "Bag", sortOrder = 0),
                testSupply(name = "Old flange", kind = SupplyKind.FLANGE, sortOrder = 1, archived = true),
            )
            val vm = viewModel()
            keepSubscribed(vm.uiState)
            advanceUntilIdle()

            assertEquals(listOf("Bag"), vm.uiState.value.supplies.map { it.name })
        }

    @Test
    fun editCountOpensDirectlyWhenLockIsOff() =
        runTest {
            supplyDao.seed(testSupply(name = "Bag"))
            val vm = viewModel()
            keepSubscribed(vm.uiState)
            advanceUntilIdle()

            vm.requestEditCount(vm.uiState.value.supplies.single())
            advanceUntilIdle()

            assertEquals("Bag", vm.uiState.value.editCountTarget?.name)
            assertEquals(0, biometricAuth.promptCount)

            vm.dismissEditCount()
            advanceUntilIdle()
            assertNull(vm.uiState.value.editCountTarget)
        }

    @Test
    fun lockOnPromptsOnceThenSessionStaysUnlocked() =
        runTest {
            supplyDao.seed(testSupply(name = "Bag"))
            settingsRepository.setLockSettings(true)
            val vm = viewModel()
            keepSubscribed(vm.uiState)
            advanceUntilIdle()

            val bag = vm.uiState.value.supplies.single()
            vm.requestEditCount(bag)
            advanceUntilIdle()
            assertEquals(1, biometricAuth.promptCount)
            assertTrue(vm.uiState.value.sessionUnlocked)
            assertEquals("Bag", vm.uiState.value.editCountTarget?.name)

            vm.dismissEditCount()
            vm.requestEditCount(bag)
            advanceUntilIdle()
            assertEquals(1, biometricAuth.promptCount) // no re-prompt while unlocked

            vm.resetSession()
            advanceUntilIdle()
            assertFalse(vm.uiState.value.sessionUnlocked)
        }

    @Test
    fun failedBiometricBlocksTheEdit() =
        runTest {
            supplyDao.seed(testSupply(name = "Bag"))
            settingsRepository.setLockSettings(true)
            biometricAuth.nextResult = BiometricResult.Failed
            val vm = viewModel()
            keepSubscribed(vm.uiState)
            advanceUntilIdle()

            vm.requestEditCount(vm.uiState.value.supplies.single())
            advanceUntilIdle()

            assertNull(vm.uiState.value.editCountTarget)
            assertFalse(vm.uiState.value.sessionUnlocked)
        }

    @Test
    fun notEnrolledUnlocksGracefully() =
        runTest {
            supplyDao.seed(testSupply(name = "Bag"))
            settingsRepository.setLockSettings(true)
            biometricAuth.nextResult = BiometricResult.NotEnrolled
            val vm = viewModel()
            keepSubscribed(vm.uiState)
            advanceUntilIdle()

            vm.requestEditCount(vm.uiState.value.supplies.single())
            advanceUntilIdle()

            assertTrue(vm.uiState.value.sessionUnlocked)
            assertEquals("Bag", vm.uiState.value.editCountTarget?.name)
        }

    @Test
    fun setOnHandClampsNegativeToZero() =
        runTest {
            val (bagId) = supplyDao.seed(testSupply(name = "Bag", onHand = 5))
            val vm = viewModel()
            keepSubscribed(vm.uiState)
            advanceUntilIdle()

            vm.setOnHand(bagId, -3)
            advanceUntilIdle()
            assertEquals(0, supplyDao.getById(bagId)?.onHand)
        }

    @Test
    fun adjustOnHandAppliesDeltaAndClampsAtZero() =
        runTest {
            val (bagId) = supplyDao.seed(testSupply(name = "Bag", onHand = 1))
            val vm = viewModel()
            keepSubscribed(vm.uiState)
            advanceUntilIdle()

            vm.requestAdjustOnHand(bagId, currentOnHand = 1, delta = 10)
            advanceUntilIdle()
            assertEquals(11, supplyDao.getById(bagId)?.onHand)

            vm.requestAdjustOnHand(bagId, currentOnHand = 11, delta = -20)
            advanceUntilIdle()
            assertEquals(0, supplyDao.getById(bagId)?.onHand)
        }

    @Test
    fun saveSupplyDetailsSanitizesInput() =
        runTest {
            val (bagId) = supplyDao.seed(testSupply(name = "Bag", boxSize = 10, warnThresholdDays = 7))
            val vm = viewModel()
            keepSubscribed(vm.uiState)
            advanceUntilIdle()

            vm.saveSupplyDetails(bagId, name = "  Deluxe bag  ", boxSize = 0, warnThresholdDays = -2, colorIndex = 3)
            advanceUntilIdle()

            val saved = supplyDao.getById(bagId)!!
            assertEquals("Deluxe bag", saved.name)
            assertEquals(1, saved.boxSize)
            assertEquals(1, saved.warnThresholdDays)
            assertEquals(3, saved.colorIndex)

            vm.saveSupplyDetails(bagId, name = "   ", boxSize = 5, warnThresholdDays = 5, colorIndex = null)
            advanceUntilIdle()
            assertEquals("Deluxe bag", supplyDao.getById(bagId)?.name) // blank name keeps the old one
        }

    @Test
    fun archiveSupplyClosesTheEditSheet() =
        runTest {
            val (bagId) = supplyDao.seed(testSupply(name = "Bag"))
            val vm = viewModel()
            keepSubscribed(vm.uiState)
            advanceUntilIdle()

            vm.openEditSupply(vm.uiState.value.supplies.single())
            advanceUntilIdle()
            assertEquals("Bag", vm.uiState.value.editSupplyTarget?.name)

            vm.archiveSupply(bagId)
            advanceUntilIdle()
            assertNull(vm.uiState.value.editSupplyTarget)
            assertTrue(vm.uiState.value.supplies.isEmpty())
        }

    @Test
    fun addCustomSupplyAppendsAfterTheHighestSortOrder() =
        runTest {
            supplyDao.seed(testSupply(name = "Bag", sortOrder = 4))
            val vm = viewModel()
            keepSubscribed(vm.uiState)
            advanceUntilIdle()

            vm.showAddDialog()
            advanceUntilIdle()
            assertTrue(vm.uiState.value.showAddDialog)

            vm.addCustomSupply(name = " Barrier rings ", boxSize = 20, warnThresholdDays = 10, colorIndex = 2)
            advanceUntilIdle()

            assertFalse(vm.uiState.value.showAddDialog)
            val added = vm.uiState.value.supplies.first { it.kind == SupplyKind.CUSTOM }
            assertEquals("Barrier rings", added.name)
            assertEquals(5, added.sortOrder)
            assertEquals(0, added.onHand)
        }
}
