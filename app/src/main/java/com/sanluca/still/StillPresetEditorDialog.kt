package com.sanluca.still

import android.content.Intent
import android.widget.Toast
import com.sanluca.still.core.ALERT_MODE_BOTH
import com.sanluca.still.core.ALERT_MODE_SOUND
import com.sanluca.still.core.ALERT_MODE_VIBRATION
import com.sanluca.still.core.BellSound
import com.sanluca.still.core.TINGSHA_FREQ_HZ
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanluca.still.ui.theme.StillColorPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

@Composable
internal fun StillPresetEditorDialog(
    initialPreset: MeditationPreset?,
    currentGlobalSnapshot: MeditationPreset,
    onSave: (MeditationPreset) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val appContext = context.applicationContext ?: context
    val audioPreview = rememberAudioPreviewController(context)

    var name by remember(initialPreset) { mutableStateOf(initialPreset?.name ?: "") }
    var durationSeconds by remember(initialPreset) { mutableStateOf(initialPreset?.durationSeconds) }
    var durationMinutesInput by remember(initialPreset) {
        mutableStateOf(
            initialPreset?.durationSeconds?.takeIf { it > 0 }?.let { (it / 60).coerceAtLeast(1).toString() } ?: "10"
        )
    }
    var prepSeconds by remember(initialPreset) { mutableStateOf(initialPreset?.prepSeconds) }
    var prepInput by remember(initialPreset) {
        mutableStateOf((initialPreset?.prepSeconds ?: 10).toString())
    }
    var gongFreqHz by remember(initialPreset) { mutableStateOf(initialPreset?.gongFreqHz) }
    var gongVolumePercent by remember(initialPreset) { mutableStateOf(initialPreset?.gongVolumePercent) }
    var alertMode by remember(initialPreset) { mutableStateOf(initialPreset?.alertMode) }
    var intervalMins by remember(initialPreset) { mutableStateOf(initialPreset?.intervalMins) }
    var intervalInput by remember(initialPreset) {
        mutableStateOf(initialPreset?.intervalMins?.takeIf { it > 0 }?.toString() ?: "5")
    }
    var intervalGongFreqHz by remember(initialPreset) { mutableStateOf(initialPreset?.intervalGongFreqHz) }
    var ambientSoundId by remember(initialPreset) { mutableStateOf(initialPreset?.ambientSoundId) }
    var ambientVolumePercent by remember(initialPreset) { mutableStateOf(initialPreset?.ambientVolumePercent) }
    var customAmbientUri by remember(initialPreset) { mutableStateOf(initialPreset?.customAmbientUri) }
    var customAmbientName by remember(initialPreset) { mutableStateOf(initialPreset?.customAmbientName) }
    var timerStyle by remember(initialPreset) { mutableStateOf(initialPreset?.timerStyle) }
    var colorPaletteId by remember(initialPreset) { mutableStateOf(initialPreset?.colorPaletteId) }
    var autoDndEnabled by remember(initialPreset) { mutableStateOf(initialPreset?.autoDndEnabled) }

    var customAudioAvailable by remember(customAmbientUri) {
        mutableStateOf(!customAmbientUri.isNullOrBlank())
    }
    val toastCustomAudioMissingMsg = stringResource(R.string.toast_custom_audio_missing)

    LaunchedEffect(customAmbientUri) {
        customAudioAvailable = !customAmbientUri.isNullOrBlank() &&
            withContext(Dispatchers.IO) { isAudioUriReadable(context, customAmbientUri) }
    }

    fun playPreviewGong(freq: Double, vol: Int = gongVolumePercent ?: currentGlobalSnapshot.gongVolumePercent ?: 100) {
        if (alertMode == ALERT_MODE_VIBRATION) return
        audioPreview.playBellPreview(freq, vol)
    }

    fun playPreviewAmbient(
        soundscape: AmbientSoundscape,
        vol: Int = ambientVolumePercent ?: currentGlobalSnapshot.ambientVolumePercent ?: 50,
        uriOverride: String? = customAmbientUri ?: currentGlobalSnapshot.customAmbientUri
    ) {
        audioPreview.playAmbientPreview(
            soundscape = soundscape,
            volumePercent = vol,
            customUri = uriOverride,
            durationMs = 3600L,
            fadeInSeconds = 0.6,
            fadeOutSeconds = 0.8,
            onPlaybackError = {
                if (soundscape == AmbientSoundscape.CUSTOM) {
                    customAudioAvailable = false
                    Toast.makeText(context, toastCustomAudioMissingMsg, Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    val pickPresetCustomAudioLauncher = rememberLauncherForActivityResult(
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
        customAmbientUri = uriString
        customAmbientName = displayName
        customAudioAvailable = true
        ambientSoundId = AmbientSoundscape.CUSTOM.id
        playPreviewAmbient(AmbientSoundscape.CUSTOM, uriOverride = uriString)
    }

    val isSoundEnabled = alertMode != ALERT_MODE_VIBRATION
    val selectedAmbientSoundscape = ambientSoundId?.let { AmbientSoundscape.fromId(it) }
    val globalChipLabel = stringResource(R.string.preset_option_global)

    AlertDialog(
        onDismissRequest = {
            audioPreview.stopAll()
            onDismiss()
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                text = stringResource(
                    if (initialPreset == null) R.string.dialog_preset_new_title
                    else R.string.dialog_preset_edit_title
                ),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.primary
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 1. NOME E AZIONI RAPIDE
                PresetEditorIdentityCard(
                    name = name,
                    onNameChange = { name = it },
                    onUseCurrentSettings = {
                        durationSeconds = currentGlobalSnapshot.durationSeconds
                        durationMinutesInput = currentGlobalSnapshot.durationSeconds
                            ?.takeIf { it > 0 }
                            ?.let { (it / 60).coerceAtLeast(1).toString() }
                            ?: "10"
                        prepSeconds = currentGlobalSnapshot.prepSeconds
                        prepInput = (currentGlobalSnapshot.prepSeconds ?: 0).toString()
                        gongFreqHz = currentGlobalSnapshot.gongFreqHz
                        gongVolumePercent = currentGlobalSnapshot.gongVolumePercent
                        alertMode = currentGlobalSnapshot.alertMode
                        intervalMins = currentGlobalSnapshot.intervalMins
                        intervalInput = currentGlobalSnapshot.intervalMins
                            ?.takeIf { it > 0 }?.toString() ?: "5"
                        intervalGongFreqHz = currentGlobalSnapshot.intervalGongFreqHz
                        ambientSoundId = currentGlobalSnapshot.ambientSoundId
                        ambientVolumePercent = currentGlobalSnapshot.ambientVolumePercent
                        customAmbientUri = currentGlobalSnapshot.customAmbientUri
                        customAmbientName = currentGlobalSnapshot.customAmbientName
                        timerStyle = currentGlobalSnapshot.timerStyle
                        colorPaletteId = currentGlobalSnapshot.colorPaletteId
                        autoDndEnabled = currentGlobalSnapshot.autoDndEnabled
                    },
                    onResetAllGlobal = {
                        durationSeconds = null
                        prepSeconds = null
                        gongFreqHz = null
                        gongVolumePercent = null
                        alertMode = null
                        intervalMins = null
                        intervalGongFreqHz = null
                        ambientSoundId = null
                        ambientVolumePercent = null
                        customAmbientUri = null
                        customAmbientName = null
                        timerStyle = null
                        colorPaletteId = null
                        autoDndEnabled = null
                        audioPreview.stopAmbientPreview()
                    }
                )

                // 2. DURATA, PREPARAZIONE E CAMPANA INTERMEDIA
                PresetEditorTimingCard(
                    globalChipLabel = globalChipLabel,
                    durationSeconds = durationSeconds,
                    onDurationSecondsChange = { durationSeconds = it },
                    durationMinutesInput = durationMinutesInput,
                    onDurationMinutesInputChange = { durationMinutesInput = it },
                    prepSeconds = prepSeconds,
                    onPrepSecondsChange = { prepSeconds = it },
                    prepInput = prepInput,
                    onPrepInputChange = { prepInput = it },
                    intervalMins = intervalMins,
                    onIntervalMinsChange = { intervalMins = it },
                    intervalInput = intervalInput,
                    onIntervalInputChange = { intervalInput = it },
                    intervalGongFreqHz = intervalGongFreqHz,
                    onIntervalGongFreqChange = { intervalGongFreqHz = it },
                    defaultIntervalGongFreqHz = currentGlobalSnapshot.intervalGongFreqHz ?: TINGSHA_FREQ_HZ,
                    isSoundEnabled = isSoundEnabled,
                    onPreviewBell = { code -> playPreviewGong(code) }
                )

                // 3. CAMPANA PRINCIPALE, VOLUME E MODALITÀ AVVISO
                PresetEditorBellCard(
                    globalChipLabel = globalChipLabel,
                    gongFreqHz = gongFreqHz,
                    onGongFreqChange = { gongFreqHz = it },
                    defaultGongFreqHz = currentGlobalSnapshot.gongFreqHz ?: BellSound.CLASSIC_BOWL.code,
                    gongVolumePercent = gongVolumePercent,
                    onGongVolumeChange = { gongVolumePercent = it },
                    defaultGongVolumePercent = currentGlobalSnapshot.gongVolumePercent ?: 100,
                    alertMode = alertMode,
                    onAlertModeChange = { modeVal ->
                        alertMode = modeVal
                        if (modeVal == ALERT_MODE_VIBRATION) {
                            audioPreview.cancelBellPreview()
                        }
                    },
                    isSoundEnabled = isSoundEnabled,
                    onPreviewGong = { freq, vol -> playPreviewGong(freq, vol) }
                )

                // 4. SOTTOFONDO SONORO E VOLUME
                PresetEditorAmbientCard(
                    globalChipLabel = globalChipLabel,
                    ambientSoundId = ambientSoundId,
                    selectedAmbientSoundscape = selectedAmbientSoundscape,
                    onSelectGlobalAmbient = {
                        ambientSoundId = null
                        audioPreview.stopAmbientPreview(fadeOutSeconds = 0.4)
                    },
                    onSelectAmbientOption = { soundOption ->
                        if (soundOption == AmbientSoundscape.CUSTOM) {
                            val candidateUri = customAmbientUri ?: currentGlobalSnapshot.customAmbientUri
                            when {
                                candidateUri.isNullOrBlank() -> {
                                    pickPresetCustomAudioLauncher.launch(
                                        arrayOf("audio/mpeg", "audio/ogg", "application/ogg")
                                    )
                                }
                                !customAmbientUri.isNullOrBlank() && !customAudioAvailable -> {
                                    Toast.makeText(
                                        context,
                                        toastCustomAudioMissingMsg,
                                        Toast.LENGTH_LONG
                                    ).show()
                                    pickPresetCustomAudioLauncher.launch(
                                        arrayOf("audio/mpeg", "audio/ogg", "application/ogg")
                                    )
                                }
                                else -> {
                                    if (customAmbientUri.isNullOrBlank()) {
                                        customAmbientUri = currentGlobalSnapshot.customAmbientUri
                                        customAmbientName = currentGlobalSnapshot.customAmbientName
                                    }
                                    ambientSoundId = soundOption.id
                                    playPreviewAmbient(soundOption, uriOverride = candidateUri)
                                }
                            }
                        } else {
                            ambientSoundId = soundOption.id
                            playPreviewAmbient(soundOption)
                        }
                    },
                    customAmbientUri = customAmbientUri,
                    customAmbientName = customAmbientName,
                    customAudioAvailable = customAudioAvailable,
                    onPickCustomAudio = {
                        pickPresetCustomAudioLauncher.launch(
                            arrayOf("audio/mpeg", "audio/ogg", "application/ogg")
                        )
                    },
                    ambientVolumePercent = ambientVolumePercent,
                    onAmbientVolumeChange = { ambientVolumePercent = it },
                    defaultAmbientVolumePercent = currentGlobalSnapshot.ambientVolumePercent ?: 50,
                    onAdjustAmbientVolume = { newVol ->
                        ambientVolumePercent = newVol
                        val previewSound = selectedAmbientSoundscape
                            ?: currentGlobalSnapshot.effectiveAmbientSoundscape()
                            ?: AmbientSoundscape.OFF
                        audioPreview.setAmbientVolume(
                            volumePercent = newVol,
                            restartSoundscapeIfIdle = previewSound,
                            customUri = customAmbientUri ?: currentGlobalSnapshot.customAmbientUri,
                            durationMs = 3600L,
                            fadeOutSeconds = 0.8,
                            onPlaybackError = {
                                if (previewSound == AmbientSoundscape.CUSTOM) {
                                    customAudioAvailable = false
                                    Toast.makeText(context, toastCustomAudioMissingMsg, Toast.LENGTH_LONG).show()
                                }
                            }
                        )
                    }
                )

                // 5. STILE VISIVO, TEMA COLORI E NON DISTURBARE
                PresetEditorAppearanceCard(
                    globalChipLabel = globalChipLabel,
                    timerStyle = timerStyle,
                    onTimerStyleChange = { timerStyle = it },
                    colorPaletteId = colorPaletteId,
                    onColorPaletteChange = { colorPaletteId = it },
                    autoDndEnabled = autoDndEnabled,
                    onAutoDndChange = { autoDndEnabled = it }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmedName = name.trim()
                    if (trimmedName.isEmpty()) return@Button
                    val finalDuration = when {
                        durationSeconds == null -> null
                        durationSeconds == -1 -> -1
                        else -> (durationMinutesInput.toIntOrNull()?.coerceIn(1, 999)
                            ?: ((durationSeconds ?: 600) / 60).coerceAtLeast(1)) * 60
                    }
                    val finalPrep = prepSeconds?.let {
                        prepInput.toIntOrNull()?.coerceIn(0, 999) ?: it.coerceIn(0, 999)
                    }
                    val finalInterval = when {
                        intervalMins == null -> null
                        intervalMins == 0 -> 0
                        else -> intervalInput.toIntOrNull()?.coerceIn(1, 999)
                            ?: (intervalMins ?: 5).coerceIn(1, 999)
                    }
                    val finalIntervalBell = if (finalInterval == 0) null else intervalGongFreqHz
                    val isCustomSound = ambientSoundId == AmbientSoundscape.CUSTOM.id
                    val finalAmbientVolume = if (ambientSoundId == AmbientSoundscape.OFF.id) null else ambientVolumePercent

                    audioPreview.stopAll()

                    onSave(
                        MeditationPreset(
                            id = initialPreset?.id ?: UUID.randomUUID().toString(),
                            name = trimmedName,
                            durationSeconds = finalDuration,
                            prepSeconds = finalPrep,
                            gongFreqHz = gongFreqHz,
                            gongVolumePercent = gongVolumePercent,
                            alertMode = alertMode,
                            intervalMins = finalInterval,
                            intervalGongFreqHz = finalIntervalBell,
                            ambientSoundId = ambientSoundId,
                            ambientVolumePercent = finalAmbientVolume,
                            customAmbientUri = if (isCustomSound) customAmbientUri else null,
                            customAmbientName = if (isCustomSound) customAmbientName else null,
                            timerStyle = timerStyle,
                            colorPaletteId = colorPaletteId,
                            autoDndEnabled = autoDndEnabled
                        )
                    )
                },
                enabled = name.trim().isNotEmpty()
            ) {
                Text(stringResource(R.string.preset_button_save))
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    audioPreview.stopAll()
                    onDismiss()
                }
            ) {
                Text(stringResource(R.string.button_cancel))
            }
        }
    )
}

@Composable
private fun PresetEditorIdentityCard(
    name: String,
    onNameChange: (String) -> Unit,
    onUseCurrentSettings: () -> Unit,
    onResetAllGlobal: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= 40) onNameChange(it) },
                label = { Text(stringResource(R.string.preset_field_name)) },
                placeholder = { Text(stringResource(R.string.preset_field_name_placeholder)) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onUseCurrentSettings,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = stringResource(R.string.preset_button_use_current),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                OutlinedButton(
                    onClick = onResetAllGlobal,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = stringResource(R.string.preset_button_all_global),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun PresetEditorTimingCard(
    globalChipLabel: String,
    durationSeconds: Int?,
    onDurationSecondsChange: (Int?) -> Unit,
    durationMinutesInput: String,
    onDurationMinutesInputChange: (String) -> Unit,
    prepSeconds: Int?,
    onPrepSecondsChange: (Int?) -> Unit,
    prepInput: String,
    onPrepInputChange: (String) -> Unit,
    intervalMins: Int?,
    onIntervalMinsChange: (Int?) -> Unit,
    intervalInput: String,
    onIntervalInputChange: (String) -> Unit,
    intervalGongFreqHz: Double?,
    onIntervalGongFreqChange: (Double?) -> Unit,
    defaultIntervalGongFreqHz: Double,
    isSoundEnabled: Boolean,
    onPreviewBell: (Double) -> Unit
) {
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
                text = stringResource(R.string.preset_section_duration),
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
                    selected = durationSeconds == null,
                    onClick = { onDurationSecondsChange(null) },
                    label = { Text(globalChipLabel, fontSize = 12.sp) }
                )
                FilterChip(
                    selected = durationSeconds == -1,
                    onClick = { onDurationSecondsChange(-1) },
                    label = { Text("∞", fontSize = 13.sp) }
                )
                FilterChip(
                    selected = durationSeconds != null && durationSeconds > 0,
                    onClick = {
                        val mins = durationMinutesInput.toIntOrNull()?.coerceIn(1, 999) ?: 10
                        onDurationMinutesInputChange(mins.toString())
                        onDurationSecondsChange(mins * 60)
                    },
                    label = { Text(stringResource(R.string.preset_option_minutes), fontSize = 12.sp) }
                )
            }

            if (durationSeconds != null && durationSeconds > 0) {
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(5, 10, 15, 20, 30, 45).forEach { quickMin ->
                        FilterChip(
                            selected = durationSeconds == quickMin * 60,
                            onClick = {
                                onDurationMinutesInputChange(quickMin.toString())
                                onDurationSecondsChange(quickMin * 60)
                            },
                            label = { Text("${quickMin}m", fontSize = 12.sp) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = durationMinutesInput,
                    onValueChange = { input ->
                        if (input.all { it.isDigit() } && input.length <= 3) {
                            onDurationMinutesInputChange(input)
                            input.toIntOrNull()?.takeIf { it > 0 }?.let { mins ->
                                onDurationSecondsChange(mins * 60)
                            }
                        }
                    },
                    label = { Text(stringResource(R.string.preset_duration_minutes_label), fontSize = 11.sp) },
                    placeholder = { Text(stringResource(R.string.preset_duration_minutes_placeholder)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = stringResource(R.string.preset_section_prep),
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
                    selected = prepSeconds == null,
                    onClick = { onPrepSecondsChange(null) },
                    label = { Text(globalChipLabel, fontSize = 12.sp) }
                )
                FilterChip(
                    selected = prepSeconds != null,
                    onClick = {
                        val secs = prepInput.toIntOrNull()?.coerceIn(0, 999) ?: 10
                        onPrepInputChange(secs.toString())
                        onPrepSecondsChange(secs)
                    },
                    label = { Text(stringResource(R.string.preset_option_custom), fontSize = 12.sp) }
                )
            }
            if (prepSeconds != null) {
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(0, 5, 10, 15, 30).forEach { quickSec ->
                        FilterChip(
                            selected = prepSeconds == quickSec,
                            onClick = {
                                onPrepInputChange(quickSec.toString())
                                onPrepSecondsChange(quickSec)
                            },
                            label = { Text("${quickSec}s", fontSize = 12.sp) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = prepInput,
                    onValueChange = { input ->
                        if (input.all { it.isDigit() } && input.length <= 3) {
                            onPrepInputChange(input)
                            onPrepSecondsChange(input.toIntOrNull() ?: 0)
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

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = stringResource(R.string.preset_section_interval),
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
                    selected = intervalMins == null,
                    onClick = { onIntervalMinsChange(null) },
                    label = { Text(globalChipLabel, fontSize = 12.sp) }
                )
                FilterChip(
                    selected = intervalMins == 0,
                    onClick = {
                        onIntervalMinsChange(0)
                        onIntervalGongFreqChange(null)
                    },
                    label = { Text(stringResource(R.string.preset_option_off), fontSize = 12.sp) }
                )
                FilterChip(
                    selected = intervalMins != null && intervalMins > 0,
                    onClick = {
                        val mins = intervalInput.toIntOrNull()?.coerceIn(1, 999) ?: 5
                        onIntervalInputChange(mins.toString())
                        onIntervalMinsChange(mins)
                    },
                    label = { Text(stringResource(R.string.preset_option_minutes), fontSize = 12.sp) }
                )
            }
            if (intervalMins != null && intervalMins > 0) {
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = intervalInput,
                    onValueChange = { input ->
                        if (input.all { it.isDigit() } && input.length <= 3) {
                            onIntervalInputChange(input)
                            input.toIntOrNull()?.takeIf { it > 0 }?.let { mins ->
                                onIntervalMinsChange(mins)
                            }
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
            if (intervalMins != 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.preset_section_interval_bell),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                StillBellDropdown(
                    selectedBell = intervalGongFreqHz?.let { BellSound.fromCode(it, BellSound.PURE_BELL) },
                    enabled = isSoundEnabled,
                    allowGlobalOption = true,
                    globalLabel = globalChipLabel,
                    previewBellFallback = BellSound.fromCode(defaultIntervalGongFreqHz, BellSound.PURE_BELL),
                    onSelectBell = { bell -> onIntervalGongFreqChange(bell?.code) },
                    onPreviewBell = { bell -> onPreviewBell(bell.code) }
                )
            }
        }
    }
}

@Composable
private fun PresetEditorBellCard(
    globalChipLabel: String,
    gongFreqHz: Double?,
    onGongFreqChange: (Double?) -> Unit,
    defaultGongFreqHz: Double,
    gongVolumePercent: Int?,
    onGongVolumeChange: (Int?) -> Unit,
    defaultGongVolumePercent: Int,
    alertMode: Int?,
    onAlertModeChange: (Int?) -> Unit,
    isSoundEnabled: Boolean,
    onPreviewGong: (Double, Int) -> Unit
) {
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
            StillBellDropdown(
                selectedBell = gongFreqHz?.let { BellSound.fromCode(it, BellSound.CLASSIC_BOWL) },
                enabled = isSoundEnabled,
                allowGlobalOption = true,
                globalLabel = globalChipLabel,
                previewBellFallback = BellSound.fromCode(defaultGongFreqHz, BellSound.CLASSIC_BOWL),
                onSelectBell = { bell -> onGongFreqChange(bell?.code) },
                onPreviewBell = { bell -> onPreviewGong(bell.code, gongVolumePercent ?: defaultGongVolumePercent) }
            )

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = stringResource(R.string.settings_gong_volume),
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
                    selected = gongVolumePercent == null,
                    onClick = { onGongVolumeChange(null) },
                    enabled = isSoundEnabled,
                    label = { Text(globalChipLabel, fontSize = 12.sp) }
                )
                FilterChip(
                    selected = gongVolumePercent != null,
                    onClick = { onGongVolumeChange(gongVolumePercent ?: defaultGongVolumePercent) },
                    enabled = isSoundEnabled,
                    label = { Text(stringResource(R.string.preset_option_custom), fontSize = 12.sp) }
                )
            }
            gongVolumePercent?.let { currentVol ->
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            if (currentVol > 20) {
                                val newVol = (currentVol - 10).coerceAtLeast(20)
                                onGongVolumeChange(newVol)
                                onPreviewGong(gongFreqHz ?: defaultGongFreqHz, newVol)
                            }
                        },
                        enabled = isSoundEnabled && currentVol > 20,
                        modifier = Modifier
                            .widthIn(min = 48.dp)
                            .heightIn(min = 36.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("−", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        text = "$currentVol%",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSoundEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                    OutlinedButton(
                        onClick = {
                            if (currentVol < 100) {
                                val newVol = (currentVol + 10).coerceAtMost(100)
                                onGongVolumeChange(newVol)
                                onPreviewGong(gongFreqHz ?: defaultGongFreqHz, newVol)
                            }
                        },
                        enabled = isSoundEnabled && currentVol < 100,
                        modifier = Modifier
                            .widthIn(min = 48.dp)
                            .heightIn(min = 36.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("+", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
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
                FilterChip(
                    selected = alertMode == null,
                    onClick = { onAlertModeChange(null) },
                    label = { Text(globalChipLabel, fontSize = 12.sp) }
                )
                listOf(
                    ALERT_MODE_BOTH to R.string.alert_mode_both,
                    ALERT_MODE_SOUND to R.string.alert_mode_bell,
                    ALERT_MODE_VIBRATION to R.string.alert_mode_vibrate
                ).forEach { (modeVal, labelRes) ->
                    FilterChip(
                        selected = alertMode == modeVal,
                        onClick = { onAlertModeChange(modeVal) },
                        label = { Text(stringResource(labelRes), fontSize = 12.sp) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PresetEditorAmbientCard(
    globalChipLabel: String,
    ambientSoundId: String?,
    selectedAmbientSoundscape: AmbientSoundscape?,
    onSelectGlobalAmbient: () -> Unit,
    onSelectAmbientOption: (AmbientSoundscape) -> Unit,
    customAmbientUri: String?,
    customAmbientName: String?,
    customAudioAvailable: Boolean,
    onPickCustomAudio: () -> Unit,
    ambientVolumePercent: Int?,
    onAmbientVolumeChange: (Int?) -> Unit,
    defaultAmbientVolumePercent: Int,
    onAdjustAmbientVolume: (Int) -> Unit
) {
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
                text = stringResource(R.string.settings_ambient_sound),
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
                    selected = ambientSoundId == null,
                    onClick = onSelectGlobalAmbient,
                    label = { Text(globalChipLabel, fontSize = 12.sp) }
                )
                ALL_AMBIENT_CHOICES.forEach { (soundOption, labelRes) ->
                    FilterChip(
                        selected = selectedAmbientSoundscape == soundOption,
                        onClick = { onSelectAmbientOption(soundOption) },
                        label = { Text(stringResource(labelRes), fontSize = 12.sp) }
                    )
                }
            }

            if (selectedAmbientSoundscape == AmbientSoundscape.CUSTOM) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val fileLabel = when {
                        customAmbientUri.isNullOrBlank() -> stringResource(R.string.settings_custom_audio_choose)
                        !customAudioAvailable -> stringResource(R.string.settings_custom_audio_missing)
                        else -> customAmbientName ?: stringResource(R.string.ambient_sound_custom)
                    }
                    Text(
                        text = fileLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (!customAmbientUri.isNullOrBlank() && !customAudioAvailable) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    )
                    OutlinedButton(
                        onClick = onPickCustomAudio,
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.heightIn(min = 38.dp)
                    ) {
                        Text(
                            text = stringResource(
                                if (customAmbientUri.isNullOrBlank()) R.string.settings_custom_audio_choose
                                else R.string.settings_custom_audio_change
                            ),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (selectedAmbientSoundscape != AmbientSoundscape.OFF) {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = stringResource(R.string.settings_ambient_volume),
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
                        selected = ambientVolumePercent == null,
                        onClick = { onAmbientVolumeChange(null) },
                        label = { Text(globalChipLabel, fontSize = 12.sp) }
                    )
                    FilterChip(
                        selected = ambientVolumePercent != null,
                        onClick = { onAmbientVolumeChange(ambientVolumePercent ?: defaultAmbientVolumePercent) },
                        label = { Text(stringResource(R.string.preset_option_custom), fontSize = 12.sp) }
                    )
                }
                ambientVolumePercent?.let { currentVol ->
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (currentVol > 10) {
                                    onAdjustAmbientVolume((currentVol - 10).coerceAtLeast(10))
                                }
                            },
                            enabled = currentVol > 10,
                            modifier = Modifier
                                .widthIn(min = 48.dp)
                                .heightIn(min = 36.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("−", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            text = "$currentVol%",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                        OutlinedButton(
                            onClick = {
                                if (currentVol < 100) {
                                    onAdjustAmbientVolume((currentVol + 10).coerceAtMost(100))
                                }
                            },
                            enabled = currentVol < 100,
                            modifier = Modifier
                                .widthIn(min = 48.dp)
                                .heightIn(min = 36.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("+", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetEditorAppearanceCard(
    globalChipLabel: String,
    timerStyle: Int?,
    onTimerStyleChange: (Int?) -> Unit,
    colorPaletteId: String?,
    onColorPaletteChange: (String?) -> Unit,
    autoDndEnabled: Boolean?,
    onAutoDndChange: (Boolean?) -> Unit
) {
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
                    selected = timerStyle == null,
                    onClick = { onTimerStyleChange(null) },
                    label = { Text(globalChipLabel, fontSize = 12.sp) }
                )
                listOf(
                    TIMER_STYLE_ESSENTIAL to R.string.settings_timer_style_essential,
                    0 to R.string.settings_timer_style_circle,
                    1 to R.string.settings_timer_style_hourglass,
                    2 to R.string.settings_timer_style_candle
                ).forEach { (styleVal, labelRes) ->
                    FilterChip(
                        selected = timerStyle == styleVal,
                        onClick = { onTimerStyleChange(styleVal) },
                        label = { Text(stringResource(labelRes), fontSize = 12.sp) }
                    )
                }
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
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = colorPaletteId == null,
                    onClick = { onColorPaletteChange(null) },
                    label = { Text(globalChipLabel, fontSize = 12.sp) }
                )
                StillColorPalette.entries.forEach { palette ->
                    FilterChip(
                        selected = colorPaletteId == palette.id,
                        onClick = { onColorPaletteChange(palette.id) },
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(palette.swatchColor, CircleShape)
                                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                            )
                        },
                        label = { Text(stringResource(palette.labelRes), fontSize = 12.sp) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = stringResource(R.string.settings_dnd_mode),
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
                    selected = autoDndEnabled == null,
                    onClick = { onAutoDndChange(null) },
                    label = { Text(globalChipLabel, fontSize = 12.sp) }
                )
                FilterChip(
                    selected = autoDndEnabled == true,
                    onClick = { onAutoDndChange(true) },
                    label = { Text(stringResource(R.string.preset_option_on), fontSize = 12.sp) }
                )
                FilterChip(
                    selected = autoDndEnabled == false,
                    onClick = { onAutoDndChange(false) },
                    label = { Text(stringResource(R.string.preset_option_off), fontSize = 12.sp) }
                )
            }
        }
    }
}
