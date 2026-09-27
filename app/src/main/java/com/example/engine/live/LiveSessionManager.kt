package com.example.engine.live

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Full Duplex Real-Time Voice Engine Manager.
 * Orchestrates:
 * 1. Offline wake-word detection ("Hello") during idle state.
 * 2. Bi-directional WebSocket streaming with Gemini Multimodal Live API.
 * 3. Continuous 16kHz PCM audio streaming with AEC and Instant Barge-In.
 * 4. 10-minute active session countdown timer & 2-minute silence inactivity fallback.
 * 5. Complete state machine management.
 */
class LiveSessionManager(
    private val context: Context,
    private val apiKeyProvider: () -> String,
    private val onMessageLog: (sender: String, text: String, tag: String?) -> Unit
) {
    private val tag = "LiveSessionManager"
    private val scope = CoroutineScope(Dispatchers.Default + Job())

    // 10-minute session duration (600 seconds)
    private val maxSessionDurationSeconds = 600
    // 2-minute silence fallback (120 seconds)
    private val maxSilenceDurationSeconds = 120

    private val _sessionState = MutableStateFlow(LiveSessionState.IDLE_LISTENING_WAKEWORD)
    val sessionState = _sessionState.asStateFlow()

    private val _sessionRemainingSeconds = MutableStateFlow(maxSessionDurationSeconds)
    val sessionRemainingSeconds = _sessionRemainingSeconds.asStateFlow()

    private val _sessionTimerFormatted = MutableStateFlow("10:00")
    val sessionTimerFormatted = _sessionTimerFormatted.asStateFlow()

    private val _inactivitySilenceSeconds = MutableStateFlow(0)
    val inactivitySilenceSeconds = _inactivitySilenceSeconds.asStateFlow()

    private val _audioRms = MutableStateFlow(0f)
    val audioRms = _audioRms.asStateFlow()

    private val _statusDescription = MutableStateFlow("STANDBY // SAY 'HELLO' TO ACTIVATE LIVE DUPLEX")
    val statusDescription = _statusDescription.asStateFlow()

    private val _lastTranscript = MutableStateFlow<String?>(null)
    val lastTranscript = _lastTranscript.asStateFlow()

    private var sessionTimerJob: Job? = null
    private var inactivityTimerJob: Job? = null
    private var rmsSyncJob: Job? = null

    private var lastUserActivityTimestamp = System.currentTimeMillis()

    // 1. Player (AudioTrack 24kHz / 16-bit PCM mono)
    val audioPlayer: AudioStreamPlayer = AudioStreamPlayer()

    // 2. Gemini Multimodal Live WebSocket Client
    val geminiClient: GeminiLiveClient = GeminiLiveClient(
        apiKeyProvider = apiKeyProvider,
        onConnected = {
            onWebSocketConnected()
        },
        onAudioReceived = { pcmBytes, sampleRate ->
            handleIncomingAudio(pcmBytes, sampleRate)
        },
        onTranscriptReceived = { text ->
            _lastTranscript.value = text
            onMessageLog("JARVIS", text, "🎙️ LIVE_STREAM")
        },
        onInterrupted = {
            handleBargeIn()
        },
        onSessionClosedByModel = {
            stopLiveSession("Model signed off ('Going to sleep now')")
        },
        onError = { error ->
            Log.e(tag, "Gemini Live error: $error")
            _statusDescription.value = "⚠️ ERROR: $error"
            _sessionState.value = LiveSessionState.ERROR
            onMessageLog("SYSTEM", "Live API error: $error", "⚠️ LIVE_ERROR")
            scope.launch {
                delay(3000)
                if (_sessionState.value == LiveSessionState.ERROR) {
                    returnToIdleWakeWord()
                }
            }
        },
        onDisconnected = {
            if (_sessionState.value != LiveSessionState.IDLE_LISTENING_WAKEWORD) {
                returnToIdleWakeWord()
            }
        }
    )

    // 3. Recorder (AudioRecord 16kHz / 16-bit PCM mono + AEC)
    val audioRecorder: AudioStreamRecorder = AudioStreamRecorder(
        context = context,
        onAudioChunk = { pcmChunk ->
            if (_sessionState.value == LiveSessionState.LIVE_ACTIVE) {
                geminiClient.sendAudioChunk(pcmChunk)
            }
        },
        onUserSpeechActivity = {
            lastUserActivityTimestamp = System.currentTimeMillis()
            _inactivitySilenceSeconds.value = 0
        },
        onBargeInDetected = {
            handleBargeIn()
        }
    )

    // 4. Lightweight offline Wake-Word Detector ("Hello")
    val wakeWordDetector: WakeWordDetector = WakeWordDetector(
        context = context,
        onWakeWordDetected = {
            Log.d(tag, "Wake-word 'Hello' triggered! Starting Live Mode immediately.")
            onMessageLog("USER", "Hello", "🗣️ WAKE_WORD")
            startLiveSession()
        }
    )

    init {
        // Observe and sync visualizer audio RMS (mic during speaking, player during AI response)
        rmsSyncJob = scope.launch {
            while (isActive) {
                if (audioPlayer.isPlaying.value) {
                    _audioRms.value = audioPlayer.playbackRms.value
                } else if (audioRecorder.isRecording.value) {
                    _audioRms.value = audioRecorder.audioRms.value
                } else {
                    _audioRms.value = 0f
                }
                delay(40)
            }
        }

        // Start in idle wake-word mode
        startWakeWordIdle()
    }

    /**
     * Idle Mode: keep heavy WebSocket disconnected to save battery and bandwidth.
     */
    fun startWakeWordIdle() {
        Log.d(tag, "Entering IDLE_LISTENING_WAKEWORD state")
        _sessionState.value = LiveSessionState.IDLE_LISTENING_WAKEWORD
        _statusDescription.value = "STANDBY // TAP MIC OR REACTOR TO TALK"
        audioRecorder.stopRecording()
        audioPlayer.interruptPlayback()
        geminiClient.disconnect()
        wakeWordDetector.stopListening()
    }

    /**
     * Transition immediately to Live Mode upon wake-word detection or manual UI tap.
     */
    @Synchronized
    fun startLiveSession() {
        if (_sessionState.value == LiveSessionState.LIVE_ACTIVE || _sessionState.value == LiveSessionState.CONNECTING) {
            Log.w(tag, "Live session already active or connecting")
            return
        }

        Log.d(tag, "Transitioning to CONNECTING state...")
        _sessionState.value = LiveSessionState.CONNECTING
        _statusDescription.value = "CONNECTING TO GEMINI LIVE WEBSOCKET..."

        // Stop wake-word detector so microphone is free for continuous 16kHz PCM streaming
        wakeWordDetector.stopListening()

        // Reset session timers
        _sessionRemainingSeconds.value = maxSessionDurationSeconds
        _sessionTimerFormatted.value = formatTimer(maxSessionDurationSeconds)
        _inactivitySilenceSeconds.value = 0
        lastUserActivityTimestamp = System.currentTimeMillis()

        // Connect bi-directional WebSocket after brief audio release pause
        scope.launch {
            delay(120)
            geminiClient.connect()
        }
    }

    private fun onWebSocketConnected() {
        Log.d(tag, "WebSocket connected. Transitioning to LIVE_ACTIVE state...")
        _sessionState.value = LiveSessionState.LIVE_ACTIVE
        _statusDescription.value = "FULL DUPLEX LIVE // 16kHz PCM STREAMING // AEC ACTIVE"

        // Start continuous microphone recording with AEC
        val micStarted = audioRecorder.startRecording()
        if (!micStarted) {
            _statusDescription.value = "⚠️ MIC ERROR: Check Record Audio permission"
            _sessionState.value = LiveSessionState.ERROR
            return
        }

        start10MinuteCountdownTimer()
        startInactivitySilenceFallbackTimer()

        onMessageLog("JARVIS", "Live Full Duplex stream connected. Systems active, Sir Vakaar.", "⚡ LIVE_OPEN")
    }

    /**
     * 10-Minute Session Countdown Timer:
     * Keeps the mic active and streaming continuously for 10 minutes (600s).
     * Gracefully closes when expired and returns to Wake-Word Mode.
     */
    private fun start10MinuteCountdownTimer() {
        sessionTimerJob?.cancel()
        sessionTimerJob = scope.launch {
            var seconds = maxSessionDurationSeconds
            while (isActive && seconds > 0 && _sessionState.value == LiveSessionState.LIVE_ACTIVE) {
                delay(1000)
                seconds--
                _sessionRemainingSeconds.value = seconds
                _sessionTimerFormatted.value = formatTimer(seconds)
            }

            if (seconds <= 0 && _sessionState.value == LiveSessionState.LIVE_ACTIVE) {
                Log.d(tag, "10-minute session duration expired. Auto-sleeping...")
                stopLiveSession("10-minute session timer completed")
            }
        }
    }

    /**
     * Inactivity fallback: automatically closes session if complete silence persists for 2 minutes (120s).
     */
    private fun startInactivitySilenceFallbackTimer() {
        inactivityTimerJob?.cancel()
        inactivityTimerJob = scope.launch {
            while (isActive && _sessionState.value == LiveSessionState.LIVE_ACTIVE) {
                delay(1000)
                val elapsedSilenceSeconds = ((System.currentTimeMillis() - lastUserActivityTimestamp) / 1000).toInt()
                _inactivitySilenceSeconds.value = elapsedSilenceSeconds

                if (elapsedSilenceSeconds >= maxSilenceDurationSeconds) {
                    Log.d(tag, "2 minutes of silence detected. Triggering auto-sleep inactivity fallback...")
                    stopLiveSession("Inactivity auto-sleep (2 minutes of silence)")
                    break
                }
            }
        }
    }

    private fun handleIncomingAudio(pcmBytes: ByteArray, sampleRate: Int) {
        audioRecorder.isAiVoiceActive = true
        audioPlayer.enqueueAudio(pcmBytes, sampleRate)
    }

    /**
     * Barge-In (Interruption Handling):
     * Instantly mutes and clears the speaker audio playback buffer when user speech is detected during AI responses.
     */
    fun handleBargeIn() {
        audioRecorder.isAiVoiceActive = false
        audioPlayer.interruptPlayback()
    }

    /**
     * Gracefully close WebSocket, stop mic, and return to Wake-Word Mode.
     */
    @Synchronized
    fun stopLiveSession(reason: String = "User requested stop") {
        if (_sessionState.value == LiveSessionState.IDLE_LISTENING_WAKEWORD) return

        Log.d(tag, "Stopping live session: $reason")
        _sessionState.value = LiveSessionState.DISCONNECTING
        _statusDescription.value = "CLOSING SESSION // RETURNING TO WAKE-WORD STANDBY..."

        sessionTimerJob?.cancel()
        inactivityTimerJob?.cancel()

        audioPlayer.interruptPlayback()
        audioRecorder.stopRecording()
        geminiClient.disconnect()

        onMessageLog("JARVIS", "Going to sleep now, Sir. Just say 'Hello' whenever you need me.", "🌙 AUTO_SLEEP")

        scope.launch {
            delay(600)
            returnToIdleWakeWord()
        }
    }

    private fun returnToIdleWakeWord() {
        _sessionState.value = LiveSessionState.IDLE_LISTENING_WAKEWORD
        _statusDescription.value = "STANDBY // SAY 'HELLO' TO ACTIVATE LIVE DUPLEX"
        _sessionRemainingSeconds.value = maxSessionDurationSeconds
        _sessionTimerFormatted.value = formatTimer(maxSessionDurationSeconds)
        _inactivitySilenceSeconds.value = 0
        audioRecorder.stopRecording()
        audioPlayer.interruptPlayback()
        wakeWordDetector.startListening()
    }

    private fun formatTimer(totalSeconds: Int): String {
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }

    fun release() {
        sessionTimerJob?.cancel()
        inactivityTimerJob?.cancel()
        rmsSyncJob?.cancel()
        audioPlayer.release()
        audioRecorder.release()
        geminiClient.disconnect()
        wakeWordDetector.release()
    }
}
