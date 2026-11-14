package com.sanluca.still.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

val DarkWarmColorScheme = darkColorScheme(
    primary = WarmPeachPrimary, // #E08E45
    onPrimary = WarmPeachOnPrimary, // #261104
    primaryContainer = StatsMinutesBadge, // #63371E
    onPrimaryContainer = WarmIvoryText, // #F2E8DF
    secondary = WarmTitleStill, // #D97F4B
    onSecondary = WarmPeachOnPrimary, // #261104
    secondaryContainer = StatsSessionsBadge, // #472515
    onSecondaryContainer = WarmIvoryText, // #F2E8DF
    tertiary = WarmTitleStill, // #D97F4B
    onTertiary = WarmPeachOnPrimary, // #261104
    tertiaryContainer = StatsStreakBadge, // #F5D487
    onTertiaryContainer = StatsStreakText, // #2C1906
    background = DarkWarmBackground, // #0D0806
    onBackground = WarmIvoryText, // #F2E8DF
    surface = DarkWarmSurface, // #1E1410
    onSurface = WarmIvoryText, // #F2E8DF
    surfaceVariant = DarkWarmSurfaceVariant, // #2A1B15
    onSurfaceVariant = WarmSandSubtitles, // #C4AF9E
    outline = DarkWarmOutline, // #4A3024
    outlineVariant = DarkWarmOutline, // #4A3024
)

val LocalStillColors = staticCompositionLocalOf { AutumnExtendedColors }

@Composable
fun StillTheme(
    palette: StillColorPalette = StillColorPalette.AUTUMN,
    customTheme: CustomThemeConfig = CustomThemeConfig(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when (palette) {
        StillColorPalette.AUTUMN -> DarkWarmColorScheme
        StillColorPalette.TITANIUM -> TitaniumColorScheme
        StillColorPalette.DEEP_SPACE -> DeepSpaceColorScheme
        StillColorPalette.NOTHING -> NothingColorScheme
        StillColorPalette.CHAMPAGNE -> ChampagneColorScheme
        StillColorPalette.SAGE -> SageColorScheme
        StillColorPalette.CUSTOM -> buildCustomColorScheme(customTheme)
        StillColorPalette.DYNAMIC -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                dynamicDarkColorScheme(context)
            } else {
                DarkWarmColorScheme
            }
        }
    }

    val extendedColors = when (palette) {
        StillColorPalette.AUTUMN -> AutumnExtendedColors
        StillColorPalette.TITANIUM -> TitaniumExtendedColors
        StillColorPalette.DEEP_SPACE -> DeepSpaceExtendedColors
        StillColorPalette.NOTHING -> NothingExtendedColors
        StillColorPalette.CHAMPAGNE -> ChampagneExtendedColors
        StillColorPalette.SAGE -> SageExtendedColors
        StillColorPalette.CUSTOM -> buildCustomExtendedColors(customTheme)
        StillColorPalette.DYNAMIC -> StillExtendedColors(
            overtime = OvertimeGold,
            sandLight = colorScheme.primary,
            sandDeep = colorScheme.tertiary,
            woodFrame = colorScheme.outlineVariant,
            glassOutline = colorScheme.onSurfaceVariant
        )
    }

    CompositionLocalProvider(LocalStillColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
