package com.sanluca.still

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.sanluca.still.core.playGongSound
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class AudioPreviewController(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private var previewGongJob: Job? = null
    private val previewAmbientPlayer = AmbientSoundPlayer(scope)
    private var previewAmbientJob: Job? = null

    fun playBellPreview(freqHz: Double, volumePercent: Int) {
        previewGongJob?.cancel()
        previewGongJob = scope.launch(Dispatchers.Default) {
            playGongSound(context, freqHz, repeatCount = 1, volumePercent = volumePercent)
        }
    }

    fun cancelBellPreview() {
        previewGongJob?.cancel()
        previewGongJob = null
    }

    fun playAmbientPreview(
        soundscape: AmbientSoundscape,
        volumePercent: Int,
        customUri: String? = null,
        durationMs: Long = 4200L,
        fadeInSeconds: Double = 0.6,
        fadeOutSeconds: Double = 0.9,
        onPlaybackError: () -> Unit = {}
    ) {
        previewAmbientJob?.cancel()
        if (StillTimerService.isRunning) {
            previewAmbientPlayer.stopImmediate()
            return
        }
        if (soundscape == AmbientSoundscape.OFF) {
            previewAmbientPlayer.stop(fadeOutSeconds = 0.5)
            return
        }
        previewAmbientJob = scope.launch(Dispatchers.Default) {
            previewAmbientPlayer.start(
                context = context,
                soundscape = soundscape,
                volumePercent = volumePercent,
                fadeInSeconds = fadeInSeconds,
                fadeOutSeconds = fadeOutSeconds,
                customUri = customUri,
                onPlaybackError = onPlaybackError
            )
            delay(durationMs)
            previewAmbientPlayer.stop(fadeOutSeconds = fadeOutSeconds)
        }
    }

    fun setAmbientVolume(
        volumePercent: Int,
        restartSoundscapeIfIdle: AmbientSoundscape? = null,
        customUri: String? = null,
        durationMs: Long = 4200L,
        fadeOutSeconds: Double = 0.9,
        onPlaybackError: () -> Unit = {}
    ) {
        previewAmbientPlayer.setVolumePercent(volumePercent)
        if (restartSoundscapeIfIdle != null &&
            restartSoundscapeIfIdle != AmbientSoundscape.OFF &&
            !previewAmbientPlayer.isPlaying() &&
            !StillTimerService.isRunning
        ) {
            playAmbientPreview(
                soundscape = restartSoundscapeIfIdle,
                volumePercent = volumePercent,
                customUri = customUri,
                durationMs = durationMs,
                fadeOutSeconds = fadeOutSeconds,
                onPlaybackError = onPlaybackError
            )
        }
    }

    fun stopAmbientPreview(fadeOutSeconds: Double = 0.0) {
        previewAmbientJob?.cancel()
        previewAmbientJob = null
        if (fadeOutSeconds > 0.0) {
            previewAmbientPlayer.stop(fadeOutSeconds = fadeOutSeconds)
        } else {
            previewAmbientPlayer.stopImmediate()
        }
    }

    fun stopAll() {
        previewGongJob?.cancel()
        previewGongJob = null
        previewAmbientJob?.cancel()
        previewAmbientJob = null
        previewAmbientPlayer.stopImmediate()
    }
}

@Composable
internal fun rememberAudioPreviewController(context: Context): AudioPreviewController {
    val coroutineScope = rememberCoroutineScope()
    val controller = remember(context, coroutineScope) {
        AudioPreviewController(context, coroutineScope)
    }
    DisposableEffect(controller) {
        onDispose {
            controller.stopAll()
        }
    }
    return controller
}
