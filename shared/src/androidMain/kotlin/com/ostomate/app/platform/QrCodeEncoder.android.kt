package com.ostomate.app.platform

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.io.ByteArrayOutputStream

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual object QrCodeEncoder {
    actual fun encodeToPng(url: String, size: Int): ByteArray {
        val matrix =
            try {
                QRCodeWriter().encode(
                    url,
                    BarcodeFormat.QR_CODE,
                    size,
                    size,
                    mapOf(EncodeHintType.MARGIN to 1),
                )
            } catch (e: Exception) {
                return ByteArray(0)
            }
        val bitmap = Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
        for (x in 0 until matrix.width) {
            for (y in 0 until matrix.height) {
                bitmap.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        return ByteArrayOutputStream().also {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }.toByteArray()
    }
}
