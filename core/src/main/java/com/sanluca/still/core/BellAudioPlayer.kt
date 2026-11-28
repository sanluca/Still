package com.sanluca.still.core

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import androidx.annotation.RawRes
import androidx.annotation.StringRes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

const val TINGSHA_FREQ_HZ = 2640.0

private const val BELL_REPEAT_INTERVAL_MS = 2300L
private const val BELL_DEFAULT_WAIT_MS = 12_000L

enum class BellSound(
    val code: Double,
    @param:RawRes val rawResId: Int,
    @param:StringRes val labelRes: Int
) {
    CLASSIC_BOWL(
        432.0,
        R.raw.campane_tibetane_freesound_community_singing_bowl_81915,
        R.string.bell_name_classic_bowl
    ),
    PURE_BELL(
        TINGSHA_FREQ_HZ,
        R.raw.campane_tibetane_freesound_community_singing_bell_hit_1_105400,
        R.string.bell_name_pure_bell
    ),
    TIBETAN_GONG(
        216.0,
        R.raw.campane_tibetane_freesound_community_singing_bowl_gong_69238,
        R.string.bell_name_tibetan_gong
    ),
    TEMPLE_BOWL(
        528.0,
        R.raw.campane_tibetane_freesound_community_temple_chiming_bowl_singing_bowl_72080,
        R.string.bell_name_temple_bowl
    ),
    HIGH_ZEN_TONE(
        880.0,
        R.raw.campane_tibetane_alex_jauk_zen_tone_high_202554,
        R.string.bell_name_high_zen
    ),
    HARMONIC_BOWL(
        392.0,
        R.raw.campane_tibetane_creative_spark_singing_bowl_248904,
        R.string.bell_name_harmonic_bowl
    ),
    WARM_BOWL(
        256.0,
        R.raw.campane_tibetane_freesound_community_singing_bowl_hit_3_33366,
        R.string.bell_name_warm_bowl
    ),
    EFLAT_BOWL(
        660.0,
        R.raw.campane_tibetane_freesound_community_e_flat_tibetan_singing_bowl_struck_38746,
        R.string.bell_name_eflat_bowl
    ),
    MONK_GONG(
        196.0,
        R.raw.campane_tibetane_freesound_community_043238_gong_bell_monkay39s_singing_bowl_modified_61150,
        R.string.bell_name_monk_gong
    ),
    BRIGHT_STRIKE(
        1100.0,
        R.raw.campane_tibetane_chisper_singing_bowl_strike_638373,
        R.string.bell_name_bright_strike
    ),
    DEEP_BOWL(
        1200.0,
        R.raw.campane_tibetane_freesound_community_singing_bowl_strike_sound_84682,
        R.string.bell_name_deep_bowl
    ),
    LOW_BOWL(
        1300.0,
        R.raw.campane_tibetane_freesound_community_singing_bowl_male_frequency_29714,
        R.string.bell_name_low_bowl
    ),
    NATURAL_BOWL(
        1400.0,
        R.raw.campane_tibetane_freesound_community_singing_bowl_23042017_01_raw_71015,
        R.string.bell_name_natural_bowl
    );

    companion object {
        fun fromCode(rawFreq: Double, defaultBell: BellSound = CLASSIC_BOWL): BellSound {
            if (!rawFreq.isFinite()) return defaultBell
            if (rawFreq > 2500.0) return PURE_BELL
            return entries.firstOrNull { it.code == rawFreq }
                ?: entries.firstOrNull { abs(rawFreq - it.code) <= 0.5 }
                ?: defaultBell
        }
    }
}

private data class BellPlaybackHandle(
    val player: MediaPlayer,
    val released: AtomicBoolean,
    val done: CompletableDeferred<Unit>,
    val durationMs: Long
)

private fun releaseBellPlayer(player: MediaPlayer, released: AtomicBoolean) {
    if (released.compareAndSet(false, true)) {
        try {
            if (player.isPlaying) {
                player.stop()
            }
        } catch (_: Exception) {
        }
        try {
            player.release()
        } catch (_: Exception) {
        }
    }
}

/**
 * Riproduce le campane reali in formato .ogg da res/raw usando istanze MediaPlayer dedicate
 * (una per rintocco), così nei rintocchi multipli le code naturali si sovrappongono in modo
 * armonioso dopo ~2,3 s senza tagliarsi bruscamente. Rispetta il volume scelto dall'utente
 * applicando una curva di volume quadratica percettiva solo sul player.
 */
suspend fun playGongSound(
    context: Context,
    frequencyHz: Double = 432.0,
    repeatCount: Int = 1,
    volumePercent: Int = 100,
    requestAudioFocus: Boolean = true,
    usage: Int = AudioAttributes.USAGE_MEDIA,
    contentType: Int = AudioAttributes.CONTENT_TYPE_MUSIC
) {
    val appContext = context.applicationContext ?: context
    val bell = BellSound.fromCode(frequencyHz)
    val safeRepeatCount = repeatCount.coerceAtLeast(1)
    val normalizedVol = volumePercent.coerceIn(0, 100) / 100f
    val gain = normalizedVol * normalizedVol

    val audioAttributes = AudioAttributes.Builder()
        .setUsage(usage)
        .setContentType(contentType)
        .build()

    val audioManager = if (requestAudioFocus) {
        appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    } else {
        null
    }
    val focusRequest = if (audioManager != null) {
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(audioAttributes)
            .build()
    } else {
        null
    }

    val activeHandles = mutableListOf<BellPlaybackHandle>()

    try {
        if (focusRequest != null) {
            audioManager?.requestAudioFocus(focusRequest)
        }

        for (count in 1..safeRepeatCount) {
            val handle = withContext(Dispatchers.IO) {
                val player = MediaPlayer()
                val released = AtomicBoolean(false)
                val done = CompletableDeferred<Unit>()
                try {
                    player.setAudioAttributes(audioAttributes)
                    appContext.resources.openRawResourceFd(bell.rawResId).use { afd ->
                        player.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    }
                    player.prepare()
                    player.setVolume(gain, gain)
                    val durationMs = player.duration.toLong().takeIf { it > 0L } ?: BELL_DEFAULT_WAIT_MS
                    player.setOnCompletionListener {
                        releaseBellPlayer(player, released)
                        done.complete(Unit)
                    }
                    player.setOnErrorListener { _, _, _ ->
                        releaseBellPlayer(player, released)
                        done.complete(Unit)
                        true
                    }
                    player.start()
                    BellPlaybackHandle(player, released, done, durationMs)
                } catch (_: Exception) {
                    releaseBellPlayer(player, released)
                    done.complete(Unit)
                    null
                }
            }

            if (handle != null) {
                activeHandles.add(handle)
            }

            if (safeRepeatCount > 1 && count < safeRepeatCount) {
                delay(BELL_REPEAT_INTERVAL_MS)
            }
        }

        val tailWaitMs = (activeHandles.maxOfOrNull { it.durationMs } ?: BELL_DEFAULT_WAIT_MS) + 1500L
        withTimeoutOrNull(tailWaitMs) {
            activeHandles.forEach { it.done.await() }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
    } finally {
        activeHandles.forEach { handle ->
            releaseBellPlayer(handle.player, handle.released)
            handle.done.complete(Unit)
        }
        if (focusRequest != null) {
            audioManager?.abandonAudioFocusRequest(focusRequest)
        }
    }
}
