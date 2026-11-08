package com.sanluca.still.wear

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlinx.coroutines.delay
import kotlin.math.exp
import kotlin.math.sin

private val wearVolumeLock = Any()
private var activeWearGongCount = 0
private var savedWearStreamVolume = -1

private fun acquireFixedWearStreamVolume(audioManager: AudioManager) {
    synchronized(wearVolumeLock) {
        if (activeWearGongCount == 0) {
            try {
                savedWearStreamVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
                val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                if (savedWearStreamVolume != maxVol) {
                    audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVol, 0)
                }
            } catch (_: Exception) {
                savedWearStreamVolume = -1
            }
        }
        activeWearGongCount++
    }
}

private fun releaseFixedWearStreamVolume(audioManager: AudioManager) {
    synchronized(wearVolumeLock) {
        activeWearGongCount = (activeWearGongCount - 1).coerceAtLeast(0)
        if (activeWearGongCount == 0 && savedWearStreamVolume >= 0) {
            try {
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, savedWearStreamVolume, 0)
            } catch (_: Exception) {
                // Ignora se il sistema impedisce il cambio volume
            } finally {
                savedWearStreamVolume = -1
            }
        }
    }
}

/**
 * Utility audio per l'orologio (Wear OS / Pixel Watch): supporta le 3 frequenze (432 Hz, 528 Hz, 216 Hz)
 * e i 3 Gong di chiusura, forzando la riproduzione tramite USAGE_ALARM e CONTENT_TYPE_SONIFICATION dall'altoparlante
 * a volume assoluto costante (ripristinando il volume di sistema dell'orologio al termine).
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
    acquireFixedWearStreamVolume(audioManager)
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
        audioTrack.setVolume(normalizedVol.toFloat())

        for (count in 1..repeatCount) {
            audioTrack.stop()
            audioTrack.reloadStaticData()
            audioTrack.play()
            if (repeatCount > 1 && count < repeatCount) {
                delay(2200L) // Pausa naturale tra un Gong e l'altro nel trio finale
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
        releaseFixedWearStreamVolume(audioManager)
    }
}