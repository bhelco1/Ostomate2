package com.ostomate.app.platform

import com.ostomate.app.domain.StickerWriteOutcome

enum class NfcStatus {
    ON,

    /** The phone has NFC but it is switched off; offer the NFC settings. */
    OFF,

    /** No NFC chip: the sticker action is hidden. */
    NO_HARDWARE,

    /** iPhone: the app cannot write stickers itself (needs Apple's paid NFC capability). */
    NOT_IN_APP,
}

/**
 * Writes an item's link to an NFC sticker (FEAT-05). While writing is on, the phone hands
 * every tag to the writer instead of opening the app's NFC link, so a sticker tap during
 * writing never logs an event.
 */
interface NfcStickerWriting {
    fun status(): NfcStatus

    /**
     * Starts listening for a sticker to write [link] to; each sticker held to the phone gets one
     * [onOutcome], on the main thread. Returns false when writing could not start.
     */
    fun start(
        link: String,
        onOutcome: (StickerWriteOutcome) -> Unit,
    ): Boolean

    /** Stops listening. Safe to call when not started. */
    fun stop()

    fun openNfcSettings()
}
