package com.sanluca.still

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.tanh

const val ALERT_MODE_BOTH = 0
const val ALERT_MODE_SOUND = 1
const val ALERT_MODE_VIBRATION = 2
const val TINGSHA_FREQ_HZ = 2640.0

// Campane "soft": le frequenze fungono anche da identificativo del timbro salvato nelle preferenze
const val CHIME_FREQ_HZ = 880.0
const val WATER_BOWL_FREQ_HZ = 392.0
const val SOFT_BOWL_FREQ_HZ = 256.0
const val FEATHER_FREQ_HZ = 660.0
const val SOFT_GONG_FREQ_HZ = 196.0

private enum class SoftBell { NONE, CHIME, WATER_BOWL, SOFT_BOWL, FEATHER, SOFT_GONG }

private fun softBellKindOf(frequencyHz: Double): SoftBell = when {
    abs(frequencyHz - CHIME_FREQ_HZ) <= 1.0 -> SoftBell.CHIME
    abs(frequencyHz - WATER_BOWL_FREQ_HZ) <= 1.0 -> SoftBell.WATER_BOWL
    abs(frequencyHz - SOFT_BOWL_FREQ_HZ) <= 1.0 -> SoftBell.SOFT_BOWL
    abs(frequencyHz - FEATHER_FREQ_HZ) <= 1.0 -> SoftBell.FEATHER
    abs(frequencyHz - SOFT_GONG_FREQ_HZ) <= 1.0 -> SoftBell.SOFT_GONG
    else -> SoftBell.NONE
}

/** Riporta alla costante esatta una frequenza salvata di una campana soft, oppure null se non lo è. */
internal fun normalizeSoftBellFrequency(rawFreq: Double): Double? = when (softBellKindOf(rawFreq)) {
    SoftBell.CHIME -> CHIME_FREQ_HZ
    SoftBell.WATER_BOWL -> WATER_BOWL_FREQ_HZ
    SoftBell.SOFT_BOWL -> SOFT_BOWL_FREQ_HZ
    SoftBell.FEATHER -> FEATHER_FREQ_HZ
    SoftBell.SOFT_GONG -> SOFT_GONG_FREQ_HZ
    SoftBell.NONE -> null
}

enum class AmbientSoundscape(val id: String) {
    OFF("off"),
    RAIN("rain"),
    OCEAN("ocean"),
    WIND("wind"),
    DRONE("drone");

    companion object {
        fun fromId(id: String?): AmbientSoundscape =
            entries.firstOrNull { it.id == id } ?: OFF
    }
}

private class FastXorShiftRng(seed: Long) {
    private var state: Long = if (seed == 0L) 0x5E3779B97F4A7C15L else seed

    fun nextDoubleSigned(): Double {
        var x = state
        x = x xor (x shl 13)
        x = x xor (x ushr 7)
        x = x xor (x shl 17)
        state = x
        return ((x ushr 11) * (1.0 / (1L shl 53).toDouble())) * 2.0 - 1.0
    }

    fun nextDoubleUnit(): Double {
        return (nextDoubleSigned() + 1.0) * 0.5
    }
}

private class VossPinkNoise7(seed: Long) {
    private val rng = FastXorShiftRng(seed)
    private val rows = DoubleArray(7)
    private var runningSum = 0.0
    private var counter = 0

    init {
        for (i in 0 until 7) {
            val v = rng.nextDoubleSigned()
            rows[i] = v
            runningSum += v
        }
    }

    fun next(): Double {
        counter++
        val row = Integer.numberOfTrailingZeros(counter)
        if (row < 7) {
            runningSum -= rows[row]
            val newVal = rng.nextDoubleSigned()
            rows[row] = newVal
            runningSum += newVal
        }
        val white = rng.nextDoubleSigned()
        return (runningSum + white) * 0.125
    }
}

private class BiquadFilter {
    private var b0 = 1.0
    private var b1 = 0.0
    private var b2 = 0.0
    private var a1 = 0.0
    private var a2 = 0.0
    private var z1 = 0.0
    private var z2 = 0.0

    fun setLowPass(freqHz: Double, q: Double, sampleRate: Double) {
        val f = freqHz.coerceIn(40.0, sampleRate * 0.42)
        val clampedQ = q.coerceIn(0.3, 10.0)
        val w0 = 2.0 * PI * f / sampleRate
        val cosW0 = cos(w0)
        val sinW0 = sin(w0)
        val alpha = sinW0 / (2.0 * clampedQ)
        val invA0 = 1.0 / (1.0 + alpha)
        val oneMinusCos = 1.0 - cosW0
        b0 = (oneMinusCos * 0.5) * invA0
        b1 = oneMinusCos * invA0
        b2 = b0
        a1 = (-2.0 * cosW0) * invA0
        a2 = (1.0 - alpha) * invA0
    }

    fun setBandPass(freqHz: Double, q: Double, sampleRate: Double) {
        val f = freqHz.coerceIn(40.0, sampleRate * 0.42)
        val clampedQ = q.coerceIn(0.3, 12.0)
        val w0 = 2.0 * PI * f / sampleRate
        val cosW0 = cos(w0)
        val sinW0 = sin(w0)
        val alpha = sinW0 / (2.0 * clampedQ)
        val invA0 = 1.0 / (1.0 + alpha)
        b0 = alpha * invA0
        b1 = 0.0
        b2 = -alpha * invA0
        a1 = (-2.0 * cosW0) * invA0
        a2 = (1.0 - alpha) * invA0
    }

    fun process(input: Double): Double {
        val out = b0 * input + z1
        z1 = b1 * input - a1 * out + z2
        z2 = b2 * input - a2 * out
        return out
    }
}

