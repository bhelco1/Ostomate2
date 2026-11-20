package com.ostomate.app.domain

/**
 * Builds the `ostomate://log?item=…` link that a QR label, an NFC sticker or a copied link
 * holds for one item (FEAT-05). The one place this link is built; [DeepLinkParser] reads it.
 *
 * Bag and flange are named, so their labels keep working whatever row id they get; a custom
 * item is named by its id, which a backup restore keeps.
 */
object SupplyLink {
    private const val BASE = "ostomate://log?item="

    /**
     * [nfc] adds `&src=nfc`, for the iPhone Shortcuts automation: iOS gives the app no other
     * sign of an NFC tap. Android stickers hold the plain link, since Android knows from the
     * NFC intent.
     */
    fun of(
        kind: SupplyKind,
        id: Long,
        nfc: Boolean = false,
    ): String {
        val item =
            when (kind) {
                SupplyKind.BAG -> "bag"
                SupplyKind.FLANGE -> "flange"
                SupplyKind.CUSTOM -> "id:$id"
            }
        return if (nfc) "$BASE$item&src=nfc" else "$BASE$item"
    }
}
