package com.sanluca.still

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Matrix
import android.graphics.RuntimeShader
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.sanluca.still.ui.theme.LocalStillColors
import com.sanluca.still.ui.theme.StillColorPalette
import com.sanluca.still.ui.theme.StillTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle as JavaTextStyle
import java.util.Date
import java.util.Locale

fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

class MainActivity : ComponentActivity() {
    private val showHealthRationaleState = mutableStateOf(false)
    private val isUpdateDownloadedState = mutableStateOf(false)
    private lateinit var appUpdateManager: AppUpdateManager
    private var hasCheckedUpdate = false

    private val installStateUpdatedListener = InstallStateUpdatedListener { state ->
        if (state.installStatus() == InstallStatus.DOWNLOADED) {
            isUpdateDownloadedState.value = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Se il processo è stato terminato durante una sessione con DND automatico, ripristina il filtro salvato
        if (!StillTimerService.isRunning) {
            disableDoNotDisturb(this)
        }
        showHealthRationaleState.value = checkIsHealthRationale(intent)
        appUpdateManager = AppUpdateManagerFactory.create(this)
        appUpdateManager.registerListener(installStateUpdatedListener)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent {
            var selectedPalette by remember { mutableStateOf(getSavedColorPalette(this)) }
            val snackbarHostState = remember { SnackbarHostState() }
            val updateReadyMsg = stringResource(R.string.update_ready_message)
            val updateReadyAction = stringResource(R.string.update_ready_action)
            val updateLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartIntentSenderForResult()
            ) { _ ->
                // Se l'utente rifiuta o annulla, non riproponiamo nello stesso avvio
            }

            LaunchedEffect(Unit) {
                checkForPlayStoreUpdate(updateLauncher)
            }

            LaunchedEffect(isUpdateDownloadedState.value) {
                if (isUpdateDownloadedState.value) {
                    val result = snackbarHostState.showSnackbar(
                        message = updateReadyMsg,
                        actionLabel = updateReadyAction,
                        duration = SnackbarDuration.Indefinite
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        appUpdateManager.completeUpdate()
                    } else {
                        isUpdateDownloadedState.value = false
                    }
                }
            }

            StillTheme(palette = selectedPalette) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
                ) { innerPadding ->
                    MeditationTimerScreen(
                        modifier = Modifier.padding(innerPadding),
                        selectedPalette = selectedPalette,
                        onPaletteSelected = { newPalette ->
                            selectedPalette = newPalette
                            saveColorPalette(this, newPalette)
                        },
                        showHealthRationale = showHealthRationaleState.value,
                        onDismissHealthRationale = { showHealthRationaleState.value = false }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.installStatus() == InstallStatus.DOWNLOADED) {
                isUpdateDownloadedState.value = true
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
        if (::appUpdateManager.isInitialized) {
            appUpdateManager.unregisterListener(installStateUpdatedListener)
        }
        // Se l'Activity viene chiusa davvero (back, swipe dai recenti) non lasciamo un timer orfano
        if (isFinishing && StillTimerService.isRunning) {
            stopService(Intent(this, StillTimerService::class.java))
        }
        super.onDestroy()
    }

    private fun checkForPlayStoreUpdate(updateLauncher: ActivityResultLauncher<IntentSenderRequest>) {
        if (hasCheckedUpdate || StillTimerService.isRunning) return
        hasCheckedUpdate = true

        appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) &&
                !StillTimerService.isRunning
            ) {
                appUpdateManager.startUpdateFlowForResult(
                    appUpdateInfo,
                    updateLauncher,
                    AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build()
                )
            }
        }
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
private const val KEY_INTERVAL_GONG_FREQ = "interval_gong_frequency_hz"
private const val KEY_GONG_VOLUME = "gong_volume_percent"
private const val KEY_ALERT_MODE = "alert_mode"
private const val KEY_INTERVAL_MINS = "interval_gong_mins"
private const val KEY_AUTO_DND = "auto_dnd_enabled"
private const val KEY_TIMER_STYLE = "timer_visual_style" // 0 = Cerchio, 1 = Clessidra
private const val KEY_COLOR_PALETTE = "color_palette"
private const val KEY_HEALTH_CONNECTED = "health_connected"
private const val KEY_SESSION_HISTORY = "session_history_list"
private const val KEY_PENDING_HEALTH_SESSIONS = "pending_health_sessions"

// Lock condiviso da MainActivity e WearListenerService per i read-modify-write su storico e coda Health Connect
private val historyPrefsLock = Any()

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

private fun normalizeBellFrequency(rawFreq: Double, defaultFreq: Double): Double {
    return when {
        rawFreq > 2500.0 -> TINGSHA_FREQ_HZ
        rawFreq in 215.0..217.0 -> 216.0
        rawFreq in 431.0..433.0 -> 432.0
        rawFreq in 527.0..529.0 -> 528.0
        else -> defaultFreq
    }
}

fun saveGongFrequency(context: Context, freqHz: Double) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putFloat(KEY_GONG_FREQ, freqHz.toFloat()).apply()
}

fun getSavedGongFrequency(context: Context): Double {
    return try {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getFloat(KEY_GONG_FREQ, 432.0f).toDouble()
        normalizeBellFrequency(raw, 432.0)
    } catch (_: Exception) {
        432.0
    }
}

fun saveIntervalGongFrequency(context: Context, freqHz: Double) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putFloat(KEY_INTERVAL_GONG_FREQ, freqHz.toFloat()).apply()
}

fun getSavedIntervalGongFrequency(context: Context): Double {
    return try {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_INTERVAL_GONG_FREQ)) {
            TINGSHA_FREQ_HZ
        } else {
            val raw = prefs.getFloat(KEY_INTERVAL_GONG_FREQ, TINGSHA_FREQ_HZ.toFloat()).toDouble()
            normalizeBellFrequency(raw, TINGSHA_FREQ_HZ)
        }
    } catch (_: Exception) {
        TINGSHA_FREQ_HZ
    }
}

fun saveGongVolume(context: Context, volumePercent: Int) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putInt(KEY_GONG_VOLUME, volumePercent.coerceIn(20, 100)).apply()
}

fun getSavedGongVolume(context: Context): Int {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getInt(KEY_GONG_VOLUME, 100).coerceIn(20, 100)
}

fun saveAlertMode(context: Context, mode: Int) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putInt(KEY_ALERT_MODE, mode).apply()
}

fun getSavedAlertMode(context: Context): Int {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getInt(KEY_ALERT_MODE, ALERT_MODE_BOTH)
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

fun saveColorPalette(context: Context, palette: StillColorPalette) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putString(KEY_COLOR_PALETTE, palette.id).apply()
}

fun getSavedColorPalette(context: Context): StillColorPalette {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return StillColorPalette.fromId(prefs.getString(KEY_COLOR_PALETTE, StillColorPalette.AUTUMN.id))
}

fun savePrepTime(context: Context, prepSeconds: Int) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putInt(KEY_PREP_TIME, prepSeconds).apply()
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
    synchronized(historyPrefsLock) {
        val existingSet = prefs.getStringSet(KEY_PENDING_HEALTH_SESSIONS, emptySet()) ?: emptySet()
        val raw = "$durationSec|${startInstant.epochSecond}|${endInstant.epochSecond}"
        val updatedSet = existingSet.toMutableSet()
        updatedSet.add(raw)
        prefs.edit().putStringSet(KEY_PENDING_HEALTH_SESSIONS, updatedSet).apply()
    }
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
    synchronized(historyPrefsLock) {
        val existingSet = prefs.getStringSet(KEY_PENDING_HEALTH_SESSIONS, emptySet()) ?: emptySet()
        val updatedSet = existingSet.toMutableSet()
        updatedSet.removeAll(rawList.toSet())
        prefs.edit().putStringSet(KEY_PENDING_HEALTH_SESSIONS, updatedSet).apply()
    }
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
    
    val timestamp = startInstant.toEpochMilli()
    val minutes = (durationSeconds / 60).coerceAtLeast(1)
    val startSec = startInstant.epochSecond
    val endSec = endInstant.epochSecond
    val newEntry = "$timestamp|$minutes|$startSec|$endSec"
    
    synchronized(historyPrefsLock) {
        val existingSet = prefs.getStringSet(KEY_SESSION_HISTORY, emptySet()) ?: emptySet()
        val updatedSet = existingSet.toMutableSet()
        updatedSet.add(newEntry)
        prefs.edit().putStringSet(KEY_SESSION_HISTORY, updatedSet).apply()
    }
}

fun deleteSessionFromLocalHistory(context: Context, rawString: String) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    synchronized(historyPrefsLock) {
        val existingSet = prefs.getStringSet(KEY_SESSION_HISTORY, emptySet()) ?: emptySet()
        val updatedSet = existingSet.toMutableSet()
        updatedSet.remove(rawString)
        prefs.edit().putStringSet(KEY_SESSION_HISTORY, updatedSet).apply()
    }
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

    val zone = ZoneId.systemDefault()
    val sessionDates = history.mapTo(HashSet()) {
        Instant.ofEpochSecond(it.startEpochSec).atZone(zone).toLocalDate()
    }

    var streak = 0
    var currentDate = LocalDate.now(zone)

    // Se oggi non si è ancora meditato, partiamo a verificare da ieri senza interrompere la striscia
    if (!sessionDates.contains(currentDate)) {
        currentDate = currentDate.minusDays(1)
    }

    while (sessionDates.contains(currentDate)) {
        streak++
        currentDate = currentDate.minusDays(1)
    }
    return streak
}

