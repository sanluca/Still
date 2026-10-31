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
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.sanluca.still.wear.R
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
private const val KEY_PENDING_SESSIONS = "pending_watch_sessions"

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

fun triggerHapticGong(context: Context) {
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

            val timings = longArrayOf(0, 500, 200, 800)
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
    var currentClockText by remember { mutableStateOf(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())) }

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
                currentClockText = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
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
        currentClockText = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
    }

    LaunchedEffect(Unit) {
        try {
            flushPendingSessionsToPhone(context)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        while (true) {
            currentClockText = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
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
            Toast.makeText(context, context.getString(R.string.toast_sync_success), Toast.LENGTH_SHORT).show()
        }

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

    val isInitialState = !isTimerRunning && !isOvertime && (timeLeftInSeconds == selectedTimeInSeconds || (selectedTimeInSeconds == -1 && timeLeftInSeconds == 0))

    // La pagina Impostazioni esiste solo quando il timer è fermo
    val showSettingsPage = !isTimerRunning && !isOvertime
    val pagerState = rememberPagerState(pageCount = { if (showSettingsPage) 2 else 1 })
    LaunchedEffect(showSettingsPage) {
        if (!showSettingsPage) pagerState.scrollToPage(0)
    }

    // Cerchio adattato all'altezza reale dello schermo per lasciare sempre spazio al pulsante sotto
    val circleSize = (LocalConfiguration.current.screenHeightDp * (if (isOvertime) 0.46f else 0.54f)).dp
        .coerceIn(92.dp, 124.dp)
    val crescentRadiusOffset = circleSize / 2 + 17.dp

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
            // ================= PAGINA 1: TIMER (fissa, centrata) =================
            if (page == 0) Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 4.dp, end = 4.dp, bottom = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = currentClockText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isAmbient && isTimerRunning) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(2.dp))

                // CERCHIO CON MEZZE LUNE [-] E [+] ATTORNO AL QUADRANTE
                val showCrescents = !isTimerRunning && !isOvertime
                val canDecrease = selectedTimeInSeconds != -1
                val crescentActiveColor = MaterialTheme.colorScheme.secondaryContainer
                val crescentDisabledColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(width = circleSize + 52.dp, height = circleSize + 8.dp)
                ) {
                    if (showCrescents) {
                        Canvas(modifier = Modifier.size(circleSize + 34.dp)) {
                            val strokePx = 16.dp.toPx()
                            // Mezza luna sinistra (-)
                            drawArc(
                                color = if (canDecrease) crescentActiveColor else crescentDisabledColor,
                                startAngle = 144f,
                                sweepAngle = 72f,
                                useCenter = false,
                                style = Stroke(width = strokePx, cap = StrokeCap.Round)
                            )
                            // Mezza luna destra (+)
                            drawArc(
                                color = crescentActiveColor,
                                startAngle = -36f,
                                sweepAngle = 72f,
                                useCenter = false,
                                style = Stroke(width = strokePx, cap = StrokeCap.Round)
                            )
                        }

                        // Tocco Mezza Luna Sinistra (- : centrato esattamente sull'arco sinistro)
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .offset(x = -crescentRadiusOffset)
                                .size(width = 32.dp, height = 88.dp)
                                .clickable(
                                    enabled = canDecrease,
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() }
                                ) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    if (selectedTimeInSeconds > 60) {
                                        updateTimeSelection(selectedTimeInSeconds - 60)
                                    } else if (selectedTimeInSeconds == 60) {
                                        updateTimeSelection(-1)
                                    }
                                }
                        ) {
                            Text(
                                text = "−",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (canDecrease) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                        }

                        // Tocco Mezza Luna Destra (+ : centrato esattamente sull'arco destro)
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .offset(x = crescentRadiusOffset)
                                .size(width = 32.dp, height = 88.dp)
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() }
                                ) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    updateTimeSelection(if (selectedTimeInSeconds == -1) 60 else selectedTimeInSeconds + 60)
                                }
                        ) {
                            Text(
                                text = "+",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(circleSize - 8.dp)
                    ) {
                        if (isPreparing) {
                            CircularProgressIndicator(
                                modifier = Modifier.fillMaxSize(),
                                color = prepColor,
                                strokeWidth = 6.dp
                            )
                        } else if (isOvertime) {
                            CircularProgressIndicator(
                                progress = { 1f },
                                modifier = Modifier.fillMaxSize(),
                                color = overtimeColor,
                                strokeWidth = 6.dp,
                                strokeCap = StrokeCap.Round
                            )
                        } else if (selectedTimeInSeconds != -1) {
                            CircularProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxSize(),
                                color = if (isAmbient && isTimerRunning) Color.White else MaterialTheme.colorScheme.primary,
                                trackColor = if (isAmbient && isTimerRunning) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant,
                                strokeWidth = 6.dp,
                                strokeCap = StrokeCap.Round
                            )
                        } else {
                            CircularProgressIndicator(
                                progress = { 1f },
                                modifier = Modifier.fillMaxSize(),
                                color = if (isAmbient && isTimerRunning) Color.White else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                strokeWidth = 4.dp
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = when {
                                    isPreparing -> stringResource(R.string.text_prepare)
                                    isOvertime -> stringResource(R.string.status_extra)
                                    else -> stringResource(R.string.status_still)
                                },
                                fontSize = 11.sp,
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
                                fontSize = if (isOvertime) 22.sp else 26.sp,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    isPreparing -> prepColor
                                    isOvertime -> overtimeColor
                                    else -> MaterialTheme.colorScheme.onBackground
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // PULSANTI PRINCIPALI (INIZIA / PAUSA / SALVA)
                if (isOvertime) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Button(
                            onClick = { saveAndFinishSession(includeExtra = true) },
                            modifier = Modifier.height(30.dp).fillMaxWidth(0.9f),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = overtimeColor)
                        ) {
                            Text(stringResource(R.string.button_save_extra, (overtimeSeconds / 60)), fontSize = 10.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            OutlinedButton(
                                onClick = { 
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
                                },
                                modifier = Modifier.height(28.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Text(stringResource(R.string.button_reset), fontSize = 10.sp)
                            }

                            OutlinedButton(
                                onClick = { saveAndFinishSession(includeExtra = false) },
                                modifier = Modifier.height(28.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Text(stringResource(R.string.button_only_planned), fontSize = 10.sp)
                            }
                        }
                    }
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { 
                                if (!isTimerRunning && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                }
                                isTimerRunning = !isTimerRunning 
                            },
                            modifier = Modifier.height(34.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
                        ) {
                            Text(
                                text = if (isTimerRunning) {
                                    stringResource(R.string.button_pause)
                                } else if (!isInitialState) {
                                    stringResource(R.string.button_resume)
                                } else {
                                    stringResource(R.string.button_start)
                                },
                                fontSize = if (!isInitialState && !isTimerRunning) 11.sp else 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        if (!isInitialState && !isTimerRunning) {
                            Button(
                                onClick = { saveAndFinishSession(includeExtra = false) },
                                modifier = Modifier.height(34.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                            ) {
                                Text("✓", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = { 
                                    timeLeftInSeconds = if (selectedTimeInSeconds == -1) 0 else selectedTimeInSeconds
                                    sessionStartTime = null 
                                },
                                modifier = Modifier.height(34.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Text(stringResource(R.string.button_reset), fontSize = 11.sp)
                            }
                        }
                    }
                }
            } else Column(
                // ================= PAGINA 2: IMPOSTAZIONI (compatta e fissa) =================
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                // Titolo cliccabile: un tap riporta subito alla pagina Timer
                Text(
                    text = "‹ " + stringResource(R.string.settings_title),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clickable { coroutineScope.launch { pagerState.animateScrollToPage(0) } }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )

                // GRUPPO 1: FREQUENZA CAMPANA TIBETANA
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.settings_gong_sound), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = gongFreqHz == 432.0,
                            onClick = { 
                                gongFreqHz = 432.0
                                saveGongFrequency(context, 432.0)
                                coroutineScope.launch(Dispatchers.Default) {
                                    playGongSound(432.0, repeatCount = 1)
                                }
                            },
                            label = { Text("432Hz", fontSize = 10.sp) }
                        )

                        FilterChip(
                            selected = gongFreqHz == 528.0,
                            onClick = { 
                                gongFreqHz = 528.0
                                saveGongFrequency(context, 528.0)
                                coroutineScope.launch(Dispatchers.Default) {
                                    playGongSound(528.0, repeatCount = 1)
                                }
                            },
                            label = { Text("528Hz", fontSize = 10.sp) }
                        )

                        FilterChip(
                            selected = gongFreqHz == 216.0,
                            onClick = { 
                                gongFreqHz = 216.0
                                saveGongFrequency(context, 216.0)
                                coroutineScope.launch(Dispatchers.Default) {
                                    playGongSound(216.0, repeatCount = 1)
                                }
                            },
                            label = { Text("216Hz", fontSize = 10.sp) }
                        )
                    }
                }

                // GRUPPO 2: TEMPO DI PREPARAZIONE COMPATTO [-5s]  10s  [+5s]
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.status_preparing),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                            modifier = Modifier.size(width = 44.dp, height = 30.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("-5s", fontSize = 10.sp)
                        }

                        Text(
                            text = "${prepTimeSeconds}s",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = prepColor
                        )

                        OutlinedButton(
                            onClick = { 
                                prepTimeSeconds += 5
                                savePrepTime(context, prepTimeSeconds)
                            },
                            modifier = Modifier.size(width = 44.dp, height = 30.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("+5s", fontSize = 10.sp)
                        }
                    }
                }

                Text(
                    text = stringResource(R.string.settings_app_version),
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Indicatore di pagina (visibile solo quando il timer è fermo)
        if (showSettingsPage) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                repeat(2) { index ->
                    val selected = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .size(if (selected) 6.dp else 4.dp)
                            .background(
                                if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                                CircleShape
                            )
                    )
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