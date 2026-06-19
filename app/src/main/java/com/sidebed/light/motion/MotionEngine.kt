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
 *  - magnitude >= [MotionConfig.activationThreshold] turns the light on at the floor
 *    (a deliberate pick-up); once on, [MotionConfig.moveThreshold] keeps it alive.
 *  - Beyond that, brightness *accumulates* the harder/longer you shake — shake
 *    detection is 1.5x less sensitive than on/off detection, and the climb is a
 *    gradual per-second increment (not an instant jump to the shake's level).
 *  - Brightness is peak-held: it only ever grows; it never fades back while on.
 *
 * After [MotionConfig.offDelayMs] with no significant motion it calls [onIdle], which
 * resets the held peak so the next movement starts again from the floor.
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
    private var lastSampleAt = 0L

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
        lastSampleAt = 0L
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
        val dtSeconds = if (lastSampleAt == 0L) {
            DEFAULT_DT_SECONDS
        } else {
            (now - lastSampleAt).coerceIn(1L, 100L) / 1000f
        }
        lastSampleAt = now

        // Turning on needs a more significant move (a pick-up); staying on is easier.
        val onThreshold = if (active) config.moveThreshold else config.activationThreshold
        if (magnitude >= onThreshold) {
            // Shake detection for the ramp is 3x less sensitive than on/off detection:
            // turning the light on stays easy, but it takes a much bigger shake to climb.
            val span = (config.shakeThreshold - config.moveThreshold).coerceAtLeast(0.1f) *
                SHAKE_DETECTION_DIVISOR
            val shakeStrength = ((magnitude - config.moveThreshold) / span).coerceIn(0f, 1f)
            // Gradual peak-hold: brightness accumulates the longer/harder you shake and
            // never fades back. It resets only when the light turns off (idle branch).
            emitted = (emitted + shakeStrength * GROWTH_PER_SECOND * dtSeconds).coerceIn(0f, 1f)
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

        // Shake detection for the brightness ramp is 1.5x less sensitive than on/off detection
        // (was 3x — halved so it takes ~50% less shake to climb).
        private const val SHAKE_DETECTION_DIVISOR = 1.5f

        // Brightness added per second at a full-strength shake (reaches max in ~1-2s of good
        // shaking). Higher = climbs faster. Lower = more gradual.
        private const val GROWTH_PER_SECOND = 1.0f

        private const val DEFAULT_DT_SECONDS = 0.02f
    }
}
