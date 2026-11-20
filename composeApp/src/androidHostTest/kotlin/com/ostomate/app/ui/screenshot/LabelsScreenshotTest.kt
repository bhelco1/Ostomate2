package com.ostomate.app.ui.screenshot

import com.ostomate.app.domain.StickerWriteOutcome
import com.ostomate.app.domain.SupplyKind
import com.ostomate.app.platform.FileSharer
import com.ostomate.app.platform.NfcStatus
import com.ostomate.app.ui.FakeNfcStickerWriting
import com.ostomate.app.ui.settings.LabelItem
import com.ostomate.app.ui.settings.LabelsScreen
import com.ostomate.app.ui.settings.LabelsViewModel
import com.ostomate.app.ui.settings.StickerCard
import com.ostomate.app.ui.settings.StickerSheet
import com.ostomate.app.ui.settings.WritePhase
import com.ostomate.app.ui.testSupply
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

/**
 * Labels & stickers (FEAT-05). The screen resolves its FileSharer through `koinInject`, so it
 * needs a Koin graph. The QR bitmaps are a pure function of the link, so they are stable
 * across runs. The sticker dialog's card is captured on its own: a Dialog opens its own window,
 * which a composable capture does not see.
 */
class LabelsScreenshotTest : ScreenshotTest() {
    @Before
    fun startKoinForFileSharer() {
        startKoin { modules(module { single { FileSharer() } }) }
    }

    @After
    fun stopKoinAfterTest() {
        stopKoin()
    }

    private fun seedItems() =
        seed {
            supplyDao.seed(
                testSupply(name = "Bag", kind = SupplyKind.BAG, onHand = 24),
                testSupply(name = "Flange", kind = SupplyKind.FLANGE, onHand = 12, sortOrder = 1),
                testSupply(name = "Barrier Ring", kind = SupplyKind.CUSTOM, onHand = 8, sortOrder = 2, colorIndex = 2),
            )
        }

    private fun viewModel(status: NfcStatus) = LabelsViewModel(supplyRepository, FakeNfcStickerWriting(status))

    /** Android with NFC: one row per item with Share QR and Write sticker. */
    @Test
    fun labelsRows() {
        seedItems()
        val vm = viewModel(NfcStatus.ON)
        awaitState(vm.uiState) { it.items.size == 3 }

        capture("labels_rows") { LabelsScreen(onBack = {}, viewModel = vm) }
    }

    /** iPhone: the sticker action opens setup steps instead of writing. */
    @Test
    fun labelsRowsIphone() {
        seedItems()
        val vm = viewModel(NfcStatus.NOT_IN_APP)
        awaitState(vm.uiState) { it.items.size == 3 }

        capture("labels_rows_iphone") { LabelsScreen(onBack = {}, viewModel = vm) }
    }

    /** A phone without NFC: the sticker action is hidden, QR stays. */
    @Test
    fun labelsRowsNoNfc() {
        seedItems()
        val vm = viewModel(NfcStatus.NO_HARDWARE)
        awaitState(vm.uiState) { it.items.size == 3 }

        capture("labels_rows_no_nfc") { LabelsScreen(onBack = {}, viewModel = vm) }
    }

    /** No supplies yet: centred empty-state copy under the top bar. */
    @Test
    fun labelsEmpty() {
        val vm = viewModel(NfcStatus.ON)
        awaitState(vm.uiState) { it.items.isEmpty() }

        capture("labels_empty") { LabelsScreen(onBack = {}, viewModel = vm) }
    }

    private val custom =
        LabelItem(testSupply(id = 41, name = "Barrier Ring", kind = SupplyKind.CUSTOM, colorIndex = 2))

    private fun captureCard(
        name: String,
        sheet: StickerSheet,
    ) = capture(name) { StickerCard(sheet = sheet, onClose = {}, onWriteAgain = {}, onOpenNfcSettings = {}) }

    @Test
    fun stickerWaiting() = captureCard("sticker_waiting", StickerSheet.Write(custom, WritePhase.Waiting))

    @Test
    fun stickerWritten() =
        captureCard("sticker_written", StickerSheet.Write(custom, WritePhase.Done(StickerWriteOutcome.WRITTEN)))

    @Test
    fun stickerLocked() =
        captureCard("sticker_locked", StickerSheet.Write(custom, WritePhase.Done(StickerWriteOutcome.LOCKED)))

    @Test
    fun stickerNfcOff() = captureCard("sticker_nfc_off", StickerSheet.Write(custom, WritePhase.NfcOff))

    @Test
    fun stickerIphoneSteps() = captureCard("sticker_iphone_steps", StickerSheet.Steps(custom))
}
