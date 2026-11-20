package com.ostomate.app.platform

import androidx.compose.ui.graphics.toArgb
import com.ostomate.app.data.db.SupplyTypeEntity
import com.ostomate.app.domain.SupplyLink
import com.ostomate.app.ui.theme.supplyPrintColor
import kotlin.math.floor

private const val POINTS_PER_INCH = 72.0
private const val MM_PER_INCH = 25.4

/** Millimetres to points (1/72 in): the unit of an Android PDF page and of iOS's paper rect. */
fun mmToPoints(mm: Double): Double = mm * POINTS_PER_INCH / MM_PER_INCH

data class PrintRect(
    val left: Double,
    val top: Double,
    val width: Double,
    val height: Double,
) {
    val right: Double get() = left + width
    val bottom: Double get() = top + height
    val centerX: Double get() = left + width / 2
}

/** Where one label goes: its cell, the QR symbol inside it, and the baselines of its two lines of text. */
data class LabelPlacement(
    val index: Int,
    val page: Int,
    val column: Int,
    val row: Int,
    val cell: PrintRect,
    val qr: PrintRect,
    val nameBaseline: Double,
    val linkBaseline: Double,
)

class QrLabelSheet(
    val columns: Int,
    val rows: Int,
    val placements: List<LabelPlacement>,
) {
    val labelsPerPage: Int get() = columns * rows
    val pageCount: Int get() = (placements.size + labelsPerPage - 1) / labelsPerPage

    fun onPage(page: Int): List<LabelPlacement> = placements.filter { it.page == page }
}

/**
 * Label sheet layout shared by the Android and iOS printers, in points on the physical page,
 * so a label prints at its true size at 100% scale. Each label is the item's QR code (the
 * symbol 25 mm square; its 1-module quiet zone is white space around it), the item's name and
 * its link. Labels fill a grid row by row, centred across the page, and run on to further
 * pages rather than being dropped.
 */
object QrLabelLayout {
    /** Bobby's choice (2026-11-20): both phones read every test code down to 8 mm. */
    const val QR_SIZE_MM = 25.0
    val qrSizePt: Double = mmToPoints(QR_SIZE_MM)

    const val MARGIN_PT = 36.0
    const val CELL_WIDTH_PT = 108.0
    const val GAP_PT = 18.0
    const val NAME_TEXT_PT = 9.0
    const val LINK_TEXT_PT = 6.0
    private const val NAME_BASELINE_GAP_PT = 12.0
    private const val LINK_BASELINE_GAP_PT = 9.0
    private const val DESCENT_PT = 4.0
    val cellHeightPt: Double = qrSizePt + NAME_BASELINE_GAP_PT + LINK_BASELINE_GAP_PT + DESCENT_PT

    /** The link's grey (ARGB). */
    const val LINK_COLOR: Int = 0xFF555555.toInt()

    fun layout(
        count: Int,
        pageWidth: Double,
        pageHeight: Double,
    ): QrLabelSheet {
        val columns = fit(pageWidth, CELL_WIDTH_PT)
        val rows = fit(pageHeight, cellHeightPt)
        val gridWidth = columns * CELL_WIDTH_PT + (columns - 1) * GAP_PT
        val gridLeft = (pageWidth - gridWidth) / 2
        val perPage = columns * rows
        val placements =
            List(count) { index ->
                val slot = index % perPage
                val column = slot % columns
                val row = slot / columns
                val cell =
                    PrintRect(
                        left = gridLeft + column * (CELL_WIDTH_PT + GAP_PT),
                        top = MARGIN_PT + row * (cellHeightPt + GAP_PT),
                        width = CELL_WIDTH_PT,
                        height = cellHeightPt,
                    )
                val qr = PrintRect(cell.centerX - qrSizePt / 2, cell.top, qrSizePt, qrSizePt)
                val nameBaseline = qr.bottom + NAME_BASELINE_GAP_PT
                LabelPlacement(
                    index = index,
                    page = index / perPage,
                    column = column,
                    row = row,
                    cell = cell,
                    qr = qr,
                    nameBaseline = nameBaseline,
                    linkBaseline = nameBaseline + LINK_BASELINE_GAP_PT,
                )
            }
        return QrLabelSheet(columns, rows, placements)
    }

    /** Cells that fit between the margins; at least one, so a small page still prints. */
    private fun fit(
        pageExtent: Double,
        cellExtent: Double,
    ): Int = maxOf(1, floor((pageExtent - 2 * MARGIN_PT + GAP_PT) / (cellExtent + GAP_PT)).toInt())
}

/** The rectangles to fill for [this] code so its symbol exactly covers [symbol]. */
fun QrModules.moduleRects(symbol: PrintRect): List<PrintRect> {
    val module = symbol.width / symbolSize
    return darkRuns().map { run ->
        PrintRect(
            left = symbol.left + (run.start - quietZone) * module,
            top = symbol.top + (run.row - quietZone) * module,
            width = run.length * module,
            height = module,
        )
    }
}

/** One item's label: what both printers draw. */
class PrintLabel(
    val name: String,
    val link: String,
    val color: Int,
    val modules: QrModules,
)

fun printLabels(supplies: List<SupplyTypeEntity>): List<PrintLabel> =
    supplies.mapNotNull { supply ->
        val link = SupplyLink.of(supply.kind, supply.id)
        val modules = QrCodeEncoder.encodeModules(link) ?: return@mapNotNull null
        PrintLabel(supply.name, link, supplyPrintColor(supply.kind, supply.colorIndex).toArgb(), modules)
    }
