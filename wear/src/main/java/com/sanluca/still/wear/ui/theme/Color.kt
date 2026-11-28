package com.sanluca.still.wear.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import com.sanluca.still.wear.R

internal val OvertimeGold = Color(0xFFFFCA58)

internal enum class StillColorPalette(val id: String, val labelRes: Int, val swatchColor: Color) {
    SPRING("spring", R.string.theme_spring, Color(0xFFF596AA)),
    SUMMER("summer", R.string.theme_summer, Color(0xFFE85D75)),
    AUTUMN("autumn", R.string.theme_autumn, Color(0xFF6A994E)),
    WINTER("winter", R.string.theme_winter, Color(0xFFE06D53));

    companion object {
        fun fromId(id: String?): StillColorPalette = when (id) {
            "spring" -> SPRING
            "summer" -> SUMMER
            "winter" -> WINTER
            else -> AUTUMN
        }
    }
}

// 1. PRIMAVERA (Verde Germoglio, Rosa Ciliegio, Giallo Polline)
internal val SpringColorScheme = darkColorScheme(
    primary = Color(0xFF64A348),            // Verde Germoglio Moegi Vivo
    onPrimary = Color(0xFF071F03),
    primaryContainer = Color(0xFF1E3F24),
    onPrimaryContainer = Color(0xFFDFF5DC),
    secondary = Color(0xFFE16B8C),          // Rosa Fiore di Prugno Kōbai
    onSecondary = Color(0xFF2B0612),
    secondaryContainer = Color(0xFF3E1C29),
    onSecondaryContainer = Color(0xFFFFE5EE),
    tertiary = Color(0xFFF1D88A),           // Giallo Polline Yamabuki
    onTertiary = Color(0xFF2B2103),
    background = Color.Black,               // Nero OLED assoluto
    onBackground = Color(0xFFFBF8F3),
    surface = Color.Black,
    onSurface = Color(0xFFFBF8F3),
    surfaceVariant = Color(0xFF183128),
    onSurfaceVariant = Color(0xFFB7E4C7),
    outline = Color(0xFF345E4E),
    outlineVariant = Color(0xFF345E4E)
)

// 2. ESTATE (Rosa-Corallo Ibisco, Verde Palma, Turchese Laguna, Giallo Sole)
internal val SummerColorScheme = darkColorScheme(
    primary = Color(0xFFE85D75),            // Rosa-Corallo Ibisco
    onPrimary = Color(0xFF28050C),
    primaryContainer = Color(0xFF143C4C),
    onPrimaryContainer = Color(0xFFCAF0F8),
    secondary = Color(0xFF40916C),          // Verde Palma / Bambù
    onSecondary = Color(0xFF041F13),
    secondaryContainer = Color(0xFF133829),
    onSecondaryContainer = Color(0xFFD8F3DC),
    tertiary = Color(0xFF48CAE4),           // Turchese Laguna
    onTertiary = Color(0xFF032029),
    background = Color.Black,               // Nero OLED assoluto
    onBackground = Color(0xFFFFFBF2),
    surface = Color.Black,
    onSurface = Color(0xFFFFFBF2),
    surfaceVariant = Color(0xFF163852),
    onSurfaceVariant = Color(0xFF72EFDD),
    outline = Color(0xFF2A5C7A),
    outlineVariant = Color(0xFF2A5C7A)
)

// 3. AUTUNNO (Verde Muschio del Bosco, Viola Prugna, Ambra Calda)
internal val DarkWarmColorScheme = darkColorScheme(
    primary = Color(0xFF6A994E),            // Verde Muschio del Bosco
    onPrimary = Color(0xFF091F03),
    primaryContainer = Color(0xFF23381B),
    onPrimaryContainer = Color(0xFFE2F2D8),
    secondary = Color(0xFFB56576),          // Viola Prugna / Mosto
    onSecondary = Color(0xFF260910),
    secondaryContainer = Color(0xFF3D1D24),
    onSecondaryContainer = Color(0xFFFFE3E8),
    tertiary = Color(0xFFF4A261),           // Ambra Calda
    onTertiary = Color(0xFF291302),
    background = Color.Black,               // Nero OLED assoluto
    onBackground = Color(0xFFFDF6EC),
    surface = Color.Black,
    onSurface = Color(0xFFFDF6EC),
    surfaceVariant = Color(0xFF212E25),
    onSurfaceVariant = Color(0xFFC2CCB8),
    outline = Color(0xFF3E5243),
    outlineVariant = Color(0xFF3E5243)
)

// 4. INVERNO (Rosso Camelia/Camino, Verde Abete, Ciano Ghiaccio)
internal val WinterColorScheme = darkColorScheme(
    primary = Color(0xFFE06D53),            // Rosso Camelia / Calore Camino
    onPrimary = Color(0xFF290902),
    primaryContainer = Color(0xFF1B354D),
    onPrimaryContainer = Color(0xFFE0F2FE),
    secondary = Color(0xFF40916C),          // Verde Abete Sempreverde
    onSecondary = Color(0xFF041F13),
    secondaryContainer = Color(0xFF163829),
    onSecondaryContainer = Color(0xFFD8F3DC),
    tertiary = Color(0xFF90E0EF),           // Ciano Ghiaccio
    onTertiary = Color(0xFF032029),
    background = Color.Black,               // Nero OLED assoluto
    onBackground = Color(0xFFF8FBFF),
    surface = Color.Black,
    onSurface = Color(0xFFF8FBFF),
    surfaceVariant = Color(0xFF162D45),
    onSurfaceVariant = Color(0xFFB7C9B9),
    outline = Color(0xFF2D4B6B),
    outlineVariant = Color(0xFF2D4B6B)
)
