package com.ostimate.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.ostimate.app.data.ChangeEventRepository
import com.ostimate.app.data.SupplyRepository
import com.ostimate.app.domain.PredictionEngine
import com.ostimate.app.domain.SupplyKind
import kotlinx.coroutines.flow.first
import org.koin.core.context.GlobalContext
import kotlin.math.roundToInt

private data class WidgetRow(
    val name: String,
    val deepLinkItem: String,
    val onHand: Int,
    val daysRemaining: Double?,
) {
    val daysText: String
        get() =
            when {
                daysRemaining == null -> "No data"
                daysRemaining == 0.0 -> "0d left"
                else -> "~${daysRemaining.roundToInt()}d left"
            }
}

class OstimateWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val rows = loadRows()
        provideContent { WidgetContent(rows) }
    }

    private suspend fun loadRows(): List<WidgetRow> {
        val koin =
            try {
                GlobalContext.get()
            } catch (_: Exception) {
                return emptyList()
            }
        return try {
            val supplyRepo = koin.get<SupplyRepository>()
            val eventRepo = koin.get<ChangeEventRepository>()
            val supplies = supplyRepo.observeSupplies().first()
            val allEvents = eventRepo.observeEvents().first()
            val bySupply = allEvents.groupBy { it.event.supplyTypeId }

            supplies.map { supply ->
                val timestamps = bySupply[supply.id]?.map { it.event.timestampMillis } ?: emptyList()
                val daysRemaining = PredictionEngine.daysRemainingFromHistory(supply.onHand, timestamps)
                val item =
                    when (supply.kind) {
                        SupplyKind.BAG -> "bag"
                        SupplyKind.FLANGE -> "flange"
                        SupplyKind.CUSTOM -> "id:${supply.id}"
                    }
                WidgetRow(
                    name = supply.name,
                    deepLinkItem = item,
                    onHand = supply.onHand,
                    daysRemaining = daysRemaining,
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}

@Composable
private fun WidgetContent(rows: List<WidgetRow>) {
    val bg = ColorProvider(androidx.compose.ui.graphics.Color(0xFFF5F5F5))
    val primary = ColorProvider(androidx.compose.ui.graphics.Color(0xFF1A6FC4))
    val onSurface = ColorProvider(androidx.compose.ui.graphics.Color(0xFF1C1B1F))
    val subtle = ColorProvider(androidx.compose.ui.graphics.Color(0xFF79747E))

    Column(
        modifier = GlanceModifier.fillMaxSize().background(bg).padding(12.dp),
    ) {
        Text(
            "Ostimate",
            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = primary),
        )
        if (rows.isEmpty()) {
            Text(
                "Open app to load supplies",
                style = TextStyle(fontSize = 12.sp, color = subtle),
                modifier = GlanceModifier.padding(top = 8.dp),
            )
        } else {
            rows.forEach { row ->
                WidgetRow(row = row, primary = primary, onSurface = onSurface, subtle = subtle)
            }
        }
    }
}

@Composable
private fun WidgetRow(
    row: WidgetRow,
    primary: ColorProvider,
    onSurface: ColorProvider,
    subtle: ColorProvider,
) {
    val logIntent =
        Intent(Intent.ACTION_VIEW, Uri.parse("ostimate://log?item=${row.deepLinkItem}"))
            .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }

    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                row.name,
                style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = onSurface),
                maxLines = 1,
            )
            Text(
                row.daysText,
                style = TextStyle(fontSize = 11.sp, color = subtle),
            )
        }
        Spacer(GlanceModifier.width(6.dp))
        androidx.glance.Button(
            text = "Log",
            onClick = actionStartActivity(logIntent),
        )
    }
}

class OstimateWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = OstimateWidget()
}
