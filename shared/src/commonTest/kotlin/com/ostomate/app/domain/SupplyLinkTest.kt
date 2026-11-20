package com.ostomate.app.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SupplyLinkTest {
    @Test
    fun bagLinkNamesTheBag() {
        assertEquals("ostomate://log?item=bag", SupplyLink.of(SupplyKind.BAG, id = 1))
    }

    @Test
    fun flangeLinkNamesTheFlange() {
        assertEquals("ostomate://log?item=flange", SupplyLink.of(SupplyKind.FLANGE, id = 2))
    }

    @Test
    fun builtInLinksIgnoreTheRowId() {
        // A restored or re-seeded bag may get another id; its printed label must still work.
        assertEquals(SupplyLink.of(SupplyKind.BAG, id = 1), SupplyLink.of(SupplyKind.BAG, id = 99))
        assertEquals(SupplyLink.of(SupplyKind.FLANGE, id = 2), SupplyLink.of(SupplyKind.FLANGE, id = 77))
    }

    @Test
    fun customLinkCarriesTheSupplyId() {
        assertEquals("ostomate://log?item=id:42", SupplyLink.of(SupplyKind.CUSTOM, id = 42))
    }

    @Test
    fun nfcLinkAddsTheNfcSource() {
        assertEquals("ostomate://log?item=bag&src=nfc", SupplyLink.of(SupplyKind.BAG, id = 1, nfc = true))
        assertEquals("ostomate://log?item=flange&src=nfc", SupplyLink.of(SupplyKind.FLANGE, id = 2, nfc = true))
        assertEquals("ostomate://log?item=id:7&src=nfc", SupplyLink.of(SupplyKind.CUSTOM, id = 7, nfc = true))
    }

    @Test
    fun everyLinkParsesBackToItsItem() {
        for (nfc in listOf(false, true)) {
            assertEquals("bag", DeepLinkParser.parse(SupplyLink.of(SupplyKind.BAG, 1, nfc)))
            assertEquals("flange", DeepLinkParser.parse(SupplyLink.of(SupplyKind.FLANGE, 2, nfc)))
            assertEquals("id:42", DeepLinkParser.parse(SupplyLink.of(SupplyKind.CUSTOM, 42, nfc)))
        }
    }

    @Test
    fun onlyTheNfcLinkReadsAsAnNfcTap() {
        assertFalse(DeepLinkParser.isNfc(SupplyLink.of(SupplyKind.CUSTOM, 42)))
        assertTrue(DeepLinkParser.isNfc(SupplyLink.of(SupplyKind.CUSTOM, 42, nfc = true)))
    }
}
