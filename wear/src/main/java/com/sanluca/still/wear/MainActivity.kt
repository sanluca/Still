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
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
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

private const val PREFS_NAME = "still_wear_prefs"
private const val KEY_PREP_TIME = "prep_time_seconds"

fun getSavedPrepTime(context: Context): Int {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getInt(KEY_PREP_TIME, 0)
}

fun savePrepTime(context: Context, secs: Int) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putInt(KEY_PREP_TIME, secs).apply()
}

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

// Vibrazione per l'orologio con fallback diretto di sistema
fun triggerHapticGong(context: Context) {
    try {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
                } catch (e: Exception) {
                    vibrator.vibrate(VibrationEffect.createOneShot(1000L, VibrationEffect.DEFAULT_AMPLITUDE))
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(1000L, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(1000L)
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

@Composable
fun WearMeditationApp() {
    val context = LocalContext.current

    var selectedTimeInSeconds by remember { mutableStateOf(300) } // Default 5 min
    
    var prepTimeSeconds by remember { mutableStateOf(getSavedPrepTime(context)) }
    var isPreparing by remember { mutableStateOf(false) }
    var prepTimeLeftSeconds by remember { mutableStateOf(0) }

    var timeLeftInSeconds by remember { mutableStateOf(selectedTimeInSeconds) }

    var isOvertime by remember { mutableStateOf(false) }
    var overtimeSeconds by remember { mutableStateOf(0) }

    var isTimerRunning by remember { mutableStateOf(false) }
    var sessionStartTime by remember { mutableStateOf<Instant?>(null) }

    LaunchedEffect(Unit) {
        try {
            val messageClient = Wearable.getMessageClient(context)
            messageClient.addListener { messageEvent ->
                if (messageEvent.path == "/update_settings") {
                    val prepSec = String(messageEvent.data).toIntOrNull() ?: 0
                    prepTimeSeconds = prepSec
                    savePrepTime(context, prepSec)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun updateTimeSelection(newTime: Int) {
        selectedTimeInSeconds = newTime
        timeLeftInSeconds = if (newTime == -1) 0 else newTime
    }

    val progress = remember(timeLeftInSeconds, selectedTimeInSeconds) {
        if (selectedTimeInSeconds > 0) {
            timeLeftInSeconds.toFloat() / selectedTimeInSeconds.toFloat()
        } else {
            1f
        }
    }

    val backgroundColor = Color(0xFF12232E)
    val overtimeColor = Color(0xFFE5A93C)
    val prepColor = Color(0xFF80CBC4)

    LaunchedEffect(isTimerRunning) {
        if (isTimerRunning) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "StillWear::TimerWakeLock")
            
            try {
                wakeLock.acquire(12 * 60 * 60 * 1000L)

                // --- 1. CONTO ALLA ROVESCIA DI PREPARAZIONE ---
                if (sessionStartTime == null && prepTimeSeconds > 0) {
                    isPreparing = true
                    prepTimeLeftSeconds = prepTimeSeconds
                    
                    while (prepTimeLeftSeconds > 0 && isTimerRunning) {
                        delay(1000L)
                        prepTimeLeftSeconds -= 1
                    }
                    isPreparing = false
                }

                if (!isTimerRunning) return@LaunchedEffect

                // --- 2. INIZIO EFFETTIVO MEDITAZIONE CON GONG AUDIO E VIBRAZIONE ---
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
        } else {
            isPreparing = false
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

    val isInitialState = !isTimerRunning && !isOvertime && (timeLeftInSeconds == selectedTimeInSeconds || (selectedTimeInSeconds == -1 && timeLeftInSeconds == 0))

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        ScalingLazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ================= PAGINA 1: CERCHIO E TIMER AL CENTRO OTTICO =================
            item {
                Text(
                    text = if (isPreparing) "Preparati..." else if (isOvertime) "Extra ✨" else "Vipassana",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isPreparing) prepColor else if (isOvertime) overtimeColor else Color(0xFF88A0A8)
                )
            }

            // CERCHIO GRANDE (155dp)
            item {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(155.dp)
                ) {
                    if (isPreparing) {
                        CircularProgressIndicator(
                            modifier = Modifier.fillMaxSize(),
                            color = prepColor,
                            strokeWidth = 6.dp
                        )
                    } else if (isOvertime) {
                        CircularProgressIndicator(
                            progress = { 1f },
                            modifier = Modifier.fillMaxSize(),
                            color = overtimeColor,
                            strokeWidth = 6.dp,
                            strokeCap = StrokeCap.Round
                        )
                    } else if (selectedTimeInSeconds != -1) {
                        CircularProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxSize(),
                            color = Color(0xFF4A707A),
                            strokeWidth = 6.dp,
                            strokeCap = StrokeCap.Round
                        )
                    } else {
                        CircularProgressIndicator(
                            progress = { 1f },
                            modifier = Modifier.fillMaxSize(),
                            color = Color(0xFF4A707A).copy(alpha = 0.5f),
                            strokeWidth = 4.dp
                        )
                    }

                    if (isPreparing) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Preparati", fontSize = 11.sp, color = prepColor)
                            Text("${prepTimeLeftSeconds}s", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = prepColor)
                        }
                    } else {
                        Text(
                            text = if (isOvertime) "+${formatTime(overtimeSeconds)}" else if (selectedTimeInSeconds == -1 && !isTimerRunning && timeLeftInSeconds == 0) "∞" else formatTime(timeLeftInSeconds),
                            fontSize = if (isOvertime) 26.sp else 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isOvertime) overtimeColor else Color.White
                        )
                    }
                }
            }

            // PULSANTI PRINCIPALI (INIZIA / PAUSA / SALVA)
            item {
                if (isOvertime) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Button(
                            onClick = { saveAndFinishSession(includeExtra = true) },
                            modifier = Modifier.height(34.dp).fillMaxWidth(0.9f),
                            colors = ButtonDefaults.buttonColors(containerColor = overtimeColor)
                        ) {
                            Text("Salva +Extra (+${overtimeSeconds / 60}m)", fontSize = 10.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            OutlinedButton(
                                onClick = { 
                                    isTimerRunning = false
                                    isOvertime = false
                                    overtimeSeconds = 0
                                    timeLeftInSeconds = if (selectedTimeInSeconds == -1) 0 else selectedTimeInSeconds
                                    sessionStartTime = null
                                },
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Resetta", fontSize = 10.sp)
                            }

                            OutlinedButton(
                                onClick = { saveAndFinishSession(includeExtra = false) },
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Solo Previsto", fontSize = 10.sp)
                            }
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
                            Text(if (isTimerRunning) "Pausa" else "Inizia", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }

                        if (!isInitialState && !isTimerRunning) {
                            OutlinedButton(
                                onClick = { 
                                    timeLeftInSeconds = if (selectedTimeInSeconds == -1) 0 else selectedTimeInSeconds
                                    sessionStartTime = null 
                                },
                                modifier = Modifier.height(38.dp)
                            ) {
                                Text("Resetta", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            // ================= PAGINA 2: IMPOSTAZIONI DURATA E PREPARAZIONE =================
            if (!isTimerRunning && !isOvertime) {
                item {
                    Spacer(modifier = Modifier.height(20.dp))
                    HorizontalDivider(modifier = Modifier.fillMaxWidth(0.6f), color = Color(0xFF4A707A))
                    Spacer(modifier = Modifier.height(10.dp))
                }

                item {
                    Text("⚙️ Impostazioni", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF88A0A8))
                }

                item {
                    Spacer(modifier = Modifier.height(6.dp))
                }

                // 1. REGOLAZIONE DURATA MEDITAZIONE
                item {
                    Text("Durata Meditazione:", fontSize = 10.sp, color = Color.Gray)
                }

                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        FilterChip(
                            selected = selectedTimeInSeconds == 300,
                            onClick = { updateTimeSelection(300) },
                            label = { Text("5m", fontSize = 11.sp) }
                        )

                        OutlinedButton(
                            onClick = { if (selectedTimeInSeconds > 60) updateTimeSelection(selectedTimeInSeconds - 60) },
                            enabled = selectedTimeInSeconds > 60,
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("-1m", fontSize = 11.sp)
                        }

                        OutlinedButton(
                            onClick = { updateTimeSelection(selectedTimeInSeconds + 60) },
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("+1m", fontSize = 11.sp)
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // 2. REGOLAZIONE TEMPO PREPARAZIONE (Prep)
                item {
                    Text("Preparazione (Gong): ${prepTimeSeconds}s", fontSize = 10.sp, color = prepColor)
                }

                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        OutlinedButton(
                            onClick = { 
                                if (prepTimeSeconds >= 5) {
                                    prepTimeSeconds -= 5
                                    savePrepTime(context, prepTimeSeconds)
                                }
                            },
                            enabled = prepTimeSeconds >= 5,
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("-5s", fontSize = 11.sp)
                        }

                        OutlinedButton(
                            onClick = { 
                                prepTimeSeconds += 5
                                savePrepTime(context, prepTimeSeconds)
                            },
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("+5s", fontSize = 11.sp)
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(20.dp))
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