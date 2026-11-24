@file:Suppress("WearMaterialTheme")

package com.sanluca.still.wear

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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

    val settingsPagerState = rememberPagerState(pageCount = { SETTINGS_PAGE_COUNT })
    val settingsFocusRequester = remember { FocusRequester() }
    val isSoundEnabled = alertMode != ALERT_MODE_VIBRATION
    var previewGongJob by remember { mutableStateOf<Job?>(null) }
    var heartRateGranted by remember { mutableStateOf(hasHeartRatePermission(context)) }
    val heartRatePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        heartRateGranted = granted || hasHeartRatePermission(context)
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .onRotaryScrollEvent { event ->
                if (!settingsPagerState.isScrollInProgress) {
                    if (event.verticalScrollPixels > 0f && settingsPagerState.currentPage < SETTINGS_PAGE_COUNT - 1) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        coroutineScope.launch { settingsPagerState.animateScrollToPage(settingsPagerState.currentPage + 1) }
                    } else if (event.verticalScrollPixels < 0f && settingsPagerState.currentPage > 0) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        coroutineScope.launch { settingsPagerState.animateScrollToPage(settingsPagerState.currentPage - 1) }
                    }
                }
                true
            }
            .focusRequester(settingsFocusRequester)
            .focusable()
    ) {
        VerticalPager(
            state = settingsPagerState,
            modifier = Modifier.fillMaxSize()
        ) { settingsPage ->
            when (settingsPage) {
                0 -> {
                    // --- SOTTOPAGINA 1: MODALITA AVVISO + VOLUME ---
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = 10.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Text(
                            text = "‹ " + stringResource(R.string.settings_title),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clickable { onBackToTimer() }
                                .padding(horizontal = 10.dp, vertical = 2.dp)
                        )

                        // 1. MODALITA AVVISO (ENTRAMBI / CAMPANE / VIBRAZ.)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = stringResource(R.string.settings_alert_mode),
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val modeOptions = listOf(
                                    ALERT_MODE_BOTH to stringResource(R.string.alert_mode_both),
                                    ALERT_MODE_SOUND to stringResource(R.string.alert_mode_bell),
                                    ALERT_MODE_VIBRATION to stringResource(R.string.alert_mode_vibrate)
                                )
                                modeOptions.forEach { (modeValue, labelText) ->
                                    val isSelected = alertMode == modeValue
                                    Button(
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
                                        },
                                        shape = RoundedCornerShape(13.dp),
                                        border = if (isSelected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                                        modifier = Modifier.height(26.dp),
                                        contentPadding = PaddingValues(horizontal = 7.dp, vertical = 0.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                                            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    ) {
                                        Text(
                                            text = labelText,
                                            fontSize = 9.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }
                        }

                        // 2. VOLUME CAMPANA ([-]  100%  [+])
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = stringResource(R.string.settings_gong_volume),
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = {
                                        if (gongVolumePercent > 20) {
                                            val newVol = (gongVolumePercent - 20).coerceAtLeast(20)
                                            onGongVolumeChange(newVol)
                                            playPreviewGong(gongFreqHz, newVol)
                                        }
                                    },
                                    enabled = isSoundEnabled && gongVolumePercent > 20,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = if (isSoundEnabled && gongVolumePercent > 20) 0.35f else 0.15f)),
                                    modifier = Modifier.size(width = 38.dp, height = 25.dp),
                                    contentPadding = PaddingValues(0.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isSoundEnabled && gongVolumePercent > 20) 0.25f else 0.10f),
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.10f),
                                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                    )
                                ) {
                                    Text("−", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }

                                Text(
                                    text = "$gongVolumePercent%",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    style = TextStyle(fontFeatureSettings = "tnum"),
                                    color = if (isSoundEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                )

                                Button(
                                    onClick = {
                                        if (gongVolumePercent < 100) {
                                            val newVol = (gongVolumePercent + 20).coerceAtMost(100)
                                            onGongVolumeChange(newVol)
                                            playPreviewGong(gongFreqHz, newVol)
                                        }
                                    },
                                    enabled = isSoundEnabled && gongVolumePercent < 100,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = if (isSoundEnabled && gongVolumePercent < 100) 0.35f else 0.15f)),
                                    modifier = Modifier.size(width = 38.dp, height = 25.dp),
                                    contentPadding = PaddingValues(0.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isSoundEnabled && gongVolumePercent < 100) 0.25f else 0.10f),
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.10f),
                                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                    )
                                ) {
                                    Text("+", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // 3. AUTORIZZAZIONE BATTITO CARDIACO (visibile solo se non ancora concessa)
                        if (!heartRateGranted) {
                            Button(
                                onClick = {
                                    heartRatePermissionLauncher.launch(requiredHeartRatePermission())
                                },
                                shape = RoundedCornerShape(13.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                                modifier = Modifier.height(26.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                                    contentColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text(
                                    text = stringResource(R.string.settings_hr_authorize),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }

                1 -> {
                    // --- SOTTOPAGINA 2: SUONO CAMPANA (4 CLASSICHE + 5 SOFT) ---
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = 8.dp, end = 14.dp, top = 8.dp, bottom = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Text(
                            text = "⌃ " + stringResource(R.string.settings_gong_sound),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clickable { coroutineScope.launch { settingsPagerState.animateScrollToPage(0) } }
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        )

                        val gongRows = listOf(
                            listOf(
                                432.0 to stringResource(R.string.bell_name_crystal),
                                528.0 to stringResource(R.string.bell_name_keisu)
                            ),
                            listOf(
                                216.0 to stringResource(R.string.bell_name_gong),
                                TINGSHA_FREQ_HZ to stringResource(R.string.bell_name_tingsha),
                                CHIME_FREQ_HZ to stringResource(R.string.bell_name_chime)
                            ),
                            listOf(
                                WATER_BOWL_FREQ_HZ to stringResource(R.string.bell_name_water_bowl),
                                SOFT_BOWL_FREQ_HZ to stringResource(R.string.bell_name_soft_bowl)
                            ),
                            listOf(
                                FEATHER_FREQ_HZ to stringResource(R.string.bell_name_feather),
                                SOFT_GONG_FREQ_HZ to stringResource(R.string.bell_name_soft_gong)
                            )
                        )

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            gongRows.forEach { rowItems ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    rowItems.forEach { (freq, freqLabel) ->
                                        val isSelected = abs(gongFreqHz - freq) <= 1.0
                                        Button(
                                            onClick = {
                                                onGongFreqChange(freq)
                                                playPreviewGong(freq, gongVolumePercent)
                                            },
                                            enabled = isSoundEnabled,
                                            shape = RoundedCornerShape(12.dp),
                                            border = if (isSelected) null else BorderStroke(
                                                1.dp,
                                                MaterialTheme.colorScheme.outline.copy(alpha = if (isSoundEnabled) 0.35f else 0.15f)
                                            ),
                                            modifier = Modifier.height(24.dp),
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isSoundEnabled) 0.25f else 0.10f),
                                                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isSoundEnabled) 1f else 0.4f),
                                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.10f),
                                                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                            )
                                        ) {
                                            Text(
                                                text = freqLabel,
                                                fontSize = 8.5.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                else -> {
                    // --- SOTTOPAGINA 3: TEMA COLORI + PREPARAZIONE + VERSIONE ---
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = 10.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Text(
                            text = "⌃ " + stringResource(R.string.settings_title),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clickable { coroutineScope.launch { settingsPagerState.animateScrollToPage(1) } }
                                .padding(horizontal = 10.dp, vertical = 2.dp)
                        )

                        // 1. SELETTORE TEMA (4 PALLINI COLORATI SENZA ICONE)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = stringResource(
                                    R.string.settings_color_theme,
                                    stringResource(selectedPalette.labelRes)
                                ),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                StillColorPalette.entries.forEach { palette ->
                                    val isSelected = selectedPalette == palette
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(32.dp)
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
                                                    .size(30.dp)
                                                    .background(palette.swatchColor.copy(alpha = 0.22f), CircleShape)
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .size(if (isSelected) 22.dp else 18.dp)
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
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = stringResource(R.string.status_preparing),
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = {
                                        if (prepTimeSeconds >= 5) {
                                            onPrepTimeChange(prepTimeSeconds - 5)
                                        }
                                    },
                                    enabled = prepTimeSeconds >= 5,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = if (prepTimeSeconds >= 5) 0.35f else 0.15f)),
                                    modifier = Modifier.size(width = 44.dp, height = 26.dp),
                                    contentPadding = PaddingValues(0.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (prepTimeSeconds >= 5) 0.25f else 0.10f),
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.10f),
                                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                    )
                                ) {
                                    Text("-5s", fontSize = 10.sp, fontWeight = FontWeight.Medium)
                                }

                                Text(
                                    text = "${prepTimeSeconds}s",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    style = TextStyle(fontFeatureSettings = "tnum"),
                                    color = prepColor
                                )

                                Button(
                                    onClick = {
                                        onPrepTimeChange(prepTimeSeconds + 5)
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                                    modifier = Modifier.size(width = 44.dp, height = 26.dp),
                                    contentPadding = PaddingValues(0.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                ) {
                                    Text("+5s", fontSize = 10.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }

                        Text(
                            text = stringResource(R.string.settings_app_version, BuildConfig.VERSION_NAME),
                            fontSize = 8.5.sp,
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
