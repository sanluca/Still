package com.sanluca.still

import android.graphics.Matrix
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.sanluca.still.ui.theme.LocalStillColors
import kotlinx.coroutines.launch

@org.intellij.lang.annotations.Language("AGSL")
private const val AGSL_CANDLE_FLAME_SHADER = """
uniform float2 uResolution;
uniform float uTime;
uniform float uSway;
uniform float uStretch;
uniform float4 uOuterColor;
uniform float4 uMidColor;

float hash(float2 p) {
    return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453123);
}

float noise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    float2 u = f * f * (3.0 - 2.0 * f);
    return mix(
        mix(hash(i + float2(0.0, 0.0)), hash(i + float2(1.0, 0.0)), u.x),
        mix(hash(i + float2(0.0, 1.0)), hash(i + float2(1.0, 1.0)), u.x),
        u.y
    );
}

float fbm(float2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 3; i++) {
        v += a * noise(p);
        p = p * 2.03 + float2(1.7, -2.4);
        a *= 0.5;
    }
    return v;
}

half4 main(float2 fragCoord) {
    float2 uv = fragCoord / max(uResolution, float2(1.0, 1.0));
    float hNorm = clamp((0.88 - uv.y) / (0.76 * (1.0 + uStretch * 0.06)), 0.0, 1.0);
    float xCenter = 0.5 + uSway * pow(hNorm, 1.55) * 0.24;
    float turb = (fbm(float2(uv.x * 5.5, uv.y * 4.5 + uTime * 2.6)) - 0.5) * 0.095 * pow(hNorm, 0.85);
    float dx = abs((uv.x - xCenter) + turb);

    float widthProfile = sin(clamp(hNorm, 0.0, 1.0) * 3.14159265) * (1.0 - 0.36 * hNorm) * 0.24;
    float dist = dx / max(widthProfile, 0.001);

    float outerMask = smoothstep(1.05, 0.32, dist) * smoothstep(0.0, 0.06, hNorm) * smoothstep(1.0, 0.86, hNorm);
    float coreMask = smoothstep(0.58, 0.10, dist) * smoothstep(0.04, 0.14, hNorm) * smoothstep(0.76, 0.35, hNorm);
    float blueBaseMask = smoothstep(0.95, 0.42, dist) * smoothstep(0.0, 0.05, hNorm) * smoothstep(0.28, 0.06, hNorm);

    float3 col = mix(uOuterColor.rgb, uMidColor.rgb, clamp(hNorm * 1.15 + (1.0 - dist) * 0.35, 0.0, 1.0));
    col = mix(col, float3(0.10, 0.58, 0.96), blueBaseMask * 0.72);
    col = mix(col, float3(1.0, 0.98, 0.90), coreMask);

    float alpha = clamp(outerMask * 0.94 + blueBaseMask * 0.45 + coreMask * 0.35, 0.0, 1.0);
    return half4(half3(col * alpha), half(alpha));
}
"""

