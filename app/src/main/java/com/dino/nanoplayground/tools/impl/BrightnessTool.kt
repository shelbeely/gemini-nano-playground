package com.dino.nanoplayground.tools.impl

import android.content.Context
import android.provider.Settings
import com.dino.nanoplayground.tools.DeviceTool
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Adjusts the system screen brightness via [Settings.System.SCREEN_BRIGHTNESS].
 * Requires the WRITE_SETTINGS special permission.  If the permission has not been granted,
 * the tool returns a user-friendly message directing the user to Settings.
 *
 * Expected params: `{"level":"0-255"}`
 */
@Singleton
class BrightnessTool @Inject constructor(
    @ApplicationContext private val context: Context,
) : DeviceTool {

    override val name = "brightness"
    override val description = "Set screen brightness level (0 = dimmest, 255 = brightest)."
    override val paramsSchema = """{"level":"0-255"}"""

    override suspend fun execute(params: Map<String, String>): String {
        if (!Settings.System.canWrite(context)) {
            return "WRITE_SETTINGS permission is required to change brightness. " +
                "Please go to Settings > Apps > NanoPlayground > Modify system settings and enable it."
        }
        val level = params["level"]?.trim()?.toIntOrNull()
            ?: return "Missing or invalid 'level' parameter (expected 0-255)."
        val clamped = level.coerceIn(0, 255)
        return try {
            // Disable auto-brightness first so the manual value takes effect
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                clamped
            )
            "Screen brightness set to $clamped."
        } catch (e: Exception) {
            "Failed to set brightness: ${e.message}"
        }
    }
}
