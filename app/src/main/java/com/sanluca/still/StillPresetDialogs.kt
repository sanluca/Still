package com.sanluca.still

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

private val ALL_BELL_CHOICES = listOf(
    216.0 to R.string.bell_name_gong,
    432.0 to R.string.bell_name_crystal,
    528.0 to R.string.bell_name_keisu,
    TINGSHA_FREQ_HZ to R.string.bell_name_tingsha
) + SOFT_BELL_CHOICES

private val ALL_AMBIENT_CHOICES = listOf(
    AmbientSoundscape.OFF to R.string.ambient_sound_off,
    AmbientSoundscape.LIGHT_RAIN to R.string.ambient_sound_light_rain,
    AmbientSoundscape.GENTLE_RAIN to R.string.ambient_sound_gentle_rain,
    AmbientSoundscape.BIRDS_RAIN to R.string.ambient_sound_birds_rain,
    AmbientSoundscape.OCEAN to R.string.ambient_sound_ocean,
    AmbientSoundscape.RIVER to R.string.ambient_sound_river,
    AmbientSoundscape.FOREST to R.string.ambient_sound_forest,
    AmbientSoundscape.DRONE to R.string.ambient_sound_drone,
    AmbientSoundscape.CUSTOM to R.string.ambient_sound_custom
)

private fun bellNameRes(freqHz: Double): Int {
    return ALL_BELL_CHOICES.firstOrNull { it.first == freqHz }?.second ?: R.string.bell_name_crystal
}

private fun ambientNameRes(soundscape: AmbientSoundscape): Int {
    return ALL_AMBIENT_CHOICES.firstOrNull { it.first == soundscape }?.second ?: R.string.ambient_sound_off
}

private fun timerStyleNameRes(style: Int): Int = when (style) {
    TIMER_STYLE_ESSENTIAL -> R.string.settings_timer_style_essential
    0 -> R.string.settings_timer_style_circle
    1 -> R.string.settings_timer_style_hourglass
    2 -> R.string.settings_timer_style_candle
    else -> R.string.settings_timer_style_essential
}

private fun alertModeNameRes(mode: Int): Int = when (mode) {
    ALERT_MODE_SOUND -> R.string.alert_mode_bell
    ALERT_MODE_VIBRATION -> R.string.alert_mode_vibrate
    else -> R.string.alert_mode_both
}

internal fun buildPresetSummary(context: Context, preset: MeditationPreset): String {
    val parts = mutableListOf<String>()

    preset.durationSeconds?.let { sec ->
        if (sec == -1) {
            parts.add("∞")
        } else if (sec > 0) {
            parts.add(context.getString(R.string.preset_summary_min, (sec / 60).coerceAtLeast(1)))
        }
    }
    preset.prepSeconds?.let { sec ->
        parts.add(context.getString(R.string.preset_summary_prep, sec))
    }
    preset.gongFreqHz?.let { freq ->
        parts.add(context.getString(bellNameRes(freq)))
    }
    preset.gongVolumePercent?.let { vol ->
        parts.add(context.getString(R.string.preset_summary_bell_vol, vol))
    }
    preset.alertMode?.let { mode ->
        parts.add(context.getString(alertModeNameRes(mode)))
    }
    preset.intervalMins?.let { mins ->
        if (mins == 0) {
            parts.add(context.getString(R.string.preset_summary_interval_off))
        } else {
            parts.add(context.getString(R.string.preset_summary_interval, mins))
        }
    }
    preset.intervalGongFreqHz?.let { freq ->
        if (preset.intervalMins != 0) {
            parts.add(context.getString(bellNameRes(freq)))
        }
    }
    preset.effectiveAmbientSoundscape()?.let { sound ->
        if (sound == AmbientSoundscape.CUSTOM && !preset.customAmbientName.isNullOrBlank()) {
            parts.add(preset.customAmbientName)
        } else {
            parts.add(context.getString(ambientNameRes(sound)))
        }
    }
    preset.ambientVolumePercent?.let { vol ->
        if (preset.effectiveAmbientSoundscape() != AmbientSoundscape.OFF) {
            parts.add(context.getString(R.string.preset_summary_ambient_vol, vol))
        }
    }
    preset.timerStyle?.let { style ->
        parts.add(context.getString(timerStyleNameRes(style)))
    }
    preset.effectiveColorPalette()?.let { palette ->
        parts.add(context.getString(palette.labelRes))
    }
    preset.autoDndEnabled?.let { enabled ->
        parts.add(context.getString(if (enabled) R.string.preset_summary_dnd_on else R.string.preset_summary_dnd_off))
    }

    return if (parts.isEmpty()) {
        context.getString(R.string.preset_summary_all_global)
    } else {
        parts.joinToString(" · ")
    }
}

