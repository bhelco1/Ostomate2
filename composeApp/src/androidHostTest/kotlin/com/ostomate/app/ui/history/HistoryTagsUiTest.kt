package com.ostomate.app.ui.history

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.lifecycle.SavedStateHandle
import com.ostomate.app.data.ChangeSource
import com.ostomate.app.data.db.ChangeEventEntity
import com.ostomate.app.ui.screenshot.DAY_MS
import com.ostomate.app.ui.screenshot.FIXED_MILLIS
import com.ostomate.app.ui.screenshot.ScreenshotTest
import com.ostomate.app.ui.testSupply
import com.ostomate.app.ui.theme.OstomateTheme
import org.junit.Test

/**
 * The "source:…" token that records how a change was logged (ChangeSource, PR #46) is an
 * audit trail, not a user tag: History never shows it (editing keeps it: ChangeSourceTest).
 * Extends [ScreenshotTest] for its Robolectric setup and fake DAOs only; nothing is captured.
 */
@OptIn(ExperimentalTestApi::class)
class HistoryTagsUiTest : ScreenshotTest() {
    @Test
    fun historyShowsUserTagsButNeverTheSourceToken() {
        var bagId = 0L
        seed {
            bagId = supplyDao.seed(testSupply(name = "Bag", onHand = 10)).first()
            eventRepository.logChangeAt(bagId, FIXED_MILLIS - DAY_MS, source = ChangeSource.QR)
            eventDao.insert(
                ChangeEventEntity(
                    supplyTypeId = bagId,
                    timestampMillis = FIXED_MILLIS,
                    createdAtMillis = FIXED_MILLIS,
                    tags = "source:manual,Leak",
                ),
            )
        }
        val vm = HistoryViewModel(eventRepository, supplyRepository, SavedStateHandle(mapOf("supplyId" to bagId)))
        awaitState(vm.uiState) { state -> state.events.size == 2 }

        runComposeUiTest {
            setContent { OstomateTheme { HistoryScreen(supplyId = bagId, onBack = {}, viewModel = vm) } }
            onNodeWithText("Leak").assertExists()
            onNodeWithText("source:qr", substring = true).assertDoesNotExist()
            onNodeWithText("source:manual", substring = true).assertDoesNotExist()
        }
    }
}
