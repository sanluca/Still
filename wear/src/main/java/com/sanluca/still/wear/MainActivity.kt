package com.sanluca.still.wear
import androidx.fragment.app.FragmentActivity
import androidx.wear.ambient.AmbientModeSupport
import android.app.Activity
import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.sanluca.still.wear.R
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import com.google.android.gms.wearable.Wearable
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import android.content.ContextWrapper

fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

class MainActivity : FragmentActivity(), AmbientModeSupport.AmbientCallbackProvider {

    private lateinit var ambientController: AmbientModeSupport.AmbientController
    private val isAmbientState = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ambientController = AmbientModeSupport.attach(this)
        setContent {
            WearMeditationApp(isAmbient = isAmbientState.value)
        }
    }

    override fun getAmbientCallback(): AmbientModeSupport.AmbientCallback = object : AmbientModeSupport.AmbientCallback() {
        override fun onEnterAmbient(ambientDetails: Bundle?) {
            super.onEnterAmbient(ambientDetails)
            isAmbientState.value = true
        }

        override fun onExitAmbient() {
            super.onExitAmbient()
            isAmbientState.value = false
        }
    }
}

private const val PREFS_NAME = "still_wear_prefs"
private const val KEY_PREP_TIME = "prep_time_seconds"
private const val KEY_GONG_FREQ = "gong_frequency_hz"
private const val KEY_PENDING_SESSIONS = "pending_watch_sessions"

fun getSavedPrepTime(context: Context): Int {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getInt(KEY_PREP_TIME, 0)
}

fun savePrepTime(context: Context, secs: Int) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putInt(KEY_PREP_TIME, secs).apply()
}

fun getSavedGongFrequency(context: Context): Double {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getFloat(KEY_GONG_FREQ, 432.0f).toDouble()
}

fun saveGongFrequency(context: Context, freqHz: Double) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putFloat(KEY_GONG_FREQ, freqHz.toFloat()).apply()
}

fun savePendingSessionOnWatch(context: Context, raw: String) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val existingList = prefs.getStringSet(KEY_PENDING_SESSIONS, emptySet())?.toMutableList() ?: mutableListOf()
    if (!existingList.contains(raw)) {
        existingList.add(raw)
        while (existingList.size > 20) {
            existingList.removeAt(0)
        }
        prefs.edit().putStringSet(KEY_PENDING_SESSIONS, existingList.toSet()).apply()
    }
}

