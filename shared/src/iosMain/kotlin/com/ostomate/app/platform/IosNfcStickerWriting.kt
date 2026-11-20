package com.ostomate.app.platform

import com.ostomate.app.domain.StickerWriteOutcome

/** iPhone stickers are written with NFC Tools or an Android phone; the app shows the steps. */
object IosNfcStickerWriting : NfcStickerWriting {
    override fun status(): NfcStatus = NfcStatus.NOT_IN_APP

    override fun start(
        link: String,
        onOutcome: (StickerWriteOutcome) -> Unit,
    ): Boolean = false

    override fun stop() = Unit

    override fun openNfcSettings() = Unit
}
