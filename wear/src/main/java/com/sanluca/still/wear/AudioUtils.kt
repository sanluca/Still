package com.sanluca.still.wear

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

const val TINGSHA_FREQ_HZ = 2640.0

// Campane "soft": frequenze-identificativo allineate con il modulo telefono (:app)
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

/**
 * Utility audio per l'orologio (Wear OS / Pixel Watch): supporta le frequenze classiche
 * (432 Hz, 528 Hz, 216 Hz, Tingsha) e le 5 campane "soft" (Carillon, Ciotola d'acqua,
 * Ciotola morbida, Piuma, Gong morbido) adattate per l'altoparlante mono dell'orologio,
 * forzando la riproduzione tramite USAGE_ALARM e CONTENT_TYPE_SONIFICATION; il volume di sistema non viene modificato,
 * la percentuale scelta scala solo l'ampiezza dei campioni PCM.
 */
@Suppress("UNUSED_PARAMETER")
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
        isTingsha -> 2.5
        else -> 3.5
    }
    val numSamples = (durationSeconds * sampleRate).toInt()
    val buffer = ShortArray(numSamples)
    val normalizedVol = (volumePercent.coerceIn(0, 100) / 100.0)
    // Curva quadratica percettiva per rendere ogni step del 20% naturale all'orecchio umano
    val amplitudeGain = normalizedVol * normalizedVol

    val fadeInSec = when (softKind) {
        SoftBell.CHIME -> 0.030
        SoftBell.WATER_BOWL -> 0.060
        SoftBell.SOFT_BOWL -> 0.080
        SoftBell.FEATHER -> 0.120
        SoftBell.SOFT_GONG -> 0.020
        SoftBell.NONE -> if (isTingsha) 0.008 else 0.005
    }
    val fadeOutSec = if (softKind == SoftBell.FEATHER) 1.5 else 0.18
    val fadeOutStart = durationSeconds - fadeOutSec

    val harmScale = when (softKind) {
        SoftBell.SOFT_BOWL -> 0.30
        SoftBell.SOFT_GONG -> 0.70
        else -> 1.0
    }
    val wetMix = when (softKind) {
        SoftBell.CHIME, SoftBell.SOFT_BOWL, SoftBell.SOFT_GONG -> 0.10f
        SoftBell.WATER_BOWL -> 0.14f
        SoftBell.FEATHER -> 0.12f
        SoftBell.NONE -> 0.0f
    }
    val dryMix = 1.0f - wetMix

    val dry = FloatArray(numSamples)
    for (i in 0 until numSamples) {
        val t = i.toDouble() / sampleRate
        val sample = when {
            isTingsha -> {
                val envFundamental = exp(-2.35 * t)
                val envShimmer = exp(-2.55 * t)
                val envMetallic1 = exp(-4.8 * t)
                val envMetallic2 = exp(-7.5 * t)
                val envPing = exp(-18.0 * t)

                val wave = 0.44 * sin(2.0 * Math.PI * frequencyHz * t) * envFundamental +
                           0.24 * sin(2.0 * Math.PI * (frequencyHz + 4.2) * t) * envShimmer +
                           0.16 * sin(2.0 * Math.PI * (frequencyHz * 1.482) * t) * envMetallic1 +
                           0.09 * sin(2.0 * Math.PI * (frequencyHz * 2.115) * t) * envMetallic2 +
                           0.07 * sin(2.0 * Math.PI * (frequencyHz * 2.793) * t) * envPing
                wave * 0.78
            }
            softKind == SoftBell.CHIME -> {
                val env = exp(-2.4 * t)
                val tone = sin(2.0 * Math.PI * frequencyHz * t) * env
                val overtone = 0.10 * sin(2.0 * Math.PI * frequencyHz * 2.0 * t) * exp(-5.0 * t)
                0.55 * (tone + overtone)
            }
            softKind == SoftBell.WATER_BOWL -> {
                val env = exp(-1.1 * t)
                val a = sin(2.0 * Math.PI * frequencyHz * t)
                val b = sin(2.0 * Math.PI * (frequencyHz + 1.8) * t)
                0.62 * env * (0.50 * a + 0.50 * b)
            }
            softKind == SoftBell.FEATHER -> {
                val env = exp(-0.9 * t)
                val vibrato = 0.8 * sin(2.0 * Math.PI * 0.35 * t)
                0.55 * sin(2.0 * Math.PI * frequencyHz * t + vibrato) * env
            }
            else -> {
                val envelope = exp(-1.2 * t)
                val wave = 0.60 * sin(2.0 * Math.PI * frequencyHz * t) +
                           0.25 * harmScale * sin(2.0 * Math.PI * frequencyHz * 2.01 * t) +
                           0.10 * harmScale * sin(2.0 * Math.PI * frequencyHz * 3.02 * t) +
                           0.05 * harmScale * sin(2.0 * Math.PI * frequencyHz * 4.03 * t)
                wave * envelope
            }
        }
        dry[i] = sample.toFloat()
    }

    // Gong morbido: filtro passa-basso a due poli (~450 Hz) per un timbro caldo e ovattato anche in mono
    if (softKind == SoftBell.SOFT_GONG) {
        val lpfAlpha = 0.062f
        var s1 = 0f
        var s2 = 0f
        for (i in 0 until numSamples) {
            s1 += lpfAlpha * (dry[i] - s1)
            s2 += lpfAlpha * (s1 - s2)
            dry[i] = s2
        }
    }

    // Riverbero comb mono leggero applicato alle campane soft per ammorbidire l'altoparlante dell'orologio
    val useReverb = wetMix > 0f
    val d1 = 1327
    val d2 = 1993
    val buf1 = if (useReverb) FloatArray(d1) else FloatArray(0)
    val buf2 = if (useReverb) FloatArray(d2) else FloatArray(0)
    var idx1 = 0
    var idx2 = 0
    var lpf1 = 0f
    var lpf2 = 0f
    val feedback = 0.34f
    val damp = 0.32f

    for (i in 0 until numSamples) {
        val inSample = dry[i]
        val mixed = if (useReverb) {
            val out1 = buf1[idx1]
            lpf1 = out1 * (1.0f - damp) + lpf1 * damp
            buf1[idx1] = inSample + lpf1 * feedback
            if (++idx1 >= d1) idx1 = 0

            val out2 = buf2[idx2]
            lpf2 = out2 * (1.0f - damp) + lpf2 * damp
            buf2[idx2] = inSample + lpf2 * feedback
            if (++idx2 >= d2) idx2 = 0

            val wet = (out1 + out2) * 0.5f
            inSample * dryMix + wet * wetMix
        } else {
            inSample
        }

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
        val windowGain = (fadeIn * fadeOut).toFloat()

        val sampleValue = (mixed * windowGain * 32767.0f * amplitudeGain).toInt()
        buffer[i] = sampleValue.coerceIn(-32768, 32767).toShort()
    }
    if (numSamples > 0) {
        buffer[0] = 0
        buffer[numSamples - 1] = 0
    }

    var audioTrack: AudioTrack? = null
    try {
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(numSamples * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        audioTrack.write(buffer, 0, numSamples)
        // La curva quadratica è già applicata nel segnale (amplitudeGain): niente doppia attenuazione
        audioTrack.setVolume(1.0f)

        for (count in 1..repeatCount) {
            audioTrack.stop()
            audioTrack.reloadStaticData()
            audioTrack.play()
            if (repeatCount > 1 && count < repeatCount) {
                delay(2200L) // Pausa naturale tra un Gong e l'altro nel trio finale
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
            val count = if (repeatCount >= 3) 3 else 1
            val effect = if (vibrator.hasAmplitudeControl()) {
                val pulseTimings = longArrayOf(60, 80, 120, 90, 70)
                val pulseAmplitudes = intArrayOf(45, 105, 145, 90, 35)
                val gapMs = 750L
                val totalLen = count * pulseTimings.size + (count - 1)
                val timings = LongArray(totalLen)
                val amplitudes = IntArray(totalLen)
                var idx = 0
                for (i in 0 until count) {
                    if (i > 0) {
                        timings[idx] = gapMs
                        amplitudes[idx] = 0
                        idx++
                    }
                    for (j in pulseTimings.indices) {
                        timings[idx] = pulseTimings[j]
                        amplitudes[idx] = pulseAmplitudes[j]
                        idx++
                    }
                }
                VibrationEffect.createWaveform(timings, amplitudes, -1)
            } else {
                val timings = if (count >= 3) {
                    longArrayOf(0, 220, 750, 220, 750, 220)
                } else {
                    longArrayOf(0, 220)
                }
                VibrationEffect.createWaveform(timings, -1)
            }
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