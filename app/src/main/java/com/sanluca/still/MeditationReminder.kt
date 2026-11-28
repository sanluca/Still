package com.sanluca.still

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

private const val KEY_REMINDER_ENABLED = "reminder_enabled"
private const val KEY_REMINDER_HOUR = "reminder_hour"
private const val KEY_REMINDER_MINUTE = "reminder_minute"
private const val KEY_REMINDER_FREQUENCY = "reminder_frequency"
private const val KEY_REMINDER_DAYS = "reminder_days"
private const val KEY_REMINDER_NEXT_TRIGGER_MS = "reminder_next_trigger_ms"

internal const val REMINDER_CHANNEL_ID = "still_meditation_reminder"
private const val REMINDER_NOTIFICATION_ID = 1001
private const val REMINDER_REQUEST_CODE = 1001
private const val ACTION_MEDITATION_REMINDER = "com.sanluca.still.ACTION_MEDITATION_REMINDER"

internal enum class ReminderFrequency(val id: String) {
    DAILY("daily"),
    WEEKDAYS("weekdays"),
    EVERY_2_DAYS("every_2_days"),
    EVERY_3_DAYS("every_3_days");

    companion object {
        fun fromId(id: String?): ReminderFrequency =
            entries.firstOrNull { it.id == id } ?: DAILY
    }
}

internal data class MeditationReminderSettings(
    val enabled: Boolean = false,
    val hour: Int = 8,
    val minute: Int = 0,
    val frequency: ReminderFrequency = ReminderFrequency.DAILY,
    val selectedDays: Set<Int> = ALL_WEEK_DAYS
) {
    companion object {
        val ALL_WEEK_DAYS: Set<Int> = (1..7).toSet()
    }
}

internal fun getSavedReminderSettings(context: Context): MeditationReminderSettings {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val enabled = prefs.getBoolean(KEY_REMINDER_ENABLED, false)
    val hour = prefs.getInt(KEY_REMINDER_HOUR, 8).coerceIn(0, 23)
    val minute = prefs.getInt(KEY_REMINDER_MINUTE, 0).coerceIn(0, 59)
    val frequency = ReminderFrequency.fromId(prefs.getString(KEY_REMINDER_FREQUENCY, ReminderFrequency.DAILY.id))
    val storedDays = prefs.getStringSet(KEY_REMINDER_DAYS, null)
        ?.mapNotNull { it.toIntOrNull() }
        ?.filter { it in 1..7 }
        ?.toSet()
        ?.takeIf { it.isNotEmpty() }
        ?: MeditationReminderSettings.ALL_WEEK_DAYS
    return MeditationReminderSettings(
        enabled = enabled,
        hour = hour,
        minute = minute,
        frequency = frequency,
        selectedDays = storedDays
    )
}

internal fun saveReminderSettings(context: Context, settings: MeditationReminderSettings) {
    val safeDays = settings.selectedDays.filter { it in 1..7 }.toSet()
        .ifEmpty { MeditationReminderSettings.ALL_WEEK_DAYS }
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit()
        .putBoolean(KEY_REMINDER_ENABLED, settings.enabled)
        .putInt(KEY_REMINDER_HOUR, settings.hour.coerceIn(0, 23))
        .putInt(KEY_REMINDER_MINUTE, settings.minute.coerceIn(0, 59))
        .putString(KEY_REMINDER_FREQUENCY, settings.frequency.id)
        .putStringSet(KEY_REMINDER_DAYS, safeDays.map { it.toString() }.toSet())
        .apply()
}

internal fun ensureReminderNotificationChannel(context: Context) {
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    val channel = NotificationChannel(
        REMINDER_CHANNEL_ID,
        context.getString(R.string.reminder_channel_name),
        NotificationManager.IMPORTANCE_LOW
    ).apply {
        description = context.getString(R.string.reminder_channel_description)
        setSound(null, null)
        enableVibration(false)
        setShowBadge(false)
    }
    manager.createNotificationChannel(channel)
}

