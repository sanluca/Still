package com.sanluca.still.wear

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class StillTimerService : Service() {
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)

    private var isTimerRunning = false
    private var wakeLock: PowerManager.WakeLock? = null
    private var timerJob: Job? = null

    companion object {
        var isServiceRunning = false
        private const val WAKELOCK_MARGIN_MS = 15 * 60 * 1000L
        private const val WAKELOCK_RENEW_SEC = 10 * 60

        const val ACTION_START = "START"
        const val ACTION_STOP = "STOP"

        const val EXTRA_FREQ = "FREQ"
        const val EXTRA_TIME = "TIME"
        const val EXTRA_PREP = "PREP"
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
        val channel = NotificationChannel("still_wear_timer", getString(R.string.app_name), NotificationManager.IMPORTANCE_LOW)
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START) {
            val freq = intent.getDoubleExtra(EXTRA_FREQ, 432.0)
            val selectedTime = intent.getIntExtra(EXTRA_TIME, 300) // This is current time left when resuming
            val initialTime = intent.getIntExtra(EXTRA_INITIAL_TIME, selectedTime)
            val prepTime = intent.getIntExtra(EXTRA_PREP, 0)
            val isOvertimeStart = intent.getBooleanExtra(EXTRA_IS_OVERTIME, false)
            val overtimeSecsStart = intent.getIntExtra(EXTRA_OVERTIME_SECONDS, 0)

            val notificationIntent = Intent(this, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)
            val notification = Notification.Builder(this, "still_wear_timer")
                .setContentTitle(getString(R.string.status_timer))
                .setContentText(getString(R.string.status_still))
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build()

            startForeground(1, notification)
            startTimer(freq, selectedTime, prepTime, isOvertimeStart, overtimeSecsStart, initialTime)
        } else if (intent?.action == ACTION_STOP) {
            stopTimer()
            stopSelf()
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

    private fun startTimer(gongFreqHz: Double, startingTimeLeft: Int, prepTimeSeconds: Int, isOvertimeStart: Boolean, overtimeSecsStart: Int, initialSelectedTime: Int) {
        if (isTimerRunning) return
        isTimerRunning = true
        isServiceRunning = true

        val wakeLockTimeoutMs = if (initialSelectedTime > 0 && !isOvertimeStart) {
            (startingTimeLeft + prepTimeSeconds.coerceAtLeast(0)) * 1000L + WAKELOCK_MARGIN_MS
        } else {
            WAKELOCK_MARGIN_MS
        }
        acquireWakeLock(wakeLockTimeoutMs)

        timerJob = serviceScope.launch {
            try {
                // 1. CONTO ALLA ROVESCIA DI PREPARAZIONE
                var prepLeft = prepTimeSeconds
                if (prepLeft > 0) {
                    while (prepLeft > 0 && isTimerRunning) {
                        broadcastState(true, prepLeft, startingTimeLeft, false, 0, initialSelectedTime)
                        delay(1000L)
                        prepLeft -= 1
                    }
                }

                if (!isTimerRunning) return@launch

                // 2. INIZIO EFFETTIVO MEDITAZIONE CON GONG AUDIO E VIBRAZIONE
                if (prepTimeSeconds >= 0) {
                    triggerHapticGong(this@StillTimerService)
                    serviceScope.launch(Dispatchers.Default) {
                        playGongSound(gongFreqHz, repeatCount = 1)
                    }
                }

                val startRealtime = SystemClock.elapsedRealtime()
                var timeLeft = startingTimeLeft
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
                        timeLeft = startingTimeLeft + elapsedSeconds
                    } else if (!isOvertime) {
                        val remaining = startingTimeLeft - elapsedSeconds
                        if (remaining > 0) {
                            timeLeft = remaining
                        } else {
                            timeLeft = 0
                            isOvertime = true
                            overtimeSec = 0

                            triggerHapticGong(this@StillTimerService)
                            serviceScope.launch(Dispatchers.Default) {
                                playGongSound(gongFreqHz, repeatCount = 3)
                            }

                            // Risveglia e riporta in primo piano la schermata con i pulsanti per salvare
                            try {
                                val openAppIntent = Intent(this@StillTimerService, MainActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                                }
                                startActivity(openAppIntent)
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

    private fun stopTimer() {
        isTimerRunning = false
        isServiceRunning = false
        timerJob?.cancel()
        timerJob = null
        releaseWakeLock()
    }

    @Synchronized
    private fun acquireWakeLock(timeoutMs: Long) {
        if (!isTimerRunning) return
        wakeLock?.let { if (it.isHeld) it.release() }
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
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
