package com.ostomate.app.ui.theme

import androidx.compose.ui.graphics.Color
import com.ostomate.app.domain.SupplyKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/** Labels print on white paper, so an item's print colour is always its light-theme colour. */
class SupplyPrintColorTest {
    @Test
    fun bagPrintsInTheAppsBagGreen() {
        assertEquals(Color(0xFF16A34A), supplyPrintColor(SupplyKind.BAG))
        assertEquals(OstomateColors.Bag, supplyPrintColor(SupplyKind.BAG, colorIndex = 5))
    }

    @Test
    fun flangePrintsInTheAppsFlangeOrange() {
        assertEquals(Color(0xFFE65100), supplyPrintColor(SupplyKind.FLANGE))
        assertEquals(OstomateColors.Flange, supplyPrintColor(SupplyKind.FLANGE, colorIndex = 3))
    }

    @Test
    fun customItemsPrintInTheirLightPaletteColour() {
        val expected =
            listOf(
                0xFF7C3AED,
                0xFF0D9488,
                0xFFE11D48,
                0xFFB45309,
                0xFF0891B2,
                0xFF4338CA,
                0xFFDB2777,
                0xFF4D7C0F,
            )
        expected.forEachIndexed { index, argb ->
            assertEquals(Color(argb), supplyPrintColor(SupplyKind.CUSTOM, colorIndex = index), "index $index")
        }
    }

    @Test
    fun customItemWithoutAValidIndexFallsBackToTheFirstColour() {
        assertEquals(OstomateColors.Custom0, supplyPrintColor(SupplyKind.CUSTOM, colorIndex = null))
        assertEquals(OstomateColors.Custom0, supplyPrintColor(SupplyKind.CUSTOM, colorIndex = -1))
        assertEquals(OstomateColors.Custom0, supplyPrintColor(SupplyKind.CUSTOM, colorIndex = 8))
    }

    @Test
    fun printColoursNeverUseTheDarkThemeVariants() {
        assertNotEquals(OstomateColors.BagDark, supplyPrintColor(SupplyKind.BAG))
        assertNotEquals(OstomateColors.FlangeDark, supplyPrintColor(SupplyKind.FLANGE))
        assertNotEquals(OstomateColors.Custom0Dark, supplyPrintColor(SupplyKind.CUSTOM, colorIndex = 0))
    }
}
