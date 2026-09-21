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
import kotlin.test.assertNotEquals
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
    fun averageUsesOnlyEventsInSelectedPeriod() =
        runTest {
            val (bagId) = supplyDao.seed(testSupply(name = "Bag"))
            val now = Clock.System.now().toEpochMilliseconds()
            // Week window holds two events 2 days apart; month adds one 12 days
            // earlier; year adds one 85 days before that. Each period therefore
            // has a distinct interval-based average: 2.0 / 7.0 / 33.0.
            eventRepository.logChangeAt(bagId, now - 100 * DAY_MS)
            eventRepository.logChangeAt(bagId, now - 15 * DAY_MS)
            eventRepository.logChangeAt(bagId, now - 3 * DAY_MS)
            eventRepository.logChangeAt(bagId, now - DAY_MS)

            val vm = viewModel()
            keepSubscribed(vm.uiState)

            vm.selectPeriod(StatsPeriod.WEEK)
            advanceUntilIdle()
            val week = vm.uiState.value.rows.single()
            assertEquals(2, week.countInPeriod)
            assertEquals(2.0, week.avgDaysBetween, "week average must only use the 2 events in the last 7 days")
            assertEquals("You change bag every 2 days on average.", vm.uiState.value.summaryLine)

            vm.selectPeriod(StatsPeriod.MONTH)
            advanceUntilIdle()
            val month = vm.uiState.value.rows.single()
            assertEquals(3, month.countInPeriod)
            assertEquals(7.0, month.avgDaysBetween, "month average must only use the 3 events in the last 30 days")
            assertEquals("You change bag every 7 days on average.", vm.uiState.value.summaryLine)

            vm.selectPeriod(StatsPeriod.YEAR)
            advanceUntilIdle()
            val year = vm.uiState.value.rows.single()
            assertEquals(4, year.countInPeriod)
            assertEquals(33.0, year.avgDaysBetween, "year average must use all 4 events in the last 365 days")
            assertEquals("You change bag every 33 days on average.", vm.uiState.value.summaryLine)
        }

    @Test
    fun switchingPeriodChangesTheAverage() =
        runTest {
            val (bagId) = supplyDao.seed(testSupply(name = "Bag"))
            val now = Clock.System.now().toEpochMilliseconds()
            eventRepository.logChangeAt(bagId, now - 60 * DAY_MS)
            eventRepository.logChangeAt(bagId, now - 2 * DAY_MS)
            eventRepository.logChangeAt(bagId, now)

            val vm = viewModel()
            keepSubscribed(vm.uiState)

            vm.selectPeriod(StatsPeriod.WEEK)
            advanceUntilIdle()
            val weekAvg = vm.uiState.value.rows.single().avgDaysBetween

            vm.selectPeriod(StatsPeriod.YEAR)
            advanceUntilIdle()
            val yearAvg = vm.uiState.value.rows.single().avgDaysBetween

            assertNotEquals(
                weekAvg,
                yearAvg,
                "week and year windows contain different events, so their averages must differ",
            )
        }

    @Test
    fun singleChangeInPeriodHasNoAverageEvenWithOlderHistory() =
        runTest {
            val (bagId) = supplyDao.seed(testSupply(name = "Bag"))
            val now = Clock.System.now().toEpochMilliseconds()
            eventRepository.logChangeAt(bagId, now - 60 * DAY_MS)
            eventRepository.logChangeAt(bagId, now - DAY_MS)

            val vm = viewModel()
            keepSubscribed(vm.uiState)
            vm.selectPeriod(StatsPeriod.WEEK)
            advanceUntilIdle()

            // The 60-day-old change must not leak in as a 59-day "average".
            val week = vm.uiState.value.rows.single()
            assertEquals(1, week.countInPeriod)
            assertNull(week.avgDaysBetween, "one change in the window is not a rhythm")
            assertNull(vm.uiState.value.summaryLine)

            vm.selectPeriod(StatsPeriod.YEAR)
            advanceUntilIdle()
            val year = vm.uiState.value.rows.single()
            assertEquals(2, year.countInPeriod)
            assertEquals(59.0, year.avgDaysBetween)
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
