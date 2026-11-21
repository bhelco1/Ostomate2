@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.ostomate.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.ostomate.app.data.db.SupplyTypeEntity
import kotlinx.cinterop.CValue
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGContextAddRect
import platform.CoreGraphics.CGContextBeginPath
import platform.CoreGraphics.CGContextFillPath
import platform.CoreGraphics.CGContextRef
import platform.CoreGraphics.CGContextSetFillColorWithColor
import platform.CoreGraphics.CGRect
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSString
import platform.Foundation.create
import platform.UIKit.NSFontAttributeName
import platform.UIKit.NSForegroundColorAttributeName
import platform.UIKit.NSLineBreakByTruncatingTail
import platform.UIKit.NSMutableParagraphStyle
import platform.UIKit.NSParagraphStyleAttributeName
import platform.UIKit.NSTextAlignmentCenter
import platform.UIKit.UIColor
import platform.UIKit.UIFont
import platform.UIKit.UIGraphicsGetCurrentContext
import platform.UIKit.UIPrintInfo
import platform.UIKit.UIPrintInfoOutputType
import platform.UIKit.UIPrintInteractionController
import platform.UIKit.UIPrintPageRenderer
import platform.UIKit.drawInRect
import platform.darwin.NSInteger

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class QrPrinter {
    actual fun isPrintingAvailable(): Boolean = UIPrintInteractionController.isPrintingAvailable()

    actual fun print(supplies: List<SupplyTypeEntity>) {
        val labels = printLabels(supplies)
        if (labels.isEmpty()) return
        present("Ostomate QR Labels", QrLabelPageRenderer(labels))
    }

    actual fun printFullPage(supply: SupplyTypeEntity) {
        val label = printLabels(listOf(supply)).singleOrNull() ?: return
        present("Ostomate QR ${label.name}", QrFullPageRenderer(label))
    }

    private fun present(
        jobName: String,
        renderer: UIPrintPageRenderer,
    ) {
        val controller = UIPrintInteractionController.sharedPrintController()

        val info = UIPrintInfo.printInfo()
        info.jobName = jobName
        info.outputType = UIPrintInfoOutputType.UIPrintInfoOutputGeneral
        controller.printInfo = info
        controller.printingItem = null
        controller.printingItems = null
        // A page renderer draws in points on the chosen paper and is never scaled to fit,
        // unlike a printing item, so the codes print at their true size.
        controller.printPageRenderer = renderer
        controller.presentAnimated(true, completionHandler = null)
    }
}

@Composable
actual fun rememberQrPrinter(): QrPrinter = remember { QrPrinter() }

private class QrLabelPageRenderer(
    private val labels: List<PrintLabel>,
) : UIPrintPageRenderer() {
    /** UIKit sets the paper before it asks for the page count, and the paper can change between previews. */
    private fun sheet(): QrLabelSheet =
        paperRect.useContents { QrLabelLayout.layout(labels.size, size.width, size.height) }

    override fun numberOfPages(): NSInteger = sheet().pageCount.toLong()

    override fun drawPageAtIndex(
        pageIndex: NSInteger,
        inRect: CValue<CGRect>,
    ) {
        val context = UIGraphicsGetCurrentContext() ?: return
        drawQrLabelPage(context, sheet().onPage(pageIndex.toInt()), labels)
    }
}

private class QrFullPageRenderer(
    private val label: PrintLabel,
) : UIPrintPageRenderer() {
    override fun numberOfPages(): NSInteger = 1

    override fun drawPageAtIndex(
        pageIndex: NSInteger,
        inRect: CValue<CGRect>,
    ) {
        val context = UIGraphicsGetCurrentContext() ?: return
        val symbol = paperRect.useContents { QrLabelLayout.fullPage(size.width, size.height) }
        fillCode(context, label, symbol)
    }
}

/** Draws one page of labels; the context is in points with its origin at the paper's top-left corner. */
internal fun drawQrLabelPage(
    context: CGContextRef,
    placements: List<LabelPlacement>,
    labels: List<PrintLabel>,
) {
    val nameFont = UIFont.boldSystemFontOfSize(QrLabelLayout.NAME_TEXT_PT)
    val linkFont = UIFont.systemFontOfSize(QrLabelLayout.LINK_TEXT_PT)
    val centred =
        NSMutableParagraphStyle().apply {
            setAlignment(NSTextAlignmentCenter)
            setLineBreakMode(NSLineBreakByTruncatingTail)
        }
    val linkColor = uiColor(QrLabelLayout.LINK_COLOR)

    placements.forEach { placement ->
        val label = labels[placement.index]
        val color = uiColor(label.color)
        fillCode(context, label, placement.qr)
        drawLine(label.name, placement, placement.nameBaseline, nameFont, color, centred)
        drawLine(label.link, placement, placement.linkBaseline, linkFont, linkColor, centred)
    }
}

/** [label]'s modules in its colour, covering [symbol] exactly. */
private fun fillCode(
    context: CGContextRef,
    label: PrintLabel,
    symbol: PrintRect,
) {
    CGContextSetFillColorWithColor(context, uiColor(label.color).CGColor)
    // One path, filled once: rectangles filled one by one leave hairline seams where
    // anti-aliased edges meet.
    CGContextBeginPath(context)
    label.modules.moduleRects(symbol).forEach {
        CGContextAddRect(context, CGRectMake(it.left, it.top, it.width, it.height))
    }
    CGContextFillPath(context)
}

private fun drawLine(
    text: String,
    placement: LabelPlacement,
    baseline: Double,
    font: UIFont,
    color: UIColor,
    style: NSMutableParagraphStyle,
) {
    val rect = CGRectMake(placement.cell.left, baseline - font.ascender, placement.cell.width, font.lineHeight)
    NSString.create(string = text).drawInRect(
        rect,
        withAttributes =
            mapOf<Any?, Any?>(
                NSFontAttributeName to font,
                NSForegroundColorAttributeName to color,
                NSParagraphStyleAttributeName to style,
            ),
    )
}

private fun uiColor(argb: Int): UIColor =
    UIColor.colorWithRed(
        red = ((argb shr 16) and 0xFF) / 255.0,
        green = ((argb shr 8) and 0xFF) / 255.0,
        blue = (argb and 0xFF) / 255.0,
        alpha = ((argb ushr 24) and 0xFF) / 255.0,
    )
