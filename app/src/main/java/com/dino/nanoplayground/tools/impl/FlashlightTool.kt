package com.dino.nanoplayground.tools.impl

import android.content.Context
import android.hardware.camera2.CameraManager
import com.dino.nanoplayground.tools.DeviceTool
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Controls the device's torch (flashlight) via [CameraManager.setTorchMode].
 * No special permission is required on API 33+.
 *
 * Expected params: `{"on":"true"}` or `{"on":"false"}`
 */
@Singleton
class FlashlightTool @Inject constructor(
    @ApplicationContext private val context: Context,
) : DeviceTool {

    override val name = "flashlight"
    override val description = "Turn the device flashlight (torch) on or off."
    override val paramsSchema = """{"on":"true|false"}"""

    private val cameraManager by lazy {
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    override suspend fun execute(params: Map<String, String>): String {
        val on = params["on"]?.trim()?.lowercase() == "true"
        return try {
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return "No camera with a flashlight was found on this device."
            cameraManager.setTorchMode(cameraId, on)
            if (on) "Flashlight turned on." else "Flashlight turned off."
        } catch (e: Exception) {
            "Failed to toggle flashlight: ${e.message}"
        }
    }
}
