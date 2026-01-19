package com.example.aiclassroomcoach

import org.json.JSONObject

object GeminiLiveMessageParser {
    data class ParsedMessage(
        val audioBase64: String? = null,
        val text: String? = null,
        val isTurnEnd: Boolean = false
    )

    fun parse(payload: String): ParsedMessage {
        return try {
            val json = JSONObject(payload)
            val audio = json.optJSONObject("audio")?.optString("data")
            val text = json.optJSONArray("candidates")
                ?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?.optJSONObject(0)
                ?.optString("text")
            val turnEnd = json.optString("event") == "turn.end"
            ParsedMessage(audioBase64 = audio, text = text, isTurnEnd = turnEnd)
        } catch (ex: Exception) {
            ParsedMessage(text = null)
        }
    }
}
