package com.ostomate.app.platform

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual object QrCodeEncoder {
    actual fun encodeModules(url: String): QrModules? {
        val matrix = encode(url) ?: return null
        return QrModules.fromGrid(matrix.width, matrix.height) { x, y -> matrix[x, y] }
    }

    // ZXing reports input it cannot encode as WriterException or IllegalArgumentException; any
    // failure means "no code" (null), never a crash of the screen printing it.
    // Size 0 asks ZXing for one cell per module, plus the 1-module margin.
    private fun encode(url: String): BitMatrix? =
        try {
            QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 1))
        } catch (ignored: WriterException) {
            null
        } catch (ignored: IllegalArgumentException) {
            null
        }
}
