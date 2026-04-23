package com.dino.nanoplayground.tools.impl

import android.content.Context
import android.media.AudioManager
import com.dino.nanoplayground.tools.DeviceTool
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sets the device volume for a given audio stream using [AudioManager].
 * Requires the MODIFY_AUDIO_SETTINGS permission (declared in the manifest).
 *
 * Expected params: `{"stream":"ring|media|alarm","level":"0-15"}`
 */
@Singleton
class VolumeTool @Inject constructor(
    @ApplicationContext private val context: Context,
) : DeviceTool {

    override val name = "volume"
    override val description = "Set device volume. Streams: ring, media, alarm. Level: 0-15."
    override val paramsSchema = """{"stream":"ring|media|alarm","level":"0-15"}"""

    private val audioManager by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    override suspend fun execute(params: Map<String, String>): String {
        val stream = when (params["stream"]?.trim()?.lowercase()) {
            "ring"  -> AudioManager.STREAM_RING
            "alarm" -> AudioManager.STREAM_ALARM
            else    -> AudioManager.STREAM_MUSIC
        }
        val level = params["level"]?.trim()?.toIntOrNull()
            ?: return "Missing or invalid 'level' parameter (expected 0-15)."
        val maxVolume = audioManager.getStreamMaxVolume(stream)
        val clamped = level.coerceIn(0, maxVolume)
        return try {
            audioManager.setStreamVolume(stream, clamped, 0)
            "Volume for ${params["stream"] ?: "media"} stream set to $clamped."
        } catch (e: Exception) {
            "Failed to set volume: ${e.message}"
        }
    }
}
