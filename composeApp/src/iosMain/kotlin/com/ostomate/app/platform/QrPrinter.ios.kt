package com.ostomate.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.ostomate.app.data.db.SupplyTypeEntity

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class QrPrinter {
    actual fun print(supplies: List<SupplyTypeEntity>) {
        // TODO: iOS printing via UIPrintInteractionController
    }
}

@Composable
actual fun rememberQrPrinter(): QrPrinter = remember { QrPrinter() }
