package com.sanluca.still

import com.sanluca.still.core.BellSound
import com.sanluca.still.core.TINGSHA_FREQ_HZ
import com.sanluca.still.ui.theme.StillColorPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BellSoundAndCatalogTest {

    @Test
    fun normalizeBellFrequency_preservesValidAndFallsBackOnUnknown() {
        for (bell in BellSound.entries) {
            assertEquals(bell.code, normalizeBellFrequency(bell.code, 432.0), 0.0)
        }
        assertEquals(TINGSHA_FREQ_HZ, normalizeBellFrequency(999.0, TINGSHA_FREQ_HZ), 0.0)
        assertEquals(432.0, normalizeBellFrequency(Double.NaN, 432.0), 0.0)
    }

    @Test
    fun ambientSoundscape_roundTripsAndMapsLegacyIds() {
        val entries = AmbientSoundscape.entries
        assertEquals(entries.size, entries.map { it.id }.toSet().size)
        for (soundscape in entries) {
            assertEquals(soundscape, AmbientSoundscape.fromId(soundscape.id))
        }

        assertEquals(AmbientSoundscape.LIGHT_RAIN, AmbientSoundscape.fromId("rain"))
        assertEquals(AmbientSoundscape.OFF, AmbientSoundscape.fromId("wind"))
        assertEquals(AmbientSoundscape.OFF, AmbientSoundscape.fromId("unknown_id"))
        assertEquals(AmbientSoundscape.OFF, AmbientSoundscape.fromId(null))

        assertNull(AmbientSoundscape.OFF.rawResId)
        assertNull(AmbientSoundscape.DRONE.rawResId)
        assertNull(AmbientSoundscape.CUSTOM.rawResId)
        assertNotNull(AmbientSoundscape.LIGHT_RAIN.rawResId)
        assertNotNull(AmbientSoundscape.GENTLE_RAIN.rawResId)
        assertNotNull(AmbientSoundscape.BIRDS_RAIN.rawResId)
        assertNotNull(AmbientSoundscape.OCEAN.rawResId)
        assertNotNull(AmbientSoundscape.RIVER.rawResId)
        assertNotNull(AmbientSoundscape.FOREST.rawResId)
    }

    @Test
    fun colorPalette_roundTripsAndMapsLegacyIds() {
        val entries = StillColorPalette.entries
        assertEquals(entries.size, entries.map { it.id }.toSet().size)
        for (palette in entries) {
            assertEquals(palette, StillColorPalette.fromId(palette.id))
        }

        assertEquals(StillColorPalette.SPRING, StillColorPalette.fromId("champagne"))
        assertEquals(StillColorPalette.SPRING, StillColorPalette.fromId("sage"))
        assertEquals(StillColorPalette.SUMMER, StillColorPalette.fromId("titanium"))
        assertEquals(StillColorPalette.WINTER, StillColorPalette.fromId("deep_space"))
        assertEquals(StillColorPalette.WINTER, StillColorPalette.fromId("nothing"))
        assertEquals(StillColorPalette.AUTUMN, StillColorPalette.fromId("unknown"))
        assertEquals(StillColorPalette.AUTUMN, StillColorPalette.fromId(null))
    }
}
