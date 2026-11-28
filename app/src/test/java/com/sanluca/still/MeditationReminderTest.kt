package com.sanluca.still

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class MeditationReminderTest {

    private val utc: ZoneId = ZoneId.of("UTC")

    @Test
    fun reminderFrequency_fromId_roundTripsAndDefaultsToDaily() {
        for (freq in ReminderFrequency.entries) {
            assertEquals(freq, ReminderFrequency.fromId(freq.id))
        }
        assertEquals(ReminderFrequency.DAILY, ReminderFrequency.fromId("unknown"))
        assertEquals(ReminderFrequency.DAILY, ReminderFrequency.fromId(null))
    }

    @Test
    fun computeNextReminderTriggerMillis_daily_respectsFiveSecondBufferAndAfterTrigger() {
        val settings = MeditationReminderSettings(
            enabled = true,
            hour = 8,
            minute = 30,
            frequency = ReminderFrequency.DAILY
        )

        // Well before target -> same day
        val before = ZonedDateTime.of(2026, 6, 1, 8, 0, 0, 0, utc)
        val expectedToday = ZonedDateTime.of(2026, 6, 1, 8, 30, 0, 0, utc)
        assertEquals(
            expectedToday.toInstant().toEpochMilli(),
            computeNextReminderTriggerMillis(settings, now = before, afterTrigger = false)
        )

        // Within 5-second buffer -> tomorrow
        val tooClose = ZonedDateTime.of(2026, 6, 1, 8, 29, 57, 0, utc)
        val expectedTomorrow = ZonedDateTime.of(2026, 6, 2, 8, 30, 0, 0, utc)
        assertEquals(
            expectedTomorrow.toInstant().toEpochMilli(),
            computeNextReminderTriggerMillis(settings, now = tooClose, afterTrigger = false)
        )

        // After trigger -> always tomorrow
        assertEquals(
            expectedTomorrow.toInstant().toEpochMilli(),
            computeNextReminderTriggerMillis(settings, now = before, afterTrigger = true)
        )
    }

    @Test
    fun computeNextReminderTriggerMillis_every2And3Days_advancesProperlyAfterTrigger() {
        val now = ZonedDateTime.of(2026, 6, 1, 8, 30, 1, 0, utc)

        val every2 = MeditationReminderSettings(
            enabled = true,
            hour = 8,
            minute = 30,
            frequency = ReminderFrequency.EVERY_2_DAYS
        )
        val next2 = Instant.ofEpochMilli(
            computeNextReminderTriggerMillis(every2, now = now, afterTrigger = true)
        ).atZone(utc)
        assertEquals(ZonedDateTime.of(2026, 6, 3, 8, 30, 0, 0, utc), next2)

        val every3 = every2.copy(frequency = ReminderFrequency.EVERY_3_DAYS)
        val next3 = Instant.ofEpochMilli(
            computeNextReminderTriggerMillis(every3, now = now, afterTrigger = true)
        ).atZone(utc)
        assertEquals(ZonedDateTime.of(2026, 6, 4, 8, 30, 0, 0, utc), next3)
    }

    @Test
    fun computeNextReminderTriggerMillis_weekdays_skipsUnselectedDays() {
        // 2026-06-05 is Friday (dayOfWeek.value == 5)
        val fridayAfterTarget = ZonedDateTime.of(2026, 6, 5, 9, 0, 0, 0, utc)
        val settings = MeditationReminderSettings(
            enabled = true,
            hour = 8,
            minute = 0,
            frequency = ReminderFrequency.WEEKDAYS,
            selectedDays = setOf(1, 3, 5) // Mon, Wed, Fri
        )

        // Should skip Saturday (6) and Sunday (7) and land on Monday 2026-06-08 08:00
        val next = Instant.ofEpochMilli(
            computeNextReminderTriggerMillis(settings, now = fridayAfterTarget, afterTrigger = false)
        ).atZone(utc)
        assertEquals(ZonedDateTime.of(2026, 6, 8, 8, 0, 0, 0, utc), next)
    }

    @Test
    fun computeNextReminderTriggerMillis_weekdays_emptySelectionFallsBackToAllDays() {
        val fridayMorning = ZonedDateTime.of(2026, 6, 5, 7, 0, 0, 0, utc)
        val settings = MeditationReminderSettings(
            enabled = true,
            hour = 8,
            minute = 0,
            frequency = ReminderFrequency.WEEKDAYS,
            selectedDays = emptySet()
        )

        val next = Instant.ofEpochMilli(
            computeNextReminderTriggerMillis(settings, now = fridayMorning, afterTrigger = false)
        ).atZone(utc)
        assertEquals(ZonedDateTime.of(2026, 6, 5, 8, 0, 0, 0, utc), next)
    }
}
