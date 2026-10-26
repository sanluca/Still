package com.sanluca.still

import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.feature.ExperimentalMindfulnessSessionApi
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.sanluca.still.ui.theme.StillTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StillTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MeditationTimerScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

// --- FUNZIONI DI SALVATAGGIO DELLE IMPOSTAZIONI E STORICO ---
private const val PREFS_NAME = "still_prefs"
private const val KEY_SELECTED_TIME = "selected_time_seconds"
private const val KEY_PREP_TIME = "prep_time_seconds"
private const val KEY_GONG_FREQ = "gong_frequency_hz"
private const val KEY_INTERVAL_MINS = "interval_gong_mins"
private const val KEY_AUTO_DND = "auto_dnd_enabled"
private const val KEY_HEALTH_CONNECTED = "health_connected"
private const val KEY_SESSION_HISTORY = "session_history_list"
private const val KEY_PENDING_HEALTH_SESSIONS = "pending_health_sessions"

data class SessionLogEntry(
    val id: Long,
    val dateFormatted: String,
    val durationMinutes: Int,
    val startEpochSec: Long,
    val endEpochSec: Long,
    val rawString: String
)

data class PendingSessionEntry(
    val durationSec: Int,
    val startEpochSec: Long,
    val endEpochSec: Long,
    val rawString: String
)

fun saveSelectedTime(context: Context, timeInSeconds: Int) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putInt(KEY_SELECTED_TIME, timeInSeconds).apply()
}

fun getSavedSelectedTime(context: Context): Int {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getInt(KEY_SELECTED_TIME, 600) // Default 10 minuti (600s)
}

fun saveGongFrequency(context: Context, freqHz: Double) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putFloat(KEY_GONG_FREQ, freqHz.toFloat()).apply()
}

fun getSavedGongFrequency(context: Context): Double {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getFloat(KEY_GONG_FREQ, 432.0f).toDouble()
}

fun saveIntervalMins(context: Context, mins: Int) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putInt(KEY_INTERVAL_MINS, mins).apply()
}

fun getSavedIntervalMins(context: Context): Int {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getInt(KEY_INTERVAL_MINS, 0)
}

fun saveAutoDnd(context: Context, enabled: Boolean) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putBoolean(KEY_AUTO_DND, enabled).apply()
    syncSettingsToWatch(context, getSavedPrepTime(context), enabled)
}

fun getSavedAutoDnd(context: Context): Boolean {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getBoolean(KEY_AUTO_DND, true)
}

fun syncSettingsToWatch(context: Context, prepSeconds: Int, autoDnd: Boolean = getSavedAutoDnd(context)) {
    try {
        val dndInt = if (autoDnd) 1 else 0
        val messageClient = Wearable.getMessageClient(context)
        val nodeClient = Wearable.getNodeClient(context)
        nodeClient.connectedNodes.addOnSuccessListener { nodes ->
            val payload = "$prepSeconds|$dndInt".toByteArray()
            for (node in nodes) {
                messageClient.sendMessage(node.id, "/update_settings", payload)
            }
        }

        val dataClient = Wearable.getDataClient(context)
        val putDataMapReq = PutDataMapRequest.create("/still_settings")
        putDataMapReq.dataMap.putInt("prep_time_seconds", prepSeconds)
        putDataMapReq.dataMap.putBoolean("auto_dnd_enabled", autoDnd)
        putDataMapReq.dataMap.putLong("timestamp", System.currentTimeMillis())
        val putDataReq = putDataMapReq.asPutDataRequest().setUrgent()
        dataClient.putDataItem(putDataReq)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun savePrepTime(context: Context, prepSeconds: Int) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putInt(KEY_PREP_TIME, prepSeconds).apply()
    syncSettingsToWatch(context, prepSeconds)
}

fun getSavedPrepTime(context: Context): Int {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getInt(KEY_PREP_TIME, 0)
}

fun saveHealthConnectedState(context: Context, isConnected: Boolean) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putBoolean(KEY_HEALTH_CONNECTED, isConnected).apply()
}

