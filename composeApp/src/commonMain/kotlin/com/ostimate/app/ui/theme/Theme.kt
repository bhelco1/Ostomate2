package com.ostimate.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.ostimate.app.domain.SupplyKind

// Tokens from ostimate-2.0/03-ui-ux-design.md
object OstimateColors {
    val Primary = Color(0xFF2563EB)
    val PrimaryDark = Color(0xFF60A5FA)
    val Bag = Color(0xFF16A34A)
    val BagDark = Color(0xFF22C55E)
    val Flange = Color(0xFFE65100)
    val FlangeDark = Color(0xFFFB923C)
    val BackgroundLight = Color(0xFFF1F5F9)
    val BackgroundDark = Color(0xFF0F172A)
    val SurfaceLight = Color(0xFFFFFFFF)
    val SurfaceDark = Color(0xFF1E293B)
    val TextPrimaryLight = Color(0xFF1E293B)
    val TextPrimaryDark = Color(0xFFF1F5F9)
}

@Composable
fun OstimateTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colorScheme = if (dark) {
        darkColorScheme(
            primary = OstimateColors.PrimaryDark,
            background = OstimateColors.BackgroundDark,
            surface = OstimateColors.SurfaceDark,
            onBackground = OstimateColors.TextPrimaryDark,
            onSurface = OstimateColors.TextPrimaryDark,
        )
    } else {
        lightColorScheme(
            primary = OstimateColors.Primary,
            background = OstimateColors.BackgroundLight,
            surface = OstimateColors.SurfaceLight,
            onBackground = OstimateColors.TextPrimaryLight,
            onSurface = OstimateColors.TextPrimaryLight,
        )
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}

@Composable
fun supplyColor(kind: SupplyKind): Color {
    val dark = isSystemInDarkTheme()
    return when (kind) {
        SupplyKind.BAG -> if (dark) OstimateColors.BagDark else OstimateColors.Bag
        SupplyKind.FLANGE -> if (dark) OstimateColors.FlangeDark else OstimateColors.Flange
        SupplyKind.CUSTOM -> MaterialTheme.colorScheme.primary
    }
}
