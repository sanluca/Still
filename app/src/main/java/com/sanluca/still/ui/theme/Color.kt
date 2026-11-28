package com.sanluca.still.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import com.sanluca.still.R

internal enum class StillColorPalette(val id: String, val labelRes: Int, val swatchColor: Color) {
    SPRING("spring", R.string.theme_spring, Color(0xFFF596AA)),
    SUMMER("summer", R.string.theme_summer, Color(0xFFE85D75)),
    AUTUMN("autumn", R.string.theme_autumn, Color(0xFF6A994E)),
    WINTER("winter", R.string.theme_winter, Color(0xFFE06D53));

    companion object {
        fun fromId(id: String?): StillColorPalette = when (id) {
            "spring", "champagne", "sage" -> SPRING
            "summer", "titanium" -> SUMMER
            "winter", "deep_space", "nothing" -> WINTER
            else -> AUTUMN
        }
    }
}

/**
 * Palette policromatica biofilica e Kasane no Irome per le 4 Stagioni:
 * Separa nettamente i colori di Scritte/Titolo, Numeri Timer, Pulsanti Step, Vetro, Legno e Sabbia.
 */
internal data class StillExtendedColors(
    val titleColor: Color,
    val timerDigitsColor: Color,
    val overtime: Color,
    val sandLight: Color,
    val sandDeep: Color,
    val woodFrame: Color,
    val glassOutline: Color,
    val skyTop: Color,
    val skyBottom: Color,
    val auroraPrimary: Color,
    val auroraSecondary: Color,
)

// ============================================================================
// 1. PRIMAVERA — Kasane "Sakura", "Yamabuki" & "Yanagi"
// (Rosa Petalo di Ciliegio, Verde Germoglio Vivo, Giallo Polline, Rugiada & Bambù)
// ============================================================================
internal val SpringColorScheme = darkColorScheme(
    primary = Color(0xFF64A348),            // Pulsante Inizia: Verde Germoglio Moegi Vivo
    onPrimary = Color(0xFF071F03),
    primaryContainer = Color(0xFF1E3F24),
    onPrimaryContainer = Color(0xFFDFF5DC),
    secondary = Color(0xFFE16B8C),          // Pulsante Pausa: Rosa Fiore di Prugno Kōbai
    onSecondary = Color(0xFF2B0612),
    secondaryContainer = Color(0xFF3E1C29),
    onSecondaryContainer = Color(0xFFFFE5EE),
    tertiary = Color(0xFFF1D88A),           // Pulsanti -5m..+5m & Preparazione: Giallo Polline Yamabuki
    onTertiary = Color(0xFF2B2103),
    tertiaryContainer = Color(0xFF3E3312),
    onTertiaryContainer = Color(0xFFFFF5D6),
    background = Color(0xFF091712),         // Sfondo: Giardino Umido al Mattino
    onBackground = Color(0xFFFBF8F3),
    surface = Color(0xFF10221B),
    onSurface = Color(0xFFFBF8F3),
    surfaceVariant = Color(0xFF183128),
    onSurfaceVariant = Color(0xFFB7E4C7),   // Sottotitolo: Verde Salice Giovane
    outline = Color(0xFF345E4E),
    outlineVariant = Color(0xFF345E4E)
)

internal val SpringExtendedColors = StillExtendedColors(
    titleColor = Color(0xFFF596AA),         // Titolo "Still" e icone: Rosa Petalo Sakura
    timerDigitsColor = Color(0xFFFFF8FA),   // Numeri Timer: Bianco Petalo di Magnolia
    overtime = Color(0xFFFFD166),           // Extra: Oro Polline
    sandLight = Color(0xFFF1D88A),          // Sabbia Chiara: Giallo Polline Dorato
    sandDeep = Color(0xFFE9965B),           // Sabbia Profonda: Nettare di Pesca Ambrato
    woodFrame = Color(0xFFC78550),          // Struttura Clessidra: Legno di Bambù Miele
    glassOutline = Color(0xFF80E5FF),       // Vetro Clessidra: Cristallo Rugiada Mattutina
    skyTop = Color(0xFF162621),
    skyBottom = Color(0xFF091712),
    auroraPrimary = Color(0xFFF596AA),      // Luce soffusa Sakura
    auroraSecondary = Color(0xFF90C67C)     // Luce soffusa Germoglio Fresco
)

