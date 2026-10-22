package com.sanluca.still.wear

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import kotlin.math.exp
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WearMeditationApp()
        }
    }
}

// Invia i dati della meditazione completata dallo smartphone a Google Connessione Salute
fun sendSessionToPhone(context: Context, durationSec: Int, startInstant: Instant, endInstant: Instant) {
    try {
        val messageClient = Wearable.getMessageClient(context)
        val nodeClient = Wearable.getNodeClient(context)
        
        nodeClient.connectedNodes.addOnSuccessListener { nodes ->
            val payload = "$durationSec|${startInstant.epochSecond}|${endInstant.epochSecond}".toByteArray()
            for (node in nodes) {
                messageClient.sendMessage(node.id, "/meditation_completed", payload)
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

// Vibrazione aptica per l'orologio al Gong
fun triggerHapticGong(context: Context) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            val vibrator = vibratorManager.defaultVibrator
            vibrator.vibrate(VibrationEffect.createOneShot(800L, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(800L, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                vibrator.vibrate(800L)
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

@Composable
fun WearMeditationApp() {
    val context = LocalContext.current

    var selectedTimeInSeconds by remember { mutableStateOf(600) } // Default 10 min
    var timeLeftInSeconds by remember { mutableStateOf(selectedTimeInSeconds) }
    
    var isPreparing by remember { mutableStateOf(false) }
    var prepTimeLeftSeconds by remember { mutableStateOf(0) }

    var isOvertime by remember { mutableStateOf(false) }
    var overtimeSeconds by remember { mutableStateOf(0) }

    var isTimerRunning by remember { mutableStateOf(false) }
    var sessionStartTime by remember { mutableStateOf<Instant?>(null) }

    val progress = remember(timeLeftInSeconds, selectedTimeInSeconds) {
        if (selectedTimeInSeconds > 0) {
            timeLeftInSeconds.toFloat() / selectedTimeInSeconds.toFloat()
        } else {
            1f
        }
    }

    val backgroundColor = Color(0xFF12232E)
    val overtimeColor = Color(0xFFE5A93C)

    LaunchedEffect(isTimerRunning) {
        if (isTimerRunning) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "StillWear::TimerWakeLock")
            
            try {
                wakeLock.acquire(12 * 60 * 60 * 1000L)

                if (!isTimerRunning) return@LaunchedEffect

                if (sessionStartTime == null) {
                    sessionStartTime = Instant.now()
                    triggerHapticGong(context)
                    withContext(Dispatchers.Default) {
                        playGongSound()
                    }
                }

                val initialSeconds = timeLeftInSeconds
                val startRealtime = SystemClock.elapsedRealtime()

                while (isTimerRunning) {
                    delay(500L)
                    val elapsedSeconds = ((SystemClock.elapsedRealtime() - startRealtime) / 1000L).toInt()

                    if (selectedTimeInSeconds == -1) {
                        timeLeftInSeconds = initialSeconds + elapsedSeconds
                    } else if (!isOvertime) {
                        val remaining = initialSeconds - elapsedSeconds
                        if (remaining > 0) {
                            timeLeftInSeconds = remaining
                        } else {
                            timeLeftInSeconds = 0
                            isOvertime = true
                            overtimeSeconds = 0
                            
                            triggerHapticGong(context)
                            withContext(Dispatchers.Default) {
                                playGongSound()
                            }
                        }
                    } else {
                        val overtimeStartRealtime = startRealtime + (initialSeconds * 1000L)
                        val extraSec = ((SystemClock.elapsedRealtime() - overtimeStartRealtime) / 1000L).toInt()
                        overtimeSeconds = extraSec.coerceAtLeast(0)
                    }
                }
            } finally {
                if (wakeLock.isHeld) {
                    wakeLock.release()
                }
            }
        }
    }

    fun saveAndFinishSession(includeExtra: Boolean) {
        val start = sessionStartTime ?: Instant.now()
        val plannedSec = if (selectedTimeInSeconds > 0) selectedTimeInSeconds else timeLeftInSeconds
        val totalSec = if (includeExtra && isOvertime) (plannedSec + overtimeSeconds) else plannedSec
        val end = start.plusSeconds(totalSec.toLong())

        if (totalSec >= 10) {
            sendSessionToPhone(context, totalSec, start, end)
            Toast.makeText(context, "Sincronizzato con il telefono! 🧘‍♂️", Toast.LENGTH_SHORT).show()
        }

        isTimerRunning = false
        isOvertime = false
        overtimeSeconds = 0
        timeLeftInSeconds = if (selectedTimeInSeconds == -1) 0 else selectedTimeInSeconds
        sessionStartTime = null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxSize()
        ) {
            // Intestazione
            Text(
                text = if (isOvertime) "Extra ✨" else "Vipassana",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (isOvertime) overtimeColor else Color(0xFF88A0A8)
            )

            // Cerchio Timer
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(130.dp)
            ) {
                if (isOvertime) {
                    CircularProgressIndicator(
                        progress = { 1f },
                        modifier = Modifier.fillMaxSize(),
                        color = overtimeColor,
                        strokeWidth = 4.dp
                    )
                } else if (selectedTimeInSeconds != -1) {
                    CircularProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxSize(),
                        color = Color(0xFF4A707A),
                        strokeWidth = 5.dp
                    )
                }

                Text(
                    text = if (isOvertime) "+${formatTime(overtimeSeconds)}" else formatTime(timeLeftInSeconds),
                    fontSize = if (isOvertime) 22.sp else 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isOvertime) overtimeColor else Color.White
                )
            }

            // Controlli
            if (isOvertime) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { saveAndFinishSession(includeExtra = true) },
                        modifier = Modifier.height(36.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = overtimeColor)
                    ) {
                        Text("Salva +Extra", fontSize = 10.sp, color = Color.Black)
                    }

                    OutlinedButton(
                        onClick = { saveAndFinishSession(includeExtra = false) },
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text("Solo Previsto", fontSize = 10.sp)
                    }
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { isTimerRunning = !isTimerRunning },
                        modifier = Modifier.height(38.dp)
                    ) {
                        Text(if (isTimerRunning) "Pausa" else "Inizia", fontSize = 12.sp)
                    }

                    if (!isTimerRunning && timeLeftInSeconds != selectedTimeInSeconds) {
                        OutlinedButton(
                            onClick = { 
                                timeLeftInSeconds = if (selectedTimeInSeconds == -1) 0 else selectedTimeInSeconds
                                sessionStartTime = null
                            },
                            modifier = Modifier.height(38.dp)
                        ) {
                            Text("Resetta", fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }
}

fun playGongSound() {
    val sampleRate = 44100
    val durationSeconds = 3.5
    val numSamples = (durationSeconds * sampleRate).toInt()
    val buffer = ShortArray(numSamples)

    val freqFundamental = 432.0

    for (i in 0 until numSamples) {
        val t = i.toDouble() / sampleRate
        val envelope = exp(-1.2 * t)
        
        val wave = 0.6 * sin(2.0 * Math.PI * freqFundamental * t) +
                   0.25 * sin(2.0 * Math.PI * freqFundamental * 2.01 * t) +
                   0.1 * sin(2.0 * Math.PI * freqFundamental * 3.02 * t) +
                   0.05 * sin(2.0 * Math.PI * freqFundamental * 4.03 * t)

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
        audioTrack.play()
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun formatTime(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}