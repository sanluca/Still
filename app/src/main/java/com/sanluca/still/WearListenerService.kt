package com.sanluca.still

import android.net.Uri
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.util.concurrent.TimeUnit

class WearListenerService : WearableListenerService() {

    companion object {
        private const val LOG_TAG = "StillApp"
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
                    val dataMap = try {
                        DataMapItem.fromDataItem(event.dataItem).dataMap
                    } catch (e: Exception) {
                        Log.e(LOG_TAG, "Errore deserializzazione DataItem Wear: path=$path", e)
                        continue
                    }
                    val durationSec = dataMap.getInt("duration_sec", 0)
                    val startEpochSec = dataMap.getLong("start_epoch_sec", 0L)
                    val endEpochSec = dataMap.getLong("end_epoch_sec", 0L)
                    val mapStartBpm = dataMap.getInt("start_bpm", 0).takeIf { it in 30..220 }
                    val mapEndBpm = dataMap.getInt("end_bpm", 0).takeIf { it in 30..220 }
                    val mapAvgBpm = dataMap.getInt("avg_bpm", 0).takeIf { it in 30..220 }
                    val rawParts = dataMap.getString("raw")?.split("|").orEmpty()
                    val startBpm = mapStartBpm ?: rawParts.getOrNull(3)?.toIntOrNull()?.takeIf { it in 30..220 }
                    val endBpm = mapEndBpm ?: rawParts.getOrNull(4)?.toIntOrNull()?.takeIf { it in 30..220 }
                    val avgBpm = mapAvgBpm ?: rawParts.getOrNull(5)?.toIntOrNull()?.takeIf { it in 30..220 }

                    if (durationSec > 0 && startEpochSec > 0 && endEpochSec >= startEpochSec) {
                        processCompletedSession(durationSec, startEpochSec, endEpochSec, uri, startBpm, endBpm, avgBpm)
                    } else if (rawParts.size >= 3) {
                        val d = rawParts[0].toIntOrNull() ?: 0
                        val s = rawParts[1].toLongOrNull() ?: 0L
                        val e = rawParts[2].toLongOrNull() ?: 0L
                        if (d > 0 && s > 0 && e >= s) {
                            processCompletedSession(d, s, e, uri, startBpm, endBpm, avgBpm)
                        } else {
                            Log.w(LOG_TAG, "Sessione Wear scartata (raw non valido): dur=$d, start=$s, end=$e")
                        }
                    } else {
                        Log.w(LOG_TAG, "Sessione Wear scartata (timestamp non validi): dur=$durationSec, start=$startEpochSec, end=$endEpochSec")
                    }
                }
            }
        }
    }

    private fun processCompletedSession(
        durationSec: Int,
        startEpochSec: Long,
        endEpochSec: Long,
        dataItemUri: Uri? = null,
        startBpm: Int? = null,
        endBpm: Int? = null,
        avgBpm: Int? = null
    ) {
        val startInstant = Instant.ofEpochSecond(startEpochSec)
        val endInstant = Instant.ofEpochSecond(endEpochSec)

        runBlocking(Dispatchers.IO) {
            try {
                sessionMutex.withLock {
                    // Leggiamo lo storico locale per de-duplicare
                    val history = getLocalSessionHistory(applicationContext)

                    // Controlliamo in modo atomico se esiste già una sessione con gli stessi timestamp di inizio e fine
                    val alreadyExists = history.any {
                        it.startEpochSec == startEpochSec && it.endEpochSec == endEpochSec
                    }

                    if (!alreadyExists) {
                        // 1. Aggiungiamo la sessione allo storico interno dell'app (inclusi eventuali BPM dall'orologio)
                        addSessionToLocalHistory(
                            applicationContext,
                            durationSec,
                            startInstant,
                            endInstant,
                            startBpm = startBpm,
                            endBpm = endBpm,
                            avgBpm = avgBpm
                        )

                        // 2. Accodiamo sempre per Health Connect: si sincronizzerà appena Google Salute viene collegato
                        savePendingWatchSession(applicationContext, durationSec, startInstant, endInstant)
                    }
                }

                // Puliamo il DataItem elaborato dal Data Layer per evitare accumulo
                if (dataItemUri != null) {
                    try {
                        Tasks.await(
                            Wearable.getDataClient(applicationContext).deleteDataItems(dataItemUri),
                            5,
                            TimeUnit.SECONDS
                        )
                    } catch (e: Exception) {
                        Log.w(LOG_TAG, "Impossibile eliminare DataItem Wear elaborato: $dataItemUri", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(LOG_TAG, "Errore elaborazione sessione completata da Wear", e)
            }
        }
    }
}