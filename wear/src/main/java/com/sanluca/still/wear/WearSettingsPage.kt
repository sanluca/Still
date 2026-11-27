@file:Suppress("WearMaterialTheme")

package com.sanluca.still.wear

import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sanluca.still.wear.ui.theme.StillColorPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

private const val SETTINGS_PAGE_COUNT = 3

@Composable
private fun SettingsPillChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accentTextWhenUnselected: Boolean = false
) {
    val shape = RoundedCornerShape(18.dp)
    val containerColor = when {
        selected -> MaterialTheme.colorScheme.primary
        enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.10f)
    }
    val contentColor = when {
        selected -> MaterialTheme.colorScheme.onPrimary
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
        accentTextWhenUnselected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val borderColor = when {
        selected -> Color.Transparent
        enabled -> MaterialTheme.colorScheme.outline.copy(alpha = 0.38f)
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .heightIn(min = 36.dp)
            .clip(shape)
            .background(containerColor, shape)
            .then(
                if (selected) Modifier else Modifier.border(1.dp, borderColor, shape)
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            lineHeight = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            color = contentColor
        )
    }
}

@Composable
private fun SettingsStepChip(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fontSizeSp: Float = 12f
) {
    val shape = RoundedCornerShape(18.dp)
    val containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (enabled) 0.28f else 0.10f)
    val contentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.32f)
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = if (enabled) 0.38f else 0.15f)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .widthIn(min = 40.dp)
            .heightIn(min = 36.dp)
            .clip(shape)
            .background(containerColor, shape)
            .border(1.dp, borderColor, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            fontSize = fontSizeSp.sp,
            lineHeight = (fontSizeSp + 2f).sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            color = contentColor
        )
    }
}

