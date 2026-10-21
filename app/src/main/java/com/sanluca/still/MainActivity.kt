package com.sanluca.still

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.feature.ExperimentalMindfulnessSessionApi
import com.sanluca.still.ui.theme.StillTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import kotlin.math.exp
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StillTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MeditationTimerScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

// --- FUNZIONI DI SALVATAGGIO DELLE IMPOSTAZIONI ---
private const val PREFS_NAME = "still_prefs"
private const val KEY_SELECTED_TIME = "selected_time_seconds"
private const val KEY_HEALTH_CONNECTED = "health_connected"

fun saveSelectedTime(context: Context, timeInSeconds: Int) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putInt(KEY_SELECTED_TIME, timeInSeconds).apply()
}

fun getSavedSelectedTime(context: Context): Int {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getInt(KEY_SELECTED_TIME, 600) // Default 10 minuti (600s)
}

fun saveHealthConnectedState(context: Context, isConnected: Boolean) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putBoolean(KEY_HEALTH_CONNECTED, isConnected).apply()
}

fun isHealthConnectedStateSaved(context: Context): Boolean {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getBoolean(KEY_HEALTH_CONNECTED, false)
}

@OptIn(ExperimentalMindfulnessSessionApi::class)
@Composable
fun MeditationTimerScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    // Gestore per Health Connect
    val healthConnectManager = remember { HealthConnectManager(context) }
    var isHealthConnected by remember { mutableStateOf(isHealthConnectedStateSaved(context)) }

    // Verifica automatica dello stato dei permessi all'avvio
    LaunchedEffect(Unit) {
        if (healthConnectManager.hasAllPermissions()) {
            isHealthConnected = true
            saveHealthConnectedState(context, true)
        }
    }

    // Dialog Impostazioni
    var showSettingsDialog by remember { mutableStateOf(false) }
    
    // Launcher per richiedere i permessi di Health Connect
    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) { grantedPermissions ->
        if (grantedPermissions.containsAll(healthConnectManager.permissions)) {
            isHealthConnected = true
            saveHealthConnectedState(context, true)
            Toast.makeText(context, "Google Connessione Salute collegata!", Toast.LENGTH_SHORT).show()
        } else {
            isHealthConnected = false
            saveHealthConnectedState(context, false)
            Toast.makeText(context, "Permesso Connessione Salute non concesso", Toast.LENGTH_SHORT).show()
        }
    }

    // Apri lo Store di Google per installare/aggiornare Connessione Salute se manca
    fun openHealthConnectInPlayStore() {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.apps.healthdata"))
            context.startActivity(intent)
        } catch (e: Exception) {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata"))
            context.startActivity(webIntent)
        }
    }

    // Funzione per avviare il collegamento con Health Connect verificando lo stato del dispositivo
    fun connectToHealth() {
        when (healthConnectManager.getHealthConnectStatus()) {
            HealthConnectClient.SDK_AVAILABLE -> {
                requestPermissionLauncher.launch(healthConnectManager.permissions)
            }
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
                Toast.makeText(
                    context, 
                    "Apertura Play Store per installare/aggiornare Connessione Salute...", 
                    Toast.LENGTH_LONG
                ).show()
                openHealthConnectInPlayStore()
            }
            else -> {
                Toast.makeText(
                    context, 
                    "Connessione Salute non è supportata su questo dispositivo.", 
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // 1. Carichiamo l'orario salvato la volta precedente! Default 10 min (600s). -1 = Infinito.
    var selectedTimeInSeconds by remember { mutableStateOf(getSavedSelectedTime(context)) }
    
    // Tempo attuale che scorre
    var timeLeftInSeconds by remember { mutableStateOf(selectedTimeInSeconds) }
    
    // Stato del timer
    var isTimerRunning by remember { mutableStateOf(false) }

    // Registrazione orario inizio
    var sessionStartTime by remember { mutableStateOf<Instant?>(null) }

    // Salva automaticamente la scelta dell'utente ogni volta che cambia durata
    fun updateTimeSelection(newTime: Int) {
        selectedTimeInSeconds = newTime
        saveSelectedTime(context, newTime)
    }

    // Calcolo percentuale cerchio
    val progress = remember(timeLeftInSeconds, selectedTimeInSeconds) {
        if (selectedTimeInSeconds > 0) {
            timeLeftInSeconds.toFloat() / selectedTimeInSeconds.toFloat()
        } else {
            1f
        }
    }

    // Motore del timer e suono del Gong
    LaunchedEffect(isTimerRunning) {
        if (isTimerRunning) {
            if (sessionStartTime == null) {
                sessionStartTime = Instant.now()
            }

            withContext(Dispatchers.Default) {
                playGongSound()
            }
        }

        while (isTimerRunning) {
            delay(1000L)
            
            if (selectedTimeInSeconds == -1) {
                timeLeftInSeconds += 1 // Infinito
            } else {
                if (timeLeftInSeconds > 0) {
                    timeLeftInSeconds -= 1
                }
                
                // Quando arriva a 0: FINE MEDITAZIONE
                if (timeLeftInSeconds == 0) {
                    isTimerRunning = false
                    
                    withContext(Dispatchers.Default) {
                        playGongSound()
                    }

                    // Sincronizzazione automatica con Google Health Connect
                    val start = sessionStartTime
                    val end = Instant.now()
                    if (start != null && Duration.between(start, end).seconds >= 10) {
                        coroutineScope.launch {
                            if (healthConnectManager.hasAllPermissions()) {
                                val saved = healthConnectManager.saveMeditationSession(start, end)
                                if (saved) {
                                    Toast.makeText(context, "Sessione salvata su Google Salute! 🧘‍♂️", Toast.LENGTH_LONG).show()
                                }
                            }
                            sessionStartTime = null
                        }
                    }
                }
            }
        }
    }

    // Effetto per riallineare il tempo quando si cambia durata
    LaunchedEffect(selectedTimeInSeconds) {
        if (!isTimerRunning) {
            timeLeftInSeconds = if (selectedTimeInSeconds == -1) 0 else selectedTimeInSeconds
            sessionStartTime = null
        }
    }

    val isInitialState = !isTimerRunning && (timeLeftInSeconds == selectedTimeInSeconds || (selectedTimeInSeconds == -1 && timeLeftInSeconds == 0))

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // --- BARRA SUPERIORE CON IMPOSTAZIONI (SX), TITOLO (CENTRO) E CHIUDI X (DX) ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Tasto Impostazioni (Ingranaggio ⚙️) a sinistra
                IconButton(onClick = { showSettingsDialog = true }) {
                    Text("⚙️", fontSize = 20.sp)
                }

                // Titolo e sottotitolo al centro
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Vipassana",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Light),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (selectedTimeInSeconds == -1) "Modalità Senza Limiti" else "Timer di Meditazione",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Tasto Chiudi App (❌) a destra
                IconButton(
                    onClick = {
                        (context as? Activity)?.finish()
                    }
                ) {
                    Text("❌", fontSize = 20.sp)
                }
            }

            // --- CERCHIO CON TIMER AL CENTRO ---
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(260.dp)
            ) {
                if (selectedTimeInSeconds != -1) {
                    CircularProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        strokeWidth = 8.dp,
                        strokeCap = StrokeCap.Round
                    )
                } else {
                    CircularProgressIndicator(
                        progress = { 1f },
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        strokeWidth = 4.dp
                    )
                }

                Text(
                    text = if (selectedTimeInSeconds == -1 && !isTimerRunning && timeLeftInSeconds == 0) {
                        "∞"
                    } else {
                        formatTime(timeLeftInSeconds)
                    },
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = if (selectedTimeInSeconds == -1 && !isTimerRunning && timeLeftInSeconds == 0) 80.sp else 54.sp,
                        fontWeight = FontWeight.Normal
                    )
                )
            }

            // --- CONTROLLI E IMPOSTAZIONI TEMPO ---
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 24.dp)
            ) {
                AnimatedVisibility(
                    visible = isInitialState,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // --- PRESET RAPIDI (Chip) ---
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val presets = listOf(5, 10, 15, 20, 30)
                            presets.forEach { min ->
                                val seconds = min * 60
                                FilterChip(
                                    selected = selectedTimeInSeconds == seconds,
                                    onClick = { updateTimeSelection(seconds) },
                                    label = { Text("${min}m") }
                                )
                            }
                            FilterChip(
                                selected = selectedTimeInSeconds == -1,
                                onClick = { updateTimeSelection(-1) },
                                label = { Text("∞") }
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // --- REGOLAZIONE FINE (+1m, -1m, +5m, -5m) ---
                        if (selectedTimeInSeconds != -1) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = { if (selectedTimeInSeconds > 300) updateTimeSelection(selectedTimeInSeconds - 300) },
                                    enabled = selectedTimeInSeconds > 300
                                ) { Text("-5m") }

                                OutlinedButton(
                                    onClick = { if (selectedTimeInSeconds > 60) updateTimeSelection(selectedTimeInSeconds - 60) },
                                    enabled = selectedTimeInSeconds > 60
                                ) { Text("-1m") }

                                OutlinedButton(
                                    onClick = { updateTimeSelection(selectedTimeInSeconds + 60) }
                                ) { Text("+1m") }

                                OutlinedButton(
                                    onClick = { updateTimeSelection(selectedTimeInSeconds + 300) }
                                ) { Text("+5m") }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }

                // --- PULSANTE PRINCIPALE INIZIA / PAUSA ---
                val canStart = isTimerRunning || selectedTimeInSeconds == -1 || timeLeftInSeconds > 0

                Button(
                    onClick = { 
                        if (!isTimerRunning && sessionStartTime == null) {
                            sessionStartTime = Instant.now()
                        }
                        isTimerRunning = !isTimerRunning 
                    },
                    enabled = canStart,
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier
                        .height(56.dp)
                        .width(220.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isTimerRunning) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = if (isTimerRunning) "Pausa" else "Inizia Meditazione",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium)
                    )
                }

                // --- PULSANTE RESETTA ---
                if (!isInitialState && !isTimerRunning) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { 
                            val start = sessionStartTime
                            val end = Instant.now()
                            if (start != null && Duration.between(start, end).seconds >= 10) {
                                coroutineScope.launch {
                                    if (healthConnectManager.hasAllPermissions()) {
                                        val saved = healthConnectManager.saveMeditationSession(start, end)
                                        if (saved) {
                                            Toast.makeText(context, "Sessione parziale salvata su Google Salute!", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                            timeLeftInSeconds = if (selectedTimeInSeconds == -1) 0 else selectedTimeInSeconds 
                            sessionStartTime = null
                        },
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text("Resetta e Salva Sessione")
                    }
                }
            }
        }

        // --- DIALOG IMPOSTAZIONI ---
        if (showSettingsDialog) {
            AlertDialog(
                onDismissRequest = { showSettingsDialog = false },
                title = @Composable { Text(text = "Impostazioni Still ⚙️") },
                text = @Composable {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Stato Google Connessione Salute:",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isHealthConnected) "✅ Collegato e Attivo" else "❌ Non collegato",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isHealthConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = { connectToHealth() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (isHealthConnected) "Riconnetti / Verifica Permessi 🏥" else "Connetti a Google Salute 🏥")
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Versione App: 1.0.0 (Vipassana Timer)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSettingsDialog = false }) {
                        Text("Chiudi")
                    }
                }
            )
        }
    }
}

// Funzione che sintetizza via codice un suono di campana tibetana / Gong (armonica 432 Hz)
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

@Preview(showBackground = true)
@Composable
fun MeditationTimerPreview() {
    StillTheme {
        MeditationTimerScreen()
    }
}