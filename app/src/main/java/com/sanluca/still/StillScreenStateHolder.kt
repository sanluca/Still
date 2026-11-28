package com.sanluca.still

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.health.connect.client.HealthConnectClient
import com.sanluca.still.core.cancelGongVibration
import com.sanluca.still.ui.theme.StillColorPalette
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate

@Stable
internal class StillScreenStateHolder(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
    val healthConnectManager: HealthConnectManager,
    initialShowHealthRationale: Boolean
) {
    private val appContext: Context = context.applicationContext ?: context
    private val initialServiceSnapshot: StillTimerState = StillTimerService.timerState.value
    private val hasActiveServiceSession: Boolean =
        initialServiceSnapshot.isRunning || initialServiceSnapshot.isPaused

    var showPrivacyDialog by mutableStateOf(initialShowHealthRationale)
    var showGuideDialog by mutableStateOf(false)
    var showWhatsNewDialog by mutableStateOf(false)
    var hasEvaluatedWhatsNew by mutableStateOf(false)
    var isNotificationPromptInFlight by mutableStateOf(false)

    var showSettingsDialog by mutableStateOf(false)
    var showPresetPickerDialog by mutableStateOf(false)
    var showPresetManagerDialog by mutableStateOf(false)
    var showReminderDialog by mutableStateOf(false)
    var showStatsDialog by mutableStateOf(false)
    var selectedCalendarDate by mutableStateOf<LocalDate?>(null)
    var sessionToDelete by mutableStateOf<SessionLogEntry?>(null)

    var isHealthConnected by mutableStateOf(isHealthConnectedStateSaved(context))
    var isResyncing by mutableStateOf(false)
    var dndGranted by mutableStateOf(isDndPermissionGranted(context))
    var sessionHistoryList by mutableStateOf(getLocalSessionHistory(context))

    var reminderSettings by mutableStateOf(getSavedReminderSettings(context))
    var presets by mutableStateOf(getSavedPresets(context))
    var activePresetId by mutableStateOf(getActivePresetId(context))

    val activePreset: MeditationPreset?
        get() = presets.firstOrNull { it.id == activePresetId }

    var selectedTimeInSeconds by mutableIntStateOf(
        if (hasActiveServiceSession) initialServiceSnapshot.selectedTimeSeconds
        else activePreset?.durationSeconds ?: getSavedSelectedTime(context)
    )
    var activeSessionPlannedSeconds by mutableIntStateOf(
        if (hasActiveServiceSession) initialServiceSnapshot.activeSessionPlannedSeconds
        else selectedTimeInSeconds
    )
    var lastTimedSeconds by mutableIntStateOf(
        if (selectedTimeInSeconds > 0) selectedTimeInSeconds
        else getSavedSelectedTime(context).takeIf { it > 0 } ?: 600
    )

    var prepTimeSeconds by mutableIntStateOf(getSavedPrepTime(context))
    var gongFreqHz by mutableDoubleStateOf(getSavedGongFrequency(context))
    var intervalGongFreqHz by mutableDoubleStateOf(getSavedIntervalGongFrequency(context))
    var gongVolumePercent by mutableIntStateOf(getSavedGongVolume(context))
    var alertMode by mutableIntStateOf(getSavedAlertMode(context))
    var intervalMins by mutableIntStateOf(getSavedIntervalMins(context))
    var autoDndEnabled by mutableStateOf(getSavedAutoDnd(context))
    var timerStyle by mutableIntStateOf(getSavedTimerStyle(context))
    var ambientSoundscape by mutableStateOf(getSavedAmbientSoundscape(context))
    var ambientVolumePercent by mutableIntStateOf(getSavedAmbientVolume(context))
    var customAmbientUri by mutableStateOf(getSavedCustomAmbientUri(context))
    var customAmbientName by mutableStateOf(getSavedCustomAmbientName(context))

    var isPreparing by mutableStateOf(hasActiveServiceSession && initialServiceSnapshot.isPreparing)
    var prepTimeLeftSeconds by mutableIntStateOf(
        if (hasActiveServiceSession) initialServiceSnapshot.prepLeftSeconds else 0
    )
    var timeLeftInSeconds by mutableIntStateOf(
        if (hasActiveServiceSession) initialServiceSnapshot.timeLeftSeconds
        else if (selectedTimeInSeconds == -1) 0 else selectedTimeInSeconds
    )
    var isOvertime by mutableStateOf(hasActiveServiceSession && initialServiceSnapshot.isOvertime)
    var overtimeSeconds by mutableIntStateOf(
        if (hasActiveServiceSession) initialServiceSnapshot.overtimeSeconds else 0
    )
    var isTimerRunning by mutableStateOf(initialServiceSnapshot.isRunning)
    var hasStartedFromUi by mutableStateOf(hasActiveServiceSession)
    var hideTimerNumbers by mutableStateOf(false)
    var sessionStartTime by mutableStateOf(
        initialServiceSnapshot.sessionStartEpochMilli?.let { Instant.ofEpochMilli(it) }
    )

    val effectivePrepSeconds: Int
        get() = activePreset?.prepSeconds ?: prepTimeSeconds
    val effectiveGongFreqHz: Double
        get() = activePreset?.gongFreqHz ?: gongFreqHz
    val effectiveIntervalGongFreqHz: Double
        get() = activePreset?.intervalGongFreqHz ?: intervalGongFreqHz
    val effectiveGongVolumePercent: Int
        get() = activePreset?.gongVolumePercent ?: gongVolumePercent
    val effectiveAlertMode: Int
        get() = activePreset?.alertMode ?: alertMode
    val effectiveIntervalMins: Int
        get() = activePreset?.intervalMins ?: intervalMins
    val effectiveAutoDndEnabled: Boolean
        get() = activePreset?.autoDndEnabled ?: autoDndEnabled
    val effectiveTimerStyle: Int
        get() = activePreset?.timerStyle ?: timerStyle
    val effectiveAmbientSoundscape: AmbientSoundscape
        get() = activePreset?.effectiveAmbientSoundscape() ?: ambientSoundscape
    val effectiveAmbientVolumePercent: Int
        get() = activePreset?.ambientVolumePercent ?: ambientVolumePercent
    val effectiveCustomAmbientUri: String?
        get() = if (activePreset?.ambientSoundId != null) {
            if (effectiveAmbientSoundscape == AmbientSoundscape.CUSTOM) {
                activePreset?.customAmbientUri ?: customAmbientUri
            } else {
                null
            }
        } else {
            customAmbientUri
        }

    val progress: Float
        get() = if (activeSessionPlannedSeconds > 0) {
            (timeLeftInSeconds.toFloat() / activeSessionPlannedSeconds.toFloat()).coerceIn(0f, 1f)
        } else {
            1f
        }

    val isInitialState: Boolean
        get() = !isTimerRunning && !isOvertime && sessionStartTime == null

    val keepScreenAwake: Boolean
        get() = isTimerRunning || isOvertime

    fun syncFromServiceState(serviceState: StillTimerState) {
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

    fun syncIdleTimeWhenSelectedTimeChanges() {
        if (!isTimerRunning && sessionStartTime == null && !isOvertime) {
            activeSessionPlannedSeconds = selectedTimeInSeconds
            timeLeftInSeconds = if (selectedTimeInSeconds == -1) 0 else selectedTimeInSeconds
            isOvertime = false
            overtimeSeconds = 0
        }
    }

    fun refreshOnResume() {
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

    fun checkInitialHealthConnection() {
        coroutineScope.launch {
            if (healthConnectManager.hasAllPermissions()) {
                isHealthConnected = true
                saveHealthConnectedState(context, true)
                syncPendingSessionsToHealthConnect(context, healthConnectManager)
            }
        }
    }

    fun onSharedPreferenceChanged(key: String?, isLifecycleStarted: Boolean) {
        if (key == KEY_SESSION_HISTORY) {
            sessionHistoryList = getLocalSessionHistory(context)
        } else if (key == KEY_PENDING_HEALTH_SESSIONS && isLifecycleStarted) {
            coroutineScope.launch {
                if (healthConnectManager.hasAllPermissions()) {
                    syncPendingSessionsToHealthConnect(context, healthConnectManager)
                }
            }
        }
    }

    fun openStats() {
        sessionHistoryList = getLocalSessionHistory(context)
        coroutineScope.launch {
            syncPendingSessionsToHealthConnect(context, healthConnectManager)
        }
        showStatsDialog = true
    }

    fun startOrResumeTimer(onRequestNotificationPermission: (String) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            isNotificationPromptInFlight = true
            onRequestNotificationPermission(Manifest.permission.POST_NOTIFICATIONS)
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

    fun toggleHideTimerNumbers() {
        if (isTimerRunning || isOvertime) {
            hideTimerNumbers = !hideTimerNumbers
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

    fun deleteLoggedSession(session: SessionLogEntry) {
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
            Toast.makeText(
                appContext,
                appContext.getString(R.string.toast_session_deleted),
                Toast.LENGTH_SHORT
            ).show()
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

    fun handlePickedCustomAudio(uri: Uri?) {
        if (uri == null) return
        val displayName = resolveAudioDisplayName(appContext, uri)
            ?: appContext.getString(R.string.ambient_sound_custom)
        if (!isSupportedCustomAudio(appContext, uri, displayName)) {
            Toast.makeText(
                appContext,
                appContext.getString(R.string.toast_custom_audio_unsupported),
                Toast.LENGTH_LONG
            ).show()
            return
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

    fun exportHistoryToUri(uri: Uri?) {
        if (uri == null) return
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

    fun importHistoryFromUri(uri: Uri?) {
        if (uri == null) return
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

    fun onHealthPermissionsResult(grantedPermissions: Set<String>) {
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

    fun connectToHealth(launchPermissions: (Set<String>) -> Unit) {
        when (healthConnectManager.getHealthConnectStatus()) {
            HealthConnectClient.SDK_AVAILABLE -> {
                launchPermissions(healthConnectManager.permissions)
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

    fun resyncHealth() {
        if (isResyncing) return
        isResyncing = true
        coroutineScope.launch {
            try {
                val ok = resyncHistoryToHealthConnect(context, healthConnectManager)
                val msg = if (ok) {
                    context.getString(R.string.toast_health_resync_done)
                } else {
                    context.getString(R.string.toast_health_permission_denied)
                }
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            } finally {
                isResyncing = false
            }
        }
    }

    fun updatePrepTime(secs: Int) {
        prepTimeSeconds = secs
        savePrepTime(context, secs)
    }

    fun updateIntervalMins(mins: Int) {
        intervalMins = mins
        saveIntervalMins(context, mins)
    }

    fun updateGongFreq(freq: Double) {
        gongFreqHz = freq
        saveGongFrequency(context, freq)
    }

    fun updateIntervalGongFreq(freq: Double) {
        intervalGongFreqHz = freq
        saveIntervalGongFrequency(context, freq)
    }

    fun updateGongVolume(vol: Int) {
        gongVolumePercent = vol
        saveGongVolume(context, vol)
    }

    fun updateAlertMode(mode: Int) {
        alertMode = mode
        saveAlertMode(context, mode)
    }

    fun updateTimerStyle(style: Int) {
        timerStyle = style
        saveTimerStyle(context, style)
    }

    fun updateAutoDnd(enabled: Boolean) {
        autoDndEnabled = enabled
        saveAutoDnd(context, enabled)
    }

    fun updateAmbientSoundscape(sound: AmbientSoundscape) {
        ambientSoundscape = sound
        saveAmbientSoundscape(context, sound)
        if (activePreset?.ambientSoundId == null) {
            sendAmbientUpdateToService(sound, effectiveAmbientVolumePercent, customAmbientUri)
        }
    }

    fun updateAmbientVolume(vol: Int) {
        ambientVolumePercent = vol
        saveAmbientVolume(context, vol)
        if (activePreset?.ambientVolumePercent == null) {
            sendAmbientUpdateToService(effectiveAmbientSoundscape, vol, effectiveCustomAmbientUri)
        }
    }

    fun updateReminderSettings(updated: MeditationReminderSettings) {
        reminderSettings = updated
        saveReminderSettings(context, updated)
        scheduleMeditationReminder(
            context,
            updated,
            afterTrigger = false,
            preserveExistingFuture = false
        )
    }

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

    fun saveEditedPreset(editedPreset: MeditationPreset) {
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
    }

    fun deletePresetById(deletedId: String) {
        val updated = deletePreset(context, deletedId)
        presets = updated
        if (activePresetId == deletedId) {
            activePresetId = null
            syncIdleDurationForPreset(null)
        }
    }

    fun buildCurrentGlobalSnapshot(selectedPalette: StillColorPalette): MeditationPreset =
        MeditationPreset(
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

    private fun openHealthConnectInPlayStore() {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.apps.healthdata"))
            context.startActivity(intent)
        } catch (_: Exception) {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata"))
            context.startActivity(webIntent)
        }
    }
}

@Composable
internal fun rememberStillScreenStateHolder(
    context: Context,
    initialShowHealthRationale: Boolean
): StillScreenStateHolder {
    val coroutineScope = rememberCoroutineScope()
    val healthConnectManager = remember(context) { HealthConnectManager(context) }
    return remember(context, coroutineScope, healthConnectManager) {
        StillScreenStateHolder(
            context = context,
            coroutineScope = coroutineScope,
            healthConnectManager = healthConnectManager,
            initialShowHealthRationale = initialShowHealthRationale
        )
    }
}