internal fun hasReminderNotificationPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
}

internal fun areReminderNotificationsEnabled(context: Context): Boolean {
    val manager = context.getSystemService(NotificationManager::class.java) ?: return false
    if (!manager.areNotificationsEnabled()) return false
    val channel = manager.getNotificationChannel(REMINDER_CHANNEL_ID)
    return channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
}

internal fun openReminderNotificationSettings(context: Context) {
    ensureReminderNotificationChannel(context)
    try {
        val channelIntent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            putExtra(Settings.EXTRA_CHANNEL_ID, REMINDER_CHANNEL_ID)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(channelIntent)
    } catch (_: Exception) {
        try {
            val appIntent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(appIntent)
        } catch (_: Exception) {
        }
    }
}

internal fun hasMeditatedToday(context: Context, zone: ZoneId = ZoneId.systemDefault()): Boolean {
    val today = LocalDate.now(zone)
    return getLocalSessionHistory(context).any { entry ->
        val startDate = Instant.ofEpochSecond(entry.startEpochSec).atZone(zone).toLocalDate()
        val endDate = Instant.ofEpochSecond(entry.endEpochSec).atZone(zone).toLocalDate()
        startDate == today || endDate == today
    }
}

internal fun computeNextReminderTriggerMillis(
    settings: MeditationReminderSettings,
    now: ZonedDateTime = ZonedDateTime.now(),
    afterTrigger: Boolean = false
): Long {
    val hour = settings.hour.coerceIn(0, 23)
    val minute = settings.minute.coerceIn(0, 59)
    val todayTarget = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
    val minAllowed = now.plusSeconds(5)

    val nextDateTime = when (settings.frequency) {
        ReminderFrequency.DAILY -> {
            if (!afterTrigger && todayTarget.isAfter(minAllowed)) {
                todayTarget
            } else {
                todayTarget.plusDays(1)
            }
        }
        ReminderFrequency.EVERY_2_DAYS -> {
            if (afterTrigger) {
                todayTarget.plusDays(2)
            } else if (todayTarget.isAfter(minAllowed)) {
                todayTarget
            } else {
                todayTarget.plusDays(1)
            }
        }
        ReminderFrequency.EVERY_3_DAYS -> {
            if (afterTrigger) {
                todayTarget.plusDays(3)
            } else if (todayTarget.isAfter(minAllowed)) {
                todayTarget
            } else {
                todayTarget.plusDays(1)
            }
        }
        ReminderFrequency.WEEKDAYS -> {
            val allowedDays = settings.selectedDays.filter { it in 1..7 }.toSet()
                .ifEmpty { MeditationReminderSettings.ALL_WEEK_DAYS }
            var candidate = if (!afterTrigger && todayTarget.isAfter(minAllowed)) {
                todayTarget
            } else {
                todayTarget.plusDays(1)
            }
            for (offset in 0..7) {
                val check = candidate.plusDays(offset.toLong())
                if (check.dayOfWeek.value in allowedDays) {
                    candidate = check
                    break
                }
            }
            candidate
        }
    }

    return nextDateTime.toInstant().toEpochMilli()
}

private fun buildReminderPendingIntent(context: Context, flags: Int): PendingIntent? {
    val intent = Intent(context, MeditationReminderReceiver::class.java).apply {
        action = ACTION_MEDITATION_REMINDER
    }
    return PendingIntent.getBroadcast(
        context,
        REMINDER_REQUEST_CODE,
        intent,
        flags or PendingIntent.FLAG_IMMUTABLE
    )
}

internal fun cancelMeditationReminder(context: Context) {
    val alarmManager = context.getSystemService(AlarmManager::class.java)
    val pendingIntent = buildReminderPendingIntent(context, PendingIntent.FLAG_NO_CREATE)
    if (pendingIntent != null) {
        alarmManager?.cancel(pendingIntent)
        pendingIntent.cancel()
    }
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().remove(KEY_REMINDER_NEXT_TRIGGER_MS).apply()
}

