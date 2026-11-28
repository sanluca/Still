package com.sanluca.still

import android.content.Context
import com.sanluca.still.core.ALERT_MODE_SOUND
import com.sanluca.still.core.ALERT_MODE_VIBRATION
import com.sanluca.still.core.BellSound
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val ALL_AMBIENT_CHOICES = listOf(
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
    return BellSound.fromCode(freqHz).labelRes
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
internal fun StillPresetPickerDialog(
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
internal fun StillPresetManagerDialog(
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
