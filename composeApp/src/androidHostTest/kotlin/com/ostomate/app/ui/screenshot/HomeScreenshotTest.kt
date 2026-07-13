package com.ostomate.app.ui.screenshot

import com.ostomate.app.domain.NotificationScheduler
import com.ostomate.app.domain.SupplyKind
import com.ostomate.app.ui.RecordingNotifier
import com.ostomate.app.ui.home.HomeScreen
import com.ostomate.app.ui.home.HomeViewModel
import com.ostomate.app.ui.testSupply
import org.junit.Test

class HomeScreenshotTest : ScreenshotTest() {
    private fun viewModel() =
        HomeViewModel(
            eventRepository = eventRepository,
            supplyRepository = supplyRepository,
            notificationScheduler = NotificationScheduler(RecordingNotifier()),
        )

    /** The everyday screen: two supplies with enough history for a days-remaining estimate. */
    @Test
    fun homeWithSupplies() {
        seed {
            val (bagId, flangeId) =
                supplyDao.seed(
                    testSupply(name = "Bag", kind = SupplyKind.BAG, onHand = 24, warnThresholdDays = 7),
                    testSupply(
                        name = "Flange",
                        kind = SupplyKind.FLANGE,
                        onHand = 12,
                        warnThresholdDays = 7,
                        sortOrder = 1,
                    ),
                )
            repeat(4) { i ->
                eventRepository.logChangeAt(bagId, FIXED_MILLIS - (4 - i) * 2 * DAY_MS)
            }
            repeat(3) { i ->
                eventRepository.logChangeAt(flangeId, FIXED_MILLIS - (3 - i) * 3 * DAY_MS)
            }
        }

        val vm = viewModel()
        awaitState(vm.uiState) { state -> state.supplies.size == 2 && state.supplies.all { it.daysRemaining != null } }

        capture("home_with_supplies") {
            HomeScreen(viewModel = vm, today = FIXED_TODAY)
        }
    }

    /** Low stock: days remaining below the warn threshold, so the card shows its warning row. */
    @Test
    fun homeLowStockWarning() {
        seed {
            val (bagId) =
                supplyDao.seed(
                    testSupply(name = "Bag", kind = SupplyKind.BAG, onHand = 2, warnThresholdDays = 7),
                )
            repeat(4) { i ->
                eventRepository.logChangeAt(bagId, FIXED_MILLIS - (4 - i) * DAY_MS)
            }
        }

        val vm = viewModel()
        awaitState(vm.uiState) { state -> state.supplies.singleOrNull()?.daysRemaining != null }

        capture("home_low_stock") {
            HomeScreen(viewModel = vm, today = FIXED_TODAY)
        }
    }

    /** First run before onboarding seeds anything — the empty-state copy must still lay out. */
    @Test
    fun homeEmpty() {
        val vm = viewModel()
        awaitState(vm.uiState) { it.supplies.isEmpty() }

        capture("home_empty") {
            HomeScreen(viewModel = vm, today = FIXED_TODAY)
        }
    }
}
