package com.sanluca.still.wear

import com.sanluca.still.wear.ui.theme.StillColorPalette
import org.junit.Assert.assertEquals
import org.junit.Test

class WearBellSoundAndThemeTest {

    @Test
    fun colorPalette_roundTripsAndDefaultsToAutumn() {
        val entries = StillColorPalette.entries
        assertEquals(entries.size, entries.map { it.id }.toSet().size)
        for (palette in entries) {
            assertEquals(palette, StillColorPalette.fromId(palette.id))
        }
        assertEquals(StillColorPalette.AUTUMN, StillColorPalette.fromId("unknown"))
        assertEquals(StillColorPalette.AUTUMN, StillColorPalette.fromId(null))
    }
}
