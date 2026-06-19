package com.sidebed.light.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sidebed.light.SidebedState
import com.sidebed.light.ui.theme.NightSurfaceHigh
import com.sidebed.light.ui.theme.WarmAmber
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: SidebedViewModel,
    onOpenSettings: () -> Unit,
    onOpenRedLight: () -> Unit,
    onActivate: () -> Unit,
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val armed by SidebedState.isArmed.collectAsStateWithLifecycle()
    val light by SidebedState.lightIntensity.collectAsStateWithLifecycle()
    val motion by SidebedState.motionLevel.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Sidebed Light") },
                actions = {
                    IconButton(onClick = onOpenRedLight) {
                        Icon(Icons.Rounded.Bedtime, contentDescription = "Red light")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (armed) "Active" else "Tap to activate",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = if (armed) {
                        "Move your phone to light up. Shake for full brightness."
                    } else {
                        "Arm the light, then movement near your bed turns it on."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            ActivationOrb(armed = armed, light = light, motion = motion, onClick = onActivate)

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (settings.scheduleEnabled) {
                    Text(
                        text = "Auto ${formatTime(settings.scheduleStartMinutes)} – " +
                            formatTime(settings.scheduleEndMinutes),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    text = "Mode: " + if (settings.lightMode.name == "RED_SCREEN") "Red screen" else "Torch (LED)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun ActivationOrb(armed: Boolean, light: Float, motion: Float, onClick: () -> Unit) {
    val target = if (armed) max(light, motion * 0.5f) else 0f
    val glow by animateFloatAsState(targetValue = target, label = "glow")

    val transition = rememberInfiniteTransition(label = "breathe")
    val breathe by transition.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Reverse),
        label = "breatheValue",
    )
    val scale = if (armed) breathe else 1f

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(300.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .clickable(onClick = onClick),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            val radius = r * (0.6f + 0.4f * glow)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        WarmAmber.copy(alpha = 0.15f + 0.6f * glow),
                        Color.Transparent,
                    ),
                    radius = radius,
                ),
                radius = radius,
            )
        }
        Surface(
            shape = CircleShape,
            color = if (armed) WarmAmber.copy(alpha = 0.16f) else NightSurfaceHigh,
            border = BorderStroke(1.dp, WarmAmber.copy(alpha = 0.35f + 0.5f * glow)),
            modifier = Modifier.size(168.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.Bedtime,
                    contentDescription = null,
                    tint = WarmAmber,
                    modifier = Modifier.size(60.dp),
                )
            }
        }
    }
}
