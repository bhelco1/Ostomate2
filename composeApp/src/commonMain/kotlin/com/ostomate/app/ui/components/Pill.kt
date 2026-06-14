package com.ostomate.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ostomate.app.domain.SupplyKind
import com.ostomate.app.ui.theme.supplyPillBg
import com.ostomate.app.ui.theme.supplyPillText

// Pills always carry a text label — color is never the only signal (a11y rule from 03-ui-ux-design.md).
@Composable
fun Pill(
    label: String,
    kind: SupplyKind,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = supplyPillText(kind),
        modifier =
            modifier
                .then(
                    if (contentDescription != null) {
                        Modifier.semantics { this.contentDescription = contentDescription }
                    } else {
                        Modifier
                    },
                )
                .clip(RoundedCornerShape(50))
                .background(supplyPillBg(kind))
                .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}
