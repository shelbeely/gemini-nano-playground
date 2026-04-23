package com.dino.nanoplayground.tools.impl

import android.app.NotificationManager
import android.content.Context
import com.dino.nanoplayground.tools.DeviceTool
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Toggles Do-Not-Disturb mode via [NotificationManager.setInterruptionFilter].
 * Requires the ACCESS_NOTIFICATION_POLICY special permission.  If not granted, returns
 * a message directing the user to the relevant settings screen.
 *
 * Expected params: `{"mode":"all|priority|alarms|none"}`
 *  - all      → interruptions enabled (DnD off)
 *  - priority → only priority notifications break through
 *  - alarms   → only alarms break through
 *  - none     → total silence
 */
@Singleton
class DoNotDisturbTool @Inject constructor(
    @ApplicationContext private val context: Context,
) : DeviceTool {

    override val name = "do_not_disturb"
    override val description = "Set Do-Not-Disturb mode. Modes: all (off), priority, alarms, none (total silence)."
    override val paramsSchema = """{"mode":"all|priority|alarms|none"}"""

    private val notificationManager by lazy {
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    override suspend fun execute(params: Map<String, String>): String {
        if (!notificationManager.isNotificationPolicyAccessGranted) {
            return "Do-Not-Disturb access is required. " +
                "Please go to Settings > Sound > Do Not Disturb > NanoPlayground and grant access."
        }
        val filter = when (params["mode"]?.trim()?.lowercase()) {
            "all"      -> NotificationManager.INTERRUPTION_FILTER_ALL
            "priority" -> NotificationManager.INTERRUPTION_FILTER_PRIORITY
            "alarms"   -> NotificationManager.INTERRUPTION_FILTER_ALARMS
            "none"     -> NotificationManager.INTERRUPTION_FILTER_NONE
            else       -> return "Unknown mode '${params["mode"]}'. Use: all, priority, alarms, or none."
        }
        return try {
            notificationManager.setInterruptionFilter(filter)
            "Do-Not-Disturb set to '${params["mode"]}'."
        } catch (e: Exception) {
            "Failed to set Do-Not-Disturb: ${e.message}"
        }
    }
}
