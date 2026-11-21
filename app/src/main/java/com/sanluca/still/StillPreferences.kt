package com.sanluca.still

import android.content.Context
import com.sanluca.still.ui.theme.StillColorPalette

// --- FUNZIONI DI SALVATAGGIO DELLE IMPOSTAZIONI E STORICO ---
internal const val PREFS_NAME = "still_prefs"
private const val KEY_SELECTED_TIME = "selected_time_seconds"
private const val KEY_PREP_TIME = "prep_time_seconds"
private const val KEY_GONG_FREQ = "gong_frequency_hz"
private const val KEY_INTERVAL_GONG_FREQ = "interval_gong_frequency_hz"
private const val KEY_GONG_VOLUME = "gong_volume_percent"
private const val KEY_ALERT_MODE = "alert_mode"
private const val KEY_INTERVAL_MINS = "interval_gong_mins"
private const val KEY_AUTO_DND = "auto_dnd_enabled"
private const val KEY_TIMER_STYLE = "timer_visual_style" // 0 = Ruota Zen, 1 = Clessidra, 2 = Candela, 3 = Essenziale
private const val KEY_COLOR_PALETTE = "color_palette"
private const val KEY_HEALTH_CONNECTED = "health_connected"
private const val KEY_AMBIENT_SOUND = "ambient_soundscape"
private const val KEY_AMBIENT_VOLUME = "ambient_volume_percent"
internal const val KEY_SESSION_HISTORY = "session_history_list"
internal const val KEY_PENDING_HEALTH_SESSIONS = "pending_health_sessions"

const val TIMER_STYLE_ESSENTIAL = 3

fun saveSelectedTime(context: Context, timeInSeconds: Int) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putInt(KEY_SELECTED_TIME, timeInSeconds).apply()
}

fun getSavedSelectedTime(context: Context): Int {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getInt(KEY_SELECTED_TIME, 600) // Default 10 minuti (600s)
}

private fun normalizeBellFrequency(rawFreq: Double, defaultFreq: Double): Double {
    return when {
        rawFreq > 2500.0 -> TINGSHA_FREQ_HZ
        rawFreq in 215.0..217.0 -> 216.0
        rawFreq in 431.0..433.0 -> 432.0
        rawFreq in 527.0..529.0 -> 528.0
        else -> normalizeSoftBellFrequency(rawFreq) ?: defaultFreq
    }
}

fun saveGongFrequency(context: Context, freqHz: Double) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putFloat(KEY_GONG_FREQ, freqHz.toFloat()).apply()
}

fun getSavedGongFrequency(context: Context): Double {
    return try {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getFloat(KEY_GONG_FREQ, 432.0f).toDouble()
        normalizeBellFrequency(raw, 432.0)
    } catch (_: Exception) {
        432.0
    }
}

fun saveIntervalGongFrequency(context: Context, freqHz: Double) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putFloat(KEY_INTERVAL_GONG_FREQ, freqHz.toFloat()).apply()
}

fun getSavedIntervalGongFrequency(context: Context): Double {
    return try {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_INTERVAL_GONG_FREQ)) {
            TINGSHA_FREQ_HZ
        } else {
            val raw = prefs.getFloat(KEY_INTERVAL_GONG_FREQ, TINGSHA_FREQ_HZ.toFloat()).toDouble()
            normalizeBellFrequency(raw, TINGSHA_FREQ_HZ)
        }
    } catch (_: Exception) {
        TINGSHA_FREQ_HZ
    }
}

fun saveGongVolume(context: Context, volumePercent: Int) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putInt(KEY_GONG_VOLUME, volumePercent.coerceIn(20, 100)).apply()
}

fun getSavedGongVolume(context: Context): Int {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getInt(KEY_GONG_VOLUME, 100).coerceIn(20, 100)
}

fun saveAlertMode(context: Context, mode: Int) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putInt(KEY_ALERT_MODE, mode).apply()
}

fun getSavedAlertMode(context: Context): Int {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getInt(KEY_ALERT_MODE, ALERT_MODE_BOTH)
}

fun saveIntervalMins(context: Context, mins: Int) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putInt(KEY_INTERVAL_MINS, mins).apply()
}

fun getSavedIntervalMins(context: Context): Int {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getInt(KEY_INTERVAL_MINS, 0)
}

fun saveAutoDnd(context: Context, enabled: Boolean) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putBoolean(KEY_AUTO_DND, enabled).apply()
}

fun getSavedAutoDnd(context: Context): Boolean {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getBoolean(KEY_AUTO_DND, true)
}

fun saveTimerStyle(context: Context, style: Int) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putInt(KEY_TIMER_STYLE, style).apply()
}

fun getSavedTimerStyle(context: Context): Int {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getInt(KEY_TIMER_STYLE, TIMER_STYLE_ESSENTIAL)
}

fun saveColorPalette(context: Context, palette: StillColorPalette) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putString(KEY_COLOR_PALETTE, palette.id).apply()
}

fun getSavedColorPalette(context: Context): StillColorPalette {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return StillColorPalette.fromId(prefs.getString(KEY_COLOR_PALETTE, StillColorPalette.AUTUMN.id))
}

fun savePrepTime(context: Context, prepSeconds: Int) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putInt(KEY_PREP_TIME, prepSeconds).apply()
}

fun getSavedPrepTime(context: Context): Int {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getInt(KEY_PREP_TIME, 0)
}

fun saveHealthConnectedState(context: Context, isConnected: Boolean) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putBoolean(KEY_HEALTH_CONNECTED, isConnected).apply()
}

fun isHealthConnectedStateSaved(context: Context): Boolean {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getBoolean(KEY_HEALTH_CONNECTED, false)
}

fun saveAmbientSoundscape(context: Context, soundscape: AmbientSoundscape) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putString(KEY_AMBIENT_SOUND, soundscape.id).apply()
}

fun getSavedAmbientSoundscape(context: Context): AmbientSoundscape {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return AmbientSoundscape.fromId(prefs.getString(KEY_AMBIENT_SOUND, AmbientSoundscape.OFF.id))
}

fun saveAmbientVolume(context: Context, volumePercent: Int) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putInt(KEY_AMBIENT_VOLUME, volumePercent.coerceIn(10, 100)).apply()
}

fun getSavedAmbientVolume(context: Context): Int {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getInt(KEY_AMBIENT_VOLUME, 50).coerceIn(10, 100)
}
