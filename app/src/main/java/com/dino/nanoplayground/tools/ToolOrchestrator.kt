package com.dino.nanoplayground.tools

import com.google.mlkit.genai.prompt.GenerativeModel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates prompt-engineered tool use on top of Gemini Nano.
 *
 * Flow for each [runWithTools] call:
 * 1. Prepend the system-prompt snippet from [ToolRegistry] to the user message.
 * 2. Call [GenerativeModel.generateContent] and inspect the response.
 * 3. If the response contains a JSON tool-call block `{"tool":"…","params":{…}}`, extract it,
 *    execute the tool, then re-prompt with the tool result appended as context.
 * 4. Repeat up to [MAX_TOOL_ROUNDS] times.
 * 5. Return the final plain-text response.
 *
 * The [onToolCall] callback is invoked before each tool execution so callers (e.g.
 * [com.dino.nanoplayground.ground.ui.viewmodel.ChatViewModel]) can surface UI feedback.
 */
@Singleton
class ToolOrchestrator @Inject constructor(
    private val generativeModel: GenerativeModel,
    private val registry: ToolRegistry,
) {

    companion object {
        private const val MAX_TOOL_ROUNDS = 5

        // Lenient JSON parser — ignores unknown keys so partial model output is still parseable
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        /**
         * Finds the first balanced JSON object in [text] that contains a "tool" key.
         * Uses brace counting to correctly handle nested objects in the params value.
         */
        private fun findFirstJsonObject(text: String): String? {
            val start = text.indexOf("{")
            if (start < 0) return null
            var depth = 0
            for (i in start until text.length) {
                when (text[i]) {
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) {
                            val candidate = text.substring(start, i + 1)
                            if (candidate.contains("\"tool\"")) return candidate
                            // Not a tool call — look for the next opening brace
                            val next = text.indexOf("{", i + 1)
                            return if (next >= 0) findFirstJsonObject(text.substring(next)) else null
                        }
                    }
                }
            }
            return null
        }
    }

    /**
     * Run a user [prompt] through Gemini Nano, executing any tool calls the model emits before
     * returning the final natural-language response text.
     *
     * @param prompt The raw user message.
     * @param onToolCall Invoked with the tool name just before the tool is executed.
     * @return The final response text to display to the user.
     */
    suspend fun runWithTools(
        prompt: String,
        onToolCall: suspend (toolName: String) -> Unit = {},
    ): String {
        val systemSnippet = registry.systemPromptSnippet()

        // Accumulate extra context lines appended after the first user turn
        val contextLines = mutableListOf<String>()

        repeat(MAX_TOOL_ROUNDS) { round ->
            val fullPrompt = buildString {
                appendLine(systemSnippet)
                appendLine()
                appendLine("User request: $prompt")
                contextLines.forEach { appendLine(it) }
                if (round > 0) appendLine("Now provide a friendly natural-language reply.")
            }

            val responseText = try {
                generativeModel.generateContent(fullPrompt)
                    .candidates
                    .joinToString("") { it.text }
                    .trim()
            } catch (e: Exception) {
                return "Sorry, an error occurred: ${e.message}"
            }

            // Try to find and parse a tool-call JSON block in the response
            val toolCall = extractToolCall(responseText) ?: return responseText

            // Notify caller so the UI can display a "Using tool: …" chip
            onToolCall(toolCall.first)

            val toolResult = registry.execute(toolCall.first, toolCall.second)

            // Append tool interaction to the running context for the next round
            contextLines += "Tool called: ${toolCall.first} with params ${toolCall.second}"
            contextLines += "[tool result: $toolResult]"
        }

        // Fallback after exhausting rounds — ask for a plain reply
        val finalPrompt = buildString {
            appendLine(systemSnippet)
            appendLine()
            appendLine("User request: $prompt")
            contextLines.forEach { appendLine(it) }
            appendLine("Now provide a friendly natural-language reply.")
        }
        return try {
            generativeModel.generateContent(finalPrompt)
                .candidates
                .joinToString("") { it.text }
                .trim()
        } catch (e: Exception) {
            "Sorry, an error occurred: ${e.message}"
        }
    }

    /**
     * Attempts to find and parse the first `{"tool":"…","params":{…}}` JSON block within
     * [text] using balanced-brace scanning so nested params objects are handled correctly.
     * Returns a pair of (toolName, params) or null if no valid call is found.
     */
    private fun extractToolCall(text: String): Pair<String, Map<String, String>>? {
        val raw = findFirstJsonObject(text) ?: return null
        return try {
            val jsonObj: JsonObject = json.parseToJsonElement(raw).jsonObject
            val toolName = jsonObj["tool"]?.jsonPrimitive?.content ?: return null
            val paramsObj = jsonObj["params"]?.jsonObject ?: JsonObject(emptyMap())
            val params = paramsObj.entries.associate { (k, v) ->
                k to v.jsonPrimitive.content
            }
            toolName to params
        } catch (_: Exception) {
            null
        }
    }
}
