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

/**
 * High-performance, bi-directional Speech Engine for VAKAAR AI.
 * Handles continuous speech recognition (Hindi, English, Hinglish) and
 * high-fidelity Text-To-Speech with automatic turn-taking.
 */
class SpeechManager(
    private val context: Context,
    private val onSpeechRecognized: (String) -> Unit,
    private val onListeningStateChanged: (Boolean) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var isTtsInitialized = false

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking = _isSpeaking.asStateFlow()

    private val _audioRms = MutableStateFlow(0f)
    val audioRms = _audioRms.asStateFlow()

    var isContinuousListening: Boolean = false
    var speechPitch: Float = 0.95f
    var speechRate: Float = 1.05f
    var isVoiceEnabled: Boolean = true

    private var isRecognitionActive = false
    private var lastRecognizedText: String = ""
    private var lastJarvisSpokenText: String = ""
    private var lastTtsFinishedTimeMs: Long = 0L
    private var currentFinalUtteranceId: String? = null

    init {
        initTts()
        initRecognizer()
    }

    private fun initTts() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
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
                        lastRecognizedText = ""
                        try {
                            speechRecognizer?.cancel()
                        } catch (e: Exception) {
                            Log.w("SpeechManager", "Error cancelling recognizer on TTS start", e)
                        }
                    }

                    override fun onDone(utteranceId: String?) {
                        // Only finalize speaking state when the very last chunk in the sequence finishes!
                        if (utteranceId != currentFinalUtteranceId && currentFinalUtteranceId != null) {
                            return
                        }
                        currentFinalUtteranceId = null
                        _isSpeaking.value = false
                        lastTtsFinishedTimeMs = System.currentTimeMillis()
                        lastRecognizedText = ""
                        // 400ms cooldown allows speaker audio to clear without sluggish delays
                        if (isContinuousListening) {
                            mainHandler.postDelayed({
                                if (isContinuousListening && !_isSpeaking.value) {
                                    startListeningInternal()
                                }
                            }, 400)
                        }
                    }

                    override fun onError(utteranceId: String?) {
                        if (utteranceId == currentFinalUtteranceId || currentFinalUtteranceId == null) {
                            currentFinalUtteranceId = null
                            _isSpeaking.value = false
                            lastTtsFinishedTimeMs = System.currentTimeMillis()
                            lastRecognizedText = ""
                            if (isContinuousListening) {
                                mainHandler.postDelayed({
                                    if (isContinuousListening && !_isSpeaking.value) {
                                        startListeningInternal()
                                    }
                                }, 400)
                            }
                        }
                    }
                })
                isTtsInitialized = true
            } else {
                Log.e("SpeechManager", "TTS initialization failed with status $status")
            }
        }
    }

    private fun isSelfSpeechEcho(heard: String?, lastSpoken: String): Boolean {
        if (heard.isNullOrBlank() || lastSpoken.isBlank()) return false
        val h = heard.lowercase().trim()
        val s = lastSpoken.lowercase().trim()

        if (h == s) return true
        if (s.contains(h) && h.length >= 4) return true
        if (h.contains(s) && s.length >= 4) return true

        val heardWords = h.replace(Regex("[^a-zA-Z0-9\\u0900-\\u097F\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length > 2 }
        if (heardWords.isEmpty()) return false

        val sWords = s.replace(Regex("[^a-zA-Z0-9\\u0900-\\u097F\\s]"), " ")
            .split(Regex("\\s+"))
            .toSet()

        val matchCount = heardWords.count { sWords.contains(it) }
        val matchRatio = matchCount.toFloat() / heardWords.size.toFloat()

        val isRecent = (System.currentTimeMillis() - lastTtsFinishedTimeMs) < 6000L
        return matchRatio >= 0.4f && isRecent
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
                                if (!isContinuousListening) {
                                    onListeningStateChanged(false)
                                }
                                _audioRms.value = 0f
                            }

                            override fun onError(error: Int) {
                                isRecognitionActive = false
                                _audioRms.value = 0f
                                Log.w("SpeechManager", "Speech recognition error code: $error")

                                val now = System.currentTimeMillis()
                                if (_isSpeaking.value || (now - lastTtsFinishedTimeMs) < 700L) {
                                    lastRecognizedText = ""
                                }

                                // If user spoke and recognizer timed out, salvage the partial text IF not self-speech echo!
                                if (lastRecognizedText.isNotBlank()) {
                                    val salvaged = lastRecognizedText
                                    lastRecognizedText = ""
                                    if (!isSelfSpeechEcho(salvaged, lastJarvisSpokenText)) {
                                        onSpeechRecognized(salvaged)
                                        return
                                    }
                                }

                                if (isContinuousListening && !_isSpeaking.value) {
                                    val delayMs = when (error) {
                                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                                        SpeechRecognizer.ERROR_NO_MATCH -> 500L
                                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> 1000L
                                        else -> 800L
                                    }
                                    mainHandler.postDelayed({
                                        if (isContinuousListening && !_isSpeaking.value) {
                                            startListeningInternal()
                                        }
                                    }, delayMs)
                                } else {
                                    onListeningStateChanged(false)
                                }
                            }

                            override fun onResults(results: Bundle?) {
                                isRecognitionActive = false
                                _audioRms.value = 0f
                                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                val spokenText = matches?.firstOrNull()?.ifBlank { null } ?: lastRecognizedText.ifBlank { null }
                                lastRecognizedText = ""

                                val now = System.currentTimeMillis()
                                if (_isSpeaking.value || (now - lastTtsFinishedTimeMs) < 700L) {
                                    Log.d("SpeechManager", "Ignored input due to TTS speaking/cooldown: '$spokenText'")
                                    return
                                }

                                if (isSelfSpeechEcho(spokenText, lastJarvisSpokenText)) {
                                    Log.w("SpeechManager", "Acoustic self-speech echo dropped: '$spokenText'")
                                    if (isContinuousListening && !_isSpeaking.value) {
                                        mainHandler.postDelayed({
                                            if (isContinuousListening && !_isSpeaking.value) {
                                                startListeningInternal()
                                            }
                                        }, 400)
                                    }
                                    return
                                }

                                if (!spokenText.isNullOrBlank()) {
                                    Log.d("SpeechManager", "Recognized speech: '$spokenText'")
                                    onSpeechRecognized(spokenText)
                                } else {
                                    if (isContinuousListening && !_isSpeaking.value) {
                                        mainHandler.postDelayed({
                                            if (isContinuousListening && !_isSpeaking.value) {
                                                startListeningInternal()
                                            }
                                        }, 400)
                                    } else {
                                        onListeningStateChanged(false)
                                    }
                                }
                            }

                            override fun onPartialResults(partialResults: Bundle?) {
                                if (_isSpeaking.value || (System.currentTimeMillis() - lastTtsFinishedTimeMs) < 700L) {
                                    lastRecognizedText = ""
                                    return
                                }
                                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                if (!matches.isNullOrEmpty() && matches[0].isNotBlank()) {
                                    lastRecognizedText = matches[0]
                                }
                            }

                            override fun onEvent(eventType: Int, params: Bundle?) {}
                        })
                    }
                }
            } catch (e: Exception) {
                Log.e("SpeechManager", "Error initializing recognizer", e)
            }
        }
    }

    private fun cleanTextForSpeech(text: String): String {
        return text
            // Strip markdown formatting symbols
            .replace("**", "")
            .replace("*", "")
            .replace("`", "")
            .replace("#", "")
            .replace("~", "")
            .replace("_", " ")
            // Strip markdown lists / bullet points at start of lines
            .replace(Regex("""(?m)^[\s*\-•>]+\s*"""), "")
            // Remove emojis which crash/choke TTS engines
            .replace(Regex("""[\uD83C-\uDBFF\uDC00-\uDFFF\u2600-\u27BF]"""), "")
            // Normalize spaces
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun splitIntoSpeechChunks(text: String, maxChunkSize: Int = 220): List<String> {
        if (text.length <= maxChunkSize) return listOf(text)

        val chunks = mutableListOf<String>()
        // Split on sentence boundaries (. ? ! ; । \n)
        val rawSentences = text.split(Regex("""(?<=[.?!;।\n])\s+""")).filter { it.isNotBlank() }

        var currentChunk = StringBuilder()

        for (sentence in rawSentences) {
            val s = sentence.trim()
            if (s.isEmpty()) continue

            if (currentChunk.length + s.length + 1 <= maxChunkSize) {
                if (currentChunk.isNotEmpty()) currentChunk.append(" ")
                currentChunk.append(s)
            } else {
                if (currentChunk.isNotEmpty()) {
                    chunks.add(currentChunk.toString().trim())
                    currentChunk = StringBuilder()
                }
                if (s.length > maxChunkSize) {
                    // Split long sentence by comma or words
                    val clauses = s.split(Regex("""(?<=[,])\s+""")).filter { it.isNotBlank() }
                    for (clause in clauses) {
                        val c = clause.trim()
                        if (currentChunk.length + c.length + 1 <= maxChunkSize) {
                            if (currentChunk.isNotEmpty()) currentChunk.append(" ")
                            currentChunk.append(c)
                        } else {
                            if (currentChunk.isNotEmpty()) {
                                chunks.add(currentChunk.toString().trim())
                                currentChunk = StringBuilder()
                            }
                            if (c.length > maxChunkSize) {
                                val words = c.split(" ").filter { it.isNotBlank() }
                                for (word in words) {
                                    if (currentChunk.length + word.length + 1 <= maxChunkSize) {
                                        if (currentChunk.isNotEmpty()) currentChunk.append(" ")
                                        currentChunk.append(word)
                                    } else {
                                        if (currentChunk.isNotEmpty()) chunks.add(currentChunk.toString().trim())
                                        currentChunk = StringBuilder(word)
                                    }
                                }
                            } else {
                                currentChunk.append(c)
                            }
                        }
                    }
                } else {
                    currentChunk.append(s)
                }
            }
        }
        if (currentChunk.isNotEmpty()) {
            chunks.add(currentChunk.toString().trim())
        }
        return chunks.filter { it.isNotBlank() }
    }

    private fun startListeningInternal() {
        if (_isSpeaking.value) return
        val timeSinceTts = System.currentTimeMillis() - lastTtsFinishedTimeMs
        if (timeSinceTts < 350L) {
            mainHandler.postDelayed({
                if (isContinuousListening && !_isSpeaking.value) {
                    startListeningInternal()
                }
            }, 350L - timeSinceTts)
            return
        }
        mainHandler.post {
            try {
                if (speechRecognizer == null) {
                    initRecognizer()
                }
                lastRecognizedText = ""
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "hi-IN")
                    putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("hi-IN", "en-IN", "en-US"))
                    // Fast responsiveness: triggers within ~900ms of finishing speech:
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1200L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 900L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 700L)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                }
                speechRecognizer?.startListening(intent)
                if (isContinuousListening) {
                    onListeningStateChanged(true)
                }
            } catch (e: Exception) {
                Log.e("SpeechManager", "Failed to start listening", e)
                onListeningStateChanged(false)
                if (isContinuousListening) {
                    mainHandler.postDelayed({
                        if (isContinuousListening && !_isSpeaking.value) {
                            startListeningInternal()
                        }
                    }, 1200)
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
                speechRecognizer?.cancel()
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
        val cleaned = cleanTextForSpeech(text)
        if (cleaned.isBlank()) return

        _isSpeaking.value = true
        lastJarvisSpokenText = cleaned.lowercase().trim()

        mainHandler.post {
            try {
                // Cancel aborts any recording immediately and purges audio buffers:
                speechRecognizer?.cancel()
            } catch (e: Exception) {
                Log.w("SpeechManager", "Error cancelling recognizer before TTS", e)
            }
            lastRecognizedText = ""

            val containsDevanagari = cleaned.any { it in '\u0900'..'\u097F' }
            if (containsDevanagari) {
                tts?.language = Locale.Builder().setLanguage("hi").setRegion("IN").build()
            } else {
                tts?.language = Locale.Builder().setLanguage("en").setRegion("IN").build()
            }
            tts?.setPitch(speechPitch)
            tts?.setSpeechRate(speechRate)

            val chunks = splitIntoSpeechChunks(cleaned, maxChunkSize = 220)
            if (chunks.isEmpty()) {
                _isSpeaking.value = false
                return@post
            }

            val batchId = System.currentTimeMillis()
            val finalId = "VAKAAR_FINAL_${batchId}_${chunks.size - 1}"
            currentFinalUtteranceId = finalId

            // Speak all chunks seamlessly: chunk 0 clears any stale audio, subsequent chunks are queued with QUEUE_ADD
            for (i in chunks.indices) {
                val chunk = chunks[i]
                val utteranceId = if (i == chunks.size - 1) finalId else "VAKAAR_CHUNK_${batchId}_$i"
                val queueMode = if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
                tts?.speak(chunk, queueMode, null, utteranceId)
            }
        }
    }

    fun stopSpeaking() {
        currentFinalUtteranceId = null
        mainHandler.post {
            try {
                tts?.stop()
                _isSpeaking.value = false
            } catch (e: Exception) {
                Log.e("SpeechManager", "Failed to stop TTS", e)
            }
        }
    }

    fun release() {
        stopListening()
        mainHandler.post {
            try {
                tts?.stop()
                tts?.shutdown()
                speechRecognizer?.destroy()
            } catch (e: Exception) {
                Log.e("SpeechManager", "Error during release", e)
            }
        }
    }
}
