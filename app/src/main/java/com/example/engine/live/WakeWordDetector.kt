package com.example.engine.live

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

/**
 * Lightweight, 100% silent offline voice energy wake-word detector.
 * Operates without system audio-focus beeps or clicking sounds to prevent mic lockup.
 * Upon detecting voice activation (e.g. saying "Hello"), seamlessly hands off the
 * microphone to the Gemini Multimodal Live Streaming engine.
 */
class WakeWordDetector(
    private val context: Context,
    private val onWakeWordDetected: () -> Unit
) {
    private val tag = "WakeWordDetector"
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var detectorJob: Job? = null
    private var audioRecord: AudioRecord? = null

    private val _isWakeWordActive = MutableStateFlow(false)
    val isWakeWordActive = _isWakeWordActive.asStateFlow()

    companion object {
        private const val SAMPLE_RATE = 16000
        private const val VOICE_ENERGY_THRESHOLD = 1300.0
    }

    @SuppressLint("MissingPermission")
    @Synchronized
    fun startListening() {
        if (_isWakeWordActive.value) return
        _isWakeWordActive.value = true

        detectorJob?.cancel()
        detectorJob = scope.launch {
            try {
                val minBuf = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                val bufferSize = (minBuf * 2).coerceAtLeast(3200)

                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )

                if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    Log.w(tag, "WakeWord detector AudioRecord not initialized")
                    _isWakeWordActive.value = false
                    return@launch
                }

                audioRecord?.startRecording()
                Log.d(tag, "Silent WakeWord detector active (waiting for voice / 'Hello')...")

                val buffer = ShortArray(800) // 50ms chunk
                var consecutiveVoiceFrames = 0

                while (isActive && _isWakeWordActive.value) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    if (read > 0) {
                        var sum = 0.0
                        for (i in 0 until read) {
                            sum += buffer[i] * buffer[i]
                        }
                        val rms = sqrt(sum / read)
                        if (rms > VOICE_ENERGY_THRESHOLD) {
                            consecutiveVoiceFrames++
                            if (consecutiveVoiceFrames >= 3) { // ~150ms of sustained voice activity
                                Log.d(tag, "Voice activity detected (RMS: $rms). Activating Live Stream...")
                                stopInternal()
                                withContext(Dispatchers.Main) {
                                    onWakeWordDetected()
                                }
                                break
                            }
                        } else {
                            consecutiveVoiceFrames = 0
                        }
                    }
                    delay(30)
                }
            } catch (e: Exception) {
                Log.w(tag, "WakeWord detector stopped: ${e.message}")
            } finally {
                stopInternal()
            }
        }
    }

    @Synchronized
    fun stopListening() {
        _isWakeWordActive.value = false
        detectorJob?.cancel()
        stopInternal()
    }

    private fun stopInternal() {
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            // Ignored
        }
        audioRecord = null
    }

    fun release() {
        stopListening()
    }
}
