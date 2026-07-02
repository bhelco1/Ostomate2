package com.ostomate.app.ui.onboarding

import com.ostomate.app.data.SupplyRepository
import com.ostomate.app.data.settings.SettingsRepository
import com.ostomate.app.domain.ApplianceType
import com.ostomate.app.domain.SupplyKind
import com.ostomate.app.ui.FakeSupplyTypeDao
import com.ostomate.app.ui.InMemoryDataStore
import com.ostomate.app.ui.MainDispatcherTest
import com.ostomate.app.ui.testSupply
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OnboardingViewModelTest : MainDispatcherTest() {
    private val supplyDao = FakeSupplyTypeDao()
    private val settingsRepository = SettingsRepository(InMemoryDataStore())

    private fun viewModel() = OnboardingViewModel(SupplyRepository(supplyDao), settingsRepository)

    @Test
    fun initialStateIsTwoPieceWithBagAndFlange() {
        val state = viewModel().uiState.value
        assertEquals(OnboardingStep.APPLIANCE_TYPE, state.step)
        assertEquals(ApplianceType.TWO_PIECE, state.applianceType)
        assertEquals(setOf(SupplyKind.BAG, SupplyKind.FLANGE), state.selectedKinds)
        assertEquals(4, state.totalSteps)
    }

    @Test
    fun onePieceSelectsBagOnlyAndShortensFlow() {
        val vm = viewModel()
        vm.setApplianceType(ApplianceType.ONE_PIECE)
        assertEquals(setOf(SupplyKind.BAG), vm.uiState.value.selectedKinds)
        assertEquals(3, vm.uiState.value.totalSteps)

        vm.nextStep()
        assertEquals(OnboardingStep.COUNTS, vm.uiState.value.step)
        vm.prevStep()
        assertEquals(OnboardingStep.APPLIANCE_TYPE, vm.uiState.value.step)
    }

    @Test
    fun twoPieceWalksAllFourStepsForwardAndBack() {
        val vm = viewModel()
        val forward = mutableListOf(vm.uiState.value.step)
        repeat(3) {
            vm.nextStep()
            forward += vm.uiState.value.step
        }
        assertEquals(
            listOf(
                OnboardingStep.APPLIANCE_TYPE,
                OnboardingStep.SUPPLIES,
                OnboardingStep.COUNTS,
                OnboardingStep.QR_EXPLAINER,
            ),
            forward,
        )
        vm.nextStep() // already at the last step: stays put
        assertEquals(OnboardingStep.QR_EXPLAINER, vm.uiState.value.step)

        vm.prevStep()
        assertEquals(OnboardingStep.COUNTS, vm.uiState.value.step)
        assertEquals(3, vm.uiState.value.displayStep)
    }

    @Test
    fun toggleKindAddsAndRemoves() {
        val vm = viewModel()
        vm.toggleKind(SupplyKind.FLANGE)
        assertEquals(setOf(SupplyKind.BAG), vm.uiState.value.selectedKinds)
        vm.toggleKind(SupplyKind.FLANGE)
        assertEquals(setOf(SupplyKind.BAG, SupplyKind.FLANGE), vm.uiState.value.selectedKinds)
    }

    // Regression for BUG-02: typing after the pre-filled 0 must not keep the leading zero.
    @Test
    fun countInputStripsLeadingZerosAndNonDigits() {
        val vm = viewModel()
        vm.setBagCount("0200")
        assertEquals("200", vm.uiState.value.bagCount)
        vm.setFlangeCount("07")
        assertEquals("7", vm.uiState.value.flangeCount)
        vm.setBagCount("1a2b3c")
        assertEquals("123", vm.uiState.value.bagCount)
        vm.setBagCount("123456")
        assertEquals("1234", vm.uiState.value.bagCount)
        vm.setBagCount("")
        assertEquals("", vm.uiState.value.bagCount)
    }

    @Test
    fun finishWritesCountsAndSettings() =
        runTest {
            supplyDao.seed(
                testSupply(name = "Bag", kind = SupplyKind.BAG, onHand = 0, sortOrder = 0),
                testSupply(name = "Flange", kind = SupplyKind.FLANGE, onHand = 0, sortOrder = 1),
            )
            val vm = viewModel()
            vm.setApplianceType(ApplianceType.TWO_PIECE)
            vm.setBagCount("12")
            vm.setFlangeCount("5")
            vm.finish()
            advanceUntilIdle()

            val supplies = supplyDao.getAll()
            assertEquals(12, supplies.first { it.kind == SupplyKind.BAG }.onHand)
            assertEquals(5, supplies.first { it.kind == SupplyKind.FLANGE }.onHand)

            val settings = settingsRepository.settings.first()
            assertTrue(settings.onboardingDone)
            assertEquals(ApplianceType.TWO_PIECE, settings.applianceType)
        }

    @Test
    fun skipMarksOnboardingDoneWithoutTouchingSupplies() =
        runTest {
            supplyDao.seed(testSupply(name = "Bag", kind = SupplyKind.BAG, onHand = 3))
            val vm = viewModel()
            vm.skip()
            advanceUntilIdle()

            assertTrue(settingsRepository.settings.first().onboardingDone)
            assertEquals(3, supplyDao.getAll().single().onHand)
        }
}
