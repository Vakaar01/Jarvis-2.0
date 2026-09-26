package com.example.engine.live

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Lightweight offline wake-word detector configured for the wake word: "Hello".
 * Runs exclusively in IDLE state while WebSocket is disconnected to conserve battery and bandwidth.
 */
class WakeWordDetector(
    private val context: Context,
    private val onWakeWordDetected: () -> Unit
) {
    private val tag = "WakeWordDetector"
    private val mainHandler = Handler(Looper.getMainLooper())

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false

    private val _isWakeWordActive = MutableStateFlow(false)
    val isWakeWordActive = _isWakeWordActive.asStateFlow()

    private val wakeWords = listOf("hello", "hello jarvis", "hey jarvis", "hi jarvis", "namaste", "suno")

    init {
        initRecognizer()
    }

    private fun initRecognizer() {
        mainHandler.post {
            try {
                if (SpeechRecognizer.isRecognitionAvailable(context)) {
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                        setRecognitionListener(createListener())
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Failed to initialize offline wake word recognizer: ${e.message}")
            }
        }
    }

    private fun createListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                // If recognizer errors or times out in idle mode, silently restart after brief pause
                if (isListening) {
                    mainHandler.postDelayed({
                        restartListening()
                    }, 400)
                }
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                checkMatches(matches)
                if (isListening) {
                    mainHandler.postDelayed({
                        restartListening()
                    }, 200)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (checkMatches(matches)) {
                    // Match found in partial speech!
                    return
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    private fun checkMatches(matches: List<String>?): Boolean {
        if (matches.isNullOrEmpty()) return false

        for (candidate in matches) {
            val lower = candidate.lowercase().trim()
            Log.d(tag, "WakeWord audio heard: '$lower'")
            for (keyword in wakeWords) {
                if (lower.contains(keyword)) {
                    Log.d(tag, ">>> WAKE WORD DETECTED: '$keyword' in '$lower' <<<")
                    stopListening()
                    mainHandler.post {
                        onWakeWordDetected()
                    }
                    return true
                }
            }
        }
        return false
    }

    fun startListening() {
        if (isListening) return
        isListening = true
        _isWakeWordActive.value = true
        mainHandler.post {
            startInternal()
        }
    }

    private fun startInternal() {
        if (!isListening) return
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.ENGLISH.toString())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                // Request offline recognition to save battery and network
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
            speechRecognizer?.startListening(intent)
            Log.d(tag, "Wake-word detector listening for 'Hello' (offline/low-power)...")
        } catch (e: Exception) {
            Log.e(tag, "Error starting wake-word listening: ${e.message}")
        }
    }

    private fun restartListening() {
        if (!isListening) return
        try {
            speechRecognizer?.cancel()
            startInternal()
        } catch (e: Exception) {
            Log.w(tag, "Error restarting wake-word: ${e.message}")
        }
    }

    fun stopListening() {
        isListening = false
        _isWakeWordActive.value = false
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.cancel()
            } catch (e: Exception) {
                Log.w(tag, "Error stopping wake-word: ${e.message}")
            }
        }
    }

    fun release() {
        stopListening()
        mainHandler.post {
            try {
                speechRecognizer?.destroy()
                speechRecognizer = null
            } catch (e: Exception) {
                Log.w(tag, "Error releasing wake-word detector: ${e.message}")
            }
        }
    }
}
