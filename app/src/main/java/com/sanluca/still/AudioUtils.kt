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
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

const val ALERT_MODE_BOTH = 0
const val ALERT_MODE_SOUND = 1
const val ALERT_MODE_VIBRATION = 2
const val TINGSHA_FREQ_HZ = 2640.0

private val volumeLock = Any()
private var activeGongCount = 0
private var savedStreamVolume = -1

private fun acquireFixedStreamVolume(audioManager: AudioManager) {
    synchronized(volumeLock) {
        if (activeGongCount == 0) {
            try {
                savedStreamVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                if (savedStreamVolume != maxVol) {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVol, 0)
                }
            } catch (_: Exception) {
                savedStreamVolume = -1
            }
        }
        activeGongCount++
    }
}

private fun releaseFixedStreamVolume(audioManager: AudioManager) {
    synchronized(volumeLock) {
        activeGongCount = (activeGongCount - 1).coerceAtLeast(0)
        if (activeGongCount == 0 && savedStreamVolume >= 0) {
            try {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, savedStreamVolume, 0)
            } catch (_: Exception) {
                // Ignora se il sistema impedisce il cambio volume
            } finally {
                savedStreamVolume = -1
            }
        }
    }
}

/**
 * Utility per la sintesi audio ad alta fedeltà spaziale in stereo della campana tibetana / Gong (216 Hz, 432 Hz, 528 Hz)
 * e dei Cimbali Tingsha (frequenza fondamentale > 2500 Hz con armoniche metalliche inarmoniche e decadimento rapido).
 * Genera un segnale PCM stereo interleaved a 16-bit con spazializzazione delle sorgenti, riverbero comb filter
 * a frequenze prime con smorzamento passa-basso e curva di volume quadratica percettiva.
 * Imposta temporaneamente il volume hardware al massimo (senza popup UI) e lo ripristina a fine rintocco,
 * così che la percentuale scelta in Still produca sempre lo stesso volume reale.
 */
suspend fun playGongSound(
    context: Context,
    frequencyHz: Double = 432.0,
    repeatCount: Int = 1,
    volumePercent: Int = 100
) {
    val sampleRate = 44100
    val isTingsha = frequencyHz > 2500.0
    val durationSeconds = if (isTingsha) 3.0 else 4.0 // esteso di +0.5s per ospitare la coda del riverbero
    val numSamples = (durationSeconds * sampleRate).toInt()
    val buffer = ShortArray(numSamples * 2) // Interleaved stereo: L a indice 2*i, R a indice 2*i + 1
    val normalizedVol = (volumePercent.coerceIn(0, 100) / 100.0)
    // Curva quadratica percettiva per rendere ogni step naturale all'orecchio umano
    val amplitudeGain = normalizedVol * normalizedVol

    val fadeInSec = if (isTingsha) 0.008 else 0.005
    val fadeOutSec = 0.22 // 220 ms di fade-out cosinusoidale finale
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

            val h1L = 0.25 * sin(h1Base + rad45 * 0.5) * envelope * panH1L
            val h1R = 0.25 * sin(h1Base - rad45 * 0.5) * envelope * panH1R

            val h2L = 0.10 * sin(h2Base - rad45 * 0.75) * envelope * panH2L
            val h2R = 0.10 * sin(h2Base + rad45 * 0.75) * envelope * panH2R

            val h3L = 0.05 * sin(h3Base + rad45) * envelope * panH3L
            val h3R = 0.05 * sin(h3Base - rad45) * envelope * panH3R

            dryL[i] = (fundamental * 0.50 + h1L + h2L + h3L).toFloat()
            dryR[i] = (fundamental * 0.50 + h1R + h2R + h3R).toFloat()
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

        // Miscelazione: 80% dry + 20% wet (riverbero leggero naturale)
        val mixL = inL * 0.80f + wetL * 0.20f
        val mixR = inR * 0.80f + wetR * 0.20f

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

    acquireFixedStreamVolume(audioManager)
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
    } catch (e: Exception) {
        e.printStackTrace()
    } finally {
        try {
            audioTrack?.stop()
        } catch (_: Exception) {}
        audioTrack?.release()
        audioManager.abandonAudioFocusRequest(focusRequest)
        releaseFixedStreamVolume(audioManager)
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
