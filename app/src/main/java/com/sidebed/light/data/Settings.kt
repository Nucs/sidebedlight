package com.sidebed.light.data

/** Which light source the bedside light drives. */
enum class LightMode {
    /** The camera flash LED. Works with the screen off; white only. */
    TORCH,

    /** A full-screen red glow. Melatonin-friendly; needs the screen on. */
    RED_SCREEN,
}

/**
 * All user-tunable parameters. Defaults are chosen for a calm bedside experience:
 * a dim floor on gentle movement, full brightness on a real shake, and a 7-second
 * auto-off once motion settles.
 */
data class SidebedSettings(
    /** Lowest torch output (% of device max) when motion is just above threshold. */
    val minBrightnessPct: Int = 8,
    /** Highest torch output (% of device max) on a full shake. */
    val maxBrightnessPct: Int = 100,
    /** How easily ongoing motion is detected (keep-alive). Higher = smaller movements count. */
    val sensitivityPct: Int = 45,
    /** Motion needed to first turn the light on, as a % of the keep-alive threshold.
     *  200 = a deliberate pick-up (~2x more than what keeps it on). */
    val activationThresholdPct: Int = 200,
    /** How hard you must shake to reach max brightness. Higher = harder. */
    val shakeStrengthPct: Int = 55,
    /** Seconds of insignificant motion before the light turns off. */
    val offDelaySeconds: Int = 7,
    /** Hold a partial wake lock so sensing continues with the screen off. */
    val wakeLockEnabled: Boolean = true,
    /** Turning the volume all the way down disarms the light. */
    val volumeOffGesture: Boolean = true,

    /** Enable the nightly auto on/off window. */
    val scheduleEnabled: Boolean = false,
    /** Auto-arm time, in minutes from midnight (default 23:00). */
    val scheduleStartMinutes: Int = 23 * 60,
    /** Auto-disarm time, in minutes from midnight (default 06:00). */
    val scheduleEndMinutes: Int = 6 * 60,
    /** Weekdays the schedule runs on, as a bitmask (bit i = day, 0=Sunday..6=Saturday).
     *  Default = all 7 days. */
    val scheduleDaysMask: Int = 0b111_1111,

    /** Torch (LED) or red screen. */
    val lightMode: LightMode = LightMode.TORCH,
    /** Red screen brightness ceiling (% ), used in RED_SCREEN mode. */
    val redBrightnessPct: Int = 60,
)
