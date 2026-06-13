package com.ostimate.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.ostimate.app.domain.SupplyKind

@Composable
fun OstimateTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colorScheme =
        if (dark) {
            darkColorScheme(
                primary = OstimateColors.PrimaryDark,
                background = OstimateColors.BackgroundDark,
                surface = OstimateColors.SurfaceDark,
                onBackground = OstimateColors.TextPrimaryDark,
                onSurface = OstimateColors.TextPrimaryDark,
                outline = OstimateColors.DividerDark,
            )
        } else {
            lightColorScheme(
                primary = OstimateColors.Primary,
                background = OstimateColors.Background,
                surface = OstimateColors.Surface,
                onBackground = OstimateColors.TextPrimary,
                onSurface = OstimateColors.TextPrimary,
                outline = OstimateColors.Divider,
            )
        }
    MaterialTheme(colorScheme = colorScheme, typography = OstimateTypography, content = content)
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

@Composable
fun supplyPillBg(kind: SupplyKind): Color {
    val dark = isSystemInDarkTheme()
    return when (kind) {
        SupplyKind.BAG -> if (dark) OstimateColors.BagPillBgDark else OstimateColors.BagPillBg
        SupplyKind.FLANGE -> if (dark) OstimateColors.FlangePillBgDark else OstimateColors.FlangePillBg
        SupplyKind.CUSTOM -> MaterialTheme.colorScheme.primaryContainer
    }
}

@Composable
fun supplyPillText(kind: SupplyKind): Color {
    val dark = isSystemInDarkTheme()
    return when (kind) {
        SupplyKind.BAG -> if (dark) OstimateColors.BagPillTextDark else OstimateColors.BagPillText
        SupplyKind.FLANGE -> if (dark) OstimateColors.FlangePillTextDark else OstimateColors.FlangePillText
        SupplyKind.CUSTOM -> MaterialTheme.colorScheme.onPrimaryContainer
    }
}

@Composable
fun warnBg(): Color = if (isSystemInDarkTheme()) OstimateColors.WarnBgDark else OstimateColors.WarnBg

@Composable
fun warnBorder(): Color = if (isSystemInDarkTheme()) OstimateColors.WarnBorderDark else OstimateColors.WarnBorder

@Composable
fun warnText(): Color = if (isSystemInDarkTheme()) OstimateColors.WarnTextDark else OstimateColors.WarnText
