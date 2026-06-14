package com.ostomate.app.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DeepLinkParserTest {
    @Test
    fun parsesBagLink() {
        assertEquals("bag", DeepLinkParser.parse("ostomate://log?item=bag"))
    }

    @Test
    fun parsesFlangeLink() {
        assertEquals("flange", DeepLinkParser.parse("ostomate://log?item=flange"))
    }

    @Test
    fun isCaseInsensitive() {
        assertEquals("bag", DeepLinkParser.parse("OSTOMATE://LOG?ITEM=BAG"))
    }

    @Test
    fun toleratesSurroundingWhitespace() {
        assertEquals("bag", DeepLinkParser.parse("  ostomate://log?item=bag "))
    }

    @Test
    fun toleratesTrailingSlashOnHost() {
        assertEquals("bag", DeepLinkParser.parse("ostomate://log/?item=bag"))
    }

    @Test
    fun picksItemAmongOtherParams() {
        assertEquals("flange", DeepLinkParser.parse("ostomate://log?source=qr&item=flange"))
    }

    @Test
    fun rejectsUnknownSupply() {
        assertNull(DeepLinkParser.parse("ostomate://log?item=pouch"))
    }

    @Test
    fun rejectsWrongHost() {
        assertNull(DeepLinkParser.parse("ostomate://login?item=bag"))
    }

    @Test
    fun rejectsWrongScheme() {
        assertNull(DeepLinkParser.parse("https://log?item=bag"))
    }

    @Test
    fun rejectsMissingQuery() {
        assertNull(DeepLinkParser.parse("ostomate://log"))
    }

    @Test
    fun rejectsMissingItemParam() {
        assertNull(DeepLinkParser.parse("ostomate://log?source=qr"))
    }

    @Test
    fun rejectsEmptyString() {
        assertNull(DeepLinkParser.parse(""))
    }

    @Test
    fun parsesCustomSupplyIdLink() {
        assertEquals("id:42", DeepLinkParser.parse("ostomate://log?item=id:42"))
    }

    @Test
    fun rejectsNonNumericCustomId() {
        assertNull(DeepLinkParser.parse("ostomate://log?item=id:abc"))
    }

    @Test
    fun rejectsEmptyCustomId() {
        assertNull(DeepLinkParser.parse("ostomate://log?item=id:"))
    }
}
