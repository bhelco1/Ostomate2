package com.ostomate.app.domain

/** What happened when the user held a sticker to the phone to write it (FEAT-05). */
enum class StickerWriteOutcome {
    /** Written and read back: the sticker holds exactly the item's link. */
    WRITTEN,

    /** The sticker is locked (read-only); nothing can be written to it. */
    LOCKED,

    /** The link does not fit on the sticker. */
    TOO_SMALL,

    /** The write failed, almost always because the sticker moved away too soon. */
    LOST_CONTACT,

    /** The tag cannot hold an NDEF link at all (e.g. a transit or bank card). */
    UNSUPPORTED,

    /** The write went through but the read-back did not show the link; hold it again. */
    NOT_CONFIRMED,
}

enum class StickerTagFormat {
    /** Already NDEF-formatted: lock state and capacity are known. */
    NDEF,

    /** Factory blank: formatting it writes the link in the same step. */
    FORMATABLE,
    UNSUPPORTED,
}

/** Thrown by a [StickerTag] when talking to the sticker fails (lost contact, I/O error). */
class StickerIoException(message: String? = null, cause: Throwable? = null) : Exception(message, cause)

/**
 * One sticker the phone is touching. The platform wraps its NFC tag in this so the decision
 * logic in [StickerWriter] runs and tests on the JVM. There is deliberately no way to lock a
 * sticker: locking is permanent, and FEAT-05 never does it.
 */
interface StickerTag {
    val format: StickerTagFormat

    /** Meaningful for [StickerTagFormat.NDEF] only. */
    val isWritable: Boolean

    /** NDEF capacity in bytes; meaningful for [StickerTagFormat.NDEF] only. */
    val capacityBytes: Int

    /** Size in bytes of the NDEF message holding [link] as one URI record. */
    fun messageSize(link: String): Int

    /** Writes [link] as the sticker's only record. Throws [StickerIoException]. */
    fun write(link: String)

    /** The URI the sticker now holds, or null if it has none or cannot say. Throws [StickerIoException]. */
    fun readLink(): String?

    fun close()
}

object StickerWriter {
    /** Checks, writes and reads back [link]; always closes [tag]. Called off the main thread. */
    fun write(
        tag: StickerTag,
        link: String,
    ): StickerWriteOutcome =
        try {
            writeAndConfirm(tag, link)
        } finally {
            try {
                tag.close()
            } catch (_: StickerIoException) {
                // The sticker has already gone; the outcome stands.
            }
        }

    private fun writeAndConfirm(
        tag: StickerTag,
        link: String,
    ): StickerWriteOutcome {
        val refusal =
            when (tag.format) {
                StickerTagFormat.UNSUPPORTED -> StickerWriteOutcome.UNSUPPORTED
                StickerTagFormat.FORMATABLE -> null
                StickerTagFormat.NDEF ->
                    when {
                        !tag.isWritable -> StickerWriteOutcome.LOCKED
                        tag.messageSize(link) > tag.capacityBytes -> StickerWriteOutcome.TOO_SMALL
                        else -> null
                    }
            }
        if (refusal != null) return refusal

        try {
            tag.write(link)
        } catch (_: StickerIoException) {
            return StickerWriteOutcome.LOST_CONTACT
        }
        // Formatting a factory-blank sticker writes the link and fails outright if it doesn't
        // finish, and the sticker has no NDEF view to read until the phone sees it again, so a
        // format that returned is the confirmation. Without this every new sticker would say
        // "hold it again" on its first hold.
        if (tag.format == StickerTagFormat.FORMATABLE) return StickerWriteOutcome.WRITTEN
        val readBack =
            try {
                tag.readLink()
            } catch (_: StickerIoException) {
                null
            }
        return if (readBack == link) StickerWriteOutcome.WRITTEN else StickerWriteOutcome.NOT_CONFIRMED
    }
}
