package com.sanluca.still.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

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

@Composable
fun StillTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkWarmColorScheme,
        typography = Typography,
        content = content
    )
}
