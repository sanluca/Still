package com.sanluca.still

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanluca.still.ui.theme.LocalStillColors

@Immutable
internal data class StillBottomControlsUiState(
    val isInitialState: Boolean,
    val isTimerRunning: Boolean,
    val isPreparing: Boolean,
    val isOvertime: Boolean,
    val selectedTimeInSeconds: Int,
    val activeSessionPlannedSeconds: Int,
    val lastTimedSeconds: Int,
    val timeLeftInSeconds: Int,
    val overtimeSeconds: Int,
    val isCompactHeight: Boolean
)

@Immutable
internal data class StillBottomControlsActions(
    val onUpdateTimeSelection: (Int) -> Unit,
    val onAdjustActiveTimer: (Int) -> Unit,
    val onToggleTimer: () -> Unit,
    val onResetTimer: () -> Unit,
    val onSaveAndFinish: (Boolean) -> Unit
)

@Composable
internal fun StillBottomControls(
    uiState: StillBottomControlsUiState,
    actions: StillBottomControlsActions,
    modifier: Modifier = Modifier
) {
    val (
        isInitialState,
        isTimerRunning,
        isPreparing,
        isOvertime,
        selectedTimeInSeconds,
        activeSessionPlannedSeconds,
        lastTimedSeconds,
        timeLeftInSeconds,
        overtimeSeconds,
        isCompactHeight
    ) = uiState
    val (
        onUpdateTimeSelection,
        onAdjustActiveTimer,
        onToggleTimer,
        onResetTimer,
        onSaveAndFinish
    ) = actions
    val overtimeColor = LocalStillColors.current.overtime
    val canShowTimeRow = isInitialState || (activeSessionPlannedSeconds > 0 && !isOvertime && !isPreparing)
    val timeRowAlpha by animateFloatAsState(
        targetValue = if (canShowTimeRow) 1f else 0f,
        label = "time_row_alpha"
    )
    val showSecondaryRow = isOvertime || (!isInitialState && !isTimerRunning)
    val secondaryRowAlpha by animateFloatAsState(
        targetValue = if (showSecondaryRow) 1f else 0f,
        label = "secondary_row_alpha"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (isCompactHeight) 96.dp else 130.dp)
            .padding(bottom = 12.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // I controlli inferiori rimangono sempre nel layout e vengono mostrati/nascosti tramite alpha,
            // così l'altezza complessiva non cambia mai e la figura centrale resta ferma
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.alpha(timeRowAlpha)
            ) {
                val isInfinity = isInitialState && selectedTimeInSeconds == -1
                val currentBasisSeconds = if (isInitialState) selectedTimeInSeconds else timeLeftInSeconds
                val stepAutoSize = TextAutoSize.StepBased(
                    minFontSize = 7.sp,
                    maxFontSize = 14.sp,
                    stepSize = 0.5.sp
                )
                val infinityAutoSize = TextAutoSize.StepBased(
                    minFontSize = 8.sp,
                    maxFontSize = 18.sp,
                    stepSize = 0.5.sp
                )

                val stepButtonColors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.tertiary
                )
                val stepButtonBorder = BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.45f)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            if (isInitialState) {
                                if (selectedTimeInSeconds > 300) onUpdateTimeSelection(selectedTimeInSeconds - 300)
                            } else {
                                onAdjustActiveTimer(-300)
                            }
                        },
                        enabled = canShowTimeRow && currentBasisSeconds > 300,
                        colors = stepButtonColors,
                        border = stepButtonBorder,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "-5m",
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            autoSize = stepAutoSize
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            if (isInitialState) {
                                if (selectedTimeInSeconds > 60) onUpdateTimeSelection(selectedTimeInSeconds - 60)
                            } else {
                                onAdjustActiveTimer(-60)
                            }
                        },
                        enabled = canShowTimeRow && currentBasisSeconds > 60,
                        colors = stepButtonColors,
                        border = stepButtonBorder,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "-1m",
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            autoSize = stepAutoSize
                        )
                    }

                    val onInfinityClick = {
                        if (isInfinity) {
                            onUpdateTimeSelection(lastTimedSeconds)
                        } else {
                            onUpdateTimeSelection(-1)
                        }
                    }
                    val infinityLabel: @Composable () -> Unit = {
                        Text(
                            text = "∞",
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            autoSize = infinityAutoSize,
                            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                        )
                    }

                    if (isInfinity) {
                        Button(
                            onClick = onInfinityClick,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiary,
                                contentColor = MaterialTheme.colorScheme.onTertiary
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp)
                        ) { infinityLabel() }
                    } else {
                        OutlinedButton(
                            onClick = onInfinityClick,
                            enabled = isInitialState,
                            colors = stepButtonColors,
                            border = stepButtonBorder,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp)
                        ) { infinityLabel() }
                    }

                    OutlinedButton(
                        onClick = {
                            if (isInitialState) {
                                onUpdateTimeSelection(if (isInfinity) 60 else selectedTimeInSeconds + 60)
                            } else {
                                onAdjustActiveTimer(60)
                            }
                        },
                        enabled = canShowTimeRow,
                        colors = stepButtonColors,
                        border = stepButtonBorder,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "+1m",
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            autoSize = stepAutoSize
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            if (isInitialState) {
                                onUpdateTimeSelection(if (isInfinity) 300 else selectedTimeInSeconds + 300)
                            } else {
                                onAdjustActiveTimer(300)
                            }
                        },
                        enabled = canShowTimeRow,
                        colors = stepButtonColors,
                        border = stepButtonBorder,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "+5m",
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            autoSize = stepAutoSize
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // --- PULSANTE PRINCIPALE INIZIA / PAUSA / COMPLETA EXTRA ---
            val canStart = isTimerRunning || selectedTimeInSeconds == -1 || timeLeftInSeconds > 0
            Button(
                onClick = {
                    if (isOvertime) {
                        onSaveAndFinish(true)
                    } else {
                        onToggleTimer()
                    }
                },
                enabled = isOvertime || canStart,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .heightIn(min = 54.dp)
                    .fillMaxWidth(if (isOvertime) 0.85f else 0.80f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = when {
                        isOvertime -> overtimeColor
                        isTimerRunning -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.primary
                    }
                )
            ) {
                Text(
                    text = when {
                        isOvertime -> stringResource(R.string.button_save_with_extra, (overtimeSeconds / 60))
                        isTimerRunning -> stringResource(R.string.button_pause)
                        !isInitialState -> stringResource(R.string.button_resume)
                        else -> stringResource(R.string.button_start_meditation)
                    },
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = if (isOvertime) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = if (isOvertime) Color.Black else Color.Unspecified
                )
            }

            // Riga secondaria (Reset / Salva): sempre nel layout, visibile solo in pausa o in overtime
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(IntrinsicSize.Min)
                    .alpha(secondaryRowAlpha),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onResetTimer,
                    enabled = showSecondaryRow,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.button_reset),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (isOvertime) {
                    OutlinedButton(
                        onClick = { onSaveAndFinish(false) },
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.button_save_planned),
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    Button(
                        onClick = { onSaveAndFinish(false) },
                        enabled = showSecondaryRow,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.button_save_and_reset),
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