fun isHealthConnectedStateSaved(context: Context): Boolean {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getBoolean(KEY_HEALTH_CONNECTED, false)
}

fun savePendingWatchSession(context: Context, durationSec: Int, startInstant: Instant, endInstant: Instant) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val existingSet = prefs.getStringSet(KEY_PENDING_HEALTH_SESSIONS, emptySet()) ?: emptySet()
    val raw = "$durationSec|${startInstant.epochSecond}|${endInstant.epochSecond}"
    val updatedSet = existingSet.toMutableSet()
    updatedSet.add(raw)
    prefs.edit().putStringSet(KEY_PENDING_HEALTH_SESSIONS, updatedSet).apply()
}

fun getPendingWatchSessions(context: Context): List<PendingSessionEntry> {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val set = prefs.getStringSet(KEY_PENDING_HEALTH_SESSIONS, emptySet()) ?: emptySet()
    return set.mapNotNull { raw ->
        val parts = raw.split("|")
        if (parts.size >= 3) {
            val dur = parts[0].toIntOrNull() ?: 0
            val start = parts[1].toLongOrNull() ?: 0L
            val end = parts[2].toLongOrNull() ?: 0L
            PendingSessionEntry(dur, start, end, raw)
        } else null
    }
}

fun clearPendingWatchSessions(context: Context, rawList: List<String>) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val existingSet = prefs.getStringSet(KEY_PENDING_HEALTH_SESSIONS, emptySet()) ?: emptySet()
    val updatedSet = existingSet.toMutableSet()
    updatedSet.removeAll(rawList.toSet())
    prefs.edit().putStringSet(KEY_PENDING_HEALTH_SESSIONS, updatedSet).apply()
}

suspend fun syncPendingSessionsToHealthConnect(context: Context, healthConnectManager: HealthConnectManager) {
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

fun addSessionToLocalHistory(context: Context, durationSeconds: Int, startInstant: Instant, endInstant: Instant) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val existingSet = prefs.getStringSet(KEY_SESSION_HISTORY, emptySet()) ?: emptySet()
    
    val timestamp = System.currentTimeMillis()
    val minutes = (durationSeconds / 60).coerceAtLeast(1)
    val startSec = startInstant.epochSecond
    val endSec = endInstant.epochSecond
    val newEntry = "$timestamp|$minutes|$startSec|$endSec"
    
    val updatedSet = existingSet.toMutableSet()
    updatedSet.add(newEntry)
    prefs.edit().putStringSet(KEY_SESSION_HISTORY, updatedSet).apply()
}

fun deleteSessionFromLocalHistory(context: Context, rawString: String) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val existingSet = prefs.getStringSet(KEY_SESSION_HISTORY, emptySet()) ?: emptySet()
    val updatedSet = existingSet.toMutableSet()
    updatedSet.remove(rawString)
    prefs.edit().putStringSet(KEY_SESSION_HISTORY, updatedSet).apply()
}

fun getLocalSessionHistory(context: Context): List<SessionLogEntry> {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val set = prefs.getStringSet(KEY_SESSION_HISTORY, emptySet()) ?: emptySet()
    val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    
    return set.mapNotNull { raw ->
        val parts = raw.split("|")
        if (parts.size >= 2) {
            val ts = parts[0].toLongOrNull() ?: 0L
            val mins = parts[1].toIntOrNull() ?: 0
            val startSec = if (parts.size >= 4) parts[2].toLongOrNull() ?: (ts / 1000) else (ts / 1000)
            val endSec = if (parts.size >= 4) parts[3].toLongOrNull() ?: ((ts / 1000) + mins * 60) else ((ts / 1000) + mins * 60)
            
            SessionLogEntry(
                id = ts,
                dateFormatted = sdf.format(Date(startSec * 1000L)), // Mostra l'Ora di INIZIO della meditazione per allinearsi a Google Salute
                durationMinutes = mins,
                startEpochSec = startSec,
                endEpochSec = endSec,
                rawString = raw
            )
        } else null
    }.sortedByDescending { it.id }
}

