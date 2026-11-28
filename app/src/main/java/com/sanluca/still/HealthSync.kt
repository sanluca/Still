package com.sanluca.still

import android.content.Context
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant

private val pendingSyncMutex = Mutex()

internal suspend fun syncPendingSessionsToHealthConnect(context: Context, healthConnectManager: HealthConnectManager) {
    pendingSyncMutex.withLock {
        // La coda va letta DENTRO il lock: una seconda chiamata la trova già svuotata
        val pending = getPendingWatchSessions(context)
        if (pending.isNotEmpty() && healthConnectManager.hasAllPermissions()) {
            val syncedRaws = mutableListOf<String>()
            for (session in pending) {
                val start = Instant.ofEpochSecond(session.startEpochSec)
                val end = Instant.ofEpochSecond(session.endEpochSec)
                val saved = healthConnectManager.saveMeditationSession(start, end)
                if (saved) {
                    syncedRaws.add(session.rawString)
                }
            }
            if (syncedRaws.isNotEmpty()) {
                clearPendingWatchSessions(context, syncedRaws)
            }
        }
    }
}

internal suspend fun resyncHistoryToHealthConnect(context: Context, healthConnectManager: HealthConnectManager): Boolean {
    return pendingSyncMutex.withLock {
        if (!healthConnectManager.hasAllPermissions()) return@withLock false
        val history = getLocalSessionHistory(context)
        var allSaved = true
        val syncedKeys = mutableSetOf<Pair<Long, Long>>()
        for (session in history) {
            if (session.endEpochSec <= session.startEpochSec) continue
            val start = Instant.ofEpochSecond(session.startEpochSec)
            val end = Instant.ofEpochSecond(session.endEpochSec)
            // Cancella+reinserisce: evita duplicati anche per i record vecchi senza clientRecordId
            healthConnectManager.deleteMeditationSession(start, end)
            val saved = healthConnectManager.saveMeditationSession(start, end)
            if (saved) {
                syncedKeys.add(session.startEpochSec to session.endEpochSec)
            } else {
                allSaved = false
            }
        }
        if (allSaved) {
            val matchingPendingRaws = getPendingWatchSessions(context)
                .filter { (it.startEpochSec to it.endEpochSec) in syncedKeys }
                .map { it.rawString }
            if (matchingPendingRaws.isNotEmpty()) {
                clearPendingWatchSessions(context, matchingPendingRaws)
            }
        }
        allSaved
    }
}
