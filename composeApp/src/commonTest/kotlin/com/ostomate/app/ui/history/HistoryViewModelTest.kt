package com.ostomate.app.ui.history

import androidx.lifecycle.SavedStateHandle
import com.ostomate.app.data.ChangeEventRepository
import com.ostomate.app.data.SupplyRepository
import com.ostomate.app.domain.SupplyKind
import com.ostomate.app.ui.FakeChangeEventDao
import com.ostomate.app.ui.FakeSupplyTypeDao
import com.ostomate.app.ui.MainDispatcherTest
import com.ostomate.app.ui.keepSubscribed
import com.ostomate.app.ui.testSupply
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private const val DAY_MS = 86_400_000L

class HistoryViewModelTest : MainDispatcherTest() {
    private val supplyDao = FakeSupplyTypeDao()
    private val eventDao = FakeChangeEventDao(supplyDao)
    private val eventRepository = ChangeEventRepository(eventDao, supplyDao)

    private fun viewModel(savedState: SavedStateHandle = SavedStateHandle()) =
        HistoryViewModel(eventRepository, SupplyRepository(supplyDao), savedState)

    private suspend fun seedTwoSuppliesWithEvents(): Pair<Long, Long> {
        val (bagId, flangeId) =
            supplyDao.seed(
                testSupply(name = "Bag", kind = SupplyKind.BAG, sortOrder = 0),
                testSupply(name = "Flange", kind = SupplyKind.FLANGE, sortOrder = 1),
            )
        val now = 1_700_000_000_000L
        eventRepository.logChangeAt(bagId, now - DAY_MS)
        eventRepository.logChangeAt(flangeId, now)
        return bagId to flangeId
    }

    @Test
    fun defaultShowsAllEventsNewestFirst() =
        runTest {
            seedTwoSuppliesWithEvents()
            val vm = viewModel()
            keepSubscribed(vm.uiState)
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals("History", state.title)
            assertEquals(listOf("Flange", "Bag"), state.events.map { it.supplyName })
        }

    @Test
    fun navArgumentPreFiltersToOneSupply() =
        runTest {
            val (bagId, _) = seedTwoSuppliesWithEvents()
            val vm = viewModel(SavedStateHandle(mapOf("supplyId" to bagId)))
            keepSubscribed(vm.uiState)
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals("Bag history", state.title)
            assertEquals(listOf("Bag"), state.events.map { it.supplyName })
        }

    @Test
    fun setFilterSwitchesSupplyAndBackToAll() =
        runTest {
            val (_, flangeId) = seedTwoSuppliesWithEvents()
            val vm = viewModel()
            keepSubscribed(vm.uiState)
            advanceUntilIdle()

            vm.setFilter(flangeId)
            advanceUntilIdle()
            assertEquals("Flange history", vm.uiState.value.title)
            assertEquals(1, vm.uiState.value.events.size)

            vm.setFilter(-1L)
            advanceUntilIdle()
            assertEquals("History", vm.uiState.value.title)
            assertEquals(2, vm.uiState.value.events.size)
        }

    @Test
    fun deleteOffersUndoAndUndoRestoresTheEvent() =
        runTest {
            seedTwoSuppliesWithEvents()
            val vm = viewModel()
            keepSubscribed(vm.uiState)
            advanceUntilIdle()

            val target = vm.uiState.value.events.first().event
            vm.deleteEvent(target)
            advanceUntilIdle()
            assertEquals(target, vm.uiState.value.pendingUndo)
            assertEquals(1, vm.uiState.value.events.size)

            vm.undoDelete()
            advanceUntilIdle()
            assertNull(vm.uiState.value.pendingUndo)
            assertEquals(2, vm.uiState.value.events.size)
        }

    @Test
    fun updateEventStampsEditedAt() =
        runTest {
            seedTwoSuppliesWithEvents()
            val vm = viewModel()
            keepSubscribed(vm.uiState)
            advanceUntilIdle()

            val target = vm.uiState.value.events.first().event
            assertNull(target.editedAtMillis)
            vm.updateEvent(target.copy(note = "swapped brands"))
            advanceUntilIdle()

            val updated = vm.uiState.value.events.first { it.event.id == target.id }.event
            assertEquals("swapped brands", updated.note)
            assertEquals(true, updated.editedAtMillis != null)
        }
}
