@file:Suppress("WearMaterialTheme")

package com.sanluca.still.wear.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun StillTheme(
    palette: StillColorPalette = StillColorPalette.AUTUMN,
    content: @Composable () -> Unit
) {
    val colorScheme = when (palette) {
        StillColorPalette.SPRING -> SpringColorScheme
        StillColorPalette.SUMMER -> SummerColorScheme
        StillColorPalette.AUTUMN -> DarkWarmColorScheme
        StillColorPalette.WINTER -> WinterColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
