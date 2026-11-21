package com.ostomate.app.platform

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.print.pdf.PrintedPdfDocument
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.ostomate.app.data.db.SupplyTypeEntity
import java.io.FileOutputStream

private const val MILS_PER_INCH = 1000.0
private const val POINTS_PER_INCH = 72.0

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class QrPrinter(private val context: Context) {
    actual fun isPrintingAvailable(): Boolean = true

    actual fun print(supplies: List<SupplyTypeEntity>) {
        val labels = printLabels(supplies)
        if (labels.isEmpty()) return
        print(
            jobName = "Ostomate QR Labels",
            fileName = "ostomate_qr_labels.pdf",
            pages =
                object : PrintPages {
                    override fun count(
                        width: Double,
                        height: Double,
                    ) = QrLabelLayout.layout(labels.size, width, height).pageCount

                    override fun draw(
                        canvas: Canvas,
                        page: Int,
                        width: Double,
                        height: Double,
                    ) = drawQrLabelPage(canvas, QrLabelLayout.layout(labels.size, width, height).onPage(page), labels)
                },
        )
    }

    actual fun printFullPage(supply: SupplyTypeEntity) {
        val label = printLabels(listOf(supply)).singleOrNull() ?: return
        print(
            jobName = "Ostomate QR ${label.name}",
            fileName = "ostomate_qr_code.pdf",
            pages =
                object : PrintPages {
                    override fun count(
                        width: Double,
                        height: Double,
                    ) = 1

                    override fun draw(
                        canvas: Canvas,
                        page: Int,
                        width: Double,
                        height: Double,
                    ) = drawQrFullPage(canvas, label, width, height)
                },
        )
    }

    private fun print(
        jobName: String,
        fileName: String,
        pages: PrintPages,
    ) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        printManager.print(
            jobName,
            QrPrintAdapter(context, fileName, pages),
            PrintAttributes.Builder()
                .setMediaSize(PrintAttributes.MediaSize.NA_LETTER)
                .setResolution(PrintAttributes.Resolution("default", "Default", 300, 300))
                .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                .build(),
        )
    }
}

@Composable
actual fun rememberQrPrinter(): QrPrinter {
    val context = LocalContext.current
    return remember(context) { QrPrinter(context) }
}

/** What a print job draws, in points on the paper the print dialog has chosen. */
private interface PrintPages {
    fun count(
        width: Double,
        height: Double,
    ): Int

    fun draw(
        canvas: Canvas,
        page: Int,
        width: Double,
        height: Double,
    )
}

private class QrPrintAdapter(
    private val context: Context,
    private val fileName: String,
    private val content: PrintPages,
) : PrintDocumentAdapter() {
    private var printAttributes: PrintAttributes = PrintAttributes.Builder().build()
    private var pageWidth = 0.0
    private var pageHeight = 0.0
    private var pageCount: Int? = null

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback,
        extras: Bundle?,
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback.onLayoutCancelled()
            return
        }
        val media = newAttributes.mediaSize ?: PrintAttributes.MediaSize.NA_LETTER
        printAttributes = newAttributes
        // A PDF page measures in points; the chosen paper size comes in mils (1/1000 in).
        pageWidth = media.widthMils * POINTS_PER_INCH / MILS_PER_INCH
        pageHeight = media.heightMils * POINTS_PER_INCH / MILS_PER_INCH
        val count = content.count(pageWidth, pageHeight)
        val changed = oldAttributes != newAttributes || pageCount == null
        pageCount = count
        val info =
            PrintDocumentInfo
                .Builder(fileName)
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .setPageCount(count)
                .build()
        callback.onLayoutFinished(info, changed)
    }

    // The print framework waits for onWriteFinished or onWriteFailed; any failure while drawing
    // or writing the PDF must reach it as onWriteFailed, or the print dialog hangs.
    @Suppress("TooGenericExceptionCaught")
    override fun onWrite(
        pages: Array<out PageRange>?,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback,
    ) {
        val count = pageCount
        if (count == null) {
            callback.onWriteFailed("not laid out")
            return
        }
        val wanted = pages?.takeUnless { it.isEmpty() } ?: arrayOf(PageRange.ALL_PAGES)
        val pdf = PrintedPdfDocument(context, printAttributes)
        try {
            for (pageNumber in 0 until count) {
                if (wanted.none { pageNumber in it.start..it.end }) continue
                if (cancellationSignal?.isCanceled == true) {
                    callback.onWriteCancelled()
                    return
                }
                val page = pdf.startPage(pageNumber)
                // PdfDocument moves the origin to the printer's content rect; the layout is
                // for the whole sheet, so move it back to the paper's corner.
                page.canvas.translate(-page.info.contentRect.left.toFloat(), -page.info.contentRect.top.toFloat())
                content.draw(page.canvas, pageNumber, pageWidth, pageHeight)
                pdf.finishPage(page)
            }
            FileOutputStream(destination.fileDescriptor).use { pdf.writeTo(it) }
            callback.onWriteFinished(wanted)
        } catch (e: Exception) {
            callback.onWriteFailed(e.message)
        } finally {
            pdf.close()
        }
    }
}

