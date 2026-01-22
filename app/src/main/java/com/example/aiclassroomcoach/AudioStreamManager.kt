package com.example.aiclassroomcoach

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Base64
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import android.util.Log
import java.nio.ByteOrder

class AudioStreamManager(
    private val scope: CoroutineScope,
    private val onAudioChunk: (String) -> Unit,
    private val onPlaybackStarted: () -> Unit,
    private val onPlaybackStopped: () -> Unit
) {
    private val inputSampleRate = 16000
    private val outputSampleRate = 24000
    private var recordJob: Job? = null
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun startRecording() {
        Log.d("AudioManager", "Recording started")
        if (recordJob?.isActive == true) return
        val bufferSize = AudioRecord.getMinBufferSize(
            inputSampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(inputSampleRate)

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            inputSampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        ).apply { startRecording() }

        recordJob = scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(bufferSize)
            while (isActive) {
                val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (bytesRead > 0) {
                    val encoded = Base64.encodeToString(buffer.copyOf(bytesRead), Base64.NO_WRAP)
                    onAudioChunk(encoded)
                }
            }
        }
    }

    fun stopRecording() {
        recordJob?.cancel()
        recordJob = null
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }

    fun playPcm16Audio(base64Pcm: String) {
        val bytes = Base64.decode(base64Pcm, Base64.NO_WRAP)
        if (audioTrack == null) {
            val minBuffer = AudioTrack.getMinBufferSize(
                outputSampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(outputSampleRate)
            audioTrack = AudioTrack.Builder()
                .setAudioFormat(
                    android.media.AudioFormat.Builder()
                        .setSampleRate(outputSampleRate)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBuffer)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            audioTrack?.play()
            onPlaybackStarted()
        }
        audioTrack?.write(bytes, 0, bytes.size)
    }

    fun stopPlayback() {
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
        onPlaybackStopped()
    }

    fun shutdown() {
        stopRecording()
        stopPlayback()
    }
}
