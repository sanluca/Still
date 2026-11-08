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
import kotlin.math.exp
import kotlin.math.sin

const val ALERT_MODE_BOTH = 0
const val ALERT_MODE_SOUND = 1
const val ALERT_MODE_VIBRATION = 2

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
 * Utility per la sintesi audio ad alta fedeltà della campana tibetana / Gong (frequenza armonica 432 Hz / 528 Hz / 216 Hz).
 * Genera un'onda sinusoidale PCM a 16-bit con decadimento esponenziale naturale e curva di volume quadratica percettiva.
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
    val durationSeconds = 3.5
    val numSamples = (durationSeconds * sampleRate).toInt()
    val buffer = ShortArray(numSamples)
    val normalizedVol = (volumePercent.coerceIn(0, 100) / 100.0)
    // Curva quadratica percettiva per rendere ogni step del 20% naturale all'orecchio umano
    val amplitudeGain = normalizedVol * normalizedVol

    for (i in 0 until numSamples) {
        val t = i.toDouble() / sampleRate
        val envelope = exp(-1.2 * t)
        
        val wave = 0.6 * sin(2.0 * Math.PI * frequencyHz * t) +
                   0.25 * sin(2.0 * Math.PI * frequencyHz * 2.01 * t) +
                   0.1 * sin(2.0 * Math.PI * frequencyHz * 3.02 * t) +
                   0.05 * sin(2.0 * Math.PI * frequencyHz * 4.03 * t)

        val value = (wave * envelope * 32767 * amplitudeGain).toInt()
        buffer[i] = value.coerceIn(-32768, 32767).toShort()
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
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(numSamples * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        audioTrack.write(buffer, 0, numSamples)
        audioTrack.setVolume(normalizedVol.toFloat())

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
