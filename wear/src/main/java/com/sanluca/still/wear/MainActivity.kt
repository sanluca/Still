@file:Suppress("WearMaterialTheme")

package com.sanluca.still.wear

import com.sanluca.still.wear.ui.theme.OvertimeGold
import com.sanluca.still.wear.ui.theme.StillColorPalette
import com.sanluca.still.wear.ui.theme.StillTheme
import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.wear.ambient.AmbientModeSupport
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale

fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private class PendingAmbientTick {
    var hasPending: Boolean = false
    var isPreparing: Boolean = false
    var prepLeft: Int = 0
    var timeLeft: Int = 0
    var isOvertime: Boolean = false
    var overtimeSeconds: Int = 0
    var initialTime: Int = 300
}

class MainActivity : FragmentActivity(), AmbientModeSupport.AmbientCallbackProvider {

    private lateinit var ambientController: AmbientModeSupport.AmbientController
    private val isAmbientState = mutableStateOf(false)
    private val ambientUpdateTickState = mutableLongStateOf(0L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ambientController = AmbientModeSupport.attach(this)
        ambientController.setAutoResumeEnabled(true)
        setContent {
            var selectedPalette by remember { mutableStateOf(getSavedColorPalette(this)) }
            StillTheme(palette = selectedPalette) {
                WearMeditationApp(
                    isAmbient = isAmbientState.value,
                    ambientUpdateTick = ambientUpdateTickState.longValue,
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
            ambientUpdateTickState.longValue = System.currentTimeMillis()
        }

        override fun onUpdateAmbient() {
            super.onUpdateAmbient()
            ambientUpdateTickState.longValue = System.currentTimeMillis()
        }

        override fun onExitAmbient() {
            super.onExitAmbient()
            isAmbientState.value = false
            ambientUpdateTickState.longValue = System.currentTimeMillis()
        }
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
    val coroutineScope = rememberCoroutineScope()

    val heartRatePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if ((granted || hasHeartRatePermission(context)) && StillTimerService.isServiceRunning) {
            val hrIntent = Intent(context, StillTimerService::class.java).apply {
                action = StillTimerService.ACTION_START_HR_SAMPLING
            }
            context.startService(hrIntent)
        }
    }

    fun requestHeartRatePermissionIfNeeded() {
        if (!hasHeartRatePermission(context)) {
            heartRatePermissionLauncher.launch(requiredHeartRatePermission())
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        requestHeartRatePermissionIfNeeded()
    }

    var selectedTimeInSeconds by remember { mutableIntStateOf(getSavedSelectedTime(context)) }
    
    var prepTimeSeconds by remember { mutableIntStateOf(getSavedPrepTime(context)) }
    var gongFreqHz by remember { mutableDoubleStateOf(getSavedGongFrequency(context)) }
    var gongVolumePercent by remember { mutableIntStateOf(getSavedGongVolume(context)) }
    var alertMode by remember { mutableIntStateOf(getSavedAlertMode(context)) }
    val clockFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    var currentClockText by remember { mutableStateOf(clockFormatter.format(Date())) }

    var isPreparing by remember { mutableStateOf(false) }
    var prepTimeLeftSeconds by remember { mutableIntStateOf(0) }

    var timeLeftInSeconds by remember {
        mutableIntStateOf(if (selectedTimeInSeconds == -1) 0 else selectedTimeInSeconds)
    }

    var isOvertime by remember { mutableStateOf(false) }
    var overtimeSeconds by remember { mutableIntStateOf(0) }

    var isTimerRunning by remember { mutableStateOf(StillTimerService.isServiceRunning) }
    var sessionStartTime by remember { mutableStateOf<Instant?>(null) }

    val currentIsAmbient by rememberUpdatedState(isAmbient)
    val pendingAmbientTick = remember { PendingAmbientTick() }

    fun applyTickState(
        tickIsPreparing: Boolean,
        tickPrepLeft: Int,
        tickTimeLeft: Int,
        tickIsOvertime: Boolean,
        tickOvertimeSec: Int,
        tickInitialTime: Int
    ) {
        isPreparing = tickIsPreparing
        prepTimeLeftSeconds = tickPrepLeft
        timeLeftInSeconds = tickTimeLeft
        isOvertime = tickIsOvertime
        overtimeSeconds = tickOvertimeSec
        selectedTimeInSeconds = tickInitialTime

        if (sessionStartTime == null && !tickIsPreparing && isTimerRunning) {
            sessionStartTime = Instant.now()
        }
    }

    // Broadcast receiver for timer ticks and system clock ticks
    val receiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == StillTimerService.ACTION_TIMER_TICK) {
                    if (!isTimerRunning) {
                        return
                    }
                    val tickIsPreparing = intent.getBooleanExtra(StillTimerService.EXTRA_IS_PREPARING, false)
                    val tickPrepLeft = intent.getIntExtra(StillTimerService.EXTRA_PREP_LEFT, 0)
                    val tickTimeLeft = intent.getIntExtra(StillTimerService.EXTRA_TIME_LEFT, 0)
                    val tickIsOvertime = intent.getBooleanExtra(StillTimerService.EXTRA_IS_OVERTIME_STATE, false)
                    val tickOvertimeSec = intent.getIntExtra(StillTimerService.EXTRA_OVERTIME_SECONDS_STATE, 0)
                    val tickInitialTime = intent.getIntExtra(StillTimerService.EXTRA_INITIAL_TIME_STATE, 300)

                    if (currentIsAmbient && !(tickIsOvertime && !isOvertime)) {
                        // In Ambient Mode evita ricomposizioni Compose ogni 500 ms: applica su onUpdateAmbient / onExitAmbient
                        pendingAmbientTick.isPreparing = tickIsPreparing
                        pendingAmbientTick.prepLeft = tickPrepLeft
                        pendingAmbientTick.timeLeft = tickTimeLeft
                        pendingAmbientTick.isOvertime = tickIsOvertime
                        pendingAmbientTick.overtimeSeconds = tickOvertimeSec
                        pendingAmbientTick.initialTime = tickInitialTime
                        pendingAmbientTick.hasPending = true
                    } else {
                        pendingAmbientTick.hasPending = false
                        applyTickState(
                            tickIsPreparing,
                            tickPrepLeft,
                            tickTimeLeft,
                            tickIsOvertime,
                            tickOvertimeSec,
                            tickInitialTime
                        )
                    }
                } else {
                    currentClockText = clockFormatter.format(Date())
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
        if (pendingAmbientTick.hasPending && isTimerRunning) {
            pendingAmbientTick.hasPending = false
            applyTickState(
                pendingAmbientTick.isPreparing,
                pendingAmbientTick.prepLeft,
                pendingAmbientTick.timeLeft,
                pendingAmbientTick.isOvertime,
                pendingAmbientTick.overtimeSeconds,
                pendingAmbientTick.initialTime
            )
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
        saveSelectedTime(context, newTime)
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
                try {
                    flushPendingSessionsToPhone(context)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
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
        StillTimerService.clearHeartRateSamples()
        pendingAmbientTick.hasPending = false

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
            val hrSummary = StillTimerService.getHeartRateSummary()
            sendSessionToPhone(
                context = context,
                durationSec = totalSec,
                startInstant = start,
                endInstant = end,
                startBpm = hrSummary.startBpm,
                endBpm = hrSummary.endBpm,
                avgBpm = hrSummary.avgBpm
            )
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
            if (page == 0) {
                WearTimerPage(
                    isAmbient = isAmbient,
                    isTimerRunning = isTimerRunning,
                    isPreparing = isPreparing,
                    isOvertime = isOvertime,
                    isInitialState = isInitialState,
                    selectedTimeInSeconds = selectedTimeInSeconds,
                    timeLeftInSeconds = timeLeftInSeconds,
                    prepTimeSeconds = prepTimeSeconds,
                    prepTimeLeftSeconds = prepTimeLeftSeconds,
                    overtimeSeconds = overtimeSeconds,
                    progress = progress,
                    currentClockText = currentClockText,
                    overtimeColor = overtimeColor,
                    prepColor = prepColor,
                    showSettingsPage = showSettingsPage,
                    currentPage = pagerState.currentPage,
                    onUpdateTimeSelection = { updateTimeSelection(it) },
                    onResetTimer = { resetTimerState() },
                    onSaveAndFinish = { includeExtra -> saveAndFinishSession(includeExtra = includeExtra) },
                    onToggleTimer = {
                        if (!isTimerRunning) {
                            val needsNotificationPermission =
                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.POST_NOTIFICATIONS
                                    ) != PackageManager.PERMISSION_GRANTED
                            if (needsNotificationPermission) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                requestHeartRatePermissionIfNeeded()
                            }
                        }
                        isTimerRunning = !isTimerRunning
                    }
                )
            } else {
                WearSettingsPage(
                    isParentSettingsPageVisible = pagerState.currentPage == 1,
                    alertMode = alertMode,
                    onAlertModeChange = { modeValue ->
                        alertMode = modeValue
                        saveAlertMode(context, modeValue)
                    },
                    gongFreqHz = gongFreqHz,
                    onGongFreqChange = { freq ->
                        gongFreqHz = freq
                        saveGongFrequency(context, freq)
                    },
                    gongVolumePercent = gongVolumePercent,
                    onGongVolumeChange = { newVol ->
                        gongVolumePercent = newVol
                        saveGongVolume(context, newVol)
                    },
                    selectedPalette = selectedPalette,
                    onPaletteSelected = onPaletteSelected,
                    prepTimeSeconds = prepTimeSeconds,
                    onPrepTimeChange = { newPrep ->
                        prepTimeSeconds = newPrep
                        savePrepTime(context, newPrep)
                    },
                    prepColor = prepColor,
                    onBackToTimer = {
                        coroutineScope.launch { pagerState.animateScrollToPage(0) }
                    }
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