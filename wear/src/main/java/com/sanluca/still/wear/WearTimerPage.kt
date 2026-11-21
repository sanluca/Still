@file:Suppress("WearMaterialTheme")

package com.sanluca.still.wear

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun WearTimerPage(
    isAmbient: Boolean,
    isTimerRunning: Boolean,
    isPreparing: Boolean,
    isOvertime: Boolean,
    isInitialState: Boolean,
    selectedTimeInSeconds: Int,
    timeLeftInSeconds: Int,
    prepTimeSeconds: Int,
    prepTimeLeftSeconds: Int,
    overtimeSeconds: Int,
    progress: Float,
    currentClockText: String,
    overtimeColor: Color,
    prepColor: Color,
    showSettingsPage: Boolean,
    currentPage: Int,
    onUpdateTimeSelection: (Int) -> Unit,
    onResetTimer: () -> Unit,
    onSaveAndFinish: (Boolean) -> Unit,
    onToggleTimer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val diameter = minOf(maxWidth, maxHeight)
        val isPausedState = !isInitialState && !isTimerRunning && !isOvertime
        val showSideControls = isInitialState || isPausedState || isOvertime
        val canDecrease = selectedTimeInSeconds != -1

        // Animazione di pulsazione lenta del respiro: attiva solo con timer in esecuzione e non in ambient
        val breathGlowAlphaState: State<Float>? = if (isTimerRunning && !isAmbient) {
            val infiniteTransition = rememberInfiniteTransition(label = "wear_breath_transition")
            infiniteTransition.animateFloat(
                initialValue = 0.08f,
                targetValue = 0.22f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 3500, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "wear_breath_glow"
            )
        } else {
            null
        }

        val primaryColor = MaterialTheme.colorScheme.primary
        val tertiaryColor = MaterialTheme.colorScheme.tertiary
        val ringTrackColor = if (isAmbient) Color.Transparent else primaryColor.copy(alpha = 0.14f)

        // 1. CANVAS PERIMETRALE PULITO (Glow radiale soffuso + Traccia di fondo + Arco di avanzamento)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val centerOffset = Offset(w / 2f, h / 2f)
            val outerPaddingPx = 4.dp.toPx()
            val arcDiameter = (minOf(w, h) - outerPaddingPx * 2).coerceAtLeast(10f)
            val arcTopLeft = Offset((w - arcDiameter) / 2f, (h - arcDiameter) / 2f)
            val arcSize = Size(arcDiameter, arcDiameter)
            val strokeWidthPx = if (isAmbient) 1.5.dp.toPx() else 2.5.dp.toPx()

            // A) Bagliore radiale centrale molto soffuso (solo in modalità attiva, spento in Ambient)
            if (!isAmbient) {
                val glowAlpha = breathGlowAlphaState?.value ?: 0.10f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            tertiaryColor.copy(alpha = glowAlpha),
                            primaryColor.copy(alpha = glowAlpha * 0.45f),
                            Color.Transparent
                        ),
                        center = centerOffset,
                        radius = arcDiameter * 0.52f
                    ),
                    radius = arcDiameter * 0.52f,
                    center = centerOffset
                )
            }

            // B) Traccia circolare sottile di fondo a 360°
            drawArc(
                color = ringTrackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
            )

            // C) Arco perimetrale di avanzamento
            val sweepAngle = when {
                isPreparing -> (prepTimeLeftSeconds.toFloat() / prepTimeSeconds.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f) * 360f
                isOvertime -> 360f
                selectedTimeInSeconds == -1 -> if (isTimerRunning) 360f else 0f
                else -> progress.coerceIn(0f, 1f) * 360f
            }

            val arcStroke = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)

            if (isAmbient) {
                // In Ambient: arco sottile monocromatico senza gradienti
                drawArc(
                    color = Color.White,
                    startAngle = -90f,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = arcStroke
                )
            } else if (isOvertime) {
                drawArc(
                    color = overtimeColor,
                    startAngle = -90f,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = arcStroke
                )
            } else if (isPreparing) {
                drawArc(
                    color = prepColor,
                    startAngle = -90f,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = arcStroke
                )
            } else if (sweepAngle > 0.5f) {
                // Sfumatura morbida tra primary e tertiary lungo il perimetro
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(primaryColor, tertiaryColor, primaryColor),
                        center = centerOffset
                    ),
                    startAngle = -90f,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = arcStroke
                )
            }
        }

        // 2. CONTENITORE INTERNO INSCRITTO (SAFE ZONE)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = diameter * 0.10f, vertical = diameter * 0.11f),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Fascia superiore (Ore 12)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = currentClockText,
                    fontSize = 10.sp,
                    color = if (isAmbient) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    style = TextStyle(
                        fontFeatureSettings = "tnum",
                        fontWeight = FontWeight.Normal
                    )
                )
                Text(
                    text = when {
                        isPreparing -> stringResource(R.string.text_prepare)
                        isOvertime -> stringResource(R.string.status_extra)
                        else -> stringResource(R.string.status_still)
                    },
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    color = when {
                        isAmbient -> Color.White.copy(alpha = 0.7f)
                        isPreparing -> prepColor
                        isOvertime -> overtimeColor
                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                    }
                )
            }

            // Fascia centrale (Row)
            val sideBtnSize = (diameter * 0.145f).coerceIn(26.dp, 32.dp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                // Pulsante circolare [-]
                Box(
                    modifier = Modifier.size(sideBtnSize),
                    contentAlignment = Alignment.Center
                ) {
                    if (showSideControls && !isAmbient) {
                        val leftEnabled = !isInitialState || canDecrease
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(sideBtnSize)
                                .background(primaryColor.copy(alpha = if (leftEnabled) 0.12f else 0.04f), CircleShape)
                                .border(1.dp, primaryColor.copy(alpha = if (leftEnabled) 0.40f else 0.15f), CircleShape)
                                .clickable(
                                    enabled = leftEnabled,
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() }
                                ) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    if (isInitialState) {
                                        if (selectedTimeInSeconds > 60) {
                                            onUpdateTimeSelection(selectedTimeInSeconds - 60)
                                        } else if (selectedTimeInSeconds == 60) {
                                            onUpdateTimeSelection(-1)
                                        }
                                    } else {
                                        onResetTimer()
                                    }
                                }
                        ) {
                            Text(
                                text = if (isInitialState) "−" else "✕",
                                fontSize = if (isInitialState) 15.sp else 11.sp,
                                fontWeight = FontWeight.Normal,
                                color = if (leftEnabled) primaryColor else primaryColor.copy(alpha = 0.3f)
                            )
                        }
                    }
                }

                // Testo del timer MM:SS
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = when {
                            isPreparing -> "${prepTimeLeftSeconds}s"
                            isOvertime -> "+${formatTime(overtimeSeconds)}"
                            selectedTimeInSeconds == -1 && !isTimerRunning && timeLeftInSeconds == 0 -> "∞"
                            else -> formatTime(timeLeftInSeconds)
                        },
                        fontSize = if (isOvertime) (diameter.value * 0.135f).sp else (diameter.value * 0.155f).sp,
                        maxLines = 1,
                        softWrap = false,
                        color = when {
                            isPreparing -> prepColor
                            isOvertime -> overtimeColor
                            isAmbient -> Color.White
                            else -> MaterialTheme.colorScheme.onBackground
                        },
                        style = TextStyle(
                            fontFeatureSettings = "tnum",
                            fontWeight = FontWeight.Light
                        )
                    )
                }

                // Pulsante circolare [+]
                Box(
                    modifier = Modifier.size(sideBtnSize),
                    contentAlignment = Alignment.Center
                ) {
                    if (showSideControls && !isAmbient) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(sideBtnSize)
                                .background(primaryColor.copy(alpha = 0.12f), CircleShape)
                                .border(1.dp, primaryColor.copy(alpha = 0.40f), CircleShape)
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() }
                                ) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    if (isInitialState) {
                                        onUpdateTimeSelection(if (selectedTimeInSeconds == -1) 60 else selectedTimeInSeconds + 60)
                                    } else {
                                        onSaveAndFinish(false)
                                    }
                                }
                        ) {
                            Text(
                                text = if (isInitialState) "+" else "✓",
                                fontSize = if (isInitialState) 15.sp else 12.sp,
                                fontWeight = FontWeight.Normal,
                                color = primaryColor
                            )
                        }
                    }
                }
            }

            // Fascia inferiore (Ore 6)
            val bottomPillColor = when {
                isAmbient -> Color.Transparent
                isOvertime -> overtimeColor
                isTimerRunning -> MaterialTheme.colorScheme.secondary
                else -> primaryColor
            }
            val bottomPillTextColor = when {
                isAmbient -> Color.White.copy(alpha = 0.8f)
                isOvertime -> Color.Black
                isTimerRunning -> MaterialTheme.colorScheme.onSecondary
                else -> MaterialTheme.colorScheme.onPrimary
            }
            val pillWidth = (diameter * 0.46f).coerceIn(82.dp, 104.dp)

            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Indicatore orizzontale delle 2 pagine (Timer / Impostazioni)
                if (showSettingsPage && !isAmbient) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(2) { index ->
                            val selected = currentPage == index
                            Box(
                                modifier = Modifier
                                    .size(if (selected) 5.dp else 3.5.dp)
                                    .background(
                                        if (selected) primaryColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                        CircleShape
                                    )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(width = pillWidth, height = 28.dp)
                        .then(
                            if (isAmbient) {
                                Modifier.border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                            } else {
                                Modifier
                                    .background(bottomPillColor, RoundedCornerShape(14.dp))
                                    .border(1.dp, bottomPillColor.copy(alpha = 0.8f), RoundedCornerShape(14.dp))
                            }
                        )
                        .clickable(
                            enabled = !isAmbient,
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            if (isOvertime) {
                                onSaveAndFinish(true)
                            } else {
                                onToggleTimer()
                            }
                        }
                ) {
                    val bottomText = when {
                        isOvertime -> stringResource(R.string.button_save_extra, (overtimeSeconds / 60))
                        isTimerRunning -> stringResource(R.string.button_pause)
                        !isInitialState -> stringResource(R.string.button_resume)
                        else -> stringResource(R.string.button_start)
                    }
                    Text(
                        text = bottomText,
                        fontSize = if (isOvertime || (!isInitialState && !isTimerRunning)) 10.sp else 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        color = bottomPillTextColor
                    )
                }
            }
        }
    }
}
