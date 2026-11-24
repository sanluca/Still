package com.sanluca.still

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.sanluca.still.ui.theme.LocalStillColors
import kotlin.math.sign
import kotlinx.coroutines.launch

@Composable
fun AutumnHourglassGraphic(
    topSandFraction: Float,
    isFlowing: Boolean,
    isOvertime: Boolean,
    isPreparing: Boolean,
    modifier: Modifier = Modifier,
    isInitialState: Boolean = false,
    onFlipToStart: (() -> Unit)? = null
) {
    val stillColors = LocalStillColors.current

    // 1A. Inerzia fisica: anima le variazioni improvvise di progress (es. -1m / +1m) con molla morbida
    val animatedTopSand by animateFloatAsState(
        targetValue = topSandFraction.coerceIn(0f, 1f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "hourglass_sand_inertia"
    )

    // 3. Pool pre-allocato (FloatArray) di 40 granelli indipendenti (zero allocazioni dentro DrawScope)
    // Struttura per granello (8 float ciascuno = 320 float totali):
    // [0] progress: < 0f inattivo, 0f..1f caduta in aria, 1f..1.35f scivolamento lungo il pendio del cono
    // [1] fallDuration: durata di caduta tra 1.05s e 1.55s
    // [2] neckOffsetNorm: piccolo offset orizzontale iniziale nel collo (-1f..+1f)
    // [3] fanDirectionNorm: apertura a ventaglio/cono verso l'esterno (-1f..+1f)
    // [4] swayPhase: fase individuale dell'oscillazione sinusoidale (corrente d'aria)
    // [5] swayFreq: frequenza individuale dell'oscillazione sinusoidale
    // [6] radiusDp: raggio individuale tra 1.0.dp e 2.2.dp
    // [7] baseAlpha: opacità individuale (i granelli >= 0.88f usano BlendMode.Plus)
    val grainCount = 40
    val stride = 8
    val grainPool = remember {
        FloatArray(grainCount * stride).apply {
            for (i in 0 until grainCount) {
                val b = i * stride
                val h1 = ((i * 73 + 19) % 100) / 100f
                val h2 = ((i * 41 + 53) % 100) / 100f
                val h3 = ((i * 89 + 11) % 100) / 100f
                val h4 = ((i * 31 + 67) % 100) / 100f
                this[b + 0] = -1f // parte inattivo
                this[b + 1] = 1.05f + h1 * 0.50f // 1.05s .. 1.55s
                this[b + 2] = (h2 - 0.5f) * 1.7f // offset nel collo
                val side = if (i % 2 == 0) 1f else -1f
                this[b + 3] = side * (0.12f + h3 * 0.88f) // apertura a ventaglio/cono
                this[b + 4] = h4 * 6.2831855f // fase
                this[b + 5] = 2.0f + h1 * 2.4f // frequenza corrente d'aria
                this[b + 6] = 1.0f + h2 * 1.2f // raggio 1.0.dp .. 2.2.dp
                this[b + 7] = 0.56f + h3 * 0.42f // opacità
            }
        }
    }

    // Stato per tenere vivo il loop finché ci sono granelli ancora in aria dopo la pausa
    // Animazione di capovolgimento a 180° all'avvio (da stato a riposo con sabbia sul fondo)
    val flipAnimatable = remember { Animatable(0f) }
    var wasInitialState by remember { mutableStateOf(isInitialState) }
    val dragAngle = remember { Animatable(0f) }

    LaunchedEffect(isInitialState) {
        if (wasInitialState && !isInitialState) {
            launch {
                dragAngle.animateTo(0f, tween(durationMillis = 650, easing = FastOutSlowInEasing))
            }
            flipAnimatable.animateTo(180f, tween(durationMillis = 650, easing = FastOutSlowInEasing))
            wasInitialState = false
            flipAnimatable.snapTo(0f)
        } else {
            flipAnimatable.snapTo(0f)
            wasInitialState = isInitialState
        }
    }

    // Il rivolo di sabbia resta sospeso mentre la clessidra si sta capovolgendo
    val isFlipSuspended by remember(isInitialState) {
        derivedStateOf {
            (wasInitialState && !isInitialState) || flipAnimatable.value in 1f..179f
        }
    }
    val effectiveFlowing = isFlowing && !isPreparing && !isFlipSuspended
    var hasGrainsInFlight by remember { mutableStateOf(false) }
    var frameTick by remember { mutableFloatStateOf(0f) }
    val currentEffectiveFlowing by rememberUpdatedState(effectiveFlowing)
    val shouldRunGrainLoop = effectiveFlowing || hasGrainsInFlight

    // 1B. Loop withFrameNanos limitato a ~40 fps (salta il frame se frameNanos - lastFrameNanos < 25_000_000L)
    LaunchedEffect(shouldRunGrainLoop) {
        if (!shouldRunGrainLoop) return@LaunchedEffect
        var lastFrameNanos = -1L
        var spawnAccumulator = 0f
        var nextSpawnIndex = 0

        while (currentEffectiveFlowing || hasGrainsInFlight) {
            androidx.compose.runtime.withFrameNanos { frameNanos ->
                if (lastFrameNanos < 0L) {
                    lastFrameNanos = frameNanos
                } else if (frameNanos - lastFrameNanos >= 25_000_000L) {
                    val dt = ((frameNanos - lastFrameNanos).coerceAtMost(100_000_000L)).toFloat() / 1_000_000_000f
                    lastFrameNanos = frameNanos

                    // Quando currentEffectiveFlowing == true emette nuovi granelli dal collo; in pausa smette di emetterne
                    if (currentEffectiveFlowing) {
                        spawnAccumulator += dt * 24.0f // ~24 granelli/sec per mantenere il rivolo denso con caduta più rapida
                        while (spawnAccumulator >= 1f) {
                            spawnAccumulator -= 1f
                            for (attempt in 0 until grainCount) {
                                val candidate = (nextSpawnIndex + attempt) % grainCount
                                val base = candidate * stride
                                if (grainPool[base] < 0f) {
                                    grainPool[base] = 0f
                                    nextSpawnIndex = (candidate + 1) % grainCount
                                    break
                                }
                            }
                        }
                    }

                    var anyActive = false
                    for (i in 0 until grainCount) {
                        val base = i * stride
                        val p = grainPool[base]
                        if (p >= 0f) {
                            val fallDur = grainPool[base + 1]
                            val nextP = if (p < 1f) {
                                p + dt / fallDur
                            } else {
                                // Fase di scivolamento lungo il pendio del cono inferiore (~0.30s)
                                p + dt * 1.15f
                            }
                            if (nextP >= 1.35f) {
                                grainPool[base] = -1f // ricicla il granello
                            } else {
                                grainPool[base] = nextP
                                anyActive = true
                            }
                        }
                    }
                    hasGrainsInFlight = anyActive
                    frameTick = (frameTick + dt) % 3600f
                }
            }
        }
    }

    val topSpeckOffsets = remember {
        floatArrayOf(
            -0.22f, 0.25f,  0.18f, 0.32f, -0.10f, 0.48f,  0.26f, 0.55f,
            -0.28f, 0.62f,  0.06f, 0.70f, -0.14f, 0.78f,  0.15f, 0.85f
        )
    }
    val bottomSpeckOffsets = remember {
        floatArrayOf(
            -0.08f, 0.22f,  0.12f, 0.28f, -0.20f, 0.42f,  0.22f, 0.48f,
            -0.14f, 0.58f,  0.05f, 0.66f, -0.26f, 0.74f,  0.19f, 0.80f,
            -0.06f, 0.88f,  0.28f, 0.90f
        )
    }

    // Path pre-allocati per evitare allocazioni nel DrawScope
    val topBulbPath = remember { Path() }
    val bottomBulbPath = remember { Path() }
    val topSandPath = remember { Path() }
    val moundPath = remember { Path() }
    val ridgePath = remember { Path() }
    val topSpecularHighlight = remember { Path() }
    val bottomSpecularHighlight = remember { Path() }
    val rightRimHighlightTop = remember { Path() }
    val rightRimHighlightBottom = remember { Path() }
    val innerArcTopPath = remember { Path() }
    val innerArcBottomPath = remember { Path() }

    // Gesto manuale per girare la clessidra quando è a riposo
    val currentOnFlipToStart by rememberUpdatedState(onFlipToStart)
    val dragScope = rememberCoroutineScope()
    val gestureModifier = if (isInitialState && onFlipToStart != null) {
        Modifier.pointerInput(Unit) {
            val thresholdPx = 40.dp.toPx()
            val maxTiltDeg = 35f
            var total = Offset.Zero
            var completed = false
            detectDragGestures(
                onDragStart = {
                    total = Offset.Zero
                    completed = false
                },
                onDragEnd = {
                    if (!completed) {
                        dragScope.launch {
                            dragAngle.animateTo(
                                0f,
                                spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium)
                            )
                        }
                    }
                },
                onDragCancel = {
                    if (!completed) {
                        dragScope.launch {
                            dragAngle.animateTo(
                                0f,
                                spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium)
                            )
                        }
                    }
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    if (!completed) {
                        total += dragAmount
                        val distance = total.getDistance()
                        val direction = if (kotlin.math.abs(total.x) >= kotlin.math.abs(total.y)) sign(total.x) else -sign(total.y)
                        val progressToThreshold = (distance / thresholdPx).coerceIn(0f, 1f)
                        val tilt = direction * progressToThreshold * maxTiltDeg
                        dragScope.launch { dragAngle.snapTo(tilt) }
                        if (distance >= thresholdPx) {
                            completed = true
                            dragScope.launch {
                                dragAngle.animateTo(
                                    0f,
                                    spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium)
                                )
                            }
                            currentOnFlipToStart?.invoke()
                        }
                    }
                }
            )
        }
    } else {
        Modifier
    }

    Canvas(modifier = modifier.then(gestureModifier)) {
        val tickTime = frameTick

        // Rotazione di capovolgimento (0..90 sabbia nel bulbo che sale, 90..180 sabbia ormai in alto) + inclinazione del gesto
        val flipValue = flipAnimatable.value
        val flipActive = (wasInitialState && !isInitialState) || flipValue > 0f
        val restingAtBottom = isInitialState || (flipActive && flipValue < 90f)
        val flipRotation = if (flipValue < 90f) flipValue else flipValue - 180f
        drawContext.canvas.save()
        drawContext.transform.rotate(degrees = flipRotation + dragAngle.value, pivot = center)
        val w = size.width
        val h = size.height
        val centerX = w / 2f
        val midY = h / 2f

        val capHeight = 12.dp.toPx()
        val innerCollarH = 4.dp.toPx()
        val glassTop = capHeight + innerCollarH
        val glassBottom = h - capHeight - innerCollarH
        val bulbHalfHeight = midY - glassTop

        val bulbShoulderX = w * 0.17f
        val bulbRightX = w * 0.83f
        val bulbMaxLeftX = w * 0.12f
        val bulbMaxRightX = w * 0.88f
        val neckHalf = 4.2.dp.toPx()

        // 1. OMBRIERA DI APPOGGIO SUL PIANO
        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.Black.copy(alpha = 0.55f),
                    Color.Black.copy(alpha = 0.20f),
                    Color.Transparent
                ),
                center = Offset(centerX, h - 2.dp.toPx()),
                radius = w * 0.46f
            ),
            topLeft = Offset(w * 0.06f, h - 9.dp.toPx()),
            size = Size(w * 0.88f, 12.dp.toPx())
        )

        // 2. COLONNINE LATERALI TORNITE 3D
        val pillarWidth = 5.5.dp.toPx()
        val leftPillarX = w * 0.09f
        val rightPillarX = w * 0.91f - pillarWidth
        val pillarTopY = capHeight * 0.7f
        val pillarHeight = h - capHeight * 1.4f

        fun drawTurnedPillar(xLeft: Float) {
            val pillarBrush = Brush.horizontalGradient(
                colors = listOf(
                    stillColors.woodFrame.copy(alpha = 0.70f),
                    Color(0xFFFFE5B4).copy(alpha = 0.65f),
                    stillColors.woodFrame,
                    stillColors.woodFrame.copy(alpha = 0.85f),
                    Color.Black.copy(alpha = 0.65f)
                ),
                startX = xLeft,
                endX = xLeft + pillarWidth
            )
            drawRoundRect(
                brush = pillarBrush,
                topLeft = Offset(xLeft, pillarTopY),
                size = Size(pillarWidth, pillarHeight),
                cornerRadius = CornerRadius(pillarWidth / 2f, pillarWidth / 2f)
            )
            val ringW = pillarWidth + 3.dp.toPx()
            val ringX = xLeft - 1.5.dp.toPx()
            val ringH = 4.dp.toPx()
            for (ry in floatArrayOf(glassTop + 4.dp.toPx(), midY - ringH / 2f, glassBottom - 4.dp.toPx() - ringH)) {
                drawRoundRect(
                    brush = pillarBrush,
                    topLeft = Offset(ringX, ry),
                    size = Size(ringW, ringH),
                    cornerRadius = CornerRadius(ringH / 2f, ringH / 2f)
                )
            }
        }
        drawTurnedPillar(leftPillarX)
        drawTurnedPillar(rightPillarX)

        // 3. PROFILO IN VETRO SOFFIATO DELLE DUE AMPOLLE
        topBulbPath.reset()
        topBulbPath.moveTo(bulbShoulderX, glassTop)
        topBulbPath.lineTo(bulbRightX, glassTop)
        topBulbPath.cubicTo(
            bulbMaxRightX, glassTop + bulbHalfHeight * 0.36f,
            bulbMaxRightX * 0.96f, glassTop + bulbHalfHeight * 0.74f,
            centerX + neckHalf, midY
        )
        topBulbPath.lineTo(centerX - neckHalf, midY)
        topBulbPath.cubicTo(
            bulbMaxLeftX * 1.04f, glassTop + bulbHalfHeight * 0.74f,
            bulbMaxLeftX, glassTop + bulbHalfHeight * 0.36f,
            bulbShoulderX, glassTop
        )
        topBulbPath.close()

        bottomBulbPath.reset()
        bottomBulbPath.moveTo(centerX - neckHalf, midY)
        bottomBulbPath.lineTo(centerX + neckHalf, midY)
        bottomBulbPath.cubicTo(
            bulbMaxRightX * 0.96f, midY + bulbHalfHeight * 0.26f,
            bulbMaxRightX, midY + bulbHalfHeight * 0.64f,
            bulbRightX, glassBottom
        )
        bottomBulbPath.lineTo(bulbShoulderX, glassBottom)
        bottomBulbPath.cubicTo(
            bulbMaxLeftX, midY + bulbHalfHeight * 0.64f,
            bulbMaxLeftX * 1.04f, midY + bulbHalfHeight * 0.26f,
            centerX - neckHalf, midY
        )
        bottomBulbPath.close()

        // 4. CORPO DEL VETRO 3D
        val glassBodyBrush = Brush.horizontalGradient(
            colors = listOf(
                stillColors.glassOutline.copy(alpha = 0.26f),
                Color.White.copy(alpha = 0.08f),
                Color.Transparent,
                stillColors.glassOutline.copy(alpha = 0.06f),
                stillColors.glassOutline.copy(alpha = 0.28f)
            ),
            startX = bulbMaxLeftX,
            endX = bulbMaxRightX
        )
        drawPath(path = topBulbPath, brush = glassBodyBrush)
        drawPath(path = bottomBulbPath, brush = glassBodyBrush)

        // 5 (Punto 2 Specifica). SABBIA CALDA E GRANULARE DEL TEMA STAGIONALE (Nessuna sfumatura fredda grigio-azzurra)
        val sandPrimaryLight = if (isOvertime) stillColors.overtime else stillColors.sandLight
        val sandPrimaryDeep = if (isOvertime) stillColors.sandLight else stillColors.sandDeep
        val sandWarmHighlight = Color(
            red = (sandPrimaryLight.red * 0.65f + 0.35f).coerceIn(0f, 1f),
            green = (sandPrimaryLight.green * 0.65f + 0.32f).coerceIn(0f, 1f),
            blue = (sandPrimaryLight.blue * 0.65f + 0.22f).coerceIn(0f, 1f),
            alpha = 1f
        )
        val sandWarmShadow = Color(
            red = (sandPrimaryDeep.red * 0.52f).coerceIn(0f, 1f),
            green = (sandPrimaryDeep.green * 0.48f).coerceIn(0f, 1f),
            blue = (sandPrimaryDeep.blue * 0.42f).coerceIn(0f, 1f),
            alpha = 1f
        )

        val sand3DBrush = Brush.horizontalGradient(
            colorStops = arrayOf(
                0.00f to sandPrimaryDeep,
                0.24f to sandWarmHighlight, // Lato illuminato caldo a sinistra
                0.52f to sandPrimaryLight,  // Centro caldo e granulare (zero grigio/azzurro)
                0.80f to sandPrimaryDeep,
                1.00f to sandWarmShadow     // Ombreggiatura morbida calda sul lato opposto alla luce
            ),
            startX = bulbMaxLeftX,
            endX = bulbMaxRightX
        )

        val effectiveTopFraction = if (restingAtBottom) 0f else animatedTopSand
        val clampedTop = effectiveTopFraction.coerceIn(0f, 1f)
        val bulbHalfSpanX = (bulbMaxRightX - bulbMaxLeftX) * 0.5f

        // 5A. Sabbia nell'ampolla superiore con concavità centrale a imbuto tramite Path.quadraticTo
        if (clampedTop > 0.004f) {
            val sandTopY = midY - bulbHalfHeight * (0.10f + 0.85f * clampedTop)
            // L'imbuto centrale si approfondisce man mano che la sabbia si svuota
            val emptyingCurve = kotlin.math.sin(clampedTop * Math.PI).toFloat()
            val funnelDepth = if (isFlowing || hasGrainsInFlight) {
                (15.dp.toPx() * emptyingCurve + 4.dp.toPx() * (1f - clampedTop)).coerceAtMost(midY - sandTopY - 2.dp.toPx()).coerceAtLeast(2.dp.toPx())
            } else {
                (5.dp.toPx() * (1f - clampedTop) * clampedTop).coerceAtLeast(1.5.dp.toPx())
            }

            topSandPath.reset()
            topSandPath.moveTo(bulbMaxLeftX - 4f, sandTopY)
            topSandPath.quadraticTo(
                centerX,
                sandTopY + funnelDepth * 1.85f,
                bulbMaxRightX + 4f,
                sandTopY
            )
            topSandPath.lineTo(bulbMaxRightX + 4f, midY + 2f)
            topSandPath.lineTo(bulbMaxLeftX - 4f, midY + 2f)
            topSandPath.close()

            clipPath(topBulbPath) {
                drawPath(path = topSandPath, brush = sand3DBrush)

                // Ombra interna calda nella conca dell'imbuto superiore
                drawOval(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            sandWarmShadow.copy(alpha = 0.55f),
                            sandPrimaryDeep.copy(alpha = 0.35f),
                            Color.Transparent
                        ),
                        center = Offset(centerX, sandTopY + funnelDepth * 0.65f),
                        radius = bulbHalfSpanX * 0.78f
                    ),
                    topLeft = Offset(bulbMaxLeftX, sandTopY - 3.dp.toPx()),
                    size = Size(bulbMaxRightX - bulbMaxLeftX, funnelDepth * 1.5f + 6.dp.toPx())
                )

                // Texture minerale granulare calda nel bulbo superiore
                for (s in topSpeckOffsets.indices step 2) {
                    val sx = centerX + topSpeckOffsets[s] * (bulbMaxRightX - bulbMaxLeftX)
                    val sy = sandTopY + funnelDepth * 0.55f + topSpeckOffsets[s + 1] * (midY - sandTopY)
                    if (sy < midY - 3.dp.toPx()) {
                        drawCircle(
                            color = if ((s / 2) % 2 == 0) sandWarmHighlight.copy(alpha = 0.55f) else sandWarmShadow.copy(alpha = 0.35f),
                            radius = 1.15.dp.toPx(),
                            center = Offset(sx, sy)
                        )
                    }
                }
            }
        }

        // 5B. Sabbia nell'ampolla inferiore: montagnetta conica centrale (~30° = tan(30°) ≈ 0.577) con Path.quadraticTo
        val bottomFraction = if (isOvertime) 1f else (1f - clampedTop).coerceIn(0f, 1f)
        val tan30 = 0.577f
        val maxConeDrop = bulbHalfSpanX * tan30
        val coneSlopeDrop = maxConeDrop * (0.35f + 0.65f * kotlin.math.sin(bottomFraction * Math.PI * 0.85f).toFloat())

        val sideWallY = if (bottomFraction > 0.004f) {
            (glassBottom - bulbHalfHeight * (bottomFraction * 0.66f)).coerceAtLeast(midY + 12.dp.toPx())
        } else {
            glassBottom
        }
        val moundPeakY = if (bottomFraction > 0.004f) {
            (sideWallY - coneSlopeDrop).coerceAtLeast(midY + 5.dp.toPx())
        } else {
            glassBottom
        }

        // Funzione che restituisce la quota Y della superficie del cono inferiore all'ascissa x
        fun coneSurfaceYAt(x: Float): Float {
            if (bottomFraction <= 0.004f) return glassBottom
            val normX = (kotlin.math.abs(x - centerX) / bulbHalfSpanX).coerceIn(0f, 1f)
            return moundPeakY + (sideWallY - moundPeakY) * (normX * normX)
        }

        if (bottomFraction > 0.004f) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        sandPrimaryLight.copy(alpha = 0.22f * bottomFraction),
                        sandPrimaryDeep.copy(alpha = 0.08f * bottomFraction),
                        Color.Transparent
                    ),
                    center = Offset(centerX, glassBottom - 6.dp.toPx()),
                    radius = w * 0.42f
                ),
                radius = w * 0.42f,
                center = Offset(centerX, glassBottom - 6.dp.toPx())
            )

            // Profilo conico inferiore modellato con Path.quadraticTo (inclinazione ~30°)
            val controlPeakY = moundPeakY - (sideWallY - moundPeakY) * 0.38f
            moundPath.reset()
            moundPath.moveTo(bulbMaxLeftX - 4f, glassBottom + 4f)
            moundPath.lineTo(bulbMaxLeftX - 4f, sideWallY)
            moundPath.quadraticTo(
                centerX,
                controlPeakY,
                bulbMaxRightX + 4f,
                sideWallY
            )
            moundPath.lineTo(bulbMaxRightX + 4f, glassBottom + 4f)
            moundPath.close()

            clipPath(bottomBulbPath) {
                drawPath(path = moundPath, brush = sand3DBrush)

                // Strati sedimentari caldi lungo il pendio del cono
                for (layer in 1..3) {
                    val f = layer / 4f
                    val ly = controlPeakY + (glassBottom - controlPeakY) * f
                    val lSideY = sideWallY + (glassBottom - sideWallY) * f
                    ridgePath.reset()
                    ridgePath.moveTo(bulbMaxLeftX, lSideY)
                    ridgePath.quadraticTo(centerX, ly, bulbMaxRightX, lSideY)
                    drawPath(
                        path = ridgePath,
                        color = sandWarmHighlight.copy(alpha = 0.22f),
                        style = Stroke(width = 1.dp.toPx())
                    )
                }

                // Granelli minerali caldi nel cumulo inferiore
                val moundH = (glassBottom - moundPeakY).coerceAtLeast(1f)
                for (s in bottomSpeckOffsets.indices step 2) {
                    val sx = centerX + bottomSpeckOffsets[s] * (bulbMaxRightX - bulbMaxLeftX)
                    val sy = moundPeakY + bottomSpeckOffsets[s + 1] * moundH
                    drawCircle(
                        color = if ((s / 2) % 2 == 0) sandWarmHighlight.copy(alpha = 0.55f) else sandWarmShadow.copy(alpha = 0.35f),
                        radius = 1.15.dp.toPx(),
                        center = Offset(sx, sy)
                    )
                }
            }
        }

        // 6 (Punto 3 Specifica). 40 GRANELLI IPNOTICI INDIPENDENTI CHE CADONO LENTAMENTE, SI SPARPAGLIANO A VENTAGLIO E SCIVOLANO SUL CONO
        if (effectiveFlowing || hasGrainsInFlight) {
            val streamStartY = midY + 1.dp.toPx()
            val maxFanSpreadPx = bulbHalfSpanX * 0.48f
            val airSwayAmpPx = 2.6.dp.toPx()
            val neckSpreadPx = 1.8.dp.toPx()
            val slideSpanPx = bulbHalfSpanX * 0.34f

            clipPath(bottomBulbPath) {
                for (i in 0 until grainCount) {
                    val base = i * stride
                    val p = grainPool[base]
                    if (p < 0f) continue

                    val neckOffset = grainPool[base + 2] * neckSpreadPx
                    val fanDir = grainPool[base + 3]
                    val swayPhase = grainPool[base + 4]
                    val swayFreq = grainPool[base + 5]
                    val radiusPx = grainPool[base + 6].dp.toPx()
                    val baseAlpha = grainPool[base + 7]

                    // Posizione X alla fine della caduta in aria (apertura a ventaglio + oscillazione sinusoidale individuale)
                    val airProgress = p.coerceAtMost(1f)
                    // Leggera accelerazione gravitazionale morbida: 0.45 * t + 0.55 * t^2
                    val fallCurve = 0.45f * airProgress + 0.55f * airProgress * airProgress
                    val airSway = kotlin.math.sin((tickTime * swayFreq + swayPhase).toDouble()).toFloat() *
                        airSwayAmpPx * kotlin.math.sin(airProgress * Math.PI).toFloat()
                    val landingX = centerX + neckOffset + fanDir * maxFanSpreadPx * airProgress + airSway
                    val landingSurfaceY = coneSurfaceYAt(landingX).coerceAtLeast(streamStartY + 8.dp.toPx())

                    val grainX: Float
                    val grainY: Float
                    val alpha: Float

                    if (p <= 1f) {
                        // In volo verso il cumulo conico inferiore
                        grainX = landingX
                        grainY = streamStartY + fallCurve * (landingSurfaceY - streamStartY)
                        alpha = baseAlpha
                    } else {
                        // Ha toccato la superficie del cono: scivola lateralmente lungo il pendio (~30°) mentre sfuma a 0
                        val slideProgress = ((p - 1f) / 0.35f).coerceIn(0f, 1f)
                        val slideDir = if (fanDir >= 0f) 1f else -1f
                        grainX = landingX + slideDir * slideSpanPx * slideProgress
                        grainY = coneSurfaceYAt(grainX)
                        alpha = baseAlpha * (1f - slideProgress)
                    }

                    if (alpha > 0.02f) {
                        val isBrightGrain = baseAlpha >= 0.86f
                        val grainColor = when {
                            isBrightGrain -> sandWarmHighlight.copy(alpha = alpha)
                            i % 2 == 0 -> sandPrimaryLight.copy(alpha = alpha)
                            else -> sandPrimaryDeep.copy(alpha = alpha)
                        }
                        drawCircle(
                            color = grainColor,
                            radius = radiusPx,
                            center = Offset(grainX, grainY),
                            blendMode = if (isBrightGrain) BlendMode.Plus else BlendMode.SrcOver
                        )
                    }
                }
            }
        }

        // 7 (Punto 4 Specifica). DUE SOTTILI ARCHI BIANCHI CURVI A BASSISSIMA OPACITÀ (alpha = 0.12f) LUNGO I BORDI INTERNI DEI BULBI
        val innerInset = 9.dp.toPx()
        innerArcTopPath.reset()
        innerArcTopPath.moveTo(bulbRightX - innerInset * 0.7f, glassTop + 10.dp.toPx())
        innerArcTopPath.quadraticTo(
            bulbMaxRightX - innerInset,
            glassTop + bulbHalfHeight * 0.52f,
            centerX + neckHalf + 4.dp.toPx(),
            midY - 10.dp.toPx()
        )
        drawPath(
            path = innerArcTopPath,
            color = Color.White.copy(alpha = 0.12f),
            style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
        )

        innerArcBottomPath.reset()
        innerArcBottomPath.moveTo(centerX + neckHalf + 4.dp.toPx(), midY + 10.dp.toPx())
        innerArcBottomPath.quadraticTo(
            bulbMaxRightX - innerInset,
            midY + bulbHalfHeight * 0.48f,
            bulbRightX - innerInset * 0.7f,
            glassBottom - 10.dp.toPx()
        )
        drawPath(
            path = innerArcBottomPath,
            color = Color.White.copy(alpha = 0.12f),
            style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
        )

        // 8. RIFLESSI SPECULARI PRINCIPALI DEL VETRO CURVO
        topSpecularHighlight.reset()
        topSpecularHighlight.moveTo(bulbShoulderX + 7.dp.toPx(), glassTop + 8.dp.toPx())
        topSpecularHighlight.cubicTo(
            bulbMaxLeftX + 7.dp.toPx(), glassTop + bulbHalfHeight * 0.35f,
            bulbMaxLeftX + 10.dp.toPx(), glassTop + bulbHalfHeight * 0.68f,
            centerX - neckHalf - 2.dp.toPx(), midY - 6.dp.toPx()
        )
        drawPath(
            path = topSpecularHighlight,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.70f),
                    Color.White.copy(alpha = 0.30f),
                    Color.Transparent
                ),
                startY = glassTop,
                endY = midY
            ),
            style = Stroke(width = 3.2.dp.toPx(), cap = StrokeCap.Round)
        )

        bottomSpecularHighlight.reset()
        bottomSpecularHighlight.moveTo(centerX - neckHalf - 2.dp.toPx(), midY + 8.dp.toPx())
        bottomSpecularHighlight.cubicTo(
            bulbMaxLeftX + 10.dp.toPx(), midY + bulbHalfHeight * 0.32f,
            bulbMaxLeftX + 7.dp.toPx(), midY + bulbHalfHeight * 0.65f,
            bulbShoulderX + 7.dp.toPx(), glassBottom - 8.dp.toPx()
        )
        drawPath(
            path = bottomSpecularHighlight,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = 0.55f),
                    Color.White.copy(alpha = 0.18f)
                ),
                startY = midY,
                endY = glassBottom
            ),
            style = Stroke(width = 3.2.dp.toPx(), cap = StrokeCap.Round)
        )

        rightRimHighlightTop.reset()
        rightRimHighlightTop.moveTo(bulbRightX - 5.dp.toPx(), glassTop + 12.dp.toPx())
        rightRimHighlightTop.cubicTo(
            bulbMaxRightX - 5.dp.toPx(), glassTop + bulbHalfHeight * 0.38f,
            bulbMaxRightX - 8.dp.toPx(), glassTop + bulbHalfHeight * 0.70f,
            centerX + neckHalf + 1.5.dp.toPx(), midY - 8.dp.toPx()
        )
        rightRimHighlightBottom.reset()
        rightRimHighlightBottom.moveTo(centerX + neckHalf + 1.5.dp.toPx(), midY + 10.dp.toPx())
        rightRimHighlightBottom.cubicTo(
            bulbMaxRightX - 8.dp.toPx(), midY + bulbHalfHeight * 0.30f,
            bulbMaxRightX - 5.dp.toPx(), midY + bulbHalfHeight * 0.62f,
            bulbRightX - 5.dp.toPx(), glassBottom - 12.dp.toPx()
        )
        drawPath(
            path = rightRimHighlightTop,
            color = Color.White.copy(alpha = 0.22f),
            style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round)
        )
        drawPath(
            path = rightRimHighlightBottom,
            color = Color.White.copy(alpha = 0.22f),
            style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round)
        )

        // 9. CONTORNO ESTERNO IN CRISTALLO SOFFIATO CON DOPPIO SPESSORE OTTICO
        val glassEdgeBrush = Brush.horizontalGradient(
            colors = if (isOvertime) {
                listOf(stillColors.overtime, Color.White.copy(alpha = 0.85f), stillColors.overtime)
            } else {
                listOf(
                    Color.White.copy(alpha = 0.65f),
                    stillColors.glassOutline.copy(alpha = 0.85f),
                    stillColors.glassOutline.copy(alpha = 0.45f),
                    Color.White.copy(alpha = 0.50f)
                )
            },
            startX = bulbMaxLeftX,
            endX = bulbMaxRightX
        )
        drawPath(
            path = topBulbPath,
            brush = glassEdgeBrush,
            style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
        )
        drawPath(
            path = bottomBulbPath,
            brush = glassEdgeBrush,
            style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
        )

        drawOval(
            color = Color.White.copy(alpha = 0.48f),
            topLeft = Offset(centerX - neckHalf - 1.5.dp.toPx(), midY - 2.5.dp.toPx()),
            size = Size((neckHalf + 1.5.dp.toPx()) * 2f, 5.dp.toPx()),
            style = Stroke(width = 1.4.dp.toPx())
        )
        drawOval(
            color = stillColors.glassOutline.copy(alpha = 0.40f),
            topLeft = Offset(centerX - neckHalf - 3.dp.toPx(), midY - 4.dp.toPx()),
            size = Size((neckHalf + 3.dp.toPx()) * 2f, 8.dp.toPx()),
            style = Stroke(width = 1.dp.toPx())
        )

        // 10. BASI IN LEGNO TORNITO A DOPPIO GRADINO 3D
        val mainCapWidth = w * 0.88f
        val mainCapLeft = (w - mainCapWidth) / 2f
        val innerCollarWidth = w * 0.72f
        val innerCollarLeft = (w - innerCollarWidth) / 2f

        val cap3DBrush = Brush.horizontalGradient(
            colors = listOf(
                stillColors.woodFrame.copy(alpha = 0.80f),
                Color(0xFFFFE5B4).copy(alpha = 0.72f),
                stillColors.woodFrame,
                stillColors.woodFrame.copy(alpha = 0.88f),
                Color.Black.copy(alpha = 0.62f)
            ),
            startX = mainCapLeft,
            endX = mainCapLeft + mainCapWidth
        )

        drawRoundRect(
            brush = cap3DBrush,
            topLeft = Offset(innerCollarLeft, capHeight - 1.dp.toPx()),
            size = Size(innerCollarWidth, innerCollarH + 2.dp.toPx()),
            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
        )
        drawRoundRect(
            brush = cap3DBrush,
            topLeft = Offset(mainCapLeft, 1.dp.toPx()),
            size = Size(mainCapWidth, capHeight),
            cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
        )
        drawLine(
            color = Color.White.copy(alpha = 0.42f),
            start = Offset(mainCapLeft + 6.dp.toPx(), 2.5.dp.toPx()),
            end = Offset(mainCapLeft + mainCapWidth - 6.dp.toPx(), 2.5.dp.toPx()),
            strokeWidth = 1.dp.toPx()
        )
        drawLine(
            color = Color.Black.copy(alpha = 0.22f),
            start = Offset(mainCapLeft + 12.dp.toPx(), 1.dp.toPx() + capHeight * 0.62f),
            end = Offset(mainCapLeft + mainCapWidth - 14.dp.toPx(), 1.dp.toPx() + capHeight * 0.62f),
            strokeWidth = 0.8.dp.toPx()
        )

        drawRoundRect(
            brush = cap3DBrush,
            topLeft = Offset(innerCollarLeft, glassBottom - 1.dp.toPx()),
            size = Size(innerCollarWidth, innerCollarH + 2.dp.toPx()),
            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
        )
        drawRoundRect(
            brush = cap3DBrush,
            topLeft = Offset(mainCapLeft, h - capHeight - 1.dp.toPx()),
            size = Size(mainCapWidth, capHeight),
            cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
        )
        drawLine(
            color = Color.Black.copy(alpha = 0.22f),
            start = Offset(mainCapLeft + 14.dp.toPx(), h - capHeight - 1.dp.toPx() + capHeight * 0.58f),
            end = Offset(mainCapLeft + mainCapWidth - 12.dp.toPx(), h - capHeight - 1.dp.toPx() + capHeight * 0.58f),
            strokeWidth = 0.8.dp.toPx()
        )
        drawLine(
            color = Color.White.copy(alpha = 0.40f),
            start = Offset(mainCapLeft + 6.dp.toPx(), h - capHeight + 0.5.dp.toPx()),
            end = Offset(mainCapLeft + mainCapWidth - 6.dp.toPx(), h - capHeight + 0.5.dp.toPx()),
            strokeWidth = 1.dp.toPx()
        )

        drawContext.canvas.restore()
    }
}
