package com.sidebed.light.light

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Drives the camera flash LED. On Android 13+ devices that report more than one
 * strength level it uses [CameraManager.turnOnTorchWithStrengthLevel] for true
 * variable brightness; otherwise it falls back to on/off via [CameraManager.setTorchMode].
 *
 * Note: torch control needs no runtime permission.
 */
class TorchController(context: Context) : LightController {

    private val cameraManager =
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    private val cameraId: String? = findTorchCamera()

    /** Device max strength level (>= 1). A value of 1 means on/off only. */
    private val maxLevel: Int = resolveMaxLevel()

    private var isOn = false
    private var lastLevel = -1

    override val isAvailable: Boolean get() = cameraId != null

    /** True when this device supports continuous brightness. */
    val supportsVariableBrightness: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && maxLevel > 1

    override fun setIntensity(fraction: Float) {
        val id = cameraId ?: return
        val f = fraction.coerceIn(0f, 1f)
        if (f <= 0f) {
            turnOff()
            return
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && maxLevel > 1) {
                val level = max(1, (f * maxLevel).roundToInt()).coerceAtMost(maxLevel)
                if (isOn && level == lastLevel) return
                cameraManager.turnOnTorchWithStrengthLevel(id, level)
                lastLevel = level
                isOn = true
            } else if (!isOn) {
                cameraManager.setTorchMode(id, true)
                isOn = true
            }
        } catch (e: CameraAccessException) {
            isOn = false
        } catch (e: IllegalArgumentException) {
            isOn = false
        }
    }

    override fun turnOff() {
        val id = cameraId ?: return
        if (!isOn) return
        try {
            cameraManager.setTorchMode(id, false)
        } catch (e: CameraAccessException) {
            // ignore — best effort
        } finally {
            isOn = false
            lastLevel = -1
        }
    }

    override fun release() = turnOff()

    private fun findTorchCamera(): String? = try {
        val ids = cameraManager.cameraIdList
        // Prefer a back-facing camera with a flash; fall back to any flash unit.
        ids.firstOrNull { id ->
            val c = cameraManager.getCameraCharacteristics(id)
            c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        } ?: ids.firstOrNull { id ->
            cameraManager.getCameraCharacteristics(id)
                .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
    } catch (e: CameraAccessException) {
        null
    }

    private fun resolveMaxLevel(): Int {
        val id = cameraId ?: return 1
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return 1
        return try {
            cameraManager.getCameraCharacteristics(id)
                .get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL) ?: 1
        } catch (e: CameraAccessException) {
            1
        }
    }
}
