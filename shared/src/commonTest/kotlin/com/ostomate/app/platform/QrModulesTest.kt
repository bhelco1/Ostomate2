package com.ostomate.app.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QrModulesTest {
    @Test
    fun fromGridTrimsAnyEncoderMarginToOneQuietModule() {
        // A 2×2 "symbol" sitting in a 4-module margin on a 10×10 grid.
        val modules =
            assertNotNull(QrModules.fromGrid(10, 10) { x, y -> x in 4..5 && y in 4..5 && !(x == 5 && y == 5) })

        assertEquals(4, modules.size)
        assertEquals(2, modules.symbolSize)
        assertTrue(modules.isDark(1, 1))
        assertTrue(modules.isDark(2, 1))
        assertTrue(modules.isDark(1, 2))
        assertFalse(modules.isDark(2, 2))
        assertFalse(modules.isDark(0, 0))
        assertFalse(modules.isDark(3, 3))
    }

    @Test
    fun fromGridRejectsBlankAndNonSquareGrids() {
        assertNull(QrModules.fromGrid(5, 5) { _, _ -> false })
        assertNull(QrModules.fromGrid(5, 5) { x, y -> x in 1..3 && y == 2 })
    }

    @Test
    fun darkRunsJoinNeighboursAlongARow() {
        val grid =
            QrModules(
                size = 4,
                dark =
                    booleanArrayOf(
                        false, false, false, false,
                        false, true, true, false,
                        false, false, false, false,
                        false, true, false, true,
                    ),
                quietZone = 0,
            )
        assertEquals(
            listOf(
                ModuleRun(row = 1, start = 1, length = 2),
                ModuleRun(row = 3, start = 1, length = 1),
                ModuleRun(row = 3, start = 3, length = 1),
            ),
            grid.darkRuns(),
        )
    }

    @Test
    fun darkRunsRunToTheEdge() {
        val grid = QrModules(size = 3, dark = BooleanArray(9) { it >= 6 }, quietZone = 0)

        assertEquals(listOf(ModuleRun(row = 2, start = 0, length = 3)), grid.darkRuns())
    }
}
