package com.ostomate.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    @Test
    fun userTagsHideSourceTokensAndBlanks() {
        assertEquals(listOf("Leak", "night"), ChangeSource.userTags("source:qr, Leak,, night"))
        assertEquals(emptyList<String>(), ChangeSource.userTags("source:manual"))
        assertEquals(emptyList<String>(), ChangeSource.userTags(null))
    }

    @Test
    fun isSourceTagMatchesOnlyTheNamespace() {
        assertTrue(ChangeSource.isSourceTag(" source:widget"))
        assertFalse(ChangeSource.isSourceTag("Leak"))
        assertFalse(ChangeSource.isSourceTag("resource:qr"))
    }

    @Test
    fun withUserTagsKeepsTheOriginalSourceToken() {
        assertEquals("source:qr,Leak,Routine", ChangeSource.withUserTags("source:qr,Leak", listOf("Leak", "Routine")))
        assertEquals("source:manual", ChangeSource.withUserTags("source:manual,Leak", emptyList()))
        assertEquals("Leak", ChangeSource.withUserTags(null, listOf("Leak")))
        assertNull(ChangeSource.withUserTags("", emptyList()))
    }
}
