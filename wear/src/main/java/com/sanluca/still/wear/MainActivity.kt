@file:Suppress("WearMaterialTheme")

package com.sanluca.still.wear

import com.sanluca.still.wear.ui.theme.OvertimeGold
import com.sanluca.still.wear.ui.theme.StillColorPalette
import com.sanluca.still.wear.ui.theme.StillTheme
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.wear.ambient.AmbientModeSupport
import android.app.Activity
import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale
import android.content.ContextWrapper

fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

class MainActivity : FragmentActivity(), AmbientModeSupport.AmbientCallbackProvider {

    private lateinit var ambientController: AmbientModeSupport.AmbientController
    private val isAmbientState = mutableStateOf(false)
    private val ambientUpdateTickState = mutableStateOf(0L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ambientController = AmbientModeSupport.attach(this)
        ambientController.setAutoResumeEnabled(true)
        setContent {
            var selectedPalette by remember { mutableStateOf(getSavedColorPalette(this)) }
            StillTheme(palette = selectedPalette) {
                WearMeditationApp(
                    isAmbient = isAmbientState.value,
                    ambientUpdateTick = ambientUpdateTickState.value,
                    selectedPalette = selectedPalette,
                    onPaletteSelected = { newPalette ->
                        selectedPalette = newPalette
                        saveColorPalette(this, newPalette)
                    }
                )
            }
        }
    }

    override fun getAmbientCallback(): AmbientModeSupport.AmbientCallback = object : AmbientModeSupport.AmbientCallback() {
        override fun onEnterAmbient(ambientDetails: Bundle?) {
            super.onEnterAmbient(ambientDetails)
            isAmbientState.value = true
            ambientUpdateTickState.value = System.currentTimeMillis()
        }

        override fun onUpdateAmbient() {
            super.onUpdateAmbient()
            ambientUpdateTickState.value = System.currentTimeMillis()
        }

        override fun onExitAmbient() {
            super.onExitAmbient()
            isAmbientState.value = false
            ambientUpdateTickState.value = System.currentTimeMillis()
        }
    }
}

private const val PREFS_NAME = "still_wear_prefs"
private const val KEY_PREP_TIME = "prep_time_seconds"
private const val KEY_GONG_FREQ = "gong_frequency_hz"
private const val KEY_GONG_VOLUME = "gong_volume_percent"
private const val KEY_ALERT_MODE = "alert_mode"
private const val KEY_COLOR_PALETTE = "color_palette"
private const val KEY_PENDING_SESSIONS = "pending_watch_sessions"

const val ALERT_MODE_BOTH = 0
const val ALERT_MODE_SOUND = 1
const val ALERT_MODE_VIBRATION = 2

fun getSavedColorPalette(context: Context): StillColorPalette {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return StillColorPalette.fromId(prefs.getString(KEY_COLOR_PALETTE, StillColorPalette.AUTUMN.id))
}

fun saveColorPalette(context: Context, palette: StillColorPalette) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putString(KEY_COLOR_PALETTE, palette.id).apply()
}

fun getSavedPrepTime(context: Context): Int {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getInt(KEY_PREP_TIME, 0)
}

fun savePrepTime(context: Context, secs: Int) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putInt(KEY_PREP_TIME, secs).apply()
}

fun getSavedGongFrequency(context: Context): Double {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getFloat(KEY_GONG_FREQ, 432.0f).toDouble()
}

fun saveGongFrequency(context: Context, freqHz: Double) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putFloat(KEY_GONG_FREQ, freqHz.toFloat()).apply()
}

fun getSavedGongVolume(context: Context): Int {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getInt(KEY_GONG_VOLUME, 100).coerceIn(20, 100)
}

fun saveGongVolume(context: Context, volumePercent: Int) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putInt(KEY_GONG_VOLUME, volumePercent.coerceIn(20, 100)).apply()
}

fun getSavedAlertMode(context: Context): Int {
    val hasAudioHardware = context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUDIO_OUTPUT)
    val defaultMode = if (hasAudioHardware) ALERT_MODE_BOTH else ALERT_MODE_VIBRATION
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getInt(KEY_ALERT_MODE, defaultMode)
}

fun saveAlertMode(context: Context, mode: Int) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putInt(KEY_ALERT_MODE, mode).apply()
}

fun savePendingSessionOnWatch(context: Context, raw: String) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val existingList = prefs.getStringSet(KEY_PENDING_SESSIONS, emptySet())?.toMutableList() ?: mutableListOf()
    if (!existingList.contains(raw)) {
        existingList.add(raw)
        while (existingList.size > 20) {
            existingList.removeAt(0)
        }
        prefs.edit().putStringSet(KEY_PENDING_SESSIONS, existingList.toSet()).apply()
    }
}

