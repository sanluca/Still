package com.sanluca.still

import android.content.Context
import com.sanluca.still.core.ALERT_MODE_BOTH
import com.sanluca.still.core.ALERT_MODE_SOUND
import com.sanluca.still.core.ALERT_MODE_VIBRATION
import com.sanluca.still.core.TINGSHA_FREQ_HZ
import com.sanluca.still.ui.theme.StillColorPalette
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private const val KEY_MEDITATION_PRESETS = "meditation_presets_list"
private const val KEY_ACTIVE_PRESET_ID = "active_preset_id"

internal data class MeditationPreset(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val durationSeconds: Int? = null,
    val prepSeconds: Int? = null,
    val gongFreqHz: Double? = null,
    val gongVolumePercent: Int? = null,
    val alertMode: Int? = null,
    val intervalMins: Int? = null,
    val intervalGongFreqHz: Double? = null,
    val ambientSoundId: String? = null,
    val ambientVolumePercent: Int? = null,
    val customAmbientUri: String? = null,
    val customAmbientName: String? = null,
    val timerStyle: Int? = null,
    val colorPaletteId: String? = null,
    val autoDndEnabled: Boolean? = null
) {
    fun effectiveAmbientSoundscape(): AmbientSoundscape? =
        ambientSoundId?.let { AmbientSoundscape.fromId(it) }

    fun effectiveColorPalette(): StillColorPalette? =
        colorPaletteId?.let { StillColorPalette.fromId(it) }
}

internal fun getSavedPresets(context: Context): List<MeditationPreset> {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val raw = prefs.getString(KEY_MEDITATION_PRESETS, null) ?: return emptyList()
    return parsePresetsJson(raw)
}

internal fun normalizePreset(preset: MeditationPreset): MeditationPreset? {
    val cleanedName = preset.name.trim()
    if (cleanedName.isEmpty()) return null

    return preset.copy(
        id = preset.id.ifBlank { UUID.randomUUID().toString() },
        name = cleanedName,
        durationSeconds = preset.durationSeconds?.let { if (it == -1 || it > 0) it else null },
        prepSeconds = preset.prepSeconds?.coerceIn(0, 999),
        gongFreqHz = preset.gongFreqHz?.let { normalizeBellFrequency(it, 432.0) },
        gongVolumePercent = preset.gongVolumePercent?.coerceIn(20, 100),
        alertMode = preset.alertMode?.takeIf {
            it == ALERT_MODE_BOTH || it == ALERT_MODE_SOUND || it == ALERT_MODE_VIBRATION
        },
        intervalMins = preset.intervalMins?.coerceIn(0, 999),
        intervalGongFreqHz = preset.intervalGongFreqHz?.let { normalizeBellFrequency(it, TINGSHA_FREQ_HZ) },
        ambientSoundId = preset.ambientSoundId?.let { AmbientSoundscape.fromId(it).id },
        ambientVolumePercent = preset.ambientVolumePercent?.coerceIn(10, 100),
        customAmbientUri = preset.customAmbientUri?.takeIf { it.isNotBlank() },
        customAmbientName = preset.customAmbientName?.takeIf { it.isNotBlank() },
        timerStyle = preset.timerStyle?.takeIf { it in 0..3 },
        colorPaletteId = preset.colorPaletteId?.let { StillColorPalette.fromId(it).id }
    )
}

internal fun savePreset(context: Context, preset: MeditationPreset): List<MeditationPreset> {
    val normalized = normalizePreset(preset) ?: return getSavedPresets(context)

    val current = getSavedPresets(context).toMutableList()
    val existingIndex = current.indexOfFirst { it.id == normalized.id }
    if (existingIndex >= 0) {
        current[existingIndex] = normalized
    } else {
        current.add(normalized)
    }
    persistPresets(context, current)
    return current
}

internal fun deletePreset(context: Context, presetId: String): List<MeditationPreset> {
    val updated = getSavedPresets(context).filterNot { it.id == presetId }
    persistPresets(context, updated)
    if (getActivePresetId(context) == presetId) {
        saveActivePresetId(context, null)
    }
    return updated
}

internal fun getActivePresetId(context: Context): String? {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getString(KEY_ACTIVE_PRESET_ID, null)?.takeIf { it.isNotBlank() }
}

internal fun saveActivePresetId(context: Context, presetId: String?) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val editor = prefs.edit()
    if (presetId.isNullOrBlank()) {
        editor.remove(KEY_ACTIVE_PRESET_ID)
    } else {
        editor.putString(KEY_ACTIVE_PRESET_ID, presetId)
    }
    editor.apply()
}

internal fun getActivePreset(context: Context): MeditationPreset? {
    val activeId = getActivePresetId(context) ?: return null
    return getSavedPresets(context).firstOrNull { it.id == activeId }
}

private fun persistPresets(context: Context, presets: List<MeditationPreset>) {
    val array = JSONArray()
    presets.forEach { preset ->
        array.put(presetToJson(preset))
    }
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putString(KEY_MEDITATION_PRESETS, array.toString()).apply()
}

