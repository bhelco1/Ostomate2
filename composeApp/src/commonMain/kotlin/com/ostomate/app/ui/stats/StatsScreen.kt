package com.ostomate.app.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ostomate.app.resources.Res
import com.ostomate.app.resources.cd_sparkline
import com.ostomate.app.resources.stats_avg_days
import com.ostomate.app.resources.stats_changes_count
import com.ostomate.app.resources.stats_days_between
import com.ostomate.app.resources.stats_no_events
import com.ostomate.app.resources.stats_period_month
import com.ostomate.app.resources.stats_period_week
import com.ostomate.app.resources.stats_period_year
import com.ostomate.app.ui.components.Pill
import com.ostomate.app.ui.theme.supplyColor
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.roundToInt

@Composable
fun StatsScreen(viewModel: StatsViewModel = koinViewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(contentWindowInsets = WindowInsets(0)) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(12.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                StatsPeriod.entries.forEach { period ->
                    val label =
                        when (period) {
                            StatsPeriod.WEEK -> stringResource(Res.string.stats_period_week)
                            StatsPeriod.MONTH -> stringResource(Res.string.stats_period_month)
                            StatsPeriod.YEAR -> stringResource(Res.string.stats_period_year)
                        }
                    FilterChip(
                        selected = uiState.period == period,
                        onClick = { viewModel.selectPeriod(period) },
                        label = { Text(label) },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            if (uiState.rows.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(Res.string.stats_no_events),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(uiState.rows, key = { it.supplyId }) { row ->
                        StatsCard(row = row)
                    }
                    uiState.summaryLine?.let { summary ->
                        item {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = summary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsCard(row: SupplyStats) {
    val accentColor = supplyColor(row.kind)

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
            // Header: supply name + change count pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    row.supplyName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Pill(label = stringResource(Res.string.stats_changes_count, row.countInPeriod), kind = row.kind)
            }

            Spacer(Modifier.height(10.dp))

            // Avg days + sparkline
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    val avg = row.avgDaysBetween
                    if (avg != null) {
                        Text(
                            stringResource(Res.string.stats_avg_days, avg.roundToInt()),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(
                        stringResource(Res.string.stats_days_between),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (row.periodTimestamps.size >= 2) {
                    Sparkline(
                        timestamps = row.periodTimestamps,
                        color = accentColor,
                        modifier = Modifier.size(width = 140.dp, height = 44.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Sparkline(
    timestamps: List<Long>,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val points = remember(timestamps) { timestamps }
    val sparklineDesc = stringResource(Res.string.cd_sparkline, points.size)

    androidx.compose.foundation.Canvas(modifier = modifier.semantics { contentDescription = sparklineDesc }) {
        if (points.size < 2) return@Canvas
        val minT = points.first().toFloat()
        val maxT = points.last().toFloat()
        val rangeT = (maxT - minT).coerceAtLeast(1f)

        // Treat each event as a "tick" on the x-axis; y shows recency (no y data, so flat wave)
        // Instead: bucket by equal x divisions and show count per bucket as height.
        val buckets = 8
        val bucketSize = rangeT / buckets
        val counts = IntArray(buckets)
        for (t in points) {
            val b = (((t - minT) / bucketSize).toInt()).coerceIn(0, buckets - 1)
            counts[b]++
        }
        val maxCount = counts.max().coerceAtLeast(1)

        val path = Path()
        counts.forEachIndexed { i, count ->
            val x = size.width * i / (buckets - 1)
            val y = size.height * (1f - count.toFloat() / maxCount)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}
