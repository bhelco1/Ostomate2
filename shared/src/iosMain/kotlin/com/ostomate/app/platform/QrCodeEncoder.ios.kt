@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.ostomate.app.platform

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGAffineTransformMakeScale
import platform.CoreGraphics.CGRectGetWidth
import platform.CoreImage.CIFilter
import platform.CoreImage.filterWithName
import platform.Foundation.NSData
import platform.Foundation.dataWithBytes
import platform.Foundation.setValue
import platform.UIKit.UIImage
import platform.UIKit.UIImagePNGRepresentation

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual object QrCodeEncoder {
    actual fun encodeToPng(
        url: String,
        size: Int,
    ): ByteArray {
        val urlBytes = url.encodeToByteArray()
        val nsData = urlBytes.usePinned { NSData.dataWithBytes(it.addressOf(0), urlBytes.size.toULong()) }

        val filter = CIFilter.filterWithName("CIQRCodeGenerator") ?: return ByteArray(0)
        filter.setValue(nsData, forKey = "inputMessage")
        filter.setValue("M", forKey = "inputCorrectionLevel")

        val outputImage = filter.outputImage ?: return ByteArray(0)
        val inputWidth = CGRectGetWidth(outputImage.extent)
        val scale = size.toDouble() / inputWidth
        val scaled = outputImage.imageByApplyingTransform(CGAffineTransformMakeScale(scale, scale))

        val uiImage = UIImage(cIImage = scaled)
        val pngData = UIImagePNGRepresentation(uiImage) ?: return ByteArray(0)
        return pngData.bytes?.readBytes(pngData.length.toInt()) ?: ByteArray(0)
    }
}
