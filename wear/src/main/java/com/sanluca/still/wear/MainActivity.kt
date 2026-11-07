@file:Suppress("WearMaterialTheme")

package com.sanluca.still.wear

import com.sanluca.still.wear.ui.theme.OvertimeGold
import com.sanluca.still.wear.ui.theme.StillTheme
import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.google.android.gms.wearable.MessageClient
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
            StillTheme {
                WearMeditationApp(
                    isAmbient = isAmbientState.value,
                    ambientUpdateTick = ambientUpdateTickState.value
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
private const val KEY_PENDING_SESSIONS = "pending_watch_sessions"

const val ALERT_MODE_BOTH = 0
const val ALERT_MODE_SOUND = 1
const val ALERT_MODE_VIBRATION = 2

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
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build()

            val timings = if (repeatCount >= 3) {
                longArrayOf(0, 500, 200, 800, 600, 500, 200, 800, 600, 500, 200, 800)
            } else {
                longArrayOf(0, 500, 200, 800)
            }
            val effect = VibrationEffect.createWaveform(timings, -1)
            vibrator.vibrate(effect, audioAttributes)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

@Composable
fun WearMeditationApp(isAmbient: Boolean = false, ambientUpdateTick: Long = 0L) {
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
        while (true) {
            currentClockText = clockFormatter.format(Date())
            delay(10_000L)
        }
    }

    DisposableEffect(context) {
        val messageClient = Wearable.getMessageClient(context)
        val listener = MessageClient.OnMessageReceivedListener { messageEvent ->
            if (messageEvent.path == "/update_settings") {
                val raw = String(messageEvent.data)
                val parts = raw.split("|")
                if (parts.isNotEmpty()) {
                    val prepSec = parts[0].toIntOrNull() ?: 0
                    prepTimeSeconds = prepSec
                    savePrepTime(context, prepSec)
                }
            }
        }
        messageClient.addListener(listener)
        onDispose {
            messageClient.removeListener(listener)
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

    val backgroundColor = if (isAmbient && isTimerRunning) Color.Black else MaterialTheme.colorScheme.background
    val overtimeColor = if (isAmbient && isTimerRunning) Color.White else OvertimeGold
    val prepColor = if (isAmbient && isTimerRunning) Color.White else MaterialTheme.colorScheme.tertiary

    LaunchedEffect(isTimerRunning) {
        val activity = context.findActivity()
        if (isTimerRunning) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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

    val isInitialState = !isTimerRunning && !isOvertime && (timeLeftInSeconds == selectedTimeInSeconds || (selectedTimeInSeconds == -1 && timeLeftInSeconds == 0))

    // La pagina Impostazioni esiste solo quando il timer è fermo
    val showSettingsPage = !isTimerRunning && !isOvertime
    val pagerState = rememberPagerState(pageCount = { if (showSettingsPage) 2 else 1 })
    LaunchedEffect(showSettingsPage) {
        if (!showSettingsPage) pagerState.scrollToPage(0)
    }

    // Dimensioni edge-to-edge dinamiche: partiamo dal bordo reale dello schermo verso l'interno
    val config = LocalConfiguration.current
    val screenDiameterDp = minOf(config.screenWidthDp, config.screenHeightDp).dp
    val outerMarginDp = 4.dp
    val ringGapDp = 5.dp
    val crescentThicknessDp = (screenDiameterDp * 0.12f).coerceIn(22.dp, 28.dp)
    val circleSize = (screenDiameterDp - (outerMarginDp + crescentThicknessDp + ringGapDp) * 2)
        .coerceAtLeast(112.dp)
    val crescentCenterOffset = circleSize / 2 + ringGapDp + crescentThicknessDp / 2

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            // ================= PAGINA 1: TIMER (corona concentrica a filo ghiera) =================
            if (page == 0) Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                val isPausedState = !isInitialState && !isTimerRunning && !isOvertime
                val showSideCrescents = isInitialState || isPausedState || isOvertime
                val canDecrease = selectedTimeInSeconds != -1

                val leftInteractionSource = remember { MutableInteractionSource() }
                val rightInteractionSource = remember { MutableInteractionSource() }
                val bottomInteractionSource = remember { MutableInteractionSource() }
                val isLeftPressed by leftInteractionSource.collectIsPressedAsState()
                val isRightPressed by rightInteractionSource.collectIsPressedAsState()
                val isBottomPressed by bottomInteractionSource.collectIsPressedAsState()

                val crescentSecondaryColor = MaterialTheme.colorScheme.secondaryContainer
                val crescentConfirmColor = MaterialTheme.colorScheme.secondary
                val crescentPrimaryColor = MaterialTheme.colorScheme.primary
                val crescentPressedColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                val crescentPassiveColor = MaterialTheme.colorScheme.surfaceVariant
                val crescentDisabledColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)

                // 1. DISEGNO GHIERA CIRCOLARE COMPLETA A 360° (4 SETTORI INCASTRATI: ORE 12, 3, 6, 9)
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokePx = crescentThicknessDp.toPx()
                    val gapPx = 3.dp.toPx()
                    val trackRadius = crescentCenterOffset.toPx()
                    val arcDiameter = trackRadius * 2f
                    val arcTopLeft = androidx.compose.ui.geometry.Offset(
                        x = (size.width - arcDiameter) / 2f,
                        y = (size.height - arcDiameter) / 2f
                    )
                    val arcSize = androidx.compose.ui.geometry.Size(arcDiameter, arcDiameter)
                    val segmentStroke = Stroke(width = strokePx, cap = StrokeCap.Round)
                    val cutoutStroke = Stroke(width = strokePx + gapPx * 2f, cap = StrokeCap.Round)

                    if (!(isAmbient && isTimerRunning)) {
                        // A) Archi laterali (Ore 9 e Ore 3) che scorrono da sotto la lunetta superiore a sotto il pulsante inferiore
                        val leftColor = when {
                            !showSideCrescents -> crescentPassiveColor
                            isInitialState && !canDecrease -> crescentDisabledColor
                            isLeftPressed -> crescentPressedColor
                            else -> crescentSecondaryColor
                        }
                        val rightColor = when {
                            !showSideCrescents -> crescentPassiveColor
                            isRightPressed -> crescentPressedColor
                            isPausedState || isOvertime -> crescentConfirmColor
                            else -> crescentSecondaryColor
                        }

                        // Sinistra (Ore 9): da 115° (sotto l'incastro inferiore) fino a 250° (sotto l'incastro superiore)
                        drawArc(
                            color = leftColor,
                            startAngle = 115f,
                            sweepAngle = 135f,
                            useCenter = false,
                            topLeft = arcTopLeft,
                            size = arcSize,
                            style = segmentStroke
                        )

                        // Destra (Ore 3): da -70° (sotto l'incastro superiore) fino a +65° (sotto l'incastro inferiore)
                        drawArc(
                            color = rightColor,
                            startAngle = -70f,
                            sweepAngle = 135f,
                            useCenter = false,
                            topLeft = arcTopLeft,
                            size = arcSize,
                            style = segmentStroke
                        )

                        // B) Maschera e Lunetta Superiore (Ore 12 = 270°, da 240° a 300°) per l'orologio HH:mm
                        drawArc(
                            color = backgroundColor,
                            startAngle = 240f,
                            sweepAngle = 60f,
                            useCenter = false,
                            topLeft = arcTopLeft,
                            size = arcSize,
                            style = cutoutStroke
                        )
                        drawArc(
                            color = crescentPassiveColor,
                            startAngle = 240f,
                            sweepAngle = 60f,
                            useCenter = false,
                            topLeft = arcTopLeft,
                            size = arcSize,
                            style = segmentStroke
                        )

                        // C) Maschera di scavo concavo (( )) attorno al pulsante inferiore
                        drawArc(
                            color = backgroundColor,
                            startAngle = 54f,
                            sweepAngle = 72f,
                            useCenter = false,
                            topLeft = arcTopLeft,
                            size = arcSize,
                            style = cutoutStroke
                        )
                    }

                    // D) Pulsante inferiore (Ore 6 = 90°): da 54° a 126°, incastrato dentro i due archi laterali
                    val bottomBaseColor = when {
                        isAmbient && isTimerRunning -> Color.DarkGray.copy(alpha = 0.5f)
                        isOvertime -> overtimeColor
                        isTimerRunning -> crescentSecondaryColor
                        else -> crescentPrimaryColor
                    }
                    val bottomColor = if (isBottomPressed) {
                        bottomBaseColor.copy(alpha = 0.75f)
                    } else {
                        bottomBaseColor
                    }

                    drawArc(
                        color = bottomColor,
                        startAngle = 54f,
                        sweepAngle = 72f,
                        useCenter = false,
                        topLeft = arcTopLeft,
                        size = arcSize,
                        style = segmentStroke
                    )
                }

                // 2. OROLOGIO INCASTONATO NELLA LUNETTA SUPERIORE (Ore 12)
                Text(
                    text = currentClockText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isAmbient && isTimerRunning) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.offset(y = -crescentCenterOffset)
                )

                // 3. QUADRANTE CENTRALE DEL TIMER (più ampio, proporzionato allo schermo)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(circleSize)
                ) {
                    if (isPreparing) {
                        CircularProgressIndicator(
                            modifier = Modifier.fillMaxSize(),
                            color = prepColor,
                            strokeWidth = 7.dp
                        )
                    } else if (isOvertime) {
                        CircularProgressIndicator(
                            progress = { 1f },
                            modifier = Modifier.fillMaxSize(),
                            color = overtimeColor,
                            strokeWidth = 7.dp,
                            strokeCap = StrokeCap.Round
                        )
                    } else if (selectedTimeInSeconds != -1) {
                        CircularProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxSize(),
                            color = if (isAmbient && isTimerRunning) Color.White else MaterialTheme.colorScheme.primary,
                            trackColor = if (isAmbient && isTimerRunning) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant,
                            strokeWidth = 7.dp,
                            strokeCap = StrokeCap.Round
                        )
                    } else {
                        CircularProgressIndicator(
                            progress = { 1f },
                            modifier = Modifier.fillMaxSize(),
                            color = if (isAmbient && isTimerRunning) Color.White else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            strokeWidth = 5.dp
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = when {
                                isPreparing -> stringResource(R.string.text_prepare)
                                isOvertime -> stringResource(R.string.status_extra)
                                else -> stringResource(R.string.status_still)
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                isPreparing -> prepColor
                                isOvertime -> overtimeColor
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        Text(
                            text = when {
                                isPreparing -> "${prepTimeLeftSeconds}s"
                                isOvertime -> "+${formatTime(overtimeSeconds)}"
                                selectedTimeInSeconds == -1 && !isTimerRunning && timeLeftInSeconds == 0 -> "∞"
                                else -> formatTime(timeLeftInSeconds)
                            },
                            fontSize = if (isOvertime) 26.sp else 30.sp,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                isPreparing -> prepColor
                                isOvertime -> overtimeColor
                                else -> MaterialTheme.colorScheme.onBackground
                            }
                        )

                        // Indicatore orizzontale delle 2 pagine (Timer / Impostazioni) sotto il numero
                        if (showSettingsPage) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                repeat(2) { index ->
                                    val selected = pagerState.currentPage == index
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

                // 4. AREE DI TOCCO ED ETICHETTE DELLE 3 MEZZE LUNE
                if (showSideCrescents) {
                    // Mezza Luna Sinistra (Ore 9): [−] a timer fermo, [✕] (Reset) in Pausa o Extra
                    val leftEnabled = !isInitialState || canDecrease
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .offset(x = -crescentCenterOffset)
                            .size(width = crescentThicknessDp + 10.dp, height = 96.dp)
                            .clickable(
                                enabled = leftEnabled,
                                indication = null,
                                interactionSource = leftInteractionSource
                            ) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                if (isInitialState) {
                                    if (selectedTimeInSeconds > 60) {
                                        updateTimeSelection(selectedTimeInSeconds - 60)
                                    } else if (selectedTimeInSeconds == 60) {
                                        updateTimeSelection(-1)
                                    }
                                } else {
                                    // Reset da Pausa o da Extra
                                    resetTimerState()
                                }
                            }
                    ) {
                        Text(
                            text = if (isInitialState) "−" else "✕",
                            fontSize = if (isInitialState) 18.sp else 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (leftEnabled) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            }
                        )
                    }

                    // Mezza Luna Destra (Ore 3): [+] a timer fermo, [✓] (Salva previsto) in Pausa o Extra
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .offset(x = crescentCenterOffset)
                            .size(width = crescentThicknessDp + 10.dp, height = 96.dp)
                            .clickable(
                                indication = null,
                                interactionSource = rightInteractionSource
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
                            fontSize = if (isInitialState) 18.sp else 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isInitialState) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSecondary
                            }
                        )
                    }
                }

                // Mezza Luna Inferiore (Ore 6): [Inizia] / [Pausa] / [Riprendi] / [Salva +Xm]
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .offset(y = crescentCenterOffset)
                        .size(width = 108.dp, height = crescentThicknessDp + 10.dp)
                        .clickable(
                            indication = null,
                            interactionSource = bottomInteractionSource
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
                    val bottomTextColor = when {
                        isAmbient && isTimerRunning -> Color.White.copy(alpha = 0.8f)
                        isOvertime -> Color.Black
                        isTimerRunning -> MaterialTheme.colorScheme.onSecondaryContainer
                        else -> MaterialTheme.colorScheme.onPrimary
                    }
                    Text(
                        text = bottomText,
                        fontSize = if (isOvertime || (!isInitialState && !isTimerRunning)) 11.sp else 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        color = bottomTextColor
                    )
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
                        playGongSound(freq, repeatCount = 1, volumePercent = vol)
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
                                                modifier = Modifier.height(26.dp),
                                                contentPadding = PaddingValues(horizontal = 7.dp, vertical = 0.dp),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                                    contentColor = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
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
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        listOf(432.0 to "432Hz", 528.0 to "528Hz", 216.0 to "216Hz").forEach { (freq, freqLabel) ->
                                            val isSelected = gongFreqHz == freq
                                            Button(
                                                onClick = {
                                                    gongFreqHz = freq
                                                    saveGongFrequency(context, freq)
                                                    playPreviewGong(freq, gongVolumePercent)
                                                },
                                                enabled = isSoundEnabled,
                                                modifier = Modifier.height(26.dp),
                                                contentPadding = PaddingValues(horizontal = 7.dp, vertical = 0.dp),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                                    contentColor = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
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
                                        OutlinedButton(
                                            onClick = {
                                                if (gongVolumePercent > 20) {
                                                    val newVol = (gongVolumePercent - 20).coerceAtLeast(20)
                                                    gongVolumePercent = newVol
                                                    saveGongVolume(context, newVol)
                                                    playPreviewGong(gongFreqHz, newVol)
                                                }
                                            },
                                            enabled = isSoundEnabled && gongVolumePercent > 20,
                                            modifier = Modifier.size(width = 38.dp, height = 25.dp),
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            Text("−", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        }

                                        Text(
                                            text = "$gongVolumePercent%",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSoundEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                        )

                                        OutlinedButton(
                                            onClick = {
                                                if (gongVolumePercent < 100) {
                                                    val newVol = (gongVolumePercent + 20).coerceAtMost(100)
                                                    gongVolumePercent = newVol
                                                    saveGongVolume(context, newVol)
                                                    playPreviewGong(gongFreqHz, newVol)
                                                }
                                            },
                                            enabled = isSoundEnabled && gongVolumePercent < 100,
                                            modifier = Modifier.size(width = 38.dp, height = 25.dp),
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            Text("+", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        } else {
                            // --- SOTTOPAGINA 2: PREPARAZIONE & VERSIONE ---
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(start = 10.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
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

                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = stringResource(R.string.status_preparing),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                if (prepTimeSeconds >= 5) {
                                                    prepTimeSeconds -= 5
                                                    savePrepTime(context, prepTimeSeconds)
                                                }
                                            },
                                            enabled = prepTimeSeconds >= 5,
                                            modifier = Modifier.size(width = 48.dp, height = 30.dp),
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            Text("-5s", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                        }

                                        Text(
                                            text = "${prepTimeSeconds}s",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = prepColor
                                        )

                                        OutlinedButton(
                                            onClick = {
                                                prepTimeSeconds += 5
                                                savePrepTime(context, prepTimeSeconds)
                                            },
                                            modifier = Modifier.size(width = 48.dp, height = 30.dp),
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            Text("+5s", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                        }
                                    }
                                }

                                Text(
                                    text = stringResource(R.string.settings_app_version, BuildConfig.VERSION_NAME),
                                    fontSize = 10.sp,
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
    return String.format("%02d:%02d", minutes, seconds)
}