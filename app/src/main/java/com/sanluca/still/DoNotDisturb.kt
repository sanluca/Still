package com.sanluca.still

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings

internal fun isDndPermissionGranted(context: Context): Boolean {
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    return notificationManager.isNotificationPolicyAccessGranted
}

private const val DND_STATE_PREFS_NAME = "still_dnd_state"
private const val KEY_PREV_INTERRUPTION_FILTER = "prev_interruption_filter"

internal fun enableDoNotDisturb(context: Context) {
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (notificationManager.isNotificationPolicyAccessGranted) {
        try {
            val prefs = context.getSharedPreferences(DND_STATE_PREFS_NAME, Context.MODE_PRIVATE)
            if (!prefs.contains(KEY_PREV_INTERRUPTION_FILTER)) {
                prefs.edit().putInt(KEY_PREV_INTERRUPTION_FILTER, notificationManager.currentInterruptionFilter).apply()
            }
            notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        } catch (_: Exception) {
        }
    }
}

internal fun disableDoNotDisturb(context: Context) {
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (notificationManager.isNotificationPolicyAccessGranted) {
        try {
            val prefs = context.getSharedPreferences(DND_STATE_PREFS_NAME, Context.MODE_PRIVATE)
            // Ripristina solo se l'abbiamo attivato noi: la seconda chiamata (onDestroy) non deve toccare nulla
            if (prefs.contains(KEY_PREV_INTERRUPTION_FILTER)) {
                val previous = prefs.getInt(KEY_PREV_INTERRUPTION_FILTER, NotificationManager.INTERRUPTION_FILTER_ALL)
                    .takeIf { it != NotificationManager.INTERRUPTION_FILTER_UNKNOWN }
                    ?: NotificationManager.INTERRUPTION_FILTER_ALL
                notificationManager.setInterruptionFilter(previous)
                prefs.edit().remove(KEY_PREV_INTERRUPTION_FILTER).apply()
            }
        } catch (_: Exception) {
        }
    }
}

internal fun requestDndAccess(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
        context.startActivity(intent)
    } catch (_: Exception) {
    }
}
