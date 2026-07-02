package com.ostomate.app.ui.home

import com.ostomate.app.data.ChangeEventRepository
import com.ostomate.app.data.SupplyRepository
import com.ostomate.app.domain.NotificationScheduler
import com.ostomate.app.ui.FakeChangeEventDao
import com.ostomate.app.ui.FakeSupplyTypeDao
import com.ostomate.app.ui.MainDispatcherTest
import com.ostomate.app.ui.RecordingNotifier
import com.ostomate.app.ui.keepSubscribed
import com.ostomate.app.ui.testSupply
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val DAY_MS = 86_400_000L

class HomeViewModelTest : MainDispatcherTest() {
    private val supplyDao = FakeSupplyTypeDao()
    private val eventDao = FakeChangeEventDao(supplyDao)
    private val eventRepository = ChangeEventRepository(eventDao, supplyDao)
    private val notifier = RecordingNotifier()

    private fun viewModel() =
        HomeViewModel(
            eventRepository = eventRepository,
            supplyRepository = SupplyRepository(supplyDao),
            notificationScheduler = NotificationScheduler(notifier),
        )

    @Test
    fun suppliesAppearWithPredictionsFromHistory() =
        runTest {
            val (bagId) = supplyDao.seed(testSupply(name = "Bag", onHand = 10))
            val now = 1_700_000_000_000L
            eventRepository.logChangeAt(bagId, now - 2 * DAY_MS)
            eventRepository.logChangeAt(bagId, now - DAY_MS)
            eventRepository.logChangeAt(bagId, now)

            val vm = viewModel()
            keepSubscribed(vm.uiState)
            advanceUntilIdle()

            val row = vm.uiState.value.supplies.single()
            assertEquals("Bag", row.supply.name)
            assertEquals(3, row.sampleCount)
            // 3 changes a day apart → 1 day/unit; 7 on hand after the 3 logs.
            assertNotNull(row.daysRemaining)
            assertEquals(7.0, row.daysRemaining)
        }

    @Test
    fun supplyWithoutHistoryHasNoPrediction() =
        runTest {
            supplyDao.seed(testSupply(name = "Bag", onHand = 5))
            val vm = viewModel()
            keepSubscribed(vm.uiState)
            advanceUntilIdle()

            assertNull(vm.uiState.value.supplies.single().daysRemaining)
        }

    @Test
    fun logChangeDecrementsStockAndOffersUndo() =
        runTest {
            supplyDao.seed(testSupply(name = "Bag", onHand = 4))
            val vm = viewModel()
            keepSubscribed(vm.uiState)
            advanceUntilIdle()

            vm.logChange(vm.uiState.value.supplies.single().supply)
            advanceUntilIdle()

            val state = vm.uiState.value
            assertNotNull(state.pendingUndo)
            assertEquals("Bag", state.undoSupplyName)
            assertEquals(3, state.undoOnHand)
            assertEquals(3, supplyDao.getAll().single().onHand)
            assertEquals(1, eventDao.count().toInt())
        }

    @Test
    fun undoRemovesTheEventAndRestoresStock() =
        runTest {
            supplyDao.seed(testSupply(name = "Bag", onHand = 4))
            val vm = viewModel()
            keepSubscribed(vm.uiState)
            advanceUntilIdle()

            vm.logChange(vm.uiState.value.supplies.single().supply)
            advanceUntilIdle()
            vm.undoLog()
            advanceUntilIdle()

            assertNull(vm.uiState.value.pendingUndo)
            assertEquals(0, eventDao.count().toInt())
            assertEquals(4, supplyDao.getAll().single().onHand)
        }

    @Test
    fun clearUndoDismissesWithoutDeleting() =
        runTest {
            supplyDao.seed(testSupply(name = "Bag", onHand = 4))
            val vm = viewModel()
            keepSubscribed(vm.uiState)
            advanceUntilIdle()

            vm.logChange(vm.uiState.value.supplies.single().supply)
            advanceUntilIdle()
            vm.clearUndo()
            advanceUntilIdle()

            assertNull(vm.uiState.value.pendingUndo)
            assertEquals(1, eventDao.count().toInt())
        }

    @Test
    fun reorderRemindersAreRescheduledFromHistory() =
        runTest {
            val (bagId) = supplyDao.seed(testSupply(name = "Bag", onHand = 10, warnThresholdDays = 7))
            val now = 1_700_000_000_000L
            eventRepository.logChangeAt(bagId, now - DAY_MS)
            eventRepository.logChangeAt(bagId, now)

            val vm = viewModel()
            keepSubscribed(vm.uiState)
            advanceUntilIdle()

            assertTrue(notifier.scheduled.isNotEmpty())
            assertEquals("reorder-$bagId", notifier.scheduled.last().tag)
        }
}
