package com.sanluca.still

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

class MainActivity : ComponentActivity() {
    companion object {
        private var hasShownOpeningQuoteInProcess = false
    }

    private val showHealthRationaleState = mutableStateOf(false)
    private val showOpeningQuoteState = mutableStateOf(false)
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
        val isHealthRationaleIntent = checkIsHealthRationale(intent)
        showHealthRationaleState.value = isHealthRationaleIntent
        val isTimerActiveOrPaused = StillTimerService.isRunning ||
            StillTimerService.timerState.value.isRunning ||
            StillTimerService.timerState.value.isPaused
        if (savedInstanceState == null &&
            !hasShownOpeningQuoteInProcess &&
            !isTimerActiveOrPaused &&
            !isHealthRationaleIntent
        ) {
            hasShownOpeningQuoteInProcess = true
            showOpeningQuoteState.value = true
        }
        appUpdateManager = AppUpdateManagerFactory.create(this)
        appUpdateManager.registerListener(installStateUpdatedListener)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent {
            var selectedPalette by remember { mutableStateOf(getSavedColorPalette(this)) }
            var presetPaletteOverride by remember {
                mutableStateOf(getActivePreset(this)?.effectiveColorPalette())
            }
            var isWhatsNewVisible by remember { mutableStateOf(false) }
            var isWhatsNewEvaluated by remember { mutableStateOf(false) }
            val effectivePalette = presetPaletteOverride ?: selectedPalette
            val snackbarHostState = remember { SnackbarHostState() }
            val updateReadyMsg = stringResource(R.string.update_ready_message)
            val updateReadyAction = stringResource(R.string.update_ready_action)
            val updateLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartIntentSenderForResult()
            ) { _ ->
                // Se l'utente rifiuta o annulla, non riproponiamo nello stesso avvio
            }

            LaunchedEffect(
                showOpeningQuoteState.value,
                showHealthRationaleState.value,
                isWhatsNewVisible,
                isWhatsNewEvaluated
            ) {
                if (!showOpeningQuoteState.value &&
                    !showHealthRationaleState.value &&
                    isWhatsNewEvaluated &&
                    !isWhatsNewVisible
                ) {
                    checkForPlayStoreUpdate(updateLauncher)
                }
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

            StillTheme(palette = effectivePalette) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
                    ) { innerPadding ->
                        MeditationTimerScreen(
                            modifier = Modifier.padding(innerPadding),
                            selectedPalette = selectedPalette,
                            onPaletteSelected = { newPalette ->
                                selectedPalette = newPalette
                                saveColorPalette(this@MainActivity, newPalette)
                            },
                            showHealthRationale = showHealthRationaleState.value,
                            onDismissHealthRationale = { showHealthRationaleState.value = false },
                            onPresetPaletteOverrideChange = { overridePalette ->
                                presetPaletteOverride = overridePalette
                            },
                            showOpeningQuote = showOpeningQuoteState.value,
                            onDismissOpeningQuote = { showOpeningQuoteState.value = false },
                            onWhatsNewStateChange = { visible, evaluated ->
                                isWhatsNewVisible = visible
                                isWhatsNewEvaluated = evaluated
                            }
                        )
                    }

