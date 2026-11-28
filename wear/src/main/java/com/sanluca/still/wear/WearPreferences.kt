package com.sanluca.still.wear

import android.content.Context
import android.content.pm.PackageManager
import com.sanluca.still.core.ALERT_MODE_BOTH
import com.sanluca.still.core.ALERT_MODE_VIBRATION
import com.sanluca.still.core.BellSound
import com.sanluca.still.wear.ui.theme.StillColorPalette

internal const val PREFS_NAME = "still_wear_prefs"
private const val KEY_SELECTED_TIME = "selected_time_seconds"
private const val KEY_PREP_TIME = "prep_time_seconds"
private const val KEY_GONG_FREQ = "gong_frequency_hz"
private const val KEY_GONG_VOLUME = "gong_volume_percent"
private const val KEY_ALERT_MODE = "alert_mode"
private const val KEY_COLOR_PALETTE = "color_palette"
private const val KEY_HEART_RATE_ENABLED = "heart_rate_enabled"

internal fun getSavedSelectedTime(context: Context): Int {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val saved = prefs.getInt(KEY_SELECTED_TIME, 300)
    return if (saved == -1 || saved > 0) saved else 300
}

internal fun saveSelectedTime(context: Context, timeInSeconds: Int) {
    val valid = if (timeInSeconds == -1 || timeInSeconds > 0) timeInSeconds else 300
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putInt(KEY_SELECTED_TIME, valid).apply()
}

internal fun getSavedColorPalette(context: Context): StillColorPalette {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return StillColorPalette.fromId(prefs.getString(KEY_COLOR_PALETTE, StillColorPalette.AUTUMN.id))
}

internal fun saveColorPalette(context: Context, palette: StillColorPalette) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putString(KEY_COLOR_PALETTE, palette.id).apply()
}

internal fun getSavedPrepTime(context: Context): Int {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getInt(KEY_PREP_TIME, 0)
}

internal fun savePrepTime(context: Context, secs: Int) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putInt(KEY_PREP_TIME, secs).apply()
}

internal fun getSavedGongFrequency(context: Context): Double {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val raw = prefs.getFloat(KEY_GONG_FREQ, BellSound.CLASSIC_BOWL.code.toFloat()).toDouble()
    return BellSound.fromCode(raw, BellSound.CLASSIC_BOWL).code
}

internal fun saveGongFrequency(context: Context, freqHz: Double) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putFloat(KEY_GONG_FREQ, freqHz.toFloat()).apply()
}

internal fun getSavedGongVolume(context: Context): Int {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getInt(KEY_GONG_VOLUME, 100).coerceIn(20, 100)
}

internal fun saveGongVolume(context: Context, volumePercent: Int) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putInt(KEY_GONG_VOLUME, volumePercent.coerceIn(20, 100)).apply()
}

internal fun getSavedAlertMode(context: Context): Int {
    val hasAudioHardware = context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUDIO_OUTPUT)
    val defaultMode = if (hasAudioHardware) ALERT_MODE_BOTH else ALERT_MODE_VIBRATION
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getInt(KEY_ALERT_MODE, defaultMode)
}

internal fun saveAlertMode(context: Context, mode: Int) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putInt(KEY_ALERT_MODE, mode).apply()
}

internal fun isHeartRateTrackingEnabled(context: Context): Boolean {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return if (prefs.contains(KEY_HEART_RATE_ENABLED)) {
        prefs.getBoolean(KEY_HEART_RATE_ENABLED, false)
    } else {
        hasHeartRatePermission(context)
    }
}

internal fun setHeartRateTrackingEnabled(context: Context, enabled: Boolean) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putBoolean(KEY_HEART_RATE_ENABLED, enabled).apply()
}

internal fun shouldTrackHeartRate(context: Context): Boolean =
    isHeartRateTrackingEnabled(context) && hasHeartRatePermission(context)
