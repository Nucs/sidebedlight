package com.sidebed.light.service

import android.content.Context
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings

/**
 * Watches the system volume and fires [onVolumeZero] when the media or ring stream
 * is taken all the way down to 0 (a downward transition, so an already-silent phone
 * at arm time doesn't trigger it). Uses a ContentObserver on system settings, which
 * keeps working with the screen off while the service is alive.
 */
class VolumeWatcher(
    private val context: Context,
    private val onVolumeZero: () -> Unit,
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val handler = Handler(Looper.getMainLooper())
    private val streams = intArrayOf(AudioManager.STREAM_MUSIC, AudioManager.STREAM_RING)
    private val last = HashMap<Int, Int>()
    private var started = false

    private val observer = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) = check()
    }

    fun ensureStarted() {
        if (started) return
        started = true
        for (s in streams) last[s] = volumeOf(s)
        context.contentResolver.registerContentObserver(
            Settings.System.CONTENT_URI, true, observer,
        )
    }

    fun stop() {
        if (!started) return
        started = false
        runCatching { context.contentResolver.unregisterContentObserver(observer) }
        last.clear()
    }

    private fun volumeOf(stream: Int): Int = runCatching {
        audioManager.getStreamVolume(stream)
    }.getOrDefault(0)

    private fun check() {
        for (s in streams) {
            val current = volumeOf(s)
            val previous = last[s] ?: current
            last[s] = current
            if (previous > 0 && current == 0) {
                onVolumeZero()
                return
            }
        }
    }
}
