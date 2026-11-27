package com.sanluca.still

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanluca.still.ui.theme.LocalStillColors

@Composable
fun StillAtmosphericBackground(
    isTimerRunning: Boolean,
    isPreparing: Boolean,
    isOvertime: Boolean,
    modifier: Modifier = Modifier
) {
    val stillColors = LocalStillColors.current
    val bgColor = MaterialTheme.colorScheme.background
    val overtimeColor = stillColors.overtime
    val breathPhase = if (isTimerRunning) {
        val atmosphereTransition = rememberInfiniteTransition(label = "natural_atmosphere")
        val animatedPhase by atmosphereTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 6500, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "breath_phase"
        )
        animatedPhase
    } else {
        0.5f
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(stillColors.skyTop, bgColor, stillColors.skyBottom)
            )
        )
        val activeBoost = if (isTimerRunning && !isPreparing) 1.35f else 0.85f
        val primaryGlowColor = if (isOvertime) overtimeColor else stillColors.auroraPrimary
        val auroraCenter = Offset(
            x = size.width * (0.46f + 0.08f * breathPhase),
            y = size.height * (0.43f + 0.04f * (1f - breathPhase))
        )
        val auroraRadius = size.minDimension * 0.88f
        if (auroraRadius > 0f) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryGlowColor.copy(alpha = (0.14f + 0.06f * breathPhase) * activeBoost),
                        stillColors.auroraSecondary.copy(alpha = (0.06f + 0.04f * (1f - breathPhase)) * activeBoost),
                        Color.Transparent
                    ),
                    center = auroraCenter,
                    radius = auroraRadius
                ),
                radius = auroraRadius,
                center = auroraCenter
            )
        }
    }
}

@Composable
fun StillTopBar(
    isPreparing: Boolean,
    isOvertime: Boolean,
    selectedTimeInSeconds: Int,
    onOpenSettings: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenPrivacy: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenPresets: () -> Unit = {},
    activePresetName: String? = null,
    canSelectPreset: Boolean = true
) {
    val stillColors = LocalStillColors.current
    val overtimeColor = stillColors.overtime

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.still_title),
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Light),
            color = stillColors.titleColor
        )
        Text(
            text = when {
                isPreparing -> stringResource(R.string.status_preparing)
                isOvertime -> stringResource(R.string.status_overtime)
                activePresetName != null -> activePresetName
                selectedTimeInSeconds == -1 -> stringResource(R.string.status_no_limit)
                else -> stringResource(R.string.status_timer)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = when {
                isPreparing -> MaterialTheme.colorScheme.tertiary
                isOvertime -> overtimeColor
                activePresetName != null -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(10.dp))

        val presetsContentDesc = stringResource(R.string.cd_presets_button)
        // Capsula unificata per i 4 pulsanti (Preset, Impostazioni, Statistiche, Info)
        Row(
            modifier = Modifier
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(24.dp)
                )
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(24.dp)
                )
                .padding(horizontal = 6.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onOpenPresets,
                enabled = canSelectPreset,
                modifier = Modifier
                    .size(40.dp)
                    .alpha(if (canSelectPreset) 1f else 0.35f)
                    .semantics { contentDescription = presetsContentDesc }
            ) {
                Text(
                    text = "P",
                    fontSize = 17.sp,
                    fontWeight = if (activePresetName != null) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (activePresetName != null) MaterialTheme.colorScheme.primary else stillColors.titleColor
                )
            }
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(18.dp)
                    .background(MaterialTheme.colorScheme.outline)
            )
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier.size(40.dp)
            ) {
                Text("⚙", fontSize = 19.sp, color = stillColors.titleColor)
            }
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(18.dp)
                    .background(MaterialTheme.colorScheme.outline)
            )
            IconButton(
                onClick = onOpenStats,
                modifier = Modifier.size(40.dp)
            ) {
                Text("≡", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = stillColors.titleColor)
            }
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(18.dp)
                    .background(MaterialTheme.colorScheme.outline)
            )
            IconButton(
                onClick = onOpenPrivacy,
                modifier = Modifier.size(40.dp)
            ) {
                Text("i", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = stillColors.titleColor)
            }
        }
    }
}

@Composable
fun StillTimerCenterStage(
    isTimerRunning: Boolean,
    isPreparing: Boolean,
    isOvertime: Boolean,
    isInitialState: Boolean,
    timerStyle: Int,
    selectedTimeInSeconds: Int,
    activeSessionPlannedSeconds: Int,
    timeLeftInSeconds: Int,
    prepTimeLeftSeconds: Int,
    overtimeSeconds: Int,
    progress: Float,
    hideTimerNumbers: Boolean,
    isCompactHeight: Boolean,
    onToggleHideTimerNumbers: () -> Unit,
    modifier: Modifier = Modifier,
    onStartTimer: () -> Unit = {}
) {
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

@Composable
fun StillBottomControls(
    isInitialState: Boolean,
    isTimerRunning: Boolean,
    isPreparing: Boolean,
    isOvertime: Boolean,
    selectedTimeInSeconds: Int,
    activeSessionPlannedSeconds: Int,
    lastTimedSeconds: Int,
    timeLeftInSeconds: Int,
    overtimeSeconds: Int,
    isCompactHeight: Boolean,
    onUpdateTimeSelection: (Int) -> Unit,
    onAdjustActiveTimer: (Int) -> Unit,
    onToggleTimer: () -> Unit,
    onResetTimer: () -> Unit,
    onSaveAndFinish: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
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
