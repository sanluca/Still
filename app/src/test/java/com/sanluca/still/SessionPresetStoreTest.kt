package com.sanluca.still

import com.sanluca.still.core.ALERT_MODE_VIBRATION
import com.sanluca.still.core.BellSound
import com.sanluca.still.core.TINGSHA_FREQ_HZ
import com.sanluca.still.ui.theme.StillColorPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionPresetStoreTest {

    @Test
    fun normalizePreset_rejectsBlankName() {
        assertNull(normalizePreset(MeditationPreset(name = "   ")))
        assertNull(normalizePreset(MeditationPreset(name = "")))
    }

    @Test
    fun normalizePreset_clampsValuesNormalizesLegacyFieldsAndGeneratesId() {
        val raw = MeditationPreset(
            id = "   ",
            name = "  Mattina Zen  ",
            durationSeconds = 0, // invalid -> null
            prepSeconds = 1500, // clamped to 999
            gongFreqHz = 3000.0, // legacy high freq -> PURE_BELL (2640.0)
            gongVolumePercent = 5, // clamped to 20
            alertMode = 99, // invalid -> null
            intervalMins = -4, // clamped to 0
            intervalGongFreqHz = Double.NaN, // fallback -> TINGSHA_FREQ_HZ
            ambientSoundId = "rain", // legacy -> light_rain
            ambientVolumePercent = 150, // clamped to 100
            customAmbientUri = "   ", // blank -> null
            customAmbientName = "  ", // blank -> null
            timerStyle = 7, // invalid (valid 0..3) -> null
            colorPaletteId = "deep_space", // legacy -> winter
            autoDndEnabled = true
        )

        val normalized = normalizePreset(raw)
        assertNotNull(normalized)
        normalized!!

        assertTrue(normalized.id.isNotBlank())
        assertEquals("Mattina Zen", normalized.name)
        assertNull(normalized.durationSeconds)
        assertEquals(999, normalized.prepSeconds)
        assertEquals(BellSound.PURE_BELL.code, normalized.gongFreqHz!!, 0.0)
        assertEquals(20, normalized.gongVolumePercent)
        assertNull(normalized.alertMode)
        assertEquals(0, normalized.intervalMins)
        assertEquals(TINGSHA_FREQ_HZ, normalized.intervalGongFreqHz!!, 0.0)
        assertEquals(AmbientSoundscape.LIGHT_RAIN.id, normalized.ambientSoundId)
        assertEquals(AmbientSoundscape.LIGHT_RAIN, normalized.effectiveAmbientSoundscape())
        assertEquals(100, normalized.ambientVolumePercent)
        assertNull(normalized.customAmbientUri)
        assertNull(normalized.customAmbientName)
        assertNull(normalized.timerStyle)
        assertEquals(StillColorPalette.WINTER.id, normalized.colorPaletteId)
        assertEquals(StillColorPalette.WINTER, normalized.effectiveColorPalette())
        assertEquals(true, normalized.autoDndEnabled)
    }

    @Test
    fun normalizePreset_preservesFreeTimerAndValidBellCodes() {
        val freeTimer = normalizePreset(
            MeditationPreset(
                id = "preset-free",
                name = "Libero",
                durationSeconds = -1,
                gongFreqHz = BellSound.NATURAL_BOWL.code,
                intervalGongFreqHz = BellSound.DEEP_BOWL.code,
                alertMode = ALERT_MODE_VIBRATION,
                timerStyle = TIMER_STYLE_ESSENTIAL
            )
        )

        assertNotNull(freeTimer)
        freeTimer!!
        assertEquals("preset-free", freeTimer.id)
        assertEquals(-1, freeTimer.durationSeconds)
        assertEquals(BellSound.NATURAL_BOWL.code, freeTimer.gongFreqHz!!, 0.0)
        assertEquals(BellSound.DEEP_BOWL.code, freeTimer.intervalGongFreqHz!!, 0.0)
        assertEquals(ALERT_MODE_VIBRATION, freeTimer.alertMode)
        assertEquals(TIMER_STYLE_ESSENTIAL, freeTimer.timerStyle)
    }
}
