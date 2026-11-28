package com.sanluca.still.wear

import android.content.Context
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import java.time.Instant

private const val KEY_PENDING_SESSIONS = "pending_watch_sessions"
private val watchSyncLock = Any()

private fun savePendingSessionOnWatch(context: Context, raw: String) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    synchronized(watchSyncLock) {
        val existingList = prefs.getStringSet(KEY_PENDING_SESSIONS, emptySet())?.toMutableList() ?: mutableListOf()
        if (!existingList.contains(raw)) {
            existingList.add(raw)
            if (existingList.size > 20) {
                existingList.sortBy { entry ->
                    entry.split("|").getOrNull(1)?.toLongOrNull() ?: 0L
                }
                while (existingList.size > 20) {
                    existingList.removeAt(0)
                }
            }
            prefs.edit().putStringSet(KEY_PENDING_SESSIONS, existingList.toSet()).apply()
        }
    }
}

private fun removePendingSessionOnWatch(context: Context, raw: String) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    synchronized(watchSyncLock) {
        val existingSet = prefs.getStringSet(KEY_PENDING_SESSIONS, emptySet()) ?: emptySet()
        if (existingSet.contains(raw)) {
            val updatedSet = existingSet.toMutableSet()
            updatedSet.remove(raw)
            if (updatedSet.isEmpty()) {
                prefs.edit().remove(KEY_PENDING_SESSIONS).apply()
            } else {
                prefs.edit().putStringSet(KEY_PENDING_SESSIONS, updatedSet).apply()
            }
        }
    }
}

internal fun flushPendingSessionsToPhone(context: Context) {
    val appContext = context.applicationContext ?: context
    try {
        val pendingSnapshot = synchronized(watchSyncLock) {
            val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            (prefs.getStringSet(KEY_PENDING_SESSIONS, emptySet()) ?: emptySet()).toList()
        }
        if (pendingSnapshot.isNotEmpty()) {
            val dataClient = Wearable.getDataClient(appContext)
            for (raw in pendingSnapshot) {
                val parts = raw.split("|")
                val durationSec = parts.getOrNull(0)?.toIntOrNull() ?: 0
                val startEpochSec = parts.getOrNull(1)?.toLongOrNull() ?: 0L
                val endEpochSec = parts.getOrNull(2)?.toLongOrNull() ?: 0L
                val startBpm = parts.getOrNull(3)?.toIntOrNull()?.takeIf { it in 30..220 }
                val endBpm = parts.getOrNull(4)?.toIntOrNull()?.takeIf { it in 30..220 }
                val avgBpm = parts.getOrNull(5)?.toIntOrNull()?.takeIf { it in 30..220 }
                if (parts.size >= 3 && durationSec > 0 && startEpochSec > 0 && endEpochSec > 0) {
                    val putDataMapReq = PutDataMapRequest.create("/meditation_completed/$startEpochSec").apply {
                        dataMap.putInt("duration_sec", durationSec)
                        dataMap.putLong("start_epoch_sec", startEpochSec)
                        dataMap.putLong("end_epoch_sec", endEpochSec)
                        if (startBpm != null) dataMap.putInt("start_bpm", startBpm)
                        if (endBpm != null) dataMap.putInt("end_bpm", endBpm)
                        if (avgBpm != null) dataMap.putInt("avg_bpm", avgBpm)
                        dataMap.putString("raw", raw)
                        dataMap.putLong("timestamp", System.currentTimeMillis())
                    }
                    val putDataReq = putDataMapReq.asPutDataRequest().setUrgent()
                    dataClient.putDataItem(putDataReq).addOnSuccessListener {
                        removePendingSessionOnWatch(appContext, raw)
                    }
                } else {
                    removePendingSessionOnWatch(appContext, raw)
                }
            }
        }
    } catch (_: Exception) {
    }
}

internal fun sendSessionToPhone(
    context: Context,
    durationSec: Int,
    startInstant: Instant,
    endInstant: Instant,
    startBpm: Int? = null,
    endBpm: Int? = null,
    avgBpm: Int? = null
) {
    val appContext = context.applicationContext ?: context
    val startEpochSec = startInstant.epochSecond
    val endEpochSec = endInstant.epochSecond
    val validStartBpm = startBpm?.takeIf { it in 30..220 }
    val validEndBpm = endBpm?.takeIf { it in 30..220 }
    val validAvgBpm = avgBpm?.takeIf { it in 30..220 }
    val hasHr = validStartBpm != null || validEndBpm != null || validAvgBpm != null
    val raw = if (hasHr) {
        "$durationSec|$startEpochSec|$endEpochSec|${validStartBpm ?: 0}|${validEndBpm ?: 0}|${validAvgBpm ?: 0}"
    } else {
        "$durationSec|$startEpochSec|$endEpochSec"
    }

    savePendingSessionOnWatch(appContext, raw)

    try {
        // 1. Invio affidabile offline/background con DataClient
        // Google Play Services salva i dati localmente sull'orologio e li sincronizza
        // in automatico appena il telefono è di nuovo visibile/acceso.
        val dataClient = Wearable.getDataClient(appContext)
        val putDataMapReq = PutDataMapRequest.create("/meditation_completed/$startEpochSec").apply {
            dataMap.putInt("duration_sec", durationSec)
            dataMap.putLong("start_epoch_sec", startEpochSec)
            dataMap.putLong("end_epoch_sec", endEpochSec)
            if (validStartBpm != null) dataMap.putInt("start_bpm", validStartBpm)
            if (validEndBpm != null) dataMap.putInt("end_bpm", validEndBpm)
            if (validAvgBpm != null) dataMap.putInt("avg_bpm", validAvgBpm)
            dataMap.putString("raw", raw)
            dataMap.putLong("timestamp", System.currentTimeMillis())
        }
        val putDataReq = putDataMapReq.asPutDataRequest().setUrgent()
        dataClient.putDataItem(putDataReq).addOnSuccessListener {
            removePendingSessionOnWatch(appContext, raw)
        }
    } catch (_: Exception) {
    }
}
