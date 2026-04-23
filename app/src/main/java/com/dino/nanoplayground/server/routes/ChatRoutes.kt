package com.dino.nanoplayground.server.routes

import com.dino.nanoplayground.server.models.ChatCompletionChunk
import com.dino.nanoplayground.server.models.ChatCompletionRequest
import com.dino.nanoplayground.server.models.ChatCompletionResponse
import com.dino.nanoplayground.server.models.Choice
import com.dino.nanoplayground.server.models.ChatMessage
import com.dino.nanoplayground.server.models.DeltaMessage
import com.dino.nanoplayground.server.models.ErrorDetail
import com.dino.nanoplayground.server.models.ErrorResponse
import com.dino.nanoplayground.server.models.StreamChoice
import com.dino.nanoplayground.server.models.Usage
import com.google.mlkit.genai.prompt.GenerativeModel
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondTextWriter
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * Registers `POST /v1/chat/completions` with both plain-JSON and SSE streaming modes.
 *
 * When [bearerToken] is non-empty every request must carry a matching
 * `Authorization: Bearer <token>` header; requests without it receive HTTP 401.
 *
 * The prompt sent to Gemini Nano is assembled by concatenating all message turns
 * with role prefixes, mirroring typical instruction-following conventions.
 */
fun Route.chatRoutes(
    generativeModel: GenerativeModel,
    bearerToken: () -> String,
) {
    post("/v1/chat/completions") {
        // Bearer token check
        val token = bearerToken()
        if (token.isNotEmpty()) {
            val authHeader = call.request.headers["Authorization"] ?: ""
            if (authHeader != "Bearer $token") {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorResponse(ErrorDetail("Invalid or missing Bearer token", "auth_error", "401"))
                )
                return@post
            }
        }

        val req = try {
            call.receive<ChatCompletionRequest>()
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(ErrorDetail(e.message ?: "Invalid request body", "invalid_request_error"))
            )
            return@post
        }

        val prompt = buildPrompt(req)
        val completionId = "chatcmpl-${UUID.randomUUID()}"
        val created = System.currentTimeMillis() / 1000

        if (req.stream) {
            // SSE streaming response
            call.respondTextWriter(contentType = ContentType.Text.EventStream) {
                try {
                    val result = generativeModel.generateContent(prompt)
                    val text = result.candidates.joinToString("") { it.text }

                    // First delta: role
                    val roleChunk = ChatCompletionChunk(
                        id = completionId,
                        created = created,
                        model = req.model,
                        choices = listOf(
                            StreamChoice(index = 0, delta = DeltaMessage(role = "assistant"))
                        )
                    )
                    write("data: ${Json.encodeToString(ChatCompletionChunk.serializer(), roleChunk)}\n\n")
                    flush()

                    // Stream content in word-level chunks for a progressive feel
                    val words = text.split(" ")
                    words.forEachIndexed { idx, word ->
                        val content = if (idx == words.lastIndex) word else "$word "
                        val chunk = ChatCompletionChunk(
                            id = completionId,
                            created = created,
                            model = req.model,
                            choices = listOf(
                                StreamChoice(index = 0, delta = DeltaMessage(content = content))
                            )
                        )
                        write("data: ${Json.encodeToString(ChatCompletionChunk.serializer(), chunk)}\n\n")
                        flush()
                    }

                    // Terminal chunk
                    val doneChunk = ChatCompletionChunk(
                        id = completionId,
                        created = created,
                        model = req.model,
                        choices = listOf(
                            StreamChoice(index = 0, delta = DeltaMessage(), finishReason = "stop")
                        )
                    )
                    write("data: ${Json.encodeToString(ChatCompletionChunk.serializer(), doneChunk)}\n\n")
                    write("data: [DONE]\n\n")
                    flush()
                } catch (e: Exception) {
                    val safeMsg = (e.message ?: "Inference error")
                        .replace("\\", "\\\\")
                        .replace("\"", "\\\"")
                    write("data: {\"error\":\"$safeMsg\"}\n\n")
                    flush()
                }
            }
        } else {
            // Non-streaming path
            try {
                val result = generativeModel.generateContent(prompt)
                val text = result.candidates.joinToString("") { it.text }

                call.respond(
                    ChatCompletionResponse(
                        id = completionId,
                        created = created,
                        model = req.model,
                        choices = listOf(
                            Choice(
                                index = 0,
                                message = ChatMessage(role = "assistant", content = text),
                            )
                        ),
                        usage = Usage(
                            promptTokens = prompt.length / 4,
                            completionTokens = text.length / 4,
                            totalTokens = (prompt.length + text.length) / 4,
                        )
                    )
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse(ErrorDetail(e.message ?: "Inference error", "server_error"))
                )
            }
        }
    }
}

private fun buildPrompt(req: ChatCompletionRequest): String = buildString {
    req.messages.forEach { msg ->
        when (msg.role) {
            "system" -> appendLine("[System]: ${msg.content}")
            "user" -> appendLine("[User]: ${msg.content}")
            "assistant" -> appendLine("[Assistant]: ${msg.content}")
            else -> appendLine(msg.content)
        }
    }
}
