package com.sanluca.still.core

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

private fun getSystemVibrator(context: Context): Vibrator? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
}

fun triggerHapticGong(context: Context, repeatCount: Int = 1) {
    try {
        val vibrator = getSystemVibrator(context)
        if (vibrator != null && vibrator.hasVibrator()) {
            val count = if (repeatCount >= 3) 3 else 1
            val effect = if (vibrator.hasAmplitudeControl()) {
                val pulseTimings = longArrayOf(60, 80, 120, 90, 70)
                val pulseAmplitudes = intArrayOf(45, 105, 145, 90, 35)
                val gapMs = 750L
                val totalLen = count * pulseTimings.size + (count - 1)
                val timings = LongArray(totalLen)
                val amplitudes = IntArray(totalLen)
                var idx = 0
                for (i in 0 until count) {
                    if (i > 0) {
                        timings[idx] = gapMs
                        amplitudes[idx] = 0
                        idx++
                    }
                    for (j in pulseTimings.indices) {
                        timings[idx] = pulseTimings[j]
                        amplitudes[idx] = pulseAmplitudes[j]
                        idx++
                    }
                }
                VibrationEffect.createWaveform(timings, amplitudes, -1)
            } else {
                val timings = if (count >= 3) {
                    longArrayOf(0, 220, 750, 220, 750, 220)
                } else {
                    longArrayOf(0, 220)
                }
                VibrationEffect.createWaveform(timings, -1)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val vibrationAttributes = android.os.VibrationAttributes.createForUsage(
                    android.os.VibrationAttributes.USAGE_ALARM
                )
                vibrator.vibrate(effect, vibrationAttributes)
            } else {
                val audioAttributes = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .build()
                @Suppress("DEPRECATION")
                vibrator.vibrate(effect, audioAttributes)
            }
        }
    } catch (_: Exception) {
    }
}

fun cancelGongVibration(context: Context) {
    try {
        getSystemVibrator(context)?.cancel()
    } catch (_: Exception) {
    }
}
