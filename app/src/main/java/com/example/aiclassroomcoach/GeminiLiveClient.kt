package com.example.aiclassroomcoach

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

class GeminiLiveClient(
    private val apiKey: String,
    private val onEvent: (GeminiLiveEvent) -> Unit
) {
    private val okHttpClient = OkHttpClient()
    private var webSocket: WebSocket? = null

    fun connect() {
        if (webSocket != null) return
        val request = Request.Builder()
            .url("wss://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:streamGenerateContent?key=$apiKey")
            .build()
        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                onEvent(GeminiLiveEvent.Connected)
                sendSystemPrompt()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                onEvent(GeminiLiveEvent.RawResponse(text))
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                onEvent(GeminiLiveEvent.Error(t.message ?: "Connection error"))
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                onEvent(GeminiLiveEvent.Disconnected)
            }
        })
    }

    fun disconnect() {
        webSocket?.close(1000, "Client closing")
        webSocket = null
    }

    fun sendAudioChunk(base64Pcm16: String) {
        val payload = JSONObject(
            mapOf(
                "audio" to mapOf(
                    "format" to "pcm16",
                    "sample_rate_hz" to 16000,
                    "data" to base64Pcm16
                )
            )
        )
        webSocket?.send(payload.toString())
    }

    fun requestTurnEnd() {
        val payload = JSONObject(mapOf("event" to "turn.end"))
        webSocket?.send(payload.toString())
    }

    private fun sendSystemPrompt() {
        val payload = JSONObject(
            mapOf(
                "system_instruction" to mapOf(
                    "parts" to listOf(mapOf("text" to PromptProvider.SYSTEM_PROMPT))
                )
            )
        )
        webSocket?.send(payload.toString())
    }
}

sealed class GeminiLiveEvent {
    data object Connected : GeminiLiveEvent()
    data object Disconnected : GeminiLiveEvent()
    data class RawResponse(val payload: String) : GeminiLiveEvent()
    data class Error(val message: String) : GeminiLiveEvent()
}
