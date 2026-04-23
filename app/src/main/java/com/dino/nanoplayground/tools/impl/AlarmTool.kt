package com.dino.nanoplayground.tools.impl

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import com.dino.nanoplayground.tools.DeviceTool
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Creates an alarm via the system Clock app using [AlarmClock.ACTION_SET_ALARM].
 * No special permission is required; the Clock app handles the alarm creation.
 *
 * Expected params: `{"hour":"0-23","minute":"0-59","title":"label"}`
 */
@Singleton
class AlarmTool @Inject constructor(
    @ApplicationContext private val context: Context,
) : DeviceTool {

    override val name = "alarm"
    override val description = "Set an alarm. Provide hour (0-23), minute (0-59), and an optional title."
    override val paramsSchema = """{"hour":"0-23","minute":"0-59","title":"optional label"}"""

    override suspend fun execute(params: Map<String, String>): String {
        val hour = params["hour"]?.trim()?.toIntOrNull()
            ?: return "Missing or invalid 'hour' parameter (expected 0-23)."
        val minute = params["minute"]?.trim()?.toIntOrNull()
            ?: return "Missing or invalid 'minute' parameter (expected 0-59)."
        if (hour !in 0..23) return "Hour must be between 0 and 23."
        if (minute !in 0..59) return "Minute must be between 0 and 59."

        val title = params["title"]?.trim() ?: "Alarm"
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_MESSAGE, title)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Alarm set for %02d:%02d — "%s".".format(hour, minute, title)
        } catch (e: Exception) {
            "Failed to set alarm: ${e.message}"
        }
    }
}
