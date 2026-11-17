package com.sanluca.still.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

val LocalStillColors = staticCompositionLocalOf { AutumnExtendedColors }

@Composable
fun StillTheme(
    palette: StillColorPalette = StillColorPalette.AUTUMN,
    content: @Composable () -> Unit,
) {
    val colorScheme = when (palette) {
        StillColorPalette.SPRING -> SpringColorScheme
        StillColorPalette.SUMMER -> SummerColorScheme
        StillColorPalette.AUTUMN -> DarkWarmColorScheme
        StillColorPalette.WINTER -> WinterColorScheme
    }

    val extendedColors = when (palette) {
        StillColorPalette.SPRING -> SpringExtendedColors
        StillColorPalette.SUMMER -> SummerExtendedColors
        StillColorPalette.AUTUMN -> AutumnExtendedColors
        StillColorPalette.WINTER -> WinterExtendedColors
    }

    CompositionLocalProvider(LocalStillColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
