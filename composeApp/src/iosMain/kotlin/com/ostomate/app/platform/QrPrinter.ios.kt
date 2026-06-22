@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.ostomate.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.ostomate.app.data.db.SupplyTypeEntity
import com.ostomate.app.domain.SupplyKind
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.dataWithBytes
import platform.UIKit.UIImage
import platform.UIKit.UIPrintInfo
import platform.UIKit.UIPrintInteractionController

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class QrPrinter {
    actual fun isPrintingAvailable(): Boolean = UIPrintInteractionController.isPrintingAvailable()

    actual fun print(supplies: List<SupplyTypeEntity>) {
        val controller = UIPrintInteractionController.sharedPrintController()

        val info = UIPrintInfo.printInfo()
        info.jobName = "Ostomate QR Labels"
        controller.printInfo = info

        val images: List<UIImage> =
            supplies.mapNotNull { supply ->
                val png = QrCodeEncoder.encodeToPng(supplyDeepLinkUrl(supply), 512)
                if (png.isEmpty()) return@mapNotNull null
                val nsData = png.usePinned { NSData.dataWithBytes(it.addressOf(0), png.size.toULong()) }
                UIImage(data = nsData)
            }
        if (images.isEmpty()) return

        controller.printingItems = images
        controller.presentAnimated(true, completionHandler = null)
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

@Composable
actual fun rememberQrPrinter(): QrPrinter = remember { QrPrinter() }
