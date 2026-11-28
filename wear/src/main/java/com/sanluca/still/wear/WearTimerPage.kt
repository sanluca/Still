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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanluca.still.core.formatTime

@Immutable
internal data class WearTimerPageUiState(
    val isAmbient: Boolean,
    val isTimerRunning: Boolean,
    val isPreparing: Boolean,
    val isOvertime: Boolean,
    val isInitialState: Boolean,
    val selectedTimeInSeconds: Int,
    val timeLeftInSeconds: Int,
    val prepTimeSeconds: Int,
    val prepTimeLeftSeconds: Int,
    val overtimeSeconds: Int,
    val progress: Float,
    val currentClockText: String,
    val overtimeColor: Color,
    val prepColor: Color,
    val showSettingsPage: Boolean,
    val currentPage: Int
)

@Immutable
internal data class WearTimerPageActions(
    val onUpdateTimeSelection: (Int) -> Unit,
    val onResetTimer: () -> Unit,
    val onSaveAndFinish: (Boolean) -> Unit,
    val onToggleTimer: () -> Unit
)

@Composable
internal fun WearTimerPage(
    uiState: WearTimerPageUiState,
    actions: WearTimerPageActions,
    modifier: Modifier = Modifier
) {
    val (
        isAmbient,
        isTimerRunning,
        isPreparing,
        isOvertime,
        isInitialState,
        selectedTimeInSeconds,
        timeLeftInSeconds,
        prepTimeSeconds,
        prepTimeLeftSeconds,
        overtimeSeconds,
        progress,
        currentClockText,
        overtimeColor,
        prepColor,
        showSettingsPage,
        currentPage
    ) = uiState
    val (
        onUpdateTimeSelection,
        onResetTimer,
        onSaveAndFinish,
        onToggleTimer
    ) = actions
    val haptic = LocalHapticFeedback.current
    val fontScale = LocalDensity.current.fontScale

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val diameter = minOf(maxWidth, maxHeight)
        val isPausedState = !isInitialState && !isTimerRunning && !isOvertime
        val showSideControls = isInitialState || isPausedState || isOvertime
        val primaryColor = MaterialTheme.colorScheme.primary
        val tertiaryColor = MaterialTheme.colorScheme.tertiary

        // 1. CANVAS PERIMETRALE PULITO (Glow radiale soffuso + Traccia di fondo + Arco di avanzamento)
        WearTimerPerimeterCanvas(
            isAmbient = isAmbient,
            isTimerRunning = isTimerRunning,
            isPreparing = isPreparing,
            isOvertime = isOvertime,
            selectedTimeInSeconds = selectedTimeInSeconds,
            prepTimeSeconds = prepTimeSeconds,
            prepTimeLeftSeconds = prepTimeLeftSeconds,
            progress = progress,
            primaryColor = primaryColor,
            tertiaryColor = tertiaryColor,
            overtimeColor = overtimeColor,
            prepColor = prepColor
        )

        // 2. CONTENITORE INTERNO IN SAFE-ZONE CIRCOLARE REALE (>=14% verticale, >=14% orizzontale su ore 12/6, equatore ottimizzato)
        val verticalSafePadding = diameter * 0.14f
        val equatorHorizontalPadding = diameter * 0.065f
        val polarExtraHorizontalPadding = diameter * 0.08f
        val compactTopRow = fontScale > 1.25f && diameter < 210.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = equatorHorizontalPadding, vertical = verticalSafePadding),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            WearTimerTopHeader(
                isAmbient = isAmbient,
                isPreparing = isPreparing,
                isOvertime = isOvertime,
                compactTopRow = compactTopRow,
                currentClockText = currentClockText,
                prepColor = prepColor,
                overtimeColor = overtimeColor,
                polarExtraHorizontalPadding = polarExtraHorizontalPadding
            )

            WearTimerCenterRow(
                diameter = diameter,
                equatorHorizontalPadding = equatorHorizontalPadding,
                fontScale = fontScale,
                isAmbient = isAmbient,
                isTimerRunning = isTimerRunning,
                isPreparing = isPreparing,
                isOvertime = isOvertime,
                isInitialState = isInitialState,
                showSideControls = showSideControls,
                selectedTimeInSeconds = selectedTimeInSeconds,
                timeLeftInSeconds = timeLeftInSeconds,
                prepTimeLeftSeconds = prepTimeLeftSeconds,
                overtimeSeconds = overtimeSeconds,
                primaryColor = primaryColor,
                prepColor = prepColor,
                overtimeColor = overtimeColor,
                onLeftButtonClick = {
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
                },
                onRightButtonClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    if (isInitialState) {
                        onUpdateTimeSelection(if (selectedTimeInSeconds == -1) 60 else selectedTimeInSeconds + 60)
                    } else {
                        onSaveAndFinish(false)
                    }
                }
            )

            WearTimerBottomControls(
                diameter = diameter,
                polarExtraHorizontalPadding = polarExtraHorizontalPadding,
                isAmbient = isAmbient,
                isTimerRunning = isTimerRunning,
                isOvertime = isOvertime,
                isInitialState = isInitialState,
                overtimeSeconds = overtimeSeconds,
                primaryColor = primaryColor,
                overtimeColor = overtimeColor,
                showSettingsPage = showSettingsPage,
                currentPage = currentPage,
                onActionClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    if (isOvertime) {
                        onSaveAndFinish(true)
                    } else {
                        onToggleTimer()
                    }
                }
            )
        }
    }
}

