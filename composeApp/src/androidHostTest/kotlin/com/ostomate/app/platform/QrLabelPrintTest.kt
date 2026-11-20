package com.ostomate.app.platform

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.ostomate.app.domain.SupplyKind
import com.ostomate.app.ui.testSupply
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Android label printer's drawing, on a canvas in points (as a PDF page is). It records
 * what is drawn, so the printed size is measured rather than assumed. Robolectric has no
 * native PdfDocument, so the sheet is also rendered at 300 dpi to
 * `build/outputs/qr-labels/qr-labels-letter-300dpi.png` and measured in pixels.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QrLabelPrintTest {
    private val bagGreen = 0xFF16A34A.toInt()
    private val flangeOrange = 0xFFE65100.toInt()
    private val barrierRose = 0xFFE11D48.toInt()

    private class Recorded(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val color: Int,
    )

    private class RecordedText(
        val text: String,
        val x: Float,
        val y: Float,
        val color: Int,
        val size: Float,
    )

    /** Keeps every rectangle and line of text drawn on it. */
    private class RecordingCanvas : Canvas() {
        val rects = mutableListOf<Recorded>()
        val texts = mutableListOf<RecordedText>()

        override fun drawRect(
            left: Float,
            top: Float,
            right: Float,
            bottom: Float,
            paint: Paint,
        ) {
            rects += Recorded(left, top, right, bottom, paint.color)
        }

        override fun drawPath(
            path: Path,
            paint: Paint,
        ) {
            val bounds = RectF().also { path.computeBounds(it, true) }
            rects += Recorded(bounds.left, bounds.top, bounds.right, bounds.bottom, paint.color)
        }

        override fun drawText(
            text: String,
            x: Float,
            y: Float,
            paint: Paint,
        ) {
            texts += RecordedText(text, x, y, paint.color, paint.textSize)
        }
    }

    private val supplies =
        listOf(
            testSupply(id = 1, name = "Bag", kind = SupplyKind.BAG),
            testSupply(id = 2, name = "Flange", kind = SupplyKind.FLANGE, sortOrder = 1),
            testSupply(id = 9, name = "Barrier Ring", kind = SupplyKind.CUSTOM, sortOrder = 2, colorIndex = 2),
        )

    private fun assertClose(
        expected: Double,
        actual: Float,
        message: String,
    ) = assertTrue(abs(expected - actual) < 0.001, "$message: expected $expected, got $actual")

    @Test
    fun eachCodeIsDrawnTwentyFiveMillimetresSquareInItsItemsColour() {
        val labels = printLabels(supplies)
        val sheet = QrLabelLayout.layout(labels.size, 612.0, 792.0)
        val canvas = RecordingCanvas()

        drawQrLabelPage(canvas, sheet.onPage(0), labels)

        val expectedColors = listOf(bagGreen, flangeOrange, barrierRose)
        sheet.onPage(0).forEach { placement ->
            val color = expectedColors[placement.index]
            val code = canvas.rects.filter { it.color == color }
            val width = code.maxOf { it.right } - code.minOf { it.left }
            val height = code.maxOf { it.bottom } - code.minOf { it.top }
            assertClose(70.866, width, "label ${placement.index} width in points")
            assertClose(70.866, height, "label ${placement.index} height in points")
            assertClose(placement.qr.left, code.minOf { it.left }, "label ${placement.index} left")
            assertClose(placement.qr.top, code.minOf { it.top }, "label ${placement.index} top")
        }
        assertEquals(setOf(bagGreen, flangeOrange, barrierRose), canvas.rects.map { it.color }.toSet())
    }

    @Test
    fun nameIsInTheItemsColourAndTheLinkInSmallGrey() {
        val labels = printLabels(supplies)
        val sheet = QrLabelLayout.layout(labels.size, 612.0, 792.0)
        val canvas = RecordingCanvas()

        drawQrLabelPage(canvas, sheet.onPage(0), labels)

        val bag = sheet.placements[0]
        val name = canvas.texts.single { it.text == "Bag" }
        assertEquals(bagGreen, name.color)
        assertClose(bag.nameBaseline, name.y, "name baseline")
        assertClose(bag.cell.centerX, name.x, "name centre")
        val link = canvas.texts.single { it.text == "ostomate://log?item=bag" }
        assertEquals(QrLabelLayout.LINK_COLOR, link.color)
        assertEquals(6f, link.size)
        assertClose(bag.linkBaseline, link.y, "link baseline")
        assertEquals(
            listOf("ostomate://log?item=bag", "ostomate://log?item=flange", "ostomate://log?item=id:9"),
            canvas.texts.filter { it.color == QrLabelLayout.LINK_COLOR }.map { it.text },
        )
    }

    @Test
    fun aLongNameIsShortenedToItsLabel() {
        val long =
            testSupply(id = 5, name = "Extra-wide convex barrier ring with a very long name", kind = SupplyKind.CUSTOM)
        val labels = printLabels(listOf(long))
        val canvas = RecordingCanvas()

        drawQrLabelPage(canvas, QrLabelLayout.layout(1, 612.0, 792.0).onPage(0), labels)

        val name = canvas.texts.first { it.color != QrLabelLayout.LINK_COLOR }.text
        assertTrue(name.endsWith("…") && name.length < long.name.length, "got \"$name\"")
    }

    @Test
    fun thirtyItemsPrintOnTwoPagesWithNoneDropped() {
        val many =
            (1..30).map {
                testSupply(id = it.toLong(), name = "Item $it", kind = SupplyKind.CUSTOM, colorIndex = it % 8)
            }
        val labels = printLabels(many)
        val sheet = QrLabelLayout.layout(labels.size, 612.0, 792.0)

        val drawn =
            (0 until sheet.pageCount).flatMap { page ->
                RecordingCanvas().also { drawQrLabelPage(it, sheet.onPage(page), labels) }.texts
                    .filter { it.color != QrLabelLayout.LINK_COLOR }
                    .map { it.text }
            }

        assertEquals(2, sheet.pageCount)
        assertEquals(many.map { it.name }, drawn)
    }

    @Test
    fun renderedAt300DpiTheBagCodeMeasuresTwentyFiveMillimetresAndEveryCodeDecodes() {
        val labels = printLabels(supplies)
        val sheet = QrLabelLayout.layout(labels.size, 612.0, 792.0)
        val dpi = 300
        // Letter at 300 dpi, drawn through the same points-based code a PDF page gets.
        val bitmap = Bitmap.createBitmap(2550, 3300, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap).apply { drawColor(Color.WHITE) }
        canvas.scale(dpi / 72f, dpi / 72f)
        drawQrLabelPage(canvas, sheet.onPage(0), labels)
        File("build/outputs/qr-labels").mkdirs()
        File("build/outputs/qr-labels/qr-labels-letter-300dpi.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }

        // The bag's code is the top-left one; its name, drawn below it, is excluded by row.
        val qr = sheet.placements[0].qr
        val rows = (qr.top * dpi / 72).toInt() until (qr.bottom * dpi / 72).toInt() + 2
        val columns = (qr.left * dpi / 72).toInt() - 2 until (qr.right * dpi / 72).toInt() + 2
        val green = rows.flatMap { y -> columns.filter { x -> bitmap.getPixel(x, y) == bagGreen }.map { x -> x to y } }
        val widthMm = (green.maxOf { it.first } - green.minOf { it.first } + 1) * 25.4 / dpi
        val heightMm = (green.maxOf { it.second } - green.minOf { it.second } + 1) * 25.4 / dpi
        println("Bag code at $dpi dpi: %.2f mm × %.2f mm".format(widthMm, heightMm))
        // One 300-dpi pixel is 0.085 mm; allow one at each edge.
        assertTrue(abs(widthMm - 25.0) < 0.2, "width $widthMm mm")
        assertTrue(abs(heightMm - 25.0) < 0.2, "height $heightMm mm")

        // And each coloured code still decodes to its own link.
        sheet.onPage(0).forEach { placement ->
            val pad = 20
            val left = (placement.qr.left * dpi / 72).toInt() - pad
            val top = (placement.qr.top * dpi / 72).toInt() - pad
            val size = (placement.qr.width * dpi / 72).toInt() + 2 * pad
            val pixels = IntArray(size * size)
            bitmap.getPixels(pixels, 0, size, left, top, size, size)
            val binary = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(size, size, pixels)))
            assertEquals(labels[placement.index].link, QRCodeReader().decode(binary).text)
        }
    }

    @Test
    fun sharedPngDrawsTheModulesInTheItemsColourOnWhite() {
        val png = QrCodeEncoder.encodeToPng("ostomate://log?item=bag", 512, bagGreen)
        val bitmap = BitmapFactory.decodeByteArray(png, 0, png.size)

        val colors =
            (0 until bitmap.width step 4).flatMap { x ->
                (0 until bitmap.height step 4).map { y ->
                    bitmap.getPixel(
                        x,
                        y,
                    )
                }
            }.toSet()
        assertEquals(setOf(bagGreen, Color.WHITE), colors)
    }

    @Test
    fun printLabelsCarryEachItemsLinkAndPrintColour() {
        val labels =
            printLabels(
                listOf(
                    testSupply(id = 1, name = "Bag", kind = SupplyKind.BAG),
                    testSupply(id = 2, name = "Flange", kind = SupplyKind.FLANGE),
                    testSupply(id = 9, name = "Barrier Ring", kind = SupplyKind.CUSTOM, colorIndex = 2),
                ),
            )

        assertEquals(listOf("Bag", "Flange", "Barrier Ring"), labels.map { it.name })
        assertEquals(
            listOf("ostomate://log?item=bag", "ostomate://log?item=flange", "ostomate://log?item=id:9"),
            labels.map { it.link },
        )
        assertEquals(listOf(0xFF16A34A.toInt(), 0xFFE65100.toInt(), 0xFFE11D48.toInt()), labels.map { it.color })
        labels.forEach { assertEquals(it.modules.symbolSize + 2, it.modules.size) }
    }
}
