package com.sanluca.still.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import com.sanluca.still.R

// 1. Sfondi e Superfici Principali (Autunno Caldo Zen: Corteccia Notturna e Castagna)
val DarkWarmBackground = Color(0xFF0D0806)
val DarkWarmSurface = Color(0xFF1E1410)
val DarkWarmSurfaceVariant = Color(0xFF2A1B15)
val DarkWarmOutline = Color(0xFF4A3024)

// 2. Accento Primario (Ambra Autunnale / Foglia d'Acero Dorata)
val WarmPeachPrimary = Color(0xFFE08E45)
val WarmPeachOnPrimary = Color(0xFF261104)

// 3. Tipografia e Testi (Avorio Caldo, Nocciola e Terracotta Morbida)
val WarmTitleStill = Color(0xFFD97F4B)
val WarmIvoryText = Color(0xFFF2E8DF)
val WarmSandSubtitles = Color(0xFFC4AF9E)

// 4. Elementi Specifici, Clessidra Autunnale e Statistiche
val StatsMinutesBadge = Color(0xFF63371E)
val StatsSessionsBadge = Color(0xFF472515)
val StatsStreakBadge = Color(0xFFF5D487)
val StatsStreakText = Color(0xFF2C1906)
val OvertimeGold = Color(0xFFE8A838)

// 5. Colori Dedicati alla Clessidra Autunnale
val AutumnSandLight = Color(0xFFF0A84B)
val AutumnSandDeep = Color(0xFFC45328)
val AutumnWoodFrame = Color(0xFF8C5332)
val AutumnGlassOutline = Color(0xFFB87B52)

enum class StillColorPalette(val id: String, val labelRes: Int, val swatchColor: Color) {
    AUTUMN("autumn", R.string.theme_autumn, WarmPeachPrimary),
    TITANIUM("titanium", R.string.theme_titanium, Color(0xFFA5D8FF)),
    DEEP_SPACE("deep_space", R.string.theme_deep_space, Color(0xFFA5B4FC)),
    NOTHING("nothing", R.string.theme_nothing, Color(0xFFF4F4F5)),
    CHAMPAGNE("champagne", R.string.theme_champagne, Color(0xFFE2B49A)),
    SAGE("sage", R.string.theme_sage, Color(0xFF8FD4B0)),
    CUSTOM("custom", R.string.theme_custom, Color(0xFF7CC4FA)),
    DYNAMIC("dynamic", R.string.theme_dynamic, Color(0xFF9E9E9E));

    companion object {
        fun fromId(id: String?): StillColorPalette =
            entries.firstOrNull { it.id == id } ?: AUTUMN
    }
}

data class CustomThemeConfig(
    val hue: Float = 205f,
    val saturation: Float = 0.55f,
    val bgMode: Int = 1 // 0 = OLED Nero, 1 = Ardesia (Slate), 2 = Grafite
)

data class StillExtendedColors(
    val overtime: Color,
    val sandLight: Color,
    val sandDeep: Color,
    val woodFrame: Color,
    val glassOutline: Color,
)

val AutumnExtendedColors = StillExtendedColors(
    overtime = OvertimeGold,
    sandLight = AutumnSandLight,
    sandDeep = AutumnSandDeep,
    woodFrame = AutumnWoodFrame,
    glassOutline = AutumnGlassOutline
)

// Palette 2: Titanio Ghiaccio (VisionOS / Apple High-Tech)
val TitaniumColorScheme = darkColorScheme(
    primary = Color(0xFFA5D8FF),
    onPrimary = Color(0xFF081C2E),
    primaryContainer = Color(0xFF1B354D),
    onPrimaryContainer = Color(0xFFE3F2FF),
    secondary = Color(0xFFC5E4FF),
    onSecondary = Color(0xFF081C2E),
    secondaryContainer = Color(0xFF142738),
    onSecondaryContainer = Color(0xFFE3F2FF),
    tertiary = Color(0xFF94A3B8),
    onTertiary = Color(0xFF0F172A),
    tertiaryContainer = Color(0xFF233044),
    onTertiaryContainer = Color(0xFFE2E8F0),
    background = Color(0xFF080B10),
    onBackground = Color(0xFFF1F5F9),
    surface = Color(0xFF10151E),
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = Color(0xFF18202C),
    onSurfaceVariant = Color(0xFF9FB0C4),
    outline = Color(0xFF2B3748),
    outlineVariant = Color(0xFF2B3748)
)

val TitaniumExtendedColors = StillExtendedColors(
    overtime = Color(0xFFF5D07B),
    sandLight = Color(0xFFCBE8FF),
    sandDeep = Color(0xFF5B9BD5),
    woodFrame = Color(0xFF38475C),
    glassOutline = Color(0xFF7BA4C7)
)