/**
 * Generatore procedurale stereofonico continuo in streaming (44.1 kHz, 16-bit PCM stereo)
 * per i 4 sottofondi meditativi (Pioggia, Onde, Vento, Bordone 136.1 Hz).
 * Opera interamente senza allocazioni nel ciclo di sintesi, con dissolvenze raised-cosine,
 * curva di volume quadratica percettiva, ducking morbido durante le campane e rilascio
 * garantito di AudioTrack senza modificare il volume hardware globale del telefono.
 */
class AmbientSoundPlayer(
    private val scope: CoroutineScope
) {
    private val controlLock = Any()
    private var streamJob: Job? = null
    private var switchJob: Job? = null

    @Volatile private var activeSoundscape: AmbientSoundscape = AmbientSoundscape.OFF
    @Volatile private var targetVolumeGain: Double = 0.25
    @Volatile private var targetDuckGain: Double = 1.0
    @Volatile private var fadeOutRequested: Boolean = false
    @Volatile private var activeFadeOutSeconds: Double = DEFAULT_FADE_OUT_SEC

    companion object {
        private const val SAMPLE_RATE = 44100
        private const val SAMPLE_RATE_D = 44100.0
        private const val CHUNK_FRAMES = 1024
        private const val CONTROL_BLOCK = 32
        private const val TWO_PI = 2.0 * PI
        private const val DEFAULT_FADE_IN_SEC = 2.5
        private const val DEFAULT_FADE_OUT_SEC = 2.0
        private const val DUCK_GAIN = 0.35
        private const val MAX_RAIN_DROPS = 6
    }

    fun isPlaying(): Boolean = synchronized(controlLock) {
        streamJob?.isActive == true && !fadeOutRequested
    }

    fun isPlayingSoundscape(soundscape: AmbientSoundscape): Boolean = synchronized(controlLock) {
        streamJob?.isActive == true && !fadeOutRequested && activeSoundscape == soundscape
    }

    fun setVolumePercent(percent: Int) {
        val norm = percent.coerceIn(0, 100) / 100.0
        targetVolumeGain = norm * norm
    }

    fun setDucking(ducked: Boolean) {
        targetDuckGain = if (ducked) DUCK_GAIN else 1.0
    }

    @Suppress("UNUSED_PARAMETER")
    fun start(
        context: Context,
        soundscape: AmbientSoundscape,
        volumePercent: Int,
        fadeInSeconds: Double = DEFAULT_FADE_IN_SEC,
        fadeOutSeconds: Double = DEFAULT_FADE_OUT_SEC
    ) {
        if (soundscape == AmbientSoundscape.OFF) {
            stop(fadeOutSeconds)
            return
        }
        setVolumePercent(volumePercent)

        synchronized(controlLock) {
            switchJob?.cancel()
            switchJob = null
            if (streamJob?.isActive == true && activeSoundscape == soundscape && !fadeOutRequested) {
                return
            }
            streamJob?.cancel()
            activeSoundscape = soundscape
            fadeOutRequested = false
            activeFadeOutSeconds = fadeOutSeconds.coerceAtLeast(0.05)
            streamJob = launchStreamJob(
                soundscape = soundscape,
                fadeInSeconds = fadeInSeconds.coerceAtLeast(0.05)
            )
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun switchSoundscape(
        context: Context,
        soundscape: AmbientSoundscape,
        volumePercent: Int,
        fadeInSeconds: Double = DEFAULT_FADE_IN_SEC,
        fadeOutSeconds: Double = DEFAULT_FADE_OUT_SEC
    ) {
        if (soundscape == AmbientSoundscape.OFF) {
            stop(fadeOutSeconds)
            return
        }
        setVolumePercent(volumePercent)

        val previousJob: Job?
        synchronized(controlLock) {
            if (streamJob?.isActive == true && activeSoundscape == soundscape && !fadeOutRequested) {
                return
            }
            switchJob?.cancel()
            previousJob = streamJob
            if (previousJob?.isActive == true) {
                activeFadeOutSeconds = 0.45
                fadeOutRequested = true
            }
            switchJob = scope.launch(Dispatchers.Default) {
                try {
                    previousJob?.join()
                } catch (_: CancellationException) {
                    return@launch
                }
                synchronized(controlLock) {
                    if (!isActive) return@launch
                    activeSoundscape = soundscape
                    fadeOutRequested = false
                    activeFadeOutSeconds = fadeOutSeconds.coerceAtLeast(0.05)
                    streamJob = launchStreamJob(
                        soundscape = soundscape,
                        fadeInSeconds = fadeInSeconds.coerceAtLeast(0.05)
                    )
                }
            }
        }
    }

    fun stop(fadeOutSeconds: Double = DEFAULT_FADE_OUT_SEC): Job? = synchronized(controlLock) {
        switchJob?.cancel()
        switchJob = null
        activeSoundscape = AmbientSoundscape.OFF
        val job = streamJob
        if (job?.isActive == true) {
            activeFadeOutSeconds = fadeOutSeconds.coerceAtLeast(0.05)
            fadeOutRequested = true
            job
        } else {
            streamJob = null
            fadeOutRequested = false
            null
        }
    }

    fun stopImmediate() {
        synchronized(controlLock) {
            switchJob?.cancel()
            switchJob = null
            activeSoundscape = AmbientSoundscape.OFF
            fadeOutRequested = false
            streamJob?.cancel()
            streamJob = null
        }
    }

    private fun launchStreamJob(
        soundscape: AmbientSoundscape,
        fadeInSeconds: Double
    ): Job = scope.launch(Dispatchers.Default) {
        var audioTrack: AudioTrack? = null
        // Pre-allocazione completa di buffer e stati DSP prima del ciclo di streaming (zero allocazioni nel loop)
        val pcmBuffer = ShortArray(CHUNK_FRAMES * 2)
        val pinkL = VossPinkNoise7(0x51A7F3C9B2E1D4A7L)
        val pinkR = VossPinkNoise7(0x2D9E6B4F8A1C7351L)
        val dropRng = FastXorShiftRng(0x7B3E19D5C4F2A813L)

        val lpL = BiquadFilter()
        val lpR = BiquadFilter()
        val bpL1 = BiquadFilter()
        val bpL2 = BiquadFilter()
        val bpR1 = BiquadFilter()
        val bpR2 = BiquadFilter()
        val windBedL = BiquadFilter()
        val windBedR = BiquadFilter()

        // Pool pre-allocato per micro-gocce di pioggia
        val dropAmp = DoubleArray(MAX_RAIN_DROPS)
        val dropDecay = DoubleArray(MAX_RAIN_DROPS)
        val dropPhase = DoubleArray(MAX_RAIN_DROPS)
        val dropPhaseInc = DoubleArray(MAX_RAIN_DROPS)
        val dropPanL = DoubleArray(MAX_RAIN_DROPS)
        val dropPanR = DoubleArray(MAX_RAIN_DROPS)

        // Stati specifici dei timbri
        var brownL = 0.0
        var brownR = 0.0
        var rainDriftPhase = 0.0

        var oceanPhase1L = 0.0
        var oceanPhase1R = 0.65
        var oceanPhase2L = 1.4
        var oceanPhase2R = 2.55

        var windLfo1 = 0.0
        var windLfo2 = 1.7
        var windGustL = 0.4
        var windGustR = 2.1

        // Accumulatori di fase continui per Bordone 136.1 Hz (Om) con detune binaurale ±0.28 Hz
        var dFundL = 0.0; var dFundR = 0.0
        var dFifthL = 0.0; var dFifthR = 0.0
        var dSubL = 0.0; var dSubR = 0.0
        var dOctL = 0.0; var dOctR = 0.0
        var droneBreathPhase = 0.0

        val dFundIncL = TWO_PI * (136.1 - 0.28) / SAMPLE_RATE_D
        val dFundIncR = TWO_PI * (136.1 + 0.28) / SAMPLE_RATE_D
        val dFifthIncL = TWO_PI * (204.15 - 0.28) / SAMPLE_RATE_D
        val dFifthIncR = TWO_PI * (204.15 + 0.28) / SAMPLE_RATE_D
        val dSubIncL = TWO_PI * (68.05 - 0.14) / SAMPLE_RATE_D
        val dSubIncR = TWO_PI * (68.05 + 0.14) / SAMPLE_RATE_D
        val dOctIncL = TWO_PI * (272.20 - 0.28) / SAMPLE_RATE_D
        val dOctIncR = TWO_PI * (272.20 + 0.28) / SAMPLE_RATE_D
        val droneBreathInc = TWO_PI * 0.08 / SAMPLE_RATE_D

        val oceanInc1 = TWO_PI / (9.5 * SAMPLE_RATE_D)
        val oceanInc2 = TWO_PI / (13.2 * SAMPLE_RATE_D)
        val windInc1 = TWO_PI * 0.047 / SAMPLE_RATE_D
        val windInc2 = TWO_PI * 0.073 / SAMPLE_RATE_D
        val windGustIncL = TWO_PI * 0.031 / SAMPLE_RATE_D
        val windGustIncR = TWO_PI * 0.041 / SAMPLE_RATE_D
        val rainDriftInc = TWO_PI * 0.025 / SAMPLE_RATE_D

        when (soundscape) {
            AmbientSoundscape.RAIN -> {
                lpL.setLowPass(480.0, 0.707, SAMPLE_RATE_D)
                lpR.setLowPass(480.0, 0.707, SAMPLE_RATE_D)
            }
            AmbientSoundscape.OCEAN -> {
                lpL.setLowPass(240.0, 0.707, SAMPLE_RATE_D)
                lpR.setLowPass(240.0, 0.707, SAMPLE_RATE_D)
            }
            AmbientSoundscape.WIND -> {
                bpL1.setBandPass(320.0, 2.6, SAMPLE_RATE_D)
                bpL2.setBandPass(420.0, 3.2, SAMPLE_RATE_D)
                bpR1.setBandPass(420.0, 2.6, SAMPLE_RATE_D)
                bpR2.setBandPass(320.0, 3.2, SAMPLE_RATE_D)
                windBedL.setLowPass(240.0, 0.707, SAMPLE_RATE_D)
                windBedR.setLowPass(240.0, 0.707, SAMPLE_RATE_D)
            }
            AmbientSoundscape.DRONE -> {
                lpL.setLowPass(420.0, 0.707, SAMPLE_RATE_D)
                lpR.setLowPass(420.0, 0.707, SAMPLE_RATE_D)
            }
            AmbientSoundscape.OFF -> return@launch
        }

        val fadeInSamples = (fadeInSeconds * SAMPLE_RATE_D).toLong().coerceAtLeast(1L)
        var fadeInCounter = 0L
        var currentEnvelope = 0.0
        var fadeOutActive = false
        var fadeOutStartLevel = 1.0
        var fadeOutCounter = 0L
        var fadeOutSamples = (DEFAULT_FADE_OUT_SEC * SAMPLE_RATE_D).toLong().coerceAtLeast(1L)

        var currentVolGain = targetVolumeGain
        var currentDuckGain = targetDuckGain
        var controlCounter = 0
        var oceanEnvL = 0.35
        var oceanEnvR = 0.35
        var windAmpL = 0.75
        var windAmpR = 0.75

        try {
            val minBufBytes = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBufBytes <= 0) return@launch
            val bufferSizeBytes = maxOf(minBufBytes * 2, CHUNK_FRAMES * 2 * 2 * 2)

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSizeBytes)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack.setVolume(1.0f)
            audioTrack.play()

            var finishedFadeOut = false
            while (isActive && !finishedFadeOut) {
                val targetVol = targetVolumeGain
                val targetDuck = targetDuckGain

                if (fadeOutRequested && !fadeOutActive) {
                    fadeOutActive = true
                    fadeOutStartLevel = currentEnvelope
                    fadeOutCounter = 0L
                    fadeOutSamples = (activeFadeOutSeconds * SAMPLE_RATE_D).toLong().coerceAtLeast(1L)
                }

                for (i in 0 until CHUNK_FRAMES) {
                    // Inviluppo fade-in / fade-out raised-cosine
                    if (fadeOutActive) {
                        if (fadeOutCounter >= fadeOutSamples) {
                            currentEnvelope = 0.0
                            finishedFadeOut = true
                        } else {
                            val p = fadeOutCounter.toDouble() / fadeOutSamples.toDouble()
                            currentEnvelope = fadeOutStartLevel * 0.5 * (1.0 + cos(PI * p))
                            fadeOutCounter++
                        }
                    } else if (fadeInCounter < fadeInSamples) {
                        val p = fadeInCounter.toDouble() / fadeInSamples.toDouble()
                        currentEnvelope = 0.5 * (1.0 - cos(PI * p))
                        fadeInCounter++
                    } else {
                        currentEnvelope = 1.0
                    }

                    // Smussamento volume e ducking (zero zipper noise)
                    currentVolGain += (targetVol - currentVolGain) * 0.0008
                    currentDuckGain += (targetDuck - currentDuckGain) * 0.00025

                    var sampleL: Double
                    var sampleR: Double

                    if (finishedFadeOut) {
                        sampleL = 0.0
                        sampleR = 0.0
                    } else {
                        when (soundscape) {
                            AmbientSoundscape.RAIN -> {
                                rainDriftPhase += rainDriftInc
                                if (rainDriftPhase >= TWO_PI) rainDriftPhase -= TWO_PI
                                val drift = 0.95 + 0.05 * sin(rainDriftPhase)

                                val bedL = lpL.process(pinkL.next()) * 1.38 * drift
                                val bedR = lpR.process(pinkR.next()) * 1.38 * drift

                                // Micro-gocce transienti sparse ad inviluppo esponenziale rapido
                                if (dropRng.nextDoubleUnit() < (11.0 / SAMPLE_RATE_D)) {
                                    for (d in 0 until MAX_RAIN_DROPS) {
                                        if (dropAmp[d] < 0.0004) {
                                            val freq = 950.0 + dropRng.nextDoubleUnit() * 1650.0
                                            val decaySec = 0.012 + dropRng.nextDoubleUnit() * 0.020
                                            val pan = 0.12 + dropRng.nextDoubleUnit() * 0.76
                                            dropAmp[d] = 0.045 + dropRng.nextDoubleUnit() * 0.065
                                            dropDecay[d] = exp(-1.0 / (decaySec * SAMPLE_RATE_D))
                                            dropPhase[d] = 0.0
                                            dropPhaseInc[d] = TWO_PI * freq / SAMPLE_RATE_D
                                            dropPanL[d] = 1.0 - pan
                                            dropPanR[d] = pan
                                            break
                                        }
                                    }
                                }

                                var dropsL = 0.0
                                var dropsR = 0.0
                                for (d in 0 until MAX_RAIN_DROPS) {
                                    val amp = dropAmp[d]
                                    if (amp >= 0.0004) {
                                        val ph = dropPhase[d] + dropPhaseInc[d]
                                        dropPhase[d] = if (ph >= TWO_PI) ph - TWO_PI else ph
                                        val s = sin(ph) * amp
                                        dropsL += s * dropPanL[d]
                                        dropsR += s * dropPanR[d]
                                        dropAmp[d] = amp * dropDecay[d]
                                    }
                                }

                                val rawL = bedL + dropsL
                                val rawR = bedR + dropsR
                                // Leggero cross-feed stereo naturale
                                sampleL = rawL * 0.84 + rawR * 0.16
                                sampleR = rawR * 0.84 + rawL * 0.16
                            }

                            AmbientSoundscape.OCEAN -> {
                                oceanPhase1L += oceanInc1
                                if (oceanPhase1L >= TWO_PI) oceanPhase1L -= TWO_PI
                                oceanPhase1R += oceanInc1
                                if (oceanPhase1R >= TWO_PI) oceanPhase1R -= TWO_PI
                                oceanPhase2L += oceanInc2
                                if (oceanPhase2L >= TWO_PI) oceanPhase2L -= TWO_PI
                                oceanPhase2R += oceanInc2
                                if (oceanPhase2R >= TWO_PI) oceanPhase2R -= TWO_PI

                                if (controlCounter == 0) {
                                    // Onda asimmetrica: salita piu decisa verso il frangente, discesa morbida nella risacca
                                    val w1L = 0.5 * (1.0 + sin(oceanPhase1L - 0.38 * sin(oceanPhase1L)))
                                    val w2L = 0.5 * (1.0 + sin(oceanPhase2L - 0.34 * sin(oceanPhase2L)))
                                    val waveL = (0.64 * w1L + 0.36 * w2L).coerceIn(0.0, 1.0)
                                    val shapedL = waveL * waveL

                                    val w1R = 0.5 * (1.0 + sin(oceanPhase1R - 0.38 * sin(oceanPhase1R)))
                                    val w2R = 0.5 * (1.0 + sin(oceanPhase2R - 0.34 * sin(oceanPhase2R)))
                                    val waveR = (0.64 * w1R + 0.36 * w2R).coerceIn(0.0, 1.0)
                                    val shapedR = waveR * waveR

                                    lpL.setLowPass(180.0 + 720.0 * shapedL, 0.72, SAMPLE_RATE_D)
                                    lpR.setLowPass(180.0 + 720.0 * shapedR, 0.72, SAMPLE_RATE_D)
                                    oceanEnvL = 0.30 + 0.70 * shapedL
                                    oceanEnvR = 0.30 + 0.70 * shapedR
                                }

                                val pL = pinkL.next()
                                val pR = pinkR.next()
                                brownL += 0.018 * (pL - brownL)
                                brownR += 0.018 * (pR - brownR)
                                val srcL = pL * 0.76 + brownL * 0.60
                                val srcR = pR * 0.76 + brownR * 0.60

                                val filtL = lpL.process(srcL) * oceanEnvL * 1.48
                                val filtR = lpR.process(srcR) * oceanEnvR * 1.48
                                sampleL = filtL * 0.85 + filtR * 0.15
                                sampleR = filtR * 0.85 + filtL * 0.15
                            }

                            AmbientSoundscape.WIND -> {
                                windLfo1 += windInc1
                                if (windLfo1 >= TWO_PI) windLfo1 -= TWO_PI
                                windLfo2 += windInc2
                                if (windLfo2 >= TWO_PI) windLfo2 -= TWO_PI
                                windGustL += windGustIncL
                                if (windGustL >= TWO_PI) windGustL -= TWO_PI
                                windGustR += windGustIncR
                                if (windGustR >= TWO_PI) windGustR -= TWO_PI

                                if (controlCounter == 0) {
                                    val s1 = sin(windLfo1)
                                    val s2 = sin(windLfo2)
                                    // Oscillazione lenta incommensurabile tra 220 Hz e 540 Hz in opposizione stereo L/R
                                    val freqL1 = 380.0 + 155.0 * s1
                                    val freqR1 = 380.0 - 155.0 * s1
                                    val freqL2 = 380.0 + 155.0 * s2
                                    val freqR2 = 380.0 - 155.0 * s2
                                    bpL1.setBandPass(freqL1, 2.6, SAMPLE_RATE_D)
                                    bpR1.setBandPass(freqR1, 2.6, SAMPLE_RATE_D)
                                    bpL2.setBandPass(freqL2, 3.2, SAMPLE_RATE_D)
                                    bpR2.setBandPass(freqR2, 3.2, SAMPLE_RATE_D)

                                    windAmpL = 0.56 + 0.44 * (0.5 * (1.0 + sin(windGustL)))
                                    windAmpR = 0.56 + 0.44 * (0.5 * (1.0 + sin(windGustR)))
                                }

                                val pL = pinkL.next()
                                val pR = pinkR.next()
                                val wL = ((bpL1.process(pL) + bpL2.process(pL)) * 1.55 + windBedL.process(pL) * 0.30) * windAmpL
                                val wR = ((bpR1.process(pR) + bpR2.process(pR)) * 1.55 + windBedR.process(pR) * 0.30) * windAmpR
                                sampleL = wL * 0.86 + wR * 0.14
                                sampleR = wR * 0.86 + wL * 0.14
                            }

                            AmbientSoundscape.DRONE -> {
                                dFundL += dFundIncL; if (dFundL >= TWO_PI) dFundL -= TWO_PI
                                dFundR += dFundIncR; if (dFundR >= TWO_PI) dFundR -= TWO_PI
                                dFifthL += dFifthIncL; if (dFifthL >= TWO_PI) dFifthL -= TWO_PI
                                dFifthR += dFifthIncR; if (dFifthR >= TWO_PI) dFifthR -= TWO_PI
                                dSubL += dSubIncL; if (dSubL >= TWO_PI) dSubL -= TWO_PI
                                dSubR += dSubIncR; if (dSubR >= TWO_PI) dSubR -= TWO_PI
                                dOctL += dOctIncL; if (dOctL >= TWO_PI) dOctL -= TWO_PI
                                dOctR += dOctIncR; if (dOctR >= TWO_PI) dOctR -= TWO_PI
                                droneBreathPhase += droneBreathInc
                                if (droneBreathPhase >= TWO_PI) droneBreathPhase -= TWO_PI

                                val breath = 0.86 + 0.14 * sin(droneBreathPhase)
                                val harmBreath = 0.72 + 0.28 * cos(droneBreathPhase)

                                val rawL = (0.48 * sin(dFundL) +
                                    0.23 * harmBreath * sin(dFifthL) +
                                    0.21 * sin(dSubL) +
                                    0.08 * harmBreath * sin(dOctL)) * breath * 0.72
                                val rawR = (0.48 * sin(dFundR) +
                                    0.23 * harmBreath * sin(dFifthR) +
                                    0.21 * sin(dSubR) +
                                    0.08 * harmBreath * sin(dOctR)) * breath * 0.72

                                sampleL = lpL.process(rawL)
                                sampleR = lpR.process(rawR)
                            }

                            AmbientSoundscape.OFF -> {
                                sampleL = 0.0
                                sampleR = 0.0
                            }
                        }
                    }

                    controlCounter++
                    if (controlCounter >= CONTROL_BLOCK) controlCounter = 0

                    val totalGain = currentEnvelope * currentVolGain * currentDuckGain
                    val outL = tanh(sampleL * totalGain) * 32767.0
                    val outR = tanh(sampleR * totalGain) * 32767.0

                    val idx = i * 2
                    pcmBuffer[idx] = outL.toInt().coerceIn(-32768, 32767).toShort()
                    pcmBuffer[idx + 1] = outR.toInt().coerceIn(-32768, 32767).toShort()
                }

                val written = audioTrack.write(pcmBuffer, 0, CHUNK_FRAMES * 2, AudioTrack.WRITE_BLOCKING)
                if (written < 0) break
            }

            // Scrive un breve blocco di silenzio dopo il fade-out completo per chiudere pulito il buffer hardware
            if (finishedFadeOut && isActive) {
                pcmBuffer.fill(0)
                audioTrack.write(pcmBuffer, 0, CHUNK_FRAMES * 2, AudioTrack.WRITE_BLOCKING)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Gestione sicura in caso di indisponibilita temporanea del mixer audio
        } finally {
            try {
                if (audioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    audioTrack.pause()
                    audioTrack.flush()
                }
                audioTrack?.stop()
            } catch (_: Exception) {
            }
            try {
                audioTrack?.release()
            } catch (_: Exception) {
            }
        }
    }
}

/**
 * Utility per la sintesi audio ad alta fedeltà spaziale in stereo della campana tibetana / Gong (216 Hz, 432 Hz, 528 Hz)
 * e dei Cimbali Tingsha (frequenza fondamentale > 2500 Hz con armoniche metalliche inarmoniche e decadimento rapido).
 * Genera un segnale PCM stereo interleaved a 16-bit con spazializzazione delle sorgenti, riverbero comb filter
 * a frequenze prime con smorzamento passa-basso e curva di volume quadratica percettiva.
 * Rispetta il volume multimediale scelto dall'utente: la percentuale scala solo l'ampiezza dei campioni PCM.
 */
suspend fun playGongSound(
    context: Context,
    frequencyHz: Double = 432.0,
    repeatCount: Int = 1,
    volumePercent: Int = 100
) {
    val sampleRate = 44100
    val isTingsha = frequencyHz > 2500.0
    val softKind = softBellKindOf(frequencyHz)
    val durationSeconds = when {
        softKind == SoftBell.CHIME -> 2.4
        softKind == SoftBell.WATER_BOWL -> 3.2
        softKind == SoftBell.FEATHER -> 3.0
        isTingsha -> 3.0
        else -> 4.0 // esteso di +0.5s per ospitare la coda del riverbero
    }
    val numSamples = (durationSeconds * sampleRate).toInt()
    val buffer = ShortArray(numSamples * 2) // Interleaved stereo: L a indice 2*i, R a indice 2*i + 1
    val normalizedVol = (volumePercent.coerceIn(0, 100) / 100.0)
    // Curva quadratica percettiva per rendere ogni step naturale all'orecchio umano
    val amplitudeGain = normalizedVol * normalizedVol

    val fadeInSec = when (softKind) {
        SoftBell.CHIME -> 0.030
        SoftBell.WATER_BOWL -> 0.060
        SoftBell.SOFT_BOWL -> 0.080
        SoftBell.FEATHER -> 0.120
        SoftBell.SOFT_GONG -> 0.020
        SoftBell.NONE -> if (isTingsha) 0.008 else 0.005
    }
    val fadeOutSec = if (softKind == SoftBell.FEATHER) 1.5 else 0.22 // fade-out cosinusoidale finale
    // Riverbero: le campane soft usano meno segnale riverberato per un suono più asciutto e leggero
    val wetMix = when (softKind) {
        SoftBell.CHIME, SoftBell.SOFT_BOWL, SoftBell.SOFT_GONG -> 0.10f
        SoftBell.WATER_BOWL -> 0.14f
        SoftBell.FEATHER -> 0.12f
        SoftBell.NONE -> 0.20f
    }
    val dryMix = if (softKind == SoftBell.NONE) 0.80f else 1.0f - wetMix
    // Armoniche superiori attenuate nelle campane soft basate sul timbro classico
    val harmScale = when (softKind) {
        SoftBell.SOFT_BOWL -> 0.30
        SoftBell.SOFT_GONG -> 0.70
        else -> 1.0
    }
    val fadeOutStart = durationSeconds - fadeOutSec

    val rad45 = Math.PI / 4.0 // 45 gradi di sfasamento massimo

    // Buffer temporanei dry a virgola mobile prima del riverbero
    val dryL = FloatArray(numSamples)
    val dryR = FloatArray(numSamples)

    val itdDelaySamples = 15 // ~0.34 ms di ritardo interaurale per Tingsha

    for (i in 0 until numSamples) {
        val t = i.toDouble() / sampleRate

        if (isTingsha) {
            val envFundamental = exp(-2.35 * t)
            val envMetallic1 = exp(-4.8 * t)
            val envMetallic2 = exp(-7.5 * t)
            val envPing = exp(-18.0 * t)

            val cymbal1 = sin(2.0 * Math.PI * frequencyHz * t) * envFundamental
            val metallic = (0.16 * sin(2.0 * Math.PI * (frequencyHz * 1.482) * t) * envMetallic1 +
                            0.09 * sin(2.0 * Math.PI * (frequencyHz * 2.115) * t) * envMetallic2 +
                            0.07 * sin(2.0 * Math.PI * (frequencyHz * 2.793) * t) * envPing)

            // Secondo cimbalo (+4.2 Hz) con ritardo interaurale di 15 campioni tra L ed R
            val t2L = (i.toDouble() / sampleRate)
            val t2R = ((i - itdDelaySamples).coerceAtLeast(0).toDouble() / sampleRate)
            val envShimmerL = exp(-2.55 * t2L)
            val envShimmerR = exp(-2.55 * t2R)
            val cymbal2L = sin(2.0 * Math.PI * (frequencyHz + 4.2) * t2L) * envShimmerL
            val cymbal2R = sin(2.0 * Math.PI * (frequencyHz + 4.2) * t2R) * envShimmerR

            // Panning: Cimbalo 1 a 72% L / 28% R; Cimbalo 2 a 28% L / 72% R
            val sampleL = (0.44 * cymbal1 * 0.72 + 0.24 * cymbal2L * 0.28 + metallic * 0.50) * 0.78
            val sampleR = (0.44 * cymbal1 * 0.28 + 0.24 * cymbal2R * 0.72 + metallic * 0.50) * 0.78

            dryL[i] = sampleL.toFloat()
            dryR[i] = sampleR.toFloat()
        } else if (softKind == SoftBell.CHIME) {
            // Carillon delicato: tono quasi puro con una sola armonica debole e attacco morbido
            val env = exp(-2.4 * t)
            val tRight = (i - 6).coerceAtLeast(0).toDouble() / sampleRate // leggerissimo ritardo interaurale
            val toneL = sin(2.0 * Math.PI * frequencyHz * t) * env
            val toneR = sin(2.0 * Math.PI * frequencyHz * tRight) * exp(-2.4 * tRight)
            val overtone = 0.10 * sin(2.0 * Math.PI * frequencyHz * 2.0 * t) * exp(-5.0 * t)
            dryL[i] = (0.38 * (toneL + overtone)).toFloat()
            dryR[i] = (0.38 * (toneR + overtone)).toFloat()
        } else if (softKind == SoftBell.WATER_BOWL) {
            // Ciotola ad acqua: due sinusoidi vicine (+1.8 Hz) che creano un battimento liquido, nessuna armonica brillante
            val env = exp(-1.1 * t)
            val a = sin(2.0 * Math.PI * frequencyHz * t)
            val b = sin(2.0 * Math.PI * (frequencyHz + 1.8) * t)
            dryL[i] = (0.42 * env * (0.62 * a + 0.38 * b)).toFloat()
            dryR[i] = (0.42 * env * (0.38 * a + 0.62 * b)).toFloat()
        } else if (softKind == SoftBell.FEATHER) {
            // Piuma: sinusoide quasi pura con vibrato lentissimo e leggera rotazione stereo
            val env = exp(-0.9 * t)
            val vibrato = 0.8 * sin(2.0 * Math.PI * 0.35 * t)
            val tone = sin(2.0 * Math.PI * frequencyHz * t + vibrato) * env
            val rot = sin(2.0 * Math.PI * 0.25 * t)
            dryL[i] = (0.40 * tone * (1.0 + 0.15 * rot)).toFloat()
            dryR[i] = (0.40 * tone * (1.0 - 0.15 * rot)).toFloat()
        } else {
            val envelope = exp(-1.2 * t)
            // Lenta rotazione d'ampiezza delle parziali superiori a 0.35 Hz
            val rot = sin(2.0 * Math.PI * 0.35 * t)
            val panH1L = 0.50 + 0.20 * rot
            val panH1R = 0.50 - 0.20 * rot
            val panH2L = 0.50 - 0.22 * rot
            val panH2R = 0.50 + 0.22 * rot
            val panH3L = 0.50 + 0.18 * rot
            val panH3R = 0.50 - 0.18 * rot

            // Fondamentale centrata
            val fundamental = 0.60 * sin(2.0 * Math.PI * frequencyHz * t) * envelope

            // Armoniche superiori con sfasamenti (<= 45°) differenziati L/R per apertura spaziale senza cancellazioni
            val h1Base = 2.0 * Math.PI * frequencyHz * 2.01 * t
            val h2Base = 2.0 * Math.PI * frequencyHz * 3.02 * t
            val h3Base = 2.0 * Math.PI * frequencyHz * 4.03 * t

            val h1L = 0.25 * harmScale * sin(h1Base + rad45 * 0.5) * envelope * panH1L
            val h1R = 0.25 * harmScale * sin(h1Base - rad45 * 0.5) * envelope * panH1R

            val h2L = 0.10 * harmScale * sin(h2Base - rad45 * 0.75) * envelope * panH2L
            val h2R = 0.10 * harmScale * sin(h2Base + rad45 * 0.75) * envelope * panH2R

            val h3L = 0.05 * harmScale * sin(h3Base + rad45) * envelope * panH3L
            val h3R = 0.05 * harmScale * sin(h3Base - rad45) * envelope * panH3R

            dryL[i] = (fundamental * 0.50 + h1L + h2L + h3L).toFloat()
            dryR[i] = (fundamental * 0.50 + h1R + h2R + h3R).toFloat()
        }
    }

    // Gong ovattato: filtro passa-basso a due poli (~450 Hz) che toglie ogni spigolo alle alte frequenze
    if (softKind == SoftBell.SOFT_GONG) {
        val lpfAlpha = 0.062f
        var l1 = 0f; var l2 = 0f; var r1 = 0f; var r2 = 0f
        for (i in 0 until numSamples) {
            l1 += lpfAlpha * (dryL[i] - l1)
            l2 += lpfAlpha * (l1 - l2)
            r1 += lpfAlpha * (dryR[i] - r1)
            r2 += lpfAlpha * (r1 - r2)
            dryL[i] = l2
            dryR[i] = r2
        }
    }

    // 3. Riverbero ambientale stereo leggero tramite comb filter a lunghezze prime con smorzamento passa-basso
    val dL1 = 1327; val dL2 = 1993
    val dR1 = 1481; val dR2 = 2137
    val bufL1 = FloatArray(dL1); val bufL2 = FloatArray(dL2)
    val bufR1 = FloatArray(dR1); val bufR2 = FloatArray(dR2)
    var idxL1 = 0; var idxL2 = 0
    var idxR1 = 0; var idxR2 = 0
    var lpfL1 = 0.0f; var lpfL2 = 0.0f
    var lpfR1 = 0.0f; var lpfR2 = 0.0f
    val feedback = 0.34f
    val damp = 0.32f // smorzamento passa-basso delle alte frequenze

    for (i in 0 until numSamples) {
        val inL = dryL[i]
        val inR = dryR[i]

        // Comb filters canale Sinistro
        val outL1 = bufL1[idxL1]
        lpfL1 = outL1 * (1.0f - damp) + lpfL1 * damp
        bufL1[idxL1] = inL + lpfL1 * feedback
        if (++idxL1 >= dL1) idxL1 = 0

        val outL2 = bufL2[idxL2]
        lpfL2 = outL2 * (1.0f - damp) + lpfL2 * damp
        bufL2[idxL2] = inL + lpfL2 * feedback
        if (++idxL2 >= dL2) idxL2 = 0

        val wetL = (outL1 + outL2) * 0.5f

        // Comb filters canale Destro
        val outR1 = bufR1[idxR1]
        lpfR1 = outR1 * (1.0f - damp) + lpfR1 * damp
        bufR1[idxR1] = inR + lpfR1 * feedback
        if (++idxR1 >= dR1) idxR1 = 0

        val outR2 = bufR2[idxR2]
        lpfR2 = outR2 * (1.0f - damp) + lpfR2 * damp
        bufR2[idxR2] = inR + lpfR2 * feedback
        if (++idxR2 >= dR2) idxR2 = 0

        val wetR = (outR1 + outR2) * 0.5f

        // Miscelazione dry/wet (default 80% dry + 20% wet: riverbero leggero naturale)
        val mixL = inL * dryMix + wetL * wetMix
        val mixR = inR * dryMix + wetR * wetMix

        // Finestra cosinusoidale raised-cosine (fade-in iniziale e fade-out finale anti-click)
        val t = i.toDouble() / sampleRate
        val fadeIn = if (t < fadeInSec) {
            0.5 * (1.0 - cos(Math.PI * (t / fadeInSec).coerceIn(0.0, 1.0)))
        } else {
            1.0
        }
        val fadeOut = if (t > fadeOutStart) {
            0.5 * (1.0 + cos(Math.PI * ((t - fadeOutStart) / fadeOutSec).coerceIn(0.0, 1.0)))
        } else {
            1.0
        }
        val window = (fadeIn * fadeOut).toFloat()

        val intL = (mixL * window * 32767.0f * amplitudeGain).toInt()
        val intR = (mixR * window * 32767.0f * amplitudeGain).toInt()

        val bufIdx = i * 2
        buffer[bufIdx] = intL.coerceIn(-32768, 32767).toShort()
        buffer[bufIdx + 1] = intR.coerceIn(-32768, 32767).toShort()
    }

    if (numSamples > 0) {
        buffer[0] = 0; buffer[1] = 0
        buffer[(numSamples - 1) * 2] = 0; buffer[(numSamples - 1) * 2 + 1] = 0
    }

    var audioTrack: AudioTrack? = null
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        )
        .build()

    try {
        audioManager.requestAudioFocus(focusRequest)
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build()
            )
            .setBufferSizeInBytes(numSamples * 2 * 2) // numSamples * 2 canali * 2 byte (16-bit)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        audioTrack.write(buffer, 0, numSamples * 2)
        // La curva quadratica è già applicata nel segnale (amplitudeGain): niente doppia attenuazione
        audioTrack.setVolume(1.0f)

        for (count in 1..repeatCount) {
            audioTrack.stop()
            audioTrack.reloadStaticData()
            audioTrack.play()
            if (repeatCount > 1 && count < repeatCount) {
                delay(2200L) // Pausa naturale tra un Gong e l'altro nel trio finale Vipassana
            }
        }
        delay((durationSeconds * 1000).toLong())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        e.printStackTrace()
    } finally {
        try {
            audioTrack?.stop()
        } catch (_: Exception) {}
        audioTrack?.release()
        audioManager.abandonAudioFocusRequest(focusRequest)
    }
}

private fun getSystemVibrator(context: Context): Vibrator? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
}

fun triggerHapticGong(context: Context, repeatCount: Int = 1) {
    try {
        val vibrator = getSystemVibrator(context)
        if (vibrator != null && vibrator.hasVibrator()) {
            val timings = if (repeatCount >= 3) {
                longArrayOf(0, 500, 200, 800, 600, 500, 200, 800, 600, 500, 200, 800)
            } else {
                longArrayOf(0, 500, 200, 800)
            }
            val effect = VibrationEffect.createWaveform(timings, -1)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val vibrationAttributes = android.os.VibrationAttributes.createForUsage(
                    android.os.VibrationAttributes.USAGE_ALARM
                )
                vibrator.vibrate(effect, vibrationAttributes)
            } else {
                val audioAttributes = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .build()
                @Suppress("DEPRECATION")
                vibrator.vibrate(effect, audioAttributes)
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun cancelGongVibration(context: Context) {
    try {
        getSystemVibrator(context)?.cancel()
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