/** Draws one page of labels; [canvas] is in points, as a PDF page is. */
internal fun drawQrLabelPage(
    canvas: Canvas,
    placements: List<LabelPlacement>,
    labels: List<PrintLabel>,
) {
    val modulePaint = Paint().apply { style = Paint.Style.FILL }
    val namePaint =
        Paint().apply {
            textSize = QrLabelLayout.NAME_TEXT_PT.toFloat()
            isFakeBoldText = true
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
    val linkPaint =
        Paint().apply {
            color = QrLabelLayout.LINK_COLOR
            textSize = QrLabelLayout.LINK_TEXT_PT.toFloat()
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

    placements.forEach { placement ->
        val label = labels[placement.index]
        drawCode(canvas, label, placement.qr, modulePaint)
        val centerX = placement.cell.centerX.toFloat()
        val width = placement.cell.width.toFloat()
        namePaint.color = label.color
        canvas.drawText(fit(label.name, namePaint, width), centerX, placement.nameBaseline.toFloat(), namePaint)
        canvas.drawText(fit(label.link, linkPaint, width), centerX, placement.linkBaseline.toFloat(), linkPaint)
    }
}

/** Draws [label]'s code alone on a page of [pageWidth] × [pageHeight] points: no name, no link. */
internal fun drawQrFullPage(
    canvas: Canvas,
    label: PrintLabel,
    pageWidth: Double,
    pageHeight: Double,
) = drawCode(canvas, label, QrLabelLayout.fullPage(pageWidth, pageHeight), Paint().apply { style = Paint.Style.FILL })

/** [label]'s modules in its colour, covering [symbol] exactly. */
private fun drawCode(
    canvas: Canvas,
    label: PrintLabel,
    symbol: PrintRect,
    paint: Paint,
) {
    paint.color = label.color
    // One path, filled once: rectangles filled one by one leave hairline seams where
    // anti-aliased edges meet, in PDF viewers and on paper.
    val code = Path()
    label.modules.moduleRects(symbol).forEach {
        code.addRect(it.left.toFloat(), it.top.toFloat(), it.right.toFloat(), it.bottom.toFloat(), Path.Direction.CW)
    }
    canvas.drawPath(code, paint)
}

/** [text] cut short with an ellipsis if it is wider than [width]. */
private fun fit(
    text: String,
    paint: Paint,
    width: Float,
): String {
    if (paint.measureText(text) <= width) return text
    val kept = paint.breakText(text, true, width - paint.measureText(ELLIPSIS), null)
    return text.take(kept).trimEnd() + ELLIPSIS
}

private const val ELLIPSIS = "\u2026"
