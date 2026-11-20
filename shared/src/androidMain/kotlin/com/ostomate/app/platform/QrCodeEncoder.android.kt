package com.ostomate.app.platform

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import java.io.ByteArrayOutputStream

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual object QrCodeEncoder {
    // ZXing reports input it cannot encode as WriterException or IllegalArgumentException; any
    // failure means "no image" (an empty array), never a crash of the screen showing the code.
    actual fun encodeToPng(
        url: String,
        size: Int,
        color: Int,
    ): ByteArray {
        val matrix = encode(url, size) ?: return ByteArray(0)
        val bitmap = Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
        for (x in 0 until matrix.width) {
            for (y in 0 until matrix.height) {
                bitmap.setPixel(x, y, if (matrix[x, y]) color else Color.WHITE)
            }
        }
        return ByteArrayOutputStream().also {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }.toByteArray()
    }

    actual fun encodeModules(url: String): QrModules? {
        // Size 0 asks ZXing for one cell per module, plus the 1-module margin.
        val matrix = encode(url, 0) ?: return null
        return QrModules.fromGrid(matrix.width, matrix.height) { x, y -> matrix[x, y] }
    }

    private fun encode(
        url: String,
        size: Int,
    ): BitMatrix? =
        try {
            QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
        } catch (e: WriterException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
}