fun calculateBestStreakDays(history: List<SessionLogEntry>): Int {
    if (history.isEmpty()) return 0

    val zone = ZoneId.systemDefault()
    val sortedDates = history
        .map { Instant.ofEpochSecond(it.startEpochSec).atZone(zone).toLocalDate() }
        .distinct()
        .sorted()

    if (sortedDates.isEmpty()) return 0

    var bestStreak = 1
    var currentStreak = 1

    for (i in 1 until sortedDates.size) {
        if (sortedDates[i] == sortedDates[i - 1].plusDays(1)) {
            currentStreak++
            if (currentStreak > bestStreak) {
                bestStreak = currentStreak
            }
        } else {
            currentStreak = 1
        }
    }
    return bestStreak
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
    selectedPalette: StillColorPalette = StillColorPalette.AUTUMN,
    onPaletteSelected: (StillColorPalette) -> Unit = {},
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
    }

    var showSettingsDialog by remember { mutableStateOf(false) }
    var isResyncing by remember { mutableStateOf(false) }
    var showStatsDialog by remember { mutableStateOf(false) }
    var selectedCalendarDate by remember { mutableStateOf<LocalDate?>(null) }
    var sessionToDelete by remember { mutableStateOf<SessionLogEntry?>(null) }
    
    var sessionHistoryList by remember { mutableStateOf(getLocalSessionHistory(context)) }
    var dndGranted by remember { mutableStateOf(isDndPermissionGranted(context)) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                sessionHistoryList = getLocalSessionHistory(context)
                dndGranted = isDndPermissionGranted(context)
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

    // Aggiornamento live: sessioni in arrivo dall'orologio (WearListenerService) mentre l'app è aperta
    DisposableEffect(context, lifecycleOwner) {
        val livePrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val prefsListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_SESSION_HISTORY) {
                sessionHistoryList = getLocalSessionHistory(context)
            } else if (key == KEY_PENDING_HEALTH_SESSIONS &&
                lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            ) {
                coroutineScope.launch {
                    if (healthConnectManager.hasAllPermissions()) {
                        syncPendingSessionsToHealthConnect(context, healthConnectManager)
                    }
                }
            }
        }
        livePrefs.registerOnSharedPreferenceChangeListener(prefsListener)
        onDispose {
            livePrefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    val toastHealthConnectedMsg = stringResource(R.string.toast_health_connected)
    val toastHealthDeniedMsg = stringResource(R.string.toast_health_permission_denied)
    val toastHealthInstallMsg = stringResource(R.string.toast_health_install)
    val toastHealthUnsupportedMsg = stringResource(R.string.toast_health_unsupported)

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) { grantedPermissions ->
        if (grantedPermissions.containsAll(healthConnectManager.permissions)) {
            isHealthConnected = true
            saveHealthConnectedState(context, true)
            coroutineScope.launch {
                syncPendingSessionsToHealthConnect(context, healthConnectManager)
            }
            Toast.makeText(context, toastHealthConnectedMsg, Toast.LENGTH_SHORT).show()
        } else {
            isHealthConnected = false
            saveHealthConnectedState(context, false)
            Toast.makeText(context, toastHealthDeniedMsg, Toast.LENGTH_SHORT).show()
        }
    }

    fun openHealthConnectInPlayStore() {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.apps.healthdata"))
            context.startActivity(intent)
        } catch (_: Exception) {
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
                    toastHealthInstallMsg,
                    Toast.LENGTH_LONG
                ).show()
                openHealthConnectInPlayStore()
            }
            else -> {
                Toast.makeText(
                    context,
                    toastHealthUnsupportedMsg,
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    var selectedTimeInSeconds by remember { mutableStateOf(getSavedSelectedTime(context)) }
    var activeSessionPlannedSeconds by remember { mutableStateOf(selectedTimeInSeconds) }
    var liveAdjustmentSeconds by remember { mutableStateOf(0) }
    var lastTimedSeconds by remember {
        mutableStateOf(if (selectedTimeInSeconds > 0) selectedTimeInSeconds else 600)
    }
    
    var prepTimeSeconds by remember { mutableStateOf(getSavedPrepTime(context)) }
    var gongFreqHz by remember { mutableStateOf(getSavedGongFrequency(context)) }
    var intervalGongFreqHz by remember { mutableStateOf(getSavedIntervalGongFrequency(context)) }
    var gongVolumePercent by remember { mutableStateOf(getSavedGongVolume(context)) }
    var alertMode by remember { mutableStateOf(getSavedAlertMode(context)) }
    var intervalMins by remember { mutableStateOf(getSavedIntervalMins(context)) }
    var autoDndEnabled by remember { mutableStateOf(getSavedAutoDnd(context)) }
    var timerStyle by remember { mutableStateOf(getSavedTimerStyle(context)) }

    var isPreparing by remember { mutableStateOf(false) }
    var prepTimeLeftSeconds by remember { mutableStateOf(0) }

    var timeLeftInSeconds by remember {
        mutableStateOf(if (selectedTimeInSeconds == -1) 0 else selectedTimeInSeconds)
    }
    
    var isOvertime by remember { mutableStateOf(false) }
    var overtimeSeconds by remember { mutableStateOf(0) }

    var isTimerRunning by remember { mutableStateOf(false) }
    var hideTimerNumbers by remember { mutableStateOf(false) }

    var sessionStartTime by remember { mutableStateOf<Instant?>(null) }

    fun updateTimeSelection(newTime: Int) {
        if (newTime > 0) {
            lastTimedSeconds = newTime
        }
        selectedTimeInSeconds = newTime
        activeSessionPlannedSeconds = newTime
        liveAdjustmentSeconds = 0
        timeLeftInSeconds = if (newTime == -1) 0 else newTime
        isOvertime = false
        overtimeSeconds = 0
        sessionStartTime = null
        saveSelectedTime(context, newTime)
    }

    fun adjustActiveTimer(deltaSeconds: Int) {
        if (activeSessionPlannedSeconds <= 0 || isOvertime || isPreparing) return
        if (deltaSeconds < 0 && timeLeftInSeconds <= -deltaSeconds) return

        activeSessionPlannedSeconds = (activeSessionPlannedSeconds + deltaSeconds).coerceAtLeast(1)
        timeLeftInSeconds = (timeLeftInSeconds + deltaSeconds).coerceAtLeast(1)

        if (isTimerRunning) {
            liveAdjustmentSeconds += deltaSeconds
            if (StillTimerService.isRunning) {
                val adjustIntent = Intent(context, StillTimerService::class.java).apply {
                    action = "ADJUST_TIME"
                    putExtra("DELTA_SECONDS", deltaSeconds)
                }
                context.startService(adjustIntent)
            }
        }
    }

    val progress = remember(timeLeftInSeconds, activeSessionPlannedSeconds) {
        if (activeSessionPlannedSeconds > 0) {
            (timeLeftInSeconds.toFloat() / activeSessionPlannedSeconds.toFloat()).coerceIn(0f, 1f)
        } else {
            1f
        }
    }

    val overtimeColor = LocalStillColors.current.overtime
    val currentView = LocalView.current
    val keepScreenAwake = isTimerRunning || isOvertime

    // Triplo presidio per non far spegnere mai lo schermo durante preparazione, meditazione e tempo extra
    DisposableEffect(keepScreenAwake, lifecycleOwner) {
        val window = context.findActivity()?.window
        fun applyKeepScreenOn(awake: Boolean) {
            currentView.keepScreenOn = awake
            if (awake) {
                window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
        applyKeepScreenOn(keepScreenAwake)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                applyKeepScreenOn(keepScreenAwake)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            applyKeepScreenOn(false)
        }
    }

    LaunchedEffect(isTimerRunning) {
        if (isTimerRunning) {
            try {
                var serviceStartedDuringPrep = false
                fun buildFreshStartIntent(prepSeconds: Int): Intent =
                    Intent(context, StillTimerService::class.java).apply {
                        action = "START"
                        putExtra("FREQ", gongFreqHz)
                        putExtra("INTERVAL_FREQ", intervalGongFreqHz)
                        putExtra("VOLUME", gongVolumePercent)
                        putExtra("ALERT_MODE", alertMode)
                        putExtra("INTERVAL", intervalMins)
                        putExtra("TIME", selectedTimeInSeconds)
                        putExtra("PREP", prepSeconds)
                        putExtra("DND", autoDndEnabled)
                    }

                // --- 1. CONTO ALLA ROVESCIA DI PREPARAZIONE ---
                if (sessionStartTime == null && prepTimeSeconds > 0) {
                    isPreparing = true
                    prepTimeLeftSeconds = prepTimeSeconds

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                    // Il service (foreground + WakeLock) parte subito e conta anche la preparazione:
                    // blocco schermo o app in background non congelano più il conto alla rovescia
                    serviceStartedDuringPrep = try {
                        context.startForegroundService(buildFreshStartIntent(prepTimeSeconds))
                        true
                    } catch (e: Exception) {
                        e.printStackTrace()
                        false
                    }
                    
                    while (prepTimeLeftSeconds > 0 && isTimerRunning) {
                        delay(1000L)
                        if (!isTimerRunning) break
                        prepTimeLeftSeconds -= 1
                    }
                    isPreparing = false
                }

                if (!isTimerRunning) return@LaunchedEffect

                // --- 2. INIZIO EFFETTIVO MEDITAZIONE ---
                if (sessionStartTime == null) {
                    sessionStartTime = Instant.now()
                    activeSessionPlannedSeconds = selectedTimeInSeconds

                    if (!serviceStartedDuringPrep) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }

                        try {
                            context.startForegroundService(buildFreshStartIntent(0))
                        } catch (e: Exception) {
                            // Avvio da background non consentito: il timer continua comunque nella UI
                            e.printStackTrace()
                        }
                    }
                } else {
                    // Ripresa dopo pausa (alla pausa il service è stato fermato): riparte dal tempo rimanente
                    val elapsedBeforePause = if (activeSessionPlannedSeconds == -1) {
                        timeLeftInSeconds
                    } else {
                        (activeSessionPlannedSeconds - timeLeftInSeconds).coerceAtLeast(0)
                    }
                    val resumeIntent = Intent(context, StillTimerService::class.java).apply {
                        action = "START"
                        putExtra("RESUME", true)
                        putExtra("ELAPSED_OFFSET", elapsedBeforePause)
                        putExtra("FREQ", gongFreqHz)
                        putExtra("INTERVAL_FREQ", intervalGongFreqHz)
                        putExtra("VOLUME", gongVolumePercent)
                        putExtra("ALERT_MODE", alertMode)
                        putExtra("INTERVAL", intervalMins)
                        putExtra("TIME", if (activeSessionPlannedSeconds == -1) -1 else timeLeftInSeconds)
                        putExtra("DND", autoDndEnabled)
                    }
                    context.startForegroundService(resumeIntent)
                }

                liveAdjustmentSeconds = 0
                val initialSeconds = timeLeftInSeconds
                val startRealtime = SystemClock.elapsedRealtime()

                while (isTimerRunning) {
                    delay(500L)
                    if (!isTimerRunning) break
                    
                    val elapsedSeconds = ((SystemClock.elapsedRealtime() - startRealtime) / 1000L).toInt()

                    if (activeSessionPlannedSeconds == -1) {
                        timeLeftInSeconds = initialSeconds + elapsedSeconds
                    } else if (!isOvertime) {
                        val remaining = (initialSeconds + liveAdjustmentSeconds) - elapsedSeconds
                        if (remaining > 0) {
                            timeLeftInSeconds = remaining
                        } else {
                            timeLeftInSeconds = 0
                            isOvertime = true
                            overtimeSeconds = 0
                        }
                    } else {
                        val overtimeStartRealtime = startRealtime + ((initialSeconds + liveAdjustmentSeconds) * 1000L)
                        val extraSec = ((SystemClock.elapsedRealtime() - overtimeStartRealtime) / 1000L).toInt()
                        overtimeSeconds = extraSec.coerceAtLeast(0)
                    }
                }
            } finally {
                // Background service handles DND and WakeLock cleanup
            }
        } else {
            isPreparing = false
            liveAdjustmentSeconds = 0
            if (StillTimerService.isRunning) {
                val stopIntent = Intent(context, StillTimerService::class.java).apply {
                    action = "STOP"
                }
                context.startService(stopIntent)
            }
        }
    }

    LaunchedEffect(selectedTimeInSeconds) {
        if (!isTimerRunning && sessionStartTime == null && !isOvertime) {
            activeSessionPlannedSeconds = selectedTimeInSeconds
            liveAdjustmentSeconds = 0
            timeLeftInSeconds = if (selectedTimeInSeconds == -1) 0 else selectedTimeInSeconds
            isOvertime = false
            overtimeSeconds = 0
        }
    }

    val isInitialState = !isTimerRunning && !isOvertime && sessionStartTime == null

    fun resetTimerState() {
        cancelGongVibration(context)
        if (StillTimerService.isRunning) {
            val stopIntent = Intent(context, StillTimerService::class.java).apply {
                action = "STOP"
            }
            context.startService(stopIntent)
        }
        isTimerRunning = false
        isPreparing = false
        isOvertime = false
        overtimeSeconds = 0
        hideTimerNumbers = false
        activeSessionPlannedSeconds = selectedTimeInSeconds
        liveAdjustmentSeconds = 0
        timeLeftInSeconds = if (selectedTimeInSeconds == -1) 0 else selectedTimeInSeconds
        sessionStartTime = null
    }

    val toastResyncDone = stringResource(R.string.toast_health_resync_done)
    val toastHealthDenied = stringResource(R.string.toast_health_permission_denied)

    fun saveAndFinishSession(includeExtra: Boolean) {
        val plannedSec = if (activeSessionPlannedSeconds > 0) {
            if (isOvertime) activeSessionPlannedSeconds else (activeSessionPlannedSeconds - timeLeftInSeconds).coerceAtLeast(0)
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

    val isCompactHeight = LocalConfiguration.current.screenHeightDp < 640
    val circleSize = if (isCompactHeight) 180.dp else 250.dp
    val hourglassWidth = if (isCompactHeight) 130.dp else 185.dp
    val hourglassHeight = if (isCompactHeight) 168.dp else 240.dp
    val stillColors = LocalStillColors.current
    val bgColor = MaterialTheme.colorScheme.background
    val atmosphereTransition = rememberInfiniteTransition(label = "natural_atmosphere")
    val breathPhase by atmosphereTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breath_phase"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .then(if (keepScreenAwake) Modifier.keepScreenOn() else Modifier)
    ) {
        // --- SFONDO ATMOSFERICO NATURALE ---
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(stillColors.skyTop, bgColor, stillColors.skyBottom)
                )
            )
            val activeBoost = if (isTimerRunning && !isPreparing) 1.35f else 0.85f
            val primaryGlowColor = if (isOvertime) overtimeColor else stillColors.auroraPrimary
            val auroraCenter = Offset(
                x = size.width * (0.46f + 0.08f * breathPhase),
                y = size.height * (0.43f + 0.04f * (1f - breathPhase))
            )
            val auroraRadius = size.minDimension * 0.88f
            if (auroraRadius > 0f) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            primaryGlowColor.copy(alpha = (0.14f + 0.06f * breathPhase) * activeBoost),
                            stillColors.auroraSecondary.copy(alpha = (0.06f + 0.04f * (1f - breathPhase)) * activeBoost),
                            Color.Transparent
                        ),
                        center = auroraCenter,
                        radius = auroraRadius
                    ),
                    radius = auroraRadius,
                    center = auroraCenter
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = if (isCompactHeight) 10.dp else 24.dp),
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
                    color = stillColors.titleColor
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
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                            shape = RoundedCornerShape(24.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
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
                        Text("⚙", fontSize = 19.sp, color = stillColors.titleColor)
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
                        Text("≡", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = stillColors.titleColor)
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
                        Text("i", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = stillColors.titleColor)
                    }
                }
            }

            // --- AREA CENTRALE FISSA (NON SALTA MAI QUANDO PARTE IL TIMER) ---
            val initialLabelText = if (!isInitialState && !isPreparing && selectedTimeInSeconds > 0) {
                if (activeSessionPlannedSeconds != selectedTimeInSeconds && activeSessionPlannedSeconds > 0) {
                    stringResource(
                        R.string.timer_initial_adjusted_format,
                        formatTime(selectedTimeInSeconds),
                        formatTime(activeSessionPlannedSeconds)
                    )
                } else {
                    stringResource(R.string.timer_initial_format, formatTime(selectedTimeInSeconds))
                }
            } else {
                null
            }

            val ambientGlowAlpha = if (isTimerRunning && !isPreparing) {
                val glowTransition = rememberInfiniteTransition(label = "ambient_glow")
                val animatedAlpha by glowTransition.animateFloat(
                    initialValue = 0.07f,
                    targetValue = 0.16f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 3800, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "ambient_glow_alpha"
                )
                animatedAlpha
            } else {
                0.08f
            }
            val ambientGlowColor = if (isOvertime) overtimeColor else MaterialTheme.colorScheme.primary

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val glowRadius = size.minDimension * 0.62f
                    if (glowRadius > 0f) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    ambientGlowColor.copy(alpha = ambientGlowAlpha),
                                    ambientGlowColor.copy(alpha = ambientGlowAlpha * 0.35f),
                                    Color.Transparent
                                ),
                                center = center,
                                radius = glowRadius
                            ),
                            radius = glowRadius,
                            center = center
                        )
                    }
                }
                if (timerStyle == 1 || timerStyle == 2) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            if (isTimerRunning || isOvertime) {
                                hideTimerNumbers = !hideTimerNumbers
                            }
                        }
                    ) {
                        val remainingFraction = when {
                            isPreparing -> 1f
                            isOvertime -> if (timerStyle == 2) 0.10f else 0f
                            selectedTimeInSeconds == -1 -> when {
                                timerStyle == 2 -> 0.75f
                                isInitialState -> 1f
                                else -> (1f - (timeLeftInSeconds.coerceAtMost(60) / 120f))
                            }
                            else -> progress.coerceIn(0f, 1f)
                        }
                        val isActiveFlow = isTimerRunning && !isPreparing && !isOvertime

                        if (timerStyle == 1) {
                            AutumnHourglassGraphic(
                                topSandFraction = remainingFraction,
                                isFlowing = isActiveFlow,
                                isOvertime = isOvertime,
                                isPreparing = isPreparing,
                                modifier = Modifier.size(width = hourglassWidth, height = hourglassHeight)
                            )
                        } else {
                            CandleTimerGraphic(
                                waxFraction = remainingFraction,
                                isRunning = isTimerRunning,
                                isOvertime = isOvertime,
                                isPreparing = isPreparing,
                                modifier = Modifier.size(width = hourglassWidth, height = hourglassHeight)
                            )
                        }

                        Spacer(modifier = Modifier.height(if (isCompactHeight) 4.dp else 10.dp))

                        if (isPreparing) {
                            Text(
                                text = "${stringResource(R.string.text_prepare)} ${prepTimeLeftSeconds}s",
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        } else if (isOvertime) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.alpha(if (hideTimerNumbers) 0f else 1f)
                            ) {
                                Text(
                                    text = "+${formatTime(overtimeSeconds)}",
                                    maxLines = 1,
                                    softWrap = false,
                                    style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
                                    color = overtimeColor
                                )
                                if (initialLabelText != null) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = initialLabelText,
                                        maxLines = 1,
                                        softWrap = false,
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                                    )
                                }
                            }
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.alpha(if (hideTimerNumbers && isTimerRunning) 0f else 1f)
                            ) {
                                Text(
                                    text = if (selectedTimeInSeconds == -1 && !isTimerRunning && timeLeftInSeconds == 0) {
                                        "∞"
                                    } else {
                                        formatTime(timeLeftInSeconds)
                                    },
                                    maxLines = 1,
                                    softWrap = false,
                                    style = MaterialTheme.typography.displayMedium.copy(
                                        fontWeight = FontWeight.Light,
                                        color = stillColors.timerDigitsColor
                                    )
                                )
                                if (initialLabelText != null) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = initialLabelText,
                                        maxLines = 1,
                                        softWrap = false,
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(circleSize)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                if (isTimerRunning || isOvertime) {
                                    hideTimerNumbers = !hideTimerNumbers
                                }
                            }
                    ) {
                        ZenWheelTimerGraphic(
                            isRunning = isTimerRunning,
                            isInitialState = isInitialState,
                            isOvertime = isOvertime,
                            isPreparing = isPreparing,
                            modifier = Modifier.size(circleSize)
                        )

                        if (isPreparing) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(horizontal = if (isCompactHeight) 36.dp else 48.dp)
                            ) {
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
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .padding(horizontal = if (isCompactHeight) 36.dp else 48.dp)
                                    .alpha(if (hideTimerNumbers) 0f else 1f)
                            ) {
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
                                        fontSize = if (overtimeSeconds >= 6000) 36.sp else 42.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = overtimeColor
                                )
                                if (initialLabelText != null) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = initialLabelText,
                                        maxLines = 1,
                                        softWrap = false,
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                                    )
                                }
                            }
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .padding(horizontal = if (isCompactHeight) 36.dp else 48.dp)
                                    .alpha(if (hideTimerNumbers && isTimerRunning) 0f else 1f)
                            ) {
                                Text(
                                    text = if (selectedTimeInSeconds == -1 && !isTimerRunning && timeLeftInSeconds == 0) {
                                        "∞"
                                    } else {
                                        formatTime(timeLeftInSeconds)
                                    },
                                    maxLines = 1,
                                    softWrap = false,
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.displayLarge.copy(
                                        fontSize = when {
                                            selectedTimeInSeconds == -1 && !isTimerRunning && timeLeftInSeconds == 0 -> 72.sp
                                            timeLeftInSeconds >= 6000 -> 38.sp
                                            else -> 44.sp
                                        },
                                        fontWeight = FontWeight.Light,
                                        color = stillColors.timerDigitsColor,
                                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                                    )
                                )
                                if (initialLabelText != null) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = initialLabelText,
                                        maxLines = 1,
                                        softWrap = false,
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // --- CONTROLLI E IMPOSTAZIONI TEMPO (ALTEZZA ADATTIVA) ---
            val canShowTimeRow = isInitialState || (activeSessionPlannedSeconds > 0 && !isOvertime && !isPreparing)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = if (isCompactHeight) 96.dp else 130.dp)
                    .padding(bottom = 12.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AnimatedVisibility(
                        visible = canShowTimeRow,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val isInfinity = isInitialState && selectedTimeInSeconds == -1
                            val currentBasisSeconds = if (isInitialState) selectedTimeInSeconds else timeLeftInSeconds
                            val stepAutoSize = TextAutoSize.StepBased(
                                minFontSize = 7.sp,
                                maxFontSize = 14.sp,
                                stepSize = 0.5.sp
                            )
                            val infinityAutoSize = TextAutoSize.StepBased(
                                minFontSize = 8.sp,
                                maxFontSize = 18.sp,
                                stepSize = 0.5.sp
                            )

                            val stepButtonColors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.tertiary
                            )
                            val stepButtonBorder = BorderStroke(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.45f)
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(IntrinsicSize.Min),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        if (isInitialState) {
                                            if (selectedTimeInSeconds > 300) updateTimeSelection(selectedTimeInSeconds - 300)
                                        } else {
                                            adjustActiveTimer(-300)
                                        }
                                    },
                                    enabled = currentBasisSeconds > 300,
                                    colors = stepButtonColors,
                                    border = stepButtonBorder,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "-5m",
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis,
                                        autoSize = stepAutoSize
                                    )
                                }

                                OutlinedButton(
                                    onClick = {
                                        if (isInitialState) {
                                            if (selectedTimeInSeconds > 60) updateTimeSelection(selectedTimeInSeconds - 60)
                                        } else {
                                            adjustActiveTimer(-60)
                                        }
                                    },
                                    enabled = currentBasisSeconds > 60,
                                    colors = stepButtonColors,
                                    border = stepButtonBorder,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "-1m",
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis,
                                        autoSize = stepAutoSize
                                    )
                                }

                                val onInfinityClick = {
                                    if (isInfinity) {
                                        updateTimeSelection(lastTimedSeconds)
                                    } else {
                                        updateTimeSelection(-1)
                                    }
                                }
                                val infinityLabel: @Composable () -> Unit = {
                                    Text(
                                        text = "∞",
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis,
                                        autoSize = infinityAutoSize,
                                        style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                                    )
                                }

                                if (isInfinity) {
                                    Button(
                                        onClick = onInfinityClick,
                                        enabled = isInitialState,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.tertiary,
                                            contentColor = MaterialTheme.colorScheme.onTertiary
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight(),
                                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp)
                                    ) { infinityLabel() }
                                } else {
                                    OutlinedButton(
                                        onClick = onInfinityClick,
                                        enabled = isInitialState,
                                        colors = stepButtonColors,
                                        border = stepButtonBorder,
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight(),
                                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp)
                                    ) { infinityLabel() }
                                }

                                OutlinedButton(
                                    onClick = {
                                        if (isInitialState) {
                                            updateTimeSelection(if (isInfinity) 60 else selectedTimeInSeconds + 60)
                                        } else {
                                            adjustActiveTimer(60)
                                        }
                                    },
                                    colors = stepButtonColors,
                                    border = stepButtonBorder,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "+1m",
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis,
                                        autoSize = stepAutoSize
                                    )
                                }

                                OutlinedButton(
                                    onClick = {
                                        if (isInitialState) {
                                            updateTimeSelection(if (isInfinity) 300 else selectedTimeInSeconds + 300)
                                        } else {
                                            adjustActiveTimer(300)
                                        }
                                    },
                                    colors = stepButtonColors,
                                    border = stepButtonBorder,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "+5m",
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis,
                                        autoSize = stepAutoSize
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(if (isInitialState) 24.dp else 14.dp))
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
                                    .heightIn(min = 52.dp)
                                    .fillMaxWidth(0.85f),
                                colors = ButtonDefaults.buttonColors(containerColor = overtimeColor)
                            ) {
                                Text(
                                    text = stringResource(R.string.button_save_with_extra, (overtimeSeconds / 60)),
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color.Black
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth(0.85f)
                                    .height(IntrinsicSize.Min),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = { resetTimerState() },
                                    shape = RoundedCornerShape(20.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.button_reset),
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                OutlinedButton(
                                    onClick = { saveAndFinishSession(includeExtra = false) },
                                    shape = RoundedCornerShape(20.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.button_save_planned),
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
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
                                .heightIn(min = 54.dp)
                                .fillMaxWidth(0.80f),
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
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium)
                            )
                        }

                        if (!isInitialState && !isTimerRunning) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth(0.85f)
                                    .height(IntrinsicSize.Min),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = { resetTimerState() },
                                    shape = RoundedCornerShape(20.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.button_reset),
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Button(
                                    onClick = { 
                                        saveAndFinishSession(includeExtra = false)
                                    },
                                    shape = RoundedCornerShape(20.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.button_save_and_reset),
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
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
            val isSoundEnabled = alertMode != ALERT_MODE_VIBRATION
            var previewGongJob by remember { mutableStateOf<Job?>(null) }

            DisposableEffect(Unit) {
                onDispose {
                    previewGongJob?.cancel()
                }
            }

            fun playPreviewGong(freq: Double, vol: Int = gongVolumePercent) {
                gongFreqHz = freq
                saveGongFrequency(context, freq)
                previewGongJob?.cancel()
                previewGongJob = coroutineScope.launch(Dispatchers.Default) {
                    playGongSound(context, freq, repeatCount = 1, volumePercent = vol)
                }
            }

            fun playPreviewIntervalGong(freq: Double, vol: Int = gongVolumePercent) {
                intervalGongFreqHz = freq
                saveIntervalGongFrequency(context, freq)
                previewGongJob?.cancel()
                previewGongJob = coroutineScope.launch(Dispatchers.Default) {
                    playGongSound(context, freq, repeatCount = 1, volumePercent = vol)
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
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
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

                                if (intervalMins > 0) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    FlowRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        FilterChip(
                                            selected = intervalGongFreqHz == 216.0,
                                            onClick = { playPreviewIntervalGong(216.0) },
                                            enabled = isSoundEnabled,
                                            label = { Text(stringResource(R.string.bell_name_gong), fontSize = 11.sp) }
                                        )
                                        FilterChip(
                                            selected = intervalGongFreqHz == 432.0,
                                            onClick = { playPreviewIntervalGong(432.0) },
                                            enabled = isSoundEnabled,
                                            label = { Text(stringResource(R.string.bell_name_crystal), fontSize = 11.sp) }
                                        )
                                        FilterChip(
                                            selected = intervalGongFreqHz == 528.0,
                                            onClick = { playPreviewIntervalGong(528.0) },
                                            enabled = isSoundEnabled,
                                            label = { Text(stringResource(R.string.bell_name_keisu), fontSize = 11.sp) }
                                        )
                                        FilterChip(
                                            selected = intervalGongFreqHz == TINGSHA_FREQ_HZ,
                                            onClick = { playPreviewIntervalGong(TINGSHA_FREQ_HZ) },
                                            enabled = isSoundEnabled,
                                            label = { Text(stringResource(R.string.bell_name_tingsha), fontSize = 11.sp) }
                                        )
                                    }
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
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    FilterChip(
                                        selected = gongFreqHz == 216.0,
                                        onClick = { playPreviewGong(216.0) },
                                        enabled = isSoundEnabled,
                                        label = { Text(stringResource(R.string.bell_name_gong), fontSize = 12.sp) }
                                    )
                                    FilterChip(
                                        selected = gongFreqHz == 432.0,
                                        onClick = { playPreviewGong(432.0) },
                                        enabled = isSoundEnabled,
                                        label = { Text(stringResource(R.string.bell_name_crystal), fontSize = 12.sp) }
                                    )
                                    FilterChip(
                                        selected = gongFreqHz == 528.0,
                                        onClick = { playPreviewGong(528.0) },
                                        enabled = isSoundEnabled,
                                        label = { Text(stringResource(R.string.bell_name_keisu), fontSize = 12.sp) }
                                    )
                                    FilterChip(
                                        selected = gongFreqHz == TINGSHA_FREQ_HZ,
                                        onClick = { playPreviewGong(TINGSHA_FREQ_HZ) },
                                        enabled = isSoundEnabled,
                                        label = { Text(stringResource(R.string.bell_name_tingsha), fontSize = 12.sp) }
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                                Spacer(modifier = Modifier.height(10.dp))

                                Text(
                                    text = stringResource(R.string.settings_gong_volume),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            if (gongVolumePercent > 20) {
                                                val newVol = (gongVolumePercent - 10).coerceAtLeast(20)
                                                gongVolumePercent = newVol
                                                saveGongVolume(context, newVol)
                                                playPreviewGong(gongFreqHz, newVol)
                                            }
                                        },
                                        enabled = isSoundEnabled && gongVolumePercent > 20,
                                        modifier = Modifier.size(width = 48.dp, height = 36.dp),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("−", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                    }

                                    Text(
                                        text = "$gongVolumePercent%",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSoundEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                        modifier = Modifier.padding(horizontal = 24.dp)
                                    )

                                    OutlinedButton(
                                        onClick = {
                                            if (gongVolumePercent < 100) {
                                                val newVol = (gongVolumePercent + 10).coerceAtMost(100)
                                                gongVolumePercent = newVol
                                                saveGongVolume(context, newVol)
                                                playPreviewGong(gongFreqHz, newVol)
                                            }
                                        },
                                        enabled = isSoundEnabled && gongVolumePercent < 100,
                                        modifier = Modifier.size(width = 48.dp, height = 36.dp),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("+", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                                Spacer(modifier = Modifier.height(10.dp))

                                Text(
                                    text = stringResource(R.string.settings_alert_mode),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf(
                                        ALERT_MODE_BOTH to stringResource(R.string.alert_mode_both),
                                        ALERT_MODE_SOUND to stringResource(R.string.alert_mode_bell),
                                        ALERT_MODE_VIBRATION to stringResource(R.string.alert_mode_vibrate)
                                    ).forEach { (modeVal, modeLabel) ->
                                        FilterChip(
                                            selected = alertMode == modeVal,
                                            onClick = {
                                                alertMode = modeVal
                                                saveAlertMode(context, modeVal)
                                                if (modeVal == ALERT_MODE_BOTH || modeVal == ALERT_MODE_VIBRATION) {
                                                    triggerHapticGong(context, repeatCount = 1)
                                                }
                                                if (modeVal == ALERT_MODE_VIBRATION) {
                                                    previewGongJob?.cancel()
                                                }
                                            },
                                            label = { Text(modeLabel, fontSize = 12.sp) }
                                        )
                                    }
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
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
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
                                    FilterChip(
                                        selected = timerStyle == 2,
                                        onClick = {
                                            timerStyle = 2
                                            saveTimerStyle(context, 2)
                                        },
                                        label = { Text(stringResource(R.string.settings_timer_style_candle)) }
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                                Spacer(modifier = Modifier.height(10.dp))

                                Text(
                                    text = stringResource(R.string.settings_color_theme),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                val availablePalettes = StillColorPalette.entries
                                var themeMenuExpanded by remember { mutableStateOf(false) }
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    OutlinedButton(
                                        onClick = { themeMenuExpanded = true },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(14.dp)
                                                        .background(selectedPalette.swatchColor, CircleShape)
                                                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                                )
                                                Text(
                                                    text = stringResource(selectedPalette.labelRes),
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                            }
                                            Text(
                                                text = if (themeMenuExpanded) "▲" else "▼",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    DropdownMenu(
                                        expanded = themeMenuExpanded,
                                        onDismissRequest = { themeMenuExpanded = false },
                                        containerColor = MaterialTheme.colorScheme.surface
                                    ) {
                                        availablePalettes.forEach { palette ->
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        text = stringResource(palette.labelRes),
                                                        color = if (selectedPalette == palette) {
                                                            MaterialTheme.colorScheme.primary
                                                        } else {
                                                            MaterialTheme.colorScheme.onSurface
                                                        },
                                                        fontWeight = if (selectedPalette == palette) FontWeight.SemiBold else FontWeight.Normal
                                                    )
                                                },
                                                leadingIcon = {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(14.dp)
                                                            .background(palette.swatchColor, CircleShape)
                                                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                                    )
                                                },
                                                trailingIcon = if (selectedPalette == palette) {
                                                    {
                                                        Text(
                                                            text = "✓",
                                                            color = MaterialTheme.colorScheme.primary,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                } else null,
                                                onClick = {
                                                    onPaletteSelected(palette)
                                                    themeMenuExpanded = false
                                                }
                                            )
                                        }
                                    }
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

        // --- DIALOG STATISTICHE E CALENDARIO CON CANCELLAZIONE ---
        if (showStatsDialog) {
            val totalMinutes = sessionHistoryList.sumOf { it.durationMinutes }
            val totalSessions = sessionHistoryList.size
            val streak = calculateStreakDays(sessionHistoryList)
            val bestStreak = calculateBestStreakDays(sessionHistoryList)
            val zone = remember { ZoneId.systemDefault() }
            val locale = Locale.getDefault()

            val sessionsByDate = remember(sessionHistoryList, zone) {
                sessionHistoryList.groupBy {
                    Instant.ofEpochSecond(it.startEpochSec).atZone(zone).toLocalDate()
                }
            }

            var displayedYearMonth by remember { mutableStateOf(YearMonth.now(zone)) }

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
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(stringResource(R.string.stats_minutes), style = MaterialTheme.typography.labelMedium)
                                    Text(stringResource(R.string.stats_min_format, totalMinutes), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                }
                            }

                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(stringResource(R.string.stats_sessions), style = MaterialTheme.typography.labelMedium)
                                    Text("$totalSessions", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                }
                            }

                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 6.dp, vertical = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(stringResource(R.string.stats_streak), style = MaterialTheme.typography.labelMedium)
                                    Text(stringResource(R.string.stats_streak_format, streak), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                    Text(
                                        text = stringResource(R.string.stats_best_streak_format, bestStreak),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // --- CALENDARIO MENSILE ---
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 10.dp)
                            ) {
                                // Barra di navigazione < Mese Anno >
                                val monthTitle = remember(displayedYearMonth, locale) {
                                    val mName = displayedYearMonth.month
                                        .getDisplayName(JavaTextStyle.FULL_STANDALONE, locale)
                                        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
                                    "$mName ${displayedYearMonth.year}"
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = { displayedYearMonth = displayedYearMonth.minusMonths(1) },
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Text(
                                            text = "‹",
                                            fontSize = 22.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    Text(
                                        text = monthTitle,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    IconButton(
                                        onClick = { displayedYearMonth = displayedYearMonth.plusMonths(1) },
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Text(
                                            text = "›",
                                            fontSize = 22.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                // Intestazione Giorni della Settimana (Lun - Dom)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    DayOfWeek.entries.forEach { dow ->
                                        val label = dow
                                            .getDisplayName(JavaTextStyle.SHORT_STANDALONE, locale)
                                            .take(3)
                                            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
                                        Box(
                                            modifier = Modifier.weight(1f),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = label,
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                // Griglia 7 colonne dei giorni del mese
                                val firstOfMonth = displayedYearMonth.atDay(1)
                                val leadingEmptyCells = firstOfMonth.dayOfWeek.value - 1 // Lunedì = 0 .. Domenica = 6
                                val daysInMonth = displayedYearMonth.lengthOfMonth()
                                val totalCells = ((leadingEmptyCells + daysInMonth + 6) / 7) * 7
                                val today = LocalDate.now(zone)

                                for (week in 0 until (totalCells / 7)) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 2.dp),
                                        horizontalArrangement = Arrangement.SpaceEvenly
                                    ) {
                                        for (col in 0..6) {
                                            val cellIndex = week * 7 + col
                                            val dayNumber = cellIndex - leadingEmptyCells + 1
                                            if (dayNumber in 1..daysInMonth) {
                                                val cellDate = displayedYearMonth.atDay(dayNumber)
                                                val daySessions = sessionsByDate[cellDate].orEmpty()
                                                val hasMeditation = daySessions.isNotEmpty()
                                                val dayTotalMins = daySessions.sumOf { it.durationMinutes }
                                                val isToday = cellDate == today

                                                val circleBgColor = when {
                                                    !hasMeditation -> Color.Transparent
                                                    dayTotalMins >= 30 -> MaterialTheme.colorScheme.primary
                                                    dayTotalMins >= 15 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.72f)
                                                    else -> MaterialTheme.colorScheme.primaryContainer
                                                }
                                                val dayTextColor = when {
                                                    !hasMeditation && isToday -> MaterialTheme.colorScheme.primary
                                                    !hasMeditation -> MaterialTheme.colorScheme.onSurface
                                                    dayTotalMins >= 15 -> MaterialTheme.colorScheme.onPrimary
                                                    else -> MaterialTheme.colorScheme.onPrimaryContainer
                                                }

                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .height(36.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(32.dp)
                                                            .background(circleBgColor, CircleShape)
                                                            .then(
                                                                if (hasMeditation) {
                                                                    Modifier.border(
                                                                        width = 1.dp,
                                                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                                                        shape = CircleShape
                                                                    )
                                                                } else if (isToday) {
                                                                    Modifier.border(
                                                                        width = 1.dp,
                                                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                                                                        shape = CircleShape
                                                                    )
                                                                } else {
                                                                    Modifier
                                                                }
                                                            )
                                                            .clickable(enabled = hasMeditation) {
                                                                selectedCalendarDate = cellDate
                                                            },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = "$dayNumber",
                                                            fontSize = 13.sp,
                                                            fontWeight = if (hasMeditation || isToday) FontWeight.Bold else FontWeight.Normal,
                                                            color = dayTextColor,
                                                            style = TextStyle(fontFeatureSettings = "tnum")
                                                        )
                                                    }
                                                }
                                            } else {
                                                Spacer(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .height(36.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (sessionHistoryList.isEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = stringResource(R.string.stats_history_empty),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
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

        // --- POPUP 1: DETTAGLI MEDITAZIONI DEL GIORNO SELEZIONATO ---
        selectedCalendarDate?.let { selectedDate ->
            val zone = remember { ZoneId.systemDefault() }
            val locale = Locale.getDefault()
            val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm", locale) }
            val dateTitleFormatter = remember(locale) {
                DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale)
            }
            val daySessions = remember(sessionHistoryList, selectedDate, zone) {
                sessionHistoryList
                    .filter { Instant.ofEpochSecond(it.startEpochSec).atZone(zone).toLocalDate() == selectedDate }
                    .sortedBy { it.startEpochSec }
            }

            if (daySessions.isEmpty()) {
                LaunchedEffect(Unit) {
                    selectedCalendarDate = null
                }
            } else {
                val dayTotalMinutes = daySessions.sumOf { it.durationMinutes }
                AlertDialog(
                    onDismissRequest = { selectedCalendarDate = null },
                    containerColor = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(24.dp),
                    title = {
                        Column {
                            Text(
                                text = selectedDate.format(dateTitleFormatter)
                                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() },
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (daySessions.size == 1) {
                                    stringResource(R.string.stats_day_single_summary, dayTotalMinutes)
                                } else {
                                    stringResource(R.string.stats_day_total_summary, daySessions.size, dayTotalMinutes)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    text = {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 260.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(daySessions, key = { it.rawString }) { session ->
                                val startStr = Instant.ofEpochSecond(session.startEpochSec).atZone(zone).format(timeFormatter)
                                val endStr = Instant.ofEpochSecond(session.endEpochSec).atZone(zone).format(timeFormatter)

                                Card(
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = stringResource(R.string.stats_min_format, session.durationMinutes),
                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = stringResource(R.string.stats_session_time_range, startStr, endStr),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        IconButton(
                                            onClick = { sessionToDelete = session },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Text(
                                                text = "\uD83D\uDDD1",
                                                fontSize = 16.sp,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = { selectedCalendarDate = null },
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Text(stringResource(R.string.button_close))
                        }
                    }
                )
            }
        }

        // --- POPUP 2: CONFERMA CANCELLAZIONE SINGOLA SESSIONE ---
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
    val stillColors = LocalStillColors.current

    // 1A. Inerzia fisica: anima le variazioni improvvise di progress (es. -1m / +1m) con molla morbida
    val animatedTopSand by animateFloatAsState(
        targetValue = topSandFraction.coerceIn(0f, 1f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "hourglass_sand_inertia"
    )

    // 3. Pool pre-allocato (FloatArray) di 40 granelli indipendenti (zero allocazioni dentro DrawScope)
    // Struttura per granello (8 float ciascuno = 320 float totali):
    // [0] progress: < 0f inattivo, 0f..1f caduta in aria, 1f..1.35f scivolamento lungo il pendio del cono
    // [1] fallDuration: durata di caduta lenta tra 1.8s e 2.6s
    // [2] neckOffsetNorm: piccolo offset orizzontale iniziale nel collo (-1f..+1f)
    // [3] fanDirectionNorm: apertura a ventaglio/cono verso l'esterno (-1f..+1f)
    // [4] swayPhase: fase individuale dell'oscillazione sinusoidale (corrente d'aria)
    // [5] swayFreq: frequenza individuale dell'oscillazione sinusoidale
    // [6] radiusDp: raggio individuale tra 1.0.dp e 2.2.dp
    // [7] baseAlpha: opacità individuale (i granelli >= 0.88f usano BlendMode.Plus)
    val grainCount = 40
    val stride = 8
    val grainPool = remember {
        FloatArray(grainCount * stride).apply {
            for (i in 0 until grainCount) {
                val b = i * stride
                val h1 = ((i * 73 + 19) % 100) / 100f
                val h2 = ((i * 41 + 53) % 100) / 100f
                val h3 = ((i * 89 + 11) % 100) / 100f
                val h4 = ((i * 31 + 67) % 100) / 100f
                this[b + 0] = -1f // parte inattivo
                this[b + 1] = 1.8f + h1 * 0.8f // 1.8s .. 2.6s
                this[b + 2] = (h2 - 0.5f) * 1.7f // offset nel collo
                val side = if (i % 2 == 0) 1f else -1f
                this[b + 3] = side * (0.12f + h3 * 0.88f) // apertura a ventaglio/cono
                this[b + 4] = h4 * 6.2831855f // fase
                this[b + 5] = 2.0f + h1 * 2.4f // frequenza corrente d'aria
                this[b + 6] = 1.0f + h2 * 1.2f // raggio 1.0.dp .. 2.2.dp
                this[b + 7] = 0.56f + h3 * 0.42f // opacità
            }
        }
    }

    // Stato per tenere vivo il loop finché ci sono granelli ancora in aria dopo la pausa
    val effectiveFlowing = isFlowing && !isPreparing
    var hasGrainsInFlight by remember { mutableStateOf(false) }
    var frameTick by remember { mutableStateOf(0f) }

    // 1B. Loop withFrameNanos limitato a ~40 fps (salta il frame se frameNanos - lastFrameNanos < 25_000_000L)
    LaunchedEffect(effectiveFlowing, hasGrainsInFlight) {
        if (!effectiveFlowing && !hasGrainsInFlight) return@LaunchedEffect
        var lastFrameNanos = -1L
        var spawnAccumulator = 0f
        var nextSpawnIndex = 0

        while (effectiveFlowing || hasGrainsInFlight) {
            androidx.compose.runtime.withFrameNanos { frameNanos ->
                if (lastFrameNanos < 0L) {
                    lastFrameNanos = frameNanos
                } else if (frameNanos - lastFrameNanos >= 25_000_000L) {
                    val dt = ((frameNanos - lastFrameNanos).coerceAtMost(100_000_000L)).toFloat() / 1_000_000_000f
                    lastFrameNanos = frameNanos

                    // Quando effectiveFlowing == true emette nuovi granelli dal collo; in pausa smette di emetterne
                    if (effectiveFlowing) {
                        spawnAccumulator += dt * 17.5f // ~17.5 granelli/sec -> ~38-40 granelli attivi contemporaneamente
                        while (spawnAccumulator >= 1f) {
                            spawnAccumulator -= 1f
                            for (attempt in 0 until grainCount) {
                                val candidate = (nextSpawnIndex + attempt) % grainCount
                                val base = candidate * stride
                                if (grainPool[base] < 0f) {
                                    grainPool[base] = 0f
                                    nextSpawnIndex = (candidate + 1) % grainCount
                                    break
                                }
                            }
                        }
                    }

                    var anyActive = false
                    for (i in 0 until grainCount) {
                        val base = i * stride
                        val p = grainPool[base]
                        if (p >= 0f) {
                            val fallDur = grainPool[base + 1]
                            val nextP = if (p < 1f) {
                                p + dt / fallDur
                            } else {
                                // Fase di scivolamento lungo il pendio del cono inferiore (~0.45s)
                                p + dt * 0.78f
                            }
                            if (nextP >= 1.35f) {
                                grainPool[base] = -1f // ricicla il granello
                            } else {
                                grainPool[base] = nextP
                                anyActive = true
                            }
                        }
                    }
                    hasGrainsInFlight = anyActive
                    frameTick = (frameTick + dt) % 3600f
                }
            }
        }
    }

    val topSpeckOffsets = remember {
        floatArrayOf(
            -0.22f, 0.25f,  0.18f, 0.32f, -0.10f, 0.48f,  0.26f, 0.55f,
            -0.28f, 0.62f,  0.06f, 0.70f, -0.14f, 0.78f,  0.15f, 0.85f
        )
    }
    val bottomSpeckOffsets = remember {
        floatArrayOf(
            -0.08f, 0.22f,  0.12f, 0.28f, -0.20f, 0.42f,  0.22f, 0.48f,
            -0.14f, 0.58f,  0.05f, 0.66f, -0.26f, 0.74f,  0.19f, 0.80f,
            -0.06f, 0.88f,  0.28f, 0.90f
        )
    }

    // Path pre-allocati per evitare allocazioni nel DrawScope
    val topBulbPath = remember { Path() }
    val bottomBulbPath = remember { Path() }
    val topSandPath = remember { Path() }
    val moundPath = remember { Path() }
    val ridgePath = remember { Path() }
    val topSpecularHighlight = remember { Path() }
    val bottomSpecularHighlight = remember { Path() }
    val rightRimHighlightTop = remember { Path() }
    val rightRimHighlightBottom = remember { Path() }
    val innerArcTopPath = remember { Path() }
    val innerArcBottomPath = remember { Path() }

    Canvas(modifier = modifier) {
        val tickTime = frameTick
        val w = size.width
        val h = size.height
        val centerX = w / 2f
        val midY = h / 2f

        val capHeight = 12.dp.toPx()
        val innerCollarH = 4.dp.toPx()
        val glassTop = capHeight + innerCollarH
        val glassBottom = h - capHeight - innerCollarH
        val bulbHalfHeight = midY - glassTop

        val bulbShoulderX = w * 0.17f
        val bulbRightX = w * 0.83f
        val bulbMaxLeftX = w * 0.12f
        val bulbMaxRightX = w * 0.88f
        val neckHalf = 4.2.dp.toPx()

        // 1. OMBRIERA DI APPOGGIO SUL PIANO
        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.Black.copy(alpha = 0.55f),
                    Color.Black.copy(alpha = 0.20f),
                    Color.Transparent
                ),
                center = Offset(centerX, h - 2.dp.toPx()),
                radius = w * 0.46f
            ),
            topLeft = Offset(w * 0.06f, h - 9.dp.toPx()),
            size = Size(w * 0.88f, 12.dp.toPx())
        )

        // 2. COLONNINE LATERALI TORNITE 3D
        val pillarWidth = 5.5.dp.toPx()
        val leftPillarX = w * 0.09f
        val rightPillarX = w * 0.91f - pillarWidth
        val pillarTopY = capHeight * 0.7f
        val pillarHeight = h - capHeight * 1.4f

        fun drawTurnedPillar(xLeft: Float) {
            val pillarBrush = Brush.horizontalGradient(
                colors = listOf(
                    stillColors.woodFrame.copy(alpha = 0.70f),
                    Color(0xFFFFE5B4).copy(alpha = 0.65f),
                    stillColors.woodFrame,
                    stillColors.woodFrame.copy(alpha = 0.85f),
                    Color.Black.copy(alpha = 0.65f)
                ),
                startX = xLeft,
                endX = xLeft + pillarWidth
            )
            drawRoundRect(
                brush = pillarBrush,
                topLeft = Offset(xLeft, pillarTopY),
                size = Size(pillarWidth, pillarHeight),
                cornerRadius = CornerRadius(pillarWidth / 2f, pillarWidth / 2f)
            )
            val ringW = pillarWidth + 3.dp.toPx()
            val ringX = xLeft - 1.5.dp.toPx()
            val ringH = 4.dp.toPx()
            for (ry in floatArrayOf(glassTop + 4.dp.toPx(), midY - ringH / 2f, glassBottom - 4.dp.toPx() - ringH)) {
                drawRoundRect(
                    brush = pillarBrush,
                    topLeft = Offset(ringX, ry),
                    size = Size(ringW, ringH),
                    cornerRadius = CornerRadius(ringH / 2f, ringH / 2f)
                )
            }
        }
        drawTurnedPillar(leftPillarX)
        drawTurnedPillar(rightPillarX)

        // 3. PROFILO IN VETRO SOFFIATO DELLE DUE AMPOLLE
        topBulbPath.reset()
        topBulbPath.moveTo(bulbShoulderX, glassTop)
        topBulbPath.lineTo(bulbRightX, glassTop)
        topBulbPath.cubicTo(
            bulbMaxRightX, glassTop + bulbHalfHeight * 0.36f,
            bulbMaxRightX * 0.96f, glassTop + bulbHalfHeight * 0.74f,
            centerX + neckHalf, midY
        )
        topBulbPath.lineTo(centerX - neckHalf, midY)
        topBulbPath.cubicTo(
            bulbMaxLeftX * 1.04f, glassTop + bulbHalfHeight * 0.74f,
            bulbMaxLeftX, glassTop + bulbHalfHeight * 0.36f,
            bulbShoulderX, glassTop
        )
        topBulbPath.close()

        bottomBulbPath.reset()
        bottomBulbPath.moveTo(centerX - neckHalf, midY)
        bottomBulbPath.lineTo(centerX + neckHalf, midY)
        bottomBulbPath.cubicTo(
            bulbMaxRightX * 0.96f, midY + bulbHalfHeight * 0.26f,
            bulbMaxRightX, midY + bulbHalfHeight * 0.64f,
            bulbRightX, glassBottom
        )
        bottomBulbPath.lineTo(bulbShoulderX, glassBottom)
        bottomBulbPath.cubicTo(
            bulbMaxLeftX, midY + bulbHalfHeight * 0.64f,
            bulbMaxLeftX * 1.04f, midY + bulbHalfHeight * 0.26f,
            centerX - neckHalf, midY
        )
        bottomBulbPath.close()

        // 4. CORPO DEL VETRO 3D
        val glassBodyBrush = Brush.horizontalGradient(
            colors = listOf(
                stillColors.glassOutline.copy(alpha = 0.26f),
                Color.White.copy(alpha = 0.08f),
                Color.Transparent,
                stillColors.glassOutline.copy(alpha = 0.06f),
                stillColors.glassOutline.copy(alpha = 0.28f)
            ),
            startX = bulbMaxLeftX,
            endX = bulbMaxRightX
        )
        drawPath(path = topBulbPath, brush = glassBodyBrush)
        drawPath(path = bottomBulbPath, brush = glassBodyBrush)

        // 5 (Punto 2 Specifica). SABBIA CALDA E GRANULARE DEL TEMA STAGIONALE (Nessuna sfumatura fredda grigio-azzurra)
        val sandPrimaryLight = if (isOvertime) stillColors.overtime else stillColors.sandLight
        val sandPrimaryDeep = if (isOvertime) stillColors.sandLight else stillColors.sandDeep
        val sandWarmHighlight = Color(
            red = (sandPrimaryLight.red * 0.65f + 0.35f).coerceIn(0f, 1f),
            green = (sandPrimaryLight.green * 0.65f + 0.32f).coerceIn(0f, 1f),
            blue = (sandPrimaryLight.blue * 0.65f + 0.22f).coerceIn(0f, 1f),
            alpha = 1f
        )
        val sandWarmShadow = Color(
            red = (sandPrimaryDeep.red * 0.52f).coerceIn(0f, 1f),
            green = (sandPrimaryDeep.green * 0.48f).coerceIn(0f, 1f),
            blue = (sandPrimaryDeep.blue * 0.42f).coerceIn(0f, 1f),
            alpha = 1f
        )

        val sand3DBrush = Brush.horizontalGradient(
            colorStops = arrayOf(
                0.00f to sandPrimaryDeep,
                0.24f to sandWarmHighlight, // Lato illuminato caldo a sinistra
                0.52f to sandPrimaryLight,  // Centro caldo e granulare (zero grigio/azzurro)
                0.80f to sandPrimaryDeep,
                1.00f to sandWarmShadow     // Ombreggiatura morbida calda sul lato opposto alla luce
            ),
            startX = bulbMaxLeftX,
            endX = bulbMaxRightX
        )

        val clampedTop = animatedTopSand.coerceIn(0f, 1f)
        val bulbHalfSpanX = (bulbMaxRightX - bulbMaxLeftX) * 0.5f

        // 5A. Sabbia nell'ampolla superiore con concavità centrale a imbuto tramite Path.quadraticTo
        if (clampedTop > 0.004f) {
            val sandTopY = midY - bulbHalfHeight * (0.10f + 0.85f * clampedTop)
            // L'imbuto centrale si approfondisce man mano che la sabbia si svuota
            val emptyingCurve = kotlin.math.sin(clampedTop * Math.PI).toFloat()
            val funnelDepth = if (isFlowing || hasGrainsInFlight) {
                (15.dp.toPx() * emptyingCurve + 4.dp.toPx() * (1f - clampedTop)).coerceAtMost(midY - sandTopY - 2.dp.toPx()).coerceAtLeast(2.dp.toPx())
            } else {
                (5.dp.toPx() * (1f - clampedTop) * clampedTop).coerceAtLeast(1.5.dp.toPx())
            }

            topSandPath.reset()
            topSandPath.moveTo(bulbMaxLeftX - 4f, sandTopY)
            topSandPath.quadraticTo(
                centerX,
                sandTopY + funnelDepth * 1.85f,
                bulbMaxRightX + 4f,
                sandTopY
            )
            topSandPath.lineTo(bulbMaxRightX + 4f, midY + 2f)
            topSandPath.lineTo(bulbMaxLeftX - 4f, midY + 2f)
            topSandPath.close()

            clipPath(topBulbPath) {
                drawPath(path = topSandPath, brush = sand3DBrush)

                // Ombra interna calda nella conca dell'imbuto superiore
                drawOval(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            sandWarmShadow.copy(alpha = 0.55f),
                            sandPrimaryDeep.copy(alpha = 0.35f),
                            Color.Transparent
                        ),
                        center = Offset(centerX, sandTopY + funnelDepth * 0.65f),
                        radius = bulbHalfSpanX * 0.78f
                    ),
                    topLeft = Offset(bulbMaxLeftX, sandTopY - 3.dp.toPx()),
                    size = Size(bulbMaxRightX - bulbMaxLeftX, funnelDepth * 1.5f + 6.dp.toPx())
                )

                // Texture minerale granulare calda nel bulbo superiore
                for (s in topSpeckOffsets.indices step 2) {
                    val sx = centerX + topSpeckOffsets[s] * (bulbMaxRightX - bulbMaxLeftX)
                    val sy = sandTopY + funnelDepth * 0.55f + topSpeckOffsets[s + 1] * (midY - sandTopY)
                    if (sy < midY - 3.dp.toPx()) {
                        drawCircle(
                            color = if ((s / 2) % 2 == 0) sandWarmHighlight.copy(alpha = 0.55f) else sandWarmShadow.copy(alpha = 0.35f),
                            radius = 1.15.dp.toPx(),
                            center = Offset(sx, sy)
                        )
                    }
                }
            }
        }

        // 5B. Sabbia nell'ampolla inferiore: montagnetta conica centrale (~30° = tan(30°) ≈ 0.577) con Path.quadraticTo
        val bottomFraction = if (isOvertime) 1f else (1f - clampedTop).coerceIn(0f, 1f)
        val tan30 = 0.577f
        val maxConeDrop = bulbHalfSpanX * tan30
        val coneSlopeDrop = maxConeDrop * (0.35f + 0.65f * kotlin.math.sin(bottomFraction * Math.PI * 0.85f).toFloat())

        val sideWallY = if (bottomFraction > 0.004f) {
            (glassBottom - bulbHalfHeight * (bottomFraction * 0.66f)).coerceAtLeast(midY + 12.dp.toPx())
        } else {
            glassBottom
        }
        val moundPeakY = if (bottomFraction > 0.004f) {
            (sideWallY - coneSlopeDrop).coerceAtLeast(midY + 5.dp.toPx())
        } else {
            glassBottom
        }

        // Funzione che restituisce la quota Y della superficie del cono inferiore all'ascissa x
        fun coneSurfaceYAt(x: Float): Float {
            if (bottomFraction <= 0.004f) return glassBottom
            val normX = (kotlin.math.abs(x - centerX) / bulbHalfSpanX).coerceIn(0f, 1f)
            return moundPeakY + (sideWallY - moundPeakY) * (normX * normX)
        }

        if (bottomFraction > 0.004f) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        sandPrimaryLight.copy(alpha = 0.22f * bottomFraction),
                        sandPrimaryDeep.copy(alpha = 0.08f * bottomFraction),
                        Color.Transparent
                    ),
                    center = Offset(centerX, glassBottom - 6.dp.toPx()),
                    radius = w * 0.42f
                ),
                radius = w * 0.42f,
                center = Offset(centerX, glassBottom - 6.dp.toPx())
            )

            // Profilo conico inferiore modellato con Path.quadraticTo (inclinazione ~30°)
            val controlPeakY = moundPeakY - (sideWallY - moundPeakY) * 0.38f
            moundPath.reset()
            moundPath.moveTo(bulbMaxLeftX - 4f, glassBottom + 4f)
            moundPath.lineTo(bulbMaxLeftX - 4f, sideWallY)
            moundPath.quadraticTo(
                centerX,
                controlPeakY,
                bulbMaxRightX + 4f,
                sideWallY
            )
            moundPath.lineTo(bulbMaxRightX + 4f, glassBottom + 4f)
            moundPath.close()

            clipPath(bottomBulbPath) {
                drawPath(path = moundPath, brush = sand3DBrush)

                // Strati sedimentari caldi lungo il pendio del cono
                for (layer in 1..3) {
                    val f = layer / 4f
                    val ly = controlPeakY + (glassBottom - controlPeakY) * f
                    val lSideY = sideWallY + (glassBottom - sideWallY) * f
                    ridgePath.reset()
                    ridgePath.moveTo(bulbMaxLeftX, lSideY)
                    ridgePath.quadraticTo(centerX, ly, bulbMaxRightX, lSideY)
                    drawPath(
                        path = ridgePath,
                        color = sandWarmHighlight.copy(alpha = 0.22f),
                        style = Stroke(width = 1.dp.toPx())
                    )
                }

                // Granelli minerali caldi nel cumulo inferiore
                val moundH = (glassBottom - moundPeakY).coerceAtLeast(1f)
                for (s in bottomSpeckOffsets.indices step 2) {
                    val sx = centerX + bottomSpeckOffsets[s] * (bulbMaxRightX - bulbMaxLeftX)
                    val sy = moundPeakY + bottomSpeckOffsets[s + 1] * moundH
                    drawCircle(
                        color = if ((s / 2) % 2 == 0) sandWarmHighlight.copy(alpha = 0.55f) else sandWarmShadow.copy(alpha = 0.35f),
                        radius = 1.15.dp.toPx(),
                        center = Offset(sx, sy)
                    )
                }
            }
        }

        // 6 (Punto 3 Specifica). 40 GRANELLI IPNOTICI INDIPENDENTI CHE CADONO LENTAMENTE, SI SPARPAGLIANO A VENTAGLIO E SCIVOLANO SUL CONO
        if (effectiveFlowing || hasGrainsInFlight) {
            val streamStartY = midY + 1.dp.toPx()
            val maxFanSpreadPx = bulbHalfSpanX * 0.48f
            val airSwayAmpPx = 2.6.dp.toPx()
            val neckSpreadPx = 1.8.dp.toPx()
            val slideSpanPx = bulbHalfSpanX * 0.34f

            clipPath(bottomBulbPath) {
                for (i in 0 until grainCount) {
                    val base = i * stride
                    val p = grainPool[base]
                    if (p < 0f) continue

                    val neckOffset = grainPool[base + 2] * neckSpreadPx
                    val fanDir = grainPool[base + 3]
                    val swayPhase = grainPool[base + 4]
                    val swayFreq = grainPool[base + 5]
                    val radiusPx = grainPool[base + 6].dp.toPx()
                    val baseAlpha = grainPool[base + 7]

                    // Posizione X alla fine della caduta in aria (apertura a ventaglio + oscillazione sinusoidale individuale)
                    val airProgress = p.coerceAtMost(1f)
                    // Leggera accelerazione gravitazionale morbida: 0.45 * t + 0.55 * t^2
                    val fallCurve = 0.45f * airProgress + 0.55f * airProgress * airProgress
                    val airSway = kotlin.math.sin((tickTime * swayFreq + swayPhase).toDouble()).toFloat() *
                        airSwayAmpPx * kotlin.math.sin(airProgress * Math.PI).toFloat()
                    val landingX = centerX + neckOffset + fanDir * maxFanSpreadPx * airProgress + airSway
                    val landingSurfaceY = coneSurfaceYAt(landingX).coerceAtLeast(streamStartY + 8.dp.toPx())

                    val grainX: Float
                    val grainY: Float
                    val alpha: Float

                    if (p <= 1f) {
                        // In volo verso il cumulo conico inferiore
                        grainX = landingX
                        grainY = streamStartY + fallCurve * (landingSurfaceY - streamStartY)
                        alpha = baseAlpha
                    } else {
                        // Ha toccato la superficie del cono: scivola lateralmente lungo il pendio (~30°) mentre sfuma a 0
                        val slideProgress = ((p - 1f) / 0.35f).coerceIn(0f, 1f)
                        val slideDir = if (fanDir >= 0f) 1f else -1f
                        grainX = landingX + slideDir * slideSpanPx * slideProgress
                        grainY = coneSurfaceYAt(grainX)
                        alpha = baseAlpha * (1f - slideProgress)
                    }

                    if (alpha > 0.02f) {
                        val isBrightGrain = baseAlpha >= 0.86f
                        val grainColor = when {
                            isBrightGrain -> sandWarmHighlight.copy(alpha = alpha)
                            i % 2 == 0 -> sandPrimaryLight.copy(alpha = alpha)
                            else -> sandPrimaryDeep.copy(alpha = alpha)
                        }
                        drawCircle(
                            color = grainColor,
                            radius = radiusPx,
                            center = Offset(grainX, grainY),
                            blendMode = if (isBrightGrain) BlendMode.Plus else BlendMode.SrcOver
                        )
                    }
                }
            }
        }

        // 7 (Punto 4 Specifica). DUE SOTTILI ARCHI BIANCHI CURVI A BASSISSIMA OPACITÀ (alpha = 0.12f) LUNGO I BORDI INTERNI DEI BULBI
        val innerInset = 9.dp.toPx()
        innerArcTopPath.reset()
        innerArcTopPath.moveTo(bulbRightX - innerInset * 0.7f, glassTop + 10.dp.toPx())
        innerArcTopPath.quadraticTo(
            bulbMaxRightX - innerInset,
            glassTop + bulbHalfHeight * 0.52f,
            centerX + neckHalf + 4.dp.toPx(),
            midY - 10.dp.toPx()
        )
        drawPath(
            path = innerArcTopPath,
            color = Color.White.copy(alpha = 0.12f),
            style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
        )

        innerArcBottomPath.reset()
        innerArcBottomPath.moveTo(centerX + neckHalf + 4.dp.toPx(), midY + 10.dp.toPx())
        innerArcBottomPath.quadraticTo(
            bulbMaxRightX - innerInset,
            midY + bulbHalfHeight * 0.48f,
            bulbRightX - innerInset * 0.7f,
            glassBottom - 10.dp.toPx()
        )
        drawPath(
            path = innerArcBottomPath,
            color = Color.White.copy(alpha = 0.12f),
            style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
        )

        // 8. RIFLESSI SPECULARI PRINCIPALI DEL VETRO CURVO
        topSpecularHighlight.reset()
        topSpecularHighlight.moveTo(bulbShoulderX + 7.dp.toPx(), glassTop + 8.dp.toPx())
        topSpecularHighlight.cubicTo(
            bulbMaxLeftX + 7.dp.toPx(), glassTop + bulbHalfHeight * 0.35f,
            bulbMaxLeftX + 10.dp.toPx(), glassTop + bulbHalfHeight * 0.68f,
            centerX - neckHalf - 2.dp.toPx(), midY - 6.dp.toPx()
        )
        drawPath(
            path = topSpecularHighlight,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.70f),
                    Color.White.copy(alpha = 0.30f),
                    Color.Transparent
                ),
                startY = glassTop,
                endY = midY
            ),
            style = Stroke(width = 3.2.dp.toPx(), cap = StrokeCap.Round)
        )

        bottomSpecularHighlight.reset()
        bottomSpecularHighlight.moveTo(centerX - neckHalf - 2.dp.toPx(), midY + 8.dp.toPx())
        bottomSpecularHighlight.cubicTo(
            bulbMaxLeftX + 10.dp.toPx(), midY + bulbHalfHeight * 0.32f,
            bulbMaxLeftX + 7.dp.toPx(), midY + bulbHalfHeight * 0.65f,
            bulbShoulderX + 7.dp.toPx(), glassBottom - 8.dp.toPx()
        )
        drawPath(
            path = bottomSpecularHighlight,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = 0.55f),
                    Color.White.copy(alpha = 0.18f)
                ),
                startY = midY,
                endY = glassBottom
            ),
            style = Stroke(width = 3.2.dp.toPx(), cap = StrokeCap.Round)
        )

        rightRimHighlightTop.reset()
        rightRimHighlightTop.moveTo(bulbRightX - 5.dp.toPx(), glassTop + 12.dp.toPx())
        rightRimHighlightTop.cubicTo(
            bulbMaxRightX - 5.dp.toPx(), glassTop + bulbHalfHeight * 0.38f,
            bulbMaxRightX - 8.dp.toPx(), glassTop + bulbHalfHeight * 0.70f,
            centerX + neckHalf + 1.5.dp.toPx(), midY - 8.dp.toPx()
        )
        rightRimHighlightBottom.reset()
        rightRimHighlightBottom.moveTo(centerX + neckHalf + 1.5.dp.toPx(), midY + 10.dp.toPx())
        rightRimHighlightBottom.cubicTo(
            bulbMaxRightX - 8.dp.toPx(), midY + bulbHalfHeight * 0.30f,
            bulbMaxRightX - 5.dp.toPx(), midY + bulbHalfHeight * 0.62f,
            bulbRightX - 5.dp.toPx(), glassBottom - 12.dp.toPx()
        )
        drawPath(
            path = rightRimHighlightTop,
            color = Color.White.copy(alpha = 0.22f),
            style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round)
        )
        drawPath(
            path = rightRimHighlightBottom,
            color = Color.White.copy(alpha = 0.22f),
            style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round)
        )

        // 9. CONTORNO ESTERNO IN CRISTALLO SOFFIATO CON DOPPIO SPESSORE OTTICO
        val glassEdgeBrush = Brush.horizontalGradient(
            colors = if (isOvertime) {
                listOf(stillColors.overtime, Color.White.copy(alpha = 0.85f), stillColors.overtime)
            } else {
                listOf(
                    Color.White.copy(alpha = 0.65f),
                    stillColors.glassOutline.copy(alpha = 0.85f),
                    stillColors.glassOutline.copy(alpha = 0.45f),
                    Color.White.copy(alpha = 0.50f)
                )
            },
            startX = bulbMaxLeftX,
            endX = bulbMaxRightX
        )
        drawPath(
            path = topBulbPath,
            brush = glassEdgeBrush,
            style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
        )
        drawPath(
            path = bottomBulbPath,
            brush = glassEdgeBrush,
            style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
        )

        drawOval(
            color = Color.White.copy(alpha = 0.48f),
            topLeft = Offset(centerX - neckHalf - 1.5.dp.toPx(), midY - 2.5.dp.toPx()),
            size = Size((neckHalf + 1.5.dp.toPx()) * 2f, 5.dp.toPx()),
            style = Stroke(width = 1.4.dp.toPx())
        )
        drawOval(
            color = stillColors.glassOutline.copy(alpha = 0.40f),
            topLeft = Offset(centerX - neckHalf - 3.dp.toPx(), midY - 4.dp.toPx()),
            size = Size((neckHalf + 3.dp.toPx()) * 2f, 8.dp.toPx()),
            style = Stroke(width = 1.dp.toPx())
        )

        // 10. BASI IN LEGNO TORNITO A DOPPIO GRADINO 3D
        val mainCapWidth = w * 0.88f
        val mainCapLeft = (w - mainCapWidth) / 2f
        val innerCollarWidth = w * 0.72f
        val innerCollarLeft = (w - innerCollarWidth) / 2f

        val cap3DBrush = Brush.horizontalGradient(
            colors = listOf(
                stillColors.woodFrame.copy(alpha = 0.80f),
                Color(0xFFFFE5B4).copy(alpha = 0.72f),
                stillColors.woodFrame,
                stillColors.woodFrame.copy(alpha = 0.88f),
                Color.Black.copy(alpha = 0.62f)
            ),
            startX = mainCapLeft,
            endX = mainCapLeft + mainCapWidth
        )

        drawRoundRect(
            brush = cap3DBrush,
            topLeft = Offset(innerCollarLeft, capHeight - 1.dp.toPx()),
            size = Size(innerCollarWidth, innerCollarH + 2.dp.toPx()),
            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
        )
        drawRoundRect(
            brush = cap3DBrush,
            topLeft = Offset(mainCapLeft, 1.dp.toPx()),
            size = Size(mainCapWidth, capHeight),
            cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
        )
        drawLine(
            color = Color.White.copy(alpha = 0.42f),
            start = Offset(mainCapLeft + 6.dp.toPx(), 2.5.dp.toPx()),
            end = Offset(mainCapLeft + mainCapWidth - 6.dp.toPx(), 2.5.dp.toPx()),
            strokeWidth = 1.dp.toPx()
        )
        drawLine(
            color = Color.Black.copy(alpha = 0.22f),
            start = Offset(mainCapLeft + 12.dp.toPx(), 1.dp.toPx() + capHeight * 0.62f),
            end = Offset(mainCapLeft + mainCapWidth - 14.dp.toPx(), 1.dp.toPx() + capHeight * 0.62f),
            strokeWidth = 0.8.dp.toPx()
        )

        drawRoundRect(
            brush = cap3DBrush,
            topLeft = Offset(innerCollarLeft, glassBottom - 1.dp.toPx()),
            size = Size(innerCollarWidth, innerCollarH + 2.dp.toPx()),
            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
        )
        drawRoundRect(
            brush = cap3DBrush,
            topLeft = Offset(mainCapLeft, h - capHeight - 1.dp.toPx()),
            size = Size(mainCapWidth, capHeight),
            cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
        )
        drawLine(
            color = Color.Black.copy(alpha = 0.22f),
            start = Offset(mainCapLeft + 14.dp.toPx(), h - capHeight - 1.dp.toPx() + capHeight * 0.58f),
            end = Offset(mainCapLeft + mainCapWidth - 12.dp.toPx(), h - capHeight - 1.dp.toPx() + capHeight * 0.58f),
            strokeWidth = 0.8.dp.toPx()
        )
        drawLine(
            color = Color.White.copy(alpha = 0.40f),
            start = Offset(mainCapLeft + 6.dp.toPx(), h - capHeight + 0.5.dp.toPx()),
            end = Offset(mainCapLeft + mainCapWidth - 6.dp.toPx(), h - capHeight + 0.5.dp.toPx()),
            strokeWidth = 1.dp.toPx()
        )
    }
}

@Composable
fun ZenWheelTimerGraphic(
    isRunning: Boolean,
    isInitialState: Boolean,
    isOvertime: Boolean,
    isPreparing: Boolean,
    modifier: Modifier = Modifier
) {
    val stillColors = LocalStillColors.current
    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary

    // 3. Loop continuo limitato a ~40 fps (salta il frame se frameNanos - lastFrameNanos < 25_000_000L)
    var accumulatedSeconds by remember { mutableStateOf(0f) }
    var pulseSeconds by remember { mutableStateOf(0f) }
    LaunchedEffect(isInitialState, isPreparing) {
        if (isInitialState || !isPreparing) {
            accumulatedSeconds = 0f
        }
    }
    LaunchedEffect(isRunning) {
        if (isRunning) {
            var lastFrameNanos = -1L
            while (true) {
                androidx.compose.runtime.withFrameNanos { frameNanos ->
                    if (lastFrameNanos < 0L) {
                        lastFrameNanos = frameNanos
                    } else if (frameNanos - lastFrameNanos >= 25_000_000L) {
                        val deltaSec = ((frameNanos - lastFrameNanos) / 1_000_000_000f).coerceIn(0f, 0.25f)
                        lastFrameNanos = frameNanos
                        accumulatedSeconds = (accumulatedSeconds + deltaSec) % 60f
                        pulseSeconds = (pulseSeconds + deltaSec) % 3600f
                    }
                }
            }
        }
    }

    // Interpolazione spring leggera per transizioni morbide del livello/stato dell'arco
    val activeGlowBoost by animateFloatAsState(
        targetValue = when {
            isOvertime -> 1.15f
            isPreparing -> 0.85f
            isRunning -> 1.0f
            else -> 0.65f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "zen_wheel_arc_spring"
    )

    val wheelRotationPhase = (accumulatedSeconds / 60f).coerceIn(0f, 1f)

    Canvas(modifier = modifier) {
        val minDim = size.minDimension
        val c = center
        val outerRimRadius = minDim * 0.475f
        val trackRadius = minDim * 0.422f
        val innerRimRadius = minDim * 0.368f
        val channelWidth = (outerRimRadius - innerRimRadius)

        // Pulsazione organica morbida della brace a ~40 fps
        val emberPulse = 1f + 0.08f * kotlin.math.sin((pulseSeconds * 3.2f).toDouble()).toFloat()

        // 1. ALONE INTERNO E CONCA ZEN AMPIA PER FAR RISALTARE LE CIFRE CENTRALI CON AMPIO MARGINE
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    stillColors.skyBottom.copy(alpha = 0.72f),
                    stillColors.skyTop.copy(alpha = 0.42f),
                    Color.Transparent
                ),
                center = c,
                radius = innerRimRadius * 0.98f
            ),
            radius = innerRimRadius * 0.98f,
            center = c
        )

        // 2. CANALE INCASSATO TRA I DUE ANELLI DI LEGNO
        drawCircle(
            color = Color.Black.copy(alpha = 0.44f),
            radius = trackRadius,
            center = c,
            style = Stroke(width = channelWidth)
        )
        drawCircle(
            color = stillColors.glassOutline.copy(alpha = 0.10f),
            radius = trackRadius,
            center = c,
            style = Stroke(width = channelWidth * 0.70f)
        )

        // 3 (Punto 2 Specifica). GEOMETRIE INTERNE DISCRETE (Opacità ridotta tra 0.08f e 0.10f, appena percettibili in controluce)
        val bladeCount = 12
        for (i in 0 until bladeCount) {
            val baseAngleDeg = i * (360f / bladeCount)
            val innerAngleRad = Math.toRadians(baseAngleDeg.toDouble())
            val outerAngleRad = Math.toRadians((baseAngleDeg + 12f).toDouble())

            val pInner = Offset(
                x = c.x + (innerRimRadius + 2.dp.toPx()) * kotlin.math.cos(innerAngleRad).toFloat(),
                y = c.y + (innerRimRadius + 2.dp.toPx()) * kotlin.math.sin(innerAngleRad).toFloat()
            )
            val pOuter = Offset(
                x = c.x + (outerRimRadius - 2.dp.toPx()) * kotlin.math.cos(outerAngleRad).toFloat(),
                y = c.y + (outerRimRadius - 2.dp.toPx()) * kotlin.math.sin(outerAngleRad).toFloat()
            )

            drawLine(
                color = stillColors.woodFrame.copy(alpha = 0.09f),
                start = pInner,
                end = pOuter,
                strokeWidth = 2.4.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawLine(
                color = stillColors.glassOutline.copy(alpha = 0.08f),
                start = pInner,
                end = pOuter,
                strokeWidth = 1.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        // 4. ANELLO ORGANICO A 360° CON INTERPOLAZIONE SPRING MORBIDA
        val ringBrush = when {
            isOvertime -> Brush.sweepGradient(
                colors = listOf(
                    stillColors.overtime.copy(alpha = 0.38f * activeGlowBoost),
                    stillColors.sandLight.copy(alpha = 0.48f * activeGlowBoost),
                    stillColors.overtime.copy(alpha = 0.38f * activeGlowBoost)
                ),
                center = c
            )
            isPreparing -> Brush.sweepGradient(
                colors = listOf(
                    tertiaryColor.copy(alpha = 0.38f * activeGlowBoost),
                    stillColors.glassOutline.copy(alpha = 0.42f * activeGlowBoost),
                    tertiaryColor.copy(alpha = 0.38f * activeGlowBoost)
                ),
                center = c
            )
            else -> Brush.linearGradient(
                colors = listOf(
                    stillColors.glassOutline.copy(alpha = 0.26f * activeGlowBoost),
                    primaryColor.copy(alpha = 0.20f * activeGlowBoost),
                    stillColors.sandLight.copy(alpha = 0.24f * activeGlowBoost),
                    stillColors.glassOutline.copy(alpha = 0.26f * activeGlowBoost)
                ),
                start = Offset(c.x - trackRadius, c.y - trackRadius),
                end = Offset(c.x + trackRadius, c.y + trackRadius)
            )
        }
        drawCircle(
            brush = ringBrush,
            radius = trackRadius,
            center = c,
            style = Stroke(width = 11.dp.toPx())
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.10f),
            radius = trackRadius + 1.8.dp.toPx(),
            center = c,
            style = Stroke(width = 1.2.dp.toPx())
        )

        // 5. GHIERA FISSA DEI 60 SECONDI (12 Tacche Principali + 48 Intermedie delicate)
        val currentSecondAngleDeg = -90f + wheelRotationPhase * 360f
        for (sec in 0 until 60) {
            val tickAngleDeg = -90f + sec * 6f
            val tickAngleRad = Math.toRadians(tickAngleDeg.toDouble())
            val isMajor = sec % 5 == 0

            val rawDiff = ((currentSecondAngleDeg - tickAngleDeg) % 360f + 360f) % 360f
            val isNearRunner = isRunning && rawDiff <= 32f
            val glowFactor = if (isNearRunner) (1f - rawDiff / 32f) else 0f

            val rStart = if (isMajor) outerRimRadius - 5.5.dp.toPx() else outerRimRadius - 3.4.dp.toPx()
            val rEnd = outerRimRadius + 1.2.dp.toPx()

            val tickColor = when {
                glowFactor > 0f -> stillColors.sandLight.copy(alpha = 0.45f + 0.50f * glowFactor)
                isMajor -> stillColors.sandLight.copy(alpha = 0.62f)
                else -> Color.White.copy(alpha = 0.16f)
            }

            drawLine(
                color = tickColor,
                start = Offset(
                    x = c.x + rStart * kotlin.math.cos(tickAngleRad).toFloat(),
                    y = c.y + rStart * kotlin.math.sin(tickAngleRad).toFloat()
                ),
                end = Offset(
                    x = c.x + rEnd * kotlin.math.cos(tickAngleRad).toFloat(),
                    y = c.y + rEnd * kotlin.math.sin(tickAngleRad).toFloat()
                ),
                strokeWidth = if (isMajor) 2.dp.toPx() else 0.9.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        // 6. DOPPIO CERCHIO IN LEGNO TORNITO 3D ("WA" ESTERNO E INTERNO)
        val woodRimBrush = Brush.linearGradient(
            colors = listOf(
                Color(0xFFFFE5B4).copy(alpha = 0.80f),
                stillColors.woodFrame,
                stillColors.woodFrame.copy(alpha = 0.90f),
                Color.Black.copy(alpha = 0.65f)
            ),
            start = Offset(c.x - outerRimRadius, c.y - outerRimRadius),
            end = Offset(c.x + outerRimRadius, c.y + outerRimRadius)
        )

        drawCircle(
            brush = woodRimBrush,
            radius = outerRimRadius,
            center = c,
            style = Stroke(width = 4.6.dp.toPx())
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.24f),
            radius = outerRimRadius + 2.dp.toPx(),
            center = c,
            style = Stroke(width = 0.8.dp.toPx())
        )

        drawCircle(
            brush = woodRimBrush,
            radius = innerRimRadius,
            center = c,
            style = Stroke(width = 3.6.dp.toPx())
        )
        drawCircle(
            color = stillColors.glassOutline.copy(alpha = 0.35f),
            radius = innerRimRadius - 2.dp.toPx(),
            center = c,
            style = Stroke(width = 1.dp.toPx())
        )

        // 12 Perni discreti sui nodi del cerchio esterno
        for (i in 0 until 12) {
            val pinAngleRad = Math.toRadians((-90.0 + i * 30.0))
            val pinPos = Offset(
                x = c.x + outerRimRadius * kotlin.math.cos(pinAngleRad).toFloat(),
                y = c.y + outerRimRadius * kotlin.math.sin(pinAngleRad).toFloat()
            )
            drawCircle(
                color = stillColors.sandLight.copy(alpha = 0.78f),
                radius = 1.8.dp.toPx(),
                center = pinPos
            )
        }

        // 7 (Punto 1 Specifica). CURSORE A BRACE D'INCENSO (RadialGradient sfumato a Transparent con BlendMode.Plus + scia luminosa lungo l'arco)
        val emberOuterColor = when {
            isOvertime -> stillColors.overtime
            isPreparing -> tertiaryColor
            else -> stillColors.sandDeep
        }
        val emberCoreColor = when {
            isOvertime -> stillColors.sandLight
            isPreparing -> stillColors.glassOutline
            else -> stillColors.sandLight
        }

        // Scia luminosa morbida che si dissolve lungo l'arco appena percorso (con BlendMode.Plus)
        if (isRunning) {
            val trailArcSteps = 14
            val arcDiameter = trackRadius * 2f
            val arcTopLeft = Offset(c.x - trackRadius, c.y - trackRadius)
            val arcSize = Size(arcDiameter, arcDiameter)
            val stepSweep = 2.6f
            for (s in 0 until trailArcSteps) {
                val factor = 1f - (s.toFloat() / trailArcSteps)
                val segAlpha = 0.28f * factor * factor
                val segWidth = (7.5.dp.toPx() * (0.35f + 0.65f * factor))
                val segStartAngle = currentSecondAngleDeg - (s + 1) * stepSweep
                drawArc(
                    color = emberCoreColor.copy(alpha = segAlpha),
                    startAngle = segStartAngle,
                    sweepAngle = stepSweep + 0.7f,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = segWidth, cap = StrokeCap.Round),
                    blendMode = BlendMode.Plus
                )
            }
        }

        // Posizione esatta della Brace d'Incenso sull'anello
        val orbAngleRad = Math.toRadians(currentSecondAngleDeg.toDouble())
        val orbCenter = Offset(
            x = c.x + trackRadius * kotlin.math.cos(orbAngleRad).toFloat(),
            y = c.y + trackRadius * kotlin.math.sin(orbAngleRad).toFloat()
        )

        // Alone esterno ampio e soffuso della brace d'incenso (BlendMode.Plus)
        val outerGlowRadius = 21.dp.toPx() * emberPulse
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0.00f to emberCoreColor.copy(alpha = 0.65f),
                    0.38f to emberOuterColor.copy(alpha = 0.35f),
                    0.72f to emberOuterColor.copy(alpha = 0.10f),
                    1.00f to Color.Transparent
                ),
                center = orbCenter,
                radius = outerGlowRadius
            ),
            radius = outerGlowRadius,
            center = orbCenter,
            blendMode = BlendMode.Plus
        )

        // Nucleo incandescente sfumato della brace (senza alcun bordo netto)
        val innerEmberRadius = 9.5.dp.toPx() * emberPulse
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0.00f to Color(0xFFFFF8E7).copy(alpha = 0.95f),
                    0.35f to emberCoreColor.copy(alpha = 0.82f),
                    0.75f to emberOuterColor.copy(alpha = 0.32f),
                    1.00f to Color.Transparent
                ),
                center = orbCenter,
                radius = innerEmberRadius
            ),
            radius = innerEmberRadius,
            center = orbCenter,
            blendMode = BlendMode.Plus
        )
    }
}

