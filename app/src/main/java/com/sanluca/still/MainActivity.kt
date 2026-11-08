package com.sanluca.still

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.sanluca.still.ui.theme.AutumnGlassOutline
import com.sanluca.still.ui.theme.AutumnSandDeep
import com.sanluca.still.ui.theme.AutumnSandLight
import com.sanluca.still.ui.theme.AutumnWoodFrame
import com.sanluca.still.ui.theme.OvertimeGold
import com.sanluca.still.ui.theme.StillTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val showHealthRationaleState = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showHealthRationaleState.value = checkIsHealthRationale(intent)
        enableEdgeToEdge()
        setContent {
            StillTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MeditationTimerScreen(
                        modifier = Modifier.padding(innerPadding),
                        showHealthRationale = showHealthRationaleState.value,
                        onDismissHealthRationale = { showHealthRationaleState.value = false }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (checkIsHealthRationale(intent)) {
            showHealthRationaleState.value = true
        }
    }

    override fun onDestroy() {
        // Se l'Activity viene chiusa davvero (back, swipe dai recenti) non lasciamo un timer orfano
        if (isFinishing && StillTimerService.isRunning) {
            stopService(Intent(this, StillTimerService::class.java))
        }
        super.onDestroy()
    }

    private fun checkIsHealthRationale(intent: Intent?): Boolean {
        if (intent == null) return false
        val action = intent.action ?: return false
        return action == "androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE" ||
               (action == "android.intent.action.VIEW_PERMISSION_USAGE" &&
                (intent.hasCategory("android.intent.category.HEALTH_PERMISSIONS") ||
                 intent.categories?.contains("android.intent.category.HEALTH_PERMISSIONS") == true))
    }
}

// --- FUNZIONI DI SALVATAGGIO DELLE IMPOSTAZIONI E STORICO ---
private const val PREFS_NAME = "still_prefs"
private const val KEY_SELECTED_TIME = "selected_time_seconds"
private const val KEY_PREP_TIME = "prep_time_seconds"
private const val KEY_GONG_FREQ = "gong_frequency_hz"
private const val KEY_INTERVAL_MINS = "interval_gong_mins"
private const val KEY_AUTO_DND = "auto_dnd_enabled"
private const val KEY_TIMER_STYLE = "timer_visual_style" // 0 = Cerchio, 1 = Clessidra
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

fun saveTimerStyle(context: Context, style: Int) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putInt(KEY_TIMER_STYLE, style).apply()
}

fun getSavedTimerStyle(context: Context): Int {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getInt(KEY_TIMER_STYLE, 1)
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

private val pendingSyncMutex = Mutex()

suspend fun syncPendingSessionsToHealthConnect(context: Context, healthConnectManager: HealthConnectManager) {
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

suspend fun resyncHistoryToHealthConnect(context: Context, healthConnectManager: HealthConnectManager): Boolean {
    return pendingSyncMutex.withLock {
        if (!healthConnectManager.hasAllPermissions()) return@withLock false
        for (session in getLocalSessionHistory(context)) {
            if (session.endEpochSec <= session.startEpochSec) continue
            val start = Instant.ofEpochSecond(session.startEpochSec)
            val end = Instant.ofEpochSecond(session.endEpochSec)
            // Cancella+reinserisce: evita duplicati anche per i record vecchi senza clientRecordId
            healthConnectManager.deleteMeditationSession(start, end)
            healthConnectManager.saveMeditationSession(start, end)
        }
        true
    }
}

fun addSessionToLocalHistory(context: Context, durationSeconds: Int, startInstant: Instant, endInstant: Instant) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val existingSet = prefs.getStringSet(KEY_SESSION_HISTORY, emptySet()) ?: emptySet()
    
    val timestamp = startInstant.toEpochMilli()
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
    }.sortedByDescending { it.startEpochSec }
}

