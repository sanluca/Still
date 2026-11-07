@file:Suppress("WearMaterialTheme")

package com.sanluca.still.wear.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

val DarkWarmColorScheme = darkColorScheme(
    primary = WarmPeachPrimary, // #86C7A3 (Verde Salvia)
    onPrimary = WarmPeachOnPrimary, // #082416 (Verde Foresta scuro)
    primaryContainer = DarkWarmPrimaryContainer, // #2B4A3C
    onPrimaryContainer = WarmIvoryText, // #E2ECE6
    secondary = WarmTitleStill, // #6BAF8B (Verde Eucalipto)
    onSecondary = WarmPeachOnPrimary, // #082416
    secondaryContainer = DarkWarmSecondaryContainer, // #21382E (Verde Pino morbido)
    onSecondaryContainer = WarmIvoryText, // #E2ECE6
    tertiary = WarmTitleStill, // #6BAF8B
    onTertiary = WarmPeachOnPrimary, // #082416
    background = DarkWarmBackground, // #060D0A (Nero Bosco profondo)
    onBackground = WarmIvoryText, // #E2ECE6
    surface = DarkWarmSurface, // #111E18
    onSurface = WarmIvoryText, // #E2ECE6
    surfaceVariant = DarkWarmSurfaceVariant, // #16261F (Verde Muschio scuro)
    onSurfaceVariant = WarmSandSubtitles, // #A3B8AD
    outline = DarkWarmOutline, // #2F4D3F
    outlineVariant = DarkWarmOutline, // #2F4D3F
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
