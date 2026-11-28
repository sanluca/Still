package com.sanluca.still.wear

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status

internal class WearOngoingActivityHelper(
    private val context: Context
) {
    private var activeNotificationBuilder: NotificationCompat.Builder? = null
    private var activeOngoingActivity: OngoingActivity? = null
    private var activeNotificationPendingIntent: PendingIntent? = null

    companion object {
        const val CHANNEL_ID_TIMER = "still_wear_timer"
        const val CHANNEL_ID_ALARM = "still_wear_alarm"
        const val NOTIFICATION_ID_TIMER = 1
        const val NOTIFICATION_ID_ALARM = 2
    }

    fun createNotificationChannels() {
        val channel = NotificationChannel(
            CHANNEL_ID_TIMER,
            context.getString(R.string.app_name),
            NotificationManager.IMPORTANCE_LOW
        )
        val alarmChannel = NotificationChannel(
            CHANNEL_ID_ALARM,
            context.getString(R.string.app_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            // Il suono e la vibrazione sono gestiti dal gong del timer
            setSound(null, null)
            enableVibration(false)
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
        manager.createNotificationChannel(alarmChannel)
    }

    fun buildExistingNotificationOrNull(): Notification? =
        activeNotificationBuilder?.build()

    fun buildInitialForegroundNotification(
        prepTime: Int,
        isOvertimeStart: Boolean,
        overtimeSecsStart: Int,
        selectedTime: Int,
        initialTime: Int
    ): Notification {
        val pendingIntent = createMainActivityPendingIntent(requestCode = 0)
        val initialContentText = when {
            prepTime > 0 -> context.getString(R.string.status_preparing)
            isOvertimeStart -> context.getString(R.string.status_extra)
            else -> context.getString(R.string.status_still)
        }
        val notificationBuilder = createTimerNotificationBuilder(initialContentText, pendingIntent)
        activeNotificationPendingIntent = pendingIntent
        activeNotificationBuilder = notificationBuilder

        val initialOngoingStatus = when {
            prepTime > 0 -> buildPrepareOngoingStatus(prepTime)
            isOvertimeStart -> buildOvertimeOngoingStatus(overtimeSecsStart)
            initialTime == -1 -> buildUnlimitedOngoingStatus(selectedTime.coerceAtLeast(0))
            else -> buildCountdownOngoingStatus(selectedTime, initialTime)
        }

        // Ongoing Activity dinamica: chip attivo sul quadrante e voce nei recenti per rientrare nel timer
        activeOngoingActivity = try {
            OngoingActivity.Builder(context.applicationContext, NOTIFICATION_ID_TIMER, notificationBuilder)
                .setOngoingActivityId(NOTIFICATION_ID_TIMER)
                .setStaticIcon(R.drawable.ic_still_complication)
                .setTouchIntent(pendingIntent)
                .setStatus(initialOngoingStatus)
                .build()
                .also { it.apply(context.applicationContext) }
        } catch (_: Exception) {
            null
        }

        return notificationBuilder.build()
    }

    fun buildPrepareOngoingStatus(prepSeconds: Int): Status {
        val endRealtime = SystemClock.elapsedRealtime() + prepSeconds.coerceAtLeast(0) * 1000L
        return Status.Builder()
            .addTemplate(context.getString(R.string.ongoing_status_prepare_template))
            .addPart("timer", Status.TimerPart(endRealtime, -1L, prepSeconds.coerceAtLeast(1) * 1000L))
            .build()
    }

    fun buildCountdownOngoingStatus(remainingSeconds: Int, totalSeconds: Int): Status {
        val endRealtime = SystemClock.elapsedRealtime() + remainingSeconds.coerceAtLeast(0) * 1000L
        val totalMs = totalSeconds.coerceAtLeast(remainingSeconds).coerceAtLeast(1) * 1000L
        return Status.Builder()
            .addTemplate(context.getString(R.string.ongoing_status_timer_template))
            .addPart("timer", Status.TimerPart(endRealtime, -1L, totalMs))
            .build()
    }

    fun buildUnlimitedOngoingStatus(elapsedSeconds: Int): Status {
        val startRealtime = SystemClock.elapsedRealtime() - elapsedSeconds.coerceAtLeast(0) * 1000L
        return Status.Builder()
            .addTemplate(context.getString(R.string.ongoing_status_stopwatch_template))
            .addPart("stopwatch", Status.StopwatchPart(startRealtime))
            .build()
    }

    fun buildOvertimeOngoingStatus(overtimeSeconds: Int): Status {
        val startRealtime = SystemClock.elapsedRealtime() - overtimeSeconds.coerceAtLeast(0) * 1000L
        return Status.Builder()
            .addTemplate(context.getString(R.string.ongoing_status_extra_template))
            .addPart("stopwatch", Status.StopwatchPart(startRealtime))
            .build()
    }

    fun updateOngoingActivityStatus(isTimerRunning: Boolean, status: Status, contentText: String) {
        if (!isTimerRunning) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        try {
            activeNotificationBuilder?.setContentText(contentText)
            val ongoing = activeOngoingActivity
                ?: OngoingActivity.recoverOngoingActivity(context.applicationContext, NOTIFICATION_ID_TIMER)
                ?: OngoingActivity.recoverOngoingActivity(context.applicationContext)
            if (ongoing != null) {
                ongoing.update(context.applicationContext, status)
                return
            }
            val pendingIntent = activeNotificationPendingIntent ?: createMainActivityPendingIntent(requestCode = 0)
            val rebuiltBuilder = createTimerNotificationBuilder(contentText, pendingIntent)
            activeNotificationBuilder = rebuiltBuilder
            val rebuiltOngoing = OngoingActivity.Builder(context.applicationContext, NOTIFICATION_ID_TIMER, rebuiltBuilder)
                .setOngoingActivityId(NOTIFICATION_ID_TIMER)
                .setStaticIcon(R.drawable.ic_still_complication)
                .setTouchIntent(pendingIntent)
                .setStatus(status)
                .build()
            rebuiltOngoing.apply(context.applicationContext)
            activeOngoingActivity = rebuiltOngoing
            context.getSystemService(NotificationManager::class.java)
                .notify(NOTIFICATION_ID_TIMER, rebuiltBuilder.build())
        } catch (_: SecurityException) {
        } catch (_: Exception) {
        }
    }

    fun notifySessionEndAlarm() {
        try {
            val contentPendingIntent = createMainActivityPendingIntent(requestCode = 1)
            val alarmNotification = Notification.Builder(context, CHANNEL_ID_ALARM)
                .setContentTitle(context.getString(R.string.status_timer))
                .setContentText(context.getString(R.string.status_extra))
                .setSmallIcon(R.drawable.ic_still_complication)
                .setCategory(Notification.CATEGORY_ALARM)
                .setContentIntent(contentPendingIntent)
                .setAutoCancel(true)
                .build()
            context.getSystemService(NotificationManager::class.java)
                .notify(NOTIFICATION_ID_ALARM, alarmNotification)
        } catch (_: Exception) {
        }
    }

    fun clearState() {
        activeOngoingActivity = null
        activeNotificationBuilder = null
        activeNotificationPendingIntent = null
    }

    fun cancelAlarmNotification() {
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID_ALARM)
    }

    fun cancelTimerNotification() {
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID_TIMER)
    }

    private fun createMainActivityPendingIntent(requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun createTimerNotificationBuilder(
        contentText: String,
        pendingIntent: PendingIntent
    ): NotificationCompat.Builder =
        NotificationCompat.Builder(context, CHANNEL_ID_TIMER)
            .setContentTitle(context.getString(R.string.status_timer))
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_still_complication)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
}
