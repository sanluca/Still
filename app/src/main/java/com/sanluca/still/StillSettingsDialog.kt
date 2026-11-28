package com.sanluca.still

import android.widget.Toast
import com.sanluca.still.core.ALERT_MODE_BOTH
import com.sanluca.still.core.ALERT_MODE_SOUND
import com.sanluca.still.core.ALERT_MODE_VIBRATION
import com.sanluca.still.core.BellSound
import com.sanluca.still.core.triggerHapticGong
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
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

@Immutable
internal data class StillSettingsUiState(
    val prepTimeSeconds: Int,
    val intervalMins: Int,
    val gongFreqHz: Double,
    val intervalGongFreqHz: Double,
    val gongVolumePercent: Int,
    val alertMode: Int,
    val timerStyle: Int,
    val selectedPalette: StillColorPalette,
    val autoDndEnabled: Boolean,
    val dndGranted: Boolean,
    val isHealthConnected: Boolean,
    val isResyncing: Boolean,
    val ambientSoundscape: AmbientSoundscape = AmbientSoundscape.OFF,
    val ambientVolumePercent: Int = 50,
    val customAmbientUri: String? = null,
    val customAmbientName: String? = null,
    val presetCount: Int = 0,
    val reminderSummary: String = ""
)

@Immutable
internal data class StillSettingsActions(
    val onPrepTimeChange: (Int) -> Unit,
    val onIntervalMinsChange: (Int) -> Unit,
    val onGongFreqChange: (Double) -> Unit,
    val onIntervalGongFreqChange: (Double) -> Unit,
    val onGongVolumeChange: (Int) -> Unit,
    val onAlertModeChange: (Int) -> Unit,
    val onTimerStyleChange: (Int) -> Unit,
    val onPaletteSelected: (StillColorPalette) -> Unit,
    val onAutoDndChange: (Boolean) -> Unit,
    val onResyncHealth: () -> Unit,
    val onConnectHealth: () -> Unit,
    val onDismiss: () -> Unit,
    val onAmbientSoundscapeChange: (AmbientSoundscape) -> Unit = {},
    val onAmbientVolumeChange: (Int) -> Unit = {},
    val onPickCustomAudio: () -> Unit = {},
    val onOpenPresetManager: () -> Unit = {},
    val onOpenReminderSettings: () -> Unit = {}
)

