package com.sanluca.still

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class SessionHistoryStoreTest {

    private fun entryOn(
        date: LocalDate,
        hour: Int = 10,
        durationMinutes: Int = 10,
        zone: ZoneId = ZoneId.systemDefault(),
        startBpm: Int? = null,
        endBpm: Int? = null,
        avgBpm: Int? = null
    ): SessionLogEntry {
        val startSec = date.atTime(LocalTime.of(hour, 0)).atZone(zone).toEpochSecond()
        val endSec = startSec + durationMinutes * 60L
        return SessionLogEntry(
            id = startSec * 1000L,
            dateFormatted = "",
            durationMinutes = durationMinutes,
            startEpochSec = startSec,
            endEpochSec = endSec,
            rawString = "${startSec * 1000L}|$durationMinutes|$startSec|$endSec",
            startBpm = startBpm,
            endBpm = endBpm,
            avgBpm = avgBpm
        )
    }

    @Test
    fun calculateStreakDays_handlesEmptyTodayYesterdayGapAndDuplicates() {
        assertEquals(0, calculateStreakDays(emptyList()))

        val today = LocalDate.now(ZoneId.systemDefault())
        assertEquals(1, calculateStreakDays(listOf(entryOn(today))))

        // Multiple sessions on the same day count as a single day
        assertEquals(
            3,
            calculateStreakDays(
                listOf(
                    entryOn(today, hour = 8),
                    entryOn(today, hour = 19),
                    entryOn(today.minusDays(1)),
                    entryOn(today.minusDays(2)),
                    entryOn(today.minusDays(4)) // gap at -3 breaks streak
                )
            )
        )

        // Not yet meditated today: streak continues from yesterday
        assertEquals(
            2,
            calculateStreakDays(
                listOf(
                    entryOn(today.minusDays(1)),
                    entryOn(today.minusDays(2))
                )
            )
        )

        // Last session was 2 days ago: current streak is 0
        assertEquals(0, calculateStreakDays(listOf(entryOn(today.minusDays(2)))))
    }

    @Test
    fun calculateBestStreakDays_findsLongestHistoricalRun() {
        assertEquals(0, calculateBestStreakDays(emptyList()))

        val base = LocalDate.of(2025, 5, 1)
        val history = listOf(
            entryOn(base),
            entryOn(base, hour = 20), // duplicate day
            entryOn(base.plusDays(1)),
            entryOn(base.plusDays(2)),
            entryOn(base.plusDays(3)), // 4-day run
            entryOn(base.plusDays(10)),
            entryOn(base.plusDays(11)) // 2-day run
        )
        assertEquals(4, calculateBestStreakDays(history))
    }

    @Test
    fun formatHistoryAsCsv_andMergeRoundTrip_preservesSessionsAndHeartRate() {
        val utc = ZoneId.of("UTC")
        val e1 = entryOn(
            date = LocalDate.of(2026, 3, 10),
            hour = 7,
            durationMinutes = 15,
            zone = utc,
            startBpm = 68,
            endBpm = 61,
            avgBpm = 64
        )
        val e2 = entryOn(
            date = LocalDate.of(2026, 3, 11),
            hour = 21,
            durationMinutes = 20,
            zone = utc
        )

        val csv = formatHistoryAsCsv(listOf(e1, e2), zone = utc)
        val lines = csv.lines()
        assertEquals(3, lines.size)
        assertEquals(
            "date_time,duration_minutes,start_epoch_sec,end_epoch_sec,start_bpm,end_bpm,avg_bpm",
            lines[0]
        )
        assertEquals(
            "2026-03-10 07:00,15,${e1.startEpochSec},${e1.endEpochSec},68,61,64",
            lines[1]
        )
        assertEquals(
            "2026-03-11 21:00,20,${e2.startEpochSec},${e2.endEpochSec},,,",
            lines[2]
        )

        val (merged, result) = mergeCsvIntoHistory(emptySet(), csv, zone = utc)
        assertEquals(2, result.importedCount)
        assertEquals(0, result.skippedDuplicates)
        assertEquals(0, result.invalidCount)
        assertTrue(merged.contains("${e1.startEpochSec * 1000L}|15|${e1.startEpochSec}|${e1.endEpochSec}|68|61|64"))
        assertTrue(merged.contains("${e2.startEpochSec * 1000L}|20|${e2.startEpochSec}|${e2.endEpochSec}"))

        // Re-importing the same CSV skips both as duplicates
        val (mergedAgain, secondResult) = mergeCsvIntoHistory(merged, csv, zone = utc)
        assertEquals(0, secondResult.importedCount)
        assertEquals(2, secondResult.skippedDuplicates)
        assertEquals(0, secondResult.invalidCount)
        assertEquals(merged, mergedAgain)
    }

    @Test
    fun mergeCsvIntoHistory_supportsBomSemicolonFallbackDateAndValidatesBpm() {
        val utc = ZoneId.of("UTC")
        val csv = buildString {
            append('\uFEFF')
            appendLine("date_time;duration_minutes;start_epoch_sec;end_epoch_sec;start_bpm;end_bpm;avg_bpm")
            // Valid via fallback dd/MM/yyyy HH:mm + out-of-range BPM filtered out
            appendLine("\"15/04/2026 08:30\";12;;;20;250;65")
            // Duplicate of the same fallback timestamp inside the same file
            appendLine("2026-04-15 08:30;12;;;;;")
            // Invalid row (missing epoch and unparseable date)
            appendLine("not-a-date;0;;;;;")
        }

        val (updated, result) = mergeCsvIntoHistory(emptySet(), csv, zone = utc)
        assertEquals(1, result.importedCount)
        assertEquals(1, result.skippedDuplicates)
        assertEquals(1, result.invalidCount)

        val expectedStart = LocalDate.of(2026, 4, 15)
            .atTime(8, 30)
            .atZone(utc)
            .toEpochSecond()
        val expectedEnd = expectedStart + 12 * 60L
        assertEquals(
            setOf("${expectedStart * 1000L}|12|$expectedStart|$expectedEnd|0|0|65"),
            updated
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun mergeCsvIntoHistory_throwsOnEmptyContent() {
        mergeCsvIntoHistory(emptySet(), "   \n  ")
    }

    @Test(expected = IllegalArgumentException::class)
    fun mergeCsvIntoHistory_throwsOnHeaderlessGarbage() {
        mergeCsvIntoHistory(emptySet(), "foo,bar,baz\ninvalid,row,here")
    }
}
