package com.sidebed.light.light

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/**
 * A full-screen red glow drawn as a system overlay — a melatonin-friendly
 * alternative to the white LED. Requires the "Display over other apps" permission.
 *
 * Limitation: an overlay cannot reliably power the screen on by itself, so red
 * mode is best used while the screen is on. All calls must run on the main thread.
 */
class RedOverlayController(private val context: Context) : LightController {

    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var view: View? = null

    override val isAvailable: Boolean
        get() = Settings.canDrawOverlays(context)

    override fun setIntensity(fraction: Float) {
        val f = fraction.coerceIn(0f, 1f)
        if (f <= 0f) {
            turnOff()
            return
        }
        if (!isAvailable) return
        ensureView()
        val v = view ?: return
        // Dark red -> bright red as intensity rises, plus matching backlight level.
        val red = (40 + f * 215).toInt().coerceIn(0, 255)
        v.setBackgroundColor(Color.rgb(red, 0, 0))
        (v.layoutParams as? WindowManager.LayoutParams)?.let { lp ->
            lp.screenBrightness = f.coerceIn(0.02f, 1f)
            runCatching { windowManager.updateViewLayout(v, lp) }
        }
    }

    override fun turnOff() {
        val v = view ?: return
        runCatching { windowManager.removeView(v) }
        view = null
    }

    override fun release() = turnOff()

    // FLAG_TURN_SCREEN_ON / FLAG_SHOW_WHEN_LOCKED are deprecated for windows but are the
    // only (best-effort) way to wake the screen from a non-activity overlay.
    @Suppress("DEPRECATION")
    private fun ensureView() {
        if (view != null) return
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.OPAQUE,
        ).apply { gravity = Gravity.CENTER }
        val v = View(context).apply { setBackgroundColor(Color.BLACK) }
        runCatching { windowManager.addView(v, params) }
        view = v
    }
}
