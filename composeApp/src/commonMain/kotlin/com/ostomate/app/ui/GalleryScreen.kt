package com.ostomate.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ostomate.app.domain.SupplyKind
import com.ostomate.app.ui.components.LogButton
import com.ostomate.app.ui.components.Pill
import com.ostomate.app.ui.components.SupplyCard
import com.ostomate.app.ui.components.WarningBanner
import com.ostomate.app.ui.theme.supplyColor

// Phase 1 exit screen — shows every design-system component with light/dark parity.
// Navigate to it from HomeScreen during development; remove route in Phase 2.
@Composable
fun GalleryScreen() {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text("Design System Gallery", style = MaterialTheme.typography.headlineMedium)

        // --- Supply cards ---
        GallerySection("Supply cards") {
            SupplyCard(
                name = "Bags",
                kind = SupplyKind.BAG,
                onHand = 14,
                daysRemaining = 12.3,
                sampleCount = 10,
                warnThresholdDays = 14,
                onLogClick = {},
            )
            SupplyCard(
                name = "Flanges",
                kind = SupplyKind.FLANGE,
                onHand = 3,
                daysRemaining = 6.1,
                sampleCount = 10,
                warnThresholdDays = 14,
                onLogClick = {},
            )
            SupplyCard(
                name = "No history yet",
                kind = SupplyKind.BAG,
                onHand = 30,
                daysRemaining = null,
                sampleCount = 0,
                warnThresholdDays = 14,
                onLogClick = {},
            )
            SupplyCard(
                name = "Empty",
                kind = SupplyKind.FLANGE,
                onHand = 0,
                daysRemaining = 0.0,
                sampleCount = 10,
                warnThresholdDays = 14,
                onLogClick = {},
            )
        }

        // --- Log buttons ---
        GallerySection("Log buttons") {
            LogButton(label = "Log bag change", color = supplyColor(SupplyKind.BAG), onClick = {})
            LogButton(label = "Log flange change", color = supplyColor(SupplyKind.FLANGE), onClick = {})
            LogButton(label = "Log custom supply", color = MaterialTheme.colorScheme.primary, onClick = {})
        }

        // --- Pills ---
        GallerySection("Pills") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(label = "B", kind = SupplyKind.BAG)
                Pill(label = "Bag", kind = SupplyKind.BAG)
                Pill(label = "B ×2", kind = SupplyKind.BAG)
                Pill(label = "F", kind = SupplyKind.FLANGE)
                Pill(label = "Flange", kind = SupplyKind.FLANGE)
            }
        }

        // --- Warning banner ---
        GallerySection("Warning banner") {
            WarningBanner(message = "Time to reorder. About 6 days of flanges left — below your 14-day warning.")
            WarningBanner(message = "Supplies critically low.")
        }
    }
}

@Composable
private fun GallerySection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        HorizontalDivider()
        content()
    }
}
