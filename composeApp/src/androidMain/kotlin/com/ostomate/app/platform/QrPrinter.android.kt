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
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        printManager.print(
            "Ostomate QR Labels",
            QrLabelsPrintAdapter(context, labels),
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

private class QrLabelsPrintAdapter(
    private val context: Context,
    private val labels: List<PrintLabel>,
) : PrintDocumentAdapter() {
    private var printAttributes: PrintAttributes = PrintAttributes.Builder().build()
    private var sheet: QrLabelSheet? = null

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
        val laidOut =
            QrLabelLayout.layout(
                count = labels.size,
                pageWidth = media.widthMils * POINTS_PER_INCH / MILS_PER_INCH,
                pageHeight = media.heightMils * POINTS_PER_INCH / MILS_PER_INCH,
            )
        val changed = oldAttributes != newAttributes || sheet == null
        sheet = laidOut
        val info =
            PrintDocumentInfo
                .Builder("ostomate_qr_labels.pdf")
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .setPageCount(laidOut.pageCount)
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
        val laidOut = sheet
        if (laidOut == null) {
            callback.onWriteFailed("not laid out")
            return
        }
        val wanted = pages?.takeUnless { it.isEmpty() } ?: arrayOf(PageRange.ALL_PAGES)
        val pdf = PrintedPdfDocument(context, printAttributes)
        try {
            for (pageNumber in 0 until laidOut.pageCount) {
                if (wanted.none { pageNumber in it.start..it.end }) continue
                if (cancellationSignal?.isCanceled == true) {
                    callback.onWriteCancelled()
                    return
                }
                val page = pdf.startPage(pageNumber)
                // PdfDocument moves the origin to the printer's content rect; the layout is
                // for the whole sheet, so move it back to the paper's corner.
                page.canvas.translate(-page.info.contentRect.left.toFloat(), -page.info.contentRect.top.toFloat())
                drawQrLabelPage(page.canvas, laidOut.onPage(pageNumber), labels)
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
        modulePaint.color = label.color
        // One path, filled once: rectangles filled one by one leave hairline seams where
        // anti-aliased edges meet, in PDF viewers and on paper.
        val code = Path()
        label.modules.moduleRects(placement.qr).forEach {
            code.addRect(
                it.left.toFloat(),
                it.top.toFloat(),
                it.right.toFloat(),
                it.bottom.toFloat(),
                Path.Direction.CW,
            )
        }
        canvas.drawPath(code, modulePaint)
        val centerX = placement.cell.centerX.toFloat()
        val width = placement.cell.width.toFloat()
        namePaint.color = label.color
        canvas.drawText(fit(label.name, namePaint, width), centerX, placement.nameBaseline.toFloat(), namePaint)
        canvas.drawText(fit(label.link, linkPaint, width), centerX, placement.linkBaseline.toFloat(), linkPaint)
    }
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
