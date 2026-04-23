package com.dino.nanoplayground.tools.impl

import android.content.Context
import android.media.AudioManager
import android.view.KeyEvent
import com.dino.nanoplayground.tools.DeviceTool
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sends media-key events (play, pause, next, previous) to the active media session via
 * [AudioManager.dispatchMediaKeyEvent].  No special permission is required.
 *
 * Expected params: `{"action":"play|pause|next|previous"}`
 */
@Singleton
class MediaPlaybackTool @Inject constructor(
    @ApplicationContext private val context: Context,
) : DeviceTool {

    override val name = "media_playback"
    override val description = "Control media playback. Actions: play, pause, next, previous."
    override val paramsSchema = """{"action":"play|pause|next|previous"}"""

    private val audioManager by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    override suspend fun execute(params: Map<String, String>): String {
        val keyCode = when (params["action"]?.trim()?.lowercase()) {
            "play"     -> KeyEvent.KEYCODE_MEDIA_PLAY
            "pause"    -> KeyEvent.KEYCODE_MEDIA_PAUSE
            "next"     -> KeyEvent.KEYCODE_MEDIA_NEXT
            "previous" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            else       -> return "Unknown action '${params["action"]}'. Use: play, pause, next, or previous."
        }
        return try {
            audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
            "Media action '${params["action"]}' dispatched."
        } catch (e: Exception) {
            "Failed to dispatch media key: ${e.message}"
        }
    }
}
