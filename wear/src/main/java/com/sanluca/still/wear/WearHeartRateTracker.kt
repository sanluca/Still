package com.sanluca.still.wear

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

internal const val PERMISSION_READ_HEART_RATE = "android.permission.health.READ_HEART_RATE"

internal fun requiredHeartRatePermission(): String =
    if (Build.VERSION.SDK_INT >= 36) PERMISSION_READ_HEART_RATE else Manifest.permission.BODY_SENSORS

internal fun hasHeartRatePermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, requiredHeartRatePermission()) == PackageManager.PERMISSION_GRANTED

internal data class HeartRateSummary(
    val startBpm: Int? = null,
    val endBpm: Int? = null,
    val avgBpm: Int? = null
)

internal object WearHeartRateStore {
    private val hrLock = Any()
    private var hrStartBpm: Int? = null
    private var hrEndBpm: Int? = null
    private var hrSum: Long = 0L
    private var hrCount: Int = 0

    fun getSummary(): HeartRateSummary = synchronized(hrLock) {
        val avg = if (hrCount > 0) (hrSum.toDouble() / hrCount.toDouble()).roundToInt() else null
        HeartRateSummary(startBpm = hrStartBpm, endBpm = hrEndBpm, avgBpm = avg)
    }

    fun clear() = synchronized(hrLock) {
        hrStartBpm = null
        hrEndBpm = null
        hrSum = 0L
        hrCount = 0
    }

    fun recordSample(bpm: Int) = synchronized(hrLock) {
        if (hrStartBpm == null) {
            hrStartBpm = bpm
        }
        hrEndBpm = bpm
        hrSum += bpm.toLong()
        hrCount += 1
    }
}

internal class WearHeartRateTracker(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private var hrJob: Job? = null
    private val hrSensorLock = Any()
    private var activeHrSensorManager: SensorManager? = null
    private var activeHrListener: SensorEventListener? = null

    fun startSamplingIfPermitted(isSamplingAllowed: () -> Boolean) {
        if (!isSamplingAllowed() || hrJob?.isActive == true) return
        if (!shouldTrackHeartRate(context)) return
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        val hrSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE) ?: return

        hrJob = scope.launch(Dispatchers.Default) {
            var burstIndex = 0
            while (isSamplingAllowed() && shouldTrackHeartRate(context)) {
                val targetSamples = if (burstIndex == 0) 3 else 2
                var collectedInBurst = 0
                val listener = object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent?) {
                        if (event == null || event.sensor?.type != Sensor.TYPE_HEART_RATE) return
                        // Accetta anche SENSOR_STATUS_ACCURACY_LOW; scarta solo nessun contatto o inaffidabile
                        if (event.accuracy == SensorManager.SENSOR_STATUS_NO_CONTACT ||
                            event.accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE
                        ) {
                            return
                        }
                        val bpm = event.values.firstOrNull()?.roundToInt() ?: return
                        if (bpm in 30..220) {
                            WearHeartRateStore.recordSample(bpm)
                            collectedInBurst += 1
                        }
                    }

                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
                }

                try {
                    val registered = synchronized(hrSensorLock) {
                        if (!isSamplingAllowed()) {
                            false
                        } else {
                            activeHrSensorManager = sensorManager
                            activeHrListener = listener
                            try {
                                sensorManager.registerListener(listener, hrSensor, SensorManager.SENSOR_DELAY_UI)
                            } catch (_: SecurityException) {
                                false
                            }
                        }
                    }
                    if (!registered) {
                        unregisterActiveListener()
                        break
                    }
                    val maxWaitSteps = 40 // ~20s massimo per finestra di campionamento
                    var step = 0
                    while (isSamplingAllowed() && step < maxWaitSteps && collectedInBurst < targetSamples) {
                        delay(500L)
                        step++
                    }
                } catch (_: SecurityException) {
                    break
                } finally {
                    synchronized(hrSensorLock) {
                        if (activeHrListener === listener) {
                            activeHrListener = null
                            activeHrSensorManager = null
                        }
                    }
                    try {
                        sensorManager.unregisterListener(listener)
                    } catch (_: Exception) {
                    }
                }

                burstIndex++
                delay(45_000L)
            }
        }
    }

    fun stop() {
        hrJob?.cancel()
        hrJob = null
        unregisterActiveListener()
    }

    private fun unregisterActiveListener() {
        synchronized(hrSensorLock) {
            val listener = activeHrListener
            val manager = activeHrSensorManager
            activeHrListener = null
            activeHrSensorManager = null
            if (listener != null && manager != null) {
                try {
                    manager.unregisterListener(listener)
                } catch (_: Exception) {
                }
            }
        }
    }
}
