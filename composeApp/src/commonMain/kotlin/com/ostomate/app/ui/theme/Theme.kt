package com.ostomate.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.ostomate.app.domain.SupplyKind

@Composable
fun OstomateTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colorScheme =
        if (dark) {
            darkColorScheme(
                primary = OstomateColors.PrimaryDark,
                background = OstomateColors.BackgroundDark,
                surface = OstomateColors.SurfaceDark,
                onBackground = OstomateColors.TextPrimaryDark,
                onSurface = OstomateColors.TextPrimaryDark,
                outline = OstomateColors.DividerDark,
            )
        } else {
            lightColorScheme(
                primary = OstomateColors.Primary,
                background = OstomateColors.Background,
                surface = OstomateColors.Surface,
                onBackground = OstomateColors.TextPrimary,
                onSurface = OstomateColors.TextPrimary,
                outline = OstomateColors.Divider,
            )
        }
    MaterialTheme(colorScheme = colorScheme, typography = OstomateTypography, content = content)
}

@Composable
fun supplyColor(
    kind: SupplyKind,
    colorIndex: Int? = null,
): Color {
    val dark = isSystemInDarkTheme()
    return when (kind) {
        SupplyKind.BAG -> if (dark) OstomateColors.BagDark else OstomateColors.Bag
        SupplyKind.FLANGE -> if (dark) OstomateColors.FlangeDark else OstomateColors.Flange
        SupplyKind.CUSTOM -> customSupplyColor(colorIndex, dark)
    }
}

@Composable
fun supplyPillBg(
    kind: SupplyKind,
    colorIndex: Int? = null,
): Color {
    val dark = isSystemInDarkTheme()
    return when (kind) {
        SupplyKind.BAG -> if (dark) OstomateColors.BagPillBgDark else OstomateColors.BagPillBg
        SupplyKind.FLANGE -> if (dark) OstomateColors.FlangePillBgDark else OstomateColors.FlangePillBg
        SupplyKind.CUSTOM -> customSupplyColor(colorIndex, dark).copy(alpha = 0.15f)
    }
}

@Composable
fun supplyPillText(
    kind: SupplyKind,
    colorIndex: Int? = null,
): Color {
    val dark = isSystemInDarkTheme()
    return when (kind) {
        SupplyKind.BAG -> if (dark) OstomateColors.BagPillTextDark else OstomateColors.BagPillText
        SupplyKind.FLANGE -> if (dark) OstomateColors.FlangePillTextDark else OstomateColors.FlangePillText
        SupplyKind.CUSTOM -> customSupplyColor(colorIndex, dark)
    }
}

private fun customSupplyColor(
    colorIndex: Int?,
    dark: Boolean,
): Color {
    val light =
        arrayOf(
            OstomateColors.Custom0,
            OstomateColors.Custom1,
            OstomateColors.Custom2,
            OstomateColors.Custom3,
            OstomateColors.Custom4,
            OstomateColors.Custom5,
            OstomateColors.Custom6,
            OstomateColors.Custom7,
        )
    val darkPalette =
        arrayOf(
            OstomateColors.Custom0Dark,
            OstomateColors.Custom1Dark,
            OstomateColors.Custom2Dark,
            OstomateColors.Custom3Dark,
            OstomateColors.Custom4Dark,
            OstomateColors.Custom5Dark,
            OstomateColors.Custom6Dark,
            OstomateColors.Custom7Dark,
        )
    val palette = if (dark) darkPalette else light
    return if (colorIndex != null && colorIndex in palette.indices) {
        palette[colorIndex]
    } else {
        palette[0]
    }
}

@Composable
fun warnBg(): Color = if (isSystemInDarkTheme()) OstomateColors.WarnBgDark else OstomateColors.WarnBg

@Composable
fun warnBorder(): Color = if (isSystemInDarkTheme()) OstomateColors.WarnBorderDark else OstomateColors.WarnBorder

@Composable
fun warnText(): Color = if (isSystemInDarkTheme()) OstomateColors.WarnTextDark else OstomateColors.WarnText
