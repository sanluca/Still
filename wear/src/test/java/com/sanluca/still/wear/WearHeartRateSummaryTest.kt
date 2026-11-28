package com.sanluca.still.wear

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class WearHeartRateSummaryTest {

    @Before
    @After
    fun resetSamples() {
        StillTimerService.clearHeartRateSamples()
    }

    @Test
    fun emptySummary_returnsAllNulls() {
        val summary = StillTimerService.getHeartRateSummary()
        assertNull(summary.startBpm)
        assertNull(summary.endBpm)
        assertNull(summary.avgBpm)
    }

    @Test
    fun singleSample_setsStartEndAndAverageEqually() {
        StillTimerService.recordHeartRateSample(64)
        val summary = StillTimerService.getHeartRateSummary()
        assertEquals(64, summary.startBpm)
        assertEquals(64, summary.endBpm)
        assertEquals(64, summary.avgBpm)
    }

    @Test
    fun multipleSamples_tracksStartEndAndRoundedAverage() {
        StillTimerService.recordHeartRateSample(72)
        StillTimerService.recordHeartRateSample(66)
        StillTimerService.recordHeartRateSample(62)
        // Average = 200 / 3 = 66.666... -> rounds to 67
        val summary = StillTimerService.getHeartRateSummary()
        assertEquals(72, summary.startBpm)
        assertEquals(62, summary.endBpm)
        assertEquals(67, summary.avgBpm)

        StillTimerService.clearHeartRateSamples()
        assertEquals(HeartRateSummary(), StillTimerService.getHeartRateSummary())
    }
}