@Composable
private fun SettingsHeaderButton(
    text: String,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 36.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun WearSettingsPage(
    isParentSettingsPageVisible: Boolean,
    alertMode: Int,
    onAlertModeChange: (Int) -> Unit,
    gongFreqHz: Double,
    onGongFreqChange: (Double) -> Unit,
    gongVolumePercent: Int,
    onGongVolumeChange: (Int) -> Unit,
    selectedPalette: StillColorPalette,
    onPaletteSelected: (StillColorPalette) -> Unit,
    prepTimeSeconds: Int,
    onPrepTimeChange: (Int) -> Unit,
    prepColor: Color,
    onBackToTimer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val fontScale = LocalDensity.current.fontScale

    val settingsPagerState = rememberPagerState(pageCount = { SETTINGS_PAGE_COUNT })
    val pageScrollStates = List(SETTINGS_PAGE_COUNT) { rememberScrollState() }
    val settingsFocusRequester = remember { FocusRequester() }
    val isSoundEnabled = alertMode != ALERT_MODE_VIBRATION
    var previewGongJob by remember { mutableStateOf<Job?>(null) }
    var heartRateGranted by remember { mutableStateOf(hasHeartRatePermission(context)) }
    val heartRatePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        heartRateGranted = granted || hasHeartRatePermission(context)
        Log.d("StillWear", "Esito permesso HR da Impostazioni: granted=$heartRateGranted")
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                heartRateGranted = hasHeartRatePermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    fun playPreviewGong(freq: Double, vol: Int) {
        previewGongJob?.cancel()
        previewGongJob = coroutineScope.launch(Dispatchers.Default) {
            playGongSound(context, freq, repeatCount = 1, volumePercent = vol)
        }
    }

    LaunchedEffect(isParentSettingsPageVisible) {
        if (isParentSettingsPageVisible) {
            heartRateGranted = hasHeartRatePermission(context)
            settingsFocusRequester.requestFocus()
        } else {
            previewGongJob?.cancel()
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .onRotaryScrollEvent { event ->
                val pageIdx = settingsPagerState.currentPage
                val scrollState = pageScrollStates[pageIdx]
                if (!settingsPagerState.isScrollInProgress) {
                    if (event.verticalScrollPixels > 0f) {
                        if (scrollState.canScrollForward) {
                            coroutineScope.launch { scrollState.scrollBy(event.verticalScrollPixels) }
                        } else if (pageIdx < SETTINGS_PAGE_COUNT - 1) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            coroutineScope.launch { settingsPagerState.animateScrollToPage(pageIdx + 1) }
                        }
                    } else if (event.verticalScrollPixels < 0f) {
                        if (scrollState.canScrollBackward) {
                            coroutineScope.launch { scrollState.scrollBy(event.verticalScrollPixels) }
                        } else if (pageIdx > 0) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            coroutineScope.launch { settingsPagerState.animateScrollToPage(pageIdx - 1) }
                        }
                    }
                }
                true
            }
            .focusRequester(settingsFocusRequester)
            .focusable()
    ) {
        val diameter = minOf(maxWidth, maxHeight)
        val sideStartPadding = (diameter * 0.10f).coerceAtLeast(18.dp)
        val sideEndPadding = (diameter * 0.115f).coerceAtLeast(22.dp)
        val topBottomSafePadding = (diameter * 0.165f).coerceAtLeast(32.dp)

        LaunchedEffect(maxWidth, maxHeight, fontScale) {
            Log.d(
                "StillWear",
                "WearSettingsPage layout: ${maxWidth.value.toInt()}x${maxHeight.value.toInt()}dp, fontScale=$fontScale"
            )
        }

        VerticalPager(
            state = settingsPagerState,
            modifier = Modifier.fillMaxSize()
        ) { settingsPage ->
            when (settingsPage) {
                0 -> {
                    // --- SOTTOPAGINA 1: MODALITA AVVISO + VOLUME + BATTITO ---
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(pageScrollStates[0])
                            .padding(
                                start = sideStartPadding,
                                end = sideEndPadding,
                                top = topBottomSafePadding,
                                bottom = topBottomSafePadding
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SettingsHeaderButton(
                            text = "‹ " + stringResource(R.string.settings_title),
                            onClick = onBackToTimer
                        )

                        // 1. MODALITA AVVISO (FlowRow adattiva, zero taglio su 192 dp e font Largest)
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.settings_alert_mode),
                                fontSize = 11.sp,
                                lineHeight = 13.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val modeOptions = listOf(
                                    ALERT_MODE_BOTH to stringResource(R.string.alert_mode_both),
                                    ALERT_MODE_SOUND to stringResource(R.string.alert_mode_bell),
                                    ALERT_MODE_VIBRATION to stringResource(R.string.alert_mode_vibrate)
                                )
                                modeOptions.forEach { (modeValue, labelText) ->
                                    val isSelected = alertMode == modeValue
                                    SettingsPillChip(
                                        text = labelText,
                                        selected = isSelected,
                                        onClick = {
                                            onAlertModeChange(modeValue)
                                            if (modeValue == ALERT_MODE_BOTH || modeValue == ALERT_MODE_VIBRATION) {
                                                triggerHapticGong(context, repeatCount = 1)
                                            }
                                            if (modeValue == ALERT_MODE_BOTH || modeValue == ALERT_MODE_SOUND) {
                                                playPreviewGong(gongFreqHz, gongVolumePercent)
                                            } else {
                                                previewGongJob?.cancel()
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        // 2. VOLUME CAMPANA ([-]  100%  [+])
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.settings_gong_volume),
                                fontSize = 11.sp,
                                lineHeight = 13.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                itemVerticalAlignment = Alignment.CenterVertically
                            ) {
                                SettingsStepChip(
                                    text = "−",
                                    enabled = isSoundEnabled && gongVolumePercent > 20,
                                    fontSizeSp = 14f,
                                    onClick = {
                                        if (gongVolumePercent > 20) {
                                            val newVol = (gongVolumePercent - 20).coerceAtLeast(20)
                                            onGongVolumeChange(newVol)
                                            playPreviewGong(gongFreqHz, newVol)
                                        }
                                    }
                                )

                                Text(
                                    text = "$gongVolumePercent%",
                                    fontSize = 12.sp,
                                    lineHeight = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    style = TextStyle(fontFeatureSettings = "tnum"),
                                    color = if (isSoundEnabled) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    }
                                )

                                SettingsStepChip(
                                    text = "+",
                                    enabled = isSoundEnabled && gongVolumePercent < 100,
                                    fontSizeSp = 14f,
                                    onClick = {
                                        if (gongVolumePercent < 100) {
                                            val newVol = (gongVolumePercent + 20).coerceAtMost(100)
                                            onGongVolumeChange(newVol)
                                            playPreviewGong(gongFreqHz, newVol)
                                        }
                                    }
                                )
                            }
                        }

                        // 3. AUTORIZZAZIONE BATTITO CARDIACO (visibile solo se non ancora concessa)
                        if (!heartRateGranted) {
                            SettingsPillChip(
                                text = stringResource(R.string.settings_hr_authorize),
                                selected = false,
                                accentTextWhenUnselected = true,
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    heartRatePermissionLauncher.launch(requiredHeartRatePermission())
                                }
                            )
                        }
                    }
                }

                1 -> {
                    // --- SOTTOPAGINA 2: SUONO CAMPANA (4 CLASSICHE + 5 SOFT, SCROLLABILE) ---
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(pageScrollStates[1])
                            .padding(
                                start = sideStartPadding,
                                end = sideEndPadding,
                                top = topBottomSafePadding,
                                bottom = topBottomSafePadding
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SettingsHeaderButton(
                            text = "⌃ " + stringResource(R.string.settings_gong_sound),
                            onClick = { coroutineScope.launch { settingsPagerState.animateScrollToPage(0) } }
                        )

                        val gongOptions = listOf(
                            432.0 to stringResource(R.string.bell_name_crystal),
                            528.0 to stringResource(R.string.bell_name_keisu),
                            216.0 to stringResource(R.string.bell_name_gong),
                            TINGSHA_FREQ_HZ to stringResource(R.string.bell_name_tingsha),
                            CHIME_FREQ_HZ to stringResource(R.string.bell_name_chime),
                            WATER_BOWL_FREQ_HZ to stringResource(R.string.bell_name_water_bowl),
                            SOFT_BOWL_FREQ_HZ to stringResource(R.string.bell_name_soft_bowl),
                            FEATHER_FREQ_HZ to stringResource(R.string.bell_name_feather),
                            SOFT_GONG_FREQ_HZ to stringResource(R.string.bell_name_soft_gong)
                        )

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            gongOptions.forEach { (freq, freqLabel) ->
                                val isSelected = abs(gongFreqHz - freq) <= 1.0
                                SettingsPillChip(
                                    text = freqLabel,
                                    selected = isSelected,
                                    enabled = isSoundEnabled,
                                    onClick = {
                                        onGongFreqChange(freq)
                                        playPreviewGong(freq, gongVolumePercent)
                                    }
                                )
                            }
                        }
                    }
                }

                else -> {
                    // --- SOTTOPAGINA 3: TEMA COLORI + PREPARAZIONE + VERSIONE ---
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(pageScrollStates[2])
                            .padding(
                                start = sideStartPadding,
                                end = sideEndPadding,
                                top = topBottomSafePadding,
                                bottom = topBottomSafePadding
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SettingsHeaderButton(
                            text = "⌃ " + stringResource(R.string.settings_title),
                            onClick = { coroutineScope.launch { settingsPagerState.animateScrollToPage(1) } }
                        )

                        // 1. SELETTORE TEMA (4 PALLINI COLORATI, TOUCH TARGET 36 DP, FLOWROW ADATTIVA)
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.settings_color_theme,
                                    stringResource(selectedPalette.labelRes)
                                ),
                                fontSize = 11.sp,
                                lineHeight = 13.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                StillColorPalette.entries.forEach { palette ->
                                    val isSelected = selectedPalette == palette
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .clickable(
                                                indication = null,
                                                interactionSource = remember { MutableInteractionSource() }
                                            ) {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                onPaletteSelected(palette)
                                            }
                                    ) {
                                        if (isSelected) {
                                            Box(
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .background(palette.swatchColor.copy(alpha = 0.22f), CircleShape)
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .size(if (isSelected) 24.dp else 20.dp)
                                                .background(palette.swatchColor, CircleShape)
                                                .border(
                                                    width = if (isSelected) 2.dp else 1.dp,
                                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.outline.copy(alpha = 0.40f),
                                                    shape = CircleShape
                                                )
                                        )
                                    }
                                }
                            }
                        }

                        // 2. PREPARAZIONE (-5s / +5s)
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.status_preparing),
                                fontSize = 11.sp,
                                lineHeight = 13.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                itemVerticalAlignment = Alignment.CenterVertically
                            ) {
                                SettingsStepChip(
                                    text = "-5s",
                                    enabled = prepTimeSeconds >= 5,
                                    fontSizeSp = 11f,
                                    onClick = {
                                        if (prepTimeSeconds >= 5) {
                                            onPrepTimeChange(prepTimeSeconds - 5)
                                        }
                                    }
                                )

                                Text(
                                    text = "${prepTimeSeconds}s",
                                    fontSize = 13.sp,
                                    lineHeight = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    style = TextStyle(fontFeatureSettings = "tnum"),
                                    color = prepColor
                                )

                                SettingsStepChip(
                                    text = "+5s",
                                    enabled = true,
                                    fontSizeSp = 11f,
                                    onClick = {
                                        onPrepTimeChange(prepTimeSeconds + 5)
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = stringResource(R.string.settings_app_version, BuildConfig.VERSION_NAME),
                            fontSize = 11.sp,
                            lineHeight = 13.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Indicatore verticale delle 3 sottopagine Impostazioni (sul bordo destro)
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 5.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            repeat(SETTINGS_PAGE_COUNT) { idx ->
                val selected = settingsPagerState.currentPage == idx
                Box(
                    modifier = Modifier
                        .size(if (selected) 6.dp else 4.dp)
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            CircleShape
                        )
                )
            }
        }
    }
}
