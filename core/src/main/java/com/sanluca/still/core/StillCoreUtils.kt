package com.sanluca.still.core

import java.util.Locale

const val ALERT_MODE_BOTH = 0
const val ALERT_MODE_SOUND = 1
const val ALERT_MODE_VIBRATION = 2

fun formatTime(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}
