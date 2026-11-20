package com.ostomate.app.platform

import com.ostomate.app.domain.SupplyKind
import com.ostomate.app.domain.SupplyLink
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The Android encoder (ZXing). JVM only: the iOS encoder is Core Image, which can't render in
 * the simulator test runner (no Metal device), so iOS codes are checked on paper.
 */
class QrCodeEncoderTest {
    private val bagLink = SupplyLink.of(SupplyKind.BAG, id = 1)

    private fun QrModules.hasFinderAt(
        left: Int,
        top: Int,
    ): Boolean =
        (0 until 7).all { dy ->
            (0 until 7).all { dx ->
                val ring = dx == 0 || dy == 0 || dx == 6 || dy == 6
                val core = dx in 2..4 && dy in 2..4
                isDark(left + dx, top + dy) == (ring || core)
            }
        }

    @Test
    fun encodedLinkHasExactlyOneQuietModuleAroundTheSymbol() {
        val modules = assertNotNull(QrCodeEncoder.encodeModules(bagLink))

        assertEquals(1, modules.quietZone)
        assertEquals(modules.symbolSize + 2, modules.size)
        for (i in 0 until modules.size) {
            assertFalse(modules.isDark(i, 0), "top quiet zone at $i")
            assertFalse(modules.isDark(i, modules.size - 1), "bottom quiet zone at $i")
            assertFalse(modules.isDark(0, i), "left quiet zone at $i")
            assertFalse(modules.isDark(modules.size - 1, i), "right quiet zone at $i")
        }
    }

    @Test
    fun encodedLinkIsAVersion2SymbolTheRightWayUp() {
        val modules = assertNotNull(QrCodeEncoder.encodeModules(bagLink))
        val far = modules.size - 1 - 7

        // 23 bytes fit version 2 (25 modules) at error correction L.
        assertEquals(25, modules.symbolSize)
        // Finder patterns top-left, top-right and bottom-left — never bottom-right, so the
        // grid is neither flipped nor mirrored (a mirrored code fails on some readers).
        assertTrue(modules.hasFinderAt(1, 1))
        assertTrue(modules.hasFinderAt(far, 1))
        assertTrue(modules.hasFinderAt(1, far))
        assertFalse(modules.hasFinderAt(far, far))
    }

    @Test
    fun differentLinksGiveDifferentCodes() {
        val bag = assertNotNull(QrCodeEncoder.encodeModules(bagLink))
        val flange = assertNotNull(QrCodeEncoder.encodeModules(SupplyLink.of(SupplyKind.FLANGE, id = 2)))

        assertTrue(bag.darkRuns() != flange.darkRuns())
    }
}