fun calculateStreakDays(history: List<SessionLogEntry>): Int {
    if (history.isEmpty()) return 0
    
    val calendar = Calendar.getInstance()
    val sessionDays = history.map {
        calendar.timeInMillis = it.id
        val year = calendar.get(Calendar.YEAR)
        val day = calendar.get(Calendar.DAY_OF_YEAR)
        "$year-$day"
    }.distinct()

    var streak = 0
    val todayCal = Calendar.getInstance()
    
    for (i in 0..365) {
        val y = todayCal.get(Calendar.YEAR)
        val d = todayCal.get(Calendar.DAY_OF_YEAR)
        val key = "$y-$d"
        
        if (sessionDays.contains(key)) {
            streak++
            todayCal.add(Calendar.DAY_OF_YEAR, -1)
        } else {
            if (i == 0) {
                todayCal.add(Calendar.DAY_OF_YEAR, -1)
                continue
            }
            break
        }
    }
    return streak
}

fun isDndPermissionGranted(context: Context): Boolean {
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    return notificationManager.isNotificationPolicyAccessGranted
}

fun enableDoNotDisturb(context: Context) {
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (notificationManager.isNotificationPolicyAccessGranted) {
        try {
            notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

fun disableDoNotDisturb(context: Context) {
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (notificationManager.isNotificationPolicyAccessGranted) {
        try {
            notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

fun requestDndAccess(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
        context.startActivity(intent)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

@OptIn(ExperimentalMindfulnessSessionApi::class)
@Composable
fun MeditationTimerScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    val healthConnectManager = remember { HealthConnectManager(context) }
    var isHealthConnected by remember { mutableStateOf(isHealthConnectedStateSaved(context)) }

    LaunchedEffect(Unit) {
        if (healthConnectManager.hasAllPermissions()) {
            isHealthConnected = true
            saveHealthConnectedState(context, true)
            syncPendingSessionsToHealthConnect(context, healthConnectManager)
        }
        syncSettingsToWatch(context, getSavedPrepTime(context))
    }

    var showSettingsDialog by remember { mutableStateOf(false) }
    var showStatsDialog by remember { mutableStateOf(false) }
    var sessionToDelete by remember { mutableStateOf<SessionLogEntry?>(null) }
    
    var sessionHistoryList by remember { mutableStateOf(getLocalSessionHistory(context)) }

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) { grantedPermissions ->
        if (grantedPermissions.containsAll(healthConnectManager.permissions)) {
            isHealthConnected = true
            saveHealthConnectedState(context, true)
            coroutineScope.launch {
                syncPendingSessionsToHealthConnect(context, healthConnectManager)
            }
            Toast.makeText(context, "Google Connessione Salute collegata!", Toast.LENGTH_SHORT).show()
        } else {
            isHealthConnected = false
            saveHealthConnectedState(context, false)
            Toast.makeText(context, "Permesso Connessione Salute non concesso", Toast.LENGTH_SHORT).show()
        }
    }

    fun openHealthConnectInPlayStore() {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.apps.healthdata"))
            context.startActivity(intent)
        } catch (e: Exception) {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata"))
            context.startActivity(webIntent)
        }
    }

    fun connectToHealth() {
        when (healthConnectManager.getHealthConnectStatus()) {
            HealthConnectClient.SDK_AVAILABLE -> {
                requestPermissionLauncher.launch(healthConnectManager.permissions)
            }
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
                Toast.makeText(
                    context, 
                    "Apertura Play Store per installare/aggiornare Connessione Salute...", 
                    Toast.LENGTH_LONG
                ).show()
                openHealthConnectInPlayStore()
            }
            else -> {
                Toast.makeText(
                    context, 
                    "Connessione Salute non è supportata su questo dispositivo.", 
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    var selectedTimeInSeconds by remember { mutableStateOf(getSavedSelectedTime(context)) }
    
    var prepTimeSeconds by remember { mutableStateOf(getSavedPrepTime(context)) }
    var gongFreqHz by remember { mutableStateOf(getSavedGongFrequency(context)) }
    var intervalMins by remember { mutableStateOf(getSavedIntervalMins(context)) }
    var autoDndEnabled by remember { mutableStateOf(getSavedAutoDnd(context)) }

    var isPreparing by remember { mutableStateOf(false) }
    var prepTimeLeftSeconds by remember { mutableStateOf(0) }

    var timeLeftInSeconds by remember { mutableStateOf(selectedTimeInSeconds) }
    
    var isOvertime by remember { mutableStateOf(false) }
    var overtimeSeconds by remember { mutableStateOf(0) }

    var isTimerRunning by remember { mutableStateOf(false) }

    var sessionStartTime by remember { mutableStateOf<Instant?>(null) }

    fun updateTimeSelection(newTime: Int) {
        selectedTimeInSeconds = newTime
        saveSelectedTime(context, newTime)
    }

    val progress = remember(timeLeftInSeconds, selectedTimeInSeconds) {
        if (selectedTimeInSeconds > 0) {
            timeLeftInSeconds.toFloat() / selectedTimeInSeconds.toFloat()
        } else {
            1f
        }
    }

    val overtimeColor = Color(0xFFE5A93C)

    LaunchedEffect(isTimerRunning) {
        if (isTimerRunning) {
            try {
                // --- 1. CONTO ALLA ROVESCIA DI PREPARAZIONE ---
                if (sessionStartTime == null && prepTimeSeconds > 0) {
                    isPreparing = true
                    prepTimeLeftSeconds = prepTimeSeconds
                    
                    while (prepTimeLeftSeconds > 0 && isTimerRunning) {
                        delay(1000L)
                        prepTimeLeftSeconds -= 1
                    }
                    isPreparing = false
                }

                if (!isTimerRunning) return@LaunchedEffect

                // --- 2. INIZIO EFFETTIVO MEDITAZIONE ---
                if (sessionStartTime == null) {
                    sessionStartTime = Instant.now()

                    val serviceIntent = Intent(context, StillTimerService::class.java).apply {
                        action = "START"
                        putExtra("FREQ", gongFreqHz)
                        putExtra("INTERVAL", intervalMins)
                        putExtra("TIME", selectedTimeInSeconds)
                        putExtra("DND", autoDndEnabled)
                    }
                    context.startForegroundService(serviceIntent)
                }

                val initialSeconds = timeLeftInSeconds
                val startRealtime = SystemClock.elapsedRealtime()

                while (isTimerRunning) {
                    delay(500L)
                    
                    val elapsedSeconds = ((SystemClock.elapsedRealtime() - startRealtime) / 1000L).toInt()

                    if (selectedTimeInSeconds == -1) {
                        timeLeftInSeconds = initialSeconds + elapsedSeconds
                    } else if (!isOvertime) {
                        val remaining = initialSeconds - elapsedSeconds
                        if (remaining > 0) {
                            timeLeftInSeconds = remaining
                        } else {
                            timeLeftInSeconds = 0
                            isOvertime = true
                            overtimeSeconds = 0
                        }
                    } else {
                        val overtimeStartRealtime = startRealtime + (initialSeconds * 1000L)
                        val extraSec = ((SystemClock.elapsedRealtime() - overtimeStartRealtime) / 1000L).toInt()
                        overtimeSeconds = extraSec.coerceAtLeast(0)
                    }
                }
            } finally {
                // Background service handles DND and WakeLock cleanup
            }
        } else {
            isPreparing = false
            val stopIntent = Intent(context, StillTimerService::class.java).apply {
                action = "STOP"
            }
            context.startService(stopIntent)
        }
    }

    LaunchedEffect(selectedTimeInSeconds) {
        if (!isTimerRunning) {
            timeLeftInSeconds = if (selectedTimeInSeconds == -1) 0 else selectedTimeInSeconds
            isOvertime = false
            overtimeSeconds = 0
            sessionStartTime = null
        }
    }

    val isInitialState = !isTimerRunning && !isOvertime && (timeLeftInSeconds == selectedTimeInSeconds || (selectedTimeInSeconds == -1 && timeLeftInSeconds == 0))

    fun saveAndFinishSession(includeExtra: Boolean) {
        val plannedSec = if (selectedTimeInSeconds > 0) (selectedTimeInSeconds - timeLeftInSeconds) else timeLeftInSeconds
        val totalSec = if (includeExtra && isOvertime) (plannedSec + overtimeSeconds) else plannedSec
        val end = Instant.now()
        val start = end.minusSeconds(totalSec.toLong())

        if (totalSec >= 10) {
            coroutineScope.launch {
                var savedOnHealth = false
                if (healthConnectManager.hasAllPermissions()) {
                    savedOnHealth = healthConnectManager.saveMeditationSession(start, end)
                } else {
                    savePendingWatchSession(context, totalSec, start, end)
                }
                addSessionToLocalHistory(context, totalSec, start, end)
                sessionHistoryList = getLocalSessionHistory(context)
                if (savedOnHealth) {
                    Toast.makeText(context, "Sessione di $totalSec sec salvata su Google Salute! 🧘‍♂️", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "Sessione di $totalSec sec salvata nello storico!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        isTimerRunning = false
        isOvertime = false
        overtimeSeconds = 0
        timeLeftInSeconds = if (selectedTimeInSeconds == -1) 0 else selectedTimeInSeconds
        sessionStartTime = null
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // --- BARRA SUPERIORE ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Text("⚙️", fontSize = 20.sp)
                    }
                    IconButton(onClick = { 
                        sessionHistoryList = getLocalSessionHistory(context)
                        coroutineScope.launch {
                            syncPendingSessionsToHealthConnect(context, healthConnectManager)
                        }
                        showStatsDialog = true 
                    }) {
                        Text("📊", fontSize = 20.sp)
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Vipassana",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Light),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (isPreparing) "Preparati a meditare..." 
                               else if (isOvertime) "Tempo Extra di Meditazione ✨" 
                               else if (selectedTimeInSeconds == -1) "Modalità Senza Limiti" 
                               else "Timer di Meditazione",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isPreparing) MaterialTheme.colorScheme.tertiary 
                                else if (isOvertime) overtimeColor 
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = {
                        val activity = context as? Activity
                        activity?.finishAndRemoveTask()
                    }
                ) {
                    Text("❌", fontSize = 20.sp)
                }
            }

            // --- CERCHIO CON TIMER AL CENTRO E DOPPIO CERCHIO EXTRA ---
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(280.dp)
            ) {
                if (isOvertime) {
                    CircularProgressIndicator(
                        progress = { 1f },
                        modifier = Modifier.size(280.dp),
                        color = overtimeColor,
                        strokeWidth = 6.dp,
                        strokeCap = StrokeCap.Round
                    )
                }

                if (isPreparing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(250.dp),
                        color = MaterialTheme.colorScheme.tertiary,
                        strokeWidth = 6.dp
                    )
                } else if (selectedTimeInSeconds != -1) {
                    CircularProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.size(250.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        strokeWidth = 8.dp,
                        strokeCap = StrokeCap.Round
                    )
                } else {
                    CircularProgressIndicator(
                        progress = { 1f },
                        modifier = Modifier.size(250.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        strokeWidth = 4.dp
                    )
                }

                if (isPreparing) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Preparati",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                        Text(
                            text = "${prepTimeLeftSeconds}s",
                            style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                } else if (isOvertime) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Extra",
                            style = MaterialTheme.typography.titleSmall,
                            color = overtimeColor
                        )
                        Text(
                            text = "+${formatTime(overtimeSeconds)}",
                            style = MaterialTheme.typography.displayLarge.copy(
                                fontSize = 50.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = overtimeColor
                        )
                    }
                } else {
                    Text(
                        text = if (selectedTimeInSeconds == -1 && !isTimerRunning && timeLeftInSeconds == 0) {
                            "∞"
                        } else {
                            formatTime(timeLeftInSeconds)
                        },
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontSize = if (selectedTimeInSeconds == -1 && !isTimerRunning && timeLeftInSeconds == 0) 80.sp else 54.sp,
                            fontWeight = FontWeight.Normal
                        )
                    )
                }
            }

            // --- CONTROLLI E IMPOSTAZIONI TEMPO ---
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 24.dp)
            ) {
                AnimatedVisibility(
                    visible = isInitialState,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val presets = listOf(5, 10, 15, 20)
                            presets.forEach { min ->
                                val seconds = min * 60
                                FilterChip(
                                    selected = selectedTimeInSeconds == seconds,
                                    onClick = { updateTimeSelection(seconds) },
                                    label = { Text("${min}m") }
                                )
                            }
                            FilterChip(
                                selected = selectedTimeInSeconds == -1,
                                onClick = { updateTimeSelection(-1) },
                                label = { Text("∞") }
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (selectedTimeInSeconds != -1) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = { if (selectedTimeInSeconds > 300) updateTimeSelection(selectedTimeInSeconds - 300) },
                                    enabled = selectedTimeInSeconds > 300
                                ) { Text("-5m") }

                                OutlinedButton(
                                    onClick = { if (selectedTimeInSeconds > 60) updateTimeSelection(selectedTimeInSeconds - 60) },
                                    enabled = selectedTimeInSeconds > 60
                                ) { Text("-1m") }

                                OutlinedButton(
                                    onClick = { updateTimeSelection(selectedTimeInSeconds + 60) }
                                ) { Text("+1m") }

                                OutlinedButton(
                                    onClick = { updateTimeSelection(selectedTimeInSeconds + 300) }
                                ) { Text("+5m") }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }

                // --- PULSANTE PRINCIPALE INIZIA / PAUSA / COMPLETA EXTRA ---
                if (isOvertime) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { saveAndFinishSession(includeExtra = true) },
                            shape = RoundedCornerShape(28.dp),
                            modifier = Modifier
                                .height(52.dp)
                                .fillMaxWidth(0.85f),
                            colors = ButtonDefaults.buttonColors(containerColor = overtimeColor)
                        ) {
                            Text(
                                text = "Salva con Extra (+${(overtimeSeconds / 60)}m)",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.Black
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { 
                                    isTimerRunning = false
                                    isOvertime = false
                                    overtimeSeconds = 0
                                    timeLeftInSeconds = if (selectedTimeInSeconds == -1) 0 else selectedTimeInSeconds
                                    sessionStartTime = null
                                },
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Text("Resetta")
                            }

                            OutlinedButton(
                                onClick = { saveAndFinishSession(includeExtra = false) },
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Text("Salva solo Previsto (${selectedTimeInSeconds / 60}m)")
                            }
                        }
                    }
                } else {
                    val canStart = isTimerRunning || selectedTimeInSeconds == -1 || timeLeftInSeconds > 0

                    Button(
                        onClick = { isTimerRunning = !isTimerRunning },
                        enabled = canStart,
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier
                            .height(56.dp)
                            .width(220.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isTimerRunning) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(
                            text = if (isTimerRunning) "Pausa" else "Inizia Meditazione",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium)
                        )
                    }

                    if (!isInitialState && !isTimerRunning) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { 
                                    timeLeftInSeconds = if (selectedTimeInSeconds == -1) 0 else selectedTimeInSeconds 
                                    sessionStartTime = null
                                },
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Text("Resetta")
                            }

                            Button(
                                onClick = { 
                                    saveAndFinishSession(includeExtra = false)
                                },
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Text("Salva e Resetta")
                            }
                        }
                    }
                }
            }
        }

        // --- DIALOG IMPOSTAZIONI ---
        if (showSettingsDialog) {
            var prepInputText by remember { mutableStateOf(prepTimeSeconds.toString()) }
            var intervalInputText by remember { mutableStateOf(intervalMins.toString()) }
            val dndGranted = remember { isDndPermissionGranted(context) }

            AlertDialog(
                onDismissRequest = { showSettingsDialog = false },
                title = @Composable { Text(text = "Impostazioni Still ⚙️") },
                text = @Composable {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // 1. RITARDO PREPARAZIONE
                        Text(
                            text = "Ritardo di Preparazione (prima del Gong):",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        OutlinedTextField(
                            value = prepInputText,
                            onValueChange = { input ->
                                if (input.all { it.isDigit() } && input.length <= 3) {
                                    prepInputText = input
                                    val secs = input.toIntOrNull() ?: 0
                                    prepTimeSeconds = secs
                                    savePrepTime(context, secs)
                                }
                            },
                            label = { Text("Secondi di attesa") },
                            placeholder = { Text("es. 10") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // 2. FREQUENZA CAMPANA TIBETANA (CON ANTEPRIMA AUDIO AL TAP)
                        Text(
                            text = "Suono Campana Tibetana / Gong (Tocco per anteprima):",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilterChip(
                                selected = gongFreqHz == 432.0,
                                onClick = { 
                                    gongFreqHz = 432.0
                                    saveGongFrequency(context, 432.0)
                                    coroutineScope.launch(Dispatchers.Default) {
                                    playGongSound(context, 432.0, repeatCount = 1)
                                    }
                                },
                                label = { Text("432 Hz 🔔", fontSize = 11.sp) }
                            )
                            FilterChip(
                                selected = gongFreqHz == 528.0,
                                onClick = { 
                                    gongFreqHz = 528.0
                                    saveGongFrequency(context, 528.0)
                                    coroutineScope.launch(Dispatchers.Default) {
                                    playGongSound(context, 528.0, repeatCount = 1)
                                    }
                                },
                                label = { Text("528 Hz 🔔", fontSize = 11.sp) }
                            )
                            FilterChip(
                                selected = gongFreqHz == 216.0,
                                onClick = { 
                                    gongFreqHz = 216.0
                                    saveGongFrequency(context, 216.0)
                                    coroutineScope.launch(Dispatchers.Default) {
                                    playGongSound(context, 216.0, repeatCount = 1)
                                    }
                                },
                                label = { Text("216 Hz 🔔", fontSize = 11.sp) }
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // 3. CAMPANE INTERMEDIE EDITABILI
                        Text(
                            text = "Campana Intermedia (ogni X min):",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        OutlinedTextField(
                            value = intervalInputText,
                            onValueChange = { input ->
                                if (input.all { it.isDigit() } && input.length <= 3) {
                                    intervalInputText = input
                                    val mins = input.toIntOrNull() ?: 0
                                    intervalMins = mins
                                    saveIntervalMins(context, mins)
                                }
                            },
                            label = { Text("Minuti di intervallo (0 = Spento)") },
                            placeholder = { Text("es. 10") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // 4. OPZIONE INTERATTIVA NON DISTURBARE
                        Text(
                            text = "Modalità Non Disturbare:",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        OutlinedButton(
                            onClick = { 
                                val newState = !autoDndEnabled
                                autoDndEnabled = newState
                                saveAutoDnd(context, newState)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (autoDndEnabled) "Non Disturbare in Meditazione: Attivo" else "Non Disturbare in Meditazione: Disattivato")
                        }

                        if (!dndGranted && autoDndEnabled) {
                            Spacer(modifier = Modifier.height(4.dp))
                            TextButton(
                                onClick = { requestDndAccess(context) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Autorizza accesso 'Non Disturbare' nel sistema", fontSize = 11.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Stato Google Connessione Salute:",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isHealthConnected) "✅ Collegato e Attivo" else "❌ Non collegato",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isHealthConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = { connectToHealth() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (isHealthConnected) "Riconnetti / Verifica Permessi" else "Connetti a Google Salute 🏥")
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedButton(
                            onClick = { 
                                showSettingsDialog = false
                                sessionHistoryList = getLocalSessionHistory(context)
                                showStatsDialog = true
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("📜 Mostra Dati Inviati e Statistiche")
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Versione App: 1.0.0 (Vipassana Timer)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSettingsDialog = false }) {
                        Text("Chiudi")
                    }
                }
            )
        }

        // --- DIALOG STATISTICHE E STORICO CON CANCELLAZIONE 📊 ---
        if (showStatsDialog) {
            val totalMinutes = sessionHistoryList.sumOf { it.durationMinutes }
            val totalSessions = sessionHistoryList.size
            val streak = calculateStreakDays(sessionHistoryList)

            AlertDialog(
                onDismissRequest = { showStatsDialog = false },
                title = @Composable { Text(text = "Statistiche e Storico 📊") },
                text = @Composable {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Card(
                                modifier = Modifier.weight(1f),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("Minuti", style = MaterialTheme.typography.labelMedium)
                                    Text("$totalMinutes min", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                }
                            }

                            Card(
                                modifier = Modifier.weight(1f),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("Sessioni", style = MaterialTheme.typography.labelMedium)
                                    Text("$totalSessions", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                }
                            }

                            Card(
                                modifier = Modifier.weight(1f),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("Striscia", style = MaterialTheme.typography.labelMedium)
                                    Text("🔥 $streak g", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Cronologia Meditazioni:",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        if (sessionHistoryList.isEmpty()) {
                            Text(
                                text = "Nessuna sessione registrata finora. Completa una meditazione!",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 240.dp)
                            ) {
                                items(sessionHistoryList) { session ->
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(
                                                    text = "🧘 ${session.durationMinutes} minuti",
                                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                                )
                                                Text(
                                                    text = session.dateFormatted,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }

                                            IconButton(onClick = { sessionToDelete = session }) {
                                                Text("🗑️", fontSize = 18.sp)
                                            }
                                        }
                                        HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showStatsDialog = false }) {
                        Text("Chiudi")
                    }
                }
            )
        }

        // --- CONFERMA CANCELLAZIONE SINGOLA SESSIONE ---
        sessionToDelete?.let { session ->
            AlertDialog(
                onDismissRequest = { sessionToDelete = null },
                title = { Text("Elimina Meditazione 🗑️") },
                text = {
                    Text("Vuoi davvero eliminare la sessione di ${session.durationMinutes} min del ${session.dateFormatted} sia dall'app che da Google Connessione Salute?")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                healthConnectManager.deleteMeditationSession(
                                    Instant.ofEpochSecond(session.startEpochSec),
                                    Instant.ofEpochSecond(session.endEpochSec)
                                )
                                deleteSessionFromLocalHistory(context, session.rawString)
                                sessionHistoryList = getLocalSessionHistory(context)
                                sessionToDelete = null
                                Toast.makeText(context, "Sessione eliminata!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Elimina")
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { sessionToDelete = null }) {
                        Text("Annulla")
                    }
                }
            )
        }
    }
}

fun formatTime(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

@Preview(showBackground = true)
@Composable
fun MeditationTimerPreview() {
    StillTheme {
        MeditationTimerScreen()
    }
}