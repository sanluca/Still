package com.sanluca.still

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanluca.still.ui.theme.StillColorPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale
import kotlin.math.roundToInt

private const val PRIVACY_POLICY_URL = "https://github.com/sanluca/still/blob/master/PRIVACY.md"

// Campane "soft" (frequenza-identificativo, nome visualizzato)
internal val SOFT_BELL_CHOICES = listOf(
    CHIME_FREQ_HZ to R.string.bell_name_chime,
    WATER_BOWL_FREQ_HZ to R.string.bell_name_water_bowl,
    SOFT_BOWL_FREQ_HZ to R.string.bell_name_soft_bowl,
    FEATHER_FREQ_HZ to R.string.bell_name_feather,
    SOFT_GONG_FREQ_HZ to R.string.bell_name_soft_gong
)

@Composable
fun StillSettingsDialog(
    prepTimeSeconds: Int,
    onPrepTimeChange: (Int) -> Unit,
    intervalMins: Int,
    onIntervalMinsChange: (Int) -> Unit,
    gongFreqHz: Double,
    onGongFreqChange: (Double) -> Unit,
    intervalGongFreqHz: Double,
    onIntervalGongFreqChange: (Double) -> Unit,
    gongVolumePercent: Int,
    onGongVolumeChange: (Int) -> Unit,
    alertMode: Int,
    onAlertModeChange: (Int) -> Unit,
    timerStyle: Int,
    onTimerStyleChange: (Int) -> Unit,
    selectedPalette: StillColorPalette,
    onPaletteSelected: (StillColorPalette) -> Unit,
    autoDndEnabled: Boolean,
    onAutoDndChange: (Boolean) -> Unit,
    dndGranted: Boolean,
    isHealthConnected: Boolean,
    isResyncing: Boolean,
    onResyncHealth: () -> Unit,
    onConnectHealth: () -> Unit,
    onDismiss: () -> Unit,
    ambientSoundscape: AmbientSoundscape = AmbientSoundscape.OFF,
    onAmbientSoundscapeChange: (AmbientSoundscape) -> Unit = {},
    ambientVolumePercent: Int = 50,
    onAmbientVolumeChange: (Int) -> Unit = {},
    customAmbientUri: String? = null,
    customAmbientName: String? = null,
    onPickCustomAudio: () -> Unit = {},
    presetCount: Int = 0,
    onOpenPresetManager: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var prepInputText by remember { mutableStateOf(prepTimeSeconds.toString()) }
    var intervalInputText by remember { mutableStateOf(intervalMins.toString()) }
    val isSoundEnabled = alertMode != ALERT_MODE_VIBRATION
    var previewGongJob by remember { mutableStateOf<Job?>(null) }
    val previewAmbientPlayer = remember(coroutineScope) { AmbientSoundPlayer(coroutineScope) }
    var previewAmbientJob by remember { mutableStateOf<Job?>(null) }
    var customAudioAvailable by remember(customAmbientUri) {
        mutableStateOf(!customAmbientUri.isNullOrBlank())
    }
    var lastPreviewedCustomUri by remember { mutableStateOf(customAmbientUri) }
    val toastCustomAudioMissingMsg = stringResource(R.string.toast_custom_audio_missing)

    DisposableEffect(Unit) {
        onDispose {
            previewGongJob?.cancel()
            previewAmbientJob?.cancel()
            previewAmbientPlayer.stopImmediate()
        }
    }

    fun playPreviewAmbient(
        soundscape: AmbientSoundscape,
        vol: Int = ambientVolumePercent,
        uriOverride: String? = customAmbientUri
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
            delay(4200L)
            previewAmbientPlayer.stop(fadeOutSeconds = 0.9)
        }
    }

    LaunchedEffect(customAmbientUri) {
        val readable = !customAmbientUri.isNullOrBlank() &&
            withContext(Dispatchers.IO) { isAudioUriReadable(context, customAmbientUri) }
        customAudioAvailable = readable
        if (customAmbientUri != lastPreviewedCustomUri) {
            lastPreviewedCustomUri = customAmbientUri
            if (readable && ambientSoundscape == AmbientSoundscape.CUSTOM) {
                playPreviewAmbient(AmbientSoundscape.CUSTOM, ambientVolumePercent, customAmbientUri)
            }
        }
    }

    fun playPreviewGong(freq: Double, vol: Int = gongVolumePercent) {
        onGongFreqChange(freq)
        previewGongJob?.cancel()
        previewGongJob = coroutineScope.launch(Dispatchers.Default) {
            playGongSound(context, freq, repeatCount = 1, volumePercent = vol)
        }
    }

    fun playPreviewIntervalGong(freq: Double, vol: Int = gongVolumePercent) {
        onIntervalGongFreqChange(freq)
        previewGongJob?.cancel()
        previewGongJob = coroutineScope.launch(Dispatchers.Default) {
            playGongSound(context, freq, repeatCount = 1, volumePercent = vol)
        }
    }

    AlertDialog(
        onDismissRequest = {
            previewGongJob?.cancel()
            previewAmbientJob?.cancel()
            previewAmbientPlayer.stopImmediate()
            onDismiss()
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
                            onClick = {
                                previewGongJob?.cancel()
                                previewAmbientJob?.cancel()
                                previewAmbientPlayer.stopImmediate()
                                onOpenPresetManager()
                            },
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

                // 1. RITARDO PREPARAZIONE E CAMPANE INTERMEDIE IN CARD AUTUNNALE
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
                                            prepInputText = input
                                            onPrepTimeChange(input.toIntOrNull() ?: 0)
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
                                            intervalInputText = input
                                            onIntervalMinsChange(input.toIntOrNull() ?: 0)
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
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                FilterChip(
                                    selected = intervalGongFreqHz == 216.0,
                                    onClick = { playPreviewIntervalGong(216.0) },
                                    enabled = isSoundEnabled,
                                    label = { Text(stringResource(R.string.bell_name_gong), fontSize = 11.sp) }
                                )
                                FilterChip(
                                    selected = intervalGongFreqHz == 432.0,
                                    onClick = { playPreviewIntervalGong(432.0) },
                                    enabled = isSoundEnabled,
                                    label = { Text(stringResource(R.string.bell_name_crystal), fontSize = 11.sp) }
                                )
                                FilterChip(
                                    selected = intervalGongFreqHz == 528.0,
                                    onClick = { playPreviewIntervalGong(528.0) },
                                    enabled = isSoundEnabled,
                                    label = { Text(stringResource(R.string.bell_name_keisu), fontSize = 11.sp) }
                                )
                                FilterChip(
                                    selected = intervalGongFreqHz == TINGSHA_FREQ_HZ,
                                    onClick = { playPreviewIntervalGong(TINGSHA_FREQ_HZ) },
                                    enabled = isSoundEnabled,
                                    label = { Text(stringResource(R.string.bell_name_tingsha), fontSize = 11.sp) }
                                )
                                SOFT_BELL_CHOICES.forEach { (freq, labelRes) ->
                                    FilterChip(
                                        selected = intervalGongFreqHz == freq,
                                        onClick = { playPreviewIntervalGong(freq) },
                                        enabled = isSoundEnabled,
                                        label = { Text(stringResource(labelRes), fontSize = 11.sp) }
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. SUONO CAMPANA E STILE VISIVO IN CARD AUTUNNALE
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
                                selected = gongFreqHz == 216.0,
                                onClick = { playPreviewGong(216.0) },
                                enabled = isSoundEnabled,
                                label = { Text(stringResource(R.string.bell_name_gong), fontSize = 12.sp) }
                            )
                            FilterChip(
                                selected = gongFreqHz == 432.0,
                                onClick = { playPreviewGong(432.0) },
                                enabled = isSoundEnabled,
                                label = { Text(stringResource(R.string.bell_name_crystal), fontSize = 12.sp) }
                            )
                            FilterChip(
                                selected = gongFreqHz == 528.0,
                                onClick = { playPreviewGong(528.0) },
                                enabled = isSoundEnabled,
                                label = { Text(stringResource(R.string.bell_name_keisu), fontSize = 12.sp) }
                            )
                            FilterChip(
                                selected = gongFreqHz == TINGSHA_FREQ_HZ,
                                onClick = { playPreviewGong(TINGSHA_FREQ_HZ) },
                                enabled = isSoundEnabled,
                                label = { Text(stringResource(R.string.bell_name_tingsha), fontSize = 12.sp) }
                            )
                            SOFT_BELL_CHOICES.forEach { (freq, labelRes) ->
                                FilterChip(
                                    selected = gongFreqHz == freq,
                                    onClick = { playPreviewGong(freq) },
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
                                        playPreviewGong(gongFreqHz, newVol)
                                    }
                                },
                                enabled = isSoundEnabled && gongVolumePercent > 20,
                                modifier = Modifier.size(width = 48.dp, height = 36.dp),
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
                                        playPreviewGong(gongFreqHz, newVol)
                                    }
                                },
                                enabled = isSoundEnabled && gongVolumePercent < 100,
                                modifier = Modifier.size(width = 48.dp, height = 36.dp),
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
                                    onClick = {
                                        onAlertModeChange(modeVal)
                                        if (modeVal == ALERT_MODE_BOTH || modeVal == ALERT_MODE_VIBRATION) {
                                            triggerHapticGong(context, repeatCount = 1)
                                        }
                                        if (modeVal == ALERT_MODE_VIBRATION) {
                                            previewGongJob?.cancel()
                                        }
                                    },
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
                                    onClick = {
                                        if (soundOption == AmbientSoundscape.CUSTOM) {
                                            when {
                                                customAmbientUri.isNullOrBlank() -> onPickCustomAudio()
                                                !customAudioAvailable -> {
                                                    Toast.makeText(
                                                        context,
                                                        toastCustomAudioMissingMsg,
                                                        Toast.LENGTH_LONG
                                                    ).show()
                                                    onPickCustomAudio()
                                                }
                                                else -> {
                                                    onAmbientSoundscapeChange(soundOption)
                                                    playPreviewAmbient(soundOption, ambientVolumePercent)
                                                }
                                            }
                                        } else {
                                            onAmbientSoundscapeChange(soundOption)
                                            playPreviewAmbient(soundOption, ambientVolumePercent)
                                        }
                                    },
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
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Text(
                                        text = stringResource(
                                            if (customAmbientUri.isNullOrBlank()) {
                                                R.string.settings_custom_audio_choose
                                            } else {
                                                R.string.settings_custom_audio_change
                                            }
                                        ),
                                        fontSize = 12.sp
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
                                            val newVol = (ambientVolumePercent - 10).coerceAtLeast(10)
                                            onAmbientVolumeChange(newVol)
                                            previewAmbientPlayer.setVolumePercent(newVol)
                                            if (!previewAmbientPlayer.isPlaying() && !StillTimerService.isRunning) {
                                                playPreviewAmbient(ambientSoundscape, newVol)
                                            }
                                        }
                                    },
                                    enabled = ambientVolumePercent > 10,
                                    modifier = Modifier.size(width = 48.dp, height = 36.dp),
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
                                            val newVol = (ambientVolumePercent + 10).coerceAtMost(100)
                                            onAmbientVolumeChange(newVol)
                                            previewAmbientPlayer.setVolumePercent(newVol)
                                            if (!previewAmbientPlayer.isPlaying() && !StillTimerService.isRunning) {
                                                playPreviewAmbient(ambientSoundscape, newVol)
                                            }
                                        }
                                    },
                                    enabled = ambientVolumePercent < 100,
                                    modifier = Modifier.size(width = 48.dp, height = 36.dp),
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
                }

                // 3. NON DISTURBARE IN CARD AUTUNNALE
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
                                onClick = { requestDndAccess(context) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.settings_dnd_authorize), fontSize = 11.sp)
                            }
                        }
                    }
                }

                // 4. GOOGLE HEALTH CONNECT IN CARD AUTUNNALE
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
        },
        confirmButton = {
            Button(
                onClick = {
                    previewGongJob?.cancel()
                    previewAmbientJob?.cancel()
                    previewAmbientPlayer.stopImmediate()
                    onDismiss()
                },
                shape = RoundedCornerShape(20.dp)
            ) {
                Text(stringResource(R.string.button_close))
            }
        }
    )
}

