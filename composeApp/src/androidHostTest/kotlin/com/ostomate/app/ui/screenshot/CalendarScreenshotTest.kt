package com.ostomate.app.ui.screenshot

import com.ostomate.app.domain.SupplyKind
import com.ostomate.app.ui.calendar.CalendarScreen
import com.ostomate.app.ui.calendar.CalendarViewModel
import com.ostomate.app.ui.testSupply
import org.junit.Test

class CalendarScreenshotTest : ScreenshotTest() {
    private fun viewModel() = CalendarViewModel(eventRepository, supplyRepository, FixedClock)

    /**
     * March 2026 with change events scattered through the month, so the grid renders whole
     * weeks, the "today" marker (the 15th) and multi-pill days.
     */
    @Test
    fun calendarMonthWithEvents() {
        seed {
            val (bagId, flangeId) =
                supplyDao.seed(
                    testSupply(name = "Bag", kind = SupplyKind.BAG, onHand = 24),
                    testSupply(name = "Flange", kind = SupplyKind.FLANGE, onHand = 12, sortOrder = 1),
                )
            // Bag every 2 days for the first half of the month, flange every 5.
            listOf(2L, 4L, 6L, 8L, 10L, 12L, 14L).forEach { day ->
                eventRepository.logChangeAt(bagId, dayOfMarch(day))
            }
            listOf(3L, 8L, 13L).forEach { day ->
                eventRepository.logChangeAt(flangeId, dayOfMarch(day))
            }
            // Two changes on the same day → the pill shows a count.
            eventRepository.logChangeAt(bagId, dayOfMarch(10) + 3 * 60 * 60 * 1000L)
        }

        val vm = viewModel()
        awaitState(vm.uiState) { it.days.isNotEmpty() && it.days.any { day -> day.pills.isNotEmpty() } }

        capture("calendar_month") {
            CalendarScreen(viewModel = vm)
        }
    }

    /** A month with no history at all — the grid must still be a clean 6×7. */
    @Test
    fun calendarEmptyMonth() {
        seed { supplyDao.seed(testSupply(name = "Bag", kind = SupplyKind.BAG, onHand = 24)) }

        val vm = viewModel()
        awaitState(vm.uiState) { it.days.isNotEmpty() }

        capture("calendar_empty_month") {
            CalendarScreen(viewModel = vm)
        }
    }

    /** Noon on the given day of March 2026 (UTC — the JVM zone is pinned in [ScreenshotTest]). */
    private fun dayOfMarch(day: Long): Long = FIXED_MILLIS - (15L - day) * DAY_MS
}