// ============================================================================
// 2. ESTATE — Kasane "Nadeshiko", "Shōbu" & Costa Mediterranea
// (Rosa Ibisco/Corallo, Giallo Sole, Turchese Laguna, Verde Palma & Sabbia d'Oro)
// ============================================================================
internal val SummerColorScheme = darkColorScheme(
    primary = Color(0xFFE85D75),            // Pulsante Inizia: Rosa-Corallo Ibisco Estivo
    onPrimary = Color(0xFF28050C),
    primaryContainer = Color(0xFF143C4C),
    onPrimaryContainer = Color(0xFFCAF0F8),
    secondary = Color(0xFF40916C),          // Pulsante Pausa: Verde Palma / Bambù Estivo
    onSecondary = Color(0xFF041F13),
    secondaryContainer = Color(0xFF133829),
    onSecondaryContainer = Color(0xFFD8F3DC),
    tertiary = Color(0xFF48CAE4),           // Pulsanti -5m..+5m & Preparazione: Turchese Laguna
    onTertiary = Color(0xFF032029),
    tertiaryContainer = Color(0xFF103745),
    onTertiaryContainer = Color(0xFFE0FAFF),
    background = Color(0xFF072436),         // Sfondo: Blu Laguna Profondo
    onBackground = Color(0xFFFFFBF2),
    surface = Color(0xFF0D2B40),
    onSurface = Color(0xFFFFFBF2),
    surfaceVariant = Color(0xFF163852),
    onSurfaceVariant = Color(0xFF72EFDD),   // Sottotitolo: Acquamarina / Schiuma di Mare
    outline = Color(0xFF2A5C7A),
    outlineVariant = Color(0xFF2A5C7A)
)

internal val SummerExtendedColors = StillExtendedColors(
    titleColor = Color(0xFFFFD166),         // Titolo "Still" e icone: Giallo Sole d'Estate
    timerDigitsColor = Color(0xFFFFFDF7),   // Numeri Timer: Bianco Madreperla Caldo
    overtime = Color(0xFFFFD166),           // Extra: Oro Solare
    sandLight = Color(0xFFFFE66D),          // Sabbia Chiara: Sabbia Dorata Solare
    sandDeep = Color(0xFFEB6B56),           // Sabbia Profonda: Corallo Rosa Mediterraneo
    woodFrame = Color(0xFFB68048),          // Struttura Clessidra: Legno Teak Bagnato dal Sole
    glassOutline = Color(0xFF48CAE4),       // Vetro Clessidra: Cristallo Turchese Caraibico
    skyTop = Color(0xFF221933),             // Orizzonte Tramonto Violaceo-Caldo
    skyBottom = Color(0xFF072436),          // Mare Profondo
    auroraPrimary = Color(0xFF00B4D8),      // Luce soffusa Laguna
    auroraSecondary = Color(0xFFFFBE0B)     // Luce soffusa Sole Estivo
)

// ============================================================================
// 3. AUTUNNO — Kasane "Momiji", "Ominaeshi" & "Shion"
// (Acero Cremisi, Oro Ginkgo, Verde Muschio del Bosco, Prugna & Castagno)
// ============================================================================
internal val DarkWarmColorScheme = darkColorScheme(
    primary = Color(0xFF6A994E),            // Pulsante Inizia: Verde Muschio del Bosco (stacca dall'acero!)
    onPrimary = Color(0xFF091F03),
    primaryContainer = Color(0xFF23381B),
    onPrimaryContainer = Color(0xFFE2F2D8),
    secondary = Color(0xFFB56576),          // Pulsante Pausa: Viola Prugna / Mosto d'Autunno
    onSecondary = Color(0xFF260910),
    secondaryContainer = Color(0xFF3D1D24),
    onSecondaryContainer = Color(0xFFFFE3E8),
    tertiary = Color(0xFFF4A261),           // Pulsanti -5m..+5m & Preparazione: Ambra Calda
    onTertiary = Color(0xFF291302),
    tertiaryContainer = Color(0xFF422810),
    onTertiaryContainer = Color(0xFFFFEBD6),
    background = Color(0xFF0B1711),         // Sfondo: Verde-Notte Sottobosco di Pino
    onBackground = Color(0xFFFDF6EC),
    surface = Color(0xFF152019),
    onSurface = Color(0xFFFDF6EC),
    surfaceVariant = Color(0xFF212E25),
    onSurfaceVariant = Color(0xFFC2CCB8),   // Sottotitolo: Salvia / Lichene del Bosco
    outline = Color(0xFF3E5243),
    outlineVariant = Color(0xFF3E5243)
)

