@file:Suppress("WearMaterialTheme")

package com.sanluca.still.wear.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

val DarkWarmColorScheme = darkColorScheme(
    primary = WarmPeachPrimary, // #E5A882
    onPrimary = WarmPeachOnPrimary, // #24140A
    secondary = WarmTitleStill, // #D1A184
    onSecondary = WarmPeachOnPrimary, // #24140A
    tertiary = WarmTitleStill, // #D1A184
    onTertiary = WarmPeachOnPrimary, // #24140A
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
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkWarmColorScheme,
        content = content
    )
}
