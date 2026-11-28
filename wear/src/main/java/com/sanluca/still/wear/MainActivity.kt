@file:Suppress("WearMaterialTheme")

package com.sanluca.still.wear

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.wear.ambient.AmbientModeSupport
import androidx.wear.tiles.TileService
import com.sanluca.still.wear.ui.theme.OvertimeGold
import com.sanluca.still.wear.ui.theme.StillColorPalette
import com.sanluca.still.wear.ui.theme.StillTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity(), AmbientModeSupport.AmbientCallbackProvider {

    companion object {
        internal const val EXTRA_FROM_TILE = "from_tile"
    }

    private lateinit var ambientController: AmbientModeSupport.AmbientController
    private val isAmbientState = mutableStateOf(false)
    private val ambientUpdateTickState = mutableLongStateOf(0L)
    private val navigateToTimerTickState = mutableLongStateOf(0L)

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
                        try {
                            TileService.getUpdater(applicationContext).requestUpdate(StillTileService::class.java)
                        } catch (_: Exception) {
                        }
                    },
                    navigateToTimerTick = navigateToTimerTickState.longValue
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_FROM_TILE, false)) {
            navigateToTimerTickState.longValue += 1L
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
private fun WearMeditationApp(
    isAmbient: Boolean = false,
    ambientUpdateTick: Long = 0L,
    selectedPalette: StillColorPalette = StillColorPalette.AUTUMN,
    onPaletteSelected: (StillColorPalette) -> Unit = {},
    navigateToTimerTick: Long = 0L
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val stateHolder = rememberWearScreenStateHolder(context)
    val serviceSnapshot by StillTimerService.timerState.collectAsState()
    val currentIsAmbient by rememberUpdatedState(isAmbient)

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        stateHolder.onNotificationPermissionResult()
    }

    // Ricevitore dedicato ai soli tick di sistema dell'orologio (il timer è guidato unicamente dallo StateFlow)
    val clockReceiver = remember(stateHolder) {
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                stateHolder.refreshClock()
            }
        }
    }

    DisposableEffect(context, clockReceiver) {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ContextCompat.registerReceiver(
            context,
            clockReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose {
            context.unregisterReceiver(clockReceiver)
        }
    }

    LaunchedEffect(isAmbient, ambientUpdateTick) {
        stateHolder.flushAmbientTickIfNeeded()
    }

    // Aggiornamento periodico dell'ora solo con schermo attivo (in ambient bastano i tick di sistema)
    LaunchedEffect(isAmbient) {
        if (!isAmbient) {
            while (true) {
                stateHolder.refreshClock()
                delay(10_000L)
            }
        }
    }

    // Canale unico di stato dal Service (con buffering automatico in Ambient Mode)
    LaunchedEffect(serviceSnapshot) {
        stateHolder.onServiceSnapshot(serviceSnapshot, isAmbient = currentIsAmbient)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, stateHolder) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                stateHolder.onResume()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(stateHolder.isTimerRunning) {
        stateHolder.syncServiceForRunningState()
    }

    val backgroundColor = if (isAmbient) Color.Black else MaterialTheme.colorScheme.background
    val overtimeColor = if (isAmbient) Color.White else OvertimeGold
    val prepColor = if (isAmbient) Color.White else MaterialTheme.colorScheme.tertiary

    val showSettingsPage = stateHolder.showSettingsPage
    val pagerState = rememberPagerState(pageCount = { if (showSettingsPage) 2 else 1 })
    LaunchedEffect(showSettingsPage) {
        if (!showSettingsPage) pagerState.scrollToPage(0)
    }
    LaunchedEffect(navigateToTimerTick) {
        if (navigateToTimerTick > 0L) {
            pagerState.scrollToPage(0)
        }
    }

    val timerActions = remember(stateHolder, notificationPermissionLauncher) {
        WearTimerPageActions(
            onUpdateTimeSelection = stateHolder::updateTimeSelection,
            onResetTimer = stateHolder::resetTimerState,
            onSaveAndFinish = stateHolder::saveAndFinishSession,
            onToggleTimer = {
                stateHolder.toggleTimer { permission ->
                    notificationPermissionLauncher.launch(permission)
                }
            }
        )
    }

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
            if (page == 0) {
                WearTimerPage(
                    uiState = WearTimerPageUiState(
                        isAmbient = isAmbient,
                        isTimerRunning = stateHolder.isTimerRunning,
                        isPreparing = stateHolder.isPreparing,
                        isOvertime = stateHolder.isOvertime,
                        isInitialState = stateHolder.isInitialState,
                        selectedTimeInSeconds = stateHolder.selectedTimeInSeconds,
                        timeLeftInSeconds = stateHolder.timeLeftInSeconds,
                        prepTimeSeconds = stateHolder.prepTimeSeconds,
                        prepTimeLeftSeconds = stateHolder.prepTimeLeftSeconds,
                        overtimeSeconds = stateHolder.overtimeSeconds,
                        progress = stateHolder.progress,
                        currentClockText = stateHolder.currentClockText,
                        overtimeColor = overtimeColor,
                        prepColor = prepColor,
                        showSettingsPage = showSettingsPage,
                        currentPage = pagerState.currentPage
                    ),
                    actions = timerActions
                )
            } else {
                WearSettingsPage(
                    isParentSettingsPageVisible = pagerState.currentPage == 1,
                    alertMode = stateHolder.alertMode,
                    onAlertModeChange = stateHolder::updateAlertMode,
                    gongFreqHz = stateHolder.gongFreqHz,
                    onGongFreqChange = stateHolder::updateGongFreq,
                    gongVolumePercent = stateHolder.gongVolumePercent,
                    onGongVolumeChange = stateHolder::updateGongVolume,
                    selectedPalette = selectedPalette,
                    onPaletteSelected = onPaletteSelected,
                    prepTimeSeconds = stateHolder.prepTimeSeconds,
                    onPrepTimeChange = stateHolder::updatePrepTime,
                    prepColor = prepColor,
                    onBackToTimer = {
                        coroutineScope.launch { pagerState.animateScrollToPage(0) }
                    },
                    resetSubpageTick = navigateToTimerTick
                )
            }
        }
    }
}
