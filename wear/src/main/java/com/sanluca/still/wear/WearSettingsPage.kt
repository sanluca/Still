@file:Suppress("WearMaterialTheme")

package com.sanluca.still.wear

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ScrollState
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import android.media.AudioAttributes
import com.sanluca.still.core.ALERT_MODE_BOTH
import com.sanluca.still.core.ALERT_MODE_SOUND
import com.sanluca.still.core.ALERT_MODE_VIBRATION
import com.sanluca.still.core.BellSound
import com.sanluca.still.core.playGongSound
import com.sanluca.still.core.triggerHapticGong
import com.sanluca.still.wear.ui.theme.StillColorPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
internal fun WearSettingsPage(
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
    modifier: Modifier = Modifier,
    resetSubpageTick: Long = 0L
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    val settingsPagerState = rememberPagerState(pageCount = { SETTINGS_PAGE_COUNT })
    val pageScrollStates = List(SETTINGS_PAGE_COUNT) { rememberScrollState() }
    val settingsFocusRequester = remember { FocusRequester() }
    val isSoundEnabled = alertMode != ALERT_MODE_VIBRATION
    var previewGongJob by remember { mutableStateOf<Job?>(null) }
    var heartRateGranted by remember { mutableStateOf(hasHeartRatePermission(context)) }
    var heartRateEnabledPref by remember {
        mutableStateOf(
            if (heartRateGranted) {
                isHeartRateTrackingEnabled(context)
            } else {
                if (isHeartRateTrackingEnabled(context)) {
                    setHeartRateTrackingEnabled(context, false)
                }
                false
            }
        )
    }
    val isHeartRateActive = heartRateGranted && heartRateEnabledPref

    fun refreshHeartRateState() {
        val grantedNow = hasHeartRatePermission(context)
        heartRateGranted = grantedNow
        if (!grantedNow) {
            if (isHeartRateTrackingEnabled(context)) {
                setHeartRateTrackingEnabled(context, false)
            }
            heartRateEnabledPref = false
            StillTimerService.clearHeartRateSamples()
        } else {
            heartRateEnabledPref = isHeartRateTrackingEnabled(context)
        }
    }

    val heartRatePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val grantedNow = granted || hasHeartRatePermission(context)
        heartRateGranted = grantedNow
        heartRateEnabledPref = grantedNow
        setHeartRateTrackingEnabled(context, grantedNow)
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshHeartRateState()
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
            playGongSound(
                context = context,
                frequencyHz = freq,
                repeatCount = 1,
                volumePercent = vol,
                requestAudioFocus = false,
                usage = AudioAttributes.USAGE_ALARM,
                contentType = AudioAttributes.CONTENT_TYPE_SONIFICATION
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            previewGongJob?.cancel()
        }
    }

    LaunchedEffect(resetSubpageTick) {
        if (resetSubpageTick > 0L) {
            settingsPagerState.scrollToPage(0)
        }
    }

    LaunchedEffect(isParentSettingsPageVisible) {
        if (isParentSettingsPageVisible) {
            refreshHeartRateState()
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

        VerticalPager(
            state = settingsPagerState,
            modifier = Modifier.fillMaxSize()
        ) { settingsPage ->
            when (settingsPage) {
                0 -> WearAudioSettingsSubpage(
                    scrollState = pageScrollStates[0],
                    sideStartPadding = sideStartPadding,
                    sideEndPadding = sideEndPadding,
                    topBottomSafePadding = topBottomSafePadding,
                    alertMode = alertMode,
                    isSoundEnabled = isSoundEnabled,
                    gongVolumePercent = gongVolumePercent,
                    isHeartRateActive = isHeartRateActive,
                    heartRateGranted = heartRateGranted,
                    onBackToTimer = onBackToTimer,
                    onAlertModeSelected = { modeValue ->
                        onAlertModeChange(modeValue)
                        if (modeValue == ALERT_MODE_BOTH || modeValue == ALERT_MODE_VIBRATION) {
                            triggerHapticGong(context, repeatCount = 1)
                        }
                        if (modeValue == ALERT_MODE_BOTH || modeValue == ALERT_MODE_SOUND) {
                            playPreviewGong(gongFreqHz, gongVolumePercent)
                        } else {
                            previewGongJob?.cancel()
                        }
                    },
                    onGongVolumeChange = { newVol ->
                        onGongVolumeChange(newVol)
                        playPreviewGong(gongFreqHz, newVol)
                    },
                    onHeartRateDisable = {
                        heartRateEnabledPref = false
                        setHeartRateTrackingEnabled(context, false)
                        StillTimerService.clearHeartRateSamples()
                    },
                    onHeartRateEnable = {
                        if (hasHeartRatePermission(context)) {
                            heartRateGranted = true
                            heartRateEnabledPref = true
                            setHeartRateTrackingEnabled(context, true)
                        } else {
                            heartRatePermissionLauncher.launch(requiredHeartRatePermission())
                        }
                    }
                )

                1 -> WearBellSoundSubpage(
                    scrollState = pageScrollStates[1],
                    sideStartPadding = sideStartPadding,
                    sideEndPadding = sideEndPadding,
                    topBottomSafePadding = topBottomSafePadding,
                    gongFreqHz = gongFreqHz,
                    isSoundEnabled = isSoundEnabled,
                    onBackToPreviousSubpage = {
                        coroutineScope.launch { settingsPagerState.animateScrollToPage(0) }
                    },
                    onBellSelected = { bell ->
                        onGongFreqChange(bell.code)
                        playPreviewGong(bell.code, gongVolumePercent)
                    }
                )

                else -> WearAppearanceSettingsSubpage(
                    scrollState = pageScrollStates[2],
                    sideStartPadding = sideStartPadding,
                    sideEndPadding = sideEndPadding,
                    topBottomSafePadding = topBottomSafePadding,
                    selectedPalette = selectedPalette,
                    prepTimeSeconds = prepTimeSeconds,
                    prepColor = prepColor,
                    onBackToPreviousSubpage = {
                        coroutineScope.launch { settingsPagerState.animateScrollToPage(1) }
                    },
                    onPaletteSelected = { palette ->
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onPaletteSelected(palette)
                    },
                    onPrepTimeChange = onPrepTimeChange
                )
            }
        }

        WearSettingsPageIndicator(
            currentPage = settingsPagerState.currentPage,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 5.dp)
        )
    }
}