fun calculateStreakDays(history: List<SessionLogEntry>): Int {
    if (history.isEmpty()) return 0
    
    val calendar = Calendar.getInstance()
    val sessionDays = history.map {
        calendar.timeInMillis = it.startEpochSec * 1000L
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

private const val KEY_PREV_INTERRUPTION_FILTER = "prev_interruption_filter"

fun enableDoNotDisturb(context: Context) {
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (notificationManager.isNotificationPolicyAccessGranted) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (!prefs.contains(KEY_PREV_INTERRUPTION_FILTER)) {
                prefs.edit().putInt(KEY_PREV_INTERRUPTION_FILTER, notificationManager.currentInterruptionFilter).apply()
            }
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
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            // Ripristina solo se l'abbiamo attivato noi: la seconda chiamata (onDestroy) non deve toccare nulla
            if (prefs.contains(KEY_PREV_INTERRUPTION_FILTER)) {
                val previous = prefs.getInt(KEY_PREV_INTERRUPTION_FILTER, NotificationManager.INTERRUPTION_FILTER_ALL)
                    .takeIf { it != NotificationManager.INTERRUPTION_FILTER_UNKNOWN }
                    ?: NotificationManager.INTERRUPTION_FILTER_ALL
                notificationManager.setInterruptionFilter(previous)
                prefs.edit().remove(KEY_PREV_INTERRUPTION_FILTER).apply()
            }
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

@Composable
fun MeditationTimerScreen(
    modifier: Modifier = Modifier,
    showHealthRationale: Boolean = false,
    onDismissHealthRationale: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var showPrivacyDialog by remember { mutableStateOf(showHealthRationale) }

    LaunchedEffect(showHealthRationale) {
        if (showHealthRationale) {
            showPrivacyDialog = true
        }
    }
    
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
    var isResyncing by remember { mutableStateOf(false) }
    var showStatsDialog by remember { mutableStateOf(false) }
    var sessionToDelete by remember { mutableStateOf<SessionLogEntry?>(null) }
    
    var sessionHistoryList by remember { mutableStateOf(getLocalSessionHistory(context)) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                sessionHistoryList = getLocalSessionHistory(context)
                coroutineScope.launch {
                    if (healthConnectManager.hasAllPermissions()) {
                        isHealthConnected = true
                        saveHealthConnectedState(context, true)
                        syncPendingSessionsToHealthConnect(context, healthConnectManager)
                    } else {
                        isHealthConnected = false
                        saveHealthConnectedState(context, false)
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) { grantedPermissions ->
        if (grantedPermissions.containsAll(healthConnectManager.permissions)) {
            isHealthConnected = true
            saveHealthConnectedState(context, true)
            coroutineScope.launch {
                syncPendingSessionsToHealthConnect(context, healthConnectManager)
            }
            Toast.makeText(context, context.getString(R.string.toast_health_connected), Toast.LENGTH_SHORT).show()
        } else {
            isHealthConnected = false
            saveHealthConnectedState(context, false)
            Toast.makeText(context, context.getString(R.string.toast_health_permission_denied), Toast.LENGTH_SHORT).show()
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
                    context.getString(R.string.toast_health_install),
                    Toast.LENGTH_LONG
                ).show()
                openHealthConnectInPlayStore()
            }
            else -> {
                Toast.makeText(
                    context, 
                    context.getString(R.string.toast_health_unsupported),
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
    var timerStyle by remember { mutableStateOf(getSavedTimerStyle(context)) }

    var isPreparing by remember { mutableStateOf(false) }
    var prepTimeLeftSeconds by remember { mutableStateOf(0) }

    var timeLeftInSeconds by remember { mutableStateOf(selectedTimeInSeconds) }
    
    var isOvertime by remember { mutableStateOf(false) }
    var overtimeSeconds by remember { mutableStateOf(0) }

    var isTimerRunning by remember { mutableStateOf(false) }

    var sessionStartTime by remember { mutableStateOf<Instant?>(null) }

    fun updateTimeSelection(newTime: Int) {
        selectedTimeInSeconds = newTime
        timeLeftInSeconds = if (newTime == -1) 0 else newTime
        isOvertime = false
        overtimeSeconds = 0
        sessionStartTime = null
        saveSelectedTime(context, newTime)
    }

    val progress = remember(timeLeftInSeconds, selectedTimeInSeconds) {
        if (selectedTimeInSeconds > 0) {
            timeLeftInSeconds.toFloat() / selectedTimeInSeconds.toFloat()
        } else {
            1f
        }
    }

    val overtimeColor = OvertimeGold

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

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }

                    val serviceIntent = Intent(context, StillTimerService::class.java).apply {
                        action = "START"
                        putExtra("FREQ", gongFreqHz)
                        putExtra("INTERVAL", intervalMins)
                        putExtra("TIME", selectedTimeInSeconds)
                        putExtra("DND", autoDndEnabled)
                    }
                    context.startForegroundService(serviceIntent)
                } else {
                    // Ripresa dopo pausa (alla pausa il service è stato fermato): riparte dal tempo rimanente
                    val resumeIntent = Intent(context, StillTimerService::class.java).apply {
                        action = "START"
                        putExtra("RESUME", true)
                        putExtra("FREQ", gongFreqHz)
                        putExtra("INTERVAL", intervalMins)
                        putExtra("TIME", if (selectedTimeInSeconds == -1) -1 else timeLeftInSeconds)
                        putExtra("DND", autoDndEnabled)
                    }
                    context.startForegroundService(resumeIntent)
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
            if (StillTimerService.isRunning) {
                val stopIntent = Intent(context, StillTimerService::class.java).apply {
                    action = "STOP"
                }
                context.startService(stopIntent)
            }
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

    val isInitialState = !isTimerRunning && !isOvertime && sessionStartTime == null

    fun resetTimerState() {
        isTimerRunning = false
        isPreparing = false
        isOvertime = false
        overtimeSeconds = 0
        timeLeftInSeconds = if (selectedTimeInSeconds == -1) 0 else selectedTimeInSeconds
        sessionStartTime = null
    }

    val toastResyncDone = stringResource(R.string.toast_health_resync_done)
    val toastHealthDenied = stringResource(R.string.toast_health_permission_denied)

    fun saveAndFinishSession(includeExtra: Boolean) {
        val plannedSec = if (selectedTimeInSeconds > 0) {
            if (isOvertime) selectedTimeInSeconds else (selectedTimeInSeconds - timeLeftInSeconds)
        } else {
            timeLeftInSeconds
        }
        val totalSec = if (includeExtra && isOvertime) (plannedSec + overtimeSeconds) else plannedSec
        val end = Instant.now()
        val start = end.minusSeconds(totalSec.toLong())

        if (totalSec >= 10) {
            coroutineScope.launch {
                var savedOnHealth = false
                if (healthConnectManager.hasAllPermissions()) {
                    savedOnHealth = healthConnectManager.saveMeditationSession(start, end)
                }
                if (!savedOnHealth && isHealthConnectedStateSaved(context)) {
                    savePendingWatchSession(context, totalSec, start, end)
                }
                addSessionToLocalHistory(context, totalSec, start, end)
                sessionHistoryList = getLocalSessionHistory(context)
                if (savedOnHealth) {
                    Toast.makeText(context, context.getString(R.string.toast_session_saved_health, totalSec), Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, context.getString(R.string.toast_session_saved_history, totalSec), Toast.LENGTH_SHORT).show()
                }
            }
        }

        resetTimerState()
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // --- BARRA SUPERIORE CON TITOLO E CAPSULA AZIONI AUTUNNALE ---
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.still_title),
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Light),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (isPreparing) stringResource(R.string.status_preparing)
                           else if (isOvertime) stringResource(R.string.status_overtime)
                           else if (selectedTimeInSeconds == -1) stringResource(R.string.status_no_limit)
                           else stringResource(R.string.status_timer),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isPreparing) MaterialTheme.colorScheme.tertiary
                            else if (isOvertime) overtimeColor
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Capsula unificata per i 3 pulsanti (Impostazioni, Statistiche, Info)
                Row(
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(24.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline,
                            shape = RoundedCornerShape(24.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { showSettingsDialog = true },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Text("⚙", fontSize = 19.sp, color = MaterialTheme.colorScheme.primary)
                    }
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(18.dp)
                            .background(MaterialTheme.colorScheme.outline)
                    )
                    IconButton(
                        onClick = {
                            sessionHistoryList = getLocalSessionHistory(context)
                            coroutineScope.launch {
                                syncPendingSessionsToHealthConnect(context, healthConnectManager)
                            }
                            showStatsDialog = true
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Text("≡", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(18.dp)
                            .background(MaterialTheme.colorScheme.outline)
                    )
                    IconButton(
                        onClick = { showPrivacyDialog = true },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Text("i", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // --- AREA CENTRALE FISSA (NON SALTA MAI QUANDO PARTE IL TIMER) ---
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (timerStyle == 1) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        val sandFraction = when {
                            isPreparing -> 1f
                            isOvertime -> 0f
                            selectedTimeInSeconds == -1 -> if (isInitialState) 1f else (1f - (timeLeftInSeconds.coerceAtMost(60) / 120f))
                            else -> progress.coerceIn(0f, 1f)
                        }
                        val isSandFlowing = isTimerRunning && !isPreparing && !isOvertime

                        AutumnHourglassGraphic(
                            topSandFraction = sandFraction,
                            isFlowing = isSandFlowing,
                            isOvertime = isOvertime,
                            isPreparing = isPreparing,
                            modifier = Modifier.size(width = 185.dp, height = 240.dp)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        if (isPreparing) {
                            Text(
                                text = "${stringResource(R.string.text_prepare)} ${prepTimeLeftSeconds}s",
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        } else if (isOvertime) {
                            Text(
                                text = "+${formatTime(overtimeSeconds)}",
                                maxLines = 1,
                                softWrap = false,
                                style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
                                color = overtimeColor
                            )
                        } else {
                            Text(
                                text = if (selectedTimeInSeconds == -1 && !isTimerRunning && timeLeftInSeconds == 0) {
                                    "∞"
                                } else {
                                    formatTime(timeLeftInSeconds)
                                },
                                maxLines = 1,
                                softWrap = false,
                                style = MaterialTheme.typography.displayMedium.copy(
                                    fontWeight = FontWeight.Normal,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            )
                        }
                    }
                } else {
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
                                    text = stringResource(R.string.text_prepare),
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
                                    text = stringResource(R.string.text_extra),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = overtimeColor
                                )
                                Text(
                                    text = "+${formatTime(overtimeSeconds)}",
                                    maxLines = 1,
                                    softWrap = false,
                                    style = MaterialTheme.typography.displayLarge.copy(
                                        fontSize = if (overtimeSeconds >= 6000) 40.sp else 46.sp,
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
                                maxLines = 1,
                                softWrap = false,
                                style = MaterialTheme.typography.displayLarge.copy(
                                    fontSize = when {
                                        selectedTimeInSeconds == -1 && !isTimerRunning && timeLeftInSeconds == 0 -> 80.sp
                                        timeLeftInSeconds >= 6000 -> 42.sp
                                        else -> 48.sp
                                    },
                                    fontWeight = FontWeight.Normal
                                )
                            )
                        }
                    }
                }
            }

            // --- CONTROLLI E IMPOSTAZIONI TEMPO (ALTEZZA FISSA PER EVITARE GRADINI) ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .padding(bottom = 12.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
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
                                    text = stringResource(R.string.button_save_with_extra, (overtimeSeconds / 60)),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color.Black
                                )
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = { resetTimerState() },
                                    shape = RoundedCornerShape(20.dp)
                                ) {
                                    Text(stringResource(R.string.button_reset))
                                }

                                OutlinedButton(
                                    onClick = { saveAndFinishSession(includeExtra = false) },
                                    shape = RoundedCornerShape(20.dp)
                                ) {
                                    Text(stringResource(R.string.button_save_planned, (selectedTimeInSeconds / 60)))
                                }
                            }
                        }
                    } else {
                        val canStart = isTimerRunning || selectedTimeInSeconds == -1 || timeLeftInSeconds > 0

                        Button(
                            onClick = { 
                                if (!isTimerRunning && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                }
                                isTimerRunning = !isTimerRunning 
                            },
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
                                text = if (isTimerRunning) {
                                    stringResource(R.string.button_pause)
                                } else if (!isInitialState) {
                                    stringResource(R.string.button_resume)
                                } else {
                                    stringResource(R.string.button_start_meditation)
                                },
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
                                    onClick = { resetTimerState() },
                                    shape = RoundedCornerShape(20.dp)
                                ) {
                                    Text(stringResource(R.string.button_reset))
                                }

                                Button(
                                    onClick = { 
                                        saveAndFinishSession(includeExtra = false)
                                    },
                                    shape = RoundedCornerShape(20.dp)
                                ) {
                                    Text(stringResource(R.string.button_save_and_reset))
                                }
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
            var previewGongJob by remember { mutableStateOf<Job?>(null) }

            fun playPreviewGong(freq: Double) {
                gongFreqHz = freq
                saveGongFrequency(context, freq)
                previewGongJob?.cancel()
                previewGongJob = coroutineScope.launch(Dispatchers.Default) {
                    playGongSound(context, freq, repeatCount = 1)
                }
            }

            AlertDialog(
                onDismissRequest = {
                    previewGongJob?.cancel()
                    showSettingsDialog = false
                },
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(24.dp),
                title = @Composable {
                    Text(
                        text = stringResource(R.string.dialog_settings_title),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                text = @Composable {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // 1. RITARDO PREPARAZIONE E CAMPANE INTERMEDIE IN CARD AUTUNNALE
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.settings_prep_delay),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        minLines = 2
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
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
                                        label = { Text(stringResource(R.string.settings_prep_label), fontSize = 11.sp) },
                                        placeholder = { Text(stringResource(R.string.settings_prep_placeholder)) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.settings_interval_gong),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        minLines = 2
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
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
                                        label = { Text(stringResource(R.string.settings_interval_label), fontSize = 11.sp) },
                                        placeholder = { Text(stringResource(R.string.settings_interval_placeholder)) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }

                        // 2. SUONO CAMPANA E STILE VISIVO IN CARD AUTUNNALE
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.settings_gong_sound),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    FilterChip(
                                        selected = gongFreqHz == 432.0,
                                        onClick = { playPreviewGong(432.0) },
                                        label = { Text("432 Hz", fontSize = 12.sp) }
                                    )
                                    FilterChip(
                                        selected = gongFreqHz == 528.0,
                                        onClick = { playPreviewGong(528.0) },
                                        label = { Text("528 Hz", fontSize = 12.sp) }
                                    )
                                    FilterChip(
                                        selected = gongFreqHz == 216.0,
                                        onClick = { playPreviewGong(216.0) },
                                        label = { Text("216 Hz", fontSize = 12.sp) }
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                                Spacer(modifier = Modifier.height(10.dp))

                                Text(
                                    text = stringResource(R.string.settings_timer_style),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    FilterChip(
                                        selected = timerStyle == 1,
                                        onClick = {
                                            timerStyle = 1
                                            saveTimerStyle(context, 1)
                                        },
                                        label = { Text(stringResource(R.string.settings_timer_style_hourglass)) }
                                    )
                                    FilterChip(
                                        selected = timerStyle == 0,
                                        onClick = {
                                            timerStyle = 0
                                            saveTimerStyle(context, 0)
                                        },
                                        label = { Text(stringResource(R.string.settings_timer_style_circle)) }
                                    )
                                }
                            }
                        }

                        // 3. NON DISTURBARE IN CARD AUTUNNALE
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                        Text(
                                            text = stringResource(R.string.settings_dnd_mode),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = if (autoDndEnabled) stringResource(R.string.settings_dnd_active) else stringResource(R.string.settings_dnd_inactive),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = autoDndEnabled,
                                        onCheckedChange = { newState ->
                                            autoDndEnabled = newState
                                            saveAutoDnd(context, newState)
                                        }
                                    )
                                }

                                if (!dndGranted && autoDndEnabled) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    TextButton(
                                        onClick = { requestDndAccess(context) },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(stringResource(R.string.settings_dnd_authorize), fontSize = 11.sp)
                                    }
                                }
                            }
                        }

                        // 4. GOOGLE HEALTH CONNECT IN CARD AUTUNNALE
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stringResource(R.string.settings_health_status),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = if (isHealthConnected) stringResource(R.string.settings_health_connected) else stringResource(R.string.settings_health_disconnected),
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = if (isHealthConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                if (isHealthConnected) {
                                    OutlinedButton(
                                        onClick = {
                                            if (!isResyncing) {
                                                isResyncing = true
                                                coroutineScope.launch {
                                                    try {
                                                        val ok = resyncHistoryToHealthConnect(context, healthConnectManager)
                                                        Toast.makeText(
                                                            context,
                                                            if (ok) toastResyncDone else toastHealthDenied,
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    } finally {
                                                        isResyncing = false
                                                    }
                                                }
                                            }
                                        },
                                        enabled = !isResyncing,
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        if (isResyncing) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(18.dp),
                                                strokeWidth = 2.dp
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                        }
                                        Text(stringResource(R.string.settings_health_resync))
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedButton(
                                        onClick = { connectToHealth() },
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(stringResource(R.string.settings_health_reconnect))
                                    }
                                } else {
                                    Button(
                                        onClick = { connectToHealth() },
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(stringResource(R.string.settings_health_connect))
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            previewGongJob?.cancel()
                            showSettingsDialog = false
                        },
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text(stringResource(R.string.button_close))
                    }
                }
            )
        }

        // --- DIALOG STATISTICHE E STORICO CON CANCELLAZIONE ---
        if (showStatsDialog) {
            val totalMinutes = sessionHistoryList.sumOf { it.durationMinutes }
            val totalSessions = sessionHistoryList.size
            val streak = calculateStreakDays(sessionHistoryList)

            AlertDialog(
                onDismissRequest = { showStatsDialog = false },
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(24.dp),
                title = @Composable {
                    Text(
                        text = stringResource(R.string.dialog_stats_title),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                text = @Composable {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Card(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(stringResource(R.string.stats_minutes), style = MaterialTheme.typography.labelMedium)
                                    Text(stringResource(R.string.stats_min_format, totalMinutes), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                }
                            }

                            Card(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(stringResource(R.string.stats_sessions), style = MaterialTheme.typography.labelMedium)
                                    Text("$totalSessions", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                }
                            }

                            Card(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(stringResource(R.string.stats_streak), style = MaterialTheme.typography.labelMedium)
                                    Text(stringResource(R.string.stats_streak_format, streak), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = stringResource(R.string.stats_history_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        if (sessionHistoryList.isEmpty()) {
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = stringResource(R.string.stats_history_empty),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 240.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(sessionHistoryList) { session ->
                                    Card(
                                        shape = RoundedCornerShape(14.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(
                                                    text = stringResource(R.string.stats_history_item, session.durationMinutes),
                                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = session.dateFormatted,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }

                                            IconButton(
                                                onClick = { sessionToDelete = session },
                                                modifier = Modifier.size(34.dp)
                                            ) {
                                                Text("✕", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { showStatsDialog = false },
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text(stringResource(R.string.button_close))
                    }
                }
            )
        }

        // --- CONFERMA CANCELLAZIONE SINGOLA SESSIONE ---
        sessionToDelete?.let { session ->
            AlertDialog(
                onDismissRequest = { sessionToDelete = null },
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(24.dp),
                title = {
                    Text(
                        text = stringResource(R.string.dialog_delete_title),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                text = {
                    Text(stringResource(R.string.dialog_delete_message, session.durationMinutes, session.dateFormatted))
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
                                clearPendingWatchSessions(
                                    context,
                                    getPendingWatchSessions(context)
                                        .filter { it.startEpochSec == session.startEpochSec }
                                        .map { it.rawString }
                                )
                                sessionHistoryList = getLocalSessionHistory(context)
                                sessionToDelete = null
                                Toast.makeText(context, context.getString(R.string.toast_session_deleted), Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(stringResource(R.string.button_delete))
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = { sessionToDelete = null },
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text(stringResource(R.string.button_cancel))
                    }
                }
            )
        }

        // --- DIALOG INFORMATIVA PRIVACY & SALUTE (Health Connect Rationale) ---
        if (showPrivacyDialog) {
            HealthPrivacyRationaleDialog(
                onDismiss = {
                    showPrivacyDialog = false
                    onDismissHealthRationale()
                }
            )
        }
    }
}

private const val PRIVACY_POLICY_URL = "https://github.com/sanluca/still/blob/master/PRIVACY.md"

@Composable
fun HealthPrivacyRationaleDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                text = stringResource(R.string.privacy_dialog_title),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.primary
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.privacy_dialog_health_usage),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        Text(
                            text = stringResource(R.string.privacy_dialog_health_guarantee),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                OutlinedButton(
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.privacy_dialog_button_policy))
                }
                Text(
                    text = stringResource(R.string.settings_app_version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(20.dp)
            ) {
                Text(stringResource(R.string.privacy_dialog_button_understand))
            }
        }
    )
}

@Composable
fun AutumnHourglassGraphic(
    topSandFraction: Float,
    isFlowing: Boolean,
    isOvertime: Boolean,
    isPreparing: Boolean,
    modifier: Modifier = Modifier
) {
    val grainPhase = if (isFlowing) {
        val infiniteTransition = rememberInfiniteTransition(label = "sand_grains")
        val phase by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 5200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "grain_phase"
        )
        phase
    } else {
        0f
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val capHeight = 10.dp.toPx()
        val glassTop = capHeight + 4.dp.toPx()
        val glassBottom = h - capHeight - 4.dp.toPx()
        val midY = h / 2f
        val leftX = w * 0.14f
        val rightX = w * 0.86f
        val neckHalf = 5.dp.toPx()
        val centerX = w / 2f

        val topBulbPath = Path().apply {
            moveTo(leftX, glassTop)
            lineTo(rightX, glassTop)
            cubicTo(
                rightX, glassTop + (midY - glassTop) * 0.62f,
                centerX + neckHalf, midY - (midY - glassTop) * 0.25f,
                centerX + neckHalf, midY
            )
            lineTo(centerX - neckHalf, midY)
            cubicTo(
                centerX - neckHalf, midY - (midY - glassTop) * 0.25f,
                leftX, glassTop + (midY - glassTop) * 0.62f,
                leftX, glassTop
            )
            close()
        }

        val bottomBulbPath = Path().apply {
            moveTo(centerX - neckHalf, midY)
            lineTo(centerX + neckHalf, midY)
            cubicTo(
                centerX + neckHalf, midY + (glassBottom - midY) * 0.25f,
                rightX, glassBottom - (glassBottom - midY) * 0.62f,
                rightX, glassBottom
            )
            lineTo(leftX, glassBottom)
            cubicTo(
                leftX, glassBottom - (glassBottom - midY) * 0.62f,
                centerX - neckHalf, midY + (glassBottom - midY) * 0.25f,
                centerX - neckHalf, midY
            )
            close()
        }

        val sandBrushTop = Brush.verticalGradient(
            colors = if (isPreparing) {
                listOf(AutumnSandLight, AutumnGlassOutline)
            } else {
                listOf(AutumnSandLight, AutumnSandDeep)
            },
            startY = glassTop,
            endY = midY
        )

        val sandBrushBottom = Brush.verticalGradient(
            colors = if (isOvertime) {
                listOf(OvertimeGold, AutumnSandLight)
            } else {
                listOf(AutumnSandLight, AutumnSandDeep)
            },
            startY = midY,
            endY = glassBottom
        )

        // 1. Riflesso interno del vetro
        drawPath(path = topBulbPath, color = AutumnGlassOutline.copy(alpha = 0.08f))
        drawPath(path = bottomBulbPath, color = AutumnGlassOutline.copy(alpha = 0.08f))

        // 2. Sabbia nell'ampolla superiore (con leggera conca centrale quando scorre)
        val clampedTop = topSandFraction.coerceIn(0f, 1f)
        if (clampedTop > 0.005f) {
            val bulbHeight = midY - glassTop
            val sandTopY = midY - bulbHeight * clampedTop
            val funnelDip = if (isFlowing) 5.dp.toPx() * clampedTop else 0f
            val topSandPath = Path().apply {
                moveTo(0f, sandTopY)
                quadraticTo(centerX, sandTopY + funnelDip, w, sandTopY)
                lineTo(w, midY + 2f)
                lineTo(0f, midY + 2f)
                close()
            }
            clipPath(topBulbPath) {
                drawPath(path = topSandPath, brush = sandBrushTop)
            }
        }

        // 3. Sabbia nell'ampolla inferiore (mucchietto morbido che cresce)
        val bottomFraction = if (isOvertime) 1f else (1f - clampedTop).coerceIn(0f, 1f)
        if (bottomFraction > 0.005f) {
            val bulbHeight = glassBottom - midY
            val fillHeight = bulbHeight * bottomFraction
            val sandSurfaceY = glassBottom - fillHeight
            val moundPeakY = (sandSurfaceY - 10.dp.toPx() * (1f - bottomFraction)).coerceAtLeast(midY + 2f)

            val moundPath = Path().apply {
                moveTo(0f, glassBottom + 4f)
                lineTo(0f, sandSurfaceY + 6.dp.toPx())
                quadraticTo(centerX, moundPeakY, w, sandSurfaceY + 6.dp.toPx())
                lineTo(w, glassBottom + 4f)
                close()
            }
            clipPath(bottomBulbPath) {
                drawPath(path = moundPath, brush = sandBrushBottom)
            }
        }

        // 4. Chicchi di sabbia dorata che cadono dal collo fino al mucchietto inferiore
        if (isFlowing) {
            val streamStartY = midY + 1.dp.toPx()
            val streamEndY = if (bottomFraction > 0.005f) {
                val fillH = (glassBottom - midY) * bottomFraction
                (glassBottom - fillH - 3.dp.toPx()).coerceAtLeast(streamStartY + 12.dp.toPx())
            } else {
                glassBottom - 4.dp.toPx()
            }
            val streamHeight = (streamEndY - streamStartY).coerceAtLeast(1f)
            val grainCount = 7
            val horizontalOffsets = floatArrayOf(0f, -1.1f, 1.0f, -0.5f, 1.2f, -0.9f, 0.4f)

            for (i in 0 until grainCount) {
                val progressY = ((i.toFloat() / grainCount) + grainPhase) % 1f
                val yPos = streamStartY + progressY * streamHeight
                val xJitter = horizontalOffsets[i] * 1.dp.toPx()
                val radius = if (i % 2 == 0) 2.1.dp.toPx() else 1.5.dp.toPx()
                val grainColor = if (i % 3 == 0) OvertimeGold else AutumnSandLight
                drawCircle(
                    color = grainColor,
                    radius = radius,
                    center = Offset(centerX + xJitter, yPos)
                )
            }
        }

        // 5. Contorno in vetro ambrato delle due ampolle
        val outlineColor = if (isOvertime) OvertimeGold else AutumnGlassOutline
        drawPath(
            path = topBulbPath,
            color = outlineColor,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )
        drawPath(
            path = bottomBulbPath,
            color = outlineColor,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )

        // 6. Base e Capitello in legno/bronzo autunnale
        val capWidth = w * 0.82f
        val capLeft = (w - capWidth) / 2f
        drawRoundRect(
            color = AutumnWoodFrame,
            topLeft = Offset(capLeft, 2.dp.toPx()),
            size = Size(capWidth, capHeight),
            cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx())
        )
        drawRoundRect(
            color = AutumnWoodFrame,
            topLeft = Offset(capLeft, h - capHeight - 2.dp.toPx()),
            size = Size(capWidth, capHeight),
            cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx())
        )
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