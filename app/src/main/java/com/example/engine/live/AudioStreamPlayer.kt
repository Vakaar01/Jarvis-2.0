package com.example.engine.live

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.LinkedBlockingQueue
import kotlin.math.sqrt

/**
 * Low-latency real-time PCM audio streaming player using AudioTrack.
 * Supports instant Barge-In (mute, flush buffer, and clear queue immediately).
 */
class AudioStreamPlayer(
    private val defaultSampleRate: Int = 24000
) {
    private val tag = "AudioStreamPlayer"

    private var audioTrack: AudioTrack? = null
    private var currentSampleRate: Int = defaultSampleRate

    private val audioQueue = LinkedBlockingQueue<ByteArray>()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _playbackRms = MutableStateFlow(0f)
    val playbackRms = _playbackRms.asStateFlow()

    private val coroutineScope = CoroutineScope(Dispatchers.IO + Job())
    private var playbackJob: Job? = null

    init {
        initAudioTrack(defaultSampleRate)
    }

    @Synchronized
    private fun initAudioTrack(sampleRate: Int) {
        try {
            audioTrack?.release()
            currentSampleRate = sampleRate

            val channelConfig = AudioFormat.CHANNEL_OUT_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            val bufferSize = (minBufferSize * 2).coerceAtLeast(4096)

            audioTrack = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANT)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(audioFormat)
                            .setSampleRate(sampleRate)
                            .setChannelMask(channelConfig)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSize,
                    AudioTrack.MODE_STREAM
                )
            }

            audioTrack?.play()
            startPlaybackWorker()
        } catch (e: Exception) {
            Log.e(tag, "Failed to initialize AudioTrack with rate $sampleRate: ${e.message}", e)
        }
    }

    private fun startPlaybackWorker() {
        playbackJob?.cancel()
        playbackJob = coroutineScope.launch {
            while (isActive) {
                try {
                    val chunk = audioQueue.take()
                    if (chunk.isNotEmpty()) {
                        _isPlaying.value = true
                        calculatePlaybackRms(chunk)

                        val track = audioTrack
                        if (track != null && track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                            var offset = 0
                            while (offset < chunk.size && isActive) {
                                val written = track.write(chunk, offset, chunk.size - offset)
                                if (written > 0) {
                                    offset += written
                                } else {
                                    break
                                }
                            }
                        }

                        if (audioQueue.isEmpty()) {
                            _isPlaying.value = false
                            _playbackRms.value = 0f
                        }
                    }
                } catch (e: InterruptedException) {
                    break
                } catch (e: Exception) {
                    Log.e(tag, "Error during audio playback: ${e.message}")
                }
            }
        }
    }

    /**
     * Enqueues incoming PCM audio chunk from the Gemini Multimodal Live API.
     */
    fun enqueueAudio(pcmBytes: ByteArray, sampleRate: Int = defaultSampleRate) {
        if (pcmBytes.isEmpty()) return

        if (sampleRate != currentSampleRate && sampleRate > 0) {
            initAudioTrack(sampleRate)
        }

        audioQueue.offer(pcmBytes)
    }

    /**
     * Instant Barge-In (Interruption Handling):
     * Instantly mutes and clears the speaker audio playback buffer when user speech is detected.
     */
    @Synchronized
    fun interruptPlayback() {
        Log.d(tag, "Barge-in triggered: clearing audio buffer and flushing AudioTrack")
        audioQueue.clear()
        try {
            audioTrack?.let { track ->
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    track.pause()
                    track.flush()
                    track.play()
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error flushing AudioTrack during barge-in: ${e.message}")
        }
        _isPlaying.value = false
        _playbackRms.value = 0f
    }

    private fun calculatePlaybackRms(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        var sum = 0.0
        val sampleCount = bytes.size / 2
        for (i in 0 until sampleCount) {
            val idx = i * 2
            val sample = (bytes[idx].toInt() and 0xFF) or (bytes[idx + 1].toInt() shl 8)
            val sampleShort = sample.toShort()
            sum += sampleShort * sampleShort
        }
        val mean = sum / sampleCount.coerceAtLeast(1)
        val rms = sqrt(mean).toFloat()
        // Normalized 0..1 range approx
        _playbackRms.value = (rms / 32768f * 5f).coerceIn(0f, 1f)
    }

    fun release() {
        playbackJob?.cancel()
        audioQueue.clear()
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            Log.e(tag, "Error releasing AudioTrack: ${e.message}")
        }
        audioTrack = null
        _isPlaying.value = false
        _playbackRms.value = 0f
    }
}
