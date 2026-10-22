package com.sanluca.still

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant

class WearListenerService : WearableListenerService() {

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path == "/meditation_completed") {
            val raw = String(messageEvent.data)
            val parts = raw.split("|")
            if (parts.size >= 3) {
                val durationSec = parts[0].toIntOrNull() ?: 0
                val startEpochSec = parts[1].toLongOrNull() ?: 0L
                val endEpochSec = parts[2].toLongOrNull() ?: 0L

                val startInstant = Instant.ofEpochSecond(startEpochSec)
                val endInstant = Instant.ofEpochSecond(endEpochSec)

                val healthConnectManager = HealthConnectManager(applicationContext)
                CoroutineScope(Dispatchers.IO).launch {
                    if (healthConnectManager.hasAllPermissions()) {
                        healthConnectManager.saveMeditationSession(startInstant, endInstant)
                        addSessionToLocalHistory(applicationContext, durationSec, startInstant, endInstant)
                    }
                }
            }
        } else {
            super.onMessageReceived(messageEvent)
        }
    }
}