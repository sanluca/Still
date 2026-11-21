package com.sanluca.still

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
import com.sanluca.still.ui.theme.StillColorPalette
import com.sanluca.still.ui.theme.StillTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
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
            var presetPaletteOverride by remember {
                mutableStateOf(getActivePreset(this)?.effectiveColorPalette())
            }
            val effectivePalette = presetPaletteOverride ?: selectedPalette
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

            StillTheme(palette = effectivePalette) {
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
                        onDismissHealthRationale = { showHealthRationaleState.value = false },
                        onPresetPaletteOverrideChange = { overridePalette ->
                            presetPaletteOverride = overridePalette
                        }
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
fun MeditationTimerScreen(
    modifier: Modifier = Modifier,
    selectedPalette: StillColorPalette = StillColorPalette.AUTUMN,
    onPaletteSelected: (StillColorPalette) -> Unit = {},
    showHealthRationale: Boolean = false,
    onDismissHealthRationale: () -> Unit = {},
    onPresetPaletteOverrideChange: (StillColorPalette?) -> Unit = {}
) {
    val context = LocalContext.current
    val appContext = context.applicationContext ?: context
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
    var showPresetPickerDialog by remember { mutableStateOf(false) }
    var showPresetManagerDialog by remember { mutableStateOf(false) }
    var presets by remember { mutableStateOf(getSavedPresets(context)) }
    var activePresetId by remember { mutableStateOf(getActivePresetId(context)) }
    val activePreset = remember(presets, activePresetId) {
        presets.firstOrNull { it.id == activePresetId }
    }
    LaunchedEffect(activePreset) {
        onPresetPaletteOverrideChange(activePreset?.effectiveColorPalette())
    }

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

    val exportHistoryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val csv = exportSessionHistoryToCsv(appContext)
                val exportedCount = getLocalSessionHistory(appContext).size
                val outputStream = appContext.contentResolver.openOutputStream(uri)
                    ?: throw IllegalStateException("Cannot open output stream for $uri")
                outputStream.use { stream ->
                    stream.write(csv.toByteArray(Charsets.UTF_8))
                    stream.flush()
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        appContext,
                        appContext.getString(R.string.toast_backup_export_success, exportedCount),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        appContext,
                        appContext.getString(R.string.toast_backup_export_error),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    val importHistoryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val csvContent = appContext.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.bufferedReader(Charsets.UTF_8).readText()
                } ?: throw IllegalStateException("Cannot open input stream for $uri")

                val result = importSessionHistoryFromCsv(appContext, csvContent)
                val updatedHistory = getLocalSessionHistory(appContext)
                withContext(Dispatchers.Main) {
                    sessionHistoryList = updatedHistory
                    val msg = if (result.importedCount > 0) {
                        appContext.getString(
                            R.string.toast_backup_import_success,
                            result.importedCount,
                            result.skippedDuplicates
                        )
                    } else {
                        appContext.getString(
                            R.string.toast_backup_import_none,
                            result.skippedDuplicates
                        )
                    }
                    Toast.makeText(appContext, msg, Toast.LENGTH_LONG).show()
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        appContext,
                        appContext.getString(R.string.toast_backup_import_error),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

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

    val serviceState by StillTimerService.timerState.collectAsState()
    val initialServiceSnapshot = remember { StillTimerService.timerState.value }
    val hasActiveServiceSession = initialServiceSnapshot.isRunning || initialServiceSnapshot.isPaused

    var selectedTimeInSeconds by remember {
        mutableIntStateOf(
            if (hasActiveServiceSession) initialServiceSnapshot.selectedTimeSeconds
            else activePreset?.durationSeconds ?: getSavedSelectedTime(context)
        )
    }
    var activeSessionPlannedSeconds by remember {
        mutableIntStateOf(
            if (hasActiveServiceSession) initialServiceSnapshot.activeSessionPlannedSeconds
            else selectedTimeInSeconds
        )
    }
    var lastTimedSeconds by remember {
        mutableIntStateOf(
            if (selectedTimeInSeconds > 0) selectedTimeInSeconds
            else getSavedSelectedTime(context).takeIf { it > 0 } ?: 600
        )
    }
    
    var prepTimeSeconds by remember { mutableIntStateOf(getSavedPrepTime(context)) }
    var gongFreqHz by remember { mutableDoubleStateOf(getSavedGongFrequency(context)) }
    var intervalGongFreqHz by remember { mutableDoubleStateOf(getSavedIntervalGongFrequency(context)) }
    var gongVolumePercent by remember { mutableIntStateOf(getSavedGongVolume(context)) }
    var alertMode by remember { mutableIntStateOf(getSavedAlertMode(context)) }
    var intervalMins by remember { mutableIntStateOf(getSavedIntervalMins(context)) }
    var autoDndEnabled by remember { mutableStateOf(getSavedAutoDnd(context)) }
    var timerStyle by remember { mutableIntStateOf(getSavedTimerStyle(context)) }
    var ambientSoundscape by remember { mutableStateOf(getSavedAmbientSoundscape(context)) }
    var ambientVolumePercent by remember { mutableIntStateOf(getSavedAmbientVolume(context)) }
    var customAmbientUri by remember { mutableStateOf(getSavedCustomAmbientUri(context)) }
    var customAmbientName by remember { mutableStateOf(getSavedCustomAmbientName(context)) }

    val effectivePrepSeconds = activePreset?.prepSeconds ?: prepTimeSeconds
    val effectiveGongFreqHz = activePreset?.gongFreqHz ?: gongFreqHz
    val effectiveIntervalGongFreqHz = activePreset?.intervalGongFreqHz ?: intervalGongFreqHz
    val effectiveGongVolumePercent = activePreset?.gongVolumePercent ?: gongVolumePercent
    val effectiveAlertMode = activePreset?.alertMode ?: alertMode
    val effectiveIntervalMins = activePreset?.intervalMins ?: intervalMins
    val effectiveAutoDndEnabled = activePreset?.autoDndEnabled ?: autoDndEnabled
    val effectiveTimerStyle = activePreset?.timerStyle ?: timerStyle
    val effectiveAmbientSoundscape = activePreset?.effectiveAmbientSoundscape() ?: ambientSoundscape
    val effectiveAmbientVolumePercent = activePreset?.ambientVolumePercent ?: ambientVolumePercent
    val effectiveCustomAmbientUri = if (activePreset?.ambientSoundId != null) {
        if (effectiveAmbientSoundscape == AmbientSoundscape.CUSTOM) {
            activePreset.customAmbientUri ?: customAmbientUri
        } else {
            null
        }
    } else {
        customAmbientUri
    }

    var isPreparing by remember {
        mutableStateOf(hasActiveServiceSession && initialServiceSnapshot.isPreparing)
    }
    var prepTimeLeftSeconds by remember {
        mutableIntStateOf(if (hasActiveServiceSession) initialServiceSnapshot.prepLeftSeconds else 0)
    }

    var timeLeftInSeconds by remember {
        mutableIntStateOf(
            if (hasActiveServiceSession) initialServiceSnapshot.timeLeftSeconds
            else if (selectedTimeInSeconds == -1) 0 else selectedTimeInSeconds
        )
    }
    
    var isOvertime by remember {
        mutableStateOf(hasActiveServiceSession && initialServiceSnapshot.isOvertime)
    }
    var overtimeSeconds by remember {
        mutableIntStateOf(if (hasActiveServiceSession) initialServiceSnapshot.overtimeSeconds else 0)
    }

    var isTimerRunning by remember {
        mutableStateOf(initialServiceSnapshot.isRunning)
    }
    var hasStartedFromUi by remember {
        mutableStateOf(hasActiveServiceSession)
    }
    var hideTimerNumbers by remember { mutableStateOf(false) }

    var sessionStartTime by remember {
        mutableStateOf(initialServiceSnapshot.sessionStartEpochMilli?.let { Instant.ofEpochMilli(it) })
    }

    LaunchedEffect(serviceState) {
        if (serviceState.isRunning || serviceState.isPaused) {
            isTimerRunning = serviceState.isRunning
            hasStartedFromUi = true
            isPreparing = serviceState.isPreparing
            prepTimeLeftSeconds = serviceState.prepLeftSeconds
            activeSessionPlannedSeconds = serviceState.activeSessionPlannedSeconds
            timeLeftInSeconds = serviceState.timeLeftSeconds
            isOvertime = serviceState.isOvertime
            overtimeSeconds = serviceState.overtimeSeconds
            sessionStartTime = serviceState.sessionStartEpochMilli?.let { Instant.ofEpochMilli(it) }
        } else if (hasStartedFromUi && isTimerRunning) {
            isTimerRunning = false
            isPreparing = false
            prepTimeLeftSeconds = 0
        }
    }

    fun startOrResumeTimer() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        val isResuming = sessionStartTime != null
        val intent = if (!isResuming) {
            Intent(context, StillTimerService::class.java).apply {
                action = "START"
                putExtra("FREQ", effectiveGongFreqHz)
                putExtra("INTERVAL_FREQ", effectiveIntervalGongFreqHz)
                putExtra("VOLUME", effectiveGongVolumePercent)
                putExtra("ALERT_MODE", effectiveAlertMode)
                putExtra("INTERVAL", effectiveIntervalMins)
                putExtra("TIME", selectedTimeInSeconds)
                putExtra("PLANNED_TIME", selectedTimeInSeconds)
                putExtra("SELECTED_TIME", selectedTimeInSeconds)
                putExtra("PREP", effectivePrepSeconds)
                putExtra("DND", effectiveAutoDndEnabled)
                putExtra("AMBIENT_SOUND", effectiveAmbientSoundscape.id)
                putExtra("AMBIENT_VOLUME", effectiveAmbientVolumePercent)
                putExtra("AMBIENT_CUSTOM_URI", effectiveCustomAmbientUri)
            }
        } else {
            val elapsedBeforePause = if (activeSessionPlannedSeconds == -1) {
                timeLeftInSeconds
            } else {
                (activeSessionPlannedSeconds - timeLeftInSeconds).coerceAtLeast(0)
            }
            Intent(context, StillTimerService::class.java).apply {
                action = "START"
                putExtra("RESUME", true)
                putExtra("ELAPSED_OFFSET", elapsedBeforePause)
                putExtra("FREQ", effectiveGongFreqHz)
                putExtra("INTERVAL_FREQ", effectiveIntervalGongFreqHz)
                putExtra("VOLUME", effectiveGongVolumePercent)
                putExtra("ALERT_MODE", effectiveAlertMode)
                putExtra("INTERVAL", effectiveIntervalMins)
                putExtra("TIME", if (activeSessionPlannedSeconds == -1) -1 else timeLeftInSeconds)
                putExtra("PLANNED_TIME", activeSessionPlannedSeconds)
                putExtra("SELECTED_TIME", selectedTimeInSeconds)
                putExtra("DND", effectiveAutoDndEnabled)
                putExtra("AMBIENT_SOUND", effectiveAmbientSoundscape.id)
                putExtra("AMBIENT_VOLUME", effectiveAmbientVolumePercent)
                putExtra("AMBIENT_CUSTOM_URI", effectiveCustomAmbientUri)
            }
        }

        try {
            hasStartedFromUi = true
            isTimerRunning = true
            if (!isResuming && effectivePrepSeconds > 0) {
                isPreparing = true
                prepTimeLeftSeconds = effectivePrepSeconds
            }
            context.startForegroundService(intent)
        } catch (_: Exception) {
            isTimerRunning = false
            isPreparing = false
        }
    }

    fun pauseActiveTimer() {
        isTimerRunning = false
        isPreparing = false
        if (StillTimerService.isRunning) {
            val pauseIntent = Intent(context, StillTimerService::class.java).apply {
                action = "PAUSE"
            }
            context.startService(pauseIntent)
        }
    }

    fun sendAmbientUpdateToService(
        sound: AmbientSoundscape,
        volume: Int,
        customUri: String? = customAmbientUri
    ) {
        if (StillTimerService.isRunning) {
            val updateIntent = Intent(context, StillTimerService::class.java).apply {
                action = "UPDATE_AMBIENT"
                putExtra("AMBIENT_SOUND", sound.id)
                putExtra("AMBIENT_VOLUME", volume)
                putExtra("AMBIENT_CUSTOM_URI", customUri)
            }
            context.startService(updateIntent)
        }
    }

    val pickCustomAudioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val displayName = resolveAudioDisplayName(appContext, uri)
            ?: appContext.getString(R.string.ambient_sound_custom)
        if (!isSupportedCustomAudio(appContext, uri, displayName)) {
            Toast.makeText(
                appContext,
                appContext.getString(R.string.toast_custom_audio_unsupported),
                Toast.LENGTH_LONG
            ).show()
            return@rememberLauncherForActivityResult
        }
        try {
            appContext.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {
        }
        val uriString = uri.toString()
        saveCustomAmbientAudio(appContext, uriString, displayName)
        customAmbientUri = uriString
        customAmbientName = displayName
        ambientSoundscape = AmbientSoundscape.CUSTOM
        saveAmbientSoundscape(appContext, AmbientSoundscape.CUSTOM)
        if (activePreset?.ambientSoundId == null) {
            sendAmbientUpdateToService(AmbientSoundscape.CUSTOM, effectiveAmbientVolumePercent, uriString)
        }
    }

    fun updateTimeSelection(newTime: Int) {
        if (newTime > 0) {
            lastTimedSeconds = newTime
        }
        selectedTimeInSeconds = newTime
        activeSessionPlannedSeconds = newTime
        timeLeftInSeconds = if (newTime == -1) 0 else newTime
        isOvertime = false
        overtimeSeconds = 0
        sessionStartTime = null
        if (activePreset == null) {
            saveSelectedTime(context, newTime)
        }
    }

    fun adjustActiveTimer(deltaSeconds: Int) {
        if (activeSessionPlannedSeconds <= 0 || isOvertime || isPreparing) return
        if (deltaSeconds < 0 && timeLeftInSeconds <= -deltaSeconds) return

        activeSessionPlannedSeconds = (activeSessionPlannedSeconds + deltaSeconds).coerceAtLeast(1)
        timeLeftInSeconds = (timeLeftInSeconds + deltaSeconds).coerceAtLeast(1)

        if (isTimerRunning) {
            if (StillTimerService.isRunning) {
                val adjustIntent = Intent(context, StillTimerService::class.java).apply {
                    action = "ADJUST_TIME"
                    putExtra("DELTA_SECONDS", deltaSeconds)
                }
                context.startService(adjustIntent)
            }
        } else if (sessionStartTime != null) {
            StillTimerService.adjustPausedState(deltaSeconds)
        }
    }

    val progress = remember(timeLeftInSeconds, activeSessionPlannedSeconds) {
        if (activeSessionPlannedSeconds > 0) {
            (timeLeftInSeconds.toFloat() / activeSessionPlannedSeconds.toFloat()).coerceIn(0f, 1f)
        } else {
            1f
        }
    }

    val currentView = LocalView.current
    val keepScreenAwake = isTimerRunning || isOvertime

    // Con timer attivo o in overtime il gesto/tasto Indietro non chiude per errore la sessione
    BackHandler(enabled = isTimerRunning || isOvertime) { }

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

    LaunchedEffect(selectedTimeInSeconds) {
        if (!isTimerRunning && sessionStartTime == null && !isOvertime) {
            activeSessionPlannedSeconds = selectedTimeInSeconds
            timeLeftInSeconds = if (selectedTimeInSeconds == -1) 0 else selectedTimeInSeconds
            isOvertime = false
            overtimeSeconds = 0
        }
    }

    val isInitialState = !isTimerRunning && !isOvertime && sessionStartTime == null

    fun syncIdleDurationForPreset(targetPreset: MeditationPreset?) {
        if (!isInitialState) return
        val targetTime = targetPreset?.durationSeconds ?: getSavedSelectedTime(context)
        if (targetTime > 0) {
            lastTimedSeconds = targetTime
        }
        selectedTimeInSeconds = targetTime
        activeSessionPlannedSeconds = targetTime
        timeLeftInSeconds = if (targetTime == -1) 0 else targetTime
        isOvertime = false
        overtimeSeconds = 0
        sessionStartTime = null
        if (!StillTimerService.isRunning && !StillTimerService.timerState.value.isPaused) {
            StillTimerService.resetState(targetTime)
        }
    }

    fun applyPresetSelection(presetId: String?) {
        activePresetId = presetId
        saveActivePresetId(context, presetId)
        syncIdleDurationForPreset(presets.firstOrNull { it.id == presetId })
    }

    fun resetTimerState() {
        cancelGongVibration(context)
        if (StillTimerService.isRunning) {
            val stopIntent = Intent(context, StillTimerService::class.java).apply {
                action = "STOP"
            }
            context.startService(stopIntent)
        } else {
            StillTimerService.resetState(selectedTimeInSeconds)
        }
        isTimerRunning = false
        hasStartedFromUi = false
        isPreparing = false
        prepTimeLeftSeconds = 0
        isOvertime = false
        overtimeSeconds = 0
        hideTimerNumbers = false
        activeSessionPlannedSeconds = selectedTimeInSeconds
        timeLeftInSeconds = if (selectedTimeInSeconds == -1) 0 else selectedTimeInSeconds
        sessionStartTime = null
    }

    val toastResyncDone = stringResource(R.string.toast_health_resync_done)

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
            addSessionToLocalHistory(appContext, totalSec, start, end)
            sessionHistoryList = getLocalSessionHistory(appContext)
            savePendingWatchSession(appContext, totalSec, start, end)

            val savedStartEpochSec = start.epochSecond
            val savedEndEpochSec = end.epochSecond
            CoroutineScope(Dispatchers.IO).launch {
                syncPendingSessionsToHealthConnect(appContext, healthConnectManager)
                val stillPending = getPendingWatchSessions(appContext).any {
                    it.startEpochSec == savedStartEpochSec && it.endEpochSec == savedEndEpochSec
                }
                withContext(Dispatchers.Main) {
                    if (!stillPending) {
                        Toast.makeText(
                            appContext,
                            appContext.getString(R.string.toast_session_saved_health, totalSec),
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(
                            appContext,
                            appContext.getString(R.string.toast_session_saved_history, totalSec),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }

        resetTimerState()
    }

    val isCompactHeight = LocalConfiguration.current.screenHeightDp < 640

    Box(
        modifier = modifier
            .fillMaxSize()
            .then(if (keepScreenAwake) Modifier.keepScreenOn() else Modifier)
    ) {
        StillAtmosphericBackground(
            isTimerRunning = isTimerRunning,
            isPreparing = isPreparing,
            isOvertime = isOvertime
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = if (isCompactHeight) 10.dp else 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            StillTopBar(
                isPreparing = isPreparing,
                isOvertime = isOvertime,
                selectedTimeInSeconds = selectedTimeInSeconds,
                onOpenSettings = { showSettingsDialog = true },
                onOpenStats = {
                    sessionHistoryList = getLocalSessionHistory(context)
                    coroutineScope.launch {
                        syncPendingSessionsToHealthConnect(context, healthConnectManager)
                    }
                    showStatsDialog = true
                },
                onOpenPrivacy = { showPrivacyDialog = true },
                onOpenPresets = { showPresetPickerDialog = true },
                activePresetName = activePreset?.name,
                canSelectPreset = isInitialState
            )

            StillTimerCenterStage(
                isTimerRunning = isTimerRunning,
                isPreparing = isPreparing,
                isOvertime = isOvertime,
                isInitialState = isInitialState,
                timerStyle = effectiveTimerStyle,
                selectedTimeInSeconds = selectedTimeInSeconds,
                activeSessionPlannedSeconds = activeSessionPlannedSeconds,
                timeLeftInSeconds = timeLeftInSeconds,
                prepTimeLeftSeconds = prepTimeLeftSeconds,
                overtimeSeconds = overtimeSeconds,
                progress = progress,
                hideTimerNumbers = hideTimerNumbers,
                isCompactHeight = isCompactHeight,
                onToggleHideTimerNumbers = {
                    if (isTimerRunning || isOvertime) {
                        hideTimerNumbers = !hideTimerNumbers
                    }
                },
                onStartTimer = {
                    if (!isTimerRunning) {
                        startOrResumeTimer()
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )

            StillBottomControls(
                isInitialState = isInitialState,
                isTimerRunning = isTimerRunning,
                isPreparing = isPreparing,
                isOvertime = isOvertime,
                selectedTimeInSeconds = selectedTimeInSeconds,
                activeSessionPlannedSeconds = activeSessionPlannedSeconds,
                lastTimedSeconds = lastTimedSeconds,
                timeLeftInSeconds = timeLeftInSeconds,
                overtimeSeconds = overtimeSeconds,
                isCompactHeight = isCompactHeight,
                onUpdateTimeSelection = { updateTimeSelection(it) },
                onAdjustActiveTimer = { adjustActiveTimer(it) },
                onToggleTimer = {
                    if (!isTimerRunning) {
                        startOrResumeTimer()
                    } else {
                        pauseActiveTimer()
                    }
                },
                onResetTimer = { resetTimerState() },
                onSaveAndFinish = { includeExtra -> saveAndFinishSession(includeExtra = includeExtra) }
            )
        }

        // --- DIALOG IMPOSTAZIONI ---
        if (showSettingsDialog) {
            StillSettingsDialog(
                prepTimeSeconds = prepTimeSeconds,
                onPrepTimeChange = { secs ->
                    prepTimeSeconds = secs
                    savePrepTime(context, secs)
                },
                intervalMins = intervalMins,
                onIntervalMinsChange = { mins ->
                    intervalMins = mins
                    saveIntervalMins(context, mins)
                },
                gongFreqHz = gongFreqHz,
                onGongFreqChange = { freq ->
                    gongFreqHz = freq
                    saveGongFrequency(context, freq)
                },
                intervalGongFreqHz = intervalGongFreqHz,
                onIntervalGongFreqChange = { freq ->
                    intervalGongFreqHz = freq
                    saveIntervalGongFrequency(context, freq)
                },
                gongVolumePercent = gongVolumePercent,
                onGongVolumeChange = { vol ->
                    gongVolumePercent = vol
                    saveGongVolume(context, vol)
                },
                alertMode = alertMode,
                onAlertModeChange = { mode ->
                    alertMode = mode
                    saveAlertMode(context, mode)
                },
                timerStyle = timerStyle,
                onTimerStyleChange = { style ->
                    timerStyle = style
                    saveTimerStyle(context, style)
                },
                selectedPalette = selectedPalette,
                onPaletteSelected = onPaletteSelected,
                autoDndEnabled = autoDndEnabled,
                onAutoDndChange = { enabled ->
                    autoDndEnabled = enabled
                    saveAutoDnd(context, enabled)
                },
                dndGranted = dndGranted,
                isHealthConnected = isHealthConnected,
                isResyncing = isResyncing,
                onResyncHealth = {
                    if (!isResyncing) {
                        isResyncing = true
                        coroutineScope.launch {
                            try {
                                val ok = resyncHistoryToHealthConnect(context, healthConnectManager)
                                Toast.makeText(
                                    context,
                                    if (ok) toastResyncDone else toastHealthDeniedMsg,
                                    Toast.LENGTH_SHORT
                                ).show()
                            } finally {
                                isResyncing = false
                            }
                        }
                    }
                },
                onConnectHealth = { connectToHealth() },
                onDismiss = { showSettingsDialog = false },
                ambientSoundscape = ambientSoundscape,
                onAmbientSoundscapeChange = { sound ->
                    ambientSoundscape = sound
                    saveAmbientSoundscape(context, sound)
                    if (activePreset?.ambientSoundId == null) {
                        sendAmbientUpdateToService(sound, effectiveAmbientVolumePercent, customAmbientUri)
                    }
                },
                ambientVolumePercent = ambientVolumePercent,
                onAmbientVolumeChange = { vol ->
                    ambientVolumePercent = vol
                    saveAmbientVolume(context, vol)
                    if (activePreset?.ambientVolumePercent == null) {
                        sendAmbientUpdateToService(effectiveAmbientSoundscape, vol, effectiveCustomAmbientUri)
                    }
                },
                customAmbientUri = customAmbientUri,
                customAmbientName = customAmbientName,
                onPickCustomAudio = {
                    pickCustomAudioLauncher.launch(arrayOf("audio/mpeg", "audio/ogg", "application/ogg"))
                },
                presetCount = presets.size,
                onOpenPresetManager = { showPresetManagerDialog = true }
            )
        }

        // --- DIALOG SELEZIONE RAPIDA PRESET ("P") ---
        if (showPresetPickerDialog) {
            StillPresetPickerDialog(
                presets = presets,
                activePresetId = activePresetId,
                onSelectPreset = { selectedId -> applyPresetSelection(selectedId) },
                onOpenManager = { showPresetManagerDialog = true },
                onDismiss = { showPresetPickerDialog = false }
            )
        }

        // --- DIALOG GESTIONE / CREAZIONE PRESET ---
        if (showPresetManagerDialog) {
            val currentGlobalSnapshot = MeditationPreset(
                name = "",
                durationSeconds = getSavedSelectedTime(context),
                prepSeconds = prepTimeSeconds,
                gongFreqHz = gongFreqHz,
                gongVolumePercent = gongVolumePercent,
                alertMode = alertMode,
                intervalMins = intervalMins,
                intervalGongFreqHz = intervalGongFreqHz,
                ambientSoundId = ambientSoundscape.id,
                ambientVolumePercent = ambientVolumePercent,
                customAmbientUri = if (ambientSoundscape == AmbientSoundscape.CUSTOM) customAmbientUri else null,
                customAmbientName = if (ambientSoundscape == AmbientSoundscape.CUSTOM) customAmbientName else null,
                timerStyle = timerStyle,
                colorPaletteId = selectedPalette.id,
                autoDndEnabled = autoDndEnabled
            )
            StillPresetManagerDialog(
                presets = presets,
                activePresetId = activePresetId,
                currentGlobalSnapshot = currentGlobalSnapshot,
                onSavePreset = { editedPreset ->
                    val updated = savePreset(context, editedPreset)
                    presets = updated
                    if (editedPreset.id == activePresetId) {
                        val refreshed = updated.firstOrNull { it.id == editedPreset.id }
                        syncIdleDurationForPreset(refreshed)
                        if (StillTimerService.isRunning && refreshed != null) {
                            val liveSound = refreshed.effectiveAmbientSoundscape() ?: ambientSoundscape
                            val liveVolume = refreshed.ambientVolumePercent ?: ambientVolumePercent
                            val liveCustomUri = if (refreshed.ambientSoundId != null) {
                                if (liveSound == AmbientSoundscape.CUSTOM) {
                                    refreshed.customAmbientUri ?: customAmbientUri
                                } else {
                                    null
                                }
                            } else {
                                customAmbientUri
                            }
                            sendAmbientUpdateToService(liveSound, liveVolume, liveCustomUri)
                        }
                    }
                },
                onDeletePreset = { deletedId ->
                    val updated = deletePreset(context, deletedId)
                    presets = updated
                    if (activePresetId == deletedId) {
                        activePresetId = null
                        syncIdleDurationForPreset(null)
                    }
                },
                onDismiss = { showPresetManagerDialog = false }
            )
        }

        // --- DIALOG STATISTICHE E CALENDARIO CON CANCELLAZIONE ---
        if (showStatsDialog) {
            StillStatsDialog(
                sessionHistoryList = sessionHistoryList,
                onSelectDate = { selectedCalendarDate = it },
                onDismiss = { showStatsDialog = false },
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
        selectedCalendarDate?.let { selectedDate ->
            StillDayDetailDialog(
                selectedDate = selectedDate,
                sessionHistoryList = sessionHistoryList,
                onRequestDeleteSession = { sessionToDelete = it },
                onDismiss = { selectedCalendarDate = null }
            )
        }

        // --- POPUP 2: CONFERMA CANCELLAZIONE SINGOLA SESSIONE ---
        sessionToDelete?.let { session ->
            StillDeleteSessionDialog(
                session = session,
                onConfirmDelete = {
                    coroutineScope.launch {
                        healthConnectManager.deleteMeditationSession(
                            Instant.ofEpochSecond(session.startEpochSec),
                            Instant.ofEpochSecond(session.endEpochSec)
                        )
                        deleteSessionFromLocalHistory(context, session.rawString)
                        clearPendingWatchSessions(
                            context,
                            getPendingWatchSessions(context)
                                .filter {
                                    it.startEpochSec == session.startEpochSec &&
                                        it.endEpochSec == session.endEpochSec
                                }
                                .map { it.rawString }
                        )
                        sessionHistoryList = getLocalSessionHistory(appContext)
                        sessionToDelete = null
                        Toast.makeText(appContext, appContext.getString(R.string.toast_session_deleted), Toast.LENGTH_SHORT).show()
                    }
                },
                onDismiss = { sessionToDelete = null }
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