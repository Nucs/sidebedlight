package com.sidebed.light.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.sidebed.light.R
import com.sidebed.light.SidebedApp
import com.sidebed.light.SidebedState
import com.sidebed.light.data.LightMode
import com.sidebed.light.data.SettingsRepository
import com.sidebed.light.data.SidebedSettings
import com.sidebed.light.data.settingsRepository
import com.sidebed.light.light.LightController
import com.sidebed.light.light.RedOverlayController
import com.sidebed.light.light.TorchController
import com.sidebed.light.motion.MotionEngine
import com.sidebed.light.motion.toMotionConfig
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * The bedside light's brain. While running as a foreground service it watches the
 * accelerometer, drives the chosen light source, listens for the volume-off gesture,
 * and keeps a persistent "Turn off" notification. Started/stopped via [ServiceController].
 */
class SidebedLightService : LifecycleService() {

    private lateinit var settingsRepo: SettingsRepository
    @Volatile private var settings: SidebedSettings = SidebedSettings()

    private var torch: TorchController? = null
    private var red: RedOverlayController? = null
    private var active: LightController? = null

    private var motionEngine: MotionEngine? = null
    private var volumeWatcher: VolumeWatcher? = null
    private var volumeKeyWatcher: VolumeKeyWatcher? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private var started = false
    private var lastPublishedMotion = -1f
    private var lastPublishedLight = -1f

    // --- Motion callbacks ---------------------------------------------------

    private val onLight: (Float) -> Unit = { intensity ->
        // Shared floor; the ceiling depends on the active light (torch max vs red max).
        val floor = settings.minBrightnessPct / 100f
        val ceiling = when (settings.lightMode) {
            LightMode.RED_SCREEN -> settings.redBrightnessPct / 100f
            LightMode.TORCH -> settings.maxBrightnessPct / 100f
        }
        val lo = min(floor, ceiling)
        val hi = max(floor, ceiling)
        val frac = (lo + intensity * (hi - lo)).coerceIn(0f, 1f)
        active?.setIntensity(frac)
        if (!SidebedState.isLightOn.value) SidebedState.isLightOn.value = true
        publishLight(frac)
    }

    private val onIdle: () -> Unit = {
        active?.turnOff()
        SidebedState.isLightOn.value = false
        publishLight(0f)
    }

    private val onMotion: (Float) -> Unit = { publishMotion(it) }

    // --- Lifecycle ----------------------------------------------------------

