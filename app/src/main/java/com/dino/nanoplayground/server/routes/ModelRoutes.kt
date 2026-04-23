package com.dino.nanoplayground.server.routes

import com.dino.nanoplayground.server.models.ModelListResponse
import com.dino.nanoplayground.server.models.ModelObject
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/**
 * Registers `GET /v1/models` which returns the single on-device Gemini Nano model in
 * an OpenAI-compatible envelope.  Clients (e.g. Open WebUI, shell scripts) use this
 * endpoint to discover which model IDs are valid before sending a chat completion.
 */
fun Route.modelRoutes() {
    get("/v1/models") {
        val now = System.currentTimeMillis() / 1000
        call.respond(
            ModelListResponse(
                data = listOf(
                    ModelObject(
                        id = "gemini-nano",
                        created = now,
                        ownedBy = "device",
                    )
                )
            )
        )
    }
}
