@file:Suppress("DEPRECATION") // kotlinx-datetime 0.6.x deprecation — monthNumber/dayOfMonth (matches the VM)

package com.ostomate.app.ui.calendar

import com.ostomate.app.data.ChangeEventRepository
import com.ostomate.app.data.SupplyRepository
import com.ostomate.app.ui.FakeChangeEventDao
import com.ostomate.app.ui.FakeSupplyTypeDao
import com.ostomate.app.ui.MainDispatcherTest
import com.ostomate.app.ui.keepSubscribed
import com.ostomate.app.ui.testSupply
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

private val MONTH_NAMES =
    arrayOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
    )

class CalendarViewModelTest : MainDispatcherTest() {
    private val supplyDao = FakeSupplyTypeDao()
    private val eventDao = FakeChangeEventDao(supplyDao)
    private val eventRepository = ChangeEventRepository(eventDao, supplyDao)

    private val tz = TimeZone.currentSystemDefault()
    private val today = Clock.System.now().toLocalDateTime(tz).date

    private fun viewModel() = CalendarViewModel(eventRepository, SupplyRepository(supplyDao))

    private fun noonMillis(date: LocalDate): Long =
        LocalDateTime(date.year, date.monthNumber, date.dayOfMonth, 12, 0)
            .toInstant(tz)
            .toEpochMilliseconds()

    private fun label(date: LocalDate) = "${MONTH_NAMES[date.monthNumber - 1]} ${date.year}"

    @Test
    fun currentMonthGridIsWholeWeeksWithTodayMarked() =
        runTest {
            val vm = viewModel()
            keepSubscribed(vm.uiState)
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals(label(today), state.monthLabel)
            assertEquals(0, state.days.size % 7)
            val todayCell = state.days.single { it.isToday }
            assertEquals(today, todayCell.date)
            assertTrue(todayCell.isCurrentMonth)
        }

    @Test
    fun pillsAggregateTheDaysEventsPerSupply() =
        runTest {
            val (bagId) = supplyDao.seed(testSupply(name = "Bag"))
            eventRepository.logChangeAt(bagId, noonMillis(today))
            eventRepository.logChangeAt(bagId, noonMillis(today) + 60_000)

            val vm = viewModel()
            keepSubscribed(vm.uiState)
            advanceUntilIdle()

            val pill = vm.uiState.value.days.single { it.isToday }.pills.single()
            assertEquals("Bag", pill.supplyName)
            assertEquals(2, pill.count)
        }

    @Test
    fun selectingADayShowsItsEventsAndDismissClears() =
        runTest {
            val (bagId) = supplyDao.seed(testSupply(name = "Bag"))
            eventRepository.logChangeAt(bagId, noonMillis(today))

            val vm = viewModel()
            keepSubscribed(vm.uiState)
            advanceUntilIdle()

            vm.selectDay(today)
            advanceUntilIdle()
            val state = vm.uiState.value
            assertEquals(1, state.selectedDayEvents?.size)
            assertEquals(
                "${MONTH_NAMES[today.monthNumber - 1]} ${today.dayOfMonth}, ${today.year}",
                state.selectedDayLabel,
            )

            vm.dismissSheet()
            advanceUntilIdle()
            assertNull(vm.uiState.value.selectedDayEvents)
        }

    @Test
    fun monthNavigationMovesTheLabelBothWays() =
        runTest {
            val vm = viewModel()
            keepSubscribed(vm.uiState)
            advanceUntilIdle()

            vm.prevMonth()
            advanceUntilIdle()
            val firstOfMonth = LocalDate(today.year, today.monthNumber, 1)
            assertEquals(label(firstOfMonth.minus(1, DateTimeUnit.MONTH)), vm.uiState.value.monthLabel)

            vm.nextMonth()
            vm.nextMonth()
            advanceUntilIdle()
            assertEquals(label(firstOfMonth.plus(1, DateTimeUnit.MONTH)), vm.uiState.value.monthLabel)
        }

    @Test
    fun deleteOffersUndoAndUndoReinsertsTheEvent() =
        runTest {
            val (bagId) = supplyDao.seed(testSupply(name = "Bag", onHand = 5))
            eventRepository.logChangeAt(bagId, noonMillis(today))

            val vm = viewModel()
            keepSubscribed(vm.uiState)
            advanceUntilIdle()

            val event = eventDao.events.value.single()
            vm.deleteEvent(event)
            advanceUntilIdle()
            assertEquals(event, vm.uiState.value.pendingUndo)
            assertEquals(0, eventDao.count().toInt())

            vm.undoDelete()
            advanceUntilIdle()
            assertNull(vm.uiState.value.pendingUndo)
            assertEquals(1, eventDao.count().toInt())
        }

    @Test
    fun addEventForDateLogsAtNoon() =
        runTest {
            val (bagId) = supplyDao.seed(testSupply(name = "Bag", onHand = 5))
            val vm = viewModel()
            keepSubscribed(vm.uiState)
            advanceUntilIdle()

            vm.addEventForDate(bagId, today)
            advanceUntilIdle()

            assertEquals(noonMillis(today), eventDao.events.value.single().timestampMillis)
            assertEquals(4, supplyDao.getById(bagId)?.onHand)
        }
}