// Palette 3: Notte Nordica (Oura / Deep Space)
val DeepSpaceColorScheme = darkColorScheme(
    primary = Color(0xFFA5B4FC),
    onPrimary = Color(0xFF141842),
    primaryContainer = Color(0xFF272E66),
    onPrimaryContainer = Color(0xFFE8ECFF),
    secondary = Color(0xFFCBD5E1),
    onSecondary = Color(0xFF131A26),
    secondaryContainer = Color(0xFF1E2642),
    onSecondaryContainer = Color(0xFFE8ECFF),
    tertiary = Color(0xFF818CF8),
    onTertiary = Color(0xFF141842),
    tertiaryContainer = Color(0xFF2B3166),
    onTertiaryContainer = Color(0xFFE0E7FF),
    background = Color(0xFF060911),
    onBackground = Color(0xFFEEF2FF),
    surface = Color(0xFF0D1322),
    onSurface = Color(0xFFEEF2FF),
    surfaceVariant = Color(0xFF151E33),
    onSurfaceVariant = Color(0xFFA3B0CC),
    outline = Color(0xFF273452),
    outlineVariant = Color(0xFF273452)
)

val DeepSpaceExtendedColors = StillExtendedColors(
    overtime = Color(0xFFF5CF78),
    sandLight = Color(0xFFC7D2FE),
    sandDeep = Color(0xFF5C6BC0),
    woodFrame = Color(0xFF374166),
    glassOutline = Color(0xFF818CF8)
)

// Palette 4: Minimal OLED (Nothing / Leica Hi-Tech)
val NothingColorScheme = darkColorScheme(
    primary = Color(0xFFF4F4F5),
    onPrimary = Color(0xFF09090B),
    primaryContainer = Color(0xFF27272A),
    onPrimaryContainer = Color(0xFFFAFAFA),
    secondary = Color(0xFFA1A1AA),
    onSecondary = Color(0xFF09090B),
    secondaryContainer = Color(0xFF1C1C20),
    onSecondaryContainer = Color(0xFFF4F4F5),
    tertiary = Color(0xFFFF5A36),
    onTertiary = Color(0xFF240600),
    tertiaryContainer = Color(0xFF4A180D),
    onTertiaryContainer = Color(0xFFFFE5DF),
    background = Color(0xFF000000),
    onBackground = Color(0xFFFAFAFA),
    surface = Color(0xFF0D0D0F),
    onSurface = Color(0xFFFAFAFA),
    surfaceVariant = Color(0xFF161619),
    onSurfaceVariant = Color(0xFFA1A1AA),
    outline = Color(0xFF2E2E34),
    outlineVariant = Color(0xFF2E2E34)
)

val NothingExtendedColors = StillExtendedColors(
    overtime = Color(0xFFFF5A36),
    sandLight = Color(0xFFF4F4F5),
    sandDeep = Color(0xFF71717A),
    woodFrame = Color(0xFF3F3F46),
    glassOutline = Color(0xFFA1A1AA)
)

// Palette 5: Oro Satinato (Champagne / Luxury Tech)
val ChampagneColorScheme = darkColorScheme(
    primary = Color(0xFFE2B49A),
    onPrimary = Color(0xFF24140B),
    primaryContainer = Color(0xFF422B20),
    onPrimaryContainer = Color(0xFFFAEDE6),
    secondary = Color(0xFFF0D5C5),
    onSecondary = Color(0xFF24140B),
    secondaryContainer = Color(0xFF30221C),
    onSecondaryContainer = Color(0xFFFAEDE6),
    tertiary = Color(0xFFC9A0DC),
    onTertiary = Color(0xFF23112E),
    tertiaryContainer = Color(0xFF3A2447),
    onTertiaryContainer = Color(0xFFF4E8FA),
    background = Color(0xFF09080A),
    onBackground = Color(0xFFF7F2EE),
    surface = Color(0xFF141216),
    onSurface = Color(0xFFF7F2EE),
    surfaceVariant = Color(0xFF1F1B22),
    onSurfaceVariant = Color(0xFFBDB0B8),
    outline = Color(0xFF3B3340),
    outlineVariant = Color(0xFF3B3340)
)

val ChampagneExtendedColors = StillExtendedColors(
    overtime = Color(0xFFF2C97D),
    sandLight = Color(0xFFF2CFC0),
    sandDeep = Color(0xFFA86F54),
    woodFrame = Color(0xFF574139),
    glassOutline = Color(0xFFC79985)
)

