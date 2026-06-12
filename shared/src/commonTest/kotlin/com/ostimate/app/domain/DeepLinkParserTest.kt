package com.ostimate.app.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DeepLinkParserTest {

    @Test
    fun parsesBagLink() {
        assertEquals("bag", DeepLinkParser.parse("ostimate://log?item=bag"))
    }

    @Test
    fun parsesFlangeLink() {
        assertEquals("flange", DeepLinkParser.parse("ostimate://log?item=flange"))
    }

    @Test
    fun isCaseInsensitive() {
        assertEquals("bag", DeepLinkParser.parse("OSTIMATE://LOG?ITEM=BAG"))
    }

    @Test
    fun toleratesSurroundingWhitespace() {
        assertEquals("bag", DeepLinkParser.parse("  ostimate://log?item=bag "))
    }

    @Test
    fun toleratesTrailingSlashOnHost() {
        assertEquals("bag", DeepLinkParser.parse("ostimate://log/?item=bag"))
    }

    @Test
    fun picksItemAmongOtherParams() {
        assertEquals("flange", DeepLinkParser.parse("ostimate://log?source=qr&item=flange"))
    }

    @Test
    fun rejectsUnknownSupply() {
        assertNull(DeepLinkParser.parse("ostimate://log?item=pouch"))
    }

    @Test
    fun rejectsWrongHost() {
        assertNull(DeepLinkParser.parse("ostimate://login?item=bag"))
    }

    @Test
    fun rejectsWrongScheme() {
        assertNull(DeepLinkParser.parse("https://log?item=bag"))
    }

    @Test
    fun rejectsMissingQuery() {
        assertNull(DeepLinkParser.parse("ostimate://log"))
    }

    @Test
    fun rejectsMissingItemParam() {
        assertNull(DeepLinkParser.parse("ostimate://log?source=qr"))
    }

    @Test
    fun rejectsEmptyString() {
        assertNull(DeepLinkParser.parse(""))
    }
}
