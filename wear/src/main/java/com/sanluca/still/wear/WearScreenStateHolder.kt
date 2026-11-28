package com.sanluca.still.wear

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.wear.tiles.TileService
import com.sanluca.still.core.cancelGongVibration
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale

private class PendingAmbientTick {
    var hasPending: Boolean = false
    var isPreparing: Boolean = false
    var prepLeft: Int = 0
    var timeLeft: Int = 0
    var isOvertime: Boolean = false
    var overtimeSeconds: Int = 0
    var initialTime: Int = 300
}

@Stable
internal class WearScreenStateHolder(
    private val context: Context
) {
    private val initialSnapshot: WearTimerSnapshot = StillTimerService.timerState.value
    private val initialRunning: Boolean = StillTimerService.isServiceRunning || initialSnapshot.isRunning
    private val clockFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val pendingAmbientTick = PendingAmbientTick()

    var selectedTimeInSeconds by mutableIntStateOf(
        if (initialRunning) initialSnapshot.initialTime else getSavedSelectedTime(context)
    )
    var prepTimeSeconds by mutableIntStateOf(getSavedPrepTime(context))
    var gongFreqHz by mutableDoubleStateOf(getSavedGongFrequency(context))
    var gongVolumePercent by mutableIntStateOf(getSavedGongVolume(context))
    var alertMode by mutableIntStateOf(getSavedAlertMode(context))
    var currentClockText by mutableStateOf(clockFormatter.format(Date()))

    var isPreparing by mutableStateOf(if (initialRunning) initialSnapshot.isPreparing else false)
    var prepTimeLeftSeconds by mutableIntStateOf(if (initialRunning) initialSnapshot.prepLeft else 0)
    var timeLeftInSeconds by mutableIntStateOf(
        if (initialRunning) {
            initialSnapshot.timeLeft
        } else if (selectedTimeInSeconds == -1) {
            0
        } else {
            selectedTimeInSeconds
        }
    )
    var isOvertime by mutableStateOf(if (initialRunning) initialSnapshot.isOvertime else false)
    var overtimeSeconds by mutableIntStateOf(if (initialRunning) initialSnapshot.overtimeSeconds else 0)

    var isTimerRunning by mutableStateOf(initialRunning)
    var hasObservedRunningService by mutableStateOf(initialRunning)
    var pendingStartAfterNotificationPermission by mutableStateOf(false)
    var sessionStartTime by mutableStateOf<Instant?>(
        if (initialRunning && !initialSnapshot.isPreparing) Instant.now() else null
    )

    val progress: Float
        get() = if (selectedTimeInSeconds > 0) {
            timeLeftInSeconds.toFloat() / selectedTimeInSeconds.toFloat()
        } else {
            1f
        }

    val isInitialState: Boolean
        get() = !isTimerRunning &&
            !isOvertime &&
            sessionStartTime == null &&
            (timeLeftInSeconds == selectedTimeInSeconds || (selectedTimeInSeconds == -1 && timeLeftInSeconds == 0))

    val showSettingsPage: Boolean
        get() = !isTimerRunning && !isOvertime

    fun refreshClock() {
        currentClockText = clockFormatter.format(Date())
    }

    fun onServiceSnapshot(snapshot: WearTimerSnapshot, isAmbient: Boolean) {
        if (snapshot.isRunning) {
            hasObservedRunningService = true
            if (!isTimerRunning) {
                isTimerRunning = true
            }
            if (isAmbient && !(snapshot.isOvertime && !isOvertime)) {
                // In Ambient Mode evita ricomposizioni Compose ogni 500 ms: applica su onUpdateAmbient / onExitAmbient
                pendingAmbientTick.isPreparing = snapshot.isPreparing
                pendingAmbientTick.prepLeft = snapshot.prepLeft
                pendingAmbientTick.timeLeft = snapshot.timeLeft
                pendingAmbientTick.isOvertime = snapshot.isOvertime
                pendingAmbientTick.overtimeSeconds = snapshot.overtimeSeconds
                pendingAmbientTick.initialTime = snapshot.initialTime
                pendingAmbientTick.hasPending = true
            } else {
                pendingAmbientTick.hasPending = false
                applyTickState(
                    tickIsPreparing = snapshot.isPreparing,
                    tickPrepLeft = snapshot.prepLeft,
                    tickTimeLeft = snapshot.timeLeft,
                    tickIsOvertime = snapshot.isOvertime,
                    tickOvertimeSec = snapshot.overtimeSeconds,
                    tickInitialTime = snapshot.initialTime
                )
            }
        } else if (hasObservedRunningService && !StillTimerService.isServiceRunning && isTimerRunning) {
            hasObservedRunningService = false
            isTimerRunning = false
            isPreparing = false
        }
    }

    fun flushAmbientTickIfNeeded() {
        refreshClock()
        if (pendingAmbientTick.hasPending && isTimerRunning) {
            pendingAmbientTick.hasPending = false
            applyTickState(
                tickIsPreparing = pendingAmbientTick.isPreparing,
                tickPrepLeft = pendingAmbientTick.prepLeft,
                tickTimeLeft = pendingAmbientTick.timeLeft,
                tickIsOvertime = pendingAmbientTick.isOvertime,
                tickOvertimeSec = pendingAmbientTick.overtimeSeconds,
                tickInitialTime = pendingAmbientTick.initialTime
            )
        }
    }

    fun onResume() {
        if (StillTimerService.isServiceRunning) {
            val snap = StillTimerService.timerState.value
            isTimerRunning = true
            if (snap.isRunning) {
                applyTickState(
                    tickIsPreparing = snap.isPreparing,
                    tickPrepLeft = snap.prepLeft,
                    tickTimeLeft = snap.timeLeft,
                    tickIsOvertime = snap.isOvertime,
                    tickOvertimeSec = snap.overtimeSeconds,
                    tickInitialTime = snap.initialTime
                )
            }
        }
        try {
            flushPendingSessionsToPhone(context)
        } catch (_: Exception) {
        }
    }

    fun syncServiceForRunningState() {
        if (isTimerRunning) {
            if (!StillTimerService.isServiceRunning) {
                if (sessionStartTime == null) {
                    val serviceIntent = Intent(context, StillTimerService::class.java).apply {
                        action = StillTimerService.ACTION_START
                        putExtra(StillTimerService.EXTRA_FREQ, gongFreqHz)
                        putExtra(StillTimerService.EXTRA_TIME, selectedTimeInSeconds)
                        putExtra(StillTimerService.EXTRA_INITIAL_TIME, selectedTimeInSeconds)
                        putExtra(StillTimerService.EXTRA_PREP, prepTimeSeconds)
                        putExtra(StillTimerService.EXTRA_ALERT_MODE, alertMode)
                        putExtra(StillTimerService.EXTRA_VOLUME, gongVolumePercent)
                    }
                    context.startForegroundService(serviceIntent)
                } else {
                    // Resume
                    val serviceIntent = Intent(context, StillTimerService::class.java).apply {
                        action = StillTimerService.ACTION_START
                        putExtra(StillTimerService.EXTRA_FREQ, gongFreqHz)
                        putExtra(StillTimerService.EXTRA_TIME, timeLeftInSeconds)
                        putExtra(StillTimerService.EXTRA_INITIAL_TIME, selectedTimeInSeconds)
                        putExtra(StillTimerService.EXTRA_PREP, -1) // -1 tells the service it's a resume so no start gong
                        putExtra(StillTimerService.EXTRA_ALERT_MODE, alertMode)
                        putExtra(StillTimerService.EXTRA_VOLUME, gongVolumePercent)
                        putExtra(StillTimerService.EXTRA_IS_OVERTIME, isOvertime)
                        putExtra(StillTimerService.EXTRA_OVERTIME_SECONDS, overtimeSeconds)
                    }
                    context.startForegroundService(serviceIntent)
                }
            }
        } else {
            hasObservedRunningService = false
            if (StillTimerService.isServiceRunning) {
                val stopIntent = Intent(context, StillTimerService::class.java).apply {
                    action = StillTimerService.ACTION_STOP
                }
                context.startService(stopIntent)
                StillTimerService.isServiceRunning = false
            }
            isPreparing = false
        }
    }

    fun updateTimeSelection(newTime: Int) {
        selectedTimeInSeconds = newTime
        timeLeftInSeconds = if (newTime == -1) 0 else newTime
        saveSelectedTime(context, newTime)
        try {
            TileService.getUpdater(context.applicationContext).requestUpdate(StillTileService::class.java)
        } catch (_: Exception) {
        }
    }

    fun updateAlertMode(modeValue: Int) {
        alertMode = modeValue
        saveAlertMode(context, modeValue)
    }

    fun updateGongFreq(freq: Double) {
        gongFreqHz = freq
        saveGongFrequency(context, freq)
    }

    fun updateGongVolume(newVol: Int) {
        gongVolumePercent = newVol
        saveGongVolume(context, newVol)
    }

    fun updatePrepTime(newPrep: Int) {
        prepTimeSeconds = newPrep
        savePrepTime(context, newPrep)
    }

    fun toggleTimer(onRequestNotificationPermission: (String) -> Unit) {
        if (!isTimerRunning) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                pendingStartAfterNotificationPermission = true
                onRequestNotificationPermission(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                isTimerRunning = true
            }
        } else {
            isTimerRunning = false
        }
    }

    fun onNotificationPermissionResult() {
        if (pendingStartAfterNotificationPermission) {
            pendingStartAfterNotificationPermission = false
            isTimerRunning = true
        }
    }

    fun resetTimerState() {
        cancelGongVibration(context)
        if (StillTimerService.isServiceRunning) {
            val stopIntent = Intent(context, StillTimerService::class.java).apply {
                action = StillTimerService.ACTION_STOP
            }
            context.startService(stopIntent)
            StillTimerService.isServiceRunning = false
        }
        hasObservedRunningService = false
        StillTimerService.clearHeartRateSamples()
        pendingAmbientTick.hasPending = false

        isTimerRunning = false
        isOvertime = false
        overtimeSeconds = 0
        timeLeftInSeconds = if (selectedTimeInSeconds == -1) 0 else selectedTimeInSeconds
        sessionStartTime = null
    }

    fun saveAndFinishSession(includeExtra: Boolean) {
        val plannedSec = if (selectedTimeInSeconds > 0) {
            if (isOvertime) selectedTimeInSeconds else (selectedTimeInSeconds - timeLeftInSeconds)
        } else {
            timeLeftInSeconds
        }
        val totalSec = if (includeExtra && isOvertime) (plannedSec + overtimeSeconds) else plannedSec
        val end = Instant.now()
        val start = end.minusSeconds(totalSec.toLong())

        if (totalSec >= 10) {
            val hrSummary = StillTimerService.getHeartRateSummary()
            sendSessionToPhone(
                context = context,
                durationSec = totalSec,
                startInstant = start,
                endInstant = end,
                startBpm = hrSummary.startBpm,
                endBpm = hrSummary.endBpm,
                avgBpm = hrSummary.avgBpm
            )
            Toast.makeText(
                context,
                context.getString(R.string.toast_sync_success),
                Toast.LENGTH_SHORT
            ).show()
        }

        resetTimerState()
    }

    private fun applyTickState(
        tickIsPreparing: Boolean,
        tickPrepLeft: Int,
        tickTimeLeft: Int,
        tickIsOvertime: Boolean,
        tickOvertimeSec: Int,
        tickInitialTime: Int
    ) {
        isPreparing = tickIsPreparing
        prepTimeLeftSeconds = tickPrepLeft
        timeLeftInSeconds = tickTimeLeft
        isOvertime = tickIsOvertime
        overtimeSeconds = tickOvertimeSec
        selectedTimeInSeconds = tickInitialTime

        if (sessionStartTime == null && !tickIsPreparing && isTimerRunning) {
            sessionStartTime = Instant.now()
        }
    }
}

@Composable
internal fun rememberWearScreenStateHolder(context: Context): WearScreenStateHolder =
    remember(context) { WearScreenStateHolder(context) }