@Composable
fun CandleTimerGraphic(
    waxFraction: Float,
    isRunning: Boolean,
    isOvertime: Boolean,
    isPreparing: Boolean,
    modifier: Modifier = Modifier,
    isInitialState: Boolean = false,
    onLightToStart: (() -> Unit)? = null
) {
    val stillColors = LocalStillColors.current
    val tertiaryColor = MaterialTheme.colorScheme.tertiary

    // Accensione della candela: a riposo (isInitialState) è spenta; quando il timer parte (o si passa il dito verso l'alto) la fiamma nasce
    // Allo spegnimento (da accesa -> spenta) si attiva una brace residua + un filo sottile di fumo procedurale per ~3.8s
    val litAnimatable = remember { Animatable(if (isInitialState) 0f else 1f) }
    val smokeProgress = remember { Animatable(1f) }
    var wasInitialState by remember { mutableStateOf(isInitialState) }
    val dragSpark = remember { Animatable(0f) }

    LaunchedEffect(isInitialState) {
        if (wasInitialState && !isInitialState) {
            // Riaccensione immediata: interrompe subito l'eventuale filo di fumo senza interferenze
            smokeProgress.snapTo(1f)
            launch {
                dragSpark.animateTo(0f, tween(durationMillis = 450, easing = FastOutSlowInEasing))
            }
            litAnimatable.animateTo(1f, tween(durationMillis = 550, easing = FastOutSlowInEasing))
            wasInitialState = false
        } else if (!wasInitialState && isInitialState) {
            // Spegnimento dopo essere stata accesa: fiamma che si spegne dolcemente + brace e fumo per ~3.8s
            wasInitialState = true
            dragSpark.snapTo(0f)
            launch {
                litAnimatable.animateTo(0f, tween(durationMillis = 180, easing = FastOutSlowInEasing))
            }
            smokeProgress.snapTo(0f)
            smokeProgress.animateTo(1f, tween(durationMillis = 3800, easing = LinearEasing))
        } else {
            val target = if (isInitialState) 0f else 1f
            litAnimatable.snapTo(target)
            dragSpark.snapTo(0f)
            smokeProgress.snapTo(1f)
            wasInitialState = isInitialState
        }
    }

    // Gesto verso l'alto per accendere la candela quando è a riposo
    val currentOnLightToStart by rememberUpdatedState(onLightToStart)
    val dragScope = rememberCoroutineScope()
    val gestureModifier = if (isInitialState && onLightToStart != null) {
        Modifier.pointerInput(Unit) {
            val thresholdPx = 40.dp.toPx()
            var upwardDy = 0f
            var completed = false
            detectDragGestures(
                onDragStart = {
                    upwardDy = 0f
                    completed = false
                },
                onDragEnd = {
                    if (!completed) {
                        dragScope.launch {
                            dragSpark.animateTo(
                                0f,
                                spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium)
                            )
                        }
                    }
                },
                onDragCancel = {
                    if (!completed) {
                        dragScope.launch {
                            dragSpark.animateTo(
                                0f,
                                spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium)
                            )
                        }
                    }
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    if (!completed) {
                        upwardDy = (upwardDy - dragAmount.y).coerceAtLeast(0f)
                        val progressToThreshold = (upwardDy / thresholdPx).coerceIn(0f, 1f)
                        dragScope.launch { dragSpark.snapTo(progressToThreshold * 0.45f) }
                        if (upwardDy >= thresholdPx) {
                            completed = true
                            currentOnLightToStart?.invoke()
                        }
                    }
                }
            )
        }
    } else {
        Modifier
    }

    // 1. Risparmio batteria nel loop: attivo solo durante il timer e limitato a ~40 fps
    var flameTimeSec by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(isRunning) {
        if (isRunning) {
            var lastFrameNanos = -1L
            while (true) {
                androidx.compose.runtime.withFrameNanos { frameNanos ->
                    if (lastFrameNanos < 0L) {
                        lastFrameNanos = frameNanos
                    } else if (frameNanos - lastFrameNanos >= 25_000_000L) {
                        val deltaSec = ((frameNanos - lastFrameNanos).coerceAtLeast(0L)).toDouble() / 1_000_000_000.0
                        flameTimeSec = (flameTimeSec + deltaSec.toFloat()) % 3600f
                        lastFrameNanos = frameNanos
                    }
                }
            }
        }
    }

    // 6. Array pre-allocato di 8 micro-particelle calde (zero allocazioni per frame: xNorm, speed, phase, radiusDp)
    val sparkSeeds = remember {
        floatArrayOf(
            -0.22f, 0.42f, 0.00f, 1.35f,
             0.18f, 0.51f, 0.14f, 1.15f,
            -0.09f, 0.37f, 0.29f, 1.50f,
             0.26f, 0.46f, 0.43f, 1.05f,
            -0.16f, 0.55f, 0.57f, 1.25f,
             0.07f, 0.39f, 0.71f, 1.40f,
            -0.28f, 0.48f, 0.84f, 1.10f,
             0.14f, 0.44f, 0.93f, 1.30f
        )
    }

    // Colature pre-allocate (stride 5): posizione X (frazione larghezza), raggiungimento (1.0 = arriva fino al piattino),
    // raggio dp, soglia di consumo a cui nasce la colata (0..1), inclinazione della punta
    val dripStride = 5
    val dripSpecs = remember {
        floatArrayOf(
            -0.31f, 1.00f, 3.6f, 0.02f,  0.010f,
             0.22f, 1.00f, 3.1f, 0.07f, -0.008f,
            -0.04f, 0.62f, 2.5f, 0.13f,  0.006f,
             0.40f, 1.00f, 3.3f, 0.20f, -0.010f,
            -0.40f, 1.00f, 2.8f, 0.28f,  0.008f,
             0.04f, 0.78f, 2.3f, 0.36f, -0.005f,
            -0.13f, 1.00f, 3.0f, 0.45f,  0.010f,
             0.31f, 0.58f, 2.6f, 0.54f,  0.006f,
            -0.22f, 1.00f, 3.2f, 0.63f, -0.008f,
             0.13f, 1.00f, 2.7f, 0.72f,  0.007f
        )
    }

    // Gocce di cera schizzate sul piattino (stride 4): X (frazione larghezza piattino), Y (frazione altezza piattino),
    // raggio dp, soglia di consumo a cui compaiono
    val waxSpeckStride = 4
    val waxSpeckSpecs = remember {
        floatArrayOf(
            -0.40f, 0.12f, 1.6f, 0.10f,
             0.38f, 0.06f, 1.3f, 0.22f,
            -0.30f, 0.24f, 1.1f, 0.38f,
             0.28f, 0.26f, 1.8f, 0.50f,
            -0.44f, 0.02f, 1.0f, 0.62f,
             0.42f, 0.18f, 1.2f, 0.78f
        )
    }

    // Oggetti Path e Matrix riutilizzati per zero allocazioni nel DrawScope
    val cylinderPath = remember { Path() }
    val dripPath = remember { Path() }
    val wickPath = remember { Path() }
    val outerFlamePath = remember { Path() }
    val blueCupPath = remember { Path() }
    val innerCorePath = remember { Path() }
    val smokePath = remember { Path() }
    val shaderMatrix = remember { Matrix() }

    val agslFlameShader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            RuntimeShader(AGSL_CANDLE_FLAME_SHADER)
        } else {
            null
        }
    }

    Canvas(modifier = modifier.then(gestureModifier)) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val litFactor = if (isInitialState) maxOf(dragSpark.value, litAnimatable.value) else litAnimatable.value
        val smokeT = smokeProgress.value

        // 5. Oscillazione organica combinando 3 sinusoidi a frequenze non multiple
        val t = flameTimeSec * 2.2f
        val swayWave = kotlin.math.sin(t) * 0.60f +
            kotlin.math.sin(t * 2.37f) * 0.25f +
            kotlin.math.sin(t * 4.13f) * 0.15f
        val bellyWave = kotlin.math.sin(t * 1.31f + 0.8f) * 0.55f +
            kotlin.math.sin(t * 2.93f) * 0.30f +
            kotlin.math.sin(t * 4.79f) * 0.15f
        val stretchWave = kotlin.math.sin(t * 1.73f + 0.4f) * 0.60f +
            kotlin.math.sin(t * 3.19f) * 0.25f +
            kotlin.math.sin(t * 5.07f) * 0.15f

        val motionIntensity = when {
            !isRunning -> 0.18f
            isPreparing -> 0.45f
            else -> 1.0f
        }

        // 7. Stati limite: in Overtime fermo su residuo basso (10%), altrimenti usa la frazione (75% in Infinito)
        val effectiveWax = if (isOvertime) 0.10f else waxFraction.coerceIn(0.10f, 1f)

        // Dimensioni e coordinate del corpo cilindrico della candela e del piattino Zen
        val baseCenterY = h * 0.885f
        val candleWidth = w * 0.38f
        val candleLeft = cx - candleWidth / 2f
        val candleRight = cx + candleWidth / 2f
        val ellipseRy = candleWidth * 0.155f

        val candleBottomY = baseCenterY - h * 0.015f
        val minTopY = h * 0.36f // Candela nuova (100%)
        val maxTopY = h * 0.74f // Limite massimo inferiore
        val candleTopY = maxTopY - (maxTopY - minTopY) * effectiveWax
        val candleHeight = (candleBottomY - candleTopY).coerceAtLeast(ellipseRy * 1.4f)

        // Colori dinamici della fiamma in base al tema e allo stato (vira verso overtime in Overtime)
        val flameOuterColor = when {
            isOvertime -> stillColors.overtime
            isPreparing -> tertiaryColor
            else -> stillColors.sandDeep
        }
        val flameMidColor = when {
            isOvertime -> stillColors.sandLight
            isPreparing -> stillColors.glassOutline
            else -> stillColors.sandLight
        }

        // 1. OMBRA MORBIDA A TERRA SOTTO IL PIATTINO PORTA-CANDELA
        val shadowWidth = w * 0.70f
        val shadowHeight = h * 0.065f
        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.Black.copy(alpha = 0.45f),
                    Color.Black.copy(alpha = 0.18f),
                    Color.Transparent
                ),
                center = Offset(cx, baseCenterY + shadowHeight * 0.35f),
                radius = shadowWidth * 0.52f
            ),
            topLeft = Offset(cx - shadowWidth / 2f, baseCenterY - shadowHeight * 0.15f),
            size = Size(shadowWidth, shadowHeight)
        )

        // 2. PIATTINO PORTA-CANDELA TORNITO 3D IN LEGNO E CERAMICA
        val saucerWidth = w * 0.62f
        val saucerHeight = h * 0.072f
        val saucerBrush = Brush.horizontalGradient(
            colorStops = arrayOf(
                0.00f to Color.Black.copy(alpha = 0.40f),
                0.15f to stillColors.woodFrame,
                0.35f to Color.White.copy(alpha = 0.22f),
                0.55f to stillColors.woodFrame,
                0.85f to Color.Black.copy(alpha = 0.32f),
                1.00f to Color.Black.copy(alpha = 0.50f)
            ),
            startX = cx - saucerWidth / 2f,
            endX = cx + saucerWidth / 2f
        )
        drawOval(
            color = stillColors.woodFrame,
            topLeft = Offset(cx - saucerWidth / 2f, baseCenterY - saucerHeight * 0.42f),
            size = Size(saucerWidth, saucerHeight)
        )
        drawOval(
            brush = saucerBrush,
            topLeft = Offset(cx - saucerWidth / 2f, baseCenterY - saucerHeight * 0.42f),
            size = Size(saucerWidth, saucerHeight)
        )
        val innerSaucerW = saucerWidth * 0.84f
        val innerSaucerH = saucerHeight * 0.68f
        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(
                    flameMidColor.copy(alpha = 0.32f),
                    stillColors.glassOutline.copy(alpha = 0.25f),
                    Color.Black.copy(alpha = 0.38f)
                ),
                center = Offset(cx, baseCenterY - innerSaucerH * 0.15f),
                radius = innerSaucerW * 0.5f
            ),
            topLeft = Offset(cx - innerSaucerW / 2f, baseCenterY - innerSaucerH * 0.50f),
            size = Size(innerSaucerW, innerSaucerH)
        )
        drawOval(
            color = stillColors.glassOutline.copy(alpha = 0.40f),
            topLeft = Offset(cx - saucerWidth / 2f, baseCenterY - saucerHeight * 0.42f),
            size = Size(saucerWidth, saucerHeight),
            style = Stroke(width = 1.dp.toPx())
        )

        // 3. CORPO CILINDRICO 3D DELLA CANDELA IN CERA NATURALE
        val waxBaseCream = Color(0xFFF3EAD8)
        val waxWarmMid = Color(0xFFE5D5B5)
        val waxCoreShadow = Color(0xFFB8A27E)
        val waxRimLight = Color(0xFFD6C39E)

        cylinderPath.reset()
        cylinderPath.moveTo(candleLeft, candleTopY)
        cylinderPath.lineTo(candleLeft, candleBottomY)
        cylinderPath.cubicTo(
            candleLeft, candleBottomY + ellipseRy * 1.3f,
            candleRight, candleBottomY + ellipseRy * 1.3f,
            candleRight, candleBottomY
        )
        cylinderPath.lineTo(candleRight, candleTopY)
        cylinderPath.close()

        drawPath(
            path = cylinderPath,
            brush = Brush.horizontalGradient(
                colorStops = arrayOf(
                    0.00f to waxWarmMid,
                    0.20f to waxBaseCream,
                    0.48f to waxWarmMid,
                    0.82f to waxCoreShadow,
                    1.00f to waxRimLight
                ),
                startX = candleLeft,
                endX = candleRight
            )
        )

        // Subsurface Scattering nella parte alta della cera
        val sssDepth = (candleHeight * 0.58f).coerceAtLeast(ellipseRy * 2.2f)
        val sssAlpha = (0.48f + 0.08f * stretchWave * motionIntensity).coerceIn(0.35f, 0.62f)
        drawPath(
            path = cylinderPath,
            brush = Brush.verticalGradient(
                colorStops = arrayOf(
                    0.00f to flameMidColor.copy(alpha = sssAlpha * litFactor),
                    0.35f to flameOuterColor.copy(alpha = sssAlpha * 0.45f * litFactor),
                    0.75f to Color.Transparent,
                    1.00f to Color.Black.copy(alpha = 0.18f)
                ),
                startY = candleTopY,
                endY = candleTopY + sssDepth
            )
        )

        // 4. COLATURE NATURALI: più la candela si consuma, più colate nascono e scendono lungo il cilindro.
        // Ogni colata nasce a una soglia di consumo diversa, cresce in lunghezza e spessore con andamento morbido
        // e ha una punta leggermente inclinata (più sottile a metà, più arrotondata sulla punta + linea di luce a sinistra)
        val burnProgress = ((1f - effectiveWax) / 0.90f).coerceIn(0f, 1f)
        val dripCount = dripSpecs.size / dripStride
        // Le colate restano attaccate alla parete mentre il bordo si abbassa: si vede solo la parte sotto il bordo attuale
        drawContext.canvas.save()
        drawContext.canvas.clipRect(0f, candleTopY, w, h)
        for (i in 0 until dripCount) {
            val idx = i * dripStride
            val xOffsetFrac = dripSpecs[idx]
            val reach = dripSpecs[idx + 1]
            val specRadiusPx = dripSpecs[idx + 2].dp.toPx()
            val threshold = dripSpecs[idx + 3]
            val skewFrac = dripSpecs[idx + 4]

            val x = ((burnProgress - threshold) / 0.28f).coerceIn(0f, 1f)
            if (x <= 0f) continue
            val growth = x * x * (3f - 2f * x) // smoothstep: nascita lenta, corsa centrale, rallentamento finale

            val baseRadius = specRadiusPx * (0.55f + 0.45f * growth)
            val dripX = cx + candleWidth * xOffsetFrac
            val tipX = dripX + candleWidth * skewFrac * growth
            val midX = (dripX + tipX) / 2f
            // La colata nasce all'altezza del bordo di quel momento e scorre fino al piattino (curvatura della base inclusa)
            val edgeU = kotlin.math.abs(xOffsetFrac) * 2f
            val groundY = candleBottomY + ellipseRy * 0.97f * (1f - edgeU * edgeU)
            val birthTopY = maxTopY - (maxTopY - minTopY) * (1f - 0.90f * threshold)
            val dripStartY = birthTopY + ellipseRy * 0.62f
            val dripLen = (groundY - dripStartY) * reach * growth

            if (dripLen > baseRadius * 2.3f) {
                val dripMidY = dripStartY + dripLen * 0.52f
                val dripTipY = dripStartY + dripLen
                val topHalfW = baseRadius * 0.68f
                val waistHalfW = baseRadius * 0.36f // Più sottile a metà corsa
                val bulbHalfW = baseRadius * 0.88f  // Più arrotondata sulla punta

                dripPath.reset()
                dripPath.moveTo(dripX - topHalfW, dripStartY)
                dripPath.quadraticTo(
                    midX - waistHalfW, dripMidY,
                    tipX - bulbHalfW, dripTipY - bulbHalfW * 0.35f
                )
                dripPath.cubicTo(
                    tipX - bulbHalfW, dripTipY + bulbHalfW * 0.95f,
                    tipX + bulbHalfW, dripTipY + bulbHalfW * 0.95f,
                    tipX + bulbHalfW, dripTipY - bulbHalfW * 0.35f
                )
                dripPath.quadraticTo(
                    midX + waistHalfW, dripMidY,
                    dripX + topHalfW, dripStartY
                )
                dripPath.close()

                drawPath(
                    path = dripPath,
                    color = waxBaseCream.copy(alpha = 0.88f)
                )

                // Sottile linea di luce sul bordo sinistro di ciascuna colatura
                drawLine(
                    color = Color.White.copy(alpha = 0.72f),
                    start = Offset(dripX - topHalfW * 0.72f, dripStartY + baseRadius * 0.25f),
                    end = Offset(tipX - bulbHalfW * 0.62f, dripTipY),
                    strokeWidth = 0.9.dp.toPx(),
                    cap = StrokeCap.Round
                )

                // Ombra sottile sul bordo destro per dare volume alla colata solidificata
                drawLine(
                    color = waxCoreShadow.copy(alpha = 0.34f),
                    start = Offset(dripX + topHalfW * 0.80f, dripStartY + baseRadius * 0.25f),
                    end = Offset(tipX + bulbHalfW * 0.70f, dripTipY),
                    strokeWidth = 0.9.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }
        drawContext.canvas.restore()

        drawPath(
            path = cylinderPath,
            color = stillColors.glassOutline.copy(alpha = 0.28f),
            style = Stroke(width = 1.dp.toPx())
        )

        // 4b. CERA CADUTA SUL PIATTINO: le colate arrivate a terra si raccolgono in un piede di cera che si allarga
        // man mano che la candela brucia, con gocce schizzate attorno come in una vera candela
        val footBaseY = baseCenterY + saucerHeight * 0.06f
        var groundedSum = 0f
        var groundedCount = 0
        for (i in 0 until dripCount) {
            val idx = i * dripStride
            if (dripSpecs[idx + 1] >= 1f) {
                groundedCount++
                val gx = ((burnProgress - dripSpecs[idx + 3]) / 0.28f).coerceIn(0f, 1f)
                groundedSum += gx * gx * (3f - 2f * gx)
            }
        }
        val footFrac = if (groundedCount > 0) (groundedSum / groundedCount).coerceIn(0f, 1f) else 0f

        for (s in 0 until waxSpeckSpecs.size / waxSpeckStride) {
            val sIdx = s * waxSpeckStride
            val speckAlpha = ((burnProgress - waxSpeckSpecs[sIdx + 3]) / 0.08f).coerceIn(0f, 1f) * 0.9f
            if (speckAlpha > 0.02f) {
                val speckR = waxSpeckSpecs[sIdx + 2].dp.toPx()
                drawOval(
                    color = waxBaseCream.copy(alpha = speckAlpha),
                    topLeft = Offset(
                        cx + saucerWidth * waxSpeckSpecs[sIdx] - speckR * 1.4f,
                        baseCenterY + saucerHeight * waxSpeckSpecs[sIdx + 1] - speckR * 0.8f
                    ),
                    size = Size(speckR * 2.8f, speckR * 1.6f)
                )
            }
        }

        if (footFrac > 0.02f) {
            val footW = (candleWidth * (1.04f + 0.40f * footFrac)).coerceAtMost(saucerWidth * 0.92f)
            val footH = saucerHeight * (0.50f + 0.30f * footFrac)
            drawOval(
                brush = Brush.horizontalGradient(
                    colors = listOf(waxCoreShadow, waxBaseCream, waxWarmMid, waxCoreShadow),
                    startX = cx - footW / 2f,
                    endX = cx + footW / 2f
                ),
                alpha = (0.55f + 0.45f * footFrac).coerceAtMost(1f),
                topLeft = Offset(cx - footW / 2f, footBaseY - footH / 2f),
                size = Size(footW, footH)
            )
            drawOval(
                color = Color.White.copy(alpha = 0.32f * footFrac),
                topLeft = Offset(cx - footW / 2f, footBaseY - footH / 2f),
                size = Size(footW, footH),
                style = Stroke(width = 0.9.dp.toPx())
            )
        }

        // Punto di arrivo di ogni colata a terra: piccola pozza di cera che si fonde con il piede
        for (i in 0 until dripCount) {
            val idx = i * dripStride
            if (dripSpecs[idx + 1] >= 1f) {
                val gx = ((burnProgress - dripSpecs[idx + 3]) / 0.28f).coerceIn(0f, 1f)
                val arrival = (((gx * gx * (3f - 2f * gx)) - 0.80f) / 0.20f).coerceIn(0f, 1f)
                if (arrival > 0f) {
                    val landR = dripSpecs[idx + 2].dp.toPx()
                    val landU = kotlin.math.abs(dripSpecs[idx]) * 2f
                    val landX = cx + candleWidth * dripSpecs[idx]
                    val landY = candleBottomY + ellipseRy * 0.97f * (1f - landU * landU)
                    val landW = landR * (2.6f + 3.4f * arrival)
                    val landH = saucerHeight * 0.30f * arrival
                    drawOval(
                        color = waxBaseCream.copy(alpha = 0.92f),
                        topLeft = Offset(landX - landW / 2f, landY - landH * 0.35f),
                        size = Size(landW, landH)
                    )
                }
            }
        }

        // 2 (Punto 2 Specifica). LAGHETTO DI CERA FUSA INCAVATO CON RADIAL GRADIENT SCURO E LUCIDO ATTORNO ALLO STOPPINO
        // Corona esterna del bordo superiore della candela
        drawOval(
            brush = Brush.horizontalGradient(
                colors = listOf(waxBaseCream, waxWarmMid, waxCoreShadow),
                startX = candleLeft,
                endX = candleRight
            ),
            topLeft = Offset(candleLeft, candleTopY - ellipseRy),
            size = Size(candleWidth, ellipseRy * 2f)
        )
        // Ellisse concentrica leggermente incavata (spostata più in basso per effetto conca 3D)
        val poolW = candleWidth * 0.84f
        val poolRy = ellipseRy * 0.74f
        val poolCenterY = candleTopY + ellipseRy * 0.14f
        drawOval(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0.00f to Color(0xFF4A2E14).copy(alpha = 0.25f + 0.67f * litFactor), // Cera liquida profonda e scura attorno allo stoppino
                    0.28f to flameOuterColor.copy(alpha = 0.82f * litFactor),           // Riflesso ambrato lucido della cera sciolta
                    0.68f to flameMidColor.copy(alpha = 0.75f * litFactor),             // Albedo calda verso il bordo bagnato
                    1.00f to waxCoreShadow.copy(alpha = 0.40f + 0.48f * litFactor)      // Parete interna dell'incavo
                ),
                center = Offset(cx, poolCenterY),
                radius = poolW * 0.52f
            ),
            topLeft = Offset(cx - poolW / 2f, poolCenterY - poolRy),
            size = Size(poolW, poolRy * 2f)
        )
        // Riflesso speculare bagnato sul laghetto di cera liquida
        if (litFactor > 0.02f) {
            drawArc(
                color = Color.White.copy(alpha = 0.55f * litFactor),
                startAngle = 195f,
                sweepAngle = 135f,
                useCenter = false,
                topLeft = Offset(cx - poolW * 0.42f, poolCenterY - poolRy * 0.75f),
                size = Size(poolW * 0.84f, poolRy * 1.5f),
                style = Stroke(width = 1.1.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        // Bordo superiore esterno illuminato
        drawOval(
            color = Color.White.copy(alpha = 0.48f),
            topLeft = Offset(candleLeft, candleTopY - ellipseRy),
            size = Size(candleWidth, ellipseRy * 2f),
            style = Stroke(width = 1.dp.toPx())
        )

        // 3 (Punto 3 Specifica). STOPPINO CURVO (quadraticTo) E MICRO-PUNTO DI BRACE INCANDESCENTE
        val wickBaseY = poolCenterY + poolRy * 0.08f
        val wickHeight = h * 0.046f
        val wickTopY = wickBaseY - wickHeight
        val wickCurveX = (w * 0.011f) + swayWave * motionIntensity * (w * 0.008f)
        val wickTip = Offset(cx + wickCurveX, wickTopY)

        // Ombra dello stoppino nel laghetto fuso
        drawOval(
            color = Color.Black.copy(alpha = 0.42f),
            topLeft = Offset(cx - 3.2.dp.toPx(), wickBaseY - 1.5.dp.toPx()),
            size = Size(6.4.dp.toPx(), 3.dp.toPx())
        )

        wickPath.reset()
        wickPath.moveTo(cx, wickBaseY)
        wickPath.quadraticTo(
            cx - wickCurveX * 0.45f,
            wickBaseY - wickHeight * 0.52f,
            wickTip.x,
            wickTip.y
        )
        drawPath(
            path = wickPath,
            color = Color(0xFF23170F),
            style = Stroke(width = 2.1.dp.toPx(), cap = StrokeCap.Round)
        )

        // 5. GEOMETRIA DELLA FIAMMA, LUCE ADDITIVA (BlendMode.Plus) E RUNTIMESHADER AGSL (API 33+)
        if (litFactor > 0.01f) {
            val baseFlameHeight = when {
                isPreparing -> h * 0.115f
                isOvertime -> h * 0.195f
                else -> h * 0.175f
            }
            val flameHeight = baseFlameHeight * (1f + stretchWave * 0.065f * motionIntensity) * (0.20f + 0.80f * litFactor)
            val flameHalfWidth = (candleWidth * 0.25f) * (1f - stretchWave * 0.03f * motionIntensity) * (0.35f + 0.65f * litFactor)

            val tipSwayX = swayWave * motionIntensity * (candleWidth * 0.17f) * litFactor
            val bellySwayX = bellyWave * motionIntensity * (candleWidth * 0.055f) * litFactor

            val flameBaseY = wickTip.y + wickHeight * 0.16f
            val flameTip = Offset(wickTip.x + tipSwayX, flameBaseY - flameHeight)
            val flameCenter = Offset(
                x = wickTip.x + tipSwayX * 0.38f,
                y = flameBaseY - flameHeight * 0.45f
            )

            // Alone radiale esterno con BlendMode.Plus per fusione ottica additiva con lo sfondo
            val haloRadius = flameHeight * 1.55f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        flameMidColor.copy(alpha = 0.46f * litFactor),
                        flameOuterColor.copy(alpha = 0.22f * litFactor),
                        Color.Transparent
                    ),
                    center = flameCenter,
                    radius = haloRadius
                ),
                radius = haloRadius,
                center = flameCenter,
                blendMode = BlendMode.Plus
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && agslFlameShader != null) {
                val boxW = candleWidth * 1.45f
                val boxH = flameHeight * 1.35f
                val boxLeft = wickTip.x - boxW / 2f
                val boxTop = flameBaseY - boxH * 0.88f

                agslFlameShader.setFloatUniform("uResolution", boxW, boxH)
                agslFlameShader.setFloatUniform("uTime", flameTimeSec)
                agslFlameShader.setFloatUniform("uSway", swayWave * motionIntensity)
                agslFlameShader.setFloatUniform("uStretch", stretchWave * motionIntensity)
                agslFlameShader.setFloatUniform(
                    "uOuterColor",
                    flameOuterColor.red,
                    flameOuterColor.green,
                    flameOuterColor.blue,
                    1f
                )
                agslFlameShader.setFloatUniform(
                    "uMidColor",
                    flameMidColor.red,
                    flameMidColor.green,
                    flameMidColor.blue,
                    1f
                )

                shaderMatrix.reset()
                shaderMatrix.setTranslate(boxLeft, boxTop)
                agslFlameShader.setLocalMatrix(shaderMatrix)

                drawRect(
                    brush = ShaderBrush(agslFlameShader),
                    topLeft = Offset(boxLeft, boxTop),
                    size = Size(boxW, boxH),
                    alpha = litFactor
                )
            } else {
                // Fallback automatico vettoriale su Canvas per API < 33
                outerFlamePath.reset()
                outerFlamePath.moveTo(wickTip.x, flameBaseY)
                outerFlamePath.cubicTo(
                    wickTip.x - flameHalfWidth * 1.15f + bellySwayX,
                    flameBaseY - flameHeight * 0.22f,
                    wickTip.x - flameHalfWidth * 0.72f + tipSwayX * 0.55f,
                    flameBaseY - flameHeight * 0.72f,
                    flameTip.x,
                    flameTip.y
                )
                outerFlamePath.cubicTo(
                    wickTip.x + flameHalfWidth * 0.72f + tipSwayX * 0.55f,
                    flameBaseY - flameHeight * 0.72f,
                    wickTip.x + flameHalfWidth * 1.15f + bellySwayX,
                    flameBaseY - flameHeight * 0.22f,
                    wickTip.x,
                    flameBaseY
                )
                outerFlamePath.close()

                drawPath(
                    path = outerFlamePath,
                    brush = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.00f to Color(0xFFFFF59D),
                            0.35f to flameMidColor,
                            0.78f to flameOuterColor,
                            1.00f to flameOuterColor.copy(alpha = 0.70f)
                        ),
                        startY = flameTip.y,
                        endY = flameBaseY
                    ),
                    alpha = litFactor
                )

                val blueCupWidth = flameHalfWidth * 0.78f
                val blueCupHeight = flameHeight * 0.26f
                blueCupPath.reset()
                blueCupPath.moveTo(wickTip.x, flameBaseY + 1.5.dp.toPx())
                blueCupPath.cubicTo(
                    wickTip.x - blueCupWidth,
                    flameBaseY - blueCupHeight * 0.25f,
                    wickTip.x - blueCupWidth * 0.55f,
                    flameBaseY - blueCupHeight,
                    wickTip.x + bellySwayX * 0.3f,
                    flameBaseY - blueCupHeight * 0.85f
                )
                blueCupPath.cubicTo(
                    wickTip.x + blueCupWidth * 0.55f,
                    flameBaseY - blueCupHeight,
                    wickTip.x + blueCupWidth,
                    flameBaseY - blueCupHeight * 0.25f,
                    wickTip.x,
                    flameBaseY + 1.5.dp.toPx()
                )
                blueCupPath.close()

                drawPath(
                    path = blueCupPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0xFF4FC3F7).copy(alpha = 0.55f),
                            Color(0xFF0288D1).copy(alpha = 0.75f)
                        ),
                        startY = flameBaseY - blueCupHeight,
                        endY = flameBaseY + 1.5.dp.toPx()
                    ),
                    alpha = litFactor
                )

                val coreHeight = flameHeight * 0.62f
                val coreHalfWidth = flameHalfWidth * 0.52f
                val coreBaseY = flameBaseY - flameHeight * 0.05f
                val coreTip = Offset(wickTip.x + tipSwayX * 0.58f, coreBaseY - coreHeight)

                innerCorePath.reset()
                innerCorePath.moveTo(wickTip.x, coreBaseY)
                innerCorePath.cubicTo(
                    wickTip.x - coreHalfWidth + bellySwayX * 0.6f,
                    coreBaseY - coreHeight * 0.25f,
                    wickTip.x - coreHalfWidth * 0.55f + tipSwayX * 0.4f,
                    coreBaseY - coreHeight * 0.75f,
                    coreTip.x,
                    coreTip.y
                )
                innerCorePath.cubicTo(
                    wickTip.x + coreHalfWidth * 0.55f + tipSwayX * 0.4f,
                    coreBaseY - coreHeight * 0.75f,
                    wickTip.x + coreHalfWidth + bellySwayX * 0.6f,
                    coreBaseY - coreHeight * 0.25f,
                    wickTip.x,
                    coreBaseY
                )
                innerCorePath.close()

                drawPath(
                    path = innerCorePath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFFFF8E1),
                            Color.White,
                            Color(0xFFFFE082).copy(alpha = 0.90f)
                        ),
                        startY = coreTip.y,
                        endY = coreBaseY
                    ),
                    alpha = litFactor
                )
            }

            // Micro-punto rosso-arancio incandescente dove lo stoppino tocca la base blu della fiamma
            drawCircle(
                color = Color(0xFFFF3D00).copy(alpha = litFactor),
                radius = 2.4.dp.toPx(),
                center = wickTip,
                blendMode = BlendMode.Plus
            )
            drawCircle(
                color = Color(0xFFFFAB00).copy(alpha = litFactor),
                radius = 1.4.dp.toPx(),
                center = wickTip
            )
            drawCircle(
                color = Color(0xFFFFF59D).copy(alpha = litFactor),
                radius = 0.7.dp.toPx(),
                center = wickTip
            )

            // 6. MICRO-SCINTILLE CALDE (8 particelle pre-allocate che salgono sopra la fiamma dissolvendosi)
            val sparkRiseSpan = flameHeight * 0.85f
            for (s in 0 until 8) {
                val baseIdx = s * 4
                val seedX = sparkSeeds[baseIdx]
                val speed = sparkSeeds[baseIdx + 1]
                val phaseOffset = sparkSeeds[baseIdx + 2]
                val radiusPx = sparkSeeds[baseIdx + 3].dp.toPx()

                val life = (flameTimeSec * speed + phaseOffset) % 1f
                val sparkAlpha = kotlin.math.sin(life * Math.PI.toFloat()) * (1f - life * 0.45f) * (if (isRunning) 0.78f else 0.35f) * litFactor
                if (sparkAlpha > 0.03f) {
                    val driftX = kotlin.math.sin((flameTimeSec * 1.9f + s * 1.3f).toDouble()).toFloat() * (candleWidth * 0.08f) * life
                    val sparkX = flameTip.x + seedX * (candleWidth * 0.28f) + driftX
                    val sparkY = flameTip.y + flameHeight * 0.12f - life * sparkRiseSpan
                    drawCircle(
                        color = flameMidColor.copy(alpha = sparkAlpha.coerceIn(0f, 1f)),
                        radius = radiusPx * (1f - life * 0.55f),
                        center = Offset(sparkX, sparkY),
                        blendMode = BlendMode.Plus
                    )
                }
            }
        }

        // 7. BRACE RESIDUA E FILO DI FUMO PROCEDURALE ALLO SPEGNIMENTO (~3.8s, interrotto subito alla riaccensione)
        if (smokeT in 0.001f..0.999f && dragSpark.value <= 0.01f) {
            // Brace calda sullo stoppino che si affievolisce nei primi ~1.7s
            val emberNorm = (1f - smokeT / 0.45f).coerceIn(0f, 1f)
            val emberIntensity = emberNorm * emberNorm
            if (emberIntensity > 0.01f) {
                drawCircle(
                    color = Color(0xFFFF3D00).copy(alpha = 0.75f * emberIntensity),
                    radius = (2.8f * (0.45f + 0.55f * emberIntensity)).dp.toPx(),
                    center = wickTip,
                    blendMode = BlendMode.Plus
                )
                drawCircle(
                    color = Color(0xFFFF8F00).copy(alpha = 0.90f * emberIntensity),
                    radius = (1.3f * (0.5f + 0.5f * emberIntensity)).dp.toPx(),
                    center = wickTip
                )
            }

            // Filo sottile e sinuoso di fumo che sale dallo stoppino dissolvendosi lentamente
            val maxSmokeRise = h * 0.34f
            val headProgress = (smokeT / 0.62f).coerceIn(0f, 1f)
            val headFrac = 1f - (1f - headProgress) * (1f - headProgress)
            val tailFrac = ((smokeT - 0.30f) / 0.70f).coerceIn(0f, 1f)
            val fadeIn = (smokeT / 0.08f).coerceIn(0f, 1f)
            val fadeOut = (1f - smokeT).coerceIn(0f, 1f)
            val smokeFade = fadeIn * fadeOut * fadeOut.let { kotlin.math.sqrt(it) }

            if (headFrac > tailFrac + 0.02f && smokeFade > 0.008f) {
                val steps = 22
                val span = headFrac - tailFrac
                smokePath.reset()
                var prevX = 0f
                var prevY = 0f
                for (k in 0..steps) {
                    val f = k.toFloat() / steps
                    val u = tailFrac + span * f
                    val y = wickTip.y - u * maxSmokeRise
                    val sway1 = kotlin.math.sin((u * 9.5f - smokeT * 7.2f).toDouble()).toFloat()
                    val sway2 = kotlin.math.sin((u * 16.0f - smokeT * 11.0f + 1.15f).toDouble()).toFloat()
                    val spread = w * (0.014f + 0.056f * u)
                    val drift = w * 0.032f * u * u
                    val x = wickTip.x + (sway1 * 0.72f + sway2 * 0.28f) * spread + drift
                    if (k == 0) {
                        smokePath.moveTo(x, y)
                    } else {
                        val midX = (prevX + x) * 0.5f
                        val midY = (prevY + y) * 0.5f
                        smokePath.quadraticTo(prevX, prevY, midX, midY)
                        if (k == steps) {
                            smokePath.lineTo(x, y)
                        }
                    }
                    prevX = x
                    prevY = y
                }

                val topY = wickTip.y - headFrac * maxSmokeRise
                val bottomY = wickTip.y - tailFrac * maxSmokeRise
                val veilBrush = Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.00f to Color.Transparent,
                        0.28f to Color(0xFFDDE3EA).copy(alpha = 0.14f * smokeFade),
                        0.72f to Color(0xFFE8EDF2).copy(alpha = 0.18f * smokeFade),
                        1.00f to Color.Transparent
                    ),
                    startY = topY,
                    endY = bottomY
                )
                val coreBrush = Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.00f to Color.Transparent,
                        0.24f to Color(0xFFF2F5F8).copy(alpha = 0.28f * smokeFade),
                        0.76f to Color(0xFFF6F8FA).copy(alpha = 0.36f * smokeFade),
                        1.00f to Color.Transparent
                    ),
                    startY = topY,
                    endY = bottomY
                )

                drawPath(
                    path = smokePath,
                    brush = veilBrush,
                    style = Stroke(width = (5.2f + 4.8f * smokeT).dp.toPx(), cap = StrokeCap.Round)
                )
                drawPath(
                    path = smokePath,
                    brush = coreBrush,
                    style = Stroke(width = (1.5f + 1.1f * smokeT).dp.toPx(), cap = StrokeCap.Round)
                )

                // Tre sbuffi leggerissimi che si aprono in cima al filo mentre si dissolve
                for (p in 0 until 3) {
                    val puffU = (headFrac - 0.08f * p).coerceIn(tailFrac, headFrac)
                    val puffY = wickTip.y - puffU * maxSmokeRise
                    val puffSway = kotlin.math.sin((puffU * 9.5f - smokeT * 7.2f + p * 0.8f).toDouble()).toFloat()
                    val puffX = wickTip.x + puffSway * w * (0.02f + 0.06f * puffU) + w * 0.035f * puffU * puffU
                    val puffRadius = (6.5f + 9.0f * smokeT + p * 2.5f).dp.toPx()
                    val puffAlpha = (0.10f * smokeFade * (1f - p * 0.22f)).coerceIn(0f, 0.12f)
                    if (puffAlpha > 0.005f) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFFE6ECF2).copy(alpha = puffAlpha),
                                    Color.Transparent
                                ),
                                center = Offset(puffX, puffY),
                                radius = puffRadius
                            ),
                            radius = puffRadius,
                            center = Offset(puffX, puffY)
                        )
                    }
                }
            }
        }
    }
}
