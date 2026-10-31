package com.sanluca.still

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
    private var autoDndEnabled = false
    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        // true solo finché il timer del service è realmente attivo
        @Volatile var isRunning = false
        private const val WAKELOCK_MARGIN_MS = 15 * 60 * 1000L
        private const val WAKELOCK_RENEW_SEC = 10 * 60
    }

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel("still_timer", getString(R.string.app_name), NotificationManager.IMPORTANCE_LOW)
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "START") {
            val freq = intent.getDoubleExtra("FREQ", 432.0)
            val intervalMins = intent.getIntExtra("INTERVAL", 0)
            val selectedTime = intent.getIntExtra("TIME", 600)
            val isResume = intent.getBooleanExtra("RESUME", false) // ripresa dopo pausa: TIME è il tempo rimanente
            autoDndEnabled = intent.getBooleanExtra("DND", false)

            val notificationIntent = Intent(this, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)
            val notification = Notification.Builder(this, "still_timer")
                .setContentTitle(getString(R.string.status_timer))
                .setContentText(getString(R.string.still_title))
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentIntent(pendingIntent)
                .build()

            startForeground(1, notification)
            startTimer(freq, intervalMins, selectedTime, isResume)
        } else if (intent?.action == "STOP") {
            stopTimer()
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startTimer(gongFreqHz: Double, intervalMins: Int, selectedTimeInSeconds: Int, isResume: Boolean = false) {
        if (isTimerRunning) return
        isTimerRunning = true
        isRunning = true

        if (autoDndEnabled) {
            enableDoNotDisturb(this)
        }

        // WakeLock vincolato alla sessione: durata prevista + 15 min di margine
        val wakeLockTimeoutMs = if (selectedTimeInSeconds > 0) {
            selectedTimeInSeconds * 1000L + WAKELOCK_MARGIN_MS
        } else {
            WAKELOCK_MARGIN_MS
        }
        acquireWakeLock(wakeLockTimeoutMs)

        serviceScope.launch {
            try {
                // Alla ripresa dopo una pausa non si ripete il gong d'inizio
                if (!isResume) {
                    serviceScope.launch(Dispatchers.Default) {
                        playGongSound(this@StillTimerService, gongFreqHz, repeatCount = 1)
                    }
                }

                val startRealtime = SystemClock.elapsedRealtime()
                var lastIntervalGongSec = -1
                var lastWakeLockRenewSec = 0
                var isOvertime = false

                while (isTimerRunning) {
                    delay(500L)
                    val elapsedSeconds = ((SystemClock.elapsedRealtime() - startRealtime) / 1000L).toInt()

                    // Sessione senza limite o in overtime: rinnovo a finestre di 15 min (mai un lock "infinito")
                    if ((selectedTimeInSeconds == -1 || isOvertime) &&
                        elapsedSeconds - lastWakeLockRenewSec >= WAKELOCK_RENEW_SEC) {
                        lastWakeLockRenewSec = elapsedSeconds
                        acquireWakeLock(WAKELOCK_MARGIN_MS)
                    }

                    if (intervalMins > 0 && elapsedSeconds > 0 && elapsedSeconds % (intervalMins * 60) == 0 && lastIntervalGongSec != elapsedSeconds) {
                        lastIntervalGongSec = elapsedSeconds
                        serviceScope.launch(Dispatchers.Default) {
                            playGongSound(this@StillTimerService, gongFreqHz, repeatCount = 1)
                        }
                    }

                    if (selectedTimeInSeconds != -1 && !isOvertime) {
                        val remaining = selectedTimeInSeconds - elapsedSeconds
                        if (remaining <= 0) {
                            isOvertime = true
                            serviceScope.launch(Dispatchers.Default) {
                                playGongSound(this@StillTimerService, gongFreqHz, repeatCount = 3)
                            }
                        }
                    }
                }
            } finally {
                // Pulizia
            }
        }
    }

    private fun stopTimer() {
        isTimerRunning = false
        isRunning = false
        if (autoDndEnabled) {
            disableDoNotDisturb(this)
        }
        releaseWakeLock()
    }

    @Synchronized
    private fun acquireWakeLock(timeoutMs: Long) {
        if (!isTimerRunning) return // il service è già stato fermato: non creare lock orfani
        wakeLock?.let { if (it.isHeld) it.release() }
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
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
        stopTimer()
        serviceJob.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
