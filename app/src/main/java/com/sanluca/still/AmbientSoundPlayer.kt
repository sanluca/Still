package com.sanluca.still

import android.content.BroadcastReceiver
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.OpenableColumns
import androidx.annotation.RawRes
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes as Media3AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh

internal enum class AmbientSoundscape(val id: String, @param:RawRes val rawResId: Int? = null) {
    OFF("off"),
    LIGHT_RAIN("light_rain", R.raw.liecio_light_rain_109591),
    GENTLE_RAIN("gentle_rain", R.raw.eryliaa_gentle_rain_for_relaxation_and_sleep_337279),
    BIRDS_RAIN("birds_rain", R.raw.nikvideomaker_uccelli_e_la_pioggia_252092),
    OCEAN("ocean", R.raw.places_atmosphere_nature_onde_del_mare_sulla_spiaggia_379986),
    RIVER("river", R.raw.nils_vega_river_sounds_547976),
    FOREST("forest", R.raw.soundreality_nature_forest_sound_537925),
    DRONE("drone"),
    CUSTOM("custom");

    companion object {
        fun fromId(id: String?): AmbientSoundscape = when (id) {
            "rain" -> LIGHT_RAIN
            "wind" -> OFF
            else -> entries.firstOrNull { it.id == id } ?: OFF
        }
    }
}

private val SUPPORTED_CUSTOM_AUDIO_MIMES = setOf(
    "audio/mpeg",
    "audio/mp3",
    "audio/ogg",
    "application/ogg",
    "audio/x-ogg",
    "audio/vorbis",
    "audio/opus"
)

internal fun resolveAudioDisplayName(context: Context, uri: Uri): String? {
    try {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) {
                val name = cursor.getString(idx)
                if (!name.isNullOrBlank()) return name
            }
        }
    } catch (_: Exception) {
    }
    return uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
}

internal fun isSupportedCustomAudio(context: Context, uri: Uri, displayName: String? = null): Boolean {
    val mime = try {
        context.contentResolver.getType(uri)?.lowercase(Locale.ROOT)
    } catch (_: Exception) {
        null
    }
    if (mime != null && mime in SUPPORTED_CUSTOM_AUDIO_MIMES) return true
    val candidateName = (displayName ?: resolveAudioDisplayName(context, uri) ?: uri.lastPathSegment)
        ?.lowercase(Locale.ROOT)
        ?: return false
    return candidateName.endsWith(".mp3") || candidateName.endsWith(".ogg")
}

