package com.ostomate.app.platform

import androidx.compose.runtime.Composable
import com.ostomate.app.data.db.SupplyTypeEntity

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect class QrPrinter {
    fun print(supplies: List<SupplyTypeEntity>)
}

@Composable
expect fun rememberQrPrinter(): QrPrinter
