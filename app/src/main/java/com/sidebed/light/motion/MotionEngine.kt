package com.sidebed.light.motion

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlin.math.sqrt

/**
 * Reads the accelerometer, removes gravity with a low-pass filter, and turns the
 * residual (linear acceleration) into a light intensity:
 *
 *  - magnitude >= [MotionConfig.moveThreshold]  -> light on, scaled toward...
 *  - magnitude >= [MotionConfig.shakeThreshold] -> full intensity.
 *
 * After [MotionConfig.offDelayMs] with no significant motion it calls [onIdle].
 * Callbacks are delivered on the main thread.
 */
class MotionEngine(
    context: Context,
    private val configProvider: () -> MotionConfig,
    private val onLight: (Float) -> Unit,
    private val onIdle: () -> Unit,
    private val onMotion: (Float) -> Unit,
) : SensorEventListener {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val handler = Handler(Looper.getMainLooper())

    val isAvailable: Boolean get() = accelerometer != null

    private val gravity = FloatArray(3)
    private var hasGravity = false
    private var emitted = 0f
    private var active = false
    private var lastSignificantAt = 0L

    fun start() {
        val sensor = accelerometer ?: return
        reset()
        sensorManager.registerListener(this, sensor, SAMPLING_PERIOD_US, handler)
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        reset()
    }

    private fun reset() {
        hasGravity = false
        emitted = 0f
        active = false
        lastSignificantAt = 0L
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        val config = configProvider()

        // Low-pass to isolate gravity, then subtract to get linear acceleration.
        if (!hasGravity) {
            gravity[0] = event.values[0]
            gravity[1] = event.values[1]
            gravity[2] = event.values[2]
            hasGravity = true
        } else {
            for (i in 0..2) {
                gravity[i] = GRAVITY_ALPHA * gravity[i] + (1 - GRAVITY_ALPHA) * event.values[i]
            }
        }
        val lx = event.values[0] - gravity[0]
        val ly = event.values[1] - gravity[1]
        val lz = event.values[2] - gravity[2]
        val magnitude = sqrt(lx * lx + ly * ly + lz * lz)

        // Normalised motion for the live UI visualisation.
        onMotion((magnitude / config.shakeThreshold).coerceIn(0f, 1f))

        val now = SystemClock.elapsedRealtime()
        if (magnitude >= config.moveThreshold) {
            val span = (config.shakeThreshold - config.moveThreshold).coerceAtLeast(0.1f)
            val target = ((magnitude - config.moveThreshold) / span).coerceIn(0f, 1f)
            // Snap up instantly, ease down — bright on shake, no sample flicker.
            emitted = if (target > emitted) target else emitted * 0.6f + target * 0.4f
            lastSignificantAt = now
            active = true
            onLight(emitted)
        } else if (active && now - lastSignificantAt >= config.offDelayMs) {
            active = false
            emitted = 0f
            onIdle()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { /* no-op */ }

    companion object {
        private const val GRAVITY_ALPHA = 0.8f
        private const val SAMPLING_PERIOD_US = 20_000 // ~50 Hz
    }
}
