package com.example.aiclassroomcoach

import android.util.Base64
import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream

class GeminiLiveClient(
    private val apiKey: String,
    private val onEvent: (GeminiLiveEvent) -> Unit
) {
    private val okHttpClient = OkHttpClient()
    private val tag = "GeminiLiveClient"
    private val audioBuffer = ByteArrayOutputStream()

    fun connect() {
        Log.d(tag, "Gemini Live client ready (HTTP generateContent)")
        onEvent(GeminiLiveEvent.Connected)
    }

    fun disconnect() {
        Log.d(tag, "Disconnecting Gemini Live client")
        audioBuffer.reset()
        onEvent(GeminiLiveEvent.Disconnected)
    }

    fun sendAudioChunk(base64Pcm16: String) {
        val decoded = Base64.decode(base64Pcm16, Base64.NO_WRAP)
        audioBuffer.write(decoded)
        Log.d(tag, "Buffered audio chunk (${decoded.size} bytes, total=${audioBuffer.size()})")
    }

    fun requestTurnEnd() {
        if (audioBuffer.size() == 0) {
            Log.w(tag, "Skipping generateContent: no audio buffered")
            return
        }
        Log.d(tag, "Sending generateContent request with ${audioBuffer.size()} bytes")
        val base64Audio = Base64.encodeToString(audioBuffer.toByteArray(), Base64.NO_WRAP)
        audioBuffer.reset()
        sendGenerateContent(base64Audio)
    }

    private fun sendGenerateContent(base64Audio: String) {
        val payload = JSONObject(
            mapOf(
                "system_instruction" to mapOf(
                    "parts" to listOf(mapOf("text" to PromptProvider.SYSTEM_PROMPT))
                ),
                "contents" to listOf(
                    mapOf(
                        "role" to "user",
                        "parts" to listOf(
                            mapOf(
                                "inline_data" to mapOf(
                                    "mime_type" to "audio/pcm;rate=16000",
                                    "data" to base64Audio
                                )
                            )
                        )
                    )
                )
            )
        )
        val requestBody = payload.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey")
            .post(requestBody)
            .build()
        okHttpClient.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                Log.e(tag, "generateContent failed: ${e.message}", e)
                onEvent(GeminiLiveEvent.Error(e.message ?: "Request failed"))
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use {
                    val body = it.body?.string().orEmpty()
                    Log.d(tag, "generateContent response ${it.code} (${body.length} chars)")
                    if (!it.isSuccessful) {
                        onEvent(GeminiLiveEvent.Error("HTTP ${it.code}: $body"))
                        return
                    }
                    onEvent(GeminiLiveEvent.RawResponse(body))
                }
            }
        })
    }
}

sealed class GeminiLiveEvent {
    data object Connected : GeminiLiveEvent()
    data object Disconnected : GeminiLiveEvent()
    data class RawResponse(val payload: String) : GeminiLiveEvent()
    data class Error(val message: String) : GeminiLiveEvent()
}