@Composable
private fun WearAudioSettingsSubpage(
    scrollState: ScrollState,
    sideStartPadding: Dp,
    sideEndPadding: Dp,
    topBottomSafePadding: Dp,
    alertMode: Int,
    isSoundEnabled: Boolean,
    gongVolumePercent: Int,
    isHeartRateActive: Boolean,
    heartRateGranted: Boolean,
    onBackToTimer: () -> Unit,
    onAlertModeSelected: (Int) -> Unit,
    onGongVolumeChange: (Int) -> Unit,
    onHeartRateDisable: () -> Unit,
    onHeartRateEnable: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
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
                    SettingsPillChip(
                        text = labelText,
                        selected = alertMode == modeValue,
                        onClick = { onAlertModeSelected(modeValue) }
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
                            onGongVolumeChange((gongVolumePercent - 20).coerceAtLeast(20))
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
                            onGongVolumeChange((gongVolumePercent + 20).coerceAtMost(100))
                        }
                    }
                )
            }
        }

        // 3. RILEVAMENTO BATTITO CARDIACO (Off / On in FlowRow adattiva)
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_hr_label),
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
                SettingsPillChip(
                    text = stringResource(R.string.settings_hr_off),
                    selected = !isHeartRateActive,
                    onClick = onHeartRateDisable
                )
                SettingsPillChip(
                    text = stringResource(R.string.settings_hr_on),
                    selected = isHeartRateActive,
                    accentTextWhenUnselected = !heartRateGranted,
                    onClick = onHeartRateEnable
                )
            }
        }
    }
}

@Composable
private fun WearBellSoundSubpage(
    scrollState: ScrollState,
    sideStartPadding: Dp,
    sideEndPadding: Dp,
    topBottomSafePadding: Dp,
    gongFreqHz: Double,
    isSoundEnabled: Boolean,
    onBackToPreviousSubpage: () -> Unit,
    onBellSelected: (BellSound) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(
                start = sideStartPadding,
                end = sideEndPadding,
                top = topBottomSafePadding,
                bottom = topBottomSafePadding
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        SettingsHeaderButton(
            text = "⌃ " + stringResource(R.string.settings_gong_sound),
            onClick = onBackToPreviousSubpage
        )

        val selectedBell = BellSound.fromCode(gongFreqHz, BellSound.CLASSIC_BOWL)

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            BellSound.entries.forEach { bell ->
                SettingsPillChip(
                    text = stringResource(bell.labelRes),
                    selected = selectedBell == bell,
                    enabled = isSoundEnabled,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onBellSelected(bell) }
                )
            }
        }
    }
}

@Composable
private fun WearAppearanceSettingsSubpage(
    scrollState: ScrollState,
    sideStartPadding: Dp,
    sideEndPadding: Dp,
    topBottomSafePadding: Dp,
    selectedPalette: StillColorPalette,
    prepTimeSeconds: Int,
    prepColor: Color,
    onBackToPreviousSubpage: () -> Unit,
    onPaletteSelected: (StillColorPalette) -> Unit,
    onPrepTimeChange: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
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
            onClick = onBackToPreviousSubpage
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

@Composable
private fun WearSettingsPageIndicator(
    currentPage: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        repeat(SETTINGS_PAGE_COUNT) { idx ->
            val selected = currentPage == idx
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