@Composable
fun StillPresetPickerDialog(
    presets: List<MeditationPreset>,
    activePresetId: String?,
    onSelectPreset: (String?) -> Unit,
    onOpenManager: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                text = stringResource(R.string.dialog_presets_select_title),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.primary
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item(key = "preset_none") {
                    val isSelected = activePresetId == null
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelectPreset(null)
                                onDismiss()
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(
                                    text = stringResource(R.string.preset_none_title),
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(R.string.preset_none_subtitle),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (isSelected) {
                                Text(
                                    text = "✓",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                items(presets, key = { it.id }) { preset ->
                    val isSelected = preset.id == activePresetId
                    val summary = remember(preset) { buildPresetSummary(context, preset) }
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelectPreset(preset.id)
                                onDismiss()
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(
                                    text = preset.name,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = summary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (isSelected) {
                                Text(
                                    text = "✓",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                if (presets.isEmpty()) {
                    item(key = "preset_empty_hint") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.preset_picker_empty_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedButton(
                                onClick = {
                                    onDismiss()
                                    onOpenManager()
                                },
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text(stringResource(R.string.preset_button_create))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.button_close), color = MaterialTheme.colorScheme.primary)
            }
        }
    )
}

@Composable
fun StillPresetManagerDialog(
    presets: List<MeditationPreset>,
    activePresetId: String?,
    currentGlobalSnapshot: MeditationPreset,
    onSavePreset: (MeditationPreset) -> Unit,
    onDeletePreset: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var showEditor by remember { mutableStateOf(false) }
    var editingPreset by remember { mutableStateOf<MeditationPreset?>(null) }
    var presetToDelete by remember { mutableStateOf<MeditationPreset?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                text = stringResource(R.string.dialog_presets_manage_title),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.primary
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (presets.isEmpty()) {
                    Text(
                        text = stringResource(R.string.preset_manager_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(presets, key = { it.id }) { preset ->
                            val isActive = preset.id == activePresetId
                            val summary = remember(preset) { buildPresetSummary(context, preset) }
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                border = if (isActive) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                                modifier = Modifier.fillMaxWidth()
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
                                            text = preset.name,
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                                        )
                                        if (isActive) {
                                            Text(
                                                text = stringResource(R.string.preset_active_badge),
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = summary,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        TextButton(
                                            onClick = {
                                                editingPreset = preset
                                                showEditor = true
                                            },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = stringResource(R.string.preset_button_edit),
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        TextButton(
                                            onClick = { presetToDelete = preset },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = stringResource(R.string.button_delete),
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        editingPreset = null
                        showEditor = true
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.preset_button_new))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.button_close), color = MaterialTheme.colorScheme.primary)
            }
        }
    )

    if (showEditor) {
        StillPresetEditorDialog(
            initialPreset = editingPreset,
            currentGlobalSnapshot = currentGlobalSnapshot,
            onSave = { saved ->
                onSavePreset(saved)
                showEditor = false
                editingPreset = null
            },
            onDismiss = {
                showEditor = false
                editingPreset = null
            }
        )
    }

    presetToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { presetToDelete = null },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(24.dp),
            title = {
                Text(
                    text = stringResource(R.string.dialog_preset_delete_title),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.error
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.dialog_preset_delete_message, target.name),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeletePreset(target.id)
                        presetToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.button_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { presetToDelete = null }) {
                    Text(stringResource(R.string.button_cancel))
                }
            }
        )
    }
}

@Composable
private fun StillPresetEditorDialog(
    initialPreset: MeditationPreset?,
    currentGlobalSnapshot: MeditationPreset,
    onSave: (MeditationPreset) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val appContext = context.applicationContext ?: context
    val coroutineScope = rememberCoroutineScope()

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

    var previewGongJob by remember { mutableStateOf<Job?>(null) }
    val previewAmbientPlayer = remember(coroutineScope) { AmbientSoundPlayer(coroutineScope) }
    var previewAmbientJob by remember { mutableStateOf<Job?>(null) }
    var customAudioAvailable by remember(customAmbientUri) {
        mutableStateOf(!customAmbientUri.isNullOrBlank())
    }
    val toastCustomAudioMissingMsg = stringResource(R.string.toast_custom_audio_missing)

    DisposableEffect(Unit) {
        onDispose {
            previewGongJob?.cancel()
            previewAmbientJob?.cancel()
            previewAmbientPlayer.stopImmediate()
        }
    }

    LaunchedEffect(customAmbientUri) {
        customAudioAvailable = !customAmbientUri.isNullOrBlank() &&
            withContext(Dispatchers.IO) { isAudioUriReadable(context, customAmbientUri) }
    }

    fun playPreviewGong(freq: Double, vol: Int = gongVolumePercent ?: currentGlobalSnapshot.gongVolumePercent ?: 100) {
        if (alertMode == ALERT_MODE_VIBRATION) return
        previewGongJob?.cancel()
        previewGongJob = coroutineScope.launch(Dispatchers.Default) {
            playGongSound(context, freq, repeatCount = 1, volumePercent = vol)
        }
    }

    fun playPreviewAmbient(
        soundscape: AmbientSoundscape,
        vol: Int = ambientVolumePercent ?: currentGlobalSnapshot.ambientVolumePercent ?: 50,
        uriOverride: String? = customAmbientUri ?: currentGlobalSnapshot.customAmbientUri
    ) {
        previewAmbientJob?.cancel()
        if (StillTimerService.isRunning) {
            previewAmbientPlayer.stopImmediate()
            return
        }
        if (soundscape == AmbientSoundscape.OFF) {
            previewAmbientPlayer.stop(fadeOutSeconds = 0.5)
            return
        }
        previewAmbientJob = coroutineScope.launch(Dispatchers.Default) {
            previewAmbientPlayer.start(
                context = context,
                soundscape = soundscape,
                volumePercent = vol,
                fadeInSeconds = 0.6,
                fadeOutSeconds = 0.9,
                customUri = uriOverride,
                onPlaybackError = {
                    if (soundscape == AmbientSoundscape.CUSTOM) {
                        customAudioAvailable = false
                        Toast.makeText(context, toastCustomAudioMissingMsg, Toast.LENGTH_LONG).show()
                    }
                }
            )
            delay(3600L)
            previewAmbientPlayer.stop(fadeOutSeconds = 0.8)
        }
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
            previewGongJob?.cancel()
            previewAmbientJob?.cancel()
            previewAmbientPlayer.stopImmediate()
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
                            onValueChange = { if (it.length <= 40) name = it },
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
                                onClick = {
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
                                onClick = {
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
                                    previewAmbientJob?.cancel()
                                    previewAmbientPlayer.stopImmediate()
                                },
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

                // 2. DURATA, PREPARAZIONE E CAMPANA INTERMEDIA
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
                                onClick = { durationSeconds = null },
                                label = { Text(globalChipLabel, fontSize = 12.sp) }
                            )
                            FilterChip(
                                selected = durationSeconds == -1,
                                onClick = { durationSeconds = -1 },
                                label = { Text("∞", fontSize = 13.sp) }
                            )
                            FilterChip(
                                selected = durationSeconds != null && durationSeconds!! > 0,
                                onClick = {
                                    val mins = durationMinutesInput.toIntOrNull()?.coerceIn(1, 999) ?: 10
                                    durationMinutesInput = mins.toString()
                                    durationSeconds = mins * 60
                                },
                                label = { Text(stringResource(R.string.preset_option_minutes), fontSize = 12.sp) }
                            )
                        }

                        if (durationSeconds != null && durationSeconds!! > 0) {
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
                                            durationMinutesInput = quickMin.toString()
                                            durationSeconds = quickMin * 60
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
                                        durationMinutesInput = input
                                        input.toIntOrNull()?.takeIf { it > 0 }?.let { mins ->
                                            durationSeconds = mins * 60
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
                                onClick = { prepSeconds = null },
                                label = { Text(globalChipLabel, fontSize = 12.sp) }
                            )
                            FilterChip(
                                selected = prepSeconds != null,
                                onClick = {
                                    val secs = prepInput.toIntOrNull()?.coerceIn(0, 999) ?: 10
                                    prepInput = secs.toString()
                                    prepSeconds = secs
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
                                            prepInput = quickSec.toString()
                                            prepSeconds = quickSec
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
                                        prepInput = input
                                        prepSeconds = input.toIntOrNull() ?: 0
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
                                onClick = { intervalMins = null },
                                label = { Text(globalChipLabel, fontSize = 12.sp) }
                            )
                            FilterChip(
                                selected = intervalMins == 0,
                                onClick = {
                                    intervalMins = 0
                                    intervalGongFreqHz = null
                                },
                                label = { Text(stringResource(R.string.preset_option_off), fontSize = 12.sp) }
                            )
                            FilterChip(
                                selected = intervalMins != null && intervalMins!! > 0,
                                onClick = {
                                    val mins = intervalInput.toIntOrNull()?.coerceIn(1, 999) ?: 5
                                    intervalInput = mins.toString()
                                    intervalMins = mins
                                },
                                label = { Text(stringResource(R.string.preset_option_minutes), fontSize = 12.sp) }
                            )
                        }
                        if (intervalMins != null && intervalMins!! > 0) {
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = intervalInput,
                                onValueChange = { input ->
                                    if (input.all { it.isDigit() } && input.length <= 3) {
                                        intervalInput = input
                                        input.toIntOrNull()?.takeIf { it > 0 }?.let { mins ->
                                            intervalMins = mins
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
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                FilterChip(
                                    selected = intervalGongFreqHz == null,
                                    onClick = { intervalGongFreqHz = null },
                                    enabled = isSoundEnabled,
                                    label = { Text(globalChipLabel, fontSize = 11.sp) }
                                )
                                ALL_BELL_CHOICES.forEach { (freq, labelRes) ->
                                    FilterChip(
                                        selected = intervalGongFreqHz == freq,
                                        onClick = {
                                            intervalGongFreqHz = freq
                                            playPreviewGong(freq)
                                        },
                                        enabled = isSoundEnabled,
                                        label = { Text(stringResource(labelRes), fontSize = 11.sp) }
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. CAMPANA PRINCIPALE, VOLUME E MODALITÀ AVVISO
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
                                selected = gongFreqHz == null,
                                onClick = { gongFreqHz = null },
                                enabled = isSoundEnabled,
                                label = { Text(globalChipLabel, fontSize = 12.sp) }
                            )
                            ALL_BELL_CHOICES.forEach { (freq, labelRes) ->
                                FilterChip(
                                    selected = gongFreqHz == freq,
                                    onClick = {
                                        gongFreqHz = freq
                                        playPreviewGong(freq)
                                    },
                                    enabled = isSoundEnabled,
                                    label = { Text(stringResource(labelRes), fontSize = 12.sp) }
                                )
                            }
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
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = gongVolumePercent == null,
                                onClick = { gongVolumePercent = null },
                                enabled = isSoundEnabled,
                                label = { Text(globalChipLabel, fontSize = 12.sp) }
                            )
                            FilterChip(
                                selected = gongVolumePercent != null,
                                onClick = {
                                    gongVolumePercent = gongVolumePercent
                                        ?: currentGlobalSnapshot.gongVolumePercent
                                        ?: 100
                                },
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
                                            gongVolumePercent = newVol
                                            playPreviewGong(gongFreqHz ?: currentGlobalSnapshot.gongFreqHz ?: 432.0, newVol)
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
                                            gongVolumePercent = newVol
                                            playPreviewGong(gongFreqHz ?: currentGlobalSnapshot.gongFreqHz ?: 432.0, newVol)
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
                                onClick = { alertMode = null },
                                label = { Text(globalChipLabel, fontSize = 12.sp) }
                            )
                            listOf(
                                ALERT_MODE_BOTH to R.string.alert_mode_both,
                                ALERT_MODE_SOUND to R.string.alert_mode_bell,
                                ALERT_MODE_VIBRATION to R.string.alert_mode_vibrate
                            ).forEach { (modeVal, labelRes) ->
                                FilterChip(
                                    selected = alertMode == modeVal,
                                    onClick = {
                                        alertMode = modeVal
                                        if (modeVal == ALERT_MODE_VIBRATION) {
                                            previewGongJob?.cancel()
                                        }
                                    },
                                    label = { Text(stringResource(labelRes), fontSize = 12.sp) }
                                )
                            }
                        }
                    }
                }

                // 4. SOTTOFONDO SONORO E VOLUME
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
                                onClick = {
                                    ambientSoundId = null
                                    previewAmbientJob?.cancel()
                                    previewAmbientPlayer.stop(fadeOutSeconds = 0.4)
                                },
                                label = { Text(globalChipLabel, fontSize = 12.sp) }
                            )
                            ALL_AMBIENT_CHOICES.forEach { (soundOption, labelRes) ->
                                FilterChip(
                                    selected = selectedAmbientSoundscape == soundOption,
                                    onClick = {
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
                                    onClick = {
                                        pickPresetCustomAudioLauncher.launch(
                                            arrayOf("audio/mpeg", "audio/ogg", "application/ogg")
                                        )
                                    },
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
                                    onClick = { ambientVolumePercent = null },
                                    label = { Text(globalChipLabel, fontSize = 12.sp) }
                                )
                                FilterChip(
                                    selected = ambientVolumePercent != null,
                                    onClick = {
                                        ambientVolumePercent = ambientVolumePercent
                                            ?: currentGlobalSnapshot.ambientVolumePercent
                                            ?: 50
                                    },
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
                                                val newVol = (currentVol - 10).coerceAtLeast(10)
                                                ambientVolumePercent = newVol
                                                previewAmbientPlayer.setVolumePercent(newVol)
                                                val previewSound = selectedAmbientSoundscape
                                                    ?: currentGlobalSnapshot.effectiveAmbientSoundscape()
                                                    ?: AmbientSoundscape.OFF
                                                if (previewSound != AmbientSoundscape.OFF &&
                                                    !previewAmbientPlayer.isPlaying() &&
                                                    !StillTimerService.isRunning
                                                ) {
                                                    playPreviewAmbient(previewSound, vol = newVol)
                                                }
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
                                                val newVol = (currentVol + 10).coerceAtMost(100)
                                                ambientVolumePercent = newVol
                                                previewAmbientPlayer.setVolumePercent(newVol)
                                                val previewSound = selectedAmbientSoundscape
                                                    ?: currentGlobalSnapshot.effectiveAmbientSoundscape()
                                                    ?: AmbientSoundscape.OFF
                                                if (previewSound != AmbientSoundscape.OFF &&
                                                    !previewAmbientPlayer.isPlaying() &&
                                                    !StillTimerService.isRunning
                                                ) {
                                                    playPreviewAmbient(previewSound, vol = newVol)
                                                }
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

                // 5. STILE VISIVO, TEMA COLORI E NON DISTURBARE
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
                                onClick = { timerStyle = null },
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
                                    onClick = { timerStyle = styleVal },
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
                                onClick = { colorPaletteId = null },
                                label = { Text(globalChipLabel, fontSize = 12.sp) }
                            )
                            StillColorPalette.entries.forEach { palette ->
                                FilterChip(
                                    selected = colorPaletteId == palette.id,
                                    onClick = { colorPaletteId = palette.id },
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
                                onClick = { autoDndEnabled = null },
                                label = { Text(globalChipLabel, fontSize = 12.sp) }
                            )
                            FilterChip(
                                selected = autoDndEnabled == true,
                                onClick = { autoDndEnabled = true },
                                label = { Text(stringResource(R.string.preset_option_on), fontSize = 12.sp) }
                            )
                            FilterChip(
                                selected = autoDndEnabled == false,
                                onClick = { autoDndEnabled = false },
                                label = { Text(stringResource(R.string.preset_option_off), fontSize = 12.sp) }
                            )
                        }
                    }
                }
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

                    previewGongJob?.cancel()
                    previewAmbientJob?.cancel()
                    previewAmbientPlayer.stopImmediate()

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
                    previewGongJob?.cancel()
                    previewAmbientJob?.cancel()
                    previewAmbientPlayer.stopImmediate()
                    onDismiss()
                }
            ) {
                Text(stringResource(R.string.button_cancel))
            }
        }
    )
}