private fun presetToJson(preset: MeditationPreset): JSONObject {
    return JSONObject().apply {
        put("id", preset.id)
        put("name", preset.name)
        preset.durationSeconds?.let { put("durationSeconds", it) }
        preset.prepSeconds?.let { put("prepSeconds", it) }
        preset.gongFreqHz?.let { put("gongFreqHz", it) }
        preset.gongVolumePercent?.let { put("gongVolumePercent", it) }
        preset.alertMode?.let { put("alertMode", it) }
        preset.intervalMins?.let { put("intervalMins", it) }
        preset.intervalGongFreqHz?.let { put("intervalGongFreqHz", it) }
        preset.ambientSoundId?.let { put("ambientSoundId", it) }
        preset.ambientVolumePercent?.let { put("ambientVolumePercent", it) }
        preset.customAmbientUri?.let { put("customAmbientUri", it) }
        preset.customAmbientName?.let { put("customAmbientName", it) }
        preset.timerStyle?.let { put("timerStyle", it) }
        preset.colorPaletteId?.let { put("colorPaletteId", it) }
        preset.autoDndEnabled?.let { put("autoDndEnabled", it) }
    }
}

private fun parsePresetsJson(raw: String): List<MeditationPreset> {
    return try {
        val array = JSONArray(raw)
        val result = mutableListOf<MeditationPreset>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val id = obj.optString("id", "").trim()
            val name = obj.optString("name", "").trim()
            if (id.isEmpty() || name.isEmpty()) continue

            val durationSeconds = obj.optNullableInt("durationSeconds")
                ?.takeIf { it == -1 || it > 0 }
            val prepSeconds = obj.optNullableInt("prepSeconds")
                ?.coerceIn(0, 999)
            val gongFreqHz = obj.optNullableDouble("gongFreqHz")
                ?.let { normalizeBellFrequency(it, 432.0) }
            val gongVolumePercent = obj.optNullableInt("gongVolumePercent")
                ?.coerceIn(20, 100)
            val alertMode = obj.optNullableInt("alertMode")
                ?.takeIf { it == ALERT_MODE_BOTH || it == ALERT_MODE_SOUND || it == ALERT_MODE_VIBRATION }
            val intervalMins = obj.optNullableInt("intervalMins")
                ?.coerceIn(0, 999)
            val intervalGongFreqHz = obj.optNullableDouble("intervalGongFreqHz")
                ?.let { normalizeBellFrequency(it, TINGSHA_FREQ_HZ) }
            val ambientSoundId = obj.optNullableString("ambientSoundId")
                ?.let { AmbientSoundscape.fromId(it).id }
            val ambientVolumePercent = obj.optNullableInt("ambientVolumePercent")
                ?.coerceIn(10, 100)
            val customAmbientUri = obj.optNullableString("customAmbientUri")
            val customAmbientName = obj.optNullableString("customAmbientName")
            val timerStyle = obj.optNullableInt("timerStyle")
                ?.takeIf { it in 0..3 }
            val colorPaletteId = obj.optNullableString("colorPaletteId")
                ?.let { StillColorPalette.fromId(it).id }
            val autoDndEnabled = obj.optNullableBoolean("autoDndEnabled")

            result.add(
                MeditationPreset(
                    id = id,
                    name = name,
                    durationSeconds = durationSeconds,
                    prepSeconds = prepSeconds,
                    gongFreqHz = gongFreqHz,
                    gongVolumePercent = gongVolumePercent,
                    alertMode = alertMode,
                    intervalMins = intervalMins,
                    intervalGongFreqHz = intervalGongFreqHz,
                    ambientSoundId = ambientSoundId,
                    ambientVolumePercent = ambientVolumePercent,
                    customAmbientUri = customAmbientUri,
                    customAmbientName = customAmbientName,
                    timerStyle = timerStyle,
                    colorPaletteId = colorPaletteId,
                    autoDndEnabled = autoDndEnabled
                )
            )
        }
        result
    } catch (_: Exception) {
        emptyList()
    }
}

private fun JSONObject.optNullableInt(key: String): Int? {
    if (!has(key) || isNull(key)) return null
    return try {
        getInt(key)
    } catch (_: Exception) {
        null
    }
}

private fun JSONObject.optNullableDouble(key: String): Double? {
    if (!has(key) || isNull(key)) return null
    return try {
        val value = getDouble(key)
        if (value.isFinite()) value else null
    } catch (_: Exception) {
        null
    }
}

private fun JSONObject.optNullableBoolean(key: String): Boolean? {
    if (!has(key) || isNull(key)) return null
    return try {
        getBoolean(key)
    } catch (_: Exception) {
        null
    }
}

private fun JSONObject.optNullableString(key: String): String? {
    if (!has(key) || isNull(key)) return null
    return optString(key, "").takeIf { it.isNotBlank() }
}
