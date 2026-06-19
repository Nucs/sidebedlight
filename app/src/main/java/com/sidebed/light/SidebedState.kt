package com.sidebed.light

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Lightweight in-memory runtime state, shared between [service.SidebedLightService]
 * (the producer) and the Compose UI (the consumer). Both live in the same process,
 * so a plain object with [MutableStateFlow]s is enough — no IPC required.
 */
object SidebedState {
    /** True while the foreground service is running and watching for motion. */
    val isArmed = MutableStateFlow(false)

    /** True while the light (torch or red screen) is currently emitting. */
    val isLightOn = MutableStateFlow(false)

    /** Current light output, 0..1, after the min/max brightness mapping. */
    val lightIntensity = MutableStateFlow(0f)

    /** Smoothed motion magnitude, 0..1, for live UI visualisation. */
    val motionLevel = MutableStateFlow(0f)

    /** True when the active session was started by the nightly auto schedule. */
    val armedBySchedule = MutableStateFlow(false)
}
