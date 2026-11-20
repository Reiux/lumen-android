package com.reiux.lumen.util

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.sqrt

class ShakeDetector(
    context: Context,
    private val onShake: () -> Unit
) : SensorEventListener {

    private val appContext = context.applicationContext ?: context
    private var lastShakeTime = 0L

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_ACCELEROMETER || event.values.size < 3) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        val gX = x / SensorManager.GRAVITY_EARTH
        val gY = y / SensorManager.GRAVITY_EARTH
        val gZ = z / SensorManager.GRAVITY_EARTH

        val gForce = sqrt(gX * gX + gY * gY + gZ * gZ)

        // Standard Android shake threshold (2.7g) so normal handling never triggers it
        if (gForce > 2.7f) {
            val now = System.currentTimeMillis()
            if (now - lastShakeTime >= 5000L) {
                val animatorScale = try {
                    Settings.Global.getFloat(
                        appContext.contentResolver,
                        Settings.Global.ANIMATOR_DURATION_SCALE,
                        1.0f
                    )
                } catch (_: Exception) {
                    1.0f
                }
                if (animatorScale <= 0f) return

                lastShakeTime = now
                LumenLogger.i("EasterEgg") { "Shake detected (gForce=%.2f)! Playing lamp flicker animation".format(gForce) }
                onShake()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}

@Composable
fun rememberShakeDetector(onShake: () -> Unit): ShakeDetector {
    val context = LocalContext.current
    val appContext = context.applicationContext ?: context
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnShake by rememberUpdatedState(onShake)
    val detector = remember(appContext) { ShakeDetector(appContext) { currentOnShake() } }

    DisposableEffect(appContext, lifecycleOwner) {
        val sensorManager = appContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        var isRegistered = false

        fun registerIfNeeded() {
            if (!isRegistered && sensorManager != null && accelerometer != null) {
                sensorManager.registerListener(detector, accelerometer, SensorManager.SENSOR_DELAY_UI)
                isRegistered = true
                LumenLogger.d("Sensors") { "Registered ShakeDetector accelerometer listener" }
            }
        }

        fun unregisterIfNeeded() {
            if (isRegistered) {
                sensorManager?.unregisterListener(detector)
                isRegistered = false
                LumenLogger.d("Sensors") { "Unregistered ShakeDetector accelerometer listener" }
            }
        }

        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            registerIfNeeded()
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> registerIfNeeded()
                Lifecycle.Event.ON_PAUSE -> unregisterIfNeeded()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            unregisterIfNeeded()
        }
    }

    return detector
}