// Palette 6: Salvia Titanio (Bio-Tech Calmo)
val SageColorScheme = darkColorScheme(
    primary = Color(0xFF8FD4B0),
    onPrimary = Color(0xFF072114),
    primaryContainer = Color(0xFF1B3B2B),
    onPrimaryContainer = Color(0xFFE2F7EC),
    secondary = Color(0xFFB6E3CC),
    onSecondary = Color(0xFF072114),
    secondaryContainer = Color(0xFF142E21),
    onSecondaryContainer = Color(0xFFE2F7EC),
    tertiary = Color(0xFF9CB8A9),
    onTertiary = Color(0xFF0B1C13),
    tertiaryContainer = Color(0xFF23362C),
    onTertiaryContainer = Color(0xFFE0EDE6),
    background = Color(0xFF070B09),
    onBackground = Color(0xFFEBF5EF),
    surface = Color(0xFF0F1612),
    onSurface = Color(0xFFEBF5EF),
    surfaceVariant = Color(0xFF17221C),
    onSurfaceVariant = Color(0xFF9BB5A7),
    outline = Color(0xFF2A3D33),
    outlineVariant = Color(0xFF2A3D33)
)

val SageExtendedColors = StillExtendedColors(
    overtime = Color(0xFFF0CB75),
    sandLight = Color(0xFFB6E3CC),
    sandDeep = Color(0xFF4B916F),
    woodFrame = Color(0xFF345243),
    glassOutline = Color(0xFF74AD8F)
)

fun buildCustomColorScheme(config: CustomThemeConfig): androidx.compose.material3.ColorScheme {
    val hue = config.hue.coerceIn(0f, 360f)
    val sat = config.saturation.coerceIn(0.15f, 0.90f)
    val secHue = (hue + 18f) % 360f
    val terHue = (hue + 36f) % 360f

    val bg = when (config.bgMode) {
        0 -> Color(0xFF000000)
        2 -> Color(0xFF0B0B0D)
        else -> Color.hsl(hue, (sat * 0.30f).coerceAtMost(0.28f), 0.045f)
    }
    val surf = when (config.bgMode) {
        0 -> Color(0xFF0B0B0D)
        2 -> Color(0xFF141418)
        else -> Color.hsl(hue, (sat * 0.28f).coerceAtMost(0.25f), 0.085f)
    }
    val surfVar = when (config.bgMode) {
        0 -> Color(0xFF151519)
        2 -> Color(0xFF1E1E24)
        else -> Color.hsl(hue, (sat * 0.25f).coerceAtMost(0.22f), 0.135f)
    }
    val outl = when (config.bgMode) {
        0 -> Color.hsl(hue, (sat * 0.25f), 0.24f)
        2 -> Color(0xFF34343D)
        else -> Color.hsl(hue, (sat * 0.35f), 0.26f)
    }

    return darkColorScheme(
        primary = Color.hsl(hue, sat, 0.76f),
        onPrimary = Color.hsl(hue, (sat * 0.6f), 0.12f),
        primaryContainer = Color.hsl(hue, (sat * 0.55f), 0.22f),
        onPrimaryContainer = Color.hsl(hue, (sat * 0.4f), 0.93f),
        secondary = Color.hsl(secHue, (sat * 0.8f), 0.80f),
        onSecondary = Color.hsl(hue, (sat * 0.6f), 0.12f),
        secondaryContainer = Color.hsl(hue, (sat * 0.45f), 0.16f),
        onSecondaryContainer = Color.hsl(hue, (sat * 0.35f), 0.90f),
        tertiary = Color.hsl(terHue, (sat * 0.7f), 0.72f),
        onTertiary = Color.hsl(hue, (sat * 0.6f), 0.12f),
        tertiaryContainer = Color.hsl(terHue, (sat * 0.45f), 0.24f),
        onTertiaryContainer = Color.hsl(terHue, (sat * 0.4f), 0.92f),
        background = bg,
        onBackground = Color(0xFFF2F4F8),
        surface = surf,
        onSurface = Color(0xFFF2F4F8),
        surfaceVariant = surfVar,
        onSurfaceVariant = Color.hsl(hue, (sat * 0.20f), 0.70f),
        outline = outl,
        outlineVariant = outl
    )
}

fun buildCustomExtendedColors(config: CustomThemeConfig): StillExtendedColors {
    val hue = config.hue.coerceIn(0f, 360f)
    val sat = config.saturation.coerceIn(0.15f, 0.90f)
    return StillExtendedColors(
        overtime = OvertimeGold,
        sandLight = Color.hsl(hue, (sat * 0.85f), 0.82f),
        sandDeep = Color.hsl(hue, sat, 0.52f),
        woodFrame = Color.hsl(hue, (sat * 0.35f), 0.30f),
        glassOutline = Color.hsl(hue, (sat * 0.65f), 0.64f)
    )
}
