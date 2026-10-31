package com.sanluca.still.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

val DarkWarmColorScheme = darkColorScheme(
    primary = WarmPeachPrimary, // #E5A882
    onPrimary = WarmPeachOnPrimary, // #24140A
    primaryContainer = StatsMinutesBadge, // #5A3B24
    onPrimaryContainer = WarmIvoryText, // #ECE4DF
    secondary = WarmTitleStill, // #D1A184
    onSecondary = WarmPeachOnPrimary, // #24140A
    secondaryContainer = StatsSessionsBadge, // #3E291C
    onSecondaryContainer = WarmIvoryText, // #ECE4DF
    tertiary = WarmTitleStill, // #D1A184
    onTertiary = WarmPeachOnPrimary, // #24140A
    tertiaryContainer = StatsStreakBadge, // #FFF3B0
    onTertiaryContainer = StatsStreakText, // #2C1E0A
    background = DarkWarmBackground, // #0C0806
    onBackground = WarmIvoryText, // #ECE4DF
    surface = DarkWarmSurface, // #221C17
    onSurface = WarmIvoryText, // #ECE4DF
    surfaceVariant = DarkWarmSurfaceVariant, // #2A221C
    onSurfaceVariant = WarmSandSubtitles, // #B8ADA5
    outline = DarkWarmOutline, // #3E3129
    outlineVariant = DarkWarmOutline, // #3E3129
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
