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
fun supplyColor(
    kind: SupplyKind,
    colorIndex: Int? = null,
): Color {
    val dark = isSystemInDarkTheme()
    return when (kind) {
        SupplyKind.BAG -> if (dark) OstimateColors.BagDark else OstimateColors.Bag
        SupplyKind.FLANGE -> if (dark) OstimateColors.FlangeDark else OstimateColors.Flange
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
        SupplyKind.BAG -> if (dark) OstimateColors.BagPillBgDark else OstimateColors.BagPillBg
        SupplyKind.FLANGE -> if (dark) OstimateColors.FlangePillBgDark else OstimateColors.FlangePillBg
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
        SupplyKind.BAG -> if (dark) OstimateColors.BagPillTextDark else OstimateColors.BagPillText
        SupplyKind.FLANGE -> if (dark) OstimateColors.FlangePillTextDark else OstimateColors.FlangePillText
        SupplyKind.CUSTOM -> customSupplyColor(colorIndex, dark)
    }
}

private fun customSupplyColor(
    colorIndex: Int?,
    dark: Boolean,
): Color {
    val light =
        arrayOf(
            OstimateColors.Custom0, OstimateColors.Custom1, OstimateColors.Custom2,
            OstimateColors.Custom3, OstimateColors.Custom4, OstimateColors.Custom5,
            OstimateColors.Custom6, OstimateColors.Custom7,
        )
    val darkPalette =
        arrayOf(
            OstimateColors.Custom0Dark, OstimateColors.Custom1Dark, OstimateColors.Custom2Dark,
            OstimateColors.Custom3Dark, OstimateColors.Custom4Dark, OstimateColors.Custom5Dark,
            OstimateColors.Custom6Dark, OstimateColors.Custom7Dark,
        )
    val palette = if (dark) darkPalette else light
    return if (colorIndex != null && colorIndex in palette.indices) {
        palette[colorIndex]
    } else {
        palette[0]
    }
}

@Composable
fun warnBg(): Color = if (isSystemInDarkTheme()) OstimateColors.WarnBgDark else OstimateColors.WarnBg

@Composable
fun warnBorder(): Color = if (isSystemInDarkTheme()) OstimateColors.WarnBorderDark else OstimateColors.WarnBorder

@Composable
fun warnText(): Color = if (isSystemInDarkTheme()) OstimateColors.WarnTextDark else OstimateColors.WarnText