fun flushPendingSessionsToPhone(context: Context) {
    try {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val pendingSet = prefs.getStringSet(KEY_PENDING_SESSIONS, emptySet()) ?: emptySet()
        if (pendingSet.isNotEmpty()) {
            val dataClient = Wearable.getDataClient(context)
            for (raw in pendingSet) {
                val parts = raw.split("|")
                if (parts.size >= 3) {
                    val durationSec = parts[0].toIntOrNull() ?: 0
                    val startEpochSec = parts[1].toLongOrNull() ?: 0L
                    val endEpochSec = parts[2].toLongOrNull() ?: 0L
                    if (durationSec > 0 && startEpochSec > 0 && endEpochSec > 0) {
                        val putDataMapReq = PutDataMapRequest.create("/meditation_completed/$startEpochSec").apply {
                            dataMap.putInt("duration_sec", durationSec)
                            dataMap.putLong("start_epoch_sec", startEpochSec)
                            dataMap.putLong("end_epoch_sec", endEpochSec)
                            dataMap.putString("raw", raw)
                            dataMap.putLong("timestamp", System.currentTimeMillis())
                        }
                        val putDataReq = putDataMapReq.asPutDataRequest().setUrgent()
                        dataClient.putDataItem(putDataReq)
                    }
                }
            }
            prefs.edit().remove(KEY_PENDING_SESSIONS).apply()
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun sendSessionToPhone(context: Context, durationSec: Int, startInstant: Instant, endInstant: Instant) {
    try {
        val startEpochSec = startInstant.epochSecond
        val endEpochSec = endInstant.epochSecond
        val raw = "$durationSec|$startEpochSec|$endEpochSec"

        // 1. Invio affidabile offline/background con DataClient
        // Google Play Services salva i dati localmente sull'orologio e li sincronizza
        // in automatico appena il telefono è di nuovo visibile/acceso.
        val dataClient = Wearable.getDataClient(context)
        val putDataMapReq = PutDataMapRequest.create("/meditation_completed/$startEpochSec").apply {
            dataMap.putInt("duration_sec", durationSec)
            dataMap.putLong("start_epoch_sec", startEpochSec)
            dataMap.putLong("end_epoch_sec", endEpochSec)
            dataMap.putString("raw", raw)
            dataMap.putLong("timestamp", System.currentTimeMillis())
        }
        val putDataReq = putDataMapReq.asPutDataRequest().setUrgent()
        dataClient.putDataItem(putDataReq)
    } catch (e: Exception) {
        e.printStackTrace()
        // In caso di errore eccezionale, memorizziamo nelle SharedPreferences come salvaguardia
        val startEpochSec = startInstant.epochSecond
        val endEpochSec = endInstant.epochSecond
        savePendingSessionOnWatch(context, "$durationSec|$startEpochSec|$endEpochSec")
    }
}

fun triggerHapticGong(context: Context, repeatCount: Int = 1) {
    try {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager
            vibratorManager?.defaultVibrator ?: (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

        if (vibrator != null && vibrator.hasVibrator()) {
            val timings = if (repeatCount >= 3) {
                longArrayOf(0, 500, 200, 800, 600, 500, 200, 800, 600, 500, 200, 800)
            } else {
                longArrayOf(0, 500, 200, 800)
            }
            val effect = VibrationEffect.createWaveform(timings, -1)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val vibrationAttributes = android.os.VibrationAttributes.createForUsage(
                    android.os.VibrationAttributes.USAGE_ALARM
                )
                vibrator.vibrate(effect, vibrationAttributes)
            } else {
                val audioAttributes = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .build()
                @Suppress("DEPRECATION")
                vibrator.vibrate(effect, audioAttributes)
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun cancelGongVibration(context: Context) {
    try {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager
            vibratorManager?.defaultVibrator ?: (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        vibrator?.cancel()
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

@Composable
fun WearMeditationApp(
    isAmbient: Boolean = false,
    ambientUpdateTick: Long = 0L,
    selectedPalette: StillColorPalette = StillColorPalette.AUTUMN,
    onPaletteSelected: (StillColorPalette) -> Unit = {}
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    var selectedTimeInSeconds by remember { mutableStateOf(300) } // Default 5 min
    
    var prepTimeSeconds by remember { mutableStateOf(getSavedPrepTime(context)) }
    var gongFreqHz by remember { mutableStateOf(getSavedGongFrequency(context)) }
    var gongVolumePercent by remember { mutableStateOf(getSavedGongVolume(context)) }
    var alertMode by remember { mutableStateOf(getSavedAlertMode(context)) }
    val clockFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    var currentClockText by remember { mutableStateOf(clockFormatter.format(Date())) }

    var isPreparing by remember { mutableStateOf(false) }
    var prepTimeLeftSeconds by remember { mutableStateOf(0) }

    var timeLeftInSeconds by remember { mutableStateOf(selectedTimeInSeconds) }

    var isOvertime by remember { mutableStateOf(false) }
    var overtimeSeconds by remember { mutableStateOf(0) }

    var isTimerRunning by remember { mutableStateOf(StillTimerService.isServiceRunning) }
    var sessionStartTime by remember { mutableStateOf<Instant?>(null) }

    // Broadcast receiver for timer ticks and system clock ticks
    val receiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                currentClockText = clockFormatter.format(Date())
                if (intent?.action == StillTimerService.ACTION_TIMER_TICK) {
                    if (!isTimerRunning) {
                        return
                    }
                    isPreparing = intent.getBooleanExtra(StillTimerService.EXTRA_IS_PREPARING, false)
                    prepTimeLeftSeconds = intent.getIntExtra(StillTimerService.EXTRA_PREP_LEFT, 0)
                    timeLeftInSeconds = intent.getIntExtra(StillTimerService.EXTRA_TIME_LEFT, 0)
                    isOvertime = intent.getBooleanExtra(StillTimerService.EXTRA_IS_OVERTIME_STATE, false)
                    overtimeSeconds = intent.getIntExtra(StillTimerService.EXTRA_OVERTIME_SECONDS_STATE, 0)
                    val initialTimeState = intent.getIntExtra(StillTimerService.EXTRA_INITIAL_TIME_STATE, 300)
                    selectedTimeInSeconds = initialTimeState

                    if (sessionStartTime == null && !isPreparing && isTimerRunning) {
                        sessionStartTime = Instant.now()
                    }
                }
            }
        }
    }

    DisposableEffect(context) {
        val filter = IntentFilter().apply {
            addAction(StillTimerService.ACTION_TIMER_TICK)
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    LaunchedEffect(isAmbient, ambientUpdateTick) {
        currentClockText = clockFormatter.format(Date())
    }

    LaunchedEffect(Unit) {
        try {
            flushPendingSessionsToPhone(context)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Aggiornamento periodico dell'ora solo con schermo attivo (in ambient bastano i tick di sistema)
    LaunchedEffect(isAmbient) {
        if (!isAmbient) {
            while (true) {
                currentClockText = clockFormatter.format(Date())
                delay(10_000L)
            }
        }
    }

    fun updateTimeSelection(newTime: Int) {
        selectedTimeInSeconds = newTime
        timeLeftInSeconds = if (newTime == -1) 0 else newTime
    }

    val progress = remember(timeLeftInSeconds, selectedTimeInSeconds) {
        if (selectedTimeInSeconds > 0) {
            timeLeftInSeconds.toFloat() / selectedTimeInSeconds.toFloat()
        } else {
            1f
        }
    }

    val backgroundColor = if (isAmbient) Color.Black else MaterialTheme.colorScheme.background
    val overtimeColor = if (isAmbient) Color.White else OvertimeGold
    val prepColor = if (isAmbient) Color.White else MaterialTheme.colorScheme.tertiary
    val currentView = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val keepScreenAwake = isTimerRunning || isOvertime

    // Con timer attivo o in overtime lo swipe-to-dismiss/back non chiude per errore l'Activity
    BackHandler(enabled = isTimerRunning || isOvertime) { }

    // Triplo presidio per non far spegnere lo schermo durante preparazione, meditazione e tempo extra
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            if (!StillTimerService.isServiceRunning) {
                if (sessionStartTime == null) {
                    val serviceIntent = Intent(context, StillTimerService::class.java).apply {
                        action = StillTimerService.ACTION_START
                        putExtra(StillTimerService.EXTRA_FREQ, gongFreqHz)
                        putExtra(StillTimerService.EXTRA_TIME, selectedTimeInSeconds)
                        putExtra(StillTimerService.EXTRA_INITIAL_TIME, selectedTimeInSeconds)
                        putExtra(StillTimerService.EXTRA_PREP, prepTimeSeconds)
                        putExtra(StillTimerService.EXTRA_ALERT_MODE, alertMode)
                        putExtra(StillTimerService.EXTRA_VOLUME, gongVolumePercent)
                    }
                    context.startForegroundService(serviceIntent)
                } else {
                    // Resume
                    val serviceIntent = Intent(context, StillTimerService::class.java).apply {
                        action = StillTimerService.ACTION_START
                        putExtra(StillTimerService.EXTRA_FREQ, gongFreqHz)
                        putExtra(StillTimerService.EXTRA_TIME, timeLeftInSeconds)
                        putExtra(StillTimerService.EXTRA_INITIAL_TIME, selectedTimeInSeconds)
                        putExtra(StillTimerService.EXTRA_PREP, -1) // -1 tells the service it's a resume so no start gong
                        putExtra(StillTimerService.EXTRA_ALERT_MODE, alertMode)
                        putExtra(StillTimerService.EXTRA_VOLUME, gongVolumePercent)
                        putExtra(StillTimerService.EXTRA_IS_OVERTIME, isOvertime)
                        putExtra(StillTimerService.EXTRA_OVERTIME_SECONDS, overtimeSeconds)
                    }
                    context.startForegroundService(serviceIntent)
                }
            }
        } else {
            if (StillTimerService.isServiceRunning) {
                val stopIntent = Intent(context, StillTimerService::class.java).apply {
                    action = StillTimerService.ACTION_STOP
                }
                context.startService(stopIntent)
                StillTimerService.isServiceRunning = false
            }
            isPreparing = false
        }
    }

    fun resetTimerState() {
        cancelGongVibration(context)
        val stopIntent = Intent(context, StillTimerService::class.java).apply {
            action = StillTimerService.ACTION_STOP
        }
        context.startService(stopIntent)
        StillTimerService.isServiceRunning = false

        isTimerRunning = false
        isOvertime = false
        overtimeSeconds = 0
        timeLeftInSeconds = if (selectedTimeInSeconds == -1) 0 else selectedTimeInSeconds
        sessionStartTime = null
    }

    val toastSyncSuccessText = stringResource(R.string.toast_sync_success)

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
            sendSessionToPhone(context, totalSec, start, end)
            Toast.makeText(context, toastSyncSuccessText, Toast.LENGTH_SHORT).show()
        }

        resetTimerState()
    }

    val isInitialState = !isTimerRunning && !isOvertime && sessionStartTime == null && (timeLeftInSeconds == selectedTimeInSeconds || (selectedTimeInSeconds == -1 && timeLeftInSeconds == 0))

    // La pagina Impostazioni esiste solo quando il timer è fermo
    val showSettingsPage = !isTimerRunning && !isOvertime
    val pagerState = rememberPagerState(pageCount = { if (showSettingsPage) 2 else 1 })
    LaunchedEffect(showSettingsPage) {
        if (!showSettingsPage) pagerState.scrollToPage(0)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .then(if (keepScreenAwake) Modifier.keepScreenOn() else Modifier),
        contentAlignment = Alignment.Center
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            // ================= PAGINA 1: TIMER (quadrante del tempo minimale perimetrale) =================
            if (page == 0) BoxWithConstraints(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                val diameter = minOf(maxWidth, maxHeight)
                val isPausedState = !isInitialState && !isTimerRunning && !isOvertime
                val showSideControls = isInitialState || isPausedState || isOvertime
                val canDecrease = selectedTimeInSeconds != -1

                // Animazione di pulsazione lenta del respiro (solo con timer attivo e non in ambient)
                // Animazione di pulsazione lenta del respiro: attiva solo con timer in esecuzione e non in ambient
                val breathGlowAlphaState: androidx.compose.runtime.State<Float>? = if (isTimerRunning && !isAmbient) {
                    val infiniteTransition = rememberInfiniteTransition(label = "wear_breath_transition")
                    infiniteTransition.animateFloat(
                        initialValue = 0.08f,
                        targetValue = 0.22f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = 3500, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "wear_breath_glow"
                    )
                } else {
                    null
                }

                val primaryColor = MaterialTheme.colorScheme.primary
                val tertiaryColor = MaterialTheme.colorScheme.tertiary
                val ringTrackColor = if (isAmbient) Color.Transparent else primaryColor.copy(alpha = 0.14f)

                // 1. CANVAS PERIMETRALE PULITO (Glow radiale soffuso + Traccia di fondo + Arco di avanzamento)
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val centerOffset = Offset(w / 2f, h / 2f)
                    val outerPaddingPx = 4.dp.toPx()
                    val arcDiameter = (minOf(w, h) - outerPaddingPx * 2).coerceAtLeast(10f)
                    val arcTopLeft = Offset((w - arcDiameter) / 2f, (h - arcDiameter) / 2f)
                    val arcSize = androidx.compose.ui.geometry.Size(arcDiameter, arcDiameter)
                    val strokeWidthPx = if (isAmbient) 1.5.dp.toPx() else 2.5.dp.toPx()

                    // A) Bagliore radiale centrale molto soffuso (solo in modalità attiva, spento in Ambient)
                    if (!isAmbient) {
                        val glowAlpha = breathGlowAlphaState?.value ?: 0.10f
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    tertiaryColor.copy(alpha = glowAlpha),
                                    primaryColor.copy(alpha = glowAlpha * 0.45f),
                                    Color.Transparent
                                ),
                                center = centerOffset,
                                radius = arcDiameter * 0.52f
                            ),
                            radius = arcDiameter * 0.52f,
                            center = centerOffset
                        )
                    }

                    // B) Traccia circolare sottile di fondo a 360°
                    drawArc(
                        color = ringTrackColor,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = arcTopLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                    )

                    // C) Arco perimetrale di avanzamento
                    val sweepAngle = when {
                        isPreparing -> (prepTimeLeftSeconds.toFloat() / prepTimeSeconds.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f) * 360f
                        isOvertime -> 360f
                        selectedTimeInSeconds == -1 -> if (isTimerRunning) 360f else 0f
                        else -> progress.coerceIn(0f, 1f) * 360f
                    }

                    val arcStroke = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)

                    if (isAmbient) {
                        // In Ambient: arco sottile monocromatico senza gradienti
                        drawArc(
                            color = Color.White,
                            startAngle = -90f,
                            sweepAngle = sweepAngle,
                            useCenter = false,
                            topLeft = arcTopLeft,
                            size = arcSize,
                            style = arcStroke
                        )
                    } else if (isOvertime) {
                        drawArc(
                            color = overtimeColor,
                            startAngle = -90f,
                            sweepAngle = sweepAngle,
                            useCenter = false,
                            topLeft = arcTopLeft,
                            size = arcSize,
                            style = arcStroke
                        )
                    } else if (isPreparing) {
                        drawArc(
                            color = prepColor,
                            startAngle = -90f,
                            sweepAngle = sweepAngle,
                            useCenter = false,
                            topLeft = arcTopLeft,
                            size = arcSize,
                            style = arcStroke
                        )
                    } else if (sweepAngle > 0.5f) {
                        // Sfumatura morbida tra primary e tertiary lungo il perimetro
                        drawArc(
                            brush = Brush.sweepGradient(
                                colors = listOf(primaryColor, tertiaryColor, primaryColor),
                                center = centerOffset
                            ),
                            startAngle = -90f,
                            sweepAngle = sweepAngle,
                            useCenter = false,
                            topLeft = arcTopLeft,
                            size = arcSize,
                            style = arcStroke
                        )
                    }
                }

                // 2. CONTENITORE INTERNO INSCRITTO (SAFE ZONE)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = diameter * 0.10f, vertical = diameter * 0.11f),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Fascia superiore (Ore 12)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = currentClockText,
                            fontSize = 10.sp,
                            color = if (isAmbient) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                            style = TextStyle(
                                fontFeatureSettings = "tnum",
                                fontWeight = FontWeight.Normal
                            )
                        )
                        Text(
                            text = when {
                                isPreparing -> stringResource(R.string.text_prepare)
                                isOvertime -> stringResource(R.string.status_extra)
                                else -> stringResource(R.string.status_still)
                            },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Normal,
                            maxLines = 1,
                            color = when {
                                isPreparing -> prepColor
                                isOvertime -> overtimeColor
                                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                            }
                        )
                    }

                    // Fascia centrale (Row)
                    val sideBtnSize = (diameter * 0.145f).coerceIn(26.dp, 32.dp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        // Pulsante circolare [-]
                        Box(
                            modifier = Modifier.size(sideBtnSize),
                            contentAlignment = Alignment.Center
                        ) {
                            if (showSideControls && !isAmbient) {
                                val leftEnabled = !isInitialState || canDecrease
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(sideBtnSize)
                                        .background(primaryColor.copy(alpha = if (leftEnabled) 0.12f else 0.04f), CircleShape)
                                        .border(1.dp, primaryColor.copy(alpha = if (leftEnabled) 0.40f else 0.15f), CircleShape)
                                        .clickable(
                                            enabled = leftEnabled,
                                            indication = null,
                                            interactionSource = remember { MutableInteractionSource() }
                                        ) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            if (isInitialState) {
                                                if (selectedTimeInSeconds > 60) {
                                                    updateTimeSelection(selectedTimeInSeconds - 60)
                                                } else if (selectedTimeInSeconds == 60) {
                                                    updateTimeSelection(-1)
                                                }
                                            } else {
                                                resetTimerState()
                                            }
                                        }
                                ) {
                                    Text(
                                        text = if (isInitialState) "−" else "✕",
                                        fontSize = if (isInitialState) 15.sp else 11.sp,
                                        fontWeight = FontWeight.Normal,
                                        color = if (leftEnabled) primaryColor else primaryColor.copy(alpha = 0.3f)
                                    )
                                }
                            }
                        }

                        // Testo del timer MM:SS
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = when {
                                    isPreparing -> "${prepTimeLeftSeconds}s"
                                    isOvertime -> "+${formatTime(overtimeSeconds)}"
                                    selectedTimeInSeconds == -1 && !isTimerRunning && timeLeftInSeconds == 0 -> "∞"
                                    else -> formatTime(timeLeftInSeconds)
                                },
                                fontSize = if (isOvertime) (diameter.value * 0.135f).sp else (diameter.value * 0.155f).sp,
                                maxLines = 1,
                                softWrap = false,
                                color = when {
                                    isPreparing -> prepColor
                                    isOvertime -> overtimeColor
                                    isAmbient -> Color.White
                                    else -> MaterialTheme.colorScheme.onBackground
                                },
                                style = TextStyle(
                                    fontFeatureSettings = "tnum",
                                    fontWeight = FontWeight.Light
                                )
                            )
                        }

                        // Pulsante circolare [+]
                        Box(
                            modifier = Modifier.size(sideBtnSize),
                            contentAlignment = Alignment.Center
                        ) {
                            if (showSideControls && !isAmbient) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(sideBtnSize)
                                        .background(primaryColor.copy(alpha = 0.12f), CircleShape)
                                        .border(1.dp, primaryColor.copy(alpha = 0.40f), CircleShape)
                                        .clickable(
                                            indication = null,
                                            interactionSource = remember { MutableInteractionSource() }
                                        ) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            if (isInitialState) {
                                                updateTimeSelection(if (selectedTimeInSeconds == -1) 60 else selectedTimeInSeconds + 60)
                                            } else {
                                                saveAndFinishSession(includeExtra = false)
                                            }
                                        }
                                ) {
                                    Text(
                                        text = if (isInitialState) "+" else "✓",
                                        fontSize = if (isInitialState) 15.sp else 12.sp,
                                        fontWeight = FontWeight.Normal,
                                        color = primaryColor
                                    )
                                }
                            }
                        }
                    }

                    // Fascia inferiore (Ore 6)
                    val bottomPillColor = when {
                        isAmbient -> Color.Transparent
                        isOvertime -> overtimeColor
                        isTimerRunning -> MaterialTheme.colorScheme.secondary
                        else -> primaryColor
                    }
                    val bottomPillTextColor = when {
                        isAmbient -> Color.White.copy(alpha = 0.8f)
                        isOvertime -> Color.Black
                        isTimerRunning -> MaterialTheme.colorScheme.onSecondary
                        else -> MaterialTheme.colorScheme.onPrimary
                    }
                    val pillWidth = (diameter * 0.46f).coerceIn(82.dp, 104.dp)

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Indicatore orizzontale delle 2 pagine (Timer / Impostazioni)
                        if (showSettingsPage && !isAmbient) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                repeat(2) { index ->
                                    val selected = pagerState.currentPage == index
                                    Box(
                                        modifier = Modifier
                                            .size(if (selected) 5.dp else 3.5.dp)
                                            .background(
                                                if (selected) primaryColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                                CircleShape
                                            )
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(width = pillWidth, height = 28.dp)
                                .then(
                                    if (isAmbient) {
                                        Modifier.border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                                    } else {
                                        Modifier
                                            .background(bottomPillColor, RoundedCornerShape(14.dp))
                                            .border(1.dp, bottomPillColor.copy(alpha = 0.8f), RoundedCornerShape(14.dp))
                                    }
                                )
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() }
                                ) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    if (isOvertime) {
                                        saveAndFinishSession(includeExtra = true)
                                    } else {
                                        if (!isTimerRunning && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                            }
                                        }
                                        isTimerRunning = !isTimerRunning
                                    }
                                }
                        ) {
                            val bottomText = when {
                                isOvertime -> stringResource(R.string.button_save_extra, (overtimeSeconds / 60))
                                isTimerRunning -> stringResource(R.string.button_pause)
                                !isInitialState -> stringResource(R.string.button_resume)
                                else -> stringResource(R.string.button_start)
                            }
                            Text(
                                text = bottomText,
                                fontSize = if (isOvertime || (!isInitialState && !isTimerRunning)) 10.sp else 11.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                color = bottomPillTextColor
                            )
                        }
                    }
                }
            } else {
                // ================= PAGINA 2: IMPOSTAZIONI (2 pagine fisse in verticale + rotella) =================
                val settingsPagerState = rememberPagerState(pageCount = { 2 })
                val settingsFocusRequester = remember { FocusRequester() }
                val isSoundEnabled = alertMode != ALERT_MODE_VIBRATION
                var previewGongJob by remember { mutableStateOf<Job?>(null) }

                fun playPreviewGong(freq: Double, vol: Int) {
                    previewGongJob?.cancel()
                    previewGongJob = coroutineScope.launch(Dispatchers.Default) {
                        playGongSound(context, freq, repeatCount = 1, volumePercent = vol)
                    }
                }

                LaunchedEffect(pagerState.currentPage) {
                    if (pagerState.currentPage == 1) {
                        settingsFocusRequester.requestFocus()
                    } else {
                        previewGongJob?.cancel()
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onRotaryScrollEvent { event ->
                            if (!settingsPagerState.isScrollInProgress) {
                                if (event.verticalScrollPixels > 0f && settingsPagerState.currentPage < 1) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    coroutineScope.launch { settingsPagerState.animateScrollToPage(1) }
                                } else if (event.verticalScrollPixels < 0f && settingsPagerState.currentPage > 0) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    coroutineScope.launch { settingsPagerState.animateScrollToPage(0) }
                                }
                            }
                            true
                        }
                        .focusRequester(settingsFocusRequester)
                        .focusable()
                ) {
                    VerticalPager(
                        state = settingsPagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { settingsPage ->
                        if (settingsPage == 0) {
                            // --- SOTTOPAGINA 1: MODALITA AVVISO + FREQUENZA + VOLUME ---
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(start = 10.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.SpaceEvenly
                            ) {
                                Text(
                                    text = "‹ " + stringResource(R.string.settings_title),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .clickable { coroutineScope.launch { pagerState.animateScrollToPage(0) } }
                                        .padding(horizontal = 10.dp, vertical = 2.dp)
                                )

                                // 1. MODALITA AVVISO (ENTRAMBI / CAMPANE / VIBRAZ.)
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = stringResource(R.string.settings_alert_mode),
                                        fontSize = 9.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val modeOptions = listOf(
                                            ALERT_MODE_BOTH to stringResource(R.string.alert_mode_both),
                                            ALERT_MODE_SOUND to stringResource(R.string.alert_mode_bell),
                                            ALERT_MODE_VIBRATION to stringResource(R.string.alert_mode_vibrate)
                                        )
                                        modeOptions.forEach { (modeValue, labelText) ->
                                            val isSelected = alertMode == modeValue
                                            Button(
                                                onClick = {
                                                    alertMode = modeValue
                                                    saveAlertMode(context, modeValue)
                                                    if (modeValue == ALERT_MODE_BOTH || modeValue == ALERT_MODE_VIBRATION) {
                                                        triggerHapticGong(context, repeatCount = 1)
                                                    }
                                                    if (modeValue == ALERT_MODE_BOTH || modeValue == ALERT_MODE_SOUND) {
                                                        playPreviewGong(gongFreqHz, gongVolumePercent)
                                                    } else {
                                                        previewGongJob?.cancel()
                                                    }
                                                },
                                                shape = RoundedCornerShape(13.dp),
                                                border = if (isSelected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                                                modifier = Modifier.height(26.dp),
                                                contentPadding = PaddingValues(horizontal = 7.dp, vertical = 0.dp),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                                                    contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            ) {
                                                Text(
                                                    text = labelText,
                                                    fontSize = 9.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    maxLines = 1,
                                                    softWrap = false
                                                )
                                            }
                                        }
                                    }
                                }

                                // 2. FREQUENZA CAMPANA TIBETANA
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = stringResource(R.string.settings_gong_sound),
                                        fontSize = 9.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    val gongRows = listOf(
                                        listOf(
                                            432.0 to stringResource(R.string.bell_name_crystal),
                                            528.0 to stringResource(R.string.bell_name_keisu)
                                        ),
                                        listOf(
                                            216.0 to stringResource(R.string.bell_name_gong),
                                            2640.0 to stringResource(R.string.bell_name_tingsha)
                                        )
                                    )
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        gongRows.forEach { rowItems ->
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                rowItems.forEach { (freq, freqLabel) ->
                                                    val isSelected = gongFreqHz == freq
                                                    Button(
                                                        onClick = {
                                                            gongFreqHz = freq
                                                            saveGongFrequency(context, freq)
                                                            playPreviewGong(freq, gongVolumePercent)
                                                        },
                                                        enabled = isSoundEnabled,
                                                        shape = RoundedCornerShape(13.dp),
                                                        border = if (isSelected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = if (isSoundEnabled) 0.35f else 0.15f)),
                                                        modifier = Modifier.height(26.dp),
                                                        contentPadding = PaddingValues(horizontal = 7.dp, vertical = 0.dp),
                                                        colors = ButtonDefaults.buttonColors(
                                                            containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isSoundEnabled) 0.25f else 0.10f),
                                                            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isSoundEnabled) 1f else 0.4f),
                                                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.10f),
                                                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                                        )
                                                    ) {
                                                        Text(
                                                            text = freqLabel,
                                                            fontSize = 9.sp,
                                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                            maxLines = 1,
                                                            softWrap = false
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // 3. VOLUME CAMPANA ([-]  100%  [+])
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = stringResource(R.string.settings_gong_volume),
                                        fontSize = 9.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Button(
                                            onClick = {
                                                if (gongVolumePercent > 20) {
                                                    val newVol = (gongVolumePercent - 20).coerceAtLeast(20)
                                                    gongVolumePercent = newVol
                                                    saveGongVolume(context, newVol)
                                                    playPreviewGong(gongFreqHz, newVol)
                                                }
                                            },
                                            enabled = isSoundEnabled && gongVolumePercent > 20,
                                            shape = RoundedCornerShape(12.dp),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = if (isSoundEnabled && gongVolumePercent > 20) 0.35f else 0.15f)),
                                            modifier = Modifier.size(width = 38.dp, height = 25.dp),
                                            contentPadding = PaddingValues(0.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isSoundEnabled && gongVolumePercent > 20) 0.25f else 0.10f),
                                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.10f),
                                                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                            )
                                        ) {
                                            Text("−", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        }

                                        Text(
                                            text = "$gongVolumePercent%",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            style = TextStyle(fontFeatureSettings = "tnum"),
                                            color = if (isSoundEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                        )

                                        Button(
                                            onClick = {
                                                if (gongVolumePercent < 100) {
                                                    val newVol = (gongVolumePercent + 20).coerceAtMost(100)
                                                    gongVolumePercent = newVol
                                                    saveGongVolume(context, newVol)
                                                    playPreviewGong(gongFreqHz, newVol)
                                                }
                                            },
                                            enabled = isSoundEnabled && gongVolumePercent < 100,
                                            shape = RoundedCornerShape(12.dp),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = if (isSoundEnabled && gongVolumePercent < 100) 0.35f else 0.15f)),
                                            modifier = Modifier.size(width = 38.dp, height = 25.dp),
                                            contentPadding = PaddingValues(0.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isSoundEnabled && gongVolumePercent < 100) 0.25f else 0.10f),
                                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.10f),
                                                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                            )
                                        ) {
                                            Text("+", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        } else {
                            // --- SOTTOPAGINA 2: TEMA COLORI + PREPARAZIONE + VERSIONE ---
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(start = 10.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.SpaceEvenly
                            ) {
                                Text(
                                    text = "⌃ " + stringResource(R.string.settings_title),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .clickable { coroutineScope.launch { settingsPagerState.animateScrollToPage(0) } }
                                        .padding(horizontal = 10.dp, vertical = 2.dp)
                                )

                                // 1. SELETTORE TEMA (4 PALLINI COLORATI SENZA ICONE)
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = stringResource(
                                            R.string.settings_color_theme,
                                            stringResource(selectedPalette.labelRes)
                                        ),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        StillColorPalette.entries.forEach { palette ->
                                            val isSelected = selectedPalette == palette
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .clickable(
                                                        indication = null,
                                                        interactionSource = remember { MutableInteractionSource() }
                                                    ) {
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        onPaletteSelected(palette)
                                                    }
                                            ) {
                                                if (isSelected) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(30.dp)
                                                            .background(palette.swatchColor.copy(alpha = 0.22f), CircleShape)
                                                    )
                                                }
                                                Box(
                                                    modifier = Modifier
                                                        .size(if (isSelected) 22.dp else 18.dp)
                                                        .background(palette.swatchColor, CircleShape)
                                                        .border(
                                                            width = if (isSelected) 2.dp else 1.dp,
                                                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.outline.copy(alpha = 0.40f),
                                                            shape = CircleShape
                                                        )
                                                )
                                            }
                                        }
                                    }
                                }

                                // 2. PREPARAZIONE (-5s / +5s)
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = stringResource(R.string.status_preparing),
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Button(
                                            onClick = {
                                                if (prepTimeSeconds >= 5) {
                                                    prepTimeSeconds -= 5
                                                    savePrepTime(context, prepTimeSeconds)
                                                }
                                            },
                                            enabled = prepTimeSeconds >= 5,
                                            shape = RoundedCornerShape(12.dp),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = if (prepTimeSeconds >= 5) 0.35f else 0.15f)),
                                            modifier = Modifier.size(width = 44.dp, height = 26.dp),
                                            contentPadding = PaddingValues(0.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (prepTimeSeconds >= 5) 0.25f else 0.10f),
                                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.10f),
                                                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                            )
                                        ) {
                                            Text("-5s", fontSize = 10.sp, fontWeight = FontWeight.Medium)
                                        }

                                        Text(
                                            text = "${prepTimeSeconds}s",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            style = TextStyle(fontFeatureSettings = "tnum"),
                                            color = prepColor
                                        )

                                        Button(
                                            onClick = {
                                                prepTimeSeconds += 5
                                                savePrepTime(context, prepTimeSeconds)
                                            },
                                            shape = RoundedCornerShape(12.dp),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                                            modifier = Modifier.size(width = 44.dp, height = 26.dp),
                                            contentPadding = PaddingValues(0.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        ) {
                                            Text("+5s", fontSize = 10.sp, fontWeight = FontWeight.Medium)
                                        }
                                    }
                                }

                                Text(
                                    text = stringResource(R.string.settings_app_version, BuildConfig.VERSION_NAME),
                                    fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Indicatore verticale delle 2 sottopagine Impostazioni (sul bordo destro)
                    Column(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 5.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        repeat(2) { idx ->
                            val selected = settingsPagerState.currentPage == idx
                            Box(
                                modifier = Modifier
                                    .size(if (selected) 6.dp else 4.dp)
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                        CircleShape
                                    )
                            )
                        }
                    }
                }
            }
        }
    }
}

fun formatTime(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}