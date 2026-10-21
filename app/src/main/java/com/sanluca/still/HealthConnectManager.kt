package com.sanluca.still

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.feature.ExperimentalMindfulnessSessionApi
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.MindfulnessSessionRecord
import androidx.health.connect.client.records.metadata.Metadata
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

    // Registra una sessione di meditazione Vipassana su Google Salute (Health Connect)
    suspend fun saveMeditationSession(startTime: Instant, endTime: Instant): Boolean {
        if (!isHealthConnectAvailable()) return false
        return try {
            val record = MindfulnessSessionRecord(
                startTime = startTime,
                startZoneOffset = ZoneOffset.systemDefault().rules.getOffset(startTime),
                endTime = endTime,
                endZoneOffset = ZoneOffset.systemDefault().rules.getOffset(endTime),
                metadata = Metadata.manualEntry(),
                mindfulnessSessionType = MindfulnessSessionRecord.MINDFULNESS_SESSION_TYPE_MEDITATION,
                title = "Meditazione Vipassana",
                notes = "Sessione meditativa registrata con Still"
            )
            healthConnectClient.insertRecords(listOf(record))
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}