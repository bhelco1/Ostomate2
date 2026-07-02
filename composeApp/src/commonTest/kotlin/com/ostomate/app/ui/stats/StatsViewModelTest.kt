package com.ostomate.app.ui.stats

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
import kotlin.time.Clock

private const val DAY_MS = 86_400_000L

class StatsViewModelTest : MainDispatcherTest() {
    private val supplyDao = FakeSupplyTypeDao()
    private val eventDao = FakeChangeEventDao(supplyDao)
    private val eventRepository = ChangeEventRepository(eventDao, supplyDao)

    private fun viewModel() = StatsViewModel(eventRepository, SupplyRepository(supplyDao))

    @Test
    fun rowsAggregateCountsAndAverages() =
        runTest {
            val (bagId) = supplyDao.seed(testSupply(name = "Bag", onHand = 10))
            val now = Clock.System.now().toEpochMilliseconds()
            eventRepository.logChangeAt(bagId, now - 4 * DAY_MS)
            eventRepository.logChangeAt(bagId, now - 2 * DAY_MS)
            eventRepository.logChangeAt(bagId, now)

            val vm = viewModel()
            keepSubscribed(vm.uiState)
            advanceUntilIdle()

            val row = vm.uiState.value.rows.single()
            assertEquals("Bag", row.supplyName)
            assertEquals(3, row.countInPeriod)
            assertEquals(2.0, row.avgDaysBetween)
            assertEquals("You change bag every 2 days on average.", vm.uiState.value.summaryLine)
        }

    @Test
    fun periodFiltersOutOlderEvents() =
        runTest {
            val (bagId) = supplyDao.seed(testSupply(name = "Bag"))
            val now = Clock.System.now().toEpochMilliseconds()
            eventRepository.logChangeAt(bagId, now - 10 * DAY_MS)
            eventRepository.logChangeAt(bagId, now - DAY_MS)

            val vm = viewModel()
            keepSubscribed(vm.uiState)
            advanceUntilIdle()
            assertEquals(StatsPeriod.MONTH, vm.uiState.value.period)
            assertEquals(2, vm.uiState.value.rows.single().countInPeriod)

            vm.selectPeriod(StatsPeriod.WEEK)
            advanceUntilIdle()
            assertEquals(StatsPeriod.WEEK, vm.uiState.value.period)
            assertEquals(1, vm.uiState.value.rows.single().countInPeriod)
        }

    @Test
    fun suppliesWithoutEventsAreHidden() =
        runTest {
            supplyDao.seed(
                testSupply(name = "Bag", kind = SupplyKind.BAG, sortOrder = 0),
                testSupply(name = "Flange", kind = SupplyKind.FLANGE, sortOrder = 1),
            )
            val bagId = supplyDao.getByKind(SupplyKind.BAG)!!.id
            eventRepository.logChangeAt(bagId, Clock.System.now().toEpochMilliseconds())

            val vm = viewModel()
            keepSubscribed(vm.uiState)
            advanceUntilIdle()

            assertEquals(listOf("Bag"), vm.uiState.value.rows.map { it.supplyName })
            // One event → no interval → no average → no summary claim about it.
            assertNull(vm.uiState.value.rows.single().avgDaysBetween)
            assertNull(vm.uiState.value.summaryLine)
        }

    @Test
    fun fractionalAveragesKeepOneDecimalInSummary() =
        runTest {
            val (bagId) = supplyDao.seed(testSupply(name = "Bag"))
            val now = Clock.System.now().toEpochMilliseconds()
            eventRepository.logChangeAt(bagId, now - 3 * DAY_MS)
            eventRepository.logChangeAt(bagId, now - 3 * DAY_MS / 2)
            eventRepository.logChangeAt(bagId, now)

            val vm = viewModel()
            keepSubscribed(vm.uiState)
            advanceUntilIdle()

            assertEquals("You change bag every 1.5 days on average.", vm.uiState.value.summaryLine)
        }
}
