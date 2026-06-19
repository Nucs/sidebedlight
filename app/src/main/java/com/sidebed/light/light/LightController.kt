package com.sidebed.light.light

/** Abstraction over a controllable light source (LED torch or red screen). */
interface LightController {
    /** Whether this light source can be used on this device / configuration. */
    val isAvailable: Boolean

    /** Set the output level. [fraction] is 0..1; 0 turns the light off. */
    fun setIntensity(fraction: Float)

    /** Turn the light off but keep the controller usable. */
    fun turnOff()

    /** Turn off and release any held resources. */
    fun release()
}
