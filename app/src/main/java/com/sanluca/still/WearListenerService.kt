package com.sanluca.still

import android.net.Uri
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant

class WearListenerService : WearableListenerService() {

    companion object {
        // Mutex condiviso a livello di processo: il sistema può istanziare più volte il service
        private val sessionMutex = Mutex()
    }

    // Ricezione affidabile offline / background tramite DataClient
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.type == DataEvent.TYPE_CHANGED) {
                val uri = event.dataItem.uri
                val path = uri.path ?: ""
                if (path.startsWith("/meditation_completed")) {
                    val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                    val durationSec = dataMap.getInt("duration_sec", 0)
                    val startEpochSec = dataMap.getLong("start_epoch_sec", 0L)
                    val endEpochSec = dataMap.getLong("end_epoch_sec", 0L)

                    if (durationSec > 0 && startEpochSec > 0 && endEpochSec > 0) {
                        processCompletedSession(durationSec, startEpochSec, endEpochSec, uri)
                    } else {
                        val raw = dataMap.getString("raw")
                        if (!raw.isNullOrBlank()) {
                            val parts = raw.split("|")
                            if (parts.size >= 3) {
                                val d = parts[0].toIntOrNull() ?: 0
                                val s = parts[1].toLongOrNull() ?: 0L
                                val e = parts[2].toLongOrNull() ?: 0L
                                if (d > 0 && s > 0 && e > 0) {
                                    processCompletedSession(d, s, e, uri)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun processCompletedSession(
        durationSec: Int,
        startEpochSec: Long,
        endEpochSec: Long,
        dataItemUri: Uri? = null
    ) {
        val startInstant = Instant.ofEpochSecond(startEpochSec)
        val endInstant = Instant.ofEpochSecond(endEpochSec)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                sessionMutex.withLock {
                    // Leggiamo lo storico locale per de-duplicare
                    val history = getLocalSessionHistory(applicationContext)

                    // Controlliamo in modo atomico se esiste già una sessione con questo esatto orario di inizio
                    val alreadyExists = history.any { it.startEpochSec == startEpochSec }

                    if (!alreadyExists) {
                        // 1. Aggiungiamo la sessione allo storico interno dell'app
                        addSessionToLocalHistory(applicationContext, durationSec, startInstant, endInstant)

                        // 2. Salviamo la sessione nella coda in sospeso per Health Connect (verrà inviata appena l'app va in primo piano)
                        savePendingWatchSession(applicationContext, durationSec, startInstant, endInstant)
                    }
                }

                // Puliamo il DataItem elaborato dal Data Layer per evitare accumulo
                if (dataItemUri != null) {
                    try {
                        Wearable.getDataClient(applicationContext).deleteDataItems(dataItemUri)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}