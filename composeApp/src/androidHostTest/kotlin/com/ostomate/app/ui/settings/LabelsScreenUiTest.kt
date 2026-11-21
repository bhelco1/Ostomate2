package com.ostomate.app.ui.settings

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import com.ostomate.app.domain.SupplyKind
import com.ostomate.app.platform.NfcStatus
import com.ostomate.app.ui.FakeNfcStickerWriting
import com.ostomate.app.ui.screenshot.ScreenshotTest
import com.ostomate.app.ui.testSupply
import com.ostomate.app.ui.theme.OstomateTheme
import org.junit.Test

/**
 * One way to print codes, and none to share them (Bobby, 2026-11-20): the top bar's Print
 * makes the 25 mm label sheet; rows have no Print QR or Share QR, and the top bar no share
 * icon. Extends [ScreenshotTest] for its Robolectric setup only; nothing is captured.
 */
@OptIn(ExperimentalTestApi::class)
class LabelsScreenUiTest : ScreenshotTest() {
    @Test
    fun onlyTheTopBarPrintsAndNothingShares() {
        seed {
            supplyDao.seed(
                testSupply(id = 1, name = "Bag", kind = SupplyKind.BAG),
                testSupply(id = 2, name = "Flange", kind = SupplyKind.FLANGE, sortOrder = 1),
            )
        }
        val vm = LabelsViewModel(supplyRepository, FakeNfcStickerWriting(NfcStatus.ON))
        awaitState(vm.uiState) { it.items.size == 2 }

        runComposeUiTest {
            setContent { OstomateTheme { LabelsScreen(onBack = {}, viewModel = vm) } }

            // The rows are there (each has its sticker button), and "Print" appears once: the top bar.
            onNodeWithContentDescription("Write NFC sticker for Bag").assertExists()
            onNodeWithContentDescription("Write NFC sticker for Flange").assertExists()
            onAllNodesWithText("Print").assertCountEquals(1)
            onNodeWithText("Print QR", substring = true).assertDoesNotExist()
            onNodeWithContentDescription("Print QR code for", substring = true).assertDoesNotExist()
            onNodeWithText("Share", substring = true).assertDoesNotExist()
            onNodeWithContentDescription("Share", substring = true).assertDoesNotExist()
        }
    }
}
