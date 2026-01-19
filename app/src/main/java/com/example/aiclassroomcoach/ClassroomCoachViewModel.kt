package com.example.aiclassroomcoach

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ClassroomCoachViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(CoachUiState())
    val uiState: StateFlow<CoachUiState> = _uiState.asStateFlow()

    private val geminiLiveClient = GeminiLiveClient(
        apiKey = BuildConfig.GEMINI_API_KEY,
        onEvent = ::handleGeminiEvent
    )

    private val audioStreamManager = AudioStreamManager(
        scope = viewModelScope,
        onAudioChunk = { chunk -> geminiLiveClient.sendAudioChunk(chunk) },
        onPlaybackStarted = { setStatus("Speaking") },
        onPlaybackStopped = { setStatus("Listening") }
    )

    fun connect() {
        if (BuildConfig.GEMINI_API_KEY.isBlank()) {
            setStatus("Missing API key")
            return
        }
        geminiLiveClient.connect()
    }

    fun disconnect() {
        audioStreamManager.shutdown()
        geminiLiveClient.disconnect()
        setStatus("Disconnected")
    }

    fun startPushToTalk() {
        audioStreamManager.stopPlayback()
        audioStreamManager.startRecording()
        setStatus("Listening")
        _uiState.value = _uiState.value.copy(isStreaming = true)
    }

    fun stopPushToTalk() {
        audioStreamManager.stopRecording()
        geminiLiveClient.requestTurnEnd()
        _uiState.value = _uiState.value.copy(isStreaming = false)
        setStatus("Awaiting response")
    }

    fun toggleAlwaysOn(enabled: Boolean) {
        if (enabled) {
            startPushToTalk()
        } else {
            stopPushToTalk()
        }
    }

    private fun handleGeminiEvent(event: GeminiLiveEvent) {
        when (event) {
            is GeminiLiveEvent.Connected -> setStatus("Connected")
            is GeminiLiveEvent.Disconnected -> setStatus("Disconnected")
            is GeminiLiveEvent.Error -> setStatus("Error: ${event.message}")
            is GeminiLiveEvent.RawResponse -> handleResponse(event.payload)
        }
    }

    private fun handleResponse(payload: String) {
        val parsed = GeminiLiveMessageParser.parse(payload)
        parsed.audioBase64?.let { audioStreamManager.playPcm16Audio(it) }
        parsed.text?.let {
            val enforced = ResponseStructureEnforcer.enforce(it)
            _uiState.value = _uiState.value.copy(lastResponseText = enforced)
        }
    }

    private fun setStatus(message: String) {
        _uiState.value = _uiState.value.copy(status = message)
    }

    override fun onCleared() {
        super.onCleared()
        audioStreamManager.shutdown()
        geminiLiveClient.disconnect()
    }
}

data class CoachUiState(
    val status: String = "Idle",
    val isStreaming: Boolean = false,
    val lastResponseText: String = ""
)