    override fun onCreate() {
        super.onCreate()
        settingsRepo = settingsRepository
        torch = TorchController(this)
        red = RedOverlayController(this)
        lifecycleScope.launch {
            settingsRepo.settings.collect { applySettings(it) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ServiceController.ACTION_DISARM -> {
                stopSelf()
                return START_NOT_STICKY
            }

            ServiceController.ACTION_RESHOW -> {
                // The user swiped the notification away — put it straight back so the light
                // can always be turned off. Guard with `started` so it can't resurrect the
                // service after a real disarm.
                if (started) startForegroundNotification(SidebedState.armedBySchedule.value) else stopSelf()
                return START_STICKY
            }

            else -> {
                val fromSchedule = intent?.getBooleanExtra(ServiceController.EXTRA_FROM_SCHEDULE, false) ?: false
                startArmed(fromSchedule)
                return START_STICKY
            }
        }
    }

    private fun startArmed(fromSchedule: Boolean) {
        if (started) {
            if (fromSchedule) SidebedState.armedBySchedule.value = true
            return
        }
        started = true
        SidebedState.isArmed.value = true
        SidebedState.armedBySchedule.value = fromSchedule

        startForegroundNotification(fromSchedule)
        if (settings.wakeLockEnabled) acquireWakeLock()
        setupLightController()
        startMotion()
        startVolumeWatcher()
    }

    override fun onDestroy() {
        motionEngine?.stop()
        motionEngine = null
        volumeWatcher?.stop()
        volumeWatcher = null
        volumeKeyWatcher?.stop()
        volumeKeyWatcher = null
        // Deactivation must leave no light on, regardless of mode — release both sources.
        torch?.release()
        red?.release()
        releaseWakeLock()
        started = false
        SidebedState.isArmed.value = false
        SidebedState.isLightOn.value = false
        SidebedState.lightIntensity.value = 0f
        SidebedState.motionLevel.value = 0f
        SidebedState.armedBySchedule.value = false
        super.onDestroy()
    }

    // --- Settings -----------------------------------------------------------

    private fun applySettings(updated: SidebedSettings) {
        val previous = settings
        settings = updated
        if (!started) return

        if (updated.wakeLockEnabled && wakeLock?.isHeld != true) acquireWakeLock()
        if (!updated.wakeLockEnabled) releaseWakeLock()

        if (updated.lightMode != previous.lightMode) setupLightController()

        if (updated.volumeOffGesture) {
            volumeWatcher?.ensureStarted()
            volumeKeyWatcher?.start()
        } else {
            volumeWatcher?.stop()
            volumeKeyWatcher?.stop()
        }
    }

    private fun setupLightController() {
        active?.turnOff()
        active = when (settings.lightMode) {
            LightMode.RED_SCREEN -> {
                val overlay = red
                if (overlay != null && overlay.isAvailable) {
                    overlay
                } else {
                    // No overlay permission -> fall back to the LED.
                    torch
                }
            }

            LightMode.TORCH -> torch
        }
    }

    // --- Motion & volume ----------------------------------------------------

    private fun startMotion() {
        val engine = MotionEngine(
            context = this,
            configProvider = { settings.toMotionConfig() },
            onLight = onLight,
            onIdle = onIdle,
            onMotion = onMotion,
        )
        engine.start()
        motionEngine = engine
    }

    private fun startVolumeWatcher() {
        volumeWatcher = VolumeWatcher(this) {
            if (settings.volumeOffGesture) ServiceController.disarm(applicationContext)
        }
        volumeKeyWatcher = VolumeKeyWatcher(this) {
            if (settings.volumeOffGesture) ServiceController.disarm(applicationContext)
        }
        if (settings.volumeOffGesture) {
            volumeWatcher?.ensureStarted()
            volumeKeyWatcher?.start()
        }
    }

    // --- Notification -------------------------------------------------------

    private fun startForegroundNotification(fromSchedule: Boolean) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(fromSchedule), type)
    }

    // Per spec, tapping the notification body (not just the action) turns the light off.
    // The PendingIntent targets a BroadcastReceiver that only stops the service — it never
    // launches an Activity, so the Android 12 notification-trampoline rule does not apply.
    @SuppressLint("LaunchActivityFromNotification")
    private fun buildNotification(fromSchedule: Boolean): Notification {
        val turnOff = PendingIntent.getBroadcast(
            this,
            0,
            Intent(this, LightActionReceiver::class.java).setAction(LightActionReceiver.ACTION_TURN_OFF),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        // Fires if the user swipes the notification away (Android 13+ allows it even for an
        // ongoing FGS notification); ACTION_RESHOW re-posts it so it stays put.
        val reshow = PendingIntent.getService(
            this,
            2,
            Intent(this, SidebedLightService::class.java).setAction(ServiceController.ACTION_RESHOW),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = getString(
            if (fromSchedule) R.string.notif_text_scheduled else R.string.notif_text,
        )
        return NotificationCompat.Builder(this, SidebedApp.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_light)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(text)
            .setContentIntent(turnOff) // tapping the body turns off, per spec
            .addAction(R.drawable.ic_power_off, getString(R.string.action_turn_off), turnOff)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setDeleteIntent(reshow) // keep it non-dismissable by re-posting on swipe
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    // --- Wake lock ----------------------------------------------------------

    // Intentionally no timeout: a bedside light is meant to sense all night (on a charger).
    // The lock is always released in onDestroy / when the wake-lock setting is turned off.
    @SuppressLint("WakelockTimeout")
    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(PowerManager::class.java)
        val lock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
            setReferenceCounted(false)
        }
        runCatching { lock.acquire() }
        wakeLock = lock
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) runCatching { it.release() } }
        wakeLock = null
    }

    // --- State publishing (throttled) --------------------------------------

    private fun publishMotion(value: Float) {
        if (value == 0f || abs(value - lastPublishedMotion) >= 0.02f) {
            lastPublishedMotion = value
            SidebedState.motionLevel.value = value
        }
    }

    private fun publishLight(value: Float) {
        if (value == 0f || abs(value - lastPublishedLight) >= 0.02f) {
            lastPublishedLight = value
            SidebedState.lightIntensity.value = value
        }
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val WAKE_LOCK_TAG = "SidebedLight::sensing"
    }
}
