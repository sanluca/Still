package com.sanluca.still.wear

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import androidx.wear.tiles.TileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val LOG_TAG = "StillWear"

private const val PERMISSION_READ_HEART_RATE = "android.permission.health.READ_HEART_RATE"

internal fun requiredHeartRatePermission(): String =
    if (Build.VERSION.SDK_INT >= 36) PERMISSION_READ_HEART_RATE else Manifest.permission.BODY_SENSORS

internal fun hasHeartRatePermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, requiredHeartRatePermission()) == PackageManager.PERMISSION_GRANTED

data class HeartRateSummary(
    val startBpm: Int? = null,
    val endBpm: Int? = null,
    val avgBpm: Int? = null
)

data class WearTimerSnapshot(
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

    @Volatile private var isTimerRunning = false
    @Volatile private var isCurrentlyPreparing = false
    private var wakeLock: PowerManager.WakeLock? = null
    private var timerJob: Job? = null
    private var gongJob: Job? = null
    private var hrJob: Job? = null
    private val hrSensorLock = Any()
    private var activeHrSensorManager: SensorManager? = null
    private var activeHrListener: SensorEventListener? = null

    companion object {
        @Volatile var isServiceRunning = false
        private val _timerState = MutableStateFlow(WearTimerSnapshot())
        val timerState: StateFlow<WearTimerSnapshot> = _timerState.asStateFlow()
        private val hrLock = Any()
        private var hrStartBpm: Int? = null
        private var hrEndBpm: Int? = null
        private var hrSum: Long = 0L
        private var hrCount: Int = 0

        fun getHeartRateSummary(): HeartRateSummary = synchronized(hrLock) {
            val avg = if (hrCount > 0) (hrSum.toDouble() / hrCount.toDouble()).roundToInt() else null
            HeartRateSummary(startBpm = hrStartBpm, endBpm = hrEndBpm, avgBpm = avg)
        }

        fun clearHeartRateSamples() = synchronized(hrLock) {
            hrStartBpm = null
            hrEndBpm = null
            hrSum = 0L
            hrCount = 0
        }

        private fun recordHeartRateSample(bpm: Int) = synchronized(hrLock) {
            if (hrStartBpm == null) {
                hrStartBpm = bpm
            }
            hrEndBpm = bpm
            hrSum += bpm.toLong()
            hrCount += 1
        }
        private const val WAKELOCK_MARGIN_MS = 15 * 60 * 1000L
        private const val WAKELOCK_RENEW_SEC = 10 * 60

        private const val CHANNEL_ID_TIMER = "still_wear_timer"
        private const val CHANNEL_ID_ALARM = "still_wear_alarm"
        private const val NOTIFICATION_ID_TIMER = 1
        private const val NOTIFICATION_ID_ALARM = 2

        const val ACTION_START = "START"
        const val ACTION_STOP = "STOP"
        const val ACTION_START_HR_SAMPLING = "START_HR_SAMPLING"

        const val EXTRA_FREQ = "FREQ"
        const val EXTRA_TIME = "TIME"
        const val EXTRA_PREP = "PREP"
        const val EXTRA_ALERT_MODE = "ALERT_MODE"
        const val EXTRA_VOLUME = "VOLUME"
        const val EXTRA_IS_OVERTIME = "IS_OVERTIME_START"
        const val EXTRA_OVERTIME_SECONDS = "OVERTIME_SECONDS_START"
        const val EXTRA_INITIAL_TIME = "INITIAL_TIME"

        const val ACTION_TIMER_TICK = "com.sanluca.still.wear.TIMER_TICK"
        const val EXTRA_IS_PREPARING = "IS_PREPARING"
        const val EXTRA_PREP_LEFT = "PREP_LEFT"
        const val EXTRA_TIME_LEFT = "TIME_LEFT"
        const val EXTRA_IS_OVERTIME_STATE = "IS_OVERTIME"
        const val EXTRA_OVERTIME_SECONDS_STATE = "OVERTIME_SECONDS"
        const val EXTRA_IS_RUNNING = "IS_RUNNING"
        const val EXTRA_INITIAL_TIME_STATE = "INITIAL_TIME_STATE"
    }

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(CHANNEL_ID_TIMER, getString(R.string.app_name), NotificationManager.IMPORTANCE_LOW)
        val alarmChannel = NotificationChannel(CHANNEL_ID_ALARM, getString(R.string.app_name), NotificationManager.IMPORTANCE_HIGH).apply {
            // Il suono e la vibrazione sono gestiti dal gong del timer
            setSound(null, null)
            enableVibration(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
        manager.createNotificationChannel(alarmChannel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START) {
            val freq = intent.getDoubleExtra(EXTRA_FREQ, 432.0)
            val selectedTime = intent.getIntExtra(EXTRA_TIME, 300) // This is current time left when resuming
            val initialTime = intent.getIntExtra(EXTRA_INITIAL_TIME, selectedTime)
            val prepTime = intent.getIntExtra(EXTRA_PREP, 0)
            val alertMode = intent.getIntExtra(EXTRA_ALERT_MODE, ALERT_MODE_BOTH)
            val gongVolume = intent.getIntExtra(EXTRA_VOLUME, 100)
            val isOvertimeStart = intent.getBooleanExtra(EXTRA_IS_OVERTIME, false)
            val overtimeSecsStart = intent.getIntExtra(EXTRA_OVERTIME_SECONDS, 0)

            val notificationIntent = Intent(this, MainActivity::class.java).apply {
                setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            }
            val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID_TIMER)
                .setContentTitle(getString(R.string.status_timer))
                .setContentText(getString(R.string.status_still))
                .setSmallIcon(R.drawable.ic_still_complication)
                .setContentIntent(pendingIntent)
                .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
                .setOngoing(true)

            val initialOngoingStatus = when {
                prepTime > 0 -> buildPrepareOngoingStatus(prepTime)
                isOvertimeStart -> buildOvertimeOngoingStatus(overtimeSecsStart)
                initialTime == -1 -> buildUnlimitedOngoingStatus(selectedTime.coerceAtLeast(0))
                else -> buildCountdownOngoingStatus(selectedTime, initialTime)
            }

            // Ongoing Activity dinamica: chip attivo sul quadrante e voce nei recenti per rientrare nel timer
            try {
                OngoingActivity.Builder(applicationContext, NOTIFICATION_ID_TIMER, notificationBuilder)
                    .setStaticIcon(R.drawable.ic_still_complication)
                    .setTouchIntent(pendingIntent)
                    .setStatus(initialOngoingStatus)
                    .build()
                    .apply(applicationContext)
                Log.d(
                    LOG_TAG,
                    "OngoingActivity creata: prep=$prepTime, overtime=$isOvertimeStart, initialTime=$initialTime, selectedTime=$selectedTime"
                )
            } catch (e: Exception) {
                Log.e(LOG_TAG, "Errore creazione OngoingActivity", e)
            }

            val hrGranted = hasHeartRatePermission(this)
            val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                if (hrGranted) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
                } else {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                }
            } else {
                0
            }
            val notification = notificationBuilder.build()
            Log.d(
                LOG_TAG,
                "Avvio FGS StillTimerService: hrGranted=$hrGranted, fgsType=$fgsType"
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                try {
                    startForeground(NOTIFICATION_ID_TIMER, notification, fgsType)
                } catch (e: SecurityException) {
                    Log.w(
                        LOG_TAG,
                        "SecurityException su startForeground con fgsType=$fgsType, fallback a SPECIAL_USE",
                        e
                    )
                    startForeground(
                        NOTIFICATION_ID_TIMER,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                    )
                }
            } else {
                startForeground(NOTIFICATION_ID_TIMER, notification)
            }

            startTimer(freq, selectedTime, prepTime, isOvertimeStart, overtimeSecsStart, initialTime, alertMode, gongVolume)
        } else if (intent?.action == ACTION_START_HR_SAMPLING) {
            if (isTimerRunning && !isCurrentlyPreparing) {
                startHeartRateSamplingIfPermitted()
            }
        } else if (intent?.action == ACTION_STOP) {
            Log.d(LOG_TAG, "Ricevuto ACTION_STOP su StillTimerService")
            stopTimer()
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    private fun buildPrepareOngoingStatus(prepSeconds: Int): Status {
        val endRealtime = SystemClock.elapsedRealtime() + prepSeconds.coerceAtLeast(0) * 1000L
        return Status.Builder()
            .addTemplate(getString(R.string.ongoing_status_prepare_template))
            .addPart("timer", Status.TimerPart(endRealtime, -1L, prepSeconds.coerceAtLeast(1) * 1000L))
            .build()
    }

    private fun buildCountdownOngoingStatus(remainingSeconds: Int, totalSeconds: Int): Status {
        val endRealtime = SystemClock.elapsedRealtime() + remainingSeconds.coerceAtLeast(0) * 1000L
        val totalMs = totalSeconds.coerceAtLeast(remainingSeconds).coerceAtLeast(1) * 1000L
        return Status.Builder()
            .addTemplate(getString(R.string.ongoing_status_timer_template))
            .addPart("timer", Status.TimerPart(endRealtime, -1L, totalMs))
            .build()
    }

    private fun buildUnlimitedOngoingStatus(elapsedSeconds: Int): Status {
        val startRealtime = SystemClock.elapsedRealtime() - elapsedSeconds.coerceAtLeast(0) * 1000L
        return Status.Builder()
            .addTemplate(getString(R.string.ongoing_status_stopwatch_template))
            .addPart("stopwatch", Status.StopwatchPart(startRealtime))
            .build()
    }

    private fun buildOvertimeOngoingStatus(overtimeSeconds: Int): Status {
        val startRealtime = SystemClock.elapsedRealtime() - overtimeSeconds.coerceAtLeast(0) * 1000L
        return Status.Builder()
            .addTemplate(getString(R.string.ongoing_status_extra_template))
            .addPart("stopwatch", Status.StopwatchPart(startRealtime))
            .build()
    }

    private fun updateOngoingActivityStatus(status: Status, phaseLabel: String) {
        try {
            val ongoing = OngoingActivity.recoverOngoingActivity(applicationContext)
            if (ongoing != null) {
                ongoing.update(applicationContext, status)
                Log.d(LOG_TAG, "OngoingActivity aggiornata: fase=$phaseLabel")
            } else {
                Log.w(LOG_TAG, "OngoingActivity non recuperata per aggiornamento fase=$phaseLabel")
            }
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Errore aggiornamento OngoingActivity fase=$phaseLabel", e)
        }
    }

    private fun requestTileUpdate(reason: String) {
        try {
            TileService.getUpdater(applicationContext).requestUpdate(StillTileService::class.java)
            Log.d(LOG_TAG, "Richiesto aggiornamento Tile: reason=$reason")
        } catch (e: Exception) {
            Log.w(LOG_TAG, "Impossibile richiedere aggiornamento Tile: reason=$reason", e)
        }
    }

    private fun broadcastState(isPreparing: Boolean, prepLeft: Int, timeLeft: Int, isOvertime: Boolean, overtimeSec: Int, initialTime: Int) {
        _timerState.value = WearTimerSnapshot(
            isRunning = isTimerRunning,
            isPreparing = isPreparing,
            prepLeft = prepLeft,
            timeLeft = timeLeft,
            isOvertime = isOvertime,
            overtimeSeconds = overtimeSec,
            initialTime = initialTime
        )
        val intent = Intent(ACTION_TIMER_TICK).apply {
            setPackage(packageName)
            putExtra(EXTRA_IS_PREPARING, isPreparing)
            putExtra(EXTRA_PREP_LEFT, prepLeft)
            putExtra(EXTRA_TIME_LEFT, timeLeft)
            putExtra(EXTRA_IS_OVERTIME_STATE, isOvertime)
            putExtra(EXTRA_OVERTIME_SECONDS_STATE, overtimeSec)
            putExtra(EXTRA_IS_RUNNING, isTimerRunning)
            putExtra(EXTRA_INITIAL_TIME_STATE, initialTime)
        }
        sendBroadcast(intent)
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
        Log.d(
            LOG_TAG,
            "Transizione stato -> START: prep=$prepTimeSeconds, baseTimeLeft=$baseTimeLeft, initial=$initialSelectedTime, overtimeStart=$isOvertimeStart"
        )
        requestTileUpdate("start")

        timerJob = serviceScope.launch {
            try {
                // 1. CONTO ALLA ROVESCIA DI PREPARAZIONE
                var prepLeft = prepTimeSeconds
                if (prepLeft > 0) {
                    Log.d(LOG_TAG, "Transizione stato -> PREPARAZIONE (${prepLeft}s)")
                    while (prepLeft > 0 && isTimerRunning) {
                        broadcastState(true, prepLeft, baseTimeLeft, false, 0, initialSelectedTime)
                        delay(1000L)
                        prepLeft -= 1
                    }
                }
                isCurrentlyPreparing = false

                if (!isTimerRunning) return@launch

                if (prepTimeSeconds > 0) {
                    Log.d(LOG_TAG, "Transizione stato -> ATTIVO (fine preparazione)")
                    val activeStatus = if (initialSelectedTime == -1) {
                        buildUnlimitedOngoingStatus(baseTimeLeft)
                    } else {
                        buildCountdownOngoingStatus(baseTimeLeft, initialSelectedTime)
                    }
                    updateOngoingActivityStatus(activeStatus, "active")
                    broadcastState(false, 0, baseTimeLeft, isOvertimeStart, overtimeSecsStart, initialSelectedTime)
                    requestTileUpdate("phase_active")
                }

                // 2. INIZIO EFFETTIVO MEDITAZIONE CON GONG AUDIO E/O VIBRAZIONE
                if (prepTimeSeconds >= 0) {
                    if (shouldVibrate) {
                        triggerHapticGong(this@StillTimerService, repeatCount = 1)
                    }
                    if (shouldPlaySound) {
                        gongJob?.cancel()
                        gongJob = serviceScope.launch(Dispatchers.Default) {
                            playGongSound(this@StillTimerService, gongFreqHz, repeatCount = 1, volumePercent = gongVolume)
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
                    broadcastState(false, 0, timeLeft, isOvertime, overtimeSec, initialSelectedTime)
                    delay(500L)
                    val elapsedSeconds = ((SystemClock.elapsedRealtime() - startRealtime) / 1000L).toInt()

                    if ((initialSelectedTime == -1 || isOvertime) &&
                        elapsedSeconds - lastWakeLockRenewSec >= WAKELOCK_RENEW_SEC) {
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
                            Log.d(LOG_TAG, "Transizione stato -> OVERTIME")
                            updateOngoingActivityStatus(buildOvertimeOngoingStatus(0), "overtime")
                            broadcastState(false, 0, 0, true, 0, initialSelectedTime)
                            requestTileUpdate("phase_overtime")

                            if (shouldVibrate) {
                                triggerHapticGong(this@StillTimerService, repeatCount = 3)
                            }
                            if (shouldPlaySound) {
                                gongJob?.cancel()
                                gongJob = serviceScope.launch(Dispatchers.Default) {
                                    playGongSound(this@StillTimerService, gongFreqHz, repeatCount = 3, volumePercent = gongVolume)
                                }
                            }

                            // Notifica di fine sessione con tap diretto sulla schermata di salvataggio (senza full-screen intent)
                            try {
                                val openAppIntent = Intent(this@StillTimerService, MainActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                                }
                                val contentPendingIntent = PendingIntent.getActivity(
                                    this@StillTimerService,
                                    1,
                                    openAppIntent,
                                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                                )
                                val alarmNotification = Notification.Builder(this@StillTimerService, CHANNEL_ID_ALARM)
                                    .setContentTitle(getString(R.string.status_timer))
                                    .setContentText(getString(R.string.status_extra))
                                    .setSmallIcon(R.drawable.ic_still_complication)
                                    .setCategory(Notification.CATEGORY_ALARM)
                                    .setContentIntent(contentPendingIntent)
                                    .setAutoCancel(true)
                                    .build()
                                getSystemService(NotificationManager::class.java)
                                    .notify(NOTIFICATION_ID_ALARM, alarmNotification)
                            } catch (e: Exception) {
                                Log.e(LOG_TAG, "Errore invio notifica fine sessione", e)
                            }
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
        if (!isTimerRunning || isCurrentlyPreparing || hrJob?.isActive == true) return
        if (!hasHeartRatePermission(this)) {
            Log.d(LOG_TAG, "Campionamento HR saltato: permesso sensore non concesso")
            return
        }
        val sensorManager = getSystemService(SENSOR_SERVICE) as? SensorManager ?: return
        val hrSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE) ?: return
        Log.d(LOG_TAG, "Avvio campionamento HR silenzioso in StillTimerService")

        hrJob = serviceScope.launch(Dispatchers.Default) {
            var burstIndex = 0
            while (isTimerRunning) {
                val targetSamples = if (burstIndex == 0) 3 else 2
                var collectedInBurst = 0
                val listener = object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent?) {
                        if (event == null || event.sensor?.type != Sensor.TYPE_HEART_RATE) return
                        // Accetta anche SENSOR_STATUS_ACCURACY_LOW; scarta solo nessun contatto o inaffidabile
                        if (event.accuracy == SensorManager.SENSOR_STATUS_NO_CONTACT ||
                            event.accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE
                        ) {
                            return
                        }
                        val bpm = event.values.firstOrNull()?.roundToInt() ?: return
                        if (bpm in 30..220) {
                            recordHeartRateSample(bpm)
                            collectedInBurst += 1
                        }
                    }

                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
                }

                try {
                    val registered = synchronized(hrSensorLock) {
                        if (!isTimerRunning) {
                            false
                        } else {
                            activeHrSensorManager = sensorManager
                            activeHrListener = listener
                            try {
                                sensorManager.registerListener(listener, hrSensor, SensorManager.SENSOR_DELAY_UI)
                            } catch (e: SecurityException) {
                                Log.w(LOG_TAG, "SecurityException su registerListener TYPE_HEART_RATE", e)
                                false
                            }
                        }
                    }
                    if (!registered) {
                        unregisterActiveHrListener()
                        break
                    }
                    val maxWaitSteps = 40 // ~20s massimo per finestra di campionamento
                    var step = 0
                    while (isTimerRunning && step < maxWaitSteps && collectedInBurst < targetSamples) {
                        delay(500L)
                        step++
                    }
                } catch (e: SecurityException) {
                    Log.w(LOG_TAG, "SecurityException durante campionamento HR", e)
                    break
                } finally {
                    synchronized(hrSensorLock) {
                        if (activeHrListener === listener) {
                            activeHrListener = null
                            activeHrSensorManager = null
                        }
                    }
                    try {
                        sensorManager.unregisterListener(listener)
                    } catch (_: Exception) {
                    }
                }

                burstIndex++
                delay(45_000L)
            }
        }
    }

    private fun unregisterActiveHrListener() {
        synchronized(hrSensorLock) {
            val listener = activeHrListener
            val manager = activeHrSensorManager
            activeHrListener = null
            activeHrSensorManager = null
            if (listener != null && manager != null) {
                try {
                    manager.unregisterListener(listener)
                } catch (_: Exception) {
                }
            }
        }
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
        hrJob?.cancel()
        hrJob = null
        unregisterActiveHrListener()
        cancelGongVibration(this)
        getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID_ALARM)
        releaseWakeLock()
        if (wasRunning) {
            Log.d(LOG_TAG, "Transizione stato -> STOP (FGS fermato)")
            requestTileUpdate("stop")
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
