package com.example.engine

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class SpeechManager(
    private val context: Context,
    private val onSpeechRecognized: (String) -> Unit,
    private val onListeningStateChanged: (Boolean) -> Unit
) {
    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var isTtsInitialized = false
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking = _isSpeaking.asStateFlow()

    private val _audioRms = MutableStateFlow(0f)
    val audioRms = _audioRms.asStateFlow()

    // Continuous non-stop listening flag
    var isContinuousListening: Boolean = true
    var speechPitch: Float = 0.95f
    var speechRate: Float = 1.05f
    var isVoiceEnabled: Boolean = true

    // To prevent rapid restarting loop
    private var isRecognitionActive = false

    init {
        initTts()
        initRecognizer()
    }

    private fun initTts() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                // Support Hindi & English
                val hindiLocale = Locale.Builder().setLanguage("hi").setRegion("IN").build()
                val englishLocale = Locale.UK
                val testRes = tts?.isLanguageAvailable(hindiLocale)
                if (testRes != TextToSpeech.LANG_MISSING_DATA && testRes != TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.language = hindiLocale
                } else {
                    tts?.language = englishLocale
                }

                tts?.setPitch(speechPitch)
                tts?.setSpeechRate(speechRate)

                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _isSpeaking.value = true
                    }

                    override fun onDone(utteranceId: String?) {
                        _isSpeaking.value = false
                        // Once done speaking, immediately resume listening if continuous listening is on!
                        if (isContinuousListening) {
                            mainHandler.postDelayed({
                                startListeningInternal()
                            }, 400)
                        }
                    }

                    override fun onError(utteranceId: String?) {
                        _isSpeaking.value = false
                        if (isContinuousListening) {
                            mainHandler.postDelayed({
                                startListeningInternal()
                            }, 500)
                        }
                    }
                })
                isTtsInitialized = true
            } else {
                Log.e("SpeechManager", "TTS initialization failed with status $status")
            }
        }
    }

    private fun initRecognizer() {
        mainHandler.post {
            try {
                speechRecognizer?.destroy()
                if (SpeechRecognizer.isRecognitionAvailable(context)) {
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                        setRecognitionListener(object : RecognitionListener {
                            override fun onReadyForSpeech(params: Bundle?) {
                                isRecognitionActive = true
                                onListeningStateChanged(true)
                            }

                            override fun onBeginningOfSpeech() {}

                            override fun onRmsChanged(rmsdB: Float) {
                                val normalized = ((rmsdB + 2f) / 12f).coerceIn(0.1f, 1.0f)
                                _audioRms.value = normalized
                            }

                            override fun onBufferReceived(buffer: ByteArray?) {}

                            override fun onEndOfSpeech() {
                                onListeningStateChanged(false)
                                _audioRms.value = 0f
                            }

                            override fun onError(error: Int) {
                                isRecognitionActive = false
                                onListeningStateChanged(false)
                                _audioRms.value = 0f
                                Log.w("SpeechManager", "Speech recognition error: $error")

                                // In continuous mode, automatically auto-restart listening on timeouts/silence
                                if (isContinuousListening && !_isSpeaking.value) {
                                    val delayMs = when (error) {
                                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> 1000L
                                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> 350L
                                        SpeechRecognizer.ERROR_NO_MATCH -> 300L
                                        else -> 600L
                                    }
                                    mainHandler.postDelayed({
                                        startListeningInternal()
                                    }, delayMs)
                                }
                            }

                            override fun onResults(results: Bundle?) {
                                isRecognitionActive = false
                                onListeningStateChanged(false)
                                _audioRms.value = 0f
                                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                if (!matches.isNullOrEmpty()) {
                                    val spokenText = matches[0]
                                    onSpeechRecognized(spokenText)
                                }

                                // Auto-restart listening if not currently speaking
                                if (isContinuousListening && !_isSpeaking.value) {
                                    mainHandler.postDelayed({
                                        startListeningInternal()
                                    }, 400)
                                }
                            }

                            override fun onPartialResults(partialResults: Bundle?) {}

                            override fun onEvent(eventType: Int, params: Bundle?) {}
                        })
                    }
                }
            } catch (e: Exception) {
                Log.e("SpeechManager", "Error initializing recognizer", e)
            }
        }
    }

    private fun startListeningInternal() {
        if (_isSpeaking.value) return
        mainHandler.post {
            try {
                if (speechRecognizer == null) {
                    initRecognizer()
                }
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    // Accept both Hindi and English
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "hi-IN")
                    putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("hi-IN", "en-IN", "en-US"))
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                }
                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                Log.e("SpeechManager", "Failed to start listening", e)
                onListeningStateChanged(false)
                // Retry after brief interval
                if (isContinuousListening) {
                    mainHandler.postDelayed({ startListeningInternal() }, 1000)
                }
            }
        }
    }

    fun startListening() {
        stopSpeaking()
        isContinuousListening = true
        startListeningInternal()
    }

    fun stopListening() {
        isContinuousListening = false
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
            } catch (e: Exception) {
                Log.e("SpeechManager", "Failed to stop speech recognition", e)
            }
            isRecognitionActive = false
            onListeningStateChanged(false)
            _audioRms.value = 0f
        }
    }

    fun speak(text: String) {
        if (!isVoiceEnabled || !isTtsInitialized) return
        mainHandler.post {
            // Check if text is mostly Hindi/Devanagari or Roman Hindi
            val containsDevanagari = text.any { it in '\u0900'..'\u097F' }
            if (containsDevanagari) {
                tts?.language = Locale.Builder().setLanguage("hi").setRegion("IN").build()
            } else {
                tts?.language = Locale.Builder().setLanguage("en").setRegion("IN").build()
            }
            tts?.setPitch(speechPitch)
            tts?.setSpeechRate(speechRate)
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "VAKAAR_UTTERANCE_${System.currentTimeMillis()}")
        }
    }

    fun stopSpeaking() {
        tts?.stop()
        _isSpeaking.value = false
    }

    fun release() {
        isContinuousListening = false
        mainHandler.removeCallbacksAndMessages(null)
        tts?.stop()
        tts?.shutdown()
        speechRecognizer?.destroy()
    }
}