@Composable
private fun WearTimerPerimeterCanvas(
    isAmbient: Boolean,
    isTimerRunning: Boolean,
    isPreparing: Boolean,
    isOvertime: Boolean,
    selectedTimeInSeconds: Int,
    prepTimeSeconds: Int,
    prepTimeLeftSeconds: Int,
    progress: Float,
    primaryColor: Color,
    tertiaryColor: Color,
    overtimeColor: Color,
    prepColor: Color
) {
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

    val ringTrackColor = if (isAmbient) Color.Transparent else primaryColor.copy(alpha = 0.14f)

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
}

@Composable
private fun WearTimerTopHeader(
    isAmbient: Boolean,
    isPreparing: Boolean,
    isOvertime: Boolean,
    compactTopRow: Boolean,
    currentClockText: String,
    prepColor: Color,
    overtimeColor: Color,
    polarExtraHorizontalPadding: Dp
) {
    val statusText = when {
        isPreparing -> stringResource(R.string.text_prepare)
        isOvertime -> stringResource(R.string.status_extra)
        else -> stringResource(R.string.status_still)
    }
    val statusColor = when {
        isAmbient -> Color.White.copy(alpha = 0.7f)
        isPreparing -> prepColor
        isOvertime -> overtimeColor
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
    }
    val clockColor = if (isAmbient) {
        Color.White.copy(alpha = 0.6f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = polarExtraHorizontalPadding),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (compactTopRow) {
            Text(
                text = "$currentClockText · $statusText",
                fontSize = 11.sp,
                lineHeight = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                color = statusColor,
                style = TextStyle(
                    fontFeatureSettings = "tnum",
                    fontWeight = FontWeight.Normal
                )
            )
        } else {
            Text(
                text = currentClockText,
                fontSize = 11.sp,
                lineHeight = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = clockColor,
                style = TextStyle(
                    fontFeatureSettings = "tnum",
                    fontWeight = FontWeight.Normal
                )
            )
            Text(
                text = statusText,
                fontSize = 11.sp,
                lineHeight = 13.sp,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = statusColor
            )
        }
    }
}

