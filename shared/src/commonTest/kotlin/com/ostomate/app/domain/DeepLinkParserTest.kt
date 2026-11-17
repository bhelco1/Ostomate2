package com.ostomate.app.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    @Test
    fun parseCustomIdExtractsTheNumber() {
        assertEquals(42L, DeepLinkParser.parseCustomId("id:42"))
    }

    @Test
    fun parseCustomIdRejectsNamedSupplies() {
        assertNull(DeepLinkParser.parseCustomId("bag"))
    }

    @Test
    fun parseCustomIdRejectsNonNumericIds() {
        assertNull(DeepLinkParser.parseCustomId("id:abc"))
    }

    @Test
    fun nfcMarkerDoesNotChangeTheItem() {
        assertEquals("bag", DeepLinkParser.parse("ostomate://log?item=bag&src=nfc"))
        assertEquals("flange", DeepLinkParser.parse("ostomate://log?src=nfc&item=flange"))
    }

    @Test
    fun detectsNfcMarker() {
        assertTrue(DeepLinkParser.isNfc("ostomate://log?item=bag&src=nfc"))
        assertTrue(DeepLinkParser.isNfc("OSTOMATE://LOG?SRC=NFC&ITEM=BAG"))
    }

    @Test
    fun linkWithoutNfcMarkerIsNotNfc() {
        assertFalse(DeepLinkParser.isNfc("ostomate://log?item=bag"))
        assertFalse(DeepLinkParser.isNfc("ostomate://log?item=bag&src=qr"))
        assertFalse(DeepLinkParser.isNfc("ostomate://log?item=bag&nfc=src"))
        assertFalse(DeepLinkParser.isNfc("ostomate://log"))
    }

    @Test
    fun nfcMarkerOnAForeignLinkIsNotNfc() {
        assertFalse(DeepLinkParser.isNfc("ostomate://evil?item=bag&src=nfc"))
        assertFalse(DeepLinkParser.isNfc("https://log?item=bag&src=nfc"))
    }
}
