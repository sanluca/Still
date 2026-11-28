package com.sanluca.still.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class StillCoreTest {

    @Test
    fun bellSound_hasThirteenUniqueEntriesAndDistinctCodes() {
        val entries = BellSound.entries
        assertEquals(13, entries.size)
        assertEquals(entries.size, entries.map { it.code }.toSet().size)
        assertEquals(entries.size, entries.map { it.rawResId }.toSet().size)
        assertEquals(entries.size, entries.map { it.labelRes }.toSet().size)

        // Ensure pairwise distance prevents any tolerance overlap (regression guard for 1001..1004 bug)
        for (i in entries.indices) {
            for (j in i + 1 until entries.size) {
                val distance = abs(entries[i].code - entries[j].code)
                assertTrue(
                    "Codes too close: ${entries[i]} (${entries[i].code}) vs ${entries[j]} (${entries[j].code})",
                    distance > 1.0
                )
            }
        }
    }

    @Test
    fun bellSound_fromCode_roundTripsExactAndFloatValues() {
        for (bell in BellSound.entries) {
            assertEquals(bell, BellSound.fromCode(bell.code))
            val floatRoundTrip = bell.code.toFloat().toDouble()
            assertEquals(bell, BellSound.fromCode(floatRoundTrip))
        }
    }

    @Test
    fun bellSound_fromCode_handlesToleranceLegacyAndInvalidValues() {
        assertEquals(BellSound.CLASSIC_BOWL, BellSound.fromCode(432.49))
        assertEquals(BellSound.TEMPLE_BOWL, BellSound.fromCode(432.51, defaultBell = BellSound.TEMPLE_BOWL))
        assertEquals(BellSound.PURE_BELL, BellSound.fromCode(2500.1))
        assertEquals(BellSound.PURE_BELL, BellSound.fromCode(3200.0))
        assertEquals(BellSound.MONK_GONG, BellSound.fromCode(Double.NaN, defaultBell = BellSound.MONK_GONG))
        assertEquals(BellSound.MONK_GONG, BellSound.fromCode(Double.POSITIVE_INFINITY, defaultBell = BellSound.MONK_GONG))
        assertEquals(BellSound.MONK_GONG, BellSound.fromCode(Double.NEGATIVE_INFINITY, defaultBell = BellSound.MONK_GONG))
        assertEquals(BellSound.CLASSIC_BOWL, BellSound.fromCode(999.0))
    }

    @Test
    fun formatTime_formatsMinutesAndSeconds() {
        assertEquals("00:00", formatTime(0))
        assertEquals("00:05", formatTime(5))
        assertEquals("00:59", formatTime(59))
        assertEquals("01:00", formatTime(60))
        assertEquals("05:00", formatTime(300))
        assertEquals("10:00", formatTime(600))
        assertEquals("61:01", formatTime(3661))
    }

    @Test
    fun alertModeConstants_haveExpectedValues() {
        assertEquals(0, ALERT_MODE_BOTH)
        assertEquals(1, ALERT_MODE_SOUND)
        assertEquals(2, ALERT_MODE_VIBRATION)
    }
}
