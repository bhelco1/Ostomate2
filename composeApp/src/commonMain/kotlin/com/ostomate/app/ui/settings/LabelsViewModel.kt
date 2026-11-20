package com.ostomate.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ostomate.app.data.SupplyRepository
import com.ostomate.app.data.db.SupplyTypeEntity
import com.ostomate.app.domain.StickerWriteOutcome
import com.ostomate.app.domain.SupplyLink
import com.ostomate.app.platform.NfcStatus
import com.ostomate.app.platform.NfcStickerWriting
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** One item on Labels & stickers: its QR / Android sticker link and its iPhone sticker link. */
data class LabelItem(
    val supply: SupplyTypeEntity,
    val link: String = SupplyLink.of(supply.kind, supply.id),
    val nfcLink: String = SupplyLink.of(supply.kind, supply.id, nfc = true),
)

sealed interface StickerSheet {
    val item: LabelItem

    /** Android: the app writes the sticker itself. */
    data class Write(override val item: LabelItem, val phase: WritePhase) : StickerSheet

    /** iPhone: steps for NFC Tools + a Shortcuts automation, with a Copy link button. */
    data class Steps(override val item: LabelItem) : StickerSheet
}

sealed interface WritePhase {
    data object NfcOff : WritePhase

    /** Listening: hold a sticker to the phone. */
    data object Waiting : WritePhase

    data class Done(val outcome: StickerWriteOutcome) : WritePhase
}

data class LabelsUiState(
    val items: List<LabelItem> = emptyList(),
    val nfcStatus: NfcStatus = NfcStatus.NO_HARDWARE,
    val sheet: StickerSheet? = null,
) {
    /** Hidden on phones without NFC; on iPhone it opens the setup steps. */
    val showStickerAction: Boolean get() = nfcStatus != NfcStatus.NO_HARDWARE
}

/**
 * Labels & stickers (FEAT-05). Sticker writing is on only while the Write sheet is open and
 * the screen is in the foreground: [onScreenPaused] stops it and [onScreenResumed] picks it
 * back up (e.g. after the user turned NFC on in Settings). Never locks a sticker.
 */
class LabelsViewModel(
    supplyRepository: SupplyRepository,
    private val nfc: NfcStickerWriting,
) : ViewModel() {
    private val nfcStatus = MutableStateFlow(nfc.status())
    private val sheet = MutableStateFlow<StickerSheet?>(null)

    val uiState: StateFlow<LabelsUiState> =
        combine(supplyRepository.observeSupplies(), nfcStatus, sheet) { supplies, status, openSheet ->
            LabelsUiState(items = supplies.map { LabelItem(it) }, nfcStatus = status, sheet = openSheet)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LabelsUiState(nfcStatus = nfcStatus.value))

    fun openSticker(item: LabelItem) {
        val status = refreshStatus()
        sheet.value =
            when (status) {
                NfcStatus.NOT_IN_APP -> StickerSheet.Steps(item)
                NfcStatus.NO_HARDWARE -> null
                NfcStatus.OFF -> StickerSheet.Write(item, WritePhase.NfcOff)
                NfcStatus.ON -> StickerSheet.Write(item, startWriting(item))
            }
    }

    /** "Try again" / "Write another": listen afresh for the same item. */
    fun writeAgain() {
        val open = sheet.value as? StickerSheet.Write ?: return
        val phase = if (refreshStatus() == NfcStatus.ON) startWriting(open.item) else WritePhase.NfcOff
        sheet.value = open.copy(phase = phase)
    }

    fun dismissSticker() {
        nfc.stop()
        sheet.value = null
    }

    fun openNfcSettings() = nfc.openNfcSettings()

    fun onScreenPaused() {
        if (sheet.value is StickerSheet.Write) nfc.stop()
    }

    fun onScreenResumed() {
        val status = refreshStatus()
        val open = sheet.value as? StickerSheet.Write ?: return
        val phase =
            when {
                status != NfcStatus.ON -> WritePhase.NfcOff
                open.phase is WritePhase.Done -> {
                    // Keep a shown result on screen; the next sticker held up replaces it.
                    val restarted = startWriting(open.item)
                    if (restarted == WritePhase.Waiting) open.phase else restarted
                }
                else -> startWriting(open.item)
            }
        sheet.value = open.copy(phase = phase)
    }

    override fun onCleared() {
        nfc.stop()
    }

    private fun refreshStatus(): NfcStatus = nfc.status().also { nfcStatus.value = it }

    private fun startWriting(item: LabelItem): WritePhase {
        val started = nfc.start(item.link) { outcome -> onOutcome(item, outcome) }
        return when {
            started -> WritePhase.Waiting
            refreshStatus() == NfcStatus.OFF -> WritePhase.NfcOff
            else -> WritePhase.Done(StickerWriteOutcome.LOST_CONTACT)
        }
    }

    private fun onOutcome(
        item: LabelItem,
        outcome: StickerWriteOutcome,
    ) {
        val open = sheet.value as? StickerSheet.Write ?: return
        if (open.item.supply.id != item.supply.id) return
        sheet.value = open.copy(phase = WritePhase.Done(outcome))
    }
}
