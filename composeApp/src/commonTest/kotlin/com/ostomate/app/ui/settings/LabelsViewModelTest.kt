package com.ostomate.app.ui.settings

import com.ostomate.app.data.SupplyRepository
import com.ostomate.app.domain.StickerWriteOutcome
import com.ostomate.app.domain.SupplyKind
import com.ostomate.app.platform.NfcStatus
import com.ostomate.app.ui.FakeNfcStickerWriting
import com.ostomate.app.ui.FakeSupplyTypeDao
import com.ostomate.app.ui.MainDispatcherTest
import com.ostomate.app.ui.keepSubscribed
import com.ostomate.app.ui.testSupply
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LabelsViewModelTest : MainDispatcherTest() {
    private val supplyDao = FakeSupplyTypeDao()
    private val nfc = FakeNfcStickerWriting()

    private suspend fun TestScope.subscribedViewModel(): LabelsViewModel {
        supplyDao.seed(
            testSupply(name = "Bag", kind = SupplyKind.BAG, sortOrder = 0),
            testSupply(name = "Flange", kind = SupplyKind.FLANGE, sortOrder = 1),
            testSupply(id = 41, name = "Barrier rings", kind = SupplyKind.CUSTOM, sortOrder = 2),
            testSupply(name = "Old paste", kind = SupplyKind.CUSTOM, sortOrder = 3, archived = true),
        )
        val vm = LabelsViewModel(SupplyRepository(supplyDao), nfc)
        keepSubscribed(vm.uiState)
        advanceUntilIdle()
        return vm
    }

    private fun LabelsViewModel.item(name: String) = uiState.value.items.single { it.supply.name == name }

    private val LabelsViewModel.writeSheet get() = assertIs<StickerSheet.Write>(uiState.value.sheet)

    @Test
    fun oneRowPerActiveItemWithItsLinks() =
        runTest {
            val vm = subscribedViewModel()

            val items = vm.uiState.value.items
            assertEquals(listOf("Bag", "Flange", "Barrier rings"), items.map { it.supply.name })
            assertEquals(
                listOf("ostomate://log?item=bag", "ostomate://log?item=flange", "ostomate://log?item=id:41"),
                items.map { it.link },
            )
            assertEquals("ostomate://log?item=id:41&src=nfc", items.last().nfcLink)
        }

    @Test
    fun stickerActionIsHiddenOnlyWithoutNfcHardware() =
        runTest {
            for (status in NfcStatus.entries) {
                nfc.status = status
                val vm = LabelsViewModel(SupplyRepository(supplyDao), nfc)
                keepSubscribed(vm.uiState)
                advanceUntilIdle()
                assertEquals(status != NfcStatus.NO_HARDWARE, vm.uiState.value.showStickerAction, "$status")
            }
        }

    @Test
    fun writingListensForTheItemsPlainLinkUntilClosed() =
        runTest {
            val vm = subscribedViewModel()
            assertFalse(nfc.isListening, "reader mode must be off until a sheet opens")

            vm.openSticker(vm.item("Barrier rings"))
            advanceUntilIdle()

            assertEquals(WritePhase.Waiting, vm.writeSheet.phase)
            assertEquals("ostomate://log?item=id:41", nfc.listeningFor)

            vm.dismissSticker()
            advanceUntilIdle()
            assertNull(vm.uiState.value.sheet)
            assertFalse(nfc.isListening)
        }

    @Test
    fun eachOutcomeIsShownForTheOpenItem() =
        runTest {
            val vm = subscribedViewModel()
            vm.openSticker(vm.item("Bag"))

            for (outcome in StickerWriteOutcome.entries) {
                nfc.tap(outcome)
                advanceUntilIdle()
                assertEquals(WritePhase.Done(outcome), vm.writeSheet.phase)
            }
            // Still listening: the next sticker held up is written too.
            assertTrue(nfc.isListening)
        }

    @Test
    fun writeAgainListensAfresh() =
        runTest {
            val vm = subscribedViewModel()
            vm.openSticker(vm.item("Bag"))
            nfc.tap(StickerWriteOutcome.LOST_CONTACT)

            vm.writeAgain()
            advanceUntilIdle()

            assertEquals(WritePhase.Waiting, vm.writeSheet.phase)
            assertEquals(2, nfc.starts)
            assertEquals("ostomate://log?item=bag", nfc.listeningFor)
        }

    @Test
    fun writeAgainWithNothingOpenDoesNothing() =
        runTest {
            val vm = subscribedViewModel()
            vm.writeAgain()
            advanceUntilIdle()
            assertNull(vm.uiState.value.sheet)
            assertEquals(0, nfc.starts)
        }

    @Test
    fun nfcOffAsksToTurnItOnAndResumesWritingOnceItIs() =
        runTest {
            nfc.status = NfcStatus.OFF
            val vm = subscribedViewModel()

            vm.openSticker(vm.item("Flange"))
            advanceUntilIdle()
            assertEquals(WritePhase.NfcOff, vm.writeSheet.phase)
            assertFalse(nfc.isListening)

            vm.openNfcSettings()
            assertEquals(1, nfc.settingsOpened)
            vm.onScreenPaused()
            nfc.status = NfcStatus.ON
            vm.onScreenResumed()
            advanceUntilIdle()

            assertEquals(WritePhase.Waiting, vm.writeSheet.phase)
            assertEquals("ostomate://log?item=flange", nfc.listeningFor)
            assertEquals(NfcStatus.ON, vm.uiState.value.nfcStatus)
        }

    @Test
    fun turningNfcOffWhileWritingShowsNfcOffOnReturn() =
        runTest {
            val vm = subscribedViewModel()
            vm.openSticker(vm.item("Bag"))

            vm.onScreenPaused()
            nfc.status = NfcStatus.OFF
            vm.onScreenResumed()
            advanceUntilIdle()

            assertEquals(WritePhase.NfcOff, vm.writeSheet.phase)
            assertFalse(nfc.isListening)
        }

    @Test
    fun pausingTheScreenStopsWritingAndResumingRestartsIt() =
        runTest {
            val vm = subscribedViewModel()
            vm.openSticker(vm.item("Bag"))

            vm.onScreenPaused()
            assertFalse(nfc.isListening)

            vm.onScreenResumed()
            advanceUntilIdle()
            assertTrue(nfc.isListening)
            assertEquals(WritePhase.Waiting, vm.writeSheet.phase)
        }

    @Test
    fun aShownResultSurvivesPauseAndResumeAndWritingRestarts() =
        runTest {
            val vm = subscribedViewModel()
            vm.openSticker(vm.item("Bag"))
            nfc.tap(StickerWriteOutcome.WRITTEN)

            vm.onScreenPaused()
            vm.onScreenResumed()
            advanceUntilIdle()

            assertEquals(WritePhase.Done(StickerWriteOutcome.WRITTEN), vm.writeSheet.phase)
            assertTrue(nfc.isListening)
        }

    @Test
    fun pauseAndResumeWithoutAWriteSheetNeverStartReaderMode() =
        runTest {
            nfc.status = NfcStatus.NOT_IN_APP
            val vm = subscribedViewModel()
            vm.openSticker(vm.item("Bag"))

            vm.onScreenPaused()
            vm.onScreenResumed()
            advanceUntilIdle()

            assertEquals(0, nfc.starts)
            assertEquals(0, nfc.stops)
            assertIs<StickerSheet.Steps>(vm.uiState.value.sheet)
        }

    @Test
    fun iPhoneShowsStepsWithTheNfcLinkAndNeverStartsWriting() =
        runTest {
            nfc.status = NfcStatus.NOT_IN_APP
            val vm = subscribedViewModel()

            vm.openSticker(vm.item("Barrier rings"))
            advanceUntilIdle()

            val steps = assertIs<StickerSheet.Steps>(vm.uiState.value.sheet)
            assertEquals("ostomate://log?item=id:41&src=nfc", steps.item.nfcLink)
            assertEquals(0, nfc.starts)
        }

    @Test
    fun noHardwareOpensNothing() =
        runTest {
            nfc.status = NfcStatus.NO_HARDWARE
            val vm = subscribedViewModel()

            vm.openSticker(vm.item("Bag"))
            advanceUntilIdle()

            assertNull(vm.uiState.value.sheet)
            assertEquals(0, nfc.starts)
        }

    @Test
    fun aWriterThatCannotStartWhileNfcIsOnAsksToTryAgain() =
        runTest {
            nfc.startSucceeds = false
            val vm = subscribedViewModel()

            vm.openSticker(vm.item("Bag"))
            advanceUntilIdle()

            assertEquals(WritePhase.Done(StickerWriteOutcome.LOST_CONTACT), vm.writeSheet.phase)
        }

    @Test
    fun aWriterThatCannotStartBecauseNfcJustWentOffShowsNfcOff() =
        runTest {
            val vm = subscribedViewModel()
            nfc.startSucceeds = false
            nfc.statusAfterFailedStart = NfcStatus.OFF

            vm.openSticker(vm.item("Bag"))
            advanceUntilIdle()

            assertEquals(WritePhase.NfcOff, vm.writeSheet.phase)
            assertEquals(1, nfc.starts)
        }

    @Test
    fun writeAgainWithNfcOffDoesNotStartWriting() =
        runTest {
            val vm = subscribedViewModel()
            vm.openSticker(vm.item("Bag"))
            nfc.tap(StickerWriteOutcome.LOST_CONTACT)
            nfc.status = NfcStatus.OFF

            vm.writeAgain()
            advanceUntilIdle()

            assertEquals(WritePhase.NfcOff, vm.writeSheet.phase)
            assertEquals(1, nfc.starts)
        }

    @Test
    fun aLateOutcomeFromAnEarlierItemIsIgnored() =
        runTest {
            val vm = subscribedViewModel()
            vm.openSticker(vm.item("Bag"))
            vm.dismissSticker()
            vm.openSticker(vm.item("Flange"))

            nfc.callbacks.first()(StickerWriteOutcome.WRITTEN)
            advanceUntilIdle()

            assertEquals("Flange", vm.writeSheet.item.supply.name)
            assertEquals(WritePhase.Waiting, vm.writeSheet.phase)
        }

    @Test
    fun aLateOutcomeAfterClosingIsIgnored() =
        runTest {
            val vm = subscribedViewModel()
            vm.openSticker(vm.item("Bag"))
            vm.dismissSticker()

            nfc.callbacks.single()(StickerWriteOutcome.WRITTEN)
            advanceUntilIdle()

            assertNull(vm.uiState.value.sheet)
        }
}
