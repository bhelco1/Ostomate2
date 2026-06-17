package com.ostomate.app.platform

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.ostomate.app.data.db.SupplyTypeEntity
import com.ostomate.app.domain.SupplyKind
import java.io.FileOutputStream

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class QrPrinter(private val context: Context) {
    actual fun print(supplies: List<SupplyTypeEntity>) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        printManager.print(
            "Ostomate QR Labels",
            QrLabelsPrintAdapter(context, supplies),
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
    private val supplies: List<SupplyTypeEntity>,
) : PrintDocumentAdapter() {
    private var printAttributes: PrintAttributes = PrintAttributes.Builder().build()

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
        printAttributes = newAttributes
        val info =
            PrintDocumentInfo
                .Builder("ostomate_qr_labels.pdf")
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .setPageCount(1)
                .build()
        callback.onLayoutFinished(info, oldAttributes != newAttributes)
    }

    override fun onWrite(
        pages: Array<out PageRange>?,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback,
    ) {
        val pdf = PrintedPdfDocument(context, printAttributes)
        try {
            val page = pdf.startPage(0)
            drawLabels(page.canvas, pdf.pageWidth, pdf.pageHeight)
            pdf.finishPage(page)
            FileOutputStream(destination.fileDescriptor).use { pdf.writeTo(it) }
            callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (e: Exception) {
            callback.onWriteFailed(e.message)
        } finally {
            pdf.close()
        }
    }

    private fun drawLabels(
        canvas: Canvas,
        pageWidth: Int,
        pageHeight: Int,
    ) {
        val margin = 36f // 0.5 inch in points
        val cols = 2
        val colWidth = (pageWidth - margin * (cols + 1)) / cols
        val qrSize = minOf(colWidth - 16f, 180f)
        val cellHeight = qrSize + 56f // QR + name + URL text

        val namePaint =
            Paint().apply {
                color = Color.BLACK
                textSize = 14f
                isFakeBoldText = true
                isAntiAlias = true
            }
        val urlPaint =
            Paint().apply {
                color = Color.DKGRAY
                textSize = 9f
                isAntiAlias = true
            }

        supplies.forEachIndexed { index, supply ->
            val col = index % cols
            val row = index / cols

            val cellLeft = margin + col * (colWidth + margin)
            val cellTop = margin + row * (cellHeight + margin)

            if (cellTop + cellHeight > pageHeight - margin) return

            val url = supplyDeepLinkUrl(supply)

            // Draw QR code directly to canvas
            drawQrCode(canvas, url, cellLeft + (colWidth - qrSize) / 2f, cellTop, qrSize)

            // Supply name centered below QR
            val nameX = cellLeft + colWidth / 2f - namePaint.measureText(supply.name) / 2f
            canvas.drawText(supply.name, nameX, cellTop + qrSize + 18f, namePaint)

            // URL in small monospace below name
            val urlX = cellLeft + colWidth / 2f - urlPaint.measureText(url) / 2f
            canvas.drawText(url, urlX, cellTop + qrSize + 32f, urlPaint)
        }
    }

    private fun drawQrCode(
        canvas: Canvas,
        data: String,
        left: Float,
        top: Float,
        size: Float,
    ) {
        val matrix =
            try {
                QRCodeWriter().encode(
                    data,
                    BarcodeFormat.QR_CODE,
                    size.toInt(),
                    size.toInt(),
                    mapOf(EncodeHintType.MARGIN to 1),
                )
            } catch (e: Exception) {
                return
            }

        val paint = Paint().apply { color = Color.BLACK }
        val cellSize = size / matrix.width

        for (x in 0 until matrix.width) {
            for (y in 0 until matrix.height) {
                if (matrix[x, y]) {
                    canvas.drawRect(
                        left + x * cellSize,
                        top + y * cellSize,
                        left + (x + 1) * cellSize,
                        top + (y + 1) * cellSize,
                        paint,
                    )
                }
            }
        }
    }

    private fun supplyDeepLinkUrl(supply: SupplyTypeEntity): String {
        val item =
            when (supply.kind) {
                SupplyKind.BAG -> "bag"
                SupplyKind.FLANGE -> "flange"
                SupplyKind.CUSTOM -> "id:${supply.id}"
            }
        return "ostomate://log?item=$item"
    }
}
