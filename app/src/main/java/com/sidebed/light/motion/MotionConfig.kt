package com.sidebed.light.motion

import com.sidebed.light.data.SidebedSettings

/** Tuned thresholds (m/s² of linear acceleration) derived from user settings. */
data class MotionConfig(
    /** Motion that keeps the light on once active (and lights up at least the floor). */
    val moveThreshold: Float,
    /** Larger motion needed to first turn the light on from off (the pick-up gesture). */
    val activationThreshold: Float,
    /** Motion at/above which the light reaches full intensity. */
    val shakeThreshold: Float,
    /** Idle time before the light turns off. */
    val offDelayMs: Long,
)

fun SidebedSettings.toMotionConfig(): MotionConfig {
    val sens = sensitivityPct.coerceIn(0, 100) / 100f
    // More sensitive -> lower threshold so smaller movements register.
    val move = 1.6f - sens * (1.6f - 0.18f)
    val strength = shakeStrengthPct.coerceIn(0, 100) / 100f
    val shake = 3.5f + strength * (14f - 3.5f)
    // Activation is a multiple of the keep-alive threshold (default 2x = a pick-up).
    val activation = move * (activationThresholdPct.coerceIn(100, 400) / 100f)
    return MotionConfig(
        moveThreshold = move,
        activationThreshold = activation.coerceAtLeast(move),
        shakeThreshold = shake.coerceAtLeast(move + 0.8f),
        offDelayMs = offDelaySeconds.coerceAtLeast(1) * 1000L,
    )
}