fun flushPendingSessionsToPhone(context: Context) {
    try {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val pendingSet = prefs.getStringSet(KEY_PENDING_SESSIONS, emptySet()) ?: emptySet()
        if (pendingSet.isNotEmpty()) {
            val messageClient = Wearable.getMessageClient(context)
            val nodeClient = Wearable.getNodeClient(context)
            nodeClient.connectedNodes.addOnSuccessListener { nodes ->
                if (nodes.isNotEmpty()) {
                    for (raw in pendingSet) {
                        for (node in nodes) {
                            messageClient.sendMessage(node.id, "/meditation_completed", raw.toByteArray())
                        }
                    }
                    prefs.edit().remove(KEY_PENDING_SESSIONS).apply()
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun sendSessionToPhone(context: Context, durationSec: Int, startInstant: Instant, endInstant: Instant) {
    try {
        val startEpochSec = startInstant.epochSecond
        val endEpochSec = endInstant.epochSecond
        val raw = "$durationSec|$startEpochSec|$endEpochSec"

        val messageClient = Wearable.getMessageClient(context)
        val nodeClient = Wearable.getNodeClient(context)
        nodeClient.connectedNodes.addOnSuccessListener { nodes ->
            if (nodes.isNotEmpty()) {
                for (node in nodes) {
                    messageClient.sendMessage(node.id, "/meditation_completed", raw.toByteArray())
                }
            } else {
                savePendingSessionOnWatch(context, raw)
            }
        }.addOnFailureListener {
            savePendingSessionOnWatch(context, raw)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun triggerHapticGong(context: Context) {
    try {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        if (vibrator != null && vibrator.hasVibrator()) {
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build()

            val timings = longArrayOf(0, 500, 200, 800)
            val amplitudes = intArrayOf(0, 255, 0, 255)
            val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
            vibrator.vibrate(effect, audioAttributes)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

@Composable
fun WearMeditationApp(isAmbient: Boolean = false) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedTimeInSeconds by remember { mutableStateOf(300) } // Default 5 min
    
    var prepTimeSeconds by remember { mutableStateOf(getSavedPrepTime(context)) }
    var gongFreqHz by remember { mutableStateOf(getSavedGongFrequency(context)) }

    var isPreparing by remember { mutableStateOf(false) }
    var prepTimeLeftSeconds by remember { mutableStateOf(0) }

    var timeLeftInSeconds by remember { mutableStateOf(selectedTimeInSeconds) }

    var isOvertime by remember { mutableStateOf(false) }
    var overtimeSeconds by remember { mutableStateOf(0) }

    var isTimerRunning by remember { mutableStateOf(false) }
    var sessionStartTime by remember { mutableStateOf<Instant?>(null) }

    // Broadcast receiver for timer ticks
    val receiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == StillTimerService.ACTION_TIMER_TICK) {
                    val runningFromService = intent.getBooleanExtra(StillTimerService.EXTRA_IS_RUNNING, false)
                    // Ignora messaggi broadcast fantasma solo se sappiamo che il timer è fermo lato servizio
                    if (!runningFromService && !isTimerRunning && sessionStartTime == null) {
                        return
                    }
                    isPreparing = intent.getBooleanExtra(StillTimerService.EXTRA_IS_PREPARING, false)
                    prepTimeLeftSeconds = intent.getIntExtra(StillTimerService.EXTRA_PREP_LEFT, 0)
                    timeLeftInSeconds = intent.getIntExtra(StillTimerService.EXTRA_TIME_LEFT, 0)
                    isOvertime = intent.getBooleanExtra(StillTimerService.EXTRA_IS_OVERTIME_STATE, false)
                    overtimeSeconds = intent.getIntExtra(StillTimerService.EXTRA_OVERTIME_SECONDS_STATE, 0)
                    isTimerRunning = runningFromService
                    val initialTimeState = intent.getIntExtra(StillTimerService.EXTRA_INITIAL_TIME_STATE, 300)
                    selectedTimeInSeconds = initialTimeState

                    if (sessionStartTime == null && !isPreparing && isTimerRunning) {
                        sessionStartTime = Instant.now()
                    }
                }
            }
        }
    }

    DisposableEffect(context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, IntentFilter(StillTimerService.ACTION_TIMER_TICK), Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, IntentFilter(StillTimerService.ACTION_TIMER_TICK))
        }
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    LaunchedEffect(Unit) {
        try {
            flushPendingSessionsToPhone(context)

            val messageClient = Wearable.getMessageClient(context)
            messageClient.addListener { messageEvent ->
                if (messageEvent.path == "/update_settings") {
                    val raw = String(messageEvent.data)
                    val parts = raw.split("|")
                    if (parts.isNotEmpty()) {
                        val prepSec = parts[0].toIntOrNull() ?: 0
                        prepTimeSeconds = prepSec
                        savePrepTime(context, prepSec)
                    }
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

    val backgroundColor = if (isAmbient && isTimerRunning) Color.Black else Color(0xFF12232E)
    val overtimeColor = if (isAmbient && isTimerRunning) Color.White else Color(0xFFE5A93C)
    val prepColor = if (isAmbient && isTimerRunning) Color.White else Color(0xFF80CBC4)

    LaunchedEffect(isTimerRunning) {
        val activity = context.findActivity()
        if (isTimerRunning) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (sessionStartTime == null) {
                val serviceIntent = Intent(context, StillTimerService::class.java).apply {
                    action = StillTimerService.ACTION_START
                    putExtra(StillTimerService.EXTRA_FREQ, gongFreqHz)
                    putExtra(StillTimerService.EXTRA_TIME, selectedTimeInSeconds)
                    putExtra(StillTimerService.EXTRA_INITIAL_TIME, selectedTimeInSeconds)
                    putExtra(StillTimerService.EXTRA_PREP, prepTimeSeconds)
                }
                context.startForegroundService(serviceIntent)
            } else {
                // Resume
                val serviceIntent = Intent(context, StillTimerService::class.java).apply {
                    action = StillTimerService.ACTION_START
                    putExtra(StillTimerService.EXTRA_FREQ, gongFreqHz)
                    putExtra(StillTimerService.EXTRA_TIME, timeLeftInSeconds)
                    putExtra(StillTimerService.EXTRA_INITIAL_TIME, selectedTimeInSeconds)
                    putExtra(StillTimerService.EXTRA_PREP, -1) // -1 tells the service it's a resume so no start gong
                    putExtra(StillTimerService.EXTRA_IS_OVERTIME, isOvertime)
                    putExtra(StillTimerService.EXTRA_OVERTIME_SECONDS, overtimeSeconds)
                }
                context.startForegroundService(serviceIntent)
            }
        } else {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            val stopIntent = Intent(context, StillTimerService::class.java).apply {
                action = StillTimerService.ACTION_STOP
            }
            context.startService(stopIntent)
            isPreparing = false
        }
    }

    fun saveAndFinishSession(includeExtra: Boolean) {
        val plannedSec = if (selectedTimeInSeconds > 0) {
            if (isOvertime) selectedTimeInSeconds else (selectedTimeInSeconds - timeLeftInSeconds)
        } else {
            timeLeftInSeconds
        }
        val totalSec = if (includeExtra && isOvertime) (plannedSec + overtimeSeconds) else plannedSec
        val end = Instant.now()
        val start = end.minusSeconds(totalSec.toLong())

        if (totalSec >= 10) {
            sendSessionToPhone(context, totalSec, start, end)
            Toast.makeText(context, context.getString(R.string.toast_sync_success), Toast.LENGTH_SHORT).show()
        }

        val stopIntent = Intent(context, StillTimerService::class.java).apply {
            action = StillTimerService.ACTION_STOP
        }
        context.startService(stopIntent)

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
                    text = if (isPreparing) stringResource(R.string.status_preparing) else if (isOvertime) stringResource(R.string.status_extra) else stringResource(R.string.status_vipassana),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isPreparing) prepColor else if (isOvertime) overtimeColor else Color(0xFF88A0A8)
                )
            }

            // CERCHIO GRANDE (135dp) CON TASTI [-] E [+] AI LATI
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (!isTimerRunning && !isOvertime) {
                        OutlinedButton(
                            onClick = { if (selectedTimeInSeconds > 60) updateTimeSelection(selectedTimeInSeconds - 60) },
                            enabled = selectedTimeInSeconds > 60,
                            modifier = Modifier.size(34.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("-", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Spacer(modifier = Modifier.width(34.dp))
                    }

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(135.dp)
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
                                Text(stringResource(R.string.text_prepare), fontSize = 11.sp, color = prepColor)
                                Text("${prepTimeLeftSeconds}s", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = prepColor)
                            }
                        } else {
                            Text(
                                text = if (isOvertime) "+${formatTime(overtimeSeconds)}" else if (selectedTimeInSeconds == -1 && !isTimerRunning && timeLeftInSeconds == 0) "∞" else formatTime(timeLeftInSeconds),
                                fontSize = if (isOvertime) 24.sp else 30.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isOvertime) overtimeColor else Color.White
                            )
                        }
                    }

                    if (!isTimerRunning && !isOvertime) {
                        OutlinedButton(
                            onClick = { updateTimeSelection(if (selectedTimeInSeconds == -1) 60 else selectedTimeInSeconds + 60) },
                            modifier = Modifier.size(34.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("+", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Spacer(modifier = Modifier.width(34.dp))
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
                            Text(stringResource(R.string.button_save_extra, (overtimeSeconds / 60)), fontSize = 10.sp, color = Color.Black, fontWeight = FontWeight.Bold)
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
                                Text(stringResource(R.string.button_reset), fontSize = 10.sp)
                            }

                            OutlinedButton(
                                onClick = { saveAndFinishSession(includeExtra = false) },
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text(stringResource(R.string.button_only_planned), fontSize = 10.sp)
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
                            Text(if (isTimerRunning) stringResource(R.string.button_pause) else stringResource(R.string.button_start), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }

                        if (!isInitialState && !isTimerRunning) {
                            OutlinedButton(
                                onClick = { 
                                    timeLeftInSeconds = if (selectedTimeInSeconds == -1) 0 else selectedTimeInSeconds
                                    sessionStartTime = null 
                                },
                                modifier = Modifier.height(38.dp)
                            ) {
                                Text(stringResource(R.string.button_reset), fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            // ================= PAGINA 2: IMPOSTAZIONI DURATA, PREPARAZIONE E NON DISTURBARE =================
            if (!isTimerRunning && !isOvertime) {
                item {
                    Spacer(modifier = Modifier.height(20.dp))
                    HorizontalDivider(modifier = Modifier.fillMaxWidth(0.6f), color = Color(0xFF4A707A))
                    Spacer(modifier = Modifier.height(10.dp))
                }

                item {
                    Text(stringResource(R.string.settings_title), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF88A0A8))
                }

                item {
                    Spacer(modifier = Modifier.height(6.dp))
                }

                // 1. FREQUENZA CAMPANA TIBETANA SULL'OROLOGIO
                item {
                    Text(stringResource(R.string.settings_gong_sound), fontSize = 10.sp, color = Color.Gray)
                }

                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        FilterChip(
                            selected = gongFreqHz == 432.0,
                            onClick = { 
                                gongFreqHz = 432.0
                                saveGongFrequency(context, 432.0)
                                coroutineScope.launch(Dispatchers.Default) {
                                    playGongSound(432.0, repeatCount = 1)
                                }
                            },
                            label = { Text("432Hz", fontSize = 10.sp) }
                        )

                        FilterChip(
                            selected = gongFreqHz == 528.0,
                            onClick = { 
                                gongFreqHz = 528.0
                                saveGongFrequency(context, 528.0)
                                coroutineScope.launch(Dispatchers.Default) {
                                    playGongSound(528.0, repeatCount = 1)
                                }
                            },
                            label = { Text("528Hz", fontSize = 10.sp) }
                        )

                        FilterChip(
                            selected = gongFreqHz == 216.0,
                            onClick = { 
                                gongFreqHz = 216.0
                                saveGongFrequency(context, 216.0)
                                coroutineScope.launch(Dispatchers.Default) {
                                    playGongSound(216.0, repeatCount = 1)
                                }
                            },
                            label = { Text("216Hz", fontSize = 10.sp) }
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // 3. REGOLAZIONE TEMPO PREPARAZIONE (Prep)
                item {
                    Text(stringResource(R.string.settings_prep_time, prepTimeSeconds), fontSize = 10.sp, color = prepColor)
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
                    Spacer(modifier = Modifier.height(10.dp))
                }

                item {
                    Text(
                        text = stringResource(R.string.settings_app_version),
                        fontSize = 9.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(bottom = 20.dp)
                    )
                }
            }
        }
    }
}

fun formatTime(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}