@org.intellij.lang.annotations.Language("AGSL")
private const val AGSL_CANDLE_FLAME_SHADER = """
uniform float2 uResolution;
uniform float uTime;
uniform float uSway;
uniform float uStretch;
uniform float4 uOuterColor;
uniform float4 uMidColor;

float hash(float2 p) {
    return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453123);
}

float noise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    float2 u = f * f * (3.0 - 2.0 * f);
    return mix(
        mix(hash(i + float2(0.0, 0.0)), hash(i + float2(1.0, 0.0)), u.x),
        mix(hash(i + float2(0.0, 1.0)), hash(i + float2(1.0, 1.0)), u.x),
        u.y
    );
}

float fbm(float2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 3; i++) {
        v += a * noise(p);
        p = p * 2.03 + float2(1.7, -2.4);
        a *= 0.5;
    }
    return v;
}

half4 main(float2 fragCoord) {
    float2 uv = fragCoord / max(uResolution, float2(1.0, 1.0));
    float hNorm = clamp((0.88 - uv.y) / (0.76 * (1.0 + uStretch * 0.06)), 0.0, 1.0);
    float xCenter = 0.5 + uSway * pow(hNorm, 1.55) * 0.24;
    float turb = (fbm(float2(uv.x * 5.5, uv.y * 4.5 + uTime * 2.6)) - 0.5) * 0.095 * pow(hNorm, 0.85);
    float dx = abs((uv.x - xCenter) + turb);

    float widthProfile = sin(clamp(hNorm, 0.0, 1.0) * 3.14159265) * (1.0 - 0.36 * hNorm) * 0.24;
    float dist = dx / max(widthProfile, 0.001);

    float outerMask = smoothstep(1.05, 0.32, dist) * smoothstep(0.0, 0.06, hNorm) * smoothstep(1.0, 0.86, hNorm);
    float coreMask = smoothstep(0.58, 0.10, dist) * smoothstep(0.04, 0.14, hNorm) * smoothstep(0.76, 0.35, hNorm);
    float blueBaseMask = smoothstep(0.95, 0.42, dist) * smoothstep(0.0, 0.05, hNorm) * smoothstep(0.28, 0.06, hNorm);

    float3 col = mix(uOuterColor.rgb, uMidColor.rgb, clamp(hNorm * 1.15 + (1.0 - dist) * 0.35, 0.0, 1.0));
    col = mix(col, float3(0.10, 0.58, 0.96), blueBaseMask * 0.72);
    col = mix(col, float3(1.0, 0.98, 0.90), coreMask);

    float alpha = clamp(outerMask * 0.94 + blueBaseMask * 0.45 + coreMask * 0.35, 0.0, 1.0);
    return half4(half3(col * alpha), half(alpha));
}
"""

