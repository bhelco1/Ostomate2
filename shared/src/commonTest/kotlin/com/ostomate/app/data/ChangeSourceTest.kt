package com.ostomate.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChangeSourceTest {
    @Test
    fun tagEncodesSourceAsNamespacedToken() {
        assertEquals("source:qr", ChangeSource.QR.tag)
        assertEquals("source:manual", ChangeSource.MANUAL.tag)
        assertEquals("source:widget", ChangeSource.WIDGET.tag)
    }

    @Test
    fun fromTagsRoundTripsEverySource() {
        for (source in ChangeSource.entries) {
            assertEquals(source, ChangeSource.fromTags(source.tag))
        }
    }

    @Test
    fun fromTagsFindsSourceAmongOtherUserTags() {
        assertEquals(ChangeSource.QR, ChangeSource.fromTags("leak, night, source:qr"))
    }

    @Test
    fun fromTagsIsNullWhenAbsentOrBlank() {
        assertNull(ChangeSource.fromTags(null))
        assertNull(ChangeSource.fromTags(""))
        assertNull(ChangeSource.fromTags("leak,night"))
    }
}
