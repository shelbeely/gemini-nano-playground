package com.dino.nanoplayground.server.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ── Request models ─────────────────────────────────────────────────────────────

@Serializable
data class ChatCompletionRequest(
    val model: String = "gemini-nano",
    val messages: List<ChatMessage>,
    /**
     * When `true` the server sends a text/event-stream (SSE) response where each
     * chunk is delivered as it is produced.  When `false` (default) the complete
     * response is returned in one JSON object.
     */
    val stream: Boolean = false,
    @SerialName("max_tokens") val maxTokens: Int? = null,
    val temperature: Float? = null,
)

@Serializable
data class ChatMessage(
    /** `system`, `user`, or `assistant` */
    val role: String,
    val content: String,
)

// ── Response models ────────────────────────────────────────────────────────────

@Serializable
data class ChatCompletionResponse(
    val id: String,
    @SerialName("object") val objectType: String = "chat.completion",
    val created: Long,
    val model: String,
    val choices: List<Choice>,
    val usage: Usage? = null,
)

@Serializable
data class Choice(
    val index: Int,
    val message: ChatMessage,
    @SerialName("finish_reason") val finishReason: String = "stop",
)

@Serializable
data class Usage(
    @SerialName("prompt_tokens") val promptTokens: Int,
    @SerialName("completion_tokens") val completionTokens: Int,
    @SerialName("total_tokens") val totalTokens: Int,
)

// ── Streaming chunk models ─────────────────────────────────────────────────────

@Serializable
data class ChatCompletionChunk(
    val id: String,
    @SerialName("object") val objectType: String = "chat.completion.chunk",
    val created: Long,
    val model: String,
    val choices: List<StreamChoice>,
)

@Serializable
data class StreamChoice(
    val index: Int,
    val delta: DeltaMessage,
    @SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable
data class DeltaMessage(
    val role: String? = null,
    val content: String? = null,
)

// ── Model list ─────────────────────────────────────────────────────────────────

@Serializable
data class ModelListResponse(
    @SerialName("object") val objectType: String = "list",
    val data: List<ModelObject>,
)

@Serializable
data class ModelObject(
    val id: String,
    @SerialName("object") val objectType: String = "model",
    val created: Long,
    @SerialName("owned_by") val ownedBy: String = "device",
)

// ── Error ──────────────────────────────────────────────────────────────────────

@Serializable
data class ErrorResponse(
    val error: ErrorDetail,
)

@Serializable
data class ErrorDetail(
    val message: String,
    val type: String = "server_error",
    val code: String? = null,
)
