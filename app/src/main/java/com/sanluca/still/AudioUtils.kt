package com.sanluca.still

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.exp
import kotlin.math.sin

/**
 * Utility per la sintesi audio ad alta fedeltà della campana tibetana / Gong (frequenza armonica 432 Hz / 528 Hz / 216 Hz).
 * Genera un'onda sinusoidale PCM a 16-bit con decadimento esponenziale naturale.
 */
fun playGongSound(frequencyHz: Double = 432.0, repeatCount: Int = 1) {
    val sampleRate = 44100
    val durationSeconds = 3.5
    val numSamples = (durationSeconds * sampleRate).toInt()
    val buffer = ShortArray(numSamples)

    for (i in 0 until numSamples) {
        val t = i.toDouble() / sampleRate
        val envelope = exp(-1.2 * t)
        
        val wave = 0.6 * sin(2.0 * Math.PI * frequencyHz * t) +
                   0.25 * sin(2.0 * Math.PI * frequencyHz * 2.01 * t) +
                   0.1 * sin(2.0 * Math.PI * frequencyHz * 3.02 * t) +
                   0.05 * sin(2.0 * Math.PI * frequencyHz * 4.03 * t)

        val value = (wave * envelope * 32767).toInt()
        buffer[i] = value.coerceIn(-32768, 32767).toShort()
    }

    try {
        val audioTrack = AudioTrack.Builder()
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

        for (count in 1..repeatCount) {
            audioTrack.stop()
            audioTrack.reloadStaticData()
            audioTrack.play()
            if (repeatCount > 1 && count < repeatCount) {
                Thread.sleep(2200L) // Pausa naturale tra un Gong e l'altro nel trio finale Vipassana
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}