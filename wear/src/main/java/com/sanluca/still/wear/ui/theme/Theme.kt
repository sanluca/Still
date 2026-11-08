@file:Suppress("WearMaterialTheme")

package com.sanluca.still.wear.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

val DarkWarmColorScheme = darkColorScheme(
    primary = WarmPeachPrimary, // #E08E45 (Ambra Autunnale)
    onPrimary = WarmPeachOnPrimary, // #261104 (Corteccia scura)
    primaryContainer = DarkWarmPrimaryContainer, // #63371E
    onPrimaryContainer = WarmIvoryText, // #F2E8DF
    secondary = WarmTitleStill, // #D97F4B (Terracotta Morbida)
    onSecondary = WarmPeachOnPrimary, // #261104
    secondaryContainer = DarkWarmSecondaryContainer, // #472515 (Castagna caldo)
    onSecondaryContainer = WarmIvoryText, // #F2E8DF
    tertiary = WarmTitleStill, // #D97F4B
    onTertiary = WarmPeachOnPrimary, // #261104
    background = DarkWarmBackground, // #0D0806 (Corteccia Notturna)
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
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkWarmColorScheme,
        content = content
    )
}
