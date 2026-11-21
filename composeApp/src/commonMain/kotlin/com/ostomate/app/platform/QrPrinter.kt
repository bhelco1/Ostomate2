package com.ostomate.app.platform

import androidx.compose.runtime.Composable
import com.ostomate.app.data.db.SupplyTypeEntity

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect class QrPrinter {
    fun isPrintingAvailable(): Boolean

    /** All items on a sheet of 25 mm labels. */
    fun print(supplies: List<SupplyTypeEntity>)

    /** One item's code alone, as large as fits on the page, in its colour; no name or link. */
    fun printFullPage(supply: SupplyTypeEntity)
}

@Composable
expect fun rememberQrPrinter(): QrPrinter
