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
import kotlin.math.sqrt

class ShakeDetector(
    private val context: Context,
    private val onShake: () -> Unit
) : SensorEventListener {

    private var lastShakeTime = 0L

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val animatorScale = try {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1.0f
            )
        } catch (_: Exception) {
            1.0f
        }
        if (animatorScale <= 0f) return

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
    val currentOnShake by rememberUpdatedState(onShake)
    val detector = remember(context) { ShakeDetector(context) { currentOnShake() } }

    DisposableEffect(context) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        if (sensorManager != null && accelerometer != null) {
            sensorManager.registerListener(detector, accelerometer, SensorManager.SENSOR_DELAY_UI)
            LumenLogger.d("Sensors") { "Registered ShakeDetector accelerometer listener" }
        }

        onDispose {
            sensorManager?.unregisterListener(detector)
            LumenLogger.d("Sensors") { "Unregistered ShakeDetector accelerometer listener" }
        }
    }

    return detector
}
