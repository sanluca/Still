package com.sanluca.still.wear

import android.content.Context
import android.content.pm.PackageManager
import com.sanluca.still.wear.ui.theme.StillColorPalette

internal const val PREFS_NAME = "still_wear_prefs"
private const val KEY_SELECTED_TIME = "selected_time_seconds"
private const val KEY_PREP_TIME = "prep_time_seconds"
private const val KEY_GONG_FREQ = "gong_frequency_hz"
private const val KEY_GONG_VOLUME = "gong_volume_percent"
private const val KEY_ALERT_MODE = "alert_mode"
private const val KEY_COLOR_PALETTE = "color_palette"

const val ALERT_MODE_BOTH = 0
const val ALERT_MODE_SOUND = 1
const val ALERT_MODE_VIBRATION = 2

fun getSavedSelectedTime(context: Context): Int {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val saved = prefs.getInt(KEY_SELECTED_TIME, 300)
    return if (saved == -1 || saved > 0) saved else 300
}

fun saveSelectedTime(context: Context, timeInSeconds: Int) {
    val valid = if (timeInSeconds == -1 || timeInSeconds > 0) timeInSeconds else 300
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putInt(KEY_SELECTED_TIME, valid).apply()
}

fun getSavedColorPalette(context: Context): StillColorPalette {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return StillColorPalette.fromId(prefs.getString(KEY_COLOR_PALETTE, StillColorPalette.AUTUMN.id))
}

fun saveColorPalette(context: Context, palette: StillColorPalette) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putString(KEY_COLOR_PALETTE, palette.id).apply()
}

fun getSavedPrepTime(context: Context): Int {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getInt(KEY_PREP_TIME, 0)
}

fun savePrepTime(context: Context, secs: Int) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putInt(KEY_PREP_TIME, secs).apply()
}

fun getSavedGongFrequency(context: Context): Double {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val raw = prefs.getFloat(KEY_GONG_FREQ, 432.0f).toDouble()
    return when {
        raw > 2500.0 -> TINGSHA_FREQ_HZ
        raw in 215.0..217.0 -> 216.0
        raw in 431.0..433.0 -> 432.0
        raw in 527.0..529.0 -> 528.0
        else -> normalizeSoftBellFrequency(raw) ?: 432.0
    }
}

fun saveGongFrequency(context: Context, freqHz: Double) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putFloat(KEY_GONG_FREQ, freqHz.toFloat()).apply()
}

fun getSavedGongVolume(context: Context): Int {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getInt(KEY_GONG_VOLUME, 100).coerceIn(20, 100)
}

fun saveGongVolume(context: Context, volumePercent: Int) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putInt(KEY_GONG_VOLUME, volumePercent.coerceIn(20, 100)).apply()
}

fun getSavedAlertMode(context: Context): Int {
    val hasAudioHardware = context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUDIO_OUTPUT)
    val defaultMode = if (hasAudioHardware) ALERT_MODE_BOTH else ALERT_MODE_VIBRATION
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getInt(KEY_ALERT_MODE, defaultMode)
}

fun saveAlertMode(context: Context, mode: Int) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putInt(KEY_ALERT_MODE, mode).apply()
}
