package com.sidebed.light.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sidebed.light.SidebedApp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "sidebed_settings")

/** Persists [SidebedSettings] with Jetpack DataStore (Preferences). */
class SettingsRepository(private val context: Context) {

    val settings: Flow<SidebedSettings> = context.dataStore.data.map { it.toSettings() }

    /** Read the current settings once (suspending). */
    suspend fun current(): SidebedSettings = settings.first()

    suspend fun update(transform: (SidebedSettings) -> SidebedSettings) {
        context.dataStore.edit { prefs ->
            prefs.writeSettings(transform(prefs.toSettings()))
        }
    }

    private fun Preferences.toSettings(): SidebedSettings {
        val d = SidebedSettings()
        return SidebedSettings(
            minBrightnessPct = this[Keys.MIN_BRIGHTNESS] ?: d.minBrightnessPct,
            maxBrightnessPct = this[Keys.MAX_BRIGHTNESS] ?: d.maxBrightnessPct,
            sensitivityPct = this[Keys.SENSITIVITY] ?: d.sensitivityPct,
            activationThresholdPct = this[Keys.ACTIVATION_THRESHOLD] ?: d.activationThresholdPct,
            shakeStrengthPct = this[Keys.SHAKE_STRENGTH] ?: d.shakeStrengthPct,
            offDelaySeconds = this[Keys.OFF_DELAY] ?: d.offDelaySeconds,
            wakeLockEnabled = this[Keys.WAKE_LOCK] ?: d.wakeLockEnabled,
            volumeOffGesture = this[Keys.VOLUME_GESTURE] ?: d.volumeOffGesture,
            scheduleEnabled = this[Keys.SCHEDULE_ENABLED] ?: d.scheduleEnabled,
            scheduleStartMinutes = this[Keys.SCHEDULE_START] ?: d.scheduleStartMinutes,
            scheduleEndMinutes = this[Keys.SCHEDULE_END] ?: d.scheduleEndMinutes,
            lightMode = this[Keys.LIGHT_MODE]?.let { runCatching { LightMode.valueOf(it) }.getOrNull() }
                ?: d.lightMode,
            redBrightnessPct = this[Keys.RED_BRIGHTNESS] ?: d.redBrightnessPct,
        )
    }

    private fun androidx.datastore.preferences.core.MutablePreferences.writeSettings(s: SidebedSettings) {
        this[Keys.MIN_BRIGHTNESS] = s.minBrightnessPct
        this[Keys.MAX_BRIGHTNESS] = s.maxBrightnessPct
        this[Keys.SENSITIVITY] = s.sensitivityPct
        this[Keys.ACTIVATION_THRESHOLD] = s.activationThresholdPct
        this[Keys.SHAKE_STRENGTH] = s.shakeStrengthPct
        this[Keys.OFF_DELAY] = s.offDelaySeconds
        this[Keys.WAKE_LOCK] = s.wakeLockEnabled
        this[Keys.VOLUME_GESTURE] = s.volumeOffGesture
        this[Keys.SCHEDULE_ENABLED] = s.scheduleEnabled
        this[Keys.SCHEDULE_START] = s.scheduleStartMinutes
        this[Keys.SCHEDULE_END] = s.scheduleEndMinutes
        this[Keys.LIGHT_MODE] = s.lightMode.name
        this[Keys.RED_BRIGHTNESS] = s.redBrightnessPct
    }

    private object Keys {
        val MIN_BRIGHTNESS = intPreferencesKey("min_brightness")
        val MAX_BRIGHTNESS = intPreferencesKey("max_brightness")
        val SENSITIVITY = intPreferencesKey("sensitivity")
        val ACTIVATION_THRESHOLD = intPreferencesKey("activation_threshold")
        val SHAKE_STRENGTH = intPreferencesKey("shake_strength")
        val OFF_DELAY = intPreferencesKey("off_delay")
        val WAKE_LOCK = booleanPreferencesKey("wake_lock")
        val VOLUME_GESTURE = booleanPreferencesKey("volume_gesture")
        val SCHEDULE_ENABLED = booleanPreferencesKey("schedule_enabled")
        val SCHEDULE_START = intPreferencesKey("schedule_start")
        val SCHEDULE_END = intPreferencesKey("schedule_end")
        val LIGHT_MODE = stringPreferencesKey("light_mode")
        val RED_BRIGHTNESS = intPreferencesKey("red_brightness")
    }
}

/** Convenience accessor for the process-wide [SettingsRepository]. */
val Context.settingsRepository: SettingsRepository
    get() = (applicationContext as SidebedApp).settingsRepository
