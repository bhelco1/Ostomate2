@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.ostomate.app.platform

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGAffineTransformMakeScale
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGColorSpaceCreateDeviceGray
import platform.CoreGraphics.CGColorSpaceRelease
import platform.CoreGraphics.CGContextDrawImage
import platform.CoreGraphics.CGContextFillRect
import platform.CoreGraphics.CGContextRelease
import platform.CoreGraphics.CGContextSetGrayFillColor
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageGetWidth
import platform.CoreGraphics.CGImageRelease
import platform.CoreGraphics.CGRectGetWidth
import platform.CoreGraphics.CGRectMake
import platform.CoreImage.CIColor
import platform.CoreImage.CIContext
import platform.CoreImage.CIFilter
import platform.CoreImage.CIImage
import platform.CoreImage.createCGImage
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
        color: Int,
    ): ByteArray {
        val outputImage = qrImage(url) ?: return ByteArray(0)
        val inputWidth = CGRectGetWidth(outputImage.extent)
        val scale = size.toDouble() / inputWidth
        val scaled = outputImage.imageByApplyingTransform(CGAffineTransformMakeScale(scale, scale))
        val colored = tint(scaled, color) ?: return ByteArray(0)

        val uiImage = UIImage(cIImage = colored)
        val pngData = UIImagePNGRepresentation(uiImage) ?: return ByteArray(0)
        return pngData.bytes?.readBytes(pngData.length.toInt()) ?: ByteArray(0)
    }

    actual fun encodeModules(url: String): QrModules? {
        val image = qrImage(url) ?: return null
        // Null where Core Image can't render, e.g. the simulator test runner (no Metal device).
        val context = CIContext.contextWithOptions(null) ?: return null
        val cgImage = context.createCGImage(image, fromRect = image.extent) ?: return null
        val width = CGImageGetWidth(cgImage).toInt()
        val height = CGImageGetHeight(cgImage).toInt()

        // One byte of grey per pixel; Core Image makes one pixel per module. The bitmap starts
        // white, so anything left unrendered reads as light and gives no code, never a dark block.
        val pixels = ByteArray(width * height)
        pixels.usePinned { pinned ->
            val space = CGColorSpaceCreateDeviceGray()
            val bitmap =
                CGBitmapContextCreate(
                    pinned.addressOf(0),
                    width.toULong(),
                    height.toULong(),
                    8u,
                    width.toULong(),
                    space,
                    CGImageAlphaInfo.kCGImageAlphaNone.value,
                )
            val bounds = CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble())
            CGContextSetGrayFillColor(bitmap, 1.0, 1.0)
            CGContextFillRect(bitmap, bounds)
            CGContextDrawImage(bitmap, bounds, cgImage)
            CGContextRelease(bitmap)
            CGColorSpaceRelease(space)
        }
        CGImageRelease(cgImage)
        // Bitmap memory starts at the image's top row.
        return QrModules.fromGrid(width, height) { x, y -> (pixels[y * width + x].toInt() and 0xFF) < 128 }
    }

    private fun qrImage(url: String): CIImage? {
        if (url.isEmpty()) return null
        val urlBytes = url.encodeToByteArray()
        val nsData = urlBytes.usePinned { NSData.dataWithBytes(it.addressOf(0), urlBytes.size.toULong()) }

        val filter = CIFilter.filterWithName("CIQRCodeGenerator") ?: return null
        filter.setValue(nsData, forKey = "inputMessage")
        filter.setValue("M", forKey = "inputCorrectionLevel")
        return filter.outputImage
    }

    /** Dark modules in [color] (ARGB), light ones white. */
    private fun tint(
        image: CIImage,
        color: Int,
    ): CIImage? {
        val filter = CIFilter.filterWithName("CIFalseColor") ?: return null
        filter.setValue(image, forKey = "inputImage")
        filter.setValue(
            CIColor.colorWithRed(
                ((color shr 16) and 0xFF) / 255.0,
                green = ((color shr 8) and 0xFF) / 255.0,
                blue = (color and 0xFF) / 255.0,
            ),
            forKey = "inputColor0",
        )
        filter.setValue(CIColor.colorWithRed(1.0, green = 1.0, blue = 1.0), forKey = "inputColor1")
        return filter.outputImage
    }
}