@Composable
private fun WearTimerCenterRow(
    diameter: Dp,
    equatorHorizontalPadding: Dp,
    fontScale: Float,
    isAmbient: Boolean,
    isTimerRunning: Boolean,
    isPreparing: Boolean,
    isOvertime: Boolean,
    isInitialState: Boolean,
    showSideControls: Boolean,
    selectedTimeInSeconds: Int,
    timeLeftInSeconds: Int,
    prepTimeLeftSeconds: Int,
    overtimeSeconds: Int,
    primaryColor: Color,
    prepColor: Color,
    overtimeColor: Color,
    onLeftButtonClick: () -> Unit,
    onRightButtonClick: () -> Unit
) {
    // Touch target >= 36 dp mantenendo la grafica circolare compatta ed equilibrata
    val sideBtnTouchSize = 36.dp
    val sideBtnVisualSize = (diameter * 0.15f).coerceIn(28.dp, 34.dp)
    val canDecrease = selectedTimeInSeconds != -1

    val ambientTimerText = when {
        isPreparing -> stringResource(R.string.text_prepare)
        isOvertime -> stringResource(R.string.ambient_extra_minutes_format, (overtimeSeconds / 60))
        selectedTimeInSeconds == -1 -> "∞"
        else -> stringResource(
            R.string.ambient_minutes_format,
            ((timeLeftInSeconds + 59) / 60).coerceAtLeast(1)
        )
    }
    val interactiveTimerText = when {
        isPreparing -> "${prepTimeLeftSeconds}s"
        isOvertime -> "+${formatTime(overtimeSeconds)}"
        selectedTimeInSeconds == -1 && !isTimerRunning && timeLeftInSeconds == 0 -> "∞"
        else -> formatTime(timeLeftInSeconds)
    }
    val displayTimerText = if (isAmbient) ambientTimerText else interactiveTimerText

    val equatorWidthDp = (diameter - equatorHorizontalPadding * 2).value
    val timerSlotWidthDp = if (showSideControls && !isAmbient) {
        (equatorWidthDp - sideBtnTouchSize.value * 2f - 6f).coerceAtLeast(64f)
    } else {
        (equatorWidthDp - 16f).coerceAtLeast(80f)
    }
    val baseTimerSp = when {
        isAmbient && selectedTimeInSeconds != -1 -> diameter.value * 0.125f
        isOvertime -> diameter.value * 0.135f
        else -> diameter.value * 0.155f
    }
    val effectiveChars = displayTimerText.length.coerceAtLeast(3).toFloat()
    val maxFittingSp = (timerSlotWidthDp / (effectiveChars * 0.64f * fontScale)).coerceAtLeast(12f)
    val maxVerticalSp = ((diameter.value * 0.22f) / fontScale).coerceAtLeast(13f)
    val adaptiveTimerSp = minOf(baseTimerSp, maxFittingSp, maxVerticalSp)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (showSideControls && !isAmbient) {
            val leftEnabled = !isInitialState || canDecrease
            val leftIconSp = ((if (isInitialState) 15f else 12f) / fontScale.coerceAtLeast(1f)).coerceAtLeast(11f)
            WearTimerSideCircleButton(
                symbol = if (isInitialState) "−" else "✕",
                iconSp = leftIconSp,
                touchSize = sideBtnTouchSize,
                visualSize = sideBtnVisualSize,
                primaryColor = primaryColor,
                enabled = leftEnabled,
                onClick = onLeftButtonClick
            )
        }

        // Testo del timer adattivo (nessuna collisione con fontScale massimo)
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 3.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = displayTimerText,
                fontSize = adaptiveTimerSp.sp,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
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

        if (showSideControls && !isAmbient) {
            val rightIconSp = ((if (isInitialState) 15f else 13f) / fontScale.coerceAtLeast(1f)).coerceAtLeast(11f)
            WearTimerSideCircleButton(
                symbol = if (isInitialState) "+" else "✓",
                iconSp = rightIconSp,
                touchSize = sideBtnTouchSize,
                visualSize = sideBtnVisualSize,
                primaryColor = primaryColor,
                enabled = true,
                onClick = onRightButtonClick
            )
        }
    }
}

@Composable
private fun WearTimerSideCircleButton(
    symbol: String,
    iconSp: Float,
    touchSize: Dp,
    visualSize: Dp,
    primaryColor: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(touchSize)
            .clip(CircleShape)
            .clickable(
                enabled = enabled,
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick
            )
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(visualSize)
                .background(primaryColor.copy(alpha = if (enabled) 0.12f else 0.04f), CircleShape)
                .border(1.dp, primaryColor.copy(alpha = if (enabled) 0.40f else 0.15f), CircleShape)
        ) {
            Text(
                text = symbol,
                fontSize = iconSp.sp,
                fontWeight = FontWeight.Normal,
                color = if (enabled) primaryColor else primaryColor.copy(alpha = 0.3f)
            )
        }
    }
}

@Composable
private fun WearTimerBottomControls(
    diameter: Dp,
    polarExtraHorizontalPadding: Dp,
    isAmbient: Boolean,
    isTimerRunning: Boolean,
    isOvertime: Boolean,
    isInitialState: Boolean,
    overtimeSeconds: Int,
    primaryColor: Color,
    overtimeColor: Color,
    showSettingsPage: Boolean,
    currentPage: Int,
    onActionClick: () -> Unit
) {
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = polarExtraHorizontalPadding),
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
            Spacer(modifier = Modifier.height(3.dp))
        }

        if (isAmbient) {
            // In Ambient Mode evita di disegnare il bordo del pulsante disabilitato
            Spacer(modifier = Modifier.height(36.dp))
        } else {
            val bottomText = when {
                isOvertime -> stringResource(R.string.button_save_extra, (overtimeSeconds / 60))
                isTimerRunning -> stringResource(R.string.button_pause)
                !isInitialState -> stringResource(R.string.button_resume)
                else -> stringResource(R.string.button_start)
            }
            val pillShape = RoundedCornerShape(50)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .widthIn(min = 84.dp, max = diameter * 0.68f)
                    .heightIn(min = 36.dp)
                    .clip(pillShape)
                    .background(bottomPillColor, pillShape)
                    .border(1.dp, bottomPillColor.copy(alpha = 0.8f), pillShape)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onActionClick
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = bottomText,
                    fontSize = 11.sp,
                    lineHeight = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    color = bottomPillTextColor
                )
            }
        }
    }
}
