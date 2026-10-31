package com.sanluca.still

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.feature.ExperimentalMindfulnessSessionApi
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.MindfulnessSessionRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalMindfulnessSessionApi::class)
class HealthConnectManager(private val context: Context) {

    val healthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    // Richiediamo ESCLUSIVAMENTE il permesso di SCRITTURA per la meditazione
    val permissions = setOf(
        HealthPermission.getWritePermission(MindfulnessSessionRecord::class)
    )

    // Ottiene lo stato dettagliato dell'SDK di Health Connect sul dispositivo
    fun getHealthConnectStatus(): Int {
        return HealthConnectClient.getSdkStatus(context)
    }

    // Verifica se Health Connect è pronto e disponibile sul dispositivo
    fun isHealthConnectAvailable(): Boolean {
        return getHealthConnectStatus() == HealthConnectClient.SDK_AVAILABLE
    }

    // Verifica se l'utente ha già concesso il permesso di scrittura per la meditazione
    suspend fun hasAllPermissions(): Boolean {
        if (!isHealthConnectAvailable()) return false
        val granted = healthConnectClient.permissionController.getGrantedPermissions()
        return granted.containsAll(permissions)
    }

    // Registra una sessione di meditazione Vipassana su Google Salute (Health Connect) con registrazione ATTIVA
    suspend fun saveMeditationSession(startTime: Instant, endTime: Instant): Boolean {
        if (!isHealthConnectAvailable()) return false
        return try {
            val record = MindfulnessSessionRecord(
                startTime = startTime,
                startZoneOffset = ZoneOffset.systemDefault().rules.getOffset(startTime),
                endTime = endTime,
                endZoneOffset = ZoneOffset.systemDefault().rules.getOffset(endTime),
                metadata = Metadata.activelyRecorded(Device(type = Device.TYPE_PHONE)),
                mindfulnessSessionType = MindfulnessSessionRecord.MINDFULNESS_SESSION_TYPE_MEDITATION,
                title = "Still",
                notes = "Sessione meditativa registrata con Still"
            )
            healthConnectClient.insertRecords(listOf(record))
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // Cancella una sessione specifica da Google Salute in base all'intervallo temporale
    suspend fun deleteMeditationSession(startTime: Instant, endTime: Instant): Boolean {
        if (!isHealthConnectAvailable()) return false
        return try {
            healthConnectClient.deleteRecords(
                recordType = MindfulnessSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(
                    startTime.minusSeconds(2),
                    endTime.plusSeconds(2)
                )
            )
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}