                    if (showOpeningQuoteState.value) {
                        StillOpeningQuoteOverlay(
                            onDismiss = { showOpeningQuoteState.value = false }
                        )
                    }
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
            showOpeningQuoteState.value = false
            showHealthRationaleState.value = true
        } else if (StillTimerService.isRunning ||
            StillTimerService.timerState.value.isRunning ||
            StillTimerService.timerState.value.isPaused
        ) {
            showOpeningQuoteState.value = false
        }
    }

    override fun onDestroy() {
        if (::appUpdateManager.isInitialized) {
            appUpdateManager.unregisterListener(installStateUpdatedListener)
        }
        // Se l'Activity viene chiusa davvero (back, swipe dai recenti) non lasciamo un timer orfano
        if (isFinishing) {
            if (StillTimerService.isRunning) {
                stopService(Intent(this, StillTimerService::class.java))
            } else if (StillTimerService.timerState.value.isPaused) {
                StillTimerService.resetState(
                    getActivePreset(this)?.durationSeconds ?: getSavedSelectedTime(this)
                )
            }
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

@Composable
private fun MeditationTimerScreen(
    modifier: Modifier = Modifier,
    selectedPalette: StillColorPalette = StillColorPalette.AUTUMN,
    onPaletteSelected: (StillColorPalette) -> Unit = {},
    showHealthRationale: Boolean = false,
    onDismissHealthRationale: () -> Unit = {},
    onPresetPaletteOverrideChange: (StillColorPalette?) -> Unit = {},
    showOpeningQuote: Boolean = false,
    onDismissOpeningQuote: () -> Unit = {},
    onWhatsNewStateChange: (Boolean, Boolean) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val stateHolder = rememberStillScreenStateHolder(
        context = context,
        initialShowHealthRationale = showHealthRationale
    )
    val serviceState by StillTimerService.timerState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentView = LocalView.current
    val isCompactHeight = LocalConfiguration.current.screenHeightDp < 640

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        stateHolder.isNotificationPromptInFlight = false
    }

    val exportHistoryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        stateHolder.exportHistoryToUri(uri)
    }

    val importHistoryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        stateHolder.importHistoryFromUri(uri)
    }

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) { grantedPermissions ->
        stateHolder.onHealthPermissionsResult(grantedPermissions)
    }

    val pickCustomAudioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        stateHolder.handlePickedCustomAudio(uri)
    }

    LaunchedEffect(showHealthRationale) {
        if (showHealthRationale) {
            stateHolder.showPrivacyDialog = true
        }
    }

    LaunchedEffect(stateHolder) {
        stateHolder.checkInitialHealthConnection()
        if (stateHolder.reminderSettings.enabled) {
            scheduleMeditationReminder(
                context,
                stateHolder.reminderSettings,
                afterTrigger = false,
                preserveExistingFuture = true
            )
        }
    }

    LaunchedEffect(stateHolder.activePreset) {
        onPresetPaletteOverrideChange(stateHolder.activePreset?.effectiveColorPalette())
    }

    LaunchedEffect(serviceState) {
        stateHolder.syncFromServiceState(serviceState)
    }

    LaunchedEffect(stateHolder.selectedTimeInSeconds) {
        stateHolder.syncIdleTimeWhenSelectedTimeChanges()
    }

    LaunchedEffect(stateHolder.showWhatsNewDialog, stateHolder.hasEvaluatedWhatsNew) {
        onWhatsNewStateChange(stateHolder.showWhatsNewDialog, stateHolder.hasEvaluatedWhatsNew)
    }

    LaunchedEffect(
        showOpeningQuote,
        stateHolder.isTimerRunning,
        stateHolder.isOvertime,
        stateHolder.showPrivacyDialog
    ) {
        if (showOpeningQuote && (stateHolder.isTimerRunning || stateHolder.isOvertime || stateHolder.showPrivacyDialog)) {
            onDismissOpeningQuote()
        }
    }

    val isServiceSessionActive = serviceState.isRunning || serviceState.isPaused || stateHolder.sessionStartTime != null

    LaunchedEffect(
        showOpeningQuote,
        stateHolder.showPrivacyDialog,
        stateHolder.isTimerRunning,
        stateHolder.isOvertime,
        isServiceSessionActive,
        stateHolder.isNotificationPromptInFlight
    ) {
        if (stateHolder.hasEvaluatedWhatsNew) return@LaunchedEffect
        if (showOpeningQuote ||
            stateHolder.showPrivacyDialog ||
            stateHolder.isTimerRunning ||
            stateHolder.isOvertime ||
            isServiceSessionActive ||
            stateHolder.isNotificationPromptInFlight
        ) {
            return@LaunchedEffect
        }
        delay(120L)
        stateHolder.hasEvaluatedWhatsNew = true
        if (getLastSeenVersionCode(context) < BuildConfig.VERSION_CODE) {
            stateHolder.showWhatsNewDialog = true
        }
    }

    DisposableEffect(lifecycleOwner, stateHolder) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                stateHolder.refreshOnResume()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Aggiornamento live: sessioni in arrivo dall'orologio (WearListenerService) mentre l'app è aperta
    DisposableEffect(context, lifecycleOwner, stateHolder) {
        val livePrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            stateHolder.onSharedPreferenceChanged(
                key = key,
                isLifecycleStarted = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            )
        }
        livePrefs.registerOnSharedPreferenceChangeListener(prefsListener)
        onDispose {
            livePrefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        }
    }

    val keepScreenAwake = stateHolder.keepScreenAwake

    // Con timer attivo o in overtime il gesto/tasto Indietro non chiude per errore la sessione
    BackHandler(enabled = keepScreenAwake) { }

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

    val requestNotifications: (String) -> Unit = { permission ->
        notificationPermissionLauncher.launch(permission)
    }

    val centerStageActions = remember(stateHolder, notificationPermissionLauncher) {
        StillTimerCenterStageActions(
            onToggleHideTimerNumbers = stateHolder::toggleHideTimerNumbers,
            onStartTimer = {
                if (!stateHolder.isTimerRunning) {
                    stateHolder.startOrResumeTimer(requestNotifications)
                }
            }
        )
    }

    val bottomControlsActions = remember(stateHolder, notificationPermissionLauncher) {
        StillBottomControlsActions(
            onUpdateTimeSelection = stateHolder::updateTimeSelection,
            onAdjustActiveTimer = stateHolder::adjustActiveTimer,
            onToggleTimer = {
                if (!stateHolder.isTimerRunning) {
                    stateHolder.startOrResumeTimer(requestNotifications)
                } else {
                    stateHolder.pauseActiveTimer()
                }
            },
            onResetTimer = stateHolder::resetTimerState,
            onSaveAndFinish = stateHolder::saveAndFinishSession
        )
    }

    val settingsActions = remember(
        stateHolder,
        onPaletteSelected,
        requestPermissionLauncher,
        pickCustomAudioLauncher
    ) {
        StillSettingsActions(
            onPrepTimeChange = stateHolder::updatePrepTime,
            onIntervalMinsChange = stateHolder::updateIntervalMins,
            onGongFreqChange = stateHolder::updateGongFreq,
            onIntervalGongFreqChange = stateHolder::updateIntervalGongFreq,
            onGongVolumeChange = stateHolder::updateGongVolume,
            onAlertModeChange = stateHolder::updateAlertMode,
            onTimerStyleChange = stateHolder::updateTimerStyle,
            onPaletteSelected = onPaletteSelected,
            onAutoDndChange = stateHolder::updateAutoDnd,
            onResyncHealth = stateHolder::resyncHealth,
            onConnectHealth = {
                stateHolder.connectToHealth { permissions ->
                    requestPermissionLauncher.launch(permissions)
                }
            },
            onDismiss = { stateHolder.showSettingsDialog = false },
            onAmbientSoundscapeChange = stateHolder::updateAmbientSoundscape,
            onAmbientVolumeChange = stateHolder::updateAmbientVolume,
            onPickCustomAudio = {
                pickCustomAudioLauncher.launch(arrayOf("audio/mpeg", "audio/ogg", "application/ogg"))
            },
            onOpenPresetManager = { stateHolder.showPresetManagerDialog = true },
            onOpenReminderSettings = { stateHolder.showReminderDialog = true }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(if (keepScreenAwake) Modifier.keepScreenOn() else Modifier)
    ) {
        StillAtmosphericBackground(
            isTimerRunning = stateHolder.isTimerRunning,
            isPreparing = stateHolder.isPreparing,
            isOvertime = stateHolder.isOvertime
        )
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = if (isCompactHeight) 10.dp else 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            StillTopBar(
                isPreparing = stateHolder.isPreparing,
                isOvertime = stateHolder.isOvertime,
                selectedTimeInSeconds = stateHolder.selectedTimeInSeconds,
                onOpenSettings = { stateHolder.showSettingsDialog = true },
                onOpenStats = stateHolder::openStats,
                onOpenPrivacy = { stateHolder.showPrivacyDialog = true },
                onOpenGuide = { stateHolder.showGuideDialog = true },
                onOpenPresets = { stateHolder.showPresetPickerDialog = true },
                activePresetName = stateHolder.activePreset?.name,
                canSelectPreset = stateHolder.isInitialState
            )

            StillTimerCenterStage(
                uiState = StillTimerCenterStageUiState(
                    isTimerRunning = stateHolder.isTimerRunning,
                    isPreparing = stateHolder.isPreparing,
                    isOvertime = stateHolder.isOvertime,
                    isInitialState = stateHolder.isInitialState,
                    timerStyle = stateHolder.effectiveTimerStyle,
                    selectedTimeInSeconds = stateHolder.selectedTimeInSeconds,
                    activeSessionPlannedSeconds = stateHolder.activeSessionPlannedSeconds,
                    timeLeftInSeconds = stateHolder.timeLeftInSeconds,
                    prepTimeLeftSeconds = stateHolder.prepTimeLeftSeconds,
                    overtimeSeconds = stateHolder.overtimeSeconds,
                    progress = stateHolder.progress,
                    hideTimerNumbers = stateHolder.hideTimerNumbers,
                    isCompactHeight = isCompactHeight
                ),
                actions = centerStageActions,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )

            StillBottomControls(
                uiState = StillBottomControlsUiState(
                    isInitialState = stateHolder.isInitialState,
                    isTimerRunning = stateHolder.isTimerRunning,
                    isPreparing = stateHolder.isPreparing,
                    isOvertime = stateHolder.isOvertime,
                    selectedTimeInSeconds = stateHolder.selectedTimeInSeconds,
                    activeSessionPlannedSeconds = stateHolder.activeSessionPlannedSeconds,
                    lastTimedSeconds = stateHolder.lastTimedSeconds,
                    timeLeftInSeconds = stateHolder.timeLeftInSeconds,
                    overtimeSeconds = stateHolder.overtimeSeconds,
                    isCompactHeight = isCompactHeight
                ),
                actions = bottomControlsActions
            )
        }

        // --- DIALOG IMPOSTAZIONI ---
        if (stateHolder.showSettingsDialog) {
            StillSettingsDialog(
                uiState = StillSettingsUiState(
                    prepTimeSeconds = stateHolder.prepTimeSeconds,
                    intervalMins = stateHolder.intervalMins,
                    gongFreqHz = stateHolder.gongFreqHz,
                    intervalGongFreqHz = stateHolder.intervalGongFreqHz,
                    gongVolumePercent = stateHolder.gongVolumePercent,
                    alertMode = stateHolder.alertMode,
                    timerStyle = stateHolder.timerStyle,
                    selectedPalette = selectedPalette,
                    autoDndEnabled = stateHolder.autoDndEnabled,
                    dndGranted = stateHolder.dndGranted,
                    isHealthConnected = stateHolder.isHealthConnected,
                    isResyncing = stateHolder.isResyncing,
                    ambientSoundscape = stateHolder.ambientSoundscape,
                    ambientVolumePercent = stateHolder.ambientVolumePercent,
                    customAmbientUri = stateHolder.customAmbientUri,
                    customAmbientName = stateHolder.customAmbientName,
                    presetCount = stateHolder.presets.size,
                    reminderSummary = formatReminderSummary(context, stateHolder.reminderSettings)
                ),
                actions = settingsActions
            )
        }

        // --- DIALOG PROMEMORIA MEDITAZIONE ---
        if (stateHolder.showReminderDialog) {
            StillReminderDialog(
                settings = stateHolder.reminderSettings,
                onSettingsChange = stateHolder::updateReminderSettings,
                onDismiss = { stateHolder.showReminderDialog = false }
            )
        }

        // --- DIALOG SELEZIONE RAPIDA PRESET ("P") ---
        if (stateHolder.showPresetPickerDialog) {
            StillPresetPickerDialog(
                presets = stateHolder.presets,
                activePresetId = stateHolder.activePresetId,
                onSelectPreset = stateHolder::applyPresetSelection,
                onOpenManager = { stateHolder.showPresetManagerDialog = true },
                onDismiss = { stateHolder.showPresetPickerDialog = false }
            )
        }

        // --- DIALOG GESTIONE / CREAZIONE PRESET ---
        if (stateHolder.showPresetManagerDialog) {
            StillPresetManagerDialog(
                presets = stateHolder.presets,
                activePresetId = stateHolder.activePresetId,
                currentGlobalSnapshot = stateHolder.buildCurrentGlobalSnapshot(selectedPalette),
                onSavePreset = stateHolder::saveEditedPreset,
                onDeletePreset = stateHolder::deletePresetById,
                onDismiss = { stateHolder.showPresetManagerDialog = false }
            )
        }

        // --- DIALOG STATISTICHE E CALENDARIO CON CANCELLAZIONE ---
        if (stateHolder.showStatsDialog) {
            StillStatsDialog(
                sessionHistoryList = stateHolder.sessionHistoryList,
                onSelectDate = { stateHolder.selectedCalendarDate = it },
                onDismiss = { stateHolder.showStatsDialog = false },
                onExportHistory = {
                    val defaultFileName = "still-history-${LocalDate.now()}.csv"
                    exportHistoryLauncher.launch(defaultFileName)
                },
                onImportHistory = {
                    importHistoryLauncher.launch(
                        arrayOf("text/csv", "text/comma-separated-values", "application/csv", "text/plain")
                    )
                }
            )
        }

        // --- POPUP 1: DETTAGLI MEDITAZIONI DEL GIORNO SELEZIONATO ---
        stateHolder.selectedCalendarDate?.let { selectedDate ->
            StillDayDetailDialog(
                selectedDate = selectedDate,
                sessionHistoryList = stateHolder.sessionHistoryList,
                onRequestDeleteSession = { stateHolder.sessionToDelete = it },
                onDismiss = { stateHolder.selectedCalendarDate = null }
            )
        }

        // --- POPUP 2: CONFERMA CANCELLAZIONE SINGOLA SESSIONE ---
        stateHolder.sessionToDelete?.let { session ->
            StillDeleteSessionDialog(
                session = session,
                onConfirmDelete = { stateHolder.deleteLoggedSession(session) },
                onDismiss = { stateHolder.sessionToDelete = null }
            )
        }

        // --- DIALOG GUIDA OFFLINE ("?") ---
        if (stateHolder.showGuideDialog) {
            StillGuideDialog(
                onDismiss = { stateHolder.showGuideDialog = false }
            )
        }

        // --- DIALOG NOVITÀ (UNA SOLA VOLTA PER VERSIONE) ---
        if (stateHolder.showWhatsNewDialog) {
            StillWhatsNewDialog(
                onDismiss = {
                    saveLastSeenVersionCode(context, BuildConfig.VERSION_CODE)
                    stateHolder.showWhatsNewDialog = false
                }
            )
        }

        // --- DIALOG INFORMATIVA PRIVACY & SALUTE (Health Connect Rationale) ---
        if (stateHolder.showPrivacyDialog) {
            HealthPrivacyRationaleDialog(
                onDismiss = {
                    stateHolder.showPrivacyDialog = false
                    onDismissHealthRationale()
                }
            )
        }
    }
}

@Composable
private fun StillOpeningQuoteOverlay(
    onDismiss: () -> Unit
) {
    val stillColors = LocalStillColors.current
    val textAlpha = remember { Animatable(0f) }
    val scrimAlpha = remember { Animatable(1f) }
    val interactionSource = remember { MutableInteractionSource() }
    var hasDismissed by remember { mutableStateOf(false) }
    val dismissOnce = {
        if (!hasDismissed) {
            hasDismissed = true
            onDismiss()
        }
    }

    BackHandler(onBack = dismissOnce)

    LaunchedEffect(Unit) {
        textAlpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing)
        )
        delay(1800L)
        launch {
            textAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing)
            )
        }
        scrimAlpha.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing)
        )
        dismissOnce()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.96f * scrimAlpha.value))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = dismissOnce
            )
            .padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.opening_impermanence_quote),
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Light),
            color = stillColors.titleColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.alpha(textAlpha.value)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MeditationTimerPreview() {
    StillTheme {
        MeditationTimerScreen()
    }
}
