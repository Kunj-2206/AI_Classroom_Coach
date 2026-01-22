package com.example.aiclassroomcoach

import android.util.Base64
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject

class GeminiLiveClient(
    private val apiKey: String,
    private val onEvent: (GeminiLiveEvent) -> Unit
) {
    private val okHttpClient = OkHttpClient()
    private var webSocket: WebSocket? = null
    private val tag = "GeminiLiveClient"

    // Correct Live API WS endpoint (BidiGenerateContent)
    private val wsUrl =
        "wss://generativelanguage.googleapis.com/ws/" +
                "google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent" +
                "?alt=json"

    // Pick an audio-capable Live model.
    private val model = "models/gemini-2.0-flash-exp"

    // Input audio format: PCM16LE mono 16kHz
    private val inputSampleRateHz = 16_000
    private val inputMimeType = "audio/pcm;rate=$inputSampleRateHz"

    private var setupComplete: Boolean = false

    // Buffer audio chunks until setup completes (prevents "Setup not complete yet")
    private val pendingAudio = ArrayDeque<String>()
    private val maxPendingChunks = 60

    fun connect() {
        if (webSocket != null) return

        Log.d(tag, "Connecting to Gemini Live WebSocket")
        val request = Request.Builder()
            .url(wsUrl)
            // Prefer header-based auth for Live WebSockets
            .addHeader("x-goog-api-key", apiKey)
            .build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {

            override fun onOpen(ws: WebSocket, response: Response) {
                Log.d(tag, "WebSocket opened: ${response.code}")
                onEvent(GeminiLiveEvent.Connected)
                // Live: first message must be setup
                sendSetup()
            }

            override fun onMessage(ws: WebSocket, text: String) {
                Log.d(tag, "WS message received (${text.length} chars)")
                onEvent(GeminiLiveEvent.RawResponse(text))

                try {
                    val msg = JSONObject(text)

                    // If setup/auth/payload invalid, server often returns { "error": {...} }
                    if (msg.has("error")) {
                        val err = msg.getJSONObject("error")
                        val code = err.optInt("code", -1)
                        val message = err.optString("message", "Unknown error")
                        Log.e(tag, "Server error ($code): $message")
                        onEvent(GeminiLiveEvent.Error("Server error ($code): $message"))
                        return
                    }

                    // Setup complete gate
                    if (msg.has("setupComplete")) {
                        setupComplete = true
                        onEvent(GeminiLiveEvent.SetupComplete)
                        flushPendingAudio()
                        return
                    }

                    // Main streaming content
                    if (msg.has("serverContent")) {
                        val serverContent = msg.getJSONObject("serverContent")

                        if (serverContent.optBoolean("interrupted", false)) {
                            onEvent(GeminiLiveEvent.Interrupted)
                        }

                        val modelTurn = serverContent.optJSONObject("modelTurn")
                        if (modelTurn != null) {
                            val parts = modelTurn.optJSONArray("parts") ?: JSONArray()
                            for (i in 0 until parts.length()) {
                                val part = parts.getJSONObject(i)

                                // Text
                                if (part.has("text")) {
                                    onEvent(GeminiLiveEvent.Transcript(part.getString("text")))
                                }

                                // Audio (base64) in inlineData
                                val inlineData = part.optJSONObject("inlineData")
                                if (inlineData != null && inlineData.has("data")) {
                                    val b64 = inlineData.getString("data")
                                    val bytes = Base64.decode(b64, Base64.DEFAULT)
                                    val mimeType = inlineData.optString("mimeType", "audio/pcm;rate=24000")
                                    onEvent(GeminiLiveEvent.Audio(pcm16Bytes = bytes, mimeType = mimeType))
                                }
                            }
                        }

                        if (serverContent.optBoolean("turnComplete", false)) {
                            onEvent(GeminiLiveEvent.TurnComplete)
                        }
                    }
                } catch (t: Throwable) {
                    Log.e(tag, "Parse error: ${t.message}", t)
                    // Keep running; RawResponse already emitted for debugging
                }
            }

            override fun onMessage(ws: WebSocket, bytes: ByteString) {
                Log.d(tag, "Binary frame received (${bytes.size} bytes)")
                onEvent(GeminiLiveEvent.BinaryResponse(bytes.toByteArray()))
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.e(tag, "WebSocket failure: ${t.message}", t)
                response?.let {
                    Log.e(tag, "Failure response: code=${it.code}, msg=${it.message}")
                }
                onEvent(GeminiLiveEvent.Error(t.message ?: "Connection error"))
                cleanup()
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                Log.d(tag, "WebSocket closed: $code / $reason")
                onEvent(GeminiLiveEvent.Disconnected)
                cleanup()
            }
        })
    }

    fun disconnect() {
        Log.d(tag, "Disconnecting WebSocket")
        webSocket?.close(1000, "Client closing")
        cleanup()
    }

    /**
     * Send one chunk of base64-encoded PCM16LE mono 16kHz microphone audio.
     * If setup isn't complete yet, we queue and send once setupComplete arrives.
     */
    fun sendAudioChunk(base64Pcm16: String) {
        if (!setupComplete) {
            // queue
            if (pendingAudio.size >= maxPendingChunks) pendingAudio.removeFirst()
            pendingAudio.addLast(base64Pcm16)
            Log.w(tag, "Queued audio chunk; setup not complete yet (queued=${pendingAudio.size})")
            return
        }
        sendRealtimeAudio(base64Pcm16)
    }

    /**
     * "Turn end" in Live is not the old event-based API.
     * Practical boundary signal: send an empty user turn in clientContent.
     * Call this when you stop mic streaming and want the model to respond.
     */
    fun requestTurnEnd() {
        if (!setupComplete) {
            Log.w(tag, "requestTurnEnd before setupComplete; ignoring")
            return
        }

        val payload = JSONObject().put(
            "clientContent",
            JSONObject().put(
                "turns",
                JSONArray().put(
                    JSONObject()
                        .put("role", "user")
                        .put("parts", JSONArray()) // empty boundary
                )
            )
        )

        val sent = webSocket?.send(payload.toString()) ?: false
        Log.d(tag, "Turn end send result: $sent")
        if (!sent) onEvent(GeminiLiveEvent.Error("Failed to request turn end"))
    }

    private fun sendSetup() {
        Log.d(tag, "Sending setup")

        val setup = JSONObject()
            .put("model", model)
            .put(
                "generationConfig",
                JSONObject()
                    // Ask for audio output; server may also return text parts depending on config/model
                    .put("responseModalities", JSONArray().put("AUDIO"))
            )
            .put(
                "systemInstruction",
                JSONObject()
                    .put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", PromptProvider.SYSTEM_PROMPT))
                    )
            )

        val payload = JSONObject().put("setup", setup)

        val sent = webSocket?.send(payload.toString()) ?: false
        Log.d(tag, "Setup send result: $sent")
        if (!sent) onEvent(GeminiLiveEvent.Error("Failed to send setup"))
    }

    private fun sendRealtimeAudio(base64Pcm16: String) {
        val payload = JSONObject().put(
            "realtimeInput",
            JSONObject().put(
                "audio",
                JSONObject()
                    .put("data", base64Pcm16)
                    .put("mimeType", inputMimeType)
            )
        )

        val sent = webSocket?.send(payload.toString()) ?: false
        Log.d(tag, "Audio chunk send result: $sent")
        if (!sent) onEvent(GeminiLiveEvent.Error("Failed to send audio chunk"))
    }

    private fun flushPendingAudio() {
        if (pendingAudio.isEmpty()) return
        Log.d(tag, "Flushing queued audio chunks: ${pendingAudio.size}")
        while (pendingAudio.isNotEmpty()) {
            sendRealtimeAudio(pendingAudio.removeFirst())
        }
    }

    private fun cleanup() {
        webSocket = null
        setupComplete = false
        pendingAudio.clear()
    }
}

sealed class GeminiLiveEvent {
    data object Connected : GeminiLiveEvent()
    data object Disconnected : GeminiLiveEvent()

    data object SetupComplete : GeminiLiveEvent()
    data object TurnComplete : GeminiLiveEvent()
    data object Interrupted : GeminiLiveEvent()

    data class Transcript(val text: String) : GeminiLiveEvent()

    /**
     * pcm16Bytes are raw PCM16 little-endian bytes.
     * mimeType is typically "audio/pcm;rate=24000" for model output audio.
     */
    data class Audio(val pcm16Bytes: ByteArray, val mimeType: String) : GeminiLiveEvent()

    data class RawResponse(val payload: String) : GeminiLiveEvent()
    data class BinaryResponse(val payload: ByteArray) : GeminiLiveEvent()

    data class Error(val message: String) : GeminiLiveEvent()
}