@Composable
fun CandleTimerGraphic(
    waxFraction: Float,
    isRunning: Boolean,
    isOvertime: Boolean,
    isPreparing: Boolean,
    modifier: Modifier = Modifier
) {
    val stillColors = LocalStillColors.current
    val tertiaryColor = MaterialTheme.colorScheme.tertiary

    // 1. Risparmio batteria nel loop: attivo solo durante il timer e limitato a ~40 fps
    var flameTimeSec by remember { mutableStateOf(0f) }
    LaunchedEffect(isRunning) {
        if (isRunning) {
            var lastFrameNanos = -1L
            while (true) {
                androidx.compose.runtime.withFrameNanos { frameNanos ->
                    if (lastFrameNanos < 0L) {
                        lastFrameNanos = frameNanos
                    } else if (frameNanos - lastFrameNanos >= 25_000_000L) {
                        val deltaSec = ((frameNanos - lastFrameNanos).coerceAtLeast(0L)).toDouble() / 1_000_000_000.0
                        flameTimeSec = (flameTimeSec + deltaSec.toFloat()) % 3600f
                        lastFrameNanos = frameNanos
                    }
                }
            }
        }
    }

    // 6. Array pre-allocato di 8 micro-particelle calde (zero allocazioni per frame: xNorm, speed, phase, radiusDp)
    val sparkSeeds = remember {
        floatArrayOf(
            -0.22f, 0.42f, 0.00f, 1.35f,
             0.18f, 0.51f, 0.14f, 1.15f,
            -0.09f, 0.37f, 0.29f, 1.50f,
             0.26f, 0.46f, 0.43f, 1.05f,
            -0.16f, 0.55f, 0.57f, 1.25f,
             0.07f, 0.39f, 0.71f, 1.40f,
            -0.28f, 0.48f, 0.84f, 1.10f,
             0.14f, 0.44f, 0.93f, 1.30f
        )
    }

    // Oggetti Path e Matrix riutilizzati per zero allocazioni nel DrawScope
    val cylinderPath = remember { Path() }
    val dripPath = remember { Path() }
    val wickPath = remember { Path() }
    val outerFlamePath = remember { Path() }
    val blueCupPath = remember { Path() }
    val innerCorePath = remember { Path() }
    val shaderMatrix = remember { Matrix() }

    val agslFlameShader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            RuntimeShader(AGSL_CANDLE_FLAME_SHADER)
        } else {
            null
        }
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f

        // 5. Oscillazione organica combinando 3 sinusoidi a frequenze non multiple
        val t = flameTimeSec * 2.2f
        val swayWave = kotlin.math.sin(t) * 0.60f +
            kotlin.math.sin(t * 2.37f) * 0.25f +
            kotlin.math.sin(t * 4.13f) * 0.15f
        val bellyWave = kotlin.math.sin(t * 1.31f + 0.8f) * 0.55f +
            kotlin.math.sin(t * 2.93f) * 0.30f +
            kotlin.math.sin(t * 4.79f) * 0.15f
        val stretchWave = kotlin.math.sin(t * 1.73f + 0.4f) * 0.60f +
            kotlin.math.sin(t * 3.19f) * 0.25f +
            kotlin.math.sin(t * 5.07f) * 0.15f

        val motionIntensity = when {
            !isRunning -> 0.18f
            isPreparing -> 0.45f
            else -> 1.0f
        }

        // 7. Stati limite: in Overtime fermo su residuo basso (10%), altrimenti usa la frazione (75% in Infinito)
        val effectiveWax = if (isOvertime) 0.10f else waxFraction.coerceIn(0.10f, 1f)

        // Dimensioni e coordinate del corpo cilindrico della candela e del piattino Zen
        val baseCenterY = h * 0.885f
        val candleWidth = w * 0.38f
        val candleLeft = cx - candleWidth / 2f
        val candleRight = cx + candleWidth / 2f
        val ellipseRy = candleWidth * 0.155f

        val candleBottomY = baseCenterY - h * 0.015f
        val minTopY = h * 0.36f // Candela nuova (100%)
        val maxTopY = h * 0.74f // Limite massimo inferiore
        val candleTopY = maxTopY - (maxTopY - minTopY) * effectiveWax
        val candleHeight = (candleBottomY - candleTopY).coerceAtLeast(ellipseRy * 1.4f)

        // Colori dinamici della fiamma in base al tema e allo stato (vira verso overtime in Overtime)
        val flameOuterColor = when {
            isOvertime -> stillColors.overtime
            isPreparing -> tertiaryColor
            else -> stillColors.sandDeep
        }
        val flameMidColor = when {
            isOvertime -> stillColors.sandLight
            isPreparing -> stillColors.glassOutline
            else -> stillColors.sandLight
        }

        // 1. OMBRA MORBIDA A TERRA SOTTO IL PIATTINO PORTA-CANDELA
        val shadowWidth = w * 0.70f
        val shadowHeight = h * 0.065f
        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.Black.copy(alpha = 0.45f),
                    Color.Black.copy(alpha = 0.18f),
                    Color.Transparent
                ),
                center = Offset(cx, baseCenterY + shadowHeight * 0.35f),
                radius = shadowWidth * 0.52f
            ),
            topLeft = Offset(cx - shadowWidth / 2f, baseCenterY - shadowHeight * 0.15f),
            size = Size(shadowWidth, shadowHeight)
        )

        // 2. PIATTINO PORTA-CANDELA TORNITO 3D IN LEGNO E CERAMICA
        val saucerWidth = w * 0.62f
        val saucerHeight = h * 0.072f
        val saucerBrush = Brush.horizontalGradient(
            colorStops = arrayOf(
                0.00f to Color.Black.copy(alpha = 0.40f),
                0.15f to stillColors.woodFrame,
                0.35f to Color.White.copy(alpha = 0.22f),
                0.55f to stillColors.woodFrame,
                0.85f to Color.Black.copy(alpha = 0.32f),
                1.00f to Color.Black.copy(alpha = 0.50f)
            ),
            startX = cx - saucerWidth / 2f,
            endX = cx + saucerWidth / 2f
        )
        drawOval(
            color = stillColors.woodFrame,
            topLeft = Offset(cx - saucerWidth / 2f, baseCenterY - saucerHeight * 0.42f),
            size = Size(saucerWidth, saucerHeight)
        )
        drawOval(
            brush = saucerBrush,
            topLeft = Offset(cx - saucerWidth / 2f, baseCenterY - saucerHeight * 0.42f),
            size = Size(saucerWidth, saucerHeight)
        )
        val innerSaucerW = saucerWidth * 0.84f
        val innerSaucerH = saucerHeight * 0.68f
        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(
                    flameMidColor.copy(alpha = 0.32f),
                    stillColors.glassOutline.copy(alpha = 0.25f),
                    Color.Black.copy(alpha = 0.38f)
                ),
                center = Offset(cx, baseCenterY - innerSaucerH * 0.15f),
                radius = innerSaucerW * 0.5f
            ),
            topLeft = Offset(cx - innerSaucerW / 2f, baseCenterY - innerSaucerH * 0.50f),
            size = Size(innerSaucerW, innerSaucerH)
        )
        drawOval(
            color = stillColors.glassOutline.copy(alpha = 0.40f),
            topLeft = Offset(cx - saucerWidth / 2f, baseCenterY - saucerHeight * 0.42f),
            size = Size(saucerWidth, saucerHeight),
            style = Stroke(width = 1.dp.toPx())
        )

        // 3. CORPO CILINDRICO 3D DELLA CANDELA IN CERA NATURALE
        val waxBaseCream = Color(0xFFF3EAD8)
        val waxWarmMid = Color(0xFFE5D5B5)
        val waxCoreShadow = Color(0xFFB8A27E)
        val waxRimLight = Color(0xFFD6C39E)

        cylinderPath.reset()
        cylinderPath.moveTo(candleLeft, candleTopY)
        cylinderPath.lineTo(candleLeft, candleBottomY)
        cylinderPath.cubicTo(
            candleLeft, candleBottomY + ellipseRy * 1.3f,
            candleRight, candleBottomY + ellipseRy * 1.3f,
            candleRight, candleBottomY
        )
        cylinderPath.lineTo(candleRight, candleTopY)
        cylinderPath.close()

        drawPath(
            path = cylinderPath,
            brush = Brush.horizontalGradient(
                colorStops = arrayOf(
                    0.00f to waxWarmMid,
                    0.20f to waxBaseCream,
                    0.48f to waxWarmMid,
                    0.82f to waxCoreShadow,
                    1.00f to waxRimLight
                ),
                startX = candleLeft,
                endX = candleRight
            )
        )

        // Subsurface Scattering nella parte alta della cera
        val sssDepth = (candleHeight * 0.58f).coerceAtLeast(ellipseRy * 2.2f)
        val sssAlpha = (0.48f + 0.08f * stretchWave * motionIntensity).coerceIn(0.35f, 0.62f)
        drawPath(
            path = cylinderPath,
            brush = Brush.verticalGradient(
                colorStops = arrayOf(
                    0.00f to flameMidColor.copy(alpha = sssAlpha),
                    0.35f to flameOuterColor.copy(alpha = sssAlpha * 0.45f),
                    0.75f to Color.Transparent,
                    1.00f to Color.Black.copy(alpha = 0.18f)
                ),
                startY = candleTopY,
                endY = candleTopY + sssDepth
            )
        )

        // 4. COLATURE ASIMMETRICHE (Più sottili a metà, più arrotondate sulla punta + linea di luce sul bordo sinistro)
        val dripSpecs = floatArrayOf(
            -0.27f, 0.46f, 3.6f,
             0.21f, 0.62f, 3.1f,
            -0.05f, 0.31f, 2.5f
        )
        for (i in 0 until 3) {
            val idx = i * 3
            val xOffsetFrac = dripSpecs[idx]
            val lenFrac = dripSpecs[idx + 1]
            val baseRadius = dripSpecs[idx + 2].dp.toPx()

            val dripX = cx + candleWidth * xOffsetFrac
            val dripStartY = candleTopY + ellipseRy * 0.62f
            val maxAvailableLen = candleBottomY - dripStartY - baseRadius * 1.2f
            val dripLen = (candleHeight * lenFrac).coerceAtMost(maxAvailableLen)

            if (dripLen > baseRadius * 2.3f) {
                val dripMidY = dripStartY + dripLen * 0.52f
                val dripTipY = dripStartY + dripLen
                val topHalfW = baseRadius * 0.68f
                val waistHalfW = baseRadius * 0.36f // Più sottile a metà corsa
                val bulbHalfW = baseRadius * 0.88f  // Più arrotondata sulla punta

                dripPath.reset()
                dripPath.moveTo(dripX - topHalfW, dripStartY)
                dripPath.quadraticTo(
                    dripX - waistHalfW, dripMidY,
                    dripX - bulbHalfW, dripTipY - bulbHalfW * 0.35f
                )
                dripPath.cubicTo(
                    dripX - bulbHalfW, dripTipY + bulbHalfW * 0.95f,
                    dripX + bulbHalfW, dripTipY + bulbHalfW * 0.95f,
                    dripX + bulbHalfW, dripTipY - bulbHalfW * 0.35f
                )
                dripPath.quadraticTo(
                    dripX + waistHalfW, dripMidY,
                    dripX + topHalfW, dripStartY
                )
                dripPath.close()

                drawPath(
                    path = dripPath,
                    color = waxBaseCream.copy(alpha = 0.88f)
                )

                // Sottile linea di luce sul bordo sinistro di ciascuna colatura
                drawLine(
                    color = Color.White.copy(alpha = 0.72f),
                    start = Offset(dripX - topHalfW * 0.72f, dripStartY + baseRadius * 0.25f),
                    end = Offset(dripX - bulbHalfW * 0.62f, dripTipY),
                    strokeWidth = 0.9.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }

        drawPath(
            path = cylinderPath,
            color = stillColors.glassOutline.copy(alpha = 0.28f),
            style = Stroke(width = 1.dp.toPx())
        )

        // 2 (Punto 2 Specifica). LAGHETTO DI CERA FUSA INCAVATO CON RADIAL GRADIENT SCURO E LUCIDO ATTORNO ALLO STOPPINO
        // Corona esterna del bordo superiore della candela
        drawOval(
            brush = Brush.horizontalGradient(
                colors = listOf(waxBaseCream, waxWarmMid, waxCoreShadow),
                startX = candleLeft,
                endX = candleRight
            ),
            topLeft = Offset(candleLeft, candleTopY - ellipseRy),
            size = Size(candleWidth, ellipseRy * 2f)
        )
        // Ellisse concentrica leggermente incavata (spostata più in basso per effetto conca 3D)
        val poolW = candleWidth * 0.84f
        val poolRy = ellipseRy * 0.74f
        val poolCenterY = candleTopY + ellipseRy * 0.14f
        drawOval(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0.00f to Color(0xFF4A2E14).copy(alpha = 0.92f), // Cera liquida profonda e scura attorno allo stoppino
                    0.28f to flameOuterColor.copy(alpha = 0.82f),   // Riflesso ambrato lucido della cera sciolta
                    0.68f to flameMidColor.copy(alpha = 0.75f),     // Albedo calda verso il bordo bagnato
                    1.00f to waxCoreShadow.copy(alpha = 0.88f)      // Parete interna dell'incavo
                ),
                center = Offset(cx, poolCenterY),
                radius = poolW * 0.52f
            ),
            topLeft = Offset(cx - poolW / 2f, poolCenterY - poolRy),
            size = Size(poolW, poolRy * 2f)
        )
        // Riflesso speculare bagnato sul laghetto di cera liquida
        drawArc(
            color = Color.White.copy(alpha = 0.55f),
            startAngle = 195f,
            sweepAngle = 135f,
            useCenter = false,
            topLeft = Offset(cx - poolW * 0.42f, poolCenterY - poolRy * 0.75f),
            size = Size(poolW * 0.84f, poolRy * 1.5f),
            style = Stroke(width = 1.1.dp.toPx(), cap = StrokeCap.Round)
        )
        // Bordo superiore esterno illuminato
        drawOval(
            color = Color.White.copy(alpha = 0.48f),
            topLeft = Offset(candleLeft, candleTopY - ellipseRy),
            size = Size(candleWidth, ellipseRy * 2f),
            style = Stroke(width = 1.dp.toPx())
        )

        // 3 (Punto 3 Specifica). STOPPINO CURVO (quadraticTo) E MICRO-PUNTO DI BRACE INCANDESCENTE
        val wickBaseY = poolCenterY + poolRy * 0.08f
        val wickHeight = h * 0.046f
        val wickTopY = wickBaseY - wickHeight
        val wickCurveX = (w * 0.011f) + swayWave * motionIntensity * (w * 0.008f)
        val wickTip = Offset(cx + wickCurveX, wickTopY)

        // Ombra dello stoppino nel laghetto fuso
        drawOval(
            color = Color.Black.copy(alpha = 0.42f),
            topLeft = Offset(cx - 3.2.dp.toPx(), wickBaseY - 1.5.dp.toPx()),
            size = Size(6.4.dp.toPx(), 3.dp.toPx())
        )

        wickPath.reset()
        wickPath.moveTo(cx, wickBaseY)
        wickPath.quadraticTo(
            cx - wickCurveX * 0.45f,
            wickBaseY - wickHeight * 0.52f,
            wickTip.x,
            wickTip.y
        )
        drawPath(
            path = wickPath,
            color = Color(0xFF23170F),
            style = Stroke(width = 2.1.dp.toPx(), cap = StrokeCap.Round)
        )

        // 5. GEOMETRIA DELLA FIAMMA, LUCE ADDITIVA (BlendMode.Plus) E RUNTIMESHADER AGSL (API 33+)
        val baseFlameHeight = when {
            isPreparing -> h * 0.115f
            isOvertime -> h * 0.195f
            else -> h * 0.175f
        }
        val flameHeight = baseFlameHeight * (1f + stretchWave * 0.065f * motionIntensity)
        val flameHalfWidth = (candleWidth * 0.25f) * (1f - stretchWave * 0.03f * motionIntensity)

        val tipSwayX = swayWave * motionIntensity * (candleWidth * 0.17f)
        val bellySwayX = bellyWave * motionIntensity * (candleWidth * 0.055f)

        val flameBaseY = wickTip.y + wickHeight * 0.16f
        val flameTip = Offset(wickTip.x + tipSwayX, flameBaseY - flameHeight)
        val flameCenter = Offset(
            x = wickTip.x + tipSwayX * 0.38f,
            y = flameBaseY - flameHeight * 0.45f
        )

        // Alone radiale esterno con BlendMode.Plus per fusione ottica additiva con lo sfondo
        val haloRadius = flameHeight * 1.55f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    flameMidColor.copy(alpha = 0.46f),
                    flameOuterColor.copy(alpha = 0.22f),
                    Color.Transparent
                ),
                center = flameCenter,
                radius = haloRadius
            ),
            radius = haloRadius,
            center = flameCenter,
            blendMode = BlendMode.Plus
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && agslFlameShader != null) {
            val boxW = candleWidth * 1.45f
            val boxH = flameHeight * 1.35f
            val boxLeft = wickTip.x - boxW / 2f
            val boxTop = flameBaseY - boxH * 0.88f

            agslFlameShader.setFloatUniform("uResolution", boxW, boxH)
            agslFlameShader.setFloatUniform("uTime", flameTimeSec)
            agslFlameShader.setFloatUniform("uSway", swayWave * motionIntensity)
            agslFlameShader.setFloatUniform("uStretch", stretchWave * motionIntensity)
            agslFlameShader.setFloatUniform(
                "uOuterColor",
                flameOuterColor.red,
                flameOuterColor.green,
                flameOuterColor.blue,
                1f
            )
            agslFlameShader.setFloatUniform(
                "uMidColor",
                flameMidColor.red,
                flameMidColor.green,
                flameMidColor.blue,
                1f
            )

            shaderMatrix.reset()
            shaderMatrix.setTranslate(boxLeft, boxTop)
            agslFlameShader.setLocalMatrix(shaderMatrix)

            drawRect(
                brush = ShaderBrush(agslFlameShader),
                topLeft = Offset(boxLeft, boxTop),
                size = Size(boxW, boxH)
            )
        } else {
            // Fallback automatico vettoriale su Canvas per API < 33
            outerFlamePath.reset()
            outerFlamePath.moveTo(wickTip.x, flameBaseY)
            outerFlamePath.cubicTo(
                wickTip.x - flameHalfWidth * 1.15f + bellySwayX,
                flameBaseY - flameHeight * 0.22f,
                wickTip.x - flameHalfWidth * 0.72f + tipSwayX * 0.55f,
                flameBaseY - flameHeight * 0.72f,
                flameTip.x,
                flameTip.y
            )
            outerFlamePath.cubicTo(
                wickTip.x + flameHalfWidth * 0.72f + tipSwayX * 0.55f,
                flameBaseY - flameHeight * 0.72f,
                wickTip.x + flameHalfWidth * 1.15f + bellySwayX,
                flameBaseY - flameHeight * 0.22f,
                wickTip.x,
                flameBaseY
            )
            outerFlamePath.close()

            drawPath(
                path = outerFlamePath,
                brush = Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.00f to Color(0xFFFFF59D),
                        0.35f to flameMidColor,
                        0.78f to flameOuterColor,
                        1.00f to flameOuterColor.copy(alpha = 0.70f)
                    ),
                    startY = flameTip.y,
                    endY = flameBaseY
                )
            )

            val blueCupWidth = flameHalfWidth * 0.78f
            val blueCupHeight = flameHeight * 0.26f
            blueCupPath.reset()
            blueCupPath.moveTo(wickTip.x, flameBaseY + 1.5.dp.toPx())
            blueCupPath.cubicTo(
                wickTip.x - blueCupWidth,
                flameBaseY - blueCupHeight * 0.25f,
                wickTip.x - blueCupWidth * 0.55f,
                flameBaseY - blueCupHeight,
                wickTip.x + bellySwayX * 0.3f,
                flameBaseY - blueCupHeight * 0.85f
            )
            blueCupPath.cubicTo(
                wickTip.x + blueCupWidth * 0.55f,
                flameBaseY - blueCupHeight,
                wickTip.x + blueCupWidth,
                flameBaseY - blueCupHeight * 0.25f,
                wickTip.x,
                flameBaseY + 1.5.dp.toPx()
            )
            blueCupPath.close()

            drawPath(
                path = blueCupPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color(0xFF4FC3F7).copy(alpha = 0.55f),
                        Color(0xFF0288D1).copy(alpha = 0.75f)
                    ),
                    startY = flameBaseY - blueCupHeight,
                    endY = flameBaseY + 1.5.dp.toPx()
                )
            )

            val coreHeight = flameHeight * 0.62f
            val coreHalfWidth = flameHalfWidth * 0.52f
            val coreBaseY = flameBaseY - flameHeight * 0.05f
            val coreTip = Offset(wickTip.x + tipSwayX * 0.58f, coreBaseY - coreHeight)

            innerCorePath.reset()
            innerCorePath.moveTo(wickTip.x, coreBaseY)
            innerCorePath.cubicTo(
                wickTip.x - coreHalfWidth + bellySwayX * 0.6f,
                coreBaseY - coreHeight * 0.25f,
                wickTip.x - coreHalfWidth * 0.55f + tipSwayX * 0.4f,
                coreBaseY - coreHeight * 0.75f,
                coreTip.x,
                coreTip.y
            )
            innerCorePath.cubicTo(
                wickTip.x + coreHalfWidth * 0.55f + tipSwayX * 0.4f,
                coreBaseY - coreHeight * 0.75f,
                wickTip.x + coreHalfWidth + bellySwayX * 0.6f,
                coreBaseY - coreHeight * 0.25f,
                wickTip.x,
                coreBaseY
            )
            innerCorePath.close()

            drawPath(
                path = innerCorePath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFFFF8E1),
                        Color.White,
                        Color(0xFFFFE082).copy(alpha = 0.90f)
                    ),
                    startY = coreTip.y,
                    endY = coreBaseY
                )
            )
        }

        // Micro-punto rosso-arancio incandescente dove lo stoppino tocca la base blu della fiamma
        drawCircle(
            color = Color(0xFFFF3D00),
            radius = 2.4.dp.toPx(),
            center = wickTip,
            blendMode = BlendMode.Plus
        )
        drawCircle(
            color = Color(0xFFFFAB00),
            radius = 1.4.dp.toPx(),
            center = wickTip
        )
        drawCircle(
            color = Color(0xFFFFF59D),
            radius = 0.7.dp.toPx(),
            center = wickTip
        )

        // 6. MICRO-SCINTILLE CALDE (8 particelle pre-allocate che salgono sopra la fiamma dissolvendosi)
        val sparkRiseSpan = flameHeight * 0.85f
        for (s in 0 until 8) {
            val baseIdx = s * 4
            val seedX = sparkSeeds[baseIdx]
            val speed = sparkSeeds[baseIdx + 1]
            val phaseOffset = sparkSeeds[baseIdx + 2]
            val radiusPx = sparkSeeds[baseIdx + 3].dp.toPx()

            val life = (flameTimeSec * speed + phaseOffset) % 1f
            val sparkAlpha = kotlin.math.sin(life * Math.PI.toFloat()) * (1f - life * 0.45f) * (if (isRunning) 0.78f else 0.35f)
            if (sparkAlpha > 0.03f) {
                val driftX = kotlin.math.sin((flameTimeSec * 1.9f + s * 1.3f).toDouble()).toFloat() * (candleWidth * 0.08f) * life
                val sparkX = flameTip.x + seedX * (candleWidth * 0.28f) + driftX
                val sparkY = flameTip.y + flameHeight * 0.12f - life * sparkRiseSpan
                drawCircle(
                    color = flameMidColor.copy(alpha = sparkAlpha.coerceIn(0f, 1f)),
                    radius = radiusPx * (1f - life * 0.55f),
                    center = Offset(sparkX, sparkY),
                    blendMode = BlendMode.Plus
                )
            }
        }
    }
}

fun formatTime(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}

@Preview(showBackground = true)
@Composable
fun MeditationTimerPreview() {
    StillTheme {
        MeditationTimerScreen()
    }
}