internal val AutumnExtendedColors = StillExtendedColors(
    titleColor = Color(0xFFF4C95D),         // Titolo "Still" e icone: Giallo Oro Ginkgo
    timerDigitsColor = Color(0xFFFFF8EB),   // Numeri Timer: Avorio Pergamena Caldo
    overtime = Color(0xFFF4C95D),           // Extra: Oro Autunnale
    sandLight = Color(0xFFF4A261),          // Sabbia Chiara: Ambra Dorata
    sandDeep = Color(0xFFB8332A),           // Sabbia Profonda: Rosso Acero Giapponese Momiji
    woodFrame = Color(0xFF8C5830),          // Struttura Clessidra: Legno di Castagno / Corteccia
    glassOutline = Color(0xFF7AA88F),       // Vetro Clessidra: Vetro Soffiato Verde-Acqua Salvia
    skyTop = Color(0xFF211412),             // Orizzonte Cremisi/Ambra Notturno
    skyBottom = Color(0xFF0B1711),          // Sottobosco di Pino
    auroraPrimary = Color(0xFFCB1B45),      // Luce soffusa Acero Cremisi
    auroraSecondary = Color(0xFFE1AD01)     // Luce soffusa Oro Ginkgo
)

// ============================================================================
// 4. INVERNO — Kasane "Tsubaki" (Camelia nella Neve), "Matsu-gasane" & Baita Alpina
// (Ghiaccio Cristallino, Rosso Camelia/Camino, Verde Abete, Aurora Viola & Legno Caldo)
// ============================================================================
internal val WinterColorScheme = darkColorScheme(
    primary = Color(0xFFE06D53),            // Pulsante Inizia: Rosso Camelia Invernale / Calore Camino
    onPrimary = Color(0xFF290902),
    primaryContainer = Color(0xFF1B354D),
    onPrimaryContainer = Color(0xFFE0F2FE),
    secondary = Color(0xFF40916C),          // Pulsante Pausa: Verde Abete Sempreverde (Tokiwa-iro)
    onSecondary = Color(0xFF041F13),
    secondaryContainer = Color(0xFF163829),
    onSecondaryContainer = Color(0xFFD8F3DC),
    tertiary = Color(0xFF90E0EF),           // Pulsanti -5m..+5m & Preparazione: Ciano Ghiaccio
    onTertiary = Color(0xFF032029),
    tertiaryContainer = Color(0xFF143547),
    onTertiaryContainer = Color(0xFFE0FAFF),
    background = Color(0xFF071624),         // Sfondo: Blu Notte Artica
    onBackground = Color(0xFFF8FBFF),
    surface = Color(0xFF0D2033),
    onSurface = Color(0xFFF8FBFF),
    surfaceVariant = Color(0xFF162D45),
    onSurfaceVariant = Color(0xFFB7C9B9),   // Sottotitolo: Grigio-Verde Pino Innevato
    outline = Color(0xFF2D4B6B),
    outlineVariant = Color(0xFF2D4B6B)
)

internal val WinterExtendedColors = StillExtendedColors(
    titleColor = Color(0xFFA9E5FF),         // Titolo "Still" e icone: Azzurro Cristallo di Brina
    timerDigitsColor = Color(0xFFF2EFE6),   // Numeri Timer: Bianco Neve Alabastro
    overtime = Color(0xFFFFD166),           // Extra: Luce Calda Dorata
    sandLight = Color(0xFFF2EFE6),          // Sabbia Chiara: Polvere di Neve Cristallina
    sandDeep = Color(0xFF5E81AC),           // Sabbia Profonda: Quarzo Blu-Indaco Artico
    woodFrame = Color(0xFF8A5A44),          // Struttura Clessidra: Legno di Abete Rosso / Bambù Affumicato
    glassOutline = Color(0xFFCAF0F8),       // Vetro Clessidra: Cristallo Ghiaccio Puro
    skyTop = Color(0xFF14182E),             // Cielo Violaceo Boreale
    skyBottom = Color(0xFF071624),          // Ghiacciaio Notturno
    auroraPrimary = Color(0xFF48CAE4),      // Luce soffusa Ghiaccio
    auroraSecondary = Color(0xFF8E7DBE)     // Luce soffusa Viola Aurora Boreale
)
