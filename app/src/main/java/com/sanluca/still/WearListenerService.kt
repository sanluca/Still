package com.sanluca.still

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant

class WearListenerService : WearableListenerService() {

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.type == DataEvent.TYPE_CHANGED && event.dataItem.uri.path == "/meditation_completed") {
                val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                val durationSec = dataMap.getInt("durationSec", 0)
                val startEpochSec = dataMap.getLong("startEpochSec", 0L)
                val endEpochSec = dataMap.getLong("endEpochSec", 0L)

                if (durationSec > 0 && startEpochSec > 0 && endEpochSec > 0) {
                    processCompletedSession(durationSec, startEpochSec, endEpochSec)
                }
            }
        }
    }

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

    private fun processCompletedSession(durationSec: Int, startEpochSec: Long, endEpochSec: Long) {
        val startInstant = Instant.ofEpochSecond(startEpochSec)
        val endInstant = Instant.ofEpochSecond(endEpochSec)

        val healthConnectManager = HealthConnectManager(applicationContext)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (healthConnectManager.hasAllPermissions()) {
                    healthConnectManager.saveMeditationSession(startInstant, endInstant)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            addSessionToLocalHistory(applicationContext, durationSec, startInstant, endInstant)
        }
    }
}