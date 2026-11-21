package com.sanluca.still

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

data class StillTimerState(
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val isPreparing: Boolean = false,
    val isOvertime: Boolean = false,
    val selectedTimeSeconds: Int = 600,
    val activeSessionPlannedSeconds: Int = 600,
    val prepLeftSeconds: Int = 0,
    val timeLeftSeconds: Int = 600,
    val overtimeSeconds: Int = 0,
    val sessionStartEpochMilli: Long? = null
)

class StillTimerService : Service() {
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)
    private val ambientPlayer = AmbientSoundPlayer(serviceScope)
    private val activeDuckingGongs = AtomicInteger(0)

    private var isTimerRunning = false
    private var autoDndEnabled = false
    private var wakeLock: PowerManager.WakeLock? = null
    private var timerJob: Job? = null
    private var gongJob: Job? = null
    private var stopAfterFadeJob: Job? = null
    @Volatile private var currentTargetSeconds = 0
    @Volatile private var currentElapsedSeconds = 0
    @Volatile private var currentPlannedSeconds = 0
    @Volatile private var currentSelectedSeconds = 600
    @Volatile private var currentAmbientSoundscape = AmbientSoundscape.OFF
    @Volatile private var currentAmbientVolume = 50
    @Volatile private var isInPreparationPhase = false
    @Volatile private var isStoppingForPause = false

    companion object {
        // true solo finché il timer del service è realmente attivo
        @Volatile var isRunning = false
        private const val CHANNEL_ID = "still_timer"
        private const val NOTIFICATION_ID = 1
        private const val WAKELOCK_MARGIN_MS = 15 * 60 * 1000L
        private const val WAKELOCK_RENEW_SEC = 10 * 60

        private val _timerState = MutableStateFlow(StillTimerState())
        val timerState: StateFlow<StillTimerState> = _timerState.asStateFlow()

        fun resetState(selectedTimeSeconds: Int = 600) {
            _timerState.value = StillTimerState(
                isRunning = false,
                isPaused = false,
                isPreparing = false,
                isOvertime = false,
                selectedTimeSeconds = selectedTimeSeconds,
                activeSessionPlannedSeconds = selectedTimeSeconds,
                prepLeftSeconds = 0,
                timeLeftSeconds = if (selectedTimeSeconds == -1) 0 else selectedTimeSeconds,
                overtimeSeconds = 0,
                sessionStartEpochMilli = null
            )
        }

        fun adjustPausedState(deltaSeconds: Int) {
            val current = _timerState.value
            if (!current.isPaused || current.activeSessionPlannedSeconds <= 0 || current.isOvertime || deltaSeconds == 0) {
                return
            }
            if (deltaSeconds < 0 && current.timeLeftSeconds <= -deltaSeconds) return
            val updatedPlanned = (current.activeSessionPlannedSeconds + deltaSeconds).coerceAtLeast(1)
            val updatedLeft = (current.timeLeftSeconds + deltaSeconds).coerceAtLeast(1)
            _timerState.value = current.copy(
                activeSessionPlannedSeconds = updatedPlanned,
                timeLeftSeconds = updatedLeft
            )
        }
    }

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(CHANNEL_ID, getString(R.string.app_name), NotificationManager.IMPORTANCE_LOW)
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(statusText: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_still_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    private fun updateNotification(statusText: String) {
        if (!isTimerRunning) return
        try {
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(NOTIFICATION_ID, buildNotification(statusText))
        } catch (_: Exception) {
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "START") {
            stopAfterFadeJob?.cancel()
            stopAfterFadeJob = null

            val freq = intent.getDoubleExtra("FREQ", 432.0)
            val intervalFreq = intent.getDoubleExtra("INTERVAL_FREQ", TINGSHA_FREQ_HZ)
            val intervalMins = intent.getIntExtra("INTERVAL", 0)
            val selectedTime = intent.getIntExtra("TIME", 600)
            val isResume = intent.getBooleanExtra("RESUME", false) // ripresa dopo pausa: TIME è il tempo rimanente
            val elapsedOffset = intent.getIntExtra("ELAPSED_OFFSET", 0)
            val prepSeconds = if (isResume) 0 else intent.getIntExtra("PREP", 0).coerceAtLeast(0) // preparazione contata dal service
            autoDndEnabled = intent.getBooleanExtra("DND", false)
            val gongVolume = intent.getIntExtra("VOLUME", 100)
            val alertMode = intent.getIntExtra("ALERT_MODE", ALERT_MODE_BOTH)
            currentAmbientSoundscape = AmbientSoundscape.fromId(intent.getStringExtra("AMBIENT_SOUND"))
            currentAmbientVolume = intent.getIntExtra("AMBIENT_VOLUME", 50).coerceIn(10, 100)

            val plannedTime = intent.getIntExtra("PLANNED_TIME", selectedTime)
            val originalSelectedTime = intent.getIntExtra("SELECTED_TIME", plannedTime)
            currentPlannedSeconds = plannedTime
            currentSelectedSeconds = originalSelectedTime
            isStoppingForPause = false

            val initialNotificationText = when {
                prepSeconds > 0 -> getString(R.string.notification_preparing)
                plannedTime == -1 -> getString(R.string.notification_no_limit)
                else -> getString(R.string.notification_meditating)
            }
            startForeground(NOTIFICATION_ID, buildNotification(initialNotificationText))
            startTimer(freq, intervalMins, selectedTime, isResume, elapsedOffset, gongVolume, alertMode, intervalFreq, prepSeconds)
        } else if (intent?.action == "UPDATE_AMBIENT") {
            val newSoundscape = AmbientSoundscape.fromId(intent.getStringExtra("AMBIENT_SOUND"))
            val newVolume = intent.getIntExtra("AMBIENT_VOLUME", currentAmbientVolume).coerceIn(10, 100)
            currentAmbientSoundscape = newSoundscape
            currentAmbientVolume = newVolume
            if (isTimerRunning && !isInPreparationPhase) {
                when {
                    newSoundscape == AmbientSoundscape.OFF -> ambientPlayer.stop()
                    ambientPlayer.isPlayingSoundscape(newSoundscape) -> ambientPlayer.setVolumePercent(newVolume)
                    else -> ambientPlayer.switchSoundscape(this, newSoundscape, newVolume)
                }
            }
        } else if (intent?.action == "ADJUST_TIME") {
            val deltaSeconds = intent.getIntExtra("DELTA_SECONDS", 0)
            if (isTimerRunning && currentTargetSeconds > 0 && deltaSeconds != 0) {
                val minTarget = currentElapsedSeconds + 1
                val updated = (currentTargetSeconds + deltaSeconds).coerceAtLeast(minTarget)
                val appliedDelta = updated - currentTargetSeconds
                currentTargetSeconds = updated
                currentPlannedSeconds = (currentPlannedSeconds + appliedDelta).coerceAtLeast(1)
                val updatedRemaining = (currentTargetSeconds - currentElapsedSeconds).coerceAtLeast(1)
                _timerState.value = _timerState.value.copy(
                    activeSessionPlannedSeconds = currentPlannedSeconds,
                    timeLeftSeconds = updatedRemaining
                )
                if (deltaSeconds > 0) {
                    val remainingMs = (updated - currentElapsedSeconds).coerceAtLeast(0) * 1000L
                    acquireWakeLock(remainingMs + WAKELOCK_MARGIN_MS)
                }
            }
        } else if (intent?.action == "PAUSE") {
            isStoppingForPause = true
            val fadeJob = pauseTimer()
            scheduleStopSelfAfterFade(startId, fadeJob)
        } else if (intent?.action == "STOP") {
            isStoppingForPause = false
            val fadeJob = stopTimer()
            scheduleStopSelfAfterFade(startId, fadeJob)
        }
        return START_NOT_STICKY
    }

    private fun scheduleStopSelfAfterFade(startId: Int, fadeJob: Job?) {
        stopAfterFadeJob?.cancel()
        if (fadeJob == null) {
            if (!isTimerRunning) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            }
            stopSelf(startId)
        } else {
            stopAfterFadeJob = serviceScope.launch {
                try {
                    fadeJob.join()
                } finally {
                    if (!isTimerRunning) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf(startId)
                    }
                }
            }
        }
    }

    private fun launchGongWithDucking(freqHz: Double, repeatCount: Int, volumePercent: Int) {
        gongJob?.cancel()
        gongJob = serviceScope.launch(Dispatchers.Default) {
            if (activeDuckingGongs.incrementAndGet() == 1) {
                ambientPlayer.setDucking(true)
            }
            try {
                playGongSound(
                    this@StillTimerService,
                    freqHz,
                    repeatCount = repeatCount,
                    volumePercent = volumePercent
                )
            } finally {
                if (activeDuckingGongs.decrementAndGet() <= 0) {
                    activeDuckingGongs.set(0)
                    ambientPlayer.setDucking(false)
                }
            }
        }
    }

    private fun startTimer(
        gongFreqHz: Double,
        intervalMins: Int,
        selectedTimeInSeconds: Int,
        isResume: Boolean = false,
        elapsedOffset: Int = 0,
        gongVolume: Int = 100,
        alertMode: Int = ALERT_MODE_BOTH,
        intervalGongFreqHz: Double = gongFreqHz,
        prepSeconds: Int = 0
    ) {
        if (isTimerRunning) return
        isTimerRunning = true
        isRunning = true
        isInPreparationPhase = prepSeconds > 0
        currentTargetSeconds = selectedTimeInSeconds
        currentElapsedSeconds = 0

        val shouldPlaySound = alertMode == ALERT_MODE_BOTH || alertMode == ALERT_MODE_SOUND
        val shouldVibrate = alertMode == ALERT_MODE_BOTH || alertMode == ALERT_MODE_VIBRATION

        if (autoDndEnabled) {
            enableDoNotDisturb(this)
        }

        // WakeLock vincolato alla sessione: durata prevista + 15 min di margine
        val wakeLockTimeoutMs = if (selectedTimeInSeconds > 0) {
            (selectedTimeInSeconds + prepSeconds) * 1000L + WAKELOCK_MARGIN_MS
        } else {
            prepSeconds * 1000L + WAKELOCK_MARGIN_MS
        }
        acquireWakeLock(wakeLockTimeoutMs)

        val initialSessionStartMs = if (isResume) {
            _timerState.value.sessionStartEpochMilli ?: (System.currentTimeMillis() - elapsedOffset * 1000L)
        } else {
            null
        }
        val initialDisplaySeconds = if (currentPlannedSeconds == -1) {
            elapsedOffset
        } else {
            selectedTimeInSeconds.coerceAtLeast(0)
        }
        _timerState.value = StillTimerState(
            isRunning = true,
            isPaused = false,
            isPreparing = prepSeconds > 0,
            isOvertime = false,
            selectedTimeSeconds = currentSelectedSeconds,
            activeSessionPlannedSeconds = currentPlannedSeconds,
            prepLeftSeconds = prepSeconds,
            timeLeftSeconds = initialDisplaySeconds,
            overtimeSeconds = 0,
            sessionStartEpochMilli = initialSessionStartMs
        )

        timerJob = serviceScope.launch {
            try {
                // Preparazione: conteggio nel service (foreground + WakeLock), così non dipende dalla UI
                if (prepSeconds > 0) {
                    val prepEndRealtime = SystemClock.elapsedRealtime() + prepSeconds * 1000L
                    while (isTimerRunning) {
                        val remainingMs = prepEndRealtime - SystemClock.elapsedRealtime()
                        if (remainingMs <= 0L) break
                        val remainingSec = ((remainingMs + 999L) / 1000L).toInt().coerceIn(1, prepSeconds)
                        if (_timerState.value.prepLeftSeconds != remainingSec) {
                            _timerState.value = _timerState.value.copy(
                                isPreparing = true,
                                prepLeftSeconds = remainingSec
                            )
                        }
                        delay(minOf(250L, remainingMs))
                    }
                    if (!isTimerRunning) return@launch
                }
                isInPreparationPhase = false

                val activeSessionStartMs = initialSessionStartMs ?: System.currentTimeMillis()
                _timerState.value = _timerState.value.copy(
                    isRunning = true,
                    isPaused = false,
                    isPreparing = false,
                    prepLeftSeconds = 0,
                    sessionStartEpochMilli = activeSessionStartMs
                )

                if (prepSeconds > 0) {
                    val activeText = if (currentPlannedSeconds == -1) {
                        getString(R.string.notification_no_limit)
                    } else {
                        getString(R.string.notification_meditating)
                    }
                    updateNotification(activeText)
                }

                // Avvia il sottofondo sonoro all'inizio effettivo della meditazione (dopo la preparazione)
                val ambientChoice = currentAmbientSoundscape
                if (ambientChoice != AmbientSoundscape.OFF && isTimerRunning) {
                    ambientPlayer.start(
                        context = this@StillTimerService,
                        soundscape = ambientChoice,
                        volumePercent = currentAmbientVolume
                    )
                }

                // Alla ripresa dopo una pausa non si ripete il gong d'inizio
                if (!isResume) {
                    if (shouldVibrate) {
                        triggerHapticGong(this@StillTimerService, repeatCount = 1)
                    }
                    if (shouldPlaySound) {
                        launchGongWithDucking(gongFreqHz, repeatCount = 1, volumePercent = gongVolume)
                    }
                }

                val startRealtime = SystemClock.elapsedRealtime()
                val intervalSec = if (intervalMins > 0) intervalMins * 60 else 0
                var lastIntervalIndex = if (intervalSec > 0) elapsedOffset / intervalSec else 0
                var lastWakeLockRenewSec = 0
                var isOvertime = false
                var overtimeStartRealtime = 0L

                while (isTimerRunning) {
                    delay(250L)
                    if (!isTimerRunning) break
                    val nowRealtime = SystemClock.elapsedRealtime()
                    val elapsedSeconds = ((nowRealtime - startRealtime) / 1000L).toInt()
                    currentElapsedSeconds = elapsedSeconds
                    val totalElapsedSeconds = elapsedOffset + elapsedSeconds
                    val targetSeconds = currentTargetSeconds

                    // Sessione senza limite o in overtime: rinnovo a finestre di 15 min (mai un lock "infinito")
                    if ((targetSeconds == -1 || isOvertime) &&
                        elapsedSeconds - lastWakeLockRenewSec >= WAKELOCK_RENEW_SEC) {
                        lastWakeLockRenewSec = elapsedSeconds
                        acquireWakeLock(WAKELOCK_MARGIN_MS)
                    }

                    val isReachingEndNow = targetSeconds != -1 && !isOvertime && (targetSeconds - elapsedSeconds) <= 0

                    if (!isOvertime && !isReachingEndNow && intervalSec > 0 && totalElapsedSeconds > 0) {
                        val currentIntervalIndex = totalElapsedSeconds / intervalSec
                        if (currentIntervalIndex > lastIntervalIndex) {
                            lastIntervalIndex = currentIntervalIndex
                            if (shouldVibrate) {
                                triggerHapticGong(this@StillTimerService, repeatCount = 1)
                            }
                            if (shouldPlaySound) {
                                launchGongWithDucking(intervalGongFreqHz, repeatCount = 1, volumePercent = gongVolume)
                            }
                        }
                    }

                    if (isReachingEndNow) {
                        isOvertime = true
                        overtimeStartRealtime = startRealtime + (targetSeconds * 1000L)
                        updateNotification(getString(R.string.notification_overtime))
                        if (shouldVibrate) {
                            triggerHapticGong(this@StillTimerService, repeatCount = 3)
                        }
                        if (shouldPlaySound) {
                            launchGongWithDucking(gongFreqHz, repeatCount = 3, volumePercent = gongVolume)
                        }
                    }

                    val currentState = _timerState.value
                    val nextState = when {
                        targetSeconds == -1 -> currentState.copy(
                            isRunning = true,
                            isPaused = false,
                            isPreparing = false,
                            isOvertime = false,
                            activeSessionPlannedSeconds = -1,
                            timeLeftSeconds = totalElapsedSeconds,
                            overtimeSeconds = 0
                        )
                        isOvertime -> {
                            val extraSec = ((nowRealtime - overtimeStartRealtime) / 1000L).toInt().coerceAtLeast(0)
                            currentState.copy(
                                isRunning = true,
                                isPaused = false,
                                isPreparing = false,
                                isOvertime = true,
                                activeSessionPlannedSeconds = currentPlannedSeconds,
                                timeLeftSeconds = 0,
                                overtimeSeconds = extraSec
                            )
                        }
                        else -> {
                            val remaining = (targetSeconds - elapsedSeconds).coerceAtLeast(0)
                            currentState.copy(
                                isRunning = true,
                                isPaused = false,
                                isPreparing = false,
                                isOvertime = false,
                                activeSessionPlannedSeconds = currentPlannedSeconds,
                                timeLeftSeconds = remaining,
                                overtimeSeconds = 0
                            )
                        }
                    }
                    if (nextState != currentState) {
                        _timerState.value = nextState
                    }
                }
            } finally {
                // Pulizia gestita da pauseTimer / stopTimer
            }
        }
    }

    private fun pauseTimer(): Job? {
        isTimerRunning = false
        isRunning = false
        isInPreparationPhase = false
        timerJob?.cancel()
        timerJob = null
        gongJob?.cancel()
        gongJob = null
        activeDuckingGongs.set(0)
        ambientPlayer.setDucking(false)
        val fadeJob = ambientPlayer.stop()
        cancelGongVibration(this)
        disableDoNotDisturb(this)
        releaseWakeLock()

        val snapshot = _timerState.value
        if (snapshot.sessionStartEpochMilli != null) {
            _timerState.value = snapshot.copy(
                isRunning = false,
                isPaused = true,
                isPreparing = false,
                prepLeftSeconds = 0
            )
        } else {
            resetState(currentSelectedSeconds)
        }
        return fadeJob
    }

    private fun stopTimer(immediateAmbientStop: Boolean = false): Job? {
        isTimerRunning = false
        isRunning = false
        isInPreparationPhase = false
        timerJob?.cancel()
        timerJob = null
        gongJob?.cancel()
        gongJob = null
        activeDuckingGongs.set(0)
        ambientPlayer.setDucking(false)
        val fadeJob = if (immediateAmbientStop) {
            ambientPlayer.stopImmediate()
            null
        } else {
            ambientPlayer.stop()
        }
        cancelGongVibration(this)
        disableDoNotDisturb(this)
        releaseWakeLock()
        resetState(currentSelectedSeconds)
        return fadeJob
    }

    @Synchronized
    private fun acquireWakeLock(timeoutMs: Long) {
        if (!isTimerRunning) return // il service è già stato fermato: non creare lock orfani
        wakeLock?.let { if (it.isHeld) it.release() }
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Still::ServiceWakeLock").apply {
            acquire(timeoutMs)
        }
    }

    @Synchronized
    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) wakeLock?.release()
        wakeLock = null
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAfterFadeJob?.cancel()
        stopAfterFadeJob = null
        if (!isStoppingForPause) {
            stopTimer(immediateAmbientStop = true)
        } else {
            ambientPlayer.stopImmediate()
        }
        serviceJob.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
