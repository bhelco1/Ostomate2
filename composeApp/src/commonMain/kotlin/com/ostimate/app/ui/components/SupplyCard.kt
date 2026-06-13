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
import com.ostimate.app.resources.Res
import com.ostimate.app.resources.cd_supply_more_options
import com.ostimate.app.resources.supply_days_remaining
import com.ostimate.app.resources.supply_edit_count_label
import com.ostimate.app.resources.supply_log_button_label
import com.ostimate.app.resources.supply_no_data
import com.ostimate.app.resources.supply_on_hand
import com.ostimate.app.resources.supply_warning_banner
import com.ostimate.app.resources.supply_zero_days
import com.ostimate.app.ui.theme.supplyColor
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource

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
            daysRemaining == null -> stringResource(Res.string.supply_no_data)
            daysRemaining == 0.0 -> stringResource(Res.string.supply_zero_days)
            else -> stringResource(Res.string.supply_days_remaining, daysRemaining.roundToInt(), sampleCount)
        }
    val editCountLabel = stringResource(Res.string.supply_edit_count_label)

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
                            contentDescription = stringResource(Res.string.cd_supply_more_options, name),
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
                        .clickable(onClickLabel = editCountLabel) { onEditCountClick() }
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
                    text = stringResource(Res.string.supply_on_hand),
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
                    message = stringResource(
                        Res.string.supply_warning_banner,
                        daysRemaining.roundToInt(),
                        name.lowercase(),
                        warnThresholdDays,
                    ),
                )
            }

            Spacer(Modifier.height(4.dp))

            LogButton(
                label = stringResource(Res.string.supply_log_button_label, name.lowercase()),
                color = accent,
                onClick = onLogClick,
                modifier = Modifier.testTag("logButton"),
            )
        }
    }
}
