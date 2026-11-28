package com.sanluca.still

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.sanluca.still.ui.theme.LocalStillColors
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.launch

@Composable
internal fun ZenWheelTimerGraphic(
    isRunning: Boolean,
    isInitialState: Boolean,
    isOvertime: Boolean,
    isPreparing: Boolean,
    modifier: Modifier = Modifier,
    progress: Float = 1f,
    isInfiniteMode: Boolean = false,
    onTouchToStart: (() -> Unit)? = null
) {
    val stillColors = LocalStillColors.current
    val tertiaryColor = MaterialTheme.colorScheme.tertiary

    // Loop continuo limitato a ~40 fps (attivo solo quando il timer scorre)
    var accumulatedSeconds by remember { mutableFloatStateOf(0f) }
    var pulseSeconds by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(isInitialState, isPreparing) {
        if (isInitialState || !isPreparing) {
            accumulatedSeconds = 0f
        }
    }
    LaunchedEffect(isRunning) {
        if (isRunning) {
            var lastFrameNanos = -1L
            while (true) {
                androidx.compose.runtime.withFrameNanos { frameNanos ->
                    if (lastFrameNanos < 0L) {
                        lastFrameNanos = frameNanos
                    } else if (frameNanos - lastFrameNanos >= 25_000_000L) {
                        val deltaSec = ((frameNanos - lastFrameNanos) / 1_000_000_000f).coerceIn(0f, 0.25f)
                        lastFrameNanos = frameNanos
                        accumulatedSeconds = (accumulatedSeconds + deltaSec) % 60f
                        pulseSeconds = (pulseSeconds + deltaSec) % 3600f
                    }
                }
            }
        }
    }

    // Accensione morbida e prima onda concentrica all'avvio (tocco sul cerchio o pulsante Inizia)
    val awakeAnimatable = remember { Animatable(if (isInitialState) 0f else 1f) }
    val startWaveAnimatable = remember { Animatable(0f) }
    var wasInitialState by remember { mutableStateOf(isInitialState) }
    val touchGlow = remember { Animatable(0f) }

    LaunchedEffect(isInitialState) {
        if (wasInitialState && !isInitialState) {
            launch {
                touchGlow.animateTo(0f, tween(durationMillis = 450, easing = FastOutSlowInEasing))
            }
            launch {
                startWaveAnimatable.snapTo(0.01f)
                startWaveAnimatable.animateTo(1f, tween(durationMillis = 1250, easing = FastOutSlowInEasing))
                startWaveAnimatable.snapTo(0f)
            }
            awakeAnimatable.animateTo(1f, tween(durationMillis = 650, easing = FastOutSlowInEasing))
            wasInitialState = false
        } else {
            awakeAnimatable.snapTo(if (isInitialState) 0f else 1f)
            startWaveAnimatable.snapTo(0f)
            touchGlow.snapTo(0f)
            wasInitialState = isInitialState
        }
    }

    // Progresso morbido dell'arco della sessione
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "breath_ring_progress"
    )

    val activeGlowBoost by animateFloatAsState(
        targetValue = when {
            isInitialState -> 0.35f
            isOvertime -> 1.15f
            isPreparing -> 0.85f
            isRunning -> 1.0f
            else -> 0.65f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "breath_ring_glow"
    )

    // A riposo: un tocco o una carezza sullo stagno genera la prima onda e avvia il timer
    val currentOnTouchToStart by rememberUpdatedState(onTouchToStart)
    val touchScope = rememberCoroutineScope()
    val gestureModifier = if (isInitialState && onTouchToStart != null) {
        Modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                down.consume()
                touchScope.launch {
                    touchGlow.snapTo(0.55f)
                }
                var maxMove = 0f
                var completed = false
                var finished = false
                while (!finished) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull() ?: break
                    if (change.isConsumed) {
                        finished = true
                    } else if (!change.pressed) {
                        change.consume()
                        completed = true
                        finished = true
                    } else {
                        val d = (change.position - down.position).getDistance()
                        if (d > maxMove) maxMove = d
                        change.consume()
                        if (maxMove >= 28.dp.toPx()) {
                            completed = true
                            finished = true
                        }
                    }
                }
                if (completed) {
                    currentOnTouchToStart?.invoke()
                } else {
                    touchScope.launch {
                        touchGlow.animateTo(0f, tween(durationMillis = 300, easing = FastOutSlowInEasing))
                    }
                }
            }
        }
    } else {
        Modifier
    }

    // 4 micro-particelle di luce pre-allocate che galleggiano attorno alla goccia (angleOffset, speed, radialNorm, radiusDp)
    val moteSeeds = remember {
        floatArrayOf(
            -0.35f, 0.22f, -0.8f, 1.35f,
             0.28f, 0.17f,  0.9f, 1.10f,
            -0.14f, 0.26f,  0.5f, 1.55f,
             0.42f, 0.19f, -0.6f, 1.20f
        )
    }

    val wheelRotationPhase = (accumulatedSeconds / 60f).coerceIn(0f, 1f)

    Canvas(modifier = modifier.then(gestureModifier)) {
        val minDim = size.minDimension
        val c = center
        val awake = if (isInitialState) touchGlow.value else awakeAnimatable.value

        // 1. RESPIRO CALMO (ciclo naturale lento di ~9s)
        val breathWave = if (isInitialState) 0f else sin(pulseSeconds * (2.0 * Math.PI / 9.0)).toFloat()
        val breathScale = 1f + 0.016f * breathWave * awake
        val baseRingRadius = minDim * 0.422f
        val ringRadius = baseRingRadius * breathScale
        val pondRadius = ringRadius * 0.96f

        val mainLightColor = when {
            isOvertime -> stillColors.overtime
            isPreparing -> tertiaryColor
            else -> stillColors.sandLight
        }
        val deepLightColor = when {
            isOvertime -> stillColors.sandLight
            isPreparing -> stillColors.glassOutline
            else -> stillColors.sandDeep
        }

        // 2. STAGNO CALMO INTERNO: specchio d'acqua profondo dietro ai numeri + alone di respiro
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0.00f to stillColors.skyBottom.copy(alpha = 0.80f),
                    0.68f to stillColors.skyTop.copy(alpha = 0.46f),
                    0.92f to mainLightColor.copy(alpha = (0.04f + 0.05f * (0.5f + 0.5f * breathWave)) * awake),
                    1.00f to Color.Transparent
                ),
                center = c,
                radius = pondRadius
            ),
            radius = pondRadius,
            center = c
        )

        // 3. ONDE CONCENTRICHE D'ACQUA (Ripples): nascono da un puntino al centro esatto e si allargano lentamente verso il bordo
        val rippleCount = 2
        val ripplePeriodSec = 22.0f
        for (r in 0 until rippleCount) {
            val phase = if (isInitialState) {
                -1f
            } else {
                ((pulseSeconds / ripplePeriodSec) + r.toFloat() / rippleCount) % 1f
            }
            if (phase >= 0f) {
                // Parte da un puntino (2.dp) al centro esatto e si espande con rallentamento morbido fino al bordo dello stagno
                val eased = 1f - (1f - phase) * (1f - phase)
                val minDotR = 2.0.dp.toPx()
                val rippleR = minDotR + (pondRadius * 0.96f - minDotR) * eased
                val fadeEnvelope = sin(phase * Math.PI).toFloat()
                // Nei primissimi istanti (phase < 0.08) si vede anche il puntino pieno della goccia che cade al centro
                if (phase < 0.08f) {
                    val dropAlpha = (1f - phase / 0.08f) * 0.35f * awake
                    drawCircle(
                        color = mainLightColor.copy(alpha = dropAlpha),
                        radius = (3.2f * (1f - 0.4f * (phase / 0.08f))).dp.toPx(),
                        center = c,
                        blendMode = BlendMode.Plus
                    )
                }
                val rippleAlpha = (0.22f * fadeEnvelope * (1f - 0.35f * phase) * awake).coerceIn(0f, 0.24f)
                if (rippleAlpha > 0.005f) {
                    drawCircle(
                        color = mainLightColor.copy(alpha = rippleAlpha),
                        radius = rippleR,
                        center = c,
                        style = Stroke(width = (1.8f - 1.0f * phase).dp.toPx())
                    )
                }
            }
        }

        // Onda iniziale generata dal tocco / avvio della meditazione (parte anch'essa da un puntino al centro)
        val startWave = startWaveAnimatable.value
        if (startWave in 0.01f..0.99f) {
            val eased = 1f - (1f - startWave) * (1f - startWave)
            val minDotR = 2.0.dp.toPx()
            val waveR = minDotR + (pondRadius - minDotR) * eased
            val waveAlpha = (1f - startWave) * 0.45f
            drawCircle(
                color = mainLightColor.copy(alpha = waveAlpha),
                radius = waveR,
                center = c,
                style = Stroke(width = (2.4f * (1f - 0.5f * startWave)).dp.toPx()),
                blendMode = BlendMode.Plus
            )
        }

        // 4. ANELLO DI FONDO SOTTILE E PULITO (senza legno né ingranaggi)
        val breathHaloWidth = (10f + 6f * (0.5f + 0.5f * breathWave)).dp.toPx()
        if (awake > 0.02f) {
            drawCircle(
                color = mainLightColor.copy(alpha = (0.06f + 0.05f * (0.5f + 0.5f * breathWave)) * awake * activeGlowBoost),
                radius = ringRadius,
                center = c,
                style = Stroke(width = breathHaloWidth),
                blendMode = BlendMode.Plus
            )
        }

        // Traccia sottile dell'intero cerchio (sempre visibile, quieta a riposo)
        drawCircle(
            color = stillColors.glassOutline.copy(alpha = 0.20f + 0.10f * awake),
            radius = ringRadius,
            center = c,
            style = Stroke(width = 2.0.dp.toPx())
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.08f + 0.06f * awake),
            radius = ringRadius - 3.5.dp.toPx(),
            center = c,
            style = Stroke(width = 0.8.dp.toPx())
        )

        // 12 micro-punti cardinali leggerissimi come gocce di rugiada sul bordo esterno
        val dotRadiusOffset = ringRadius + 6.5.dp.toPx()
        for (i in 0 until 12) {
            val angleRad = Math.toRadians((-90.0 + i * 30.0))
            val isCardinal = i % 3 == 0
            val dotPos = Offset(
                x = c.x + dotRadiusOffset * cos(angleRad).toFloat(),
                y = c.y + dotRadiusOffset * sin(angleRad).toFloat()
            )
            drawCircle(
                color = if (isCardinal) {
                    mainLightColor.copy(alpha = 0.28f + 0.24f * awake)
                } else {
                    Color.White.copy(alpha = 0.12f + 0.10f * awake)
                },
                radius = if (isCardinal) 1.5.dp.toPx() else 0.9.dp.toPx(),
                center = dotPos
            )
        }

        // 5. ARCO DEL TEMPO E GOCCIA DI LUCE CALDA
        val arcTopLeft = Offset(c.x - ringRadius, c.y - ringRadius)
        val arcSize = Size(ringRadius * 2f, ringRadius * 2f)

        // In tutte le fasi la goccia si muove SEMPRE in senso orario a partire dalle ore 12 (-90°):
        // - in modalità con durata: l'arco si traccia in senso orario da 0° a 360° man mano che il tempo trascorre (1f - animatedProgress)
        // - in preparazione, infinito o overtime: la goccia compie un'orbita fluida in senso orario
        val dropletAngleDeg: Float
        if (isInitialState) {
            dropletAngleDeg = -90f
        } else if (isPreparing || isInfiniteMode) {
            dropletAngleDeg = -90f + wheelRotationPhase * 360f
            // Scia luminosa morbida dietro la goccia in orbita
            val trailSteps = 18
            val stepDeg = 3.2f
            for (s in 0 until trailSteps) {
                val f = 1f - (s.toFloat() / trailSteps)
                val segAlpha = 0.34f * f * f * awake
                val segWidth = (3.8f * (0.35f + 0.65f * f)).dp.toPx()
                drawArc(
                    color = mainLightColor.copy(alpha = segAlpha),
                    startAngle = dropletAngleDeg - (s + 1) * stepDeg,
                    sweepAngle = stepDeg + 0.8f,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = segWidth, cap = StrokeCap.Round),
                    blendMode = BlendMode.Plus
                )
            }
        } else if (isOvertime) {
            // In overtime il cerchio è completo e luminoso, e la goccia continua a orbitare in senso orario
            drawCircle(
                color = stillColors.overtime.copy(alpha = 0.55f * awake),
                radius = ringRadius,
                center = c,
                style = Stroke(width = 3.6.dp.toPx())
            )
            dropletAngleDeg = -90f + wheelRotationPhase * 360f
        } else {
            val elapsedFraction = (1f - animatedProgress).coerceIn(0f, 1f)
            val sweep = (360f * elapsedFraction).coerceIn(0f, 360f)
            if (sweep > 0.5f && awake > 0.01f) {
                // Alone morbido dell'arco percorso in senso orario
                drawArc(
                    color = deepLightColor.copy(alpha = 0.24f * awake),
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round),
                    blendMode = BlendMode.Plus
                )
                // Tratto luminoso principale dell'arco del tempo
                drawArc(
                    color = mainLightColor.copy(alpha = 0.82f * awake),
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = 3.2.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            dropletAngleDeg = -90f + sweep
        }

        // 6. GOCCIA DI LUCE SULLA PUNTA + MICRO-PARTICELLE CHE GALLEGGIANO
        val dropletRad = Math.toRadians(dropletAngleDeg.toDouble())
        val dropletCenter = Offset(
            x = c.x + ringRadius * cos(dropletRad).toFloat(),
            y = c.y + ringRadius * sin(dropletRad).toFloat()
        )
        val dropletPulse = 1f + 0.08f * sin((pulseSeconds * 2.6f).toDouble()).toFloat()

        if (awake > 0.01f) {
            // Micro-particelle lentissime attorno alla goccia di luce
            for (m in 0 until 4) {
                val b = m * 4
                val phase = (pulseSeconds * moteSeeds[b + 1] + m * 0.25f) % 1f
                val moteAlpha = sin(phase * Math.PI).toFloat() * 0.55f * awake
                if (moteAlpha > 0.02f) {
                    val angleOff = moteSeeds[b] * (0.4f + 0.6f * phase)
                    val radialOff = moteSeeds[b + 2] * 9.dp.toPx() * sin(phase * Math.PI).toFloat()
                    val mRad = dropletRad + angleOff
                    val mPos = Offset(
                        x = c.x + (ringRadius + radialOff) * cos(mRad).toFloat(),
                        y = c.y + (ringRadius + radialOff) * sin(mRad).toFloat()
                    )
                    drawCircle(
                        color = mainLightColor.copy(alpha = moteAlpha),
                        radius = moteSeeds[b + 3].dp.toPx(),
                        center = mPos,
                        blendMode = BlendMode.Plus
                    )
                }
            }

            // Alone esterno ampio e caldo della goccia di luce
            val outerGlowR = 18.dp.toPx() * dropletPulse * (0.5f + 0.5f * awake)
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0.00f to mainLightColor.copy(alpha = 0.62f * awake),
                        0.40f to deepLightColor.copy(alpha = 0.30f * awake),
                        0.78f to deepLightColor.copy(alpha = 0.08f * awake),
                        1.00f to Color.Transparent
                    ),
                    center = dropletCenter,
                    radius = outerGlowR
                ),
                radius = outerGlowR,
                center = dropletCenter,
                blendMode = BlendMode.Plus
            )

            // Nucleo luminoso della goccia
            val coreR = 7.5.dp.toPx() * dropletPulse * (0.6f + 0.4f * awake)
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0.00f to Color(0xFFFFFBF0).copy(alpha = 0.96f * awake),
                        0.42f to mainLightColor.copy(alpha = 0.85f * awake),
                        1.00f to Color.Transparent
                    ),
                    center = dropletCenter,
                    radius = coreR
                ),
                radius = coreR,
                center = dropletCenter,
                blendMode = BlendMode.Plus
            )
        } else {
            // A riposo: una piccola perla quieta alle ore 12 pronta a svegliarsi al tocco
            drawCircle(
                color = stillColors.glassOutline.copy(alpha = 0.55f),
                radius = 3.2.dp.toPx(),
                center = dropletCenter
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.45f),
                radius = 1.4.dp.toPx(),
                center = dropletCenter
            )
        }
    }
}