internal fun isAudioUriReadable(context: Context, uriString: String?): Boolean {
    return !uriString.isNullOrBlank() && try {
        val uri = uriString.toUri()
        context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
    } catch (_: Exception) {
        false
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

    fun process(input: Double): Double {
        val out = b0 * input + z1
        z1 = b1 * input - a1 * out + z2
        z2 = b2 * input - a2 * out
        return out
    }
}

/**
 * Motore audio ibrido per i sottofondi meditativi su smartphone:
 * - File .ogg integrati (Pixabay) e tracce locali scelte dall'utente (.mp3/.ogg) in loop continuo
 *   senza stacchi tramite Jetpack Media3 ExoPlayer.
 * - Bordone continuo a 136.1 Hz (frequenza Om) sintetizzato in streaming PCM 16-bit stereo via AudioTrack.
 * Supporta dissolvenze raised-cosine (fade-in/fade-out), curva di volume quadratica percettiva,
 * ducking morbido durante le campane e rilascio garantito delle risorse senza toccare il volume di sistema.
 */
internal class AmbientSoundPlayer(
    private val scope: CoroutineScope
) {
    private val controlLock = Any()
    private var streamJob: Job? = null
    private var switchJob: Job? = null
    private var activeExoHandle: Pair<ExoPlayer, AtomicBoolean>? = null

    @Volatile private var activeSoundscape: AmbientSoundscape = AmbientSoundscape.OFF
    @Volatile private var activeCustomUri: String? = null
    @Volatile private var targetVolumeGain: Double = 0.25
    @Volatile private var targetDuckGain: Double = 1.0
    @Volatile private var fadeOutRequested: Boolean = false
    @Volatile private var activeFadeOutSeconds: Double = DEFAULT_FADE_OUT_SEC

    companion object {
        private const val SAMPLE_RATE = 44100
        private const val SAMPLE_RATE_D = 44100.0
        private const val CHUNK_FRAMES = 1024
        private const val TWO_PI = 2.0 * PI
        private const val DEFAULT_FADE_IN_SEC = 2.5
        private const val DEFAULT_FADE_OUT_SEC = 2.0
        private const val DUCK_GAIN = 0.35
        private const val EXO_FADE_TICK_MS = 25L
    }

    fun isPlaying(): Boolean = synchronized(controlLock) {
        streamJob?.isActive == true && !fadeOutRequested
    }

    fun isPlayingSoundscape(
        soundscape: AmbientSoundscape,
        customUri: String? = null
    ): Boolean = synchronized(controlLock) {
        if (streamJob?.isActive != true || fadeOutRequested || activeSoundscape != soundscape) {
            return@synchronized false
        }
        if (soundscape == AmbientSoundscape.CUSTOM) {
            activeCustomUri == customUri
        } else {
            true
        }
    }

    fun setVolumePercent(percent: Int) {
        val norm = percent.coerceIn(0, 100) / 100.0
        targetVolumeGain = norm * norm
    }

    fun setDucking(ducked: Boolean) {
        targetDuckGain = if (ducked) DUCK_GAIN else 1.0
    }

    fun start(
        context: Context,
        soundscape: AmbientSoundscape,
        volumePercent: Int,
        fadeInSeconds: Double = DEFAULT_FADE_IN_SEC,
        fadeOutSeconds: Double = DEFAULT_FADE_OUT_SEC,
        customUri: String? = null,
        onPlaybackError: (() -> Unit)? = null
    ) {
        if (soundscape == AmbientSoundscape.OFF) {
            stop(fadeOutSeconds)
            return
        }
        val appContext = context.applicationContext ?: context
        val resolvedCustomUri = if (soundscape == AmbientSoundscape.CUSTOM) customUri else null
        setVolumePercent(volumePercent)

        synchronized(controlLock) {
            switchJob?.cancel()
            switchJob = null
            if (streamJob?.isActive == true &&
                activeSoundscape == soundscape &&
                !fadeOutRequested &&
                (soundscape != AmbientSoundscape.CUSTOM || activeCustomUri == resolvedCustomUri)
            ) {
                return
            }
            streamJob?.cancel()
            activeSoundscape = soundscape
            activeCustomUri = resolvedCustomUri
            fadeOutRequested = false
            activeFadeOutSeconds = fadeOutSeconds.coerceAtLeast(0.05)
            streamJob = launchPlaybackJob(
                appContext = appContext,
                soundscape = soundscape,
                fadeInSeconds = fadeInSeconds.coerceAtLeast(0.05),
                customUri = resolvedCustomUri,
                onPlaybackError = onPlaybackError
            )
        }
    }

    fun switchSoundscape(
        context: Context,
        soundscape: AmbientSoundscape,
        volumePercent: Int,
        fadeInSeconds: Double = DEFAULT_FADE_IN_SEC,
        fadeOutSeconds: Double = DEFAULT_FADE_OUT_SEC,
        customUri: String? = null,
        onPlaybackError: (() -> Unit)? = null
    ) {
        if (soundscape == AmbientSoundscape.OFF) {
            stop(fadeOutSeconds)
            return
        }
        val appContext = context.applicationContext ?: context
        val resolvedCustomUri = if (soundscape == AmbientSoundscape.CUSTOM) customUri else null
        setVolumePercent(volumePercent)

        val previousJob: Job?
        synchronized(controlLock) {
            if (streamJob?.isActive == true &&
                activeSoundscape == soundscape &&
                !fadeOutRequested &&
                (soundscape != AmbientSoundscape.CUSTOM || activeCustomUri == resolvedCustomUri)
            ) {
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
                    activeCustomUri = resolvedCustomUri
                    fadeOutRequested = false
                    activeFadeOutSeconds = fadeOutSeconds.coerceAtLeast(0.05)
                    streamJob = launchPlaybackJob(
                        appContext = appContext,
                        soundscape = soundscape,
                        fadeInSeconds = fadeInSeconds.coerceAtLeast(0.05),
                        customUri = resolvedCustomUri,
                        onPlaybackError = onPlaybackError
                    )
                }
            }
        }
    }

    fun stop(fadeOutSeconds: Double = DEFAULT_FADE_OUT_SEC): Job? = synchronized(controlLock) {
        switchJob?.cancel()
        switchJob = null
        activeSoundscape = AmbientSoundscape.OFF
        activeCustomUri = null
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
        val exoHandle: Pair<ExoPlayer, AtomicBoolean>?
        synchronized(controlLock) {
            switchJob?.cancel()
            switchJob = null
            activeSoundscape = AmbientSoundscape.OFF
            activeCustomUri = null
            fadeOutRequested = false
            streamJob?.cancel()
            streamJob = null
            exoHandle = activeExoHandle
            activeExoHandle = null
        }
        if (exoHandle != null) {
            val (player, released) = exoHandle
            if (Looper.myLooper() == Looper.getMainLooper()) {
                releaseExoPlayer(player, released)
            } else {
                Handler(Looper.getMainLooper()).post {
                    releaseExoPlayer(player, released)
                }
            }
        }
    }

    private fun releaseExoPlayer(player: ExoPlayer, released: AtomicBoolean) {
        if (released.compareAndSet(false, true)) {
            try {
                player.stop()
            } catch (_: Exception) {
            }
            try {
                player.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun launchPlaybackJob(
        appContext: Context,
        soundscape: AmbientSoundscape,
        fadeInSeconds: Double,
        customUri: String?,
        onPlaybackError: (() -> Unit)?
    ): Job = when {
        soundscape == AmbientSoundscape.DRONE -> launchDroneStreamJob(appContext, fadeInSeconds)
        soundscape == AmbientSoundscape.CUSTOM -> launchExoPlayerJob(
            appContext = appContext,
            mediaUri = null,
            customUriString = customUri,
            fadeInSeconds = fadeInSeconds,
            onPlaybackError = onPlaybackError
        )
        soundscape.rawResId != null -> {
            val rawUri = Uri.Builder()
                .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
                .authority(appContext.packageName)
                .path(soundscape.rawResId.toString())
                .build()
            launchExoPlayerJob(
                appContext = appContext,
                mediaUri = rawUri,
                customUriString = null,
                fadeInSeconds = fadeInSeconds,
                onPlaybackError = onPlaybackError
            )
        }
        else -> scope.launch { }
    }

    private fun launchExoPlayerJob(
        appContext: Context,
        mediaUri: Uri?,
        customUriString: String?,
        fadeInSeconds: Double,
        onPlaybackError: (() -> Unit)?
    ): Job = scope.launch(Dispatchers.Main) {
        val resolvedUri: Uri = if (mediaUri != null) {
            mediaUri
        } else {
            val readable = withContext(Dispatchers.IO) {
                isAudioUriReadable(appContext, customUriString)
            }
            if (!readable || customUriString.isNullOrBlank()) {
                synchronized(controlLock) {
                    if (activeSoundscape == AmbientSoundscape.CUSTOM) {
                        activeSoundscape = AmbientSoundscape.OFF
                        activeCustomUri = null
                    }
                }
                onPlaybackError?.invoke()
                return@launch
            }
            try {
                customUriString.toUri()
            } catch (_: Exception) {
                onPlaybackError?.invoke()
                return@launch
            }
        }

        if (!isActive) return@launch

        var player: ExoPlayer? = null
        val released = AtomicBoolean(false)
        val errorNotified = AtomicBoolean(false)
        val playbackFailed = AtomicBoolean(false)
        val stopRequestedBySystem = AtomicBoolean(false)

        try {
            val audioAttrs = Media3AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build()

            val exoPlayer = ExoPlayer.Builder(appContext)
                .setAudioAttributes(audioAttrs, true)
                .setHandleAudioBecomingNoisy(true)
                .build()
            player = exoPlayer

            synchronized(controlLock) {
                if (!isActive) {
                    releaseExoPlayer(exoPlayer, released)
                    return@launch
                }
                activeExoHandle = exoPlayer to released
            }

            exoPlayer.repeatMode = Player.REPEAT_MODE_ONE
            exoPlayer.volume = 0f
            exoPlayer.addListener(object : Player.Listener {
                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    if (!playWhenReady &&
                        (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY ||
                            reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS)
                    ) {
                        stopRequestedBySystem.set(true)
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    playbackFailed.set(true)
                    if (errorNotified.compareAndSet(false, true)) {
                        onPlaybackError?.invoke()
                    }
                }
            })
            exoPlayer.setMediaItem(MediaItem.fromUri(resolvedUri))
            exoPlayer.prepare()
            exoPlayer.play()

            val fadeInDurationMs = (fadeInSeconds * 1000.0).coerceAtLeast(50.0)
            val fadeInStartMs = SystemClock.elapsedRealtime()
            var currentEnvelope = 0.0
            var fadeOutActive = false
            var fadeOutStartLevel = 1.0
            var fadeOutStartMs = 0L
            var fadeOutDurationMs = (DEFAULT_FADE_OUT_SEC * 1000.0)

            var currentVolGain = targetVolumeGain
            var currentDuckGain = targetDuckGain
            var finishedFadeOut = false

            while (isActive && !finishedFadeOut && !playbackFailed.get() && !stopRequestedBySystem.get()) {
                val nowMs = SystemClock.elapsedRealtime()
                if (fadeOutRequested && !fadeOutActive) {
                    fadeOutActive = true
                    fadeOutStartLevel = currentEnvelope
                    fadeOutStartMs = nowMs
                    fadeOutDurationMs = (activeFadeOutSeconds * 1000.0).coerceAtLeast(50.0)
                }

                if (fadeOutActive) {
                    val progress = ((nowMs - fadeOutStartMs) / fadeOutDurationMs).coerceIn(0.0, 1.0)
                    currentEnvelope = fadeOutStartLevel * 0.5 * (1.0 + cos(PI * progress))
                    if (progress >= 1.0) {
                        currentEnvelope = 0.0
                        finishedFadeOut = true
                    }
                } else {
                    val progress = ((nowMs - fadeInStartMs) / fadeInDurationMs).coerceIn(0.0, 1.0)
                    currentEnvelope = 0.5 * (1.0 - cos(PI * progress))
                }

                currentVolGain += (targetVolumeGain - currentVolGain) * 0.35
                currentDuckGain += (targetDuckGain - currentDuckGain) * 0.15

                val finalVolume = (currentEnvelope * currentVolGain * currentDuckGain)
                    .toFloat()
                    .coerceIn(0f, 1f)
                if (!released.get()) {
                    exoPlayer.volume = finalVolume
                }

                if (!finishedFadeOut) {
                    delay(EXO_FADE_TICK_MS)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            if (errorNotified.compareAndSet(false, true)) {
                onPlaybackError?.invoke()
            }
        } finally {
            val capturedPlayer = player
            if (capturedPlayer != null) {
                withContext(NonCancellable + Dispatchers.Main.immediate) {
                    synchronized(controlLock) {
                        if (activeExoHandle?.first === capturedPlayer) {
                            activeExoHandle = null
                            if (stopRequestedBySystem.get()) {
                                activeSoundscape = AmbientSoundscape.OFF
                                activeCustomUri = null
                                fadeOutRequested = false
                            }
                        }
                    }
                    releaseExoPlayer(capturedPlayer, released)
                }
            }
        }
    }

    private fun launchDroneStreamJob(
        appContext: Context,
        fadeInSeconds: Double
    ): Job = scope.launch(Dispatchers.Default) {
        var audioTrack: AudioTrack? = null
        val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        var focusRequest: AudioFocusRequest? = null
        var focusRequested = false
        var noisyReceiverRegistered = false
        val focusGainScale = AtomicReference(1.0)
        val focusPaused = AtomicBoolean(false)
        val stopRequestedBySystem = AtomicBoolean(false)

        val noisyReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                    stopRequestedBySystem.set(true)
                    fadeOutRequested = true
                    activeFadeOutSeconds = 0.05
                }
            }
        }

        // Pre-allocazione completa prima del ciclo di streaming (zero allocazioni nel loop)
        val pcmBuffer = ShortArray(CHUNK_FRAMES * 2)
        val lpL = BiquadFilter()
        val lpR = BiquadFilter()
        lpL.setLowPass(420.0, 0.707, SAMPLE_RATE_D)
        lpR.setLowPass(420.0, 0.707, SAMPLE_RATE_D)

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

        val fadeInSamples = (fadeInSeconds * SAMPLE_RATE_D).toLong().coerceAtLeast(1L)
        var fadeInCounter = 0L
        var currentEnvelope = 0.0
        var fadeOutActive = false
        var fadeOutStartLevel = 1.0
        var fadeOutCounter = 0L
        var fadeOutSamples = (DEFAULT_FADE_OUT_SEC * SAMPLE_RATE_D).toLong().coerceAtLeast(1L)

        var currentVolGain = targetVolumeGain
        var currentDuckGain = targetDuckGain
        var currentFocusGain = 1.0

        try {
            val droneAudioAttrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()

            if (audioManager != null) {
                val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(droneAudioAttrs)
                    .setOnAudioFocusChangeListener { focusChange ->
                        when (focusChange) {
                            AudioManager.AUDIOFOCUS_LOSS -> {
                                stopRequestedBySystem.set(true)
                                focusGainScale.set(0.0)
                                fadeOutRequested = true
                                activeFadeOutSeconds = 0.15
                            }
                            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                                focusPaused.set(true)
                            }
                            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                                focusGainScale.set(0.25)
                            }
                            AudioManager.AUDIOFOCUS_GAIN -> {
                                focusPaused.set(false)
                                focusGainScale.set(1.0)
                            }
                        }
                    }
                    .build()
                focusRequest = request
                val focusResult = audioManager.requestAudioFocus(request)
                focusRequested = true
                if (focusResult != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    return@launch
                }
            }

            try {
                ContextCompat.registerReceiver(
                    appContext,
                    noisyReceiver,
                    IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
                    ContextCompat.RECEIVER_NOT_EXPORTED
                )
                noisyReceiverRegistered = true
            } catch (_: Exception) {
            }

            val minBufBytes = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBufBytes <= 0) return@launch
            val bufferSizeBytes = maxOf(minBufBytes * 2, CHUNK_FRAMES * 2 * 2 * 2)

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(droneAudioAttrs)
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
            while (isActive && !finishedFadeOut && !stopRequestedBySystem.get()) {
                if (focusPaused.get()) {
                    if (audioTrack.playState == AudioTrack.PLAYSTATE_PLAYING) {
                        try {
                            audioTrack.pause()
                        } catch (_: Exception) {
                        }
                    }
                    delay(100L)
                    continue
                } else if (audioTrack.playState != AudioTrack.PLAYSTATE_PLAYING) {
                    try {
                        audioTrack.play()
                    } catch (_: Exception) {
                    }
                }

                val targetVol = targetVolumeGain
                val targetDuck = targetDuckGain
                val targetFocus = focusGainScale.get()

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

                    // Smussamento volume, ducking e audio focus (zero zipper noise)
                    currentVolGain += (targetVol - currentVolGain) * 0.0008
                    currentDuckGain += (targetDuck - currentDuckGain) * 0.00025
                    currentFocusGain += (targetFocus - currentFocusGain) * 0.001

                    val sampleL: Double
                    val sampleR: Double

                    if (finishedFadeOut) {
                        sampleL = 0.0
                        sampleR = 0.0
                    } else {
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

                    val totalGain = currentEnvelope * currentVolGain * currentDuckGain * currentFocusGain
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
            if (finishedFadeOut && isActive && !stopRequestedBySystem.get()) {
                pcmBuffer.fill(0)
                audioTrack.write(pcmBuffer, 0, CHUNK_FRAMES * 2, AudioTrack.WRITE_BLOCKING)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Gestione sicura in caso di indisponibilita temporanea del mixer audio
        } finally {
            if (noisyReceiverRegistered) {
                try {
                    appContext.unregisterReceiver(noisyReceiver)
                } catch (_: Exception) {
                }
            }
            if (focusRequested && focusRequest != null) {
                try {
                    audioManager?.abandonAudioFocusRequest(focusRequest)
                } catch (_: Exception) {
                }
            }
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
