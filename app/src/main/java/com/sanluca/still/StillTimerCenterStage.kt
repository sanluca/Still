package com.sanluca.still

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanluca.still.core.formatTime
import com.sanluca.still.ui.theme.LocalStillColors

@Immutable
internal data class StillTimerCenterStageUiState(
    val isTimerRunning: Boolean,
    val isPreparing: Boolean,
    val isOvertime: Boolean,
    val isInitialState: Boolean,
    val timerStyle: Int,
    val selectedTimeInSeconds: Int,
    val activeSessionPlannedSeconds: Int,
    val timeLeftInSeconds: Int,
    val prepTimeLeftSeconds: Int,
    val overtimeSeconds: Int,
    val progress: Float,
    val hideTimerNumbers: Boolean,
    val isCompactHeight: Boolean
)

@Immutable
internal data class StillTimerCenterStageActions(
    val onToggleHideTimerNumbers: () -> Unit,
    val onStartTimer: () -> Unit = {}
)

@Composable
internal fun StillTimerCenterStage(
    uiState: StillTimerCenterStageUiState,
    actions: StillTimerCenterStageActions,
    modifier: Modifier = Modifier
) {
    val (
        isTimerRunning,
        isPreparing,
        isOvertime,
        isInitialState,
        timerStyle,
        selectedTimeInSeconds,
        activeSessionPlannedSeconds,
        timeLeftInSeconds,
        prepTimeLeftSeconds,
        overtimeSeconds,
        progress,
        hideTimerNumbers,
        isCompactHeight
    ) = uiState
    val onToggleHideTimerNumbers = actions.onToggleHideTimerNumbers
    val onStartTimer = actions.onStartTimer
    val stillColors = LocalStillColors.current
    val overtimeColor = stillColors.overtime

    val initialLabelText = if (!isInitialState && !isPreparing && selectedTimeInSeconds > 0) {
        if (activeSessionPlannedSeconds != selectedTimeInSeconds && activeSessionPlannedSeconds > 0) {
            stringResource(
                R.string.timer_initial_adjusted_format,
                formatTime(selectedTimeInSeconds),
                formatTime(activeSessionPlannedSeconds)
            )
        } else {
            stringResource(R.string.timer_initial_format, formatTime(selectedTimeInSeconds))
        }
    } else {
        null
    }

    val ambientGlowAlpha = if (isTimerRunning && !isPreparing) {
        val glowTransition = rememberInfiniteTransition(label = "ambient_glow")
        val animatedAlpha by glowTransition.animateFloat(
            initialValue = 0.07f,
            targetValue = 0.16f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 3800, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "ambient_glow_alpha"
        )
        animatedAlpha
    } else {
        0.08f
    }
    val ambientGlowColor = if (isOvertime) overtimeColor else MaterialTheme.colorScheme.primary

    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val circleSize = minOf(
            if (isCompactHeight) 180.dp else 250.dp,
            maxWidth * 0.88f,
            maxHeight * 0.92f
        ).coerceAtLeast(120.dp)
        val reservedDigitsHeight = if (isCompactHeight) 56.dp else 72.dp
        val maxHourglassHeight = (maxHeight - reservedDigitsHeight).coerceAtLeast(96.dp)
        val hourglassHeight = minOf(
            if (isCompactHeight) 168.dp else 240.dp,
            maxHourglassHeight
        )
        val hourglassWidth = (hourglassHeight * (185f / 240f)).coerceAtMost(maxWidth * 0.82f)

        Canvas(modifier = Modifier.fillMaxSize()) {
            val glowRadius = size.minDimension * 0.62f
            if (glowRadius > 0f) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            ambientGlowColor.copy(alpha = ambientGlowAlpha),
                            ambientGlowColor.copy(alpha = ambientGlowAlpha * 0.35f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = glowRadius
                    ),
                    radius = glowRadius,
                    center = center
                )
            }
        }
        if (timerStyle == TIMER_STYLE_ESSENTIAL) {
            val animatedEssentialProgress by animateFloatAsState(
                targetValue = when {
                    isPreparing -> 1f
                    isOvertime -> 1f
                    selectedTimeInSeconds == -1 -> 1f
                    else -> progress.coerceIn(0f, 1f)
                },
                animationSpec = tween(durationMillis = 320, easing = LinearEasing),
                label = "essential_progress"
            )
            val essentialTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.24f)
            val essentialArcColor = when {
                isPreparing -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.72f)
                isOvertime -> overtimeColor.copy(alpha = 0.78f)
                isInitialState -> MaterialTheme.colorScheme.primary.copy(alpha = 0.34f)
                else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.72f)
            }
            val essentialInnerPadding = if (isCompactHeight || circleSize < 210.dp) 28.dp else 36.dp

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(circleSize)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        if (isInitialState) {
                            onStartTimer()
                        } else {
                            onToggleHideTimerNumbers()
                        }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val trackWidth = 1.5.dp.toPx()
                    val arcWidth = 2.25.dp.toPx()
                    val inset = arcWidth / 2f + 2.dp.toPx()
                    val diameter = (size.minDimension - inset * 2f).coerceAtLeast(1f)
                    val topLeft = Offset(
                        x = (size.width - diameter) / 2f,
                        y = (size.height - diameter) / 2f
                    )
                    val arcSize = Size(diameter, diameter)

                    drawArc(
                        color = essentialTrackColor,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = trackWidth)
                    )

                    val sweep = (360f * animatedEssentialProgress).coerceIn(0f, 360f)
                    if (sweep > 0.5f) {
                        drawArc(
                            color = essentialArcColor,
                            startAngle = -90f,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = arcWidth, cap = StrokeCap.Round)
                        )
                    }
                }

                val initialLabelAutoSize = TextAutoSize.StepBased(
                    minFontSize = 8.sp,
                    maxFontSize = 12.sp,
                    stepSize = 0.5.sp
                )
                if (isPreparing) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = essentialInnerPadding)
                    ) {
                        Text(
                            text = stringResource(R.string.text_prepare),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                        Text(
                            text = "${prepTimeLeftSeconds}s",
                            maxLines = 1,
                            softWrap = false,
                            autoSize = TextAutoSize.StepBased(
                                minFontSize = 18.sp,
                                maxFontSize = 48.sp,
                                stepSize = 1.sp
                            ),
                            style = MaterialTheme.typography.displayLarge.copy(
                                fontSize = 48.sp,
                                fontWeight = FontWeight.Light,
                                fontFeatureSettings = "tnum"
                            ),
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                } else if (isOvertime) {
                    val overtimeMaxSp = if (overtimeSeconds >= 6000) 40.sp else 48.sp
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .padding(horizontal = essentialInnerPadding)
                            .alpha(if (hideTimerNumbers) 0f else 1f)
                    ) {
                        Text(
                            text = stringResource(R.string.text_extra),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleSmall,
                            color = overtimeColor
                        )
                        Text(
                            text = "+${formatTime(overtimeSeconds)}",
                            maxLines = 1,
                            softWrap = false,
                            autoSize = TextAutoSize.StepBased(
                                minFontSize = 16.sp,
                                maxFontSize = overtimeMaxSp,
                                stepSize = 1.sp
                            ),
                            style = MaterialTheme.typography.displayLarge.copy(
                                fontSize = overtimeMaxSp,
                                fontWeight = FontWeight.Light,
                                fontFeatureSettings = "tnum"
                            ),
                            color = overtimeColor
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = initialLabelText ?: " ",
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            autoSize = initialLabelAutoSize,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                            modifier = Modifier.alpha(if (initialLabelText != null) 1f else 0f)
                        )
                    }
                } else {
                    val digitsMaxSp = when {
                        selectedTimeInSeconds == -1 && !isTimerRunning && timeLeftInSeconds == 0 -> 76.sp
                        timeLeftInSeconds >= 6000 -> 44.sp
                        else -> 54.sp
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .padding(horizontal = essentialInnerPadding)
                            .alpha(if (hideTimerNumbers && isTimerRunning) 0f else 1f)
                    ) {
                        Text(
                            text = if (selectedTimeInSeconds == -1 && !isTimerRunning && timeLeftInSeconds == 0) {
                                "∞"
                            } else {
                                formatTime(timeLeftInSeconds)
                            },
                            maxLines = 1,
                            softWrap = false,
                            textAlign = TextAlign.Center,
                            autoSize = TextAutoSize.StepBased(
                                minFontSize = 16.sp,
                                maxFontSize = digitsMaxSp,
                                stepSize = 1.sp
                            ),
                            style = MaterialTheme.typography.displayLarge.copy(
                                fontSize = digitsMaxSp,
                                fontWeight = FontWeight.ExtraLight,
                                fontFeatureSettings = "tnum",
                                color = stillColors.timerDigitsColor,
                                platformStyle = PlatformTextStyle(includeFontPadding = false)
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = initialLabelText ?: " ",
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            autoSize = initialLabelAutoSize,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                            modifier = Modifier.alpha(if (initialLabelText != null) 1f else 0f)
                        )
                    }
                }
            }
        } else if (timerStyle == 1 || timerStyle == 2) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.clickable(
                    enabled = !isInitialState,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    onToggleHideTimerNumbers()
                }
            ) {
                val remainingFraction = when {
                    isPreparing -> 1f
                    isOvertime -> if (timerStyle == 2) 0.10f else 0f
                    selectedTimeInSeconds == -1 -> when {
                        isInitialState -> 1f
                        timerStyle == 2 -> 0.75f
                        else -> (1f - (timeLeftInSeconds.coerceAtMost(60) / 120f))
                    }
                    else -> progress.coerceIn(0f, 1f)
                }
                val isActiveFlow = isTimerRunning && !isPreparing && !isOvertime

                if (timerStyle == 1) {
                    AutumnHourglassGraphic(
                        topSandFraction = remainingFraction,
                        isFlowing = isActiveFlow,
                        isOvertime = isOvertime,
                        isPreparing = isPreparing,
                        modifier = Modifier.size(width = hourglassWidth, height = hourglassHeight),
                        isInitialState = isInitialState,
                        onFlipToStart = if (isInitialState) onStartTimer else null
                    )
                } else {
                    CandleTimerGraphic(
                        waxFraction = remainingFraction,
                        isRunning = isTimerRunning,
                        isOvertime = isOvertime,
                        isPreparing = isPreparing,
                        modifier = Modifier.size(width = hourglassWidth, height = hourglassHeight),
                        isInitialState = isInitialState,
                        onLightToStart = if (isInitialState) onStartTimer else null
                    )
                }

                Spacer(modifier = Modifier.height(if (isCompactHeight) 4.dp else 10.dp))

                // Il blocco dei numeri ha sempre la stessa struttura (testo displayMedium + riga "Iniziale")
                // così candela e clessidra non si spostano di un pixel quando cambia lo stato del timer
                Box(contentAlignment = Alignment.TopCenter) {
                    if (isPreparing) {
                        Text(
                            text = "${stringResource(R.string.text_prepare)} ${prepTimeLeftSeconds}s",
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }

                    val numbersAlpha = when {
                        isPreparing -> 0f
                        isOvertime -> if (hideTimerNumbers) 0f else 1f
                        else -> if (hideTimerNumbers && isTimerRunning) 0f else 1f
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.alpha(numbersAlpha)
                    ) {
                        if (isOvertime) {
                            Text(
                                text = "+${formatTime(overtimeSeconds)}",
                                maxLines = 1,
                                softWrap = false,
                                style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
                                color = overtimeColor
                            )
                        } else {
                            Text(
                                text = if (selectedTimeInSeconds == -1 && !isTimerRunning && timeLeftInSeconds == 0) {
                                    "∞"
                                } else {
                                    formatTime(timeLeftInSeconds)
                                },
                                maxLines = 1,
                                softWrap = false,
                                style = MaterialTheme.typography.displayMedium.copy(
                                    fontWeight = FontWeight.Light,
                                    color = stillColors.timerDigitsColor
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = initialLabelText ?: " ",
                            maxLines = 1,
                            softWrap = false,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                            modifier = Modifier.alpha(if (initialLabelText != null) 1f else 0f)
                        )
                    }
                }
            }
        } else {
            val circleInnerPadding = if (isCompactHeight || circleSize < 210.dp) 36.dp else 48.dp
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(circleSize)
                    .clickable(
                        enabled = !isInitialState,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        onToggleHideTimerNumbers()
                    }
            ) {
                ZenWheelTimerGraphic(
                    isRunning = isTimerRunning,
                    isInitialState = isInitialState,
                    isOvertime = isOvertime,
                    isPreparing = isPreparing,
                    modifier = Modifier.size(circleSize),
                    progress = progress,
                    isInfiniteMode = selectedTimeInSeconds == -1,
                    onTouchToStart = if (isInitialState) onStartTimer else null
                )

                val wheelLabelAutoSize = TextAutoSize.StepBased(
                    minFontSize = 8.sp,
                    maxFontSize = 12.sp,
                    stepSize = 0.5.sp
                )
                if (isPreparing) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = circleInnerPadding)
                    ) {
                        Text(
                            text = stringResource(R.string.text_prepare),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                        Text(
                            text = "${prepTimeLeftSeconds}s",
                            maxLines = 1,
                            softWrap = false,
                            autoSize = TextAutoSize.StepBased(
                                minFontSize = 16.sp,
                                maxFontSize = 42.sp,
                                stepSize = 1.sp
                            ),
                            style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                } else if (isOvertime) {
                    val overtimeMaxSp = if (overtimeSeconds >= 6000) 36.sp else 42.sp
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .padding(horizontal = circleInnerPadding)
                            .alpha(if (hideTimerNumbers) 0f else 1f)
                    ) {
                        Text(
                            text = stringResource(R.string.text_extra),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleSmall,
                            color = overtimeColor
                        )
                        Text(
                            text = "+${formatTime(overtimeSeconds)}",
                            maxLines = 1,
                            softWrap = false,
                            autoSize = TextAutoSize.StepBased(
                                minFontSize = 14.sp,
                                maxFontSize = overtimeMaxSp,
                                stepSize = 1.sp
                            ),
                            style = MaterialTheme.typography.displayLarge.copy(
                                fontSize = overtimeMaxSp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = overtimeColor
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = initialLabelText ?: " ",
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            autoSize = wheelLabelAutoSize,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                            modifier = Modifier.alpha(if (initialLabelText != null) 1f else 0f)
                        )
                    }
                } else {
                    val digitsMaxSp = when {
                        selectedTimeInSeconds == -1 && !isTimerRunning && timeLeftInSeconds == 0 -> 72.sp
                        timeLeftInSeconds >= 6000 -> 38.sp
                        else -> 44.sp
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .padding(horizontal = circleInnerPadding)
                            .alpha(if (hideTimerNumbers && isTimerRunning) 0f else 1f)
                    ) {
                        Text(
                            text = if (selectedTimeInSeconds == -1 && !isTimerRunning && timeLeftInSeconds == 0) {
                                "∞"
                            } else {
                                formatTime(timeLeftInSeconds)
                            },
                            maxLines = 1,
                            softWrap = false,
                            textAlign = TextAlign.Center,
                            autoSize = TextAutoSize.StepBased(
                                minFontSize = 14.sp,
                                maxFontSize = digitsMaxSp,
                                stepSize = 1.sp
                            ),
                            style = MaterialTheme.typography.displayLarge.copy(
                                fontSize = digitsMaxSp,
                                fontWeight = FontWeight.Light,
                                color = stillColors.timerDigitsColor,
                                platformStyle = PlatformTextStyle(includeFontPadding = false)
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = initialLabelText ?: " ",
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            autoSize = wheelLabelAutoSize,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                            modifier = Modifier.alpha(if (initialLabelText != null) 1f else 0f)
                        )
                    }
                }
            }
        }
    }
}
