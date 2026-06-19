package com.sidebed.light

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.sidebed.light.data.settingsRepository
import com.sidebed.light.ui.theme.SidebedLightTheme

/**
 * A manual, full-screen red night light — melatonin-friendly because it avoids
 * blue light. Drag up/down to set brightness; tap to exit. Stays on and shows
 * over the lock screen.
 */
class RedLightActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        setContent {
            SidebedLightTheme {
                RedLightScreen(onExit = { finish() })
            }
        }
    }
}

@Composable
private fun RedLightScreen(onExit: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    var level by remember { mutableFloatStateOf(0.6f) }

    // Seed from the saved red-brightness preference.
    LaunchedEffect(Unit) {
        val pct = runCatching { context.settingsRepository.current().redBrightnessPct }.getOrDefault(60)
        level = (pct / 100f).coerceIn(0.05f, 1f)
    }
    // Drive the backlight to match.
    LaunchedEffect(level) {
        activity?.let {
            val params = it.window.attributes
            params.screenBrightness = level.coerceIn(0.02f, 1f)
            it.window.attributes = params
        }
    }

    val red = Color(red = 0.16f + 0.84f * level, green = 0f, blue = 0f)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(red)
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, dragAmount ->
                    level = (level - dragAmount / 1500f).coerceIn(0.05f, 1f)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onExit() })
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.red_hint),
            color = Color(0x55FFFFFF),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(24.dp),
        )
    }
}
