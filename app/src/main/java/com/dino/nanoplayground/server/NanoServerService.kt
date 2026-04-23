package com.dino.nanoplayground.server

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.dino.nanoplayground.ble.NanoBleManager
import com.dino.nanoplayground.server.routes.chatRoutes
import com.dino.nanoplayground.server.routes.modelRoutes
import com.google.mlkit.genai.prompt.GenerativeModel
import dagger.hilt.android.AndroidEntryPoint
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import javax.inject.Inject

/**
 * Foreground service that hosts the NanoAI OpenAI-compatible HTTP server on the device's
 * local Wi-Fi interface.
 *
 * On start:
 * 1. Spins up a Ktor CIO server on the requested port (default 11434).
 * 2. Registers `/v1/models` and `/v1/chat/completions` routes.
 * 3. Publishes the device's Wi-Fi IP to [NanoBleManager] so the BLE Config
 *    characteristic can return the full API base URL for discovery handoff.
 * 4. Shows a persistent foreground notification.
 *
 * On destroy: stops the Ktor engine and notifies [NanoServerManager].
 */
@AndroidEntryPoint
class NanoServerService : Service() {

    @Inject lateinit var generativeModel: GenerativeModel
    @Inject lateinit var serverManager: NanoServerManager
    @Inject lateinit var bleManager: NanoBleManager

    private var engine: io.ktor.server.engine.EmbeddedServer<*, *>? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val port = intent?.getIntExtra(EXTRA_PORT, 11434) ?: 11434

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(port))

        engine = embeddedServer(CIO, port = port, host = "0.0.0.0") {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    encodeDefaults = true
                })
            }

            routing {
                modelRoutes()
                chatRoutes(generativeModel) { serverManager.bearerToken.value }
            }
        }.also { it.start(wait = false) }

        // Push Wi-Fi IP to BleManager for discovery handoff
        bleManager.wifiIp = serverManager.wifiIp.value
        bleManager.wifiPort = port

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        engine?.stop(gracePeriodMillis = 500, timeoutMillis = 2000)
        engine = null
        bleManager.wifiIp = ""
        serverManager.onServiceStopped()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "HTTP Server", NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private fun buildNotification(port: Int) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("NanoAI HTTP Server")
        .setContentText("Listening on port $port — /v1/chat/completions")
        .setSmallIcon(android.R.drawable.ic_menu_upload)
        .setOngoing(true)
        .build()

    companion object {
        const val EXTRA_PORT = "extra_port"
        private const val CHANNEL_ID = "nano_http_server"
        private const val NOTIFICATION_ID = 3
    }
}
