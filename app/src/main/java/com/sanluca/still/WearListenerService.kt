package com.sanluca.still

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant

class WearListenerService : WearableListenerService() {

    // Utilizziamo un solo canale istantaneo (MessageClient) per evitare ogni duplicato
    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path == "/meditation_completed") {
            val raw = String(messageEvent.data)
            val parts = raw.split("|")
            if (parts.size >= 3) {
                val durationSec = parts[0].toIntOrNull() ?: 0
                val startEpochSec = parts[1].toLongOrNull() ?: 0L
                val endEpochSec = parts[2].toLongOrNull() ?: 0L

                if (durationSec > 0 && startEpochSec > 0 && endEpochSec > 0) {
                    processCompletedSession(durationSec, startEpochSec, endEpochSec)
                }
            }
        } else {
            super.onMessageReceived(messageEvent)
        }
    }

    @Synchronized
    private fun processCompletedSession(durationSec: Int, startEpochSec: Long, endEpochSec: Long) {
        val startInstant = Instant.ofEpochSecond(startEpochSec)
        val endInstant = Instant.ofEpochSecond(endEpochSec)

        CoroutineScope(Dispatchers.IO).launch {
            try {
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
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}