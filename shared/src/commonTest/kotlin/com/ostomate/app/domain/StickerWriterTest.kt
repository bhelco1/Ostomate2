package com.ostomate.app.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StickerWriterTest {
    private val link = "ostomate://log?item=bag"

    private class FakeTag(
        override val format: StickerTagFormat = StickerTagFormat.NDEF,
        override val isWritable: Boolean = true,
        override val capacityBytes: Int = 137,
        private val size: Int = 28,
        private val failWrite: Boolean = false,
        private val failRead: Boolean = false,
        private val failClose: Boolean = false,
        private val readBack: ((String?) -> String?) = { it },
    ) : StickerTag {
        var written: String? = null
        var writes = 0
        var closed = 0
        var sizeAskedFor: String? = null

        override fun messageSize(link: String): Int {
            sizeAskedFor = link
            return size
        }

        override fun write(link: String) {
            writes++
            if (failWrite) throw StickerIoException("tag lost")
            written = link
        }

        override fun readLink(): String? {
            if (failRead) throw StickerIoException("tag lost")
            return readBack(written)
        }

        override fun close() {
            closed++
            if (failClose) throw StickerIoException("already gone")
        }
    }

    @Test
    fun aBlankStickerIsWrittenAndConfirmed() {
        val tag = FakeTag()

        assertEquals(StickerWriteOutcome.WRITTEN, StickerWriter.write(tag, link))
        assertEquals(link, tag.written)
        assertEquals(1, tag.writes)
        assertEquals(1, tag.closed)
    }

    @Test
    fun aStickerThatCannotHoldNdefIsUnsupported() {
        val tag = FakeTag(format = StickerTagFormat.UNSUPPORTED)

        assertEquals(StickerWriteOutcome.UNSUPPORTED, StickerWriter.write(tag, link))
        assertEquals(0, tag.writes)
        assertEquals(1, tag.closed)
    }

    @Test
    fun aLockedStickerIsReportedAndNotWritten() {
        val tag = FakeTag(isWritable = false)

        assertEquals(StickerWriteOutcome.LOCKED, StickerWriter.write(tag, link))
        assertEquals(0, tag.writes)
        assertEquals(1, tag.closed)
    }

    @Test
    fun aStickerTooSmallForTheLinkIsReportedAndNotWritten() {
        val tag = FakeTag(capacityBytes = 27, size = 28)

        assertEquals(StickerWriteOutcome.TOO_SMALL, StickerWriter.write(tag, link))
        assertEquals(link, tag.sizeAskedFor)
        assertEquals(0, tag.writes)
        assertEquals(1, tag.closed)
    }

    @Test
    fun aLinkThatExactlyFillsTheStickerIsWritten() {
        val tag = FakeTag(capacityBytes = 28, size = 28)

        assertEquals(StickerWriteOutcome.WRITTEN, StickerWriter.write(tag, link))
    }

    @Test
    fun movingTheStickerAwayDuringTheWriteIsLostContact() {
        val tag = FakeTag(failWrite = true)

        assertEquals(StickerWriteOutcome.LOST_CONTACT, StickerWriter.write(tag, link))
        assertEquals(1, tag.closed)
    }

    @Test
    fun movingTheStickerAwayDuringTheReadBackIsNotConfirmed() {
        val tag = FakeTag(failRead = true)

        assertEquals(StickerWriteOutcome.NOT_CONFIRMED, StickerWriter.write(tag, link))
        assertEquals(1, tag.closed)
    }

    @Test
    fun aReadBackOfAnotherLinkIsNotConfirmed() {
        val tag = FakeTag(readBack = { "ostomate://log?item=flange" })

        assertEquals(StickerWriteOutcome.NOT_CONFIRMED, StickerWriter.write(tag, link))
    }

    @Test
    fun anUnreadableReadBackIsNotConfirmed() {
        val tag = FakeTag(readBack = { null })

        assertEquals(StickerWriteOutcome.NOT_CONFIRMED, StickerWriter.write(tag, link))
    }

    @Test
    fun aFormatableStickerIsWrittenWithoutTheNdefChecks() {
        // A factory-blank sticker reports no NDEF size or lock state until it is formatted.
        val tag =
            FakeTag(
                format = StickerTagFormat.FORMATABLE,
                isWritable = false,
                capacityBytes = 0,
                // As on Android: a just-formatted sticker has no NDEF view to read back.
                readBack = { null },
            )

        assertEquals(StickerWriteOutcome.WRITTEN, StickerWriter.write(tag, link))
        assertNull(tag.sizeAskedFor)
        assertEquals(1, tag.writes)
    }

    @Test
    fun aFormatableStickerThatFailsToFormatHasLostContact() {
        val tag = FakeTag(format = StickerTagFormat.FORMATABLE, failWrite = true, readBack = { null })

        assertEquals(StickerWriteOutcome.LOST_CONTACT, StickerWriter.write(tag, link))
    }

    @Test
    fun aFailedCloseDoesNotHideTheOutcome() {
        val tag = FakeTag(failClose = true)

        assertEquals(StickerWriteOutcome.WRITTEN, StickerWriter.write(tag, link))
        assertTrue(tag.closed == 1)
    }
}
