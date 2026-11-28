package com.sanluca.still.wear

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.wear.tiles.TileService
import com.sanluca.still.core.ALERT_MODE_BOTH
import com.sanluca.still.core.ALERT_MODE_SOUND
import com.sanluca.still.core.ALERT_MODE_VIBRATION
import com.sanluca.still.core.cancelGongVibration
import com.sanluca.still.core.playGongSound
import com.sanluca.still.core.triggerHapticGong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class WearTimerSnapshot(
    val isRunning: Boolean = false,
    val isPreparing: Boolean = false,
    val prepLeft: Int = 0,
    val timeLeft: Int = 300,
    val isOvertime: Boolean = false,
    val overtimeSeconds: Int = 0,
    val initialTime: Int = 300
)

class StillTimerService : Service() {
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)
    private val heartRateTracker by lazy { WearHeartRateTracker(this, serviceScope) }
    private val ongoingActivityHelper by lazy { WearOngoingActivityHelper(this) }

    @Volatile private var isTimerRunning = false
    @Volatile private var isCurrentlyPreparing = false
    private var wakeLock: PowerManager.WakeLock? = null
    private var timerJob: Job? = null
    private var gongJob: Job? = null

    companion object {
        @Volatile internal var isServiceRunning = false
        private val _timerState = MutableStateFlow(WearTimerSnapshot())
        internal val timerState: StateFlow<WearTimerSnapshot> = _timerState.asStateFlow()

        internal fun getHeartRateSummary(): HeartRateSummary = WearHeartRateStore.getSummary()

        internal fun clearHeartRateSamples() = WearHeartRateStore.clear()

        internal fun recordHeartRateSample(bpm: Int) = WearHeartRateStore.recordSample(bpm)

        private const val WAKELOCK_MARGIN_MS = 15 * 60 * 1000L
        private const val WAKELOCK_RENEW_SEC = 10 * 60

        internal const val ACTION_START = "START"
        internal const val ACTION_STOP = "STOP"

        internal const val EXTRA_FREQ = "FREQ"
        internal const val EXTRA_TIME = "TIME"
        internal const val EXTRA_PREP = "PREP"
        internal const val EXTRA_ALERT_MODE = "ALERT_MODE"
        internal const val EXTRA_VOLUME = "VOLUME"
        internal const val EXTRA_IS_OVERTIME = "IS_OVERTIME_START"
        internal const val EXTRA_OVERTIME_SECONDS = "OVERTIME_SECONDS_START"
        internal const val EXTRA_INITIAL_TIME = "INITIAL_TIME"
    }

    override fun onCreate() {
        super.onCreate()
        ongoingActivityHelper.createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START) {
            val hrEnabledAndGranted = shouldTrackHeartRate(this)
            val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                if (hrEnabledAndGranted) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
                } else {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                }
            } else {
                0
            }

            if (isTimerRunning) {
                val existingNotification = ongoingActivityHelper.buildExistingNotificationOrNull()
                if (existingNotification != null) {
                    startForegroundCompat(existingNotification, fgsType)
                    return START_NOT_STICKY
                }
            }

            val freq = intent.getDoubleExtra(EXTRA_FREQ, 432.0)
            val selectedTime = intent.getIntExtra(EXTRA_TIME, 300) // This is current time left when resuming
            val initialTime = intent.getIntExtra(EXTRA_INITIAL_TIME, selectedTime)
            val prepTime = intent.getIntExtra(EXTRA_PREP, 0)
            val alertMode = intent.getIntExtra(EXTRA_ALERT_MODE, ALERT_MODE_BOTH)
            val gongVolume = intent.getIntExtra(EXTRA_VOLUME, 100)
            val isOvertimeStart = intent.getBooleanExtra(EXTRA_IS_OVERTIME, false)
            val overtimeSecsStart = intent.getIntExtra(EXTRA_OVERTIME_SECONDS, 0)

            val notification = ongoingActivityHelper.buildInitialForegroundNotification(
                prepTime = prepTime,
                isOvertimeStart = isOvertimeStart,
                overtimeSecsStart = overtimeSecsStart,
                selectedTime = selectedTime,
                initialTime = initialTime
            )

            if (!hrEnabledAndGranted) {
                clearHeartRateSamples()
            }

            startForegroundCompat(notification, fgsType)
            startTimer(freq, selectedTime, prepTime, isOvertimeStart, overtimeSecsStart, initialTime, alertMode, gongVolume)
        } else if (intent?.action == ACTION_STOP) {
            stopTimer()
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    private fun startForegroundCompat(notification: Notification, fgsType: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                startForeground(WearOngoingActivityHelper.NOTIFICATION_ID_TIMER, notification, fgsType)
            } catch (_: SecurityException) {
                startForeground(
                    WearOngoingActivityHelper.NOTIFICATION_ID_TIMER,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            }
        } else {
            startForeground(WearOngoingActivityHelper.NOTIFICATION_ID_TIMER, notification)
        }
    }

    private fun requestTileUpdate() {
        try {
            TileService.getUpdater(applicationContext).requestUpdate(StillTileService::class.java)
        } catch (_: Exception) {
        }
    }

    private fun publishState(
        isPreparing: Boolean,
        prepLeft: Int,
        timeLeft: Int,
        isOvertime: Boolean,
        overtimeSec: Int,
        initialTime: Int
    ) {
        _timerState.value = WearTimerSnapshot(
            isRunning = isTimerRunning,
            isPreparing = isPreparing,
            prepLeft = prepLeft,
            timeLeft = timeLeft,
            isOvertime = isOvertime,
            overtimeSeconds = overtimeSec,
            initialTime = initialTime
        )
    }

    private fun startTimer(
        gongFreqHz: Double,
        startingTimeLeft: Int,
        prepTimeSeconds: Int,
        isOvertimeStart: Boolean,
        overtimeSecsStart: Int,
        initialSelectedTime: Int,
        alertMode: Int,
        gongVolume: Int
    ) {
        if (isTimerRunning) return
        isTimerRunning = true
        isServiceRunning = true
        isCurrentlyPreparing = prepTimeSeconds > 0
        if (prepTimeSeconds >= 0) {
            clearHeartRateSamples()
        }

        val hasAudioHardware = packageManager.hasSystemFeature(PackageManager.FEATURE_AUDIO_OUTPUT)
        val shouldPlaySound = (alertMode == ALERT_MODE_BOTH || alertMode == ALERT_MODE_SOUND) && hasAudioHardware
        val shouldVibrate = (alertMode == ALERT_MODE_BOTH || alertMode == ALERT_MODE_VIBRATION) || !hasAudioHardware

        val wakeLockTimeoutMs = if (initialSelectedTime > 0 && !isOvertimeStart) {
            (startingTimeLeft + prepTimeSeconds.coerceAtLeast(0)) * 1000L + WAKELOCK_MARGIN_MS
        } else {
            WAKELOCK_MARGIN_MS
        }
        acquireWakeLock(wakeLockTimeoutMs)

        // In modalità senza limiti (-1) il conteggio parte da 0 (il valore -1 è solo un marcatore)
        val baseTimeLeft = if (initialSelectedTime == -1) startingTimeLeft.coerceAtLeast(0) else startingTimeLeft

        _timerState.value = WearTimerSnapshot(
            isRunning = true,
            isPreparing = prepTimeSeconds > 0,
            prepLeft = prepTimeSeconds.coerceAtLeast(0),
            timeLeft = baseTimeLeft,
            isOvertime = isOvertimeStart,
            overtimeSeconds = overtimeSecsStart,
            initialTime = initialSelectedTime
        )
        requestTileUpdate()

        timerJob = serviceScope.launch {
            try {
                // 1. CONTO ALLA ROVESCIA DI PREPARAZIONE
                var prepLeft = prepTimeSeconds
                if (prepLeft > 0) {
                    while (prepLeft > 0 && isTimerRunning) {
                        publishState(true, prepLeft, baseTimeLeft, false, 0, initialSelectedTime)
                        delay(1000L)
                        prepLeft -= 1
                    }
                }
                isCurrentlyPreparing = false

                if (!isTimerRunning) return@launch

                if (prepTimeSeconds > 0) {
                    val activeStatus = if (initialSelectedTime == -1) {
                        ongoingActivityHelper.buildUnlimitedOngoingStatus(baseTimeLeft)
                    } else {
                        ongoingActivityHelper.buildCountdownOngoingStatus(baseTimeLeft, initialSelectedTime)
                    }
                    ongoingActivityHelper.updateOngoingActivityStatus(
                        isTimerRunning = isTimerRunning,
                        status = activeStatus,
                        contentText = getString(R.string.status_still)
                    )
                    publishState(false, 0, baseTimeLeft, isOvertimeStart, overtimeSecsStart, initialSelectedTime)
                    requestTileUpdate()
                }

                // 2. INIZIO EFFETTIVO MEDITAZIONE CON GONG AUDIO E/O VIBRAZIONE
                if (prepTimeSeconds >= 0) {
                    if (shouldVibrate) {
                        triggerHapticGong(this@StillTimerService, repeatCount = 1)
                    }
                    if (shouldPlaySound) {
                        gongJob?.cancel()
                        gongJob = serviceScope.launch(Dispatchers.Default) {
                            playGongSound(
                                context = this@StillTimerService,
                                frequencyHz = gongFreqHz,
                                repeatCount = 1,
                                volumePercent = gongVolume,
                                requestAudioFocus = false,
                                usage = AudioAttributes.USAGE_ALARM,
                                contentType = AudioAttributes.CONTENT_TYPE_SONIFICATION
                            )
                        }
                    }
                }

                // 2a. CAMPIONAMENTO SILENZIOSO DEL BATTITO CARDIACO (resiliente al lifecycle della UI)
                startHeartRateSamplingIfPermitted()

                val startRealtime = SystemClock.elapsedRealtime()
                var timeLeft = baseTimeLeft
                var isOvertime = isOvertimeStart
                var overtimeSec = overtimeSecsStart
                var lastWakeLockRenewSec = 0

                while (isTimerRunning) {
                    publishState(false, 0, timeLeft, isOvertime, overtimeSec, initialSelectedTime)
                    delay(500L)
                    val elapsedSeconds = ((SystemClock.elapsedRealtime() - startRealtime) / 1000L).toInt()

                    if ((initialSelectedTime == -1 || isOvertime) &&
                        elapsedSeconds - lastWakeLockRenewSec >= WAKELOCK_RENEW_SEC
                    ) {
                        lastWakeLockRenewSec = elapsedSeconds
                        acquireWakeLock(WAKELOCK_MARGIN_MS)
                    }

                    if (initialSelectedTime == -1) {
                        timeLeft = baseTimeLeft + elapsedSeconds
                    } else if (!isOvertime) {
                        val remaining = startingTimeLeft - elapsedSeconds
                        if (remaining > 0) {
                            timeLeft = remaining
                        } else {
                            timeLeft = 0
                            isOvertime = true
                            overtimeSec = 0
                            ongoingActivityHelper.updateOngoingActivityStatus(
                                isTimerRunning = isTimerRunning,
                                status = ongoingActivityHelper.buildOvertimeOngoingStatus(0),
                                contentText = getString(R.string.status_extra)
                            )
                            publishState(false, 0, 0, true, 0, initialSelectedTime)
                            requestTileUpdate()

                            if (shouldVibrate) {
                                triggerHapticGong(this@StillTimerService, repeatCount = 3)
                            }
                            if (shouldPlaySound) {
                                gongJob?.cancel()
                                gongJob = serviceScope.launch(Dispatchers.Default) {
                                    playGongSound(
                                        context = this@StillTimerService,
                                        frequencyHz = gongFreqHz,
                                        repeatCount = 3,
                                        volumePercent = gongVolume,
                                        requestAudioFocus = false,
                                        usage = AudioAttributes.USAGE_ALARM,
                                        contentType = AudioAttributes.CONTENT_TYPE_SONIFICATION
                                    )
                                }
                            }

                            ongoingActivityHelper.notifySessionEndAlarm()
                        }
                    } else {
                        // overtime
                        val overtimeStartRealtime = startRealtime + (if (isOvertimeStart) 0 else (startingTimeLeft * 1000L))
                        val extraSec = ((SystemClock.elapsedRealtime() - overtimeStartRealtime) / 1000L).toInt() + overtimeSecsStart
                        overtimeSec = extraSec.coerceAtLeast(0)
                    }
                }
            } finally {
                // We don't reset state here anymore when it stops, we leave the UI to hold the last state
            }
        }
    }

    private fun startHeartRateSamplingIfPermitted() {
        heartRateTracker.startSamplingIfPermitted { isTimerRunning && !isCurrentlyPreparing }
    }

    private fun stopTimer() {
        val wasRunning = isTimerRunning || isServiceRunning
        isTimerRunning = false
        isServiceRunning = false
        isCurrentlyPreparing = false
        _timerState.value = _timerState.value.copy(
            isRunning = false,
            isPreparing = false
        )
        timerJob?.cancel()
        timerJob = null
        gongJob?.cancel()
        gongJob = null
        heartRateTracker.stop()
        ongoingActivityHelper.clearState()
        cancelGongVibration(this)
        ongoingActivityHelper.cancelAlarmNotification()
        stopForeground(STOP_FOREGROUND_REMOVE)
        ongoingActivityHelper.cancelTimerNotification()
        releaseWakeLock()
        if (wasRunning) {
            requestTileUpdate()
        }
    }

    @Synchronized
    private fun acquireWakeLock(timeoutMs: Long) {
        if (!isTimerRunning) return
        wakeLock?.let { if (it.isHeld) it.release() }
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "StillWear::ServiceWakeLock").apply {
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
        stopTimer()
        serviceJob.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