@Composable
internal fun StillSettingsDialog(
    uiState: StillSettingsUiState,
    actions: StillSettingsActions
) {
    val context = LocalContext.current
    val audioPreview = rememberAudioPreviewController(context)
    var prepInputText by remember { mutableStateOf(uiState.prepTimeSeconds.toString()) }
    var intervalInputText by remember { mutableStateOf(uiState.intervalMins.toString()) }
    val isSoundEnabled = uiState.alertMode != ALERT_MODE_VIBRATION
    var customAudioAvailable by remember(uiState.customAmbientUri) {
        mutableStateOf(!uiState.customAmbientUri.isNullOrBlank())
    }
    var lastPreviewedCustomUri by remember { mutableStateOf(uiState.customAmbientUri) }
    val toastCustomAudioMissingMsg = stringResource(R.string.toast_custom_audio_missing)

    fun playPreviewAmbient(
        soundscape: AmbientSoundscape,
        vol: Int = uiState.ambientVolumePercent,
        uriOverride: String? = uiState.customAmbientUri
    ) {
        audioPreview.playAmbientPreview(
            soundscape = soundscape,
            volumePercent = vol,
            customUri = uriOverride,
            durationMs = 4200L,
            fadeInSeconds = 0.6,
            fadeOutSeconds = 0.9,
            onPlaybackError = {
                if (soundscape == AmbientSoundscape.CUSTOM) {
                    customAudioAvailable = false
                    Toast.makeText(context, toastCustomAudioMissingMsg, Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    LaunchedEffect(uiState.customAmbientUri) {
        val readable = !uiState.customAmbientUri.isNullOrBlank() &&
            withContext(Dispatchers.IO) { isAudioUriReadable(context, uiState.customAmbientUri) }
        customAudioAvailable = readable
        if (uiState.customAmbientUri != lastPreviewedCustomUri) {
            lastPreviewedCustomUri = uiState.customAmbientUri
            if (readable && uiState.ambientSoundscape == AmbientSoundscape.CUSTOM) {
                playPreviewAmbient(
                    AmbientSoundscape.CUSTOM,
                    uiState.ambientVolumePercent,
                    uiState.customAmbientUri
                )
            }
        }
    }

    fun playPreviewGong(freq: Double, vol: Int = uiState.gongVolumePercent) {
        actions.onGongFreqChange(freq)
        audioPreview.playBellPreview(freq, vol)
    }

    fun playPreviewIntervalGong(freq: Double, vol: Int = uiState.gongVolumePercent) {
        actions.onIntervalGongFreqChange(freq)
        audioPreview.playBellPreview(freq, vol)
    }

    AlertDialog(
        onDismissRequest = {
            audioPreview.stopAll()
            actions.onDismiss()
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp),
        title = @Composable {
            Text(
                text = stringResource(R.string.dialog_settings_title),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.primary
            )
        },
        text = @Composable {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 0. PRESET DI MEDITAZIONE
                SettingsPresetsCard(
                    presetCount = uiState.presetCount,
                    onOpenPresetManager = {
                        audioPreview.stopAll()
                        actions.onOpenPresetManager()
                    }
                )

                // 1. RITARDO PREPARAZIONE E CAMPANE INTERMEDIE
                SettingsTimingCard(
                    prepInputText = prepInputText,
                    onPrepInputChange = { input ->
                        prepInputText = input
                        actions.onPrepTimeChange(input.toIntOrNull() ?: 0)
                    },
                    intervalInputText = intervalInputText,
                    onIntervalInputChange = { input ->
                        intervalInputText = input
                        actions.onIntervalMinsChange(input.toIntOrNull() ?: 0)
                    },
                    intervalMins = uiState.intervalMins,
                    intervalGongFreqHz = uiState.intervalGongFreqHz,
                    isSoundEnabled = isSoundEnabled,
                    onIntervalGongFreqChange = actions.onIntervalGongFreqChange,
                    onPreviewIntervalGong = { freq -> playPreviewIntervalGong(freq) }
                )

                // 2. SUONO CAMPANA, SOTTOFONDO, STILE VISIVO E TEMA
                SettingsAudioAndAppearanceCard(
                    gongFreqHz = uiState.gongFreqHz,
                    onGongFreqChange = actions.onGongFreqChange,
                    onPreviewGong = { freq, vol -> playPreviewGong(freq, vol) },
                    gongVolumePercent = uiState.gongVolumePercent,
                    onGongVolumeChange = actions.onGongVolumeChange,
                    alertMode = uiState.alertMode,
                    onAlertModeChange = { modeVal ->
                        actions.onAlertModeChange(modeVal)
                        if (modeVal == ALERT_MODE_BOTH || modeVal == ALERT_MODE_VIBRATION) {
                            triggerHapticGong(context, repeatCount = 1)
                        }
                        if (modeVal == ALERT_MODE_VIBRATION) {
                            audioPreview.cancelBellPreview()
                        }
                    },
                    isSoundEnabled = isSoundEnabled,
                    ambientSoundscape = uiState.ambientSoundscape,
                    onAmbientSoundscapeSelect = { soundOption ->
                        if (soundOption == AmbientSoundscape.CUSTOM) {
                            when {
                                uiState.customAmbientUri.isNullOrBlank() -> actions.onPickCustomAudio()
                                !customAudioAvailable -> {
                                    Toast.makeText(
                                        context,
                                        toastCustomAudioMissingMsg,
                                        Toast.LENGTH_LONG
                                    ).show()
                                    actions.onPickCustomAudio()
                                }
                                else -> {
                                    actions.onAmbientSoundscapeChange(soundOption)
                                    playPreviewAmbient(soundOption, uiState.ambientVolumePercent)
                                }
                            }
                        } else {
                            actions.onAmbientSoundscapeChange(soundOption)
                            playPreviewAmbient(soundOption, uiState.ambientVolumePercent)
                        }
                    },
                    ambientVolumePercent = uiState.ambientVolumePercent,
                    onAmbientVolumeAdjust = { newVol ->
                        actions.onAmbientVolumeChange(newVol)
                        audioPreview.setAmbientVolume(
                            volumePercent = newVol,
                            restartSoundscapeIfIdle = uiState.ambientSoundscape,
                            customUri = uiState.customAmbientUri,
                            durationMs = 4200L,
                            fadeOutSeconds = 0.9,
                            onPlaybackError = {
                                if (uiState.ambientSoundscape == AmbientSoundscape.CUSTOM) {
                                    customAudioAvailable = false
                                    Toast.makeText(context, toastCustomAudioMissingMsg, Toast.LENGTH_LONG).show()
                                }
                            }
                        )
                    },
                    customAmbientUri = uiState.customAmbientUri,
                    customAmbientName = uiState.customAmbientName,
                    customAudioAvailable = customAudioAvailable,
                    onPickCustomAudio = actions.onPickCustomAudio,
                    timerStyle = uiState.timerStyle,
                    onTimerStyleChange = actions.onTimerStyleChange,
                    selectedPalette = uiState.selectedPalette,
                    onPaletteSelected = actions.onPaletteSelected
                )

                // 3. NON DISTURBARE
                SettingsDndCard(
                    autoDndEnabled = uiState.autoDndEnabled,
                    onAutoDndChange = actions.onAutoDndChange,
                    dndGranted = uiState.dndGranted,
                    onRequestDndAccess = { requestDndAccess(context) }
                )

                // 3b. PROMEMORIA MEDITAZIONE
                SettingsReminderCard(
                    reminderSummary = uiState.reminderSummary,
                    onOpenReminderSettings = {
                        audioPreview.stopAll()
                        actions.onOpenReminderSettings()
                    }
                )

                // 4. GOOGLE HEALTH CONNECT
                SettingsHealthConnectCard(
                    isHealthConnected = uiState.isHealthConnected,
                    isResyncing = uiState.isResyncing,
                    onResyncHealth = actions.onResyncHealth,
                    onConnectHealth = actions.onConnectHealth
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    audioPreview.stopAll()
                    actions.onDismiss()
                },
                shape = RoundedCornerShape(20.dp)
            ) {
                Text(stringResource(R.string.button_close))
            }
        }
    )
}

@Composable
private fun SettingsPresetsCard(
    presetCount: Int,
    onOpenPresetManager: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    text = stringResource(R.string.settings_presets_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = when (presetCount) {
                        0 -> stringResource(R.string.settings_presets_count_zero)
                        1 -> stringResource(R.string.settings_presets_count_one)
                        else -> stringResource(R.string.settings_presets_count_many, presetCount)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(
                onClick = onOpenPresetManager,
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = stringResource(R.string.settings_presets_manage),
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun SettingsTimingCard(
    prepInputText: String,
    onPrepInputChange: (String) -> Unit,
    intervalInputText: String,
    onIntervalInputChange: (String) -> Unit,
    intervalMins: Int,
    intervalGongFreqHz: Double,
    isSoundEnabled: Boolean,
    onIntervalGongFreqChange: (Double) -> Unit,
    onPreviewIntervalGong: (Double) -> Unit
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_prep_delay),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        minLines = 2
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = prepInputText,
                        onValueChange = { input ->
                            if (input.all { it.isDigit() } && input.length <= 3) {
                                onPrepInputChange(input)
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

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_interval_gong),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        minLines = 2
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = intervalInputText,
                        onValueChange = { input ->
                            if (input.all { it.isDigit() } && input.length <= 3) {
                                onIntervalInputChange(input)
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
            }

            if (intervalMins > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                StillBellDropdown(
                    selectedBell = BellSound.fromCode(intervalGongFreqHz, BellSound.PURE_BELL),
                    enabled = isSoundEnabled,
                    onSelectBell = { bell -> bell?.let { onIntervalGongFreqChange(it.code) } },
                    onPreviewBell = { bell -> onPreviewIntervalGong(bell.code) },
                    previewBellFallback = BellSound.PURE_BELL
                )
            }
        }
    }
}

@Composable
private fun SettingsAudioAndAppearanceCard(
    gongFreqHz: Double,
    onGongFreqChange: (Double) -> Unit,
    onPreviewGong: (Double, Int) -> Unit,
    gongVolumePercent: Int,
    onGongVolumeChange: (Int) -> Unit,
    alertMode: Int,
    onAlertModeChange: (Int) -> Unit,
    isSoundEnabled: Boolean,
    ambientSoundscape: AmbientSoundscape,
    onAmbientSoundscapeSelect: (AmbientSoundscape) -> Unit,
    ambientVolumePercent: Int,
    onAmbientVolumeAdjust: (Int) -> Unit,
    customAmbientUri: String?,
    customAmbientName: String?,
    customAudioAvailable: Boolean,
    onPickCustomAudio: () -> Unit,
    timerStyle: Int,
    onTimerStyleChange: (Int) -> Unit,
    selectedPalette: StillColorPalette,
    onPaletteSelected: (StillColorPalette) -> Unit
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
                selectedBell = BellSound.fromCode(gongFreqHz, BellSound.CLASSIC_BOWL),
                enabled = isSoundEnabled,
                onSelectBell = { bell -> bell?.let { onGongFreqChange(it.code) } },
                onPreviewBell = { bell -> onPreviewGong(bell.code, gongVolumePercent) },
                previewBellFallback = BellSound.CLASSIC_BOWL
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        if (gongVolumePercent > 20) {
                            val newVol = (gongVolumePercent - 10).coerceAtLeast(20)
                            onGongVolumeChange(newVol)
                            onPreviewGong(gongFreqHz, newVol)
                        }
                    },
                    enabled = isSoundEnabled && gongVolumePercent > 20,
                    modifier = Modifier
                        .widthIn(min = 48.dp)
                        .heightIn(min = 36.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("−", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }

                Text(
                    text = "$gongVolumePercent%",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSoundEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.padding(horizontal = 24.dp)
                )

                OutlinedButton(
                    onClick = {
                        if (gongVolumePercent < 100) {
                            val newVol = (gongVolumePercent + 10).coerceAtMost(100)
                            onGongVolumeChange(newVol)
                            onPreviewGong(gongFreqHz, newVol)
                        }
                    },
                    enabled = isSoundEnabled && gongVolumePercent < 100,
                    modifier = Modifier
                        .widthIn(min = 48.dp)
                        .heightIn(min = 36.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("+", fontSize = 18.sp, fontWeight = FontWeight.Bold)
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
                listOf(
                    ALERT_MODE_BOTH to stringResource(R.string.alert_mode_both),
                    ALERT_MODE_SOUND to stringResource(R.string.alert_mode_bell),
                    ALERT_MODE_VIBRATION to stringResource(R.string.alert_mode_vibrate)
                ).forEach { (modeVal, modeLabel) ->
                    FilterChip(
                        selected = alertMode == modeVal,
                        onClick = { onAlertModeChange(modeVal) },
                        label = { Text(modeLabel, fontSize = 12.sp) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Spacer(modifier = Modifier.height(10.dp))

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
                listOf(
                    AmbientSoundscape.OFF to R.string.ambient_sound_off,
                    AmbientSoundscape.LIGHT_RAIN to R.string.ambient_sound_light_rain,
                    AmbientSoundscape.GENTLE_RAIN to R.string.ambient_sound_gentle_rain,
                    AmbientSoundscape.BIRDS_RAIN to R.string.ambient_sound_birds_rain,
                    AmbientSoundscape.OCEAN to R.string.ambient_sound_ocean,
                    AmbientSoundscape.RIVER to R.string.ambient_sound_river,
                    AmbientSoundscape.FOREST to R.string.ambient_sound_forest,
                    AmbientSoundscape.DRONE to R.string.ambient_sound_drone,
                    AmbientSoundscape.CUSTOM to R.string.ambient_sound_custom
                ).forEach { (soundOption, labelRes) ->
                    FilterChip(
                        selected = ambientSoundscape == soundOption,
                        onClick = { onAmbientSoundscapeSelect(soundOption) },
                        label = { Text(stringResource(labelRes), fontSize = 12.sp) }
                    )
                }
            }

            if (ambientSoundscape == AmbientSoundscape.CUSTOM) {
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
                                if (customAmbientUri.isNullOrBlank()) {
                                    R.string.settings_custom_audio_choose
                                } else {
                                    R.string.settings_custom_audio_change
                                }
                            ),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (ambientSoundscape != AmbientSoundscape.OFF) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.settings_ambient_volume),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            if (ambientVolumePercent > 10) {
                                onAmbientVolumeAdjust((ambientVolumePercent - 10).coerceAtLeast(10))
                            }
                        },
                        enabled = ambientVolumePercent > 10,
                        modifier = Modifier
                            .widthIn(min = 48.dp)
                            .heightIn(min = 36.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("−", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }

                    Text(
                        text = "$ambientVolumePercent%",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )

                    OutlinedButton(
                        onClick = {
                            if (ambientVolumePercent < 100) {
                                onAmbientVolumeAdjust((ambientVolumePercent + 10).coerceAtMost(100))
                            }
                        },
                        enabled = ambientVolumePercent < 100,
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
                    selected = timerStyle == TIMER_STYLE_ESSENTIAL,
                    onClick = { onTimerStyleChange(TIMER_STYLE_ESSENTIAL) },
                    label = { Text(stringResource(R.string.settings_timer_style_essential)) }
                )
                FilterChip(
                    selected = timerStyle == 0,
                    onClick = { onTimerStyleChange(0) },
                    label = { Text(stringResource(R.string.settings_timer_style_circle)) }
                )
                FilterChip(
                    selected = timerStyle == 1,
                    onClick = { onTimerStyleChange(1) },
                    label = { Text(stringResource(R.string.settings_timer_style_hourglass)) }
                )
                FilterChip(
                    selected = timerStyle == 2,
                    onClick = { onTimerStyleChange(2) },
                    label = { Text(stringResource(R.string.settings_timer_style_candle)) }
                )
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
            SettingsPaletteDropdown(
                selectedPalette = selectedPalette,
                onPaletteSelected = onPaletteSelected
            )
        }
    }
}

@Composable
private fun SettingsPaletteDropdown(
    selectedPalette: StillColorPalette,
    onPaletteSelected: (StillColorPalette) -> Unit
) {
    val availablePalettes = StillColorPalette.entries
    var themeMenuExpanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { themeMenuExpanded = true },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .background(selectedPalette.swatchColor, CircleShape)
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    )
                    Text(
                        text = stringResource(selectedPalette.labelRes),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Text(
                    text = if (themeMenuExpanded) "▲" else "▼",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        DropdownMenu(
            expanded = themeMenuExpanded,
            onDismissRequest = { themeMenuExpanded = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            availablePalettes.forEach { palette ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(palette.labelRes),
                            color = if (selectedPalette == palette) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            fontWeight = if (selectedPalette == palette) FontWeight.SemiBold else FontWeight.Normal
                        )
                    },
                    leadingIcon = {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .background(palette.swatchColor, CircleShape)
                                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        )
                    },
                    trailingIcon = if (selectedPalette == palette) {
                        {
                            Text(
                                text = "✓",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else null,
                    onClick = {
                        onPaletteSelected(palette)
                        themeMenuExpanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun SettingsDndCard(
    autoDndEnabled: Boolean,
    onAutoDndChange: (Boolean) -> Unit,
    dndGranted: Boolean,
    onRequestDndAccess: () -> Unit
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = stringResource(R.string.settings_dnd_mode),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (autoDndEnabled) stringResource(R.string.settings_dnd_active) else stringResource(R.string.settings_dnd_inactive),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = autoDndEnabled,
                    onCheckedChange = onAutoDndChange
                )
            }

            if (!dndGranted && autoDndEnabled) {
                Spacer(modifier = Modifier.height(4.dp))
                TextButton(
                    onClick = onRequestDndAccess,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.settings_dnd_authorize), fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun SettingsReminderCard(
    reminderSummary: String,
    onOpenReminderSettings: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    text = stringResource(R.string.settings_reminder_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = reminderSummary.ifBlank { stringResource(R.string.reminder_status_off) },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            OutlinedButton(
                onClick = onOpenReminderSettings,
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = stringResource(R.string.settings_reminder_configure),
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun SettingsHealthConnectCard(
    isHealthConnected: Boolean,
    isResyncing: Boolean,
    onResyncHealth: () -> Unit,
    onConnectHealth: () -> Unit
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.settings_health_status),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = if (isHealthConnected) stringResource(R.string.settings_health_connected) else stringResource(R.string.settings_health_disconnected),
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = if (isHealthConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (isHealthConnected) {
                OutlinedButton(
                    onClick = onResyncHealth,
                    enabled = !isResyncing,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isResyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(stringResource(R.string.settings_health_resync))
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onConnectHealth,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.settings_health_reconnect))
                }
            } else {
                Button(
                    onClick = onConnectHealth,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.settings_health_connect))
                }
            }
        }
    }
}