@Composable
fun HealthPrivacyRationaleDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                text = stringResource(R.string.privacy_dialog_title),
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
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.privacy_dialog_health_usage),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        Text(
                            text = stringResource(R.string.privacy_dialog_health_guarantee),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                OutlinedButton(
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.privacy_dialog_button_policy))
                }
                Text(
                    text = stringResource(R.string.privacy_dialog_audio_credits),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = stringResource(R.string.settings_app_version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(20.dp)
            ) {
                Text(stringResource(R.string.privacy_dialog_button_understand))
            }
        }
    )
}

@Composable
fun StillStatsDialog(
    sessionHistoryList: List<SessionLogEntry>,
    onSelectDate: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    onExportHistory: (() -> Unit)? = null,
    onImportHistory: (() -> Unit)? = null
) {
    val totalMinutes = sessionHistoryList.sumOf { it.durationMinutes }
    val totalSessions = sessionHistoryList.size
    val streak = calculateStreakDays(sessionHistoryList)
    val bestStreak = calculateBestStreakDays(sessionHistoryList)
    val overallAvgBpm = remember(sessionHistoryList) {
        val sessionHrValues = sessionHistoryList.mapNotNull { entry ->
            entry.avgBpm ?: when {
                entry.startBpm != null && entry.endBpm != null -> ((entry.startBpm + entry.endBpm) / 2.0).roundToInt()
                entry.startBpm != null -> entry.startBpm
                entry.endBpm != null -> entry.endBpm
                else -> null
            }
        }
        if (sessionHrValues.isNotEmpty()) sessionHrValues.average().roundToInt() else null
    }
    val zone = remember { ZoneId.systemDefault() }
    val locale = Locale.getDefault()

    val sessionsByDate = remember(sessionHistoryList, zone) {
        sessionHistoryList.groupBy {
            Instant.ofEpochSecond(it.startEpochSec).atZone(zone).toLocalDate()
        }
    }

    var displayedYearMonth by remember { mutableStateOf(YearMonth.now(zone)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp),
        title = @Composable {
            Text(
                text = stringResource(R.string.dialog_stats_title),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.primary
            )
        },
        text = @Composable {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(stringResource(R.string.stats_minutes), style = MaterialTheme.typography.labelMedium)
                            Text(stringResource(R.string.stats_min_format, totalMinutes), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        }
                    }

                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(stringResource(R.string.stats_sessions), style = MaterialTheme.typography.labelMedium)
                            Text("$totalSessions", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        }
                    }

                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 6.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(stringResource(R.string.stats_streak), style = MaterialTheme.typography.labelMedium)
                            Text(stringResource(R.string.stats_streak_format, streak), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                            Text(
                                text = stringResource(R.string.stats_best_streak_format, bestStreak),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
                                maxLines = 1
                            )
                        }
                    }
                }

                if (overallAvgBpm != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.stats_overall_hr_avg, overallAvgBpm),
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // --- CALENDARIO MENSILE ---
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 10.dp)
                    ) {
                        // Barra di navigazione < Mese Anno >
                        val monthTitle = remember(displayedYearMonth, locale) {
                            val mName = displayedYearMonth.month
                                .getDisplayName(JavaTextStyle.FULL_STANDALONE, locale)
                                .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
                            "$mName ${displayedYearMonth.year}"
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { displayedYearMonth = displayedYearMonth.minusMonths(1) },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Text(
                                    text = "‹",
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Text(
                                text = monthTitle,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            IconButton(
                                onClick = { displayedYearMonth = displayedYearMonth.plusMonths(1) },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Text(
                                    text = "›",
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Intestazione Giorni della Settimana (Lun - Dom)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            DayOfWeek.entries.forEach { dow ->
                                val label = dow
                                    .getDisplayName(JavaTextStyle.SHORT_STANDALONE, locale)
                                    .take(3)
                                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Griglia 7 colonne dei giorni del mese
                        val firstOfMonth = displayedYearMonth.atDay(1)
                        val leadingEmptyCells = firstOfMonth.dayOfWeek.value - 1 // Lunedì = 0 .. Domenica = 6
                        val daysInMonth = displayedYearMonth.lengthOfMonth()
                        val totalCells = ((leadingEmptyCells + daysInMonth + 6) / 7) * 7
                        val today = LocalDate.now(zone)

                        for (week in 0 until (totalCells / 7)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                for (col in 0..6) {
                                    val cellIndex = week * 7 + col
                                    val dayNumber = cellIndex - leadingEmptyCells + 1
                                    if (dayNumber in 1..daysInMonth) {
                                        val cellDate = displayedYearMonth.atDay(dayNumber)
                                        val daySessions = sessionsByDate[cellDate].orEmpty()
                                        val hasMeditation = daySessions.isNotEmpty()
                                        val dayTotalMins = daySessions.sumOf { it.durationMinutes }
                                        val isToday = cellDate == today

                                        val circleBgColor = when {
                                            !hasMeditation -> Color.Transparent
                                            dayTotalMins >= 30 -> MaterialTheme.colorScheme.primary
                                            dayTotalMins >= 15 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.72f)
                                            else -> MaterialTheme.colorScheme.primaryContainer
                                        }
                                        val dayTextColor = when {
                                            !hasMeditation && isToday -> MaterialTheme.colorScheme.primary
                                            !hasMeditation -> MaterialTheme.colorScheme.onSurface
                                            dayTotalMins >= 15 -> MaterialTheme.colorScheme.onPrimary
                                            else -> MaterialTheme.colorScheme.onPrimaryContainer
                                        }

                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(36.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .background(circleBgColor, CircleShape)
                                                    .then(
                                                        if (hasMeditation) {
                                                            Modifier.border(
                                                                width = 1.dp,
                                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                                                shape = CircleShape
                                                            )
                                                        } else if (isToday) {
                                                            Modifier.border(
                                                                width = 1.dp,
                                                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                                                                shape = CircleShape
                                                            )
                                                        } else {
                                                            Modifier
                                                        }
                                                    )
                                                    .clickable(enabled = hasMeditation) {
                                                        onSelectDate(cellDate)
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "$dayNumber",
                                                    fontSize = 13.sp,
                                                    fontWeight = if (hasMeditation || isToday) FontWeight.Bold else FontWeight.Normal,
                                                    color = dayTextColor,
                                                    style = TextStyle(fontFeatureSettings = "tnum")
                                                )
                                            }
                                        }
                                    } else {
                                        Spacer(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(36.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (sessionHistoryList.isEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.stats_history_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (onExportHistory != null || onImportHistory != null) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (onExportHistory != null) {
                            OutlinedButton(
                                onClick = onExportHistory,
                                enabled = sessionHistoryList.isNotEmpty(),
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.button_export_history),
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1
                                )
                            }
                        }
                        if (onImportHistory != null) {
                            OutlinedButton(
                                onClick = onImportHistory,
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.button_import_history),
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(20.dp)
            ) {
                Text(stringResource(R.string.button_close))
            }
        }
    )
}

@Composable
fun StillDayDetailDialog(
    selectedDate: LocalDate,
    sessionHistoryList: List<SessionLogEntry>,
    onRequestDeleteSession: (SessionLogEntry) -> Unit,
    onDismiss: () -> Unit
) {
    val zone = remember { ZoneId.systemDefault() }
    val locale = Locale.getDefault()
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm", locale) }
    val dateTitleFormatter = remember(locale) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale)
    }
    val daySessions = remember(sessionHistoryList, selectedDate, zone) {
        sessionHistoryList
            .filter { Instant.ofEpochSecond(it.startEpochSec).atZone(zone).toLocalDate() == selectedDate }
            .sortedBy { it.startEpochSec }
    }

    if (daySessions.isEmpty()) {
        LaunchedEffect(Unit) {
            onDismiss()
        }
    } else {
        val dayTotalMinutes = daySessions.sumOf { it.durationMinutes }
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(24.dp),
            title = {
                Column {
                    Text(
                        text = selectedDate.format(dateTitleFormatter)
                            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() },
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (daySessions.size == 1) {
                            stringResource(R.string.stats_day_single_summary, dayTotalMinutes)
                        } else {
                            stringResource(R.string.stats_day_total_summary, daySessions.size, dayTotalMinutes)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(daySessions, key = { it.rawString }) { session ->
                        val startStr = Instant.ofEpochSecond(session.startEpochSec).atZone(zone).format(timeFormatter)
                        val endStr = Instant.ofEpochSecond(session.endEpochSec).atZone(zone).format(timeFormatter)

                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.stats_min_format, session.durationMinutes),
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = stringResource(R.string.stats_session_time_range, startStr, endStr),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    val hrLabel = when {
                                        session.startBpm != null && session.endBpm != null && session.startBpm != session.endBpm -> {
                                            stringResource(R.string.stats_session_hr_range, session.startBpm, session.endBpm)
                                        }
                                        session.avgBpm != null -> {
                                            stringResource(R.string.stats_session_hr_avg, session.avgBpm)
                                        }
                                        session.startBpm != null -> {
                                            stringResource(R.string.stats_session_hr_avg, session.startBpm)
                                        }
                                        session.endBpm != null -> {
                                            stringResource(R.string.stats_session_hr_avg, session.endBpm)
                                        }
                                        else -> null
                                    }
                                    if (hrLabel != null) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = hrLabel,
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                TextButton(
                                    onClick = { onRequestDeleteSession(session) },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.button_delete),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(stringResource(R.string.button_close))
                }
            }
        )
    }
}

@Composable
fun StillDeleteSessionDialog(
    session: SessionLogEntry,
    onConfirmDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                text = stringResource(R.string.dialog_delete_title),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.primary
            )
        },
        text = {
            Text(stringResource(R.string.dialog_delete_message, session.durationMinutes, session.dateFormatted))
        },
        confirmButton = {
            Button(
                onClick = onConfirmDelete,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text(stringResource(R.string.button_delete))
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(20.dp)
            ) {
                Text(stringResource(R.string.button_cancel))
            }
        }
    )
}
