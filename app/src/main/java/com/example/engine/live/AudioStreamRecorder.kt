package com.example.engine.live

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sqrt

/**
 * Continuous PCM audio recorder (16kHz, 16-bit mono) with Acoustic Echo Cancellation (AEC)
 * and real-time speech / barge-in energy detection.
 */
class AudioStreamRecorder(
    private val context: Context,
    private val onAudioChunk: (ByteArray) -> Unit,
    private val onUserSpeechActivity: () -> Unit,
    private val onBargeInDetected: () -> Unit
) {
    private val tag = "AudioStreamRecorder"

    companion object {
        const val SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        // 50ms buffer at 16kHz 16-bit = 800 samples = 1600 bytes
        const val CHUNK_SIZE_BYTES = 1600
        // RMS threshold to consider as user speech
        const val SPEECH_ENERGY_THRESHOLD = 900.0
    }

    private var audioRecord: AudioRecord? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var gainControl: AutomaticGainControl? = null

    private val _isRecording = MutableStateFlow(false)
    val isRecording = _isRecording.asStateFlow()

    private val _audioRms = MutableStateFlow(0f)
    val audioRms = _audioRms.asStateFlow()

    private val _isUserSpeaking = MutableStateFlow(false)
    val isUserSpeaking = _isUserSpeaking.asStateFlow()

    // Flag set when AI is currently outputting voice
    var isAiVoiceActive: Boolean = false

    private val coroutineScope = CoroutineScope(Dispatchers.IO + Job())
    private var recordJob: Job? = null

    @SuppressLint("MissingPermission")
    @Synchronized
    fun startRecording(): Boolean {
        if (_isRecording.value) return true

        try {
            val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            val bufferSize = (minBufferSize * 2).coerceAtLeast(CHUNK_SIZE_BYTES * 4)

            // AudioSource.VOICE_COMMUNICATION enables native hardware AEC on Android devices
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(tag, "AudioRecord failed to initialize")
                audioRecord?.release()
                audioRecord = null
                return false
            }

            val sessionId = audioRecord?.audioSessionId ?: 0
            enableAudioEffects(sessionId)

            audioRecord?.startRecording()
            _isRecording.value = true

            startRecordLoop()
            Log.d(tag, "Continuous 16kHz PCM recording started with AEC enabled (sessionId=$sessionId)")
            return true
        } catch (e: Exception) {
            Log.e(tag, "Failed to start AudioRecord: ${e.message}", e)
            release()
            return false
        }
    }

    private fun enableAudioEffects(sessionId: Int) {
        try {
            if (AcousticEchoCanceler.isAvailable()) {
                echoCanceler = AcousticEchoCanceler.create(sessionId)?.apply {
                    enabled = true
                    Log.d(tag, "Hardware AcousticEchoCanceler enabled successfully")
                }
            } else {
                Log.w(tag, "AcousticEchoCanceler is not supported on this device hardware")
            }

            if (NoiseSuppressor.isAvailable()) {
                noiseSuppressor = NoiseSuppressor.create(sessionId)?.apply {
                    enabled = true
                    Log.d(tag, "NoiseSuppressor enabled successfully")
                }
            }

            if (AutomaticGainControl.isAvailable()) {
                gainControl = AutomaticGainControl.create(sessionId)?.apply {
                    enabled = true
                    Log.d(tag, "AutomaticGainControl enabled successfully")
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Error configuring audio effects: ${e.message}")
        }
    }

    private fun startRecordLoop() {
        recordJob?.cancel()
        recordJob = coroutineScope.launch {
            val buffer = ByteArray(CHUNK_SIZE_BYTES)
            while (isActive && _isRecording.value) {
                val record = audioRecord ?: break
                val bytesRead = record.read(buffer, 0, buffer.size)

                if (bytesRead > 0) {
                    val pcmChunk = buffer.copyOf(bytesRead)
                    val rms = calculateRms(pcmChunk)
                    _audioRms.value = (rms / 32768.0 * 5.0).toFloat().coerceIn(0f, 1f)

                    val isSpeaking = rms > SPEECH_ENERGY_THRESHOLD
                    _isUserSpeaking.value = isSpeaking

                    if (isSpeaking) {
                        onUserSpeechActivity()
                        // If AI was speaking and user begins talking -> Instant Barge-In!
                        if (isAiVoiceActive) {
                            onBargeInDetected()
                        }
                    }

                    // Continuous audio streaming to the Gemini Live API
                    onAudioChunk(pcmChunk)
                }
            }
        }
    }

    private fun calculateRms(bytes: ByteArray): Double {
        var sum = 0.0
        val sampleCount = bytes.size / 2
        for (i in 0 until sampleCount) {
            val idx = i * 2
            val sample = (bytes[idx].toInt() and 0xFF) or (bytes[idx + 1].toInt() shl 8)
            val sampleShort = sample.toShort()
            sum += sampleShort * sampleShort
        }
        val mean = sum / sampleCount.coerceAtLeast(1)
        return sqrt(mean)
    }

    @Synchronized
    fun stopRecording() {
        _isRecording.value = false
        recordJob?.cancel()
        try {
            audioRecord?.stop()
        } catch (e: Exception) {
            Log.w(tag, "Error stopping AudioRecord: ${e.message}")
        }
        _audioRms.value = 0f
        _isUserSpeaking.value = false
    }

    @Synchronized
    fun release() {
        stopRecording()
        try {
            echoCanceler?.release()
            noiseSuppressor?.release()
            gainControl?.release()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.w(tag, "Error releasing audio hardware: ${e.message}")
        }
        echoCanceler = null
        noiseSuppressor = null
        gainControl = null
        audioRecord = null
    }
}
