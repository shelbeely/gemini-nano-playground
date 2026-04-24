package com.dino.nanoplayground.tools

/**
 * Contract for a device-level tool that Gemini Nano can invoke via prompt-engineered function
 * calling.  Each tool exposes:
 *  - a unique [name] used as the key in the JSON tool-call block emitted by the model
 *  - a human-readable [description] injected into the system prompt
 *  - a [paramsSchema] (compact JSON-schema string) so the model knows which keys to populate
 *  - an [execute] function that carries out the action and returns a result string that is
 *    fed back into the next prompt turn
 */
interface DeviceTool {
    val name: String
    val description: String
    val paramsSchema: String

    /**
     * Carry out the device action described by [params] (keys/values extracted from the
     * model's JSON tool-call block).
     *
     * @return a short English string describing the outcome (success or failure reason) that
     *         will be injected as a "tool result" context turn for the model.
     */
    suspend fun execute(params: Map<String, String>): String
}
