package com.ostimate.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ostimate.app.domain.SupplyKind
import com.ostimate.app.ui.theme.supplyColor
import kotlin.math.roundToInt

@Composable
fun SupplyCard(
    name: String,
    kind: SupplyKind,
    onHand: Int,
    daysRemaining: Double?,
    sampleCount: Int,
    warnThresholdDays: Int,
    onLogClick: () -> Unit,
    onEditCountClick: (() -> Unit)? = null,
    overflowMenuContent: (@Composable ColumnScope.(dismiss: () -> Unit) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var overflowExpanded by remember { mutableStateOf(false) }
    val accent = supplyColor(kind)
    val isWarning = daysRemaining != null && daysRemaining < warnThresholdDays
    val daysText =
        when {
            daysRemaining == null -> "Not enough data yet"
            daysRemaining == 0.0 -> "0 days remaining"
            else -> "~${daysRemaining.roundToInt()} days remaining · based on $sampleCount changes"
        }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(
                    Modifier
                        .size(10.dp)
                        .background(accent, CircleShape),
                )
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier =
                        Modifier
                            .weight(1f)
                            .padding(start = 8.dp),
                )
                Box {
                    IconButton(
                        onClick = { overflowExpanded = true },
                        modifier = Modifier.size(36.dp).testTag("overflowButton"),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "More options for $name",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                    }
                    if (overflowMenuContent != null) {
                        DropdownMenu(
                            expanded = overflowExpanded,
                            onDismissRequest = { overflowExpanded = false },
                        ) {
                            overflowMenuContent { overflowExpanded = false }
                        }
                    }
                }
            }

            val countRowModifier =
                if (onEditCountClick != null) {
                    Modifier
                        .testTag("editCountRow")
                        .clickable(onClickLabel = "Edit count") { onEditCountClick() }
                } else {
                    Modifier
                }
            Row(verticalAlignment = Alignment.Bottom, modifier = countRowModifier) {
                Text(
                    text = onHand.toString(),
                    style = MaterialTheme.typography.displayMedium,
                    color = accent,
                )
                Text(
                    text = " on hand",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }

            Text(
                text = daysText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )

            if (isWarning && daysRemaining != null) {
                WarningBanner(
                    message =
                        "Time to reorder. About ${daysRemaining.roundToInt()} days of " +
                            "${name.lowercase()} left — below your $warnThresholdDays-day warning.",
                )
            }

            Spacer(Modifier.height(4.dp))

            LogButton(
                label = "Log ${name.lowercase()} change",
                color = accent,
                onClick = onLogClick,
                modifier = Modifier.testTag("logButton"),
            )
        }
    }
}
