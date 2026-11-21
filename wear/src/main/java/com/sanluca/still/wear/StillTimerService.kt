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
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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

class StillTimerService : Service() {
    private val serviceJob = Job()
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
            // Il suono e la vibrazione sono gestiti dal gong del timer: il canale serve solo al full-screen intent
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

            // Ongoing Activity: chip attivo sul quadrante e voce nei recenti per rientrare nel timer
            OngoingActivity.Builder(applicationContext, NOTIFICATION_ID_TIMER, notificationBuilder)
                .setStaticIcon(R.drawable.ic_still_complication)
                .setTouchIntent(pendingIntent)
                .setStatus(Status.Builder().addTemplate(getString(R.string.status_timer)).build())
                .build()
                .apply(applicationContext)

            startForeground(NOTIFICATION_ID_TIMER, notificationBuilder.build())
            startTimer(freq, selectedTime, prepTime, isOvertimeStart, overtimeSecsStart, initialTime, alertMode, gongVolume)
        } else if (intent?.action == ACTION_START_HR_SAMPLING) {
            if (isTimerRunning && !isCurrentlyPreparing) {
                startHeartRateSamplingIfPermitted()
            }
        } else if (intent?.action == ACTION_STOP) {
            stopTimer()
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    private fun broadcastState(isPreparing: Boolean, prepLeft: Int, timeLeft: Int, isOvertime: Boolean, overtimeSec: Int, initialTime: Int) {
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

        timerJob = serviceScope.launch {
            try {
                // 1. CONTO ALLA ROVESCIA DI PREPARAZIONE
                var prepLeft = prepTimeSeconds
                if (prepLeft > 0) {
                    while (prepLeft > 0 && isTimerRunning) {
                        broadcastState(true, prepLeft, baseTimeLeft, false, 0, initialSelectedTime)
                        delay(1000L)
                        prepLeft -= 1
                    }
                }
                isCurrentlyPreparing = false

                if (!isTimerRunning) return@launch

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

                            if (shouldVibrate) {
                                triggerHapticGong(this@StillTimerService, repeatCount = 3)
                            }
                            if (shouldPlaySound) {
                                gongJob?.cancel()
                                gongJob = serviceScope.launch(Dispatchers.Default) {
                                    playGongSound(this@StillTimerService, gongFreqHz, repeatCount = 3, volumePercent = gongVolume)
                                }
                            }

                            // Risveglia e riporta in primo piano la schermata con i pulsanti per salvare
                            // (full-screen intent: l'avvio diretto di Activity dal background è bloccato dal sistema)
                            try {
                                val openAppIntent = Intent(this@StillTimerService, MainActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                                }
                                val fullScreenPendingIntent = PendingIntent.getActivity(
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
                                    .setContentIntent(fullScreenPendingIntent)
                                    .setFullScreenIntent(fullScreenPendingIntent, true)
                                    .setAutoCancel(true)
                                    .build()
                                getSystemService(NotificationManager::class.java)
                                    .notify(NOTIFICATION_ID_ALARM, alarmNotification)
                            } catch (e: Exception) {
                                e.printStackTrace()
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
            return
        }
        val sensorManager = getSystemService(SENSOR_SERVICE) as? SensorManager ?: return
        val hrSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE) ?: return

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
                            sensorManager.registerListener(listener, hrSensor, SensorManager.SENSOR_DELAY_UI)
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
        isTimerRunning = false
        isServiceRunning = false
        isCurrentlyPreparing = false
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