internal fun scheduleMeditationReminder(
    context: Context,
    settings: MeditationReminderSettings = getSavedReminderSettings(context),
    afterTrigger: Boolean = false,
    preserveExistingFuture: Boolean = false
) {
    if (!settings.enabled) {
        cancelMeditationReminder(context)
        return
    }
    ensureReminderNotificationChannel(context)

    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val nowMs = System.currentTimeMillis()
    val storedNextMs = prefs.getLong(KEY_REMINDER_NEXT_TRIGGER_MS, 0L)
    val maxHorizonMs = when (settings.frequency) {
        ReminderFrequency.EVERY_3_DAYS -> 4L * 24L * 60L * 60L * 1000L
        ReminderFrequency.EVERY_2_DAYS -> 3L * 24L * 60L * 60L * 1000L
        ReminderFrequency.WEEKDAYS -> 8L * 24L * 60L * 60L * 1000L
        ReminderFrequency.DAILY -> 2L * 24L * 60L * 60L * 1000L
    }

    val triggerAtMillis = if (preserveExistingFuture &&
        !afterTrigger &&
        storedNextMs > nowMs + 5_000L &&
        storedNextMs <= nowMs + maxHorizonMs
    ) {
        storedNextMs
    } else {
        computeNextReminderTriggerMillis(settings = settings, afterTrigger = afterTrigger)
    }

    val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
    val pendingIntent = buildReminderPendingIntent(context, PendingIntent.FLAG_UPDATE_CURRENT) ?: return

    try {
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        prefs.edit().putLong(KEY_REMINDER_NEXT_TRIGGER_MS, triggerAtMillis).apply()
    } catch (_: Exception) {
    }
}

private fun showMeditationReminderNotification(context: Context) {
    if (!hasReminderNotificationPermission(context)) return
    ensureReminderNotificationChannel(context)

    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    if (!manager.areNotificationsEnabled()) return

    val openIntent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val contentPendingIntent = PendingIntent.getActivity(
        context,
        REMINDER_REQUEST_CODE,
        openIntent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    val notification = Notification.Builder(context, REMINDER_CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_still_notification)
        .setContentTitle(context.getString(R.string.reminder_notification_title))
        .setContentText(context.getString(R.string.reminder_notification_body))
        .setCategory(Notification.CATEGORY_REMINDER)
        .setContentIntent(contentPendingIntent)
        .setAutoCancel(true)
        .setOnlyAlertOnce(true)
        .build()

    try {
        manager.notify(REMINDER_NOTIFICATION_ID, notification)
    } catch (_: SecurityException) {
    }
}

class MeditationReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val appContext = context.applicationContext ?: context
        val settings = getSavedReminderSettings(appContext)

        when (action) {
            ACTION_MEDITATION_REMINDER -> {
                if (!settings.enabled) {
                    cancelMeditationReminder(appContext)
                    return
                }
                val isDayMatching = settings.frequency != ReminderFrequency.WEEKDAYS ||
                    (DayOfWeek.from(ZonedDateTime.now()).value in settings.selectedDays)

                if (isDayMatching &&
                    !StillTimerService.isRunning &&
                    !hasMeditatedToday(appContext)
                ) {
                    showMeditationReminderNotification(appContext)
                }
                scheduleMeditationReminder(appContext, settings, afterTrigger = true)
            }
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                if (settings.enabled) {
                    scheduleMeditationReminder(
                        appContext,
                        settings,
                        afterTrigger = false,
                        preserveExistingFuture = true
                    )
                }
            }
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                if (settings.enabled) {
                    scheduleMeditationReminder(
                        appContext,
                        settings,
                        afterTrigger = false,
                        preserveExistingFuture = false
                    )
                }
            }
        }
    }
}
