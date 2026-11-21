package com.ostomate.app.ui.settings

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.ostomate.app.domain.SupplyKind
import com.ostomate.app.platform.NfcStatus
import com.ostomate.app.ui.FakeNfcStickerWriting
import com.ostomate.app.ui.screenshot.ScreenshotTest
import com.ostomate.app.ui.testSupply
import com.ostomate.app.ui.theme.OstomateTheme
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Each item's row prints that item's code on its own page, and nothing on the screen shares
 * codes or links any more (Bobby, 2026-11-20). Extends [ScreenshotTest] for its Robolectric
 * setup only; nothing is captured.
 */
@OptIn(ExperimentalTestApi::class)
class LabelsScreenUiTest : ScreenshotTest() {
    private val bag = LabelItem(testSupply(id = 1, name = "Bag", kind = SupplyKind.BAG))

    @Test
    fun printQrPrintsThisItemAndShareQrIsGone() {
        var printed = 0
        runComposeUiTest {
            setContent {
                OstomateTheme {
                    LabelRow(item = bag, nfcStatus = NfcStatus.ON, onPrintQr = { printed++ }, onSticker = {})
                }
            }

            onNodeWithContentDescription("Print QR code for Bag").performClick()
            onNodeWithText("Print QR").assertExists()
            onNodeWithText("Share QR", substring = true).assertDoesNotExist()
        }
        assertEquals(1, printed)
    }

    @Test
    fun theTopBarKeepsPrintAndNoLongerSharesTheLinks() {
        seed { supplyDao.seed(bag.supply) }
        val vm = LabelsViewModel(supplyRepository, FakeNfcStickerWriting(NfcStatus.ON))
        awaitState(vm.uiState) { it.items.size == 1 }

        runComposeUiTest {
            setContent { OstomateTheme { LabelsScreen(onBack = {}, viewModel = vm) } }

            onNodeWithText("Print").assertExists()
            onNodeWithContentDescription("Share all links").assertDoesNotExist()
            onNodeWithContentDescription("Print QR code for Bag").assertExists()
        }
    }

    @Test
    fun withNoPrinterTheRowHasNoPrintButton() {
        runComposeUiTest {
            setContent {
                OstomateTheme {
                    LabelRow(item = bag, nfcStatus = NfcStatus.ON, onPrintQr = null, onSticker = {})
                }
            }

            onNodeWithContentDescription("Print QR code for Bag").assertDoesNotExist()
            onNodeWithText("Write sticker").assertExists()
        }
    }
}
