package com.dino.nanoplayground.tools

import com.dino.nanoplayground.tools.impl.AlarmTool
import com.dino.nanoplayground.tools.impl.BrightnessTool
import com.dino.nanoplayground.tools.impl.DoNotDisturbTool
import com.dino.nanoplayground.tools.impl.FlashlightTool
import com.dino.nanoplayground.tools.impl.MediaPlaybackTool
import com.dino.nanoplayground.tools.impl.VolumeTool
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton registry that owns every [DeviceTool] and exposes helpers used by
 * [ToolOrchestrator]:
 *  - [systemPromptSnippet] builds the compact tool-list injected into the model's system turn
 *  - [execute] dispatches a parsed tool call to the matching [DeviceTool]
 */
@Singleton
class ToolRegistry @Inject constructor(
    flashlightTool: FlashlightTool,
    volumeTool: VolumeTool,
    brightnessTool: BrightnessTool,
    alarmTool: AlarmTool,
    doNotDisturbTool: DoNotDisturbTool,
    mediaPlaybackTool: MediaPlaybackTool,
) {

    val tools: List<DeviceTool> = listOf(
        flashlightTool,
        volumeTool,
        brightnessTool,
        alarmTool,
        doNotDisturbTool,
        mediaPlaybackTool,
    )

    private val toolMap: Map<String, DeviceTool> = tools.associateBy { it.name }

    /**
     * Returns a compact system-prompt snippet listing all available tools.
     * Kept intentionally short to minimise token usage on Gemini Nano.
     */
    fun systemPromptSnippet(): String = buildString {
        appendLine("You are a helpful assistant with access to the following device controls.")
        appendLine("When you need to use a device control, output ONLY a single JSON line in this exact format and nothing else:")
        appendLine("""{"tool":"<tool_name>","params":{<parameters>}}""")
        appendLine("After receiving a [tool result: ...] line, provide a friendly natural-language reply.")
        appendLine("If no tool is needed, reply normally.")
        appendLine()
        appendLine("Available tools:")
        tools.forEach { tool ->
            appendLine("  ${tool.name}: ${tool.description} Params: ${tool.paramsSchema}")
        }
    }

    /**
     * Dispatches a tool call by [name] with the supplied [params] map.
     * Returns a result string (success or error description) to be re-injected into the prompt.
     */
    suspend fun execute(name: String, params: Map<String, String>): String {
        val tool = toolMap[name]
            ?: return "Unknown tool '$name'. Available tools: ${toolMap.keys.joinToString()}."
        return tool.execute(params)
    }
}
