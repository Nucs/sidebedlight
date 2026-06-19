package com.sidebed.light.service

import android.content.Context
import android.media.AudioManager
import android.media.VolumeProvider
import android.media.session.MediaSession
import android.media.session.PlaybackState

/**
 * Captures hardware volume-key presses via a [MediaSession] with a remote [VolumeProvider].
 * Unlike a ContentObserver, this sees a "volume down" press even when the volume is already
 * at minimum (where the value never changes) — which is exactly the case we want to catch.
 *
 * While active it passes normal up/down presses through to the media stream (so the volume
 * keys still work as usual, with the system slider) and fires [onVolumeDownAtMin] when the
 * user presses down while already at 0.
 *
 * Best-effort: the session only receives volume keys while it is the media session the
 * system routes to (typically when no other app is actively playing). When it isn't, the
 * companion [VolumeWatcher] still catches a normal lower-to-zero.
 */
class VolumeKeyWatcher(
    private val context: Context,
    private val onVolumeDownAtMin: () -> Unit,
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var session: MediaSession? = null

    fun start() {
        if (session != null) return
        val stream = AudioManager.STREAM_MUSIC
        val provider = object : VolumeProvider(
            VolumeProvider.VOLUME_CONTROL_RELATIVE,
            audioManager.getStreamMaxVolume(stream),
            audioManager.getStreamVolume(stream),
        ) {
            override fun onAdjustVolume(direction: Int) {
                when {
                    direction < 0 ->
                        if (audioManager.getStreamVolume(stream) <= 0) {
                            onVolumeDownAtMin()
                        } else {
                            audioManager.adjustStreamVolume(stream, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                        }

                    direction > 0 ->
                        audioManager.adjustStreamVolume(stream, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                }
                currentVolume = audioManager.getStreamVolume(stream)
            }
        }

        session = MediaSession(context, "SidebedLightVolume").apply {
            setPlaybackToRemote(provider)
            // A "playing" state makes this the active session the system routes volume to.
            setPlaybackState(
                PlaybackState.Builder()
                    .setState(PlaybackState.STATE_PLAYING, 0L, 1f)
                    .setActions(PlaybackState.ACTION_PLAY_PAUSE)
                    .build(),
            )
            isActive = true
        }
    }

    fun stop() {
        session?.let {
            it.isActive = false
            it.release()
        }
        session = null
    }
}
