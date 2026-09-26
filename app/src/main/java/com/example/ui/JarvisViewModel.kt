package com.example.ui

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.api.GeminiApi
import com.example.data.model.ChatMessage
import com.example.data.model.DeviceTelemetry
import com.example.data.model.JarvisPermissionStatus
import com.example.data.model.MessageSender
import com.example.engine.JarvisBrain
import com.example.engine.SpeechManager
import com.example.engine.SystemController
import com.example.engine.live.LiveSessionManager
import com.example.engine.live.LiveSessionState
import com.example.service.JarvisService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class JarvisViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("jarvis_prefs", Context.MODE_PRIVATE)

    val systemController = SystemController(application)

    private val geminiApi = GeminiApi(
        customApiKeyProvider = { prefs.getString("custom_api_key", null) }
    )

    val jarvisBrain = JarvisBrain(systemController, geminiApi)

    private val _isListening = MutableStateFlow(false)
    val isListening = _isListening.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages = _messages.asStateFlow()

    private val _telemetry = MutableStateFlow(DeviceTelemetry())
    val telemetry = _telemetry.asStateFlow()

    private val _permissionStatus = MutableStateFlow(JarvisPermissionStatus())
    val permissionStatus = _permissionStatus.asStateFlow()

    private val _isSettingsOpen = MutableStateFlow(false)
    val isSettingsOpen = _isSettingsOpen.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing = _isProcessing.asStateFlow()

    private val _currentExecutionStep = MutableStateFlow<String?>(null)
    val currentExecutionStep = _currentExecutionStep.asStateFlow()

    val isServiceRunning = JarvisService.isServiceRunning

    val customApiKey = MutableStateFlow(prefs.getString("custom_api_key", "") ?: "")
    val hasConfiguredApiKey = MutableStateFlow(geminiApi.hasConfiguredKey())
    val alwaysOnEnabled = MutableStateFlow(prefs.getBoolean("always_on_service", true))
    val voiceOutputEnabled = MutableStateFlow(prefs.getBoolean("voice_output", true))
    val speechPitch = MutableStateFlow(prefs.getFloat("speech_pitch", 0.95f))
    val speechRate = MutableStateFlow(prefs.getFloat("speech_rate", 1.05f))

    // Gemini Multimodal Live API Full Duplex Session Engine
    val liveSessionManager = LiveSessionManager(
        context = application,
        apiKeyProvider = { geminiApi.getEffectiveApiKey() },
        onMessageLog = { sender, text, tag ->
            addMessage(
                ChatMessage(
                    sender = when (sender) {
                        "USER" -> MessageSender.USER
                        "JARVIS" -> MessageSender.JARVIS
                        else -> MessageSender.SYSTEM
                    },
                    text = text,
                    actionTag = tag
                )
            )
        }
    )

    val liveState = liveSessionManager.sessionState
    val sessionRemainingSeconds = liveSessionManager.sessionRemainingSeconds
    val sessionTimerFormatted = liveSessionManager.sessionTimerFormatted
    val inactivitySilenceSeconds = liveSessionManager.inactivitySilenceSeconds
    val liveStatusDescription = liveSessionManager.statusDescription
    val liveAudioRms = liveSessionManager.audioRms
    val isLiveAiSpeaking = liveSessionManager.audioPlayer.isPlaying

    val speechManager = SpeechManager(
        context = application,
        onSpeechRecognized = { text ->
            if (text.isNotBlank()) {
                sendUserMessage(text)
            }
        },
        onListeningStateChanged = { listening ->
            _isListening.value = listening
        }
    ).apply {
        isVoiceEnabled = voiceOutputEnabled.value
        speechPitch = this@JarvisViewModel.speechPitch.value
        speechRate = this@JarvisViewModel.speechRate.value
    }

    val isSpeaking = speechManager.isSpeaking
    val audioRms = speechManager.audioRms

    private val _isAccessibilityEnabled = MutableStateFlow(false)
    val isAccessibilityEnabled = _isAccessibilityEnabled.asStateFlow()

    init {
        // Initial boot welcome message
        addMessage(
            ChatMessage(
                sender = MessageSender.JARVIS,
                text = "VAKAAR Mark LIII protocol online. Systems nominal, Sir Vakaar. Non-stop voice engine listening.",
                actionTag = "⚡ SYSTEM_INIT"
            )
        )

        // Sync speech engine to user settings
        speechManager.speak("Namaste Sir Vakaar, systems online. Main aapki har command sun raha hu.")

        // Start periodic telemetry check
        viewModelScope.launch {
            while (isActive) {
                _telemetry.value = systemController.getDeviceTelemetry()
                _isAccessibilityEnabled.value = systemController.isAccessibilityEnabled()
                checkPermissions()
                delay(4000)
            }
        }

        // If always-on service is enabled, launch it
        if (alwaysOnEnabled.value) {
            startAlwaysOnService()
        }
    }

    fun checkPermissions() {
        val app = getApplication<Application>()
        val hasMic = ContextCompat.checkSelfPermission(
            app,
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val hasCamera = ContextCompat.checkSelfPermission(
            app,
            android.Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        val hasNotif = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                app,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        val isBatteryOptIgnored = systemController.isBatteryOptimizationIgnored()
        val hasAccessibility = systemController.isAccessibilityEnabled()

        _permissionStatus.value = JarvisPermissionStatus(
            hasRecordAudio = hasMic,
            hasPostNotifications = hasNotif,
            hasCamera = hasCamera,
            isBatteryOptimizationIgnored = isBatteryOptIgnored,
            hasAccessibility = hasAccessibility
        )
    }

    fun toggleLiveSession() {
        if (liveState.value == LiveSessionState.LIVE_ACTIVE || liveState.value == LiveSessionState.CONNECTING) {
            liveSessionManager.stopLiveSession("User tapped to stop live session")
        } else {
            speechManager.stopListening()
            liveSessionManager.startLiveSession()
        }
    }

    fun startLiveSession() {
        speechManager.stopListening()
        liveSessionManager.startLiveSession()
    }

    fun stopLiveSession() {
        liveSessionManager.stopLiveSession()
    }

    fun toggleListening() {
        if (liveState.value == LiveSessionState.LIVE_ACTIVE || liveState.value == LiveSessionState.CONNECTING) {
            toggleLiveSession()
        } else {
            toggleLiveSession()
        }
    }

    fun sendUserMessage(text: String) {
        val query = text.trim()
        if (query.isBlank()) return

        addMessage(
            ChatMessage(
                sender = MessageSender.USER,
                text = query
            )
        )

        _isProcessing.value = true

        viewModelScope.launch {
            val history = _messages.value.takeLast(6).map {
                (if (it.sender == MessageSender.USER) "user" else "model") to it.text
            }

            val result = jarvisBrain.processCommand(
                input = query,
                telemetry = _telemetry.value,
                conversationHistory = history,
                onProgress = { stepText, actionTag ->
                    _currentExecutionStep.value = stepText
                    addMessage(
                        ChatMessage(
                            sender = MessageSender.JARVIS,
                            text = stepText,
                            actionTag = actionTag ?: "⚙️ STEP"
                        )
                    )
                    // Speak progressive running commentary aloud!
                    speechManager.speak(stepText)
                }
            )

            _currentExecutionStep.value = null
            _isProcessing.value = false

            addMessage(
                ChatMessage(
                    sender = MessageSender.JARVIS,
                    text = result.replyText,
                    actionTag = result.actionTag
                )
            )

            // Refresh telemetry in case an action changed hardware state
            _telemetry.value = systemController.getDeviceTelemetry()

            // Speak final execution completion or answer
            speechManager.speak(result.replyText)
        }
    }

    fun toggleFlashlight() {
        val newState = systemController.toggleTorch()
        _telemetry.value = systemController.getDeviceTelemetry()
        val text = if (newState) "Flashlight illuminated, sir." else "Flashlight turned off, sir."
        addMessage(
            ChatMessage(
                sender = MessageSender.JARVIS,
                text = text,
                actionTag = if (newState) "⚡ FLASH_ON" else "⚡ FLASH_OFF"
            )
        )
        speechManager.speak(text)
    }

    fun startAlwaysOnService() {
        JarvisService.startService(getApplication())
    }

    fun stopAlwaysOnService() {
        JarvisService.stopService(getApplication())
    }

    fun requestIgnoreBatteryOptimization() {
        systemController.requestIgnoreBatteryOptimizations()
    }

    fun openAccessibilitySettings() {
        systemController.openAccessibilitySettings()
    }

    fun openFreeApiKeyPortal() {
        systemController.openUrl("https://aistudio.google.com/app/apikey")
    }

    suspend fun validateApiKey(key: String): Result<String> {
        return geminiApi.testApiKey(key)
    }

    fun openSettings() {
        _isSettingsOpen.value = true
    }

    fun closeSettings() {
        _isSettingsOpen.value = false
    }

    fun saveSettings(
        apiKey: String,
        alwaysOn: Boolean,
        voiceOutput: Boolean,
        pitch: Float,
        rate: Float
    ) {
        val trimmedKey = apiKey.trim()
        prefs.edit().apply {
            putString("custom_api_key", trimmedKey)
            putBoolean("always_on_service", alwaysOn)
            putBoolean("voice_output", voiceOutput)
            putFloat("speech_pitch", pitch)
            putFloat("speech_rate", rate)
            apply()
        }

        customApiKey.value = trimmedKey
        hasConfiguredApiKey.value = geminiApi.hasConfiguredKey()
        alwaysOnEnabled.value = alwaysOn
        voiceOutputEnabled.value = voiceOutput
        speechPitch.value = pitch
        speechRate.value = rate

        speechManager.isVoiceEnabled = voiceOutput
        speechManager.speechPitch = pitch
        speechManager.speechRate = rate

        if (alwaysOn) {
            startAlwaysOnService()
        } else {
            stopAlwaysOnService()
        }

        closeSettings()
        val keyStatusText = if (trimmedKey.isNotBlank()) "Free Gemini API Key linked & active." else "API key cleared."
        addMessage(
            ChatMessage(
                sender = MessageSender.SYSTEM,
                text = "VAKAAR configuration updated. $keyStatusText",
                actionTag = "⚙️ CONFIG_SAVED"
            )
        )
    }

    fun testVoice(pitch: Float, rate: Float) {
        speechManager.speechPitch = pitch
        speechManager.speechRate = rate
        speechManager.isVoiceEnabled = true
        speechManager.speak("All systems running at maximum efficiency, sir. How may I assist you?")
    }

    private fun addMessage(message: ChatMessage) {
        _messages.value = _messages.value + message
    }

    override fun onCleared() {
        super.onCleared()
        liveSessionManager.release()
        speechManager.release()
    }
}
