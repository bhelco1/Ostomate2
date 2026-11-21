package com.ostomate.app.platform

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class QrLabelSheetTest {
    private val letterWidth = 612.0
    private val letterHeight = 792.0
    private val eps = 1e-9

    private fun assertClose(
        expected: Double,
        actual: Double,
        message: String? = null,
    ) = assertTrue(abs(expected - actual) < 1e-6, "${message ?: ""} expected $expected, got $actual")

    @Test
    fun twentyFiveMillimetresIsSeventyPointEightSixSixPoints() {
        assertClose(70.866141732, mmToPoints(25.0))
        assertClose(72.0, mmToPoints(25.4))
        assertClose(70.866141732, QrLabelLayout.qrSizePt)
    }

    @Test
    fun letterFitsFourColumnsBySixRows() {
        val sheet = QrLabelLayout.layout(count = 24, pageWidth = letterWidth, pageHeight = letterHeight)

        assertEquals(4, sheet.columns)
        assertEquals(6, sheet.rows)
        assertEquals(24, sheet.labelsPerPage)
        assertEquals(1, sheet.pageCount)
    }

    @Test
    fun everyQrSymbolIsTwentyFiveMillimetresSquare() {
        val sheet = QrLabelLayout.layout(count = 30, pageWidth = letterWidth, pageHeight = letterHeight)

        sheet.placements.forEach {
            assertClose(mmToPoints(25.0), it.qr.width, "label ${it.index} width")
            assertClose(mmToPoints(25.0), it.qr.height, "label ${it.index} height")
        }
    }

    @Test
    fun labelsFillRowsLeftToRightThenRunOntoTheNextPage() {
        val sheet = QrLabelLayout.layout(count = 30, pageWidth = letterWidth, pageHeight = letterHeight)

        assertEquals(30, sheet.placements.size)
        assertEquals(2, sheet.pageCount)
        assertEquals(24, sheet.onPage(0).size)
        assertEquals(6, sheet.onPage(1).size)
        with(sheet.placements[5]) {
            assertEquals(0, page)
            assertEquals(1, column)
            assertEquals(1, row)
        }
        with(sheet.placements[24]) {
            assertEquals(1, page)
            assertEquals(0, column)
            assertEquals(0, row)
        }
        // A new page starts at the same spot as the first.
        assertEquals(sheet.placements[0].cell, sheet.placements[24].cell)
        assertEquals((0 until 30).toList(), sheet.placements.map { it.index })
    }

    @Test
    fun placementsMatchTheGridArithmetic() {
        val sheet = QrLabelLayout.layout(count = 6, pageWidth = letterWidth, pageHeight = letterHeight)
        val cellHeight = QrLabelLayout.cellHeightPt

        // 4 cells of 108 pt + 3 gaps of 18 pt = 486 pt, centred on 612 pt: 63 pt each side.
        with(sheet.placements[0]) {
            assertEquals(PrintRect(63.0, 36.0, 108.0, cellHeight), cell)
            assertClose(63.0 + 54.0 - mmToPoints(25.0) / 2, qr.left)
            assertClose(36.0, qr.top)
            assertClose(qr.bottom + 12.0, nameBaseline)
            assertClose(nameBaseline + 9.0, linkBaseline)
        }
        with(sheet.placements[5]) {
            assertEquals(PrintRect(63.0 + 126.0, 36.0 + cellHeight + 18.0, 108.0, cellHeight), cell)
        }
        assertClose(36.0 + mmToPoints(25.0) + 25.0, cellHeight + 36.0)
    }

    @Test
    fun everyLabelStaysInsideTheMargins() {
        for ((width, height) in listOf(letterWidth to letterHeight, 595.28 to 841.89, letterHeight to letterWidth)) {
            val sheet = QrLabelLayout.layout(count = 100, pageWidth = width, pageHeight = height)
            sheet.placements.forEach {
                assertTrue(it.cell.left >= QrLabelLayout.MARGIN_PT - eps, "left on $width×$height")
                assertTrue(it.cell.top >= QrLabelLayout.MARGIN_PT - eps, "top on $width×$height")
                assertTrue(it.cell.right <= width - QrLabelLayout.MARGIN_PT + eps, "right on $width×$height")
                assertTrue(it.cell.bottom <= height - QrLabelLayout.MARGIN_PT + eps, "bottom on $width×$height")
                assertClose(it.cell.centerX, it.qr.centerX)
            }
        }
    }

    @Test
    fun a4AndLandscapeLetterAdaptTheGrid() {
        val a4 = QrLabelLayout.layout(count = 1, pageWidth = 595.28, pageHeight = 841.89)
        assertEquals(4, a4.columns)
        assertEquals(6, a4.rows)

        val landscape = QrLabelLayout.layout(count = 1, pageWidth = letterHeight, pageHeight = letterWidth)
        assertEquals(5, landscape.columns)
        assertEquals(4, landscape.rows)
    }

    @Test
    fun aPageTooSmallForOneLabelStillGetsOnePerPage() {
        val sheet = QrLabelLayout.layout(count = 3, pageWidth = 100.0, pageHeight = 100.0)

        assertEquals(1, sheet.labelsPerPage)
        assertEquals(3, sheet.pageCount)
        assertEquals(listOf(0, 1, 2), sheet.placements.map { it.page })
    }

    @Test
    fun noItemsMeansNoPages() {
        val sheet = QrLabelLayout.layout(count = 0, pageWidth = letterWidth, pageHeight = letterHeight)

        assertEquals(0, sheet.pageCount)
        assertTrue(sheet.placements.isEmpty())
    }

    @Test
    fun aFullPageCodeFillsTheWidthInsideTheMarginsOnPortraitPaper() {
        // Letter: 612 − 2 × 36 = 540 pt, and the page is taller than wide, so width decides.
        assertEquals(PrintRect(36.0, 36.0, 540.0, 540.0), QrLabelLayout.fullPage(letterWidth, letterHeight))
        // A4: 595.28 − 72 = 523.28 pt.
        val a4 = QrLabelLayout.fullPage(595.28, 841.89)
        assertClose(36.0, a4.left)
        assertClose(36.0, a4.top)
        assertClose(523.28, a4.width)
        assertClose(523.28, a4.height)
    }

    @Test
    fun aFullPageCodeOnLandscapePaperFillsTheHeightAndIsCentredAcross() {
        val code = QrLabelLayout.fullPage(letterHeight, letterWidth)

        // 612 − 72 = 540 pt high; (792 − 540) / 2 = 126 pt each side.
        assertEquals(PrintRect(126.0, 36.0, 540.0, 540.0), code)
    }

    @Test
    fun aFullPageCodeIsSquareCentredTopAlignedAndInsideTheMargins() {
        for ((width, height) in listOf(letterWidth to letterHeight, 595.28 to 841.89, letterHeight to letterWidth)) {
            val code = QrLabelLayout.fullPage(width, height)
            val m = QrLabelLayout.MARGIN_PT
            assertClose(code.width, code.height, "square on $width×$height")
            assertClose(width / 2, code.centerX, "centred on $width×$height")
            assertClose(m, code.top, "top on $width×$height")
            assertTrue(code.left >= m - eps && code.right <= width - m + eps, "across on $width×$height")
            assertTrue(code.bottom <= height - m + eps, "down on $width×$height")
            // As large as fits: it touches the margins on its tighter side.
            assertClose(minOf(width, height) - 2 * m, code.width, "size on $width×$height")
        }
    }

    @Test
    fun moduleRectsMakeTheSymbolExactlyFillItsSquare() {
        // A made-up 5-module symbol: dark corners, centre and second row. (Real codes need an
        // encoder; Core Image cannot render in the iOS simulator test runner.)
        val modules =
            assertNotNull(
                QrModules.fromGrid(5, 5) { x, y -> (x % 4 == 0 && y % 4 == 0) || (x == 2 && y == 2) || y == 1 },
            )
        val symbol = PrintRect(100.0, 200.0, mmToPoints(25.0), mmToPoints(25.0))

        val rects = modules.moduleRects(symbol)

        assertClose(symbol.left, rects.minOf { it.left })
        assertClose(symbol.top, rects.minOf { it.top })
        assertClose(symbol.right, rects.maxOf { it.right })
        assertClose(symbol.bottom, rects.maxOf { it.bottom })
        val module = symbol.width / modules.symbolSize
        rects.forEach { assertClose(module, it.height) }
        // Every dark module is covered exactly once.
        assertClose(
            (0 until modules.size).sumOf {
                    y ->
                (0 until modules.size).count { x -> modules.isDark(x, y) }
            } * module * module,
            rects.sumOf { it.width * it.height },
        )
    }
}
