package com.sanluca.still.wear.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import com.sanluca.still.wear.R

val OvertimeGold = Color(0xFFFFCA58)

enum class StillColorPalette(val id: String, val labelRes: Int, val swatchColor: Color) {
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
val SpringColorScheme = darkColorScheme(
    primary = Color(0xFF64A348),
    onPrimary = Color(0xFF071F03),
    primaryContainer = Color(0xFF1E3F24),
    onPrimaryContainer = Color(0xFFDFF5DC),
    secondary = Color(0xFFE16B8C),
    onSecondary = Color(0xFF2B0612),
    secondaryContainer = Color(0xFF3E1C29),
    onSecondaryContainer = Color(0xFFFFE5EE),
    tertiary = Color(0xFFF1D88A),
    onTertiary = Color(0xFF2B2103),
    background = Color(0xFF091712),
    onBackground = Color(0xFFFBF8F3),
    surface = Color(0xFF10221B),
    onSurface = Color(0xFFF596AA),
    surfaceVariant = Color(0xFF183128),
    onSurfaceVariant = Color(0xFFB7E4C7),
    outline = Color(0xFF345E4E),
    outlineVariant = Color(0xFF345E4E)
)

// 2. ESTATE (Rosa-Corallo Ibisco, Verde Palma, Turchese Laguna, Giallo Sole)
val SummerColorScheme = darkColorScheme(
    primary = Color(0xFFE85D75),
    onPrimary = Color(0xFF28050C),
    primaryContainer = Color(0xFF143C4C),
    onPrimaryContainer = Color(0xFFCAF0F8),
    secondary = Color(0xFF40916C),
    onSecondary = Color(0xFF041F13),
    secondaryContainer = Color(0xFF174559),
    onSecondaryContainer = Color(0xFFE0FAFF),
    tertiary = Color(0xFF48CAE4),
    onTertiary = Color(0xFF032029),
    background = Color(0xFF072436),
    onBackground = Color(0xFFFFFDF7),
    surface = Color(0xFF0D2B40),
    onSurface = Color(0xFFFFD166),
    surfaceVariant = Color(0xFF163852),
    onSurfaceVariant = Color(0xFF72EFDD),
    outline = Color(0xFF2A5C7A),
    outlineVariant = Color(0xFF2A5C7A)
)

// 3. AUTUNNO (Verde Muschio, Acero/Ambra, Oro Ginkgo, Castagno)
val DarkWarmColorScheme = darkColorScheme(
    primary = Color(0xFF6A994E),
    onPrimary = Color(0xFF091F03),
    primaryContainer = Color(0xFF23381B),
    onPrimaryContainer = Color(0xFFE2F2D8),
    secondary = Color(0xFFE07A5F),
    onSecondary = Color(0xFF260B04),
    secondaryContainer = Color(0xFF4A251B),
    onSecondaryContainer = Color(0xFFFFE8E0),
    tertiary = Color(0xFFF4A261),
    onTertiary = Color(0xFF291302),
    background = Color(0xFF0B1711),
    onBackground = Color(0xFFFFF8EB),
    surface = Color(0xFF152019),
    onSurface = Color(0xFFF4C95D),
    surfaceVariant = Color(0xFF212E25),
    onSurfaceVariant = Color(0xFFC2CCB8),
    outline = Color(0xFF3E5243),
    outlineVariant = Color(0xFF3E5243)
)

// 4. INVERNO (Rosso Camelia/Camino, Verde Abete, Ghiaccio Artico)
val WinterColorScheme = darkColorScheme(
    primary = Color(0xFFE06D53),
    onPrimary = Color(0xFF290902),
    primaryContainer = Color(0xFF1B354D),
    onPrimaryContainer = Color(0xFFE0F2FE),
    secondary = Color(0xFF40916C),
    onSecondary = Color(0xFF041F13),
    secondaryContainer = Color(0xFF244E6B),
    onSecondaryContainer = Color(0xFFE0F2FE),
    tertiary = Color(0xFF90E0EF),
    onTertiary = Color(0xFF032029),
    background = Color(0xFF071624),
    onBackground = Color(0xFFF2EFE6),
    surface = Color(0xFF0D2033),
    onSurface = Color(0xFFA9E5FF),
    surfaceVariant = Color(0xFF14273D),
    onSurfaceVariant = Color(0xFFB7C9B9),
    outline = Color(0xFF2D4B6B),
    outlineVariant = Color(0xFF2D4B6B)
)
