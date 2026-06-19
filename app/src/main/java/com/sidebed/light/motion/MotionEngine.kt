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
 *    (a deliberate pick-up). Once on, ANY motion above [MotionConfig.idleThreshold]
 *    keeps it alive; brightness climbs only when shaking past [MotionConfig.moveThreshold].
 *  - Brightness *accumulates* the harder/longer you shake (a gradual per-second climb)
 *    and is peak-held — it only ever grows; it never fades back while on.
 *
 * The off-timer runs only while the phone is *completely idle* (below idleThreshold); any
 * motion resets it. After [MotionConfig.offDelayMs] fully idle it calls [onIdle], which
 * resets the held peak so the next pick-up starts again from the floor.
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

        if (!active) {
            // OFF: a deliberate pick-up (high threshold) turns the light on at the floor.
            if (magnitude >= config.activationThreshold) {
                active = true
                lastSignificantAt = now
                emitted = (emitted + growth(magnitude, config, dtSeconds)).coerceIn(0f, 1f)
                onLight(emitted)
            }
        } else {
            // ON: ANY motion above the idle floor keeps it alive (brightness climbs only
            // when shaking past moveThreshold). It turns off only after the phone has been
            // *completely* idle for the whole off-delay.
            if (magnitude >= config.idleThreshold) {
                lastSignificantAt = now
                emitted = (emitted + growth(magnitude, config, dtSeconds)).coerceIn(0f, 1f)
                onLight(emitted)
            } else if (now - lastSignificantAt >= config.offDelayMs) {
                active = false
                emitted = 0f
                onIdle()
            }
        }
    }

    /** Per-sample brightness increase; only motion past moveThreshold contributes. */
    private fun growth(magnitude: Float, config: MotionConfig, dtSeconds: Float): Float {
        if (magnitude < config.moveThreshold) return 0f
        val span = (config.shakeThreshold - config.moveThreshold).coerceAtLeast(0.1f) *
            SHAKE_DETECTION_DIVISOR
        val shakeStrength = ((magnitude - config.moveThreshold) / span).coerceIn(0f, 1f)
        return shakeStrength * GROWTH_PER_SECOND * dtSeconds
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
