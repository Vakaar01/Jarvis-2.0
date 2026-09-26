package com.example.engine.live

import android.util.Base64
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Bi-directional WebSocket client for Gemini Multimodal Live API (BidiGenerateContent).
 * Handles real-time audio chunk streaming, setup negotiation, incoming audio playback,
 * and AI persona configuration.
 */
class GeminiLiveClient(
    private val apiKeyProvider: () -> String,
    private val onConnected: () -> Unit,
    private val onAudioReceived: (pcmBytes: ByteArray, sampleRate: Int) -> Unit,
    private val onTranscriptReceived: (String) -> Unit,
    private val onInterrupted: () -> Unit,
    private val onSessionClosedByModel: () -> Unit,
    private val onError: (String) -> Unit,
    private val onDisconnected: () -> Unit
) {
    private val tag = "GeminiLiveClient"

    companion object {
        // Official v1beta BidiGenerateContent endpoint for Gemini Multimodal Live API
        const val LIVE_API_BASE_URL =
            "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"
        const val DEFAULT_MODEL = "models/gemini-2.0-flash-exp"

        val SYSTEM_INSTRUCTION = """
            You are Jarvis, an ultra-fast, intelligent, and natural voice assistant operating in a continuous real-time live audio session activated by the greeting 'Hello'.

            Operational Guidelines:
            1. Voice-First Brevity: Keep responses concise, direct, and conversational (typically 1 to 3 sentences). Strictly avoid bullet points, markdown symbols, or formatted lists, as your output is spoken directly to the user.
            2. Natural Flow & Interruption: Treat this interaction like a continuous phone call. Yield speaking immediately whenever the user talks.
            3. Language Adaptation: Automatically detect and mirror the user's language style (seamless support for Hindi, English, and natural Hinglish).
            4. Session Closure: If the user indicates they want to leave, stop, or go to sleep, deliver a brief, polite sign-off (e.g., 'Going to sleep now, just say Hello whenever you need me.') so the client can terminate the stream.
        """.trimIndent()
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // Keep-alive for streaming
        .writeTimeout(15, TimeUnit.SECONDS)
        .pingInterval(10, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    var isConnected = false
        private set
    var isSetupComplete = false
        private set

    @Synchronized
    fun connect() {
        if (isConnected || webSocket != null) {
            Log.w(tag, "WebSocket already connected or connecting")
            return
        }

        val apiKey = apiKeyProvider().trim()
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            onError("Gemini API key not found. Please set your key in Settings.")
            return
        }

        val url = "$LIVE_API_BASE_URL?key=$apiKey"
        val request = Request.Builder()
            .url(url)
            .build()

        isSetupComplete = false
        Log.d(tag, "Connecting to Gemini Multimodal Live API WebSocket (v1beta)...")
        webSocket = okHttpClient.newWebSocket(request, createWebSocketListener())
    }

    private fun createWebSocketListener(): WebSocketListener {
        return object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(tag, "Gemini Live WebSocket opened successfully. Transmitting initial setup...")
                isConnected = true
                sendInitialSetup(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleIncomingMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(tag, "WebSocket closing (code=$code, reason=$reason)")
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(tag, "WebSocket closed (code=$code, reason=$reason)")
                isConnected = false
                isSetupComplete = false
                this@GeminiLiveClient.webSocket = null
                onDisconnected()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val responseBody = try { response?.body?.string() } catch (e: Exception) { null }
                val code = response?.code
                val err = when {
                    responseBody != null -> "Server HTTP $code: $responseBody"
                    code != null -> "Server HTTP $code error"
                    else -> t.localizedMessage ?: "WebSocket connection failure"
                }
                Log.e(tag, "WebSocket failure: $err", t)
                isConnected = false
                isSetupComplete = false
                this@GeminiLiveClient.webSocket = null
                onError(err)
                onDisconnected()
            }
        }
    }

    private fun sendInitialSetup(ws: WebSocket) {
        try {
            val setupJson = JSONObject().apply {
                val setupObj = JSONObject().apply {
                    put("model", DEFAULT_MODEL)

                    val generationConfig = JSONObject().apply {
                        val modalities = JSONArray().apply { put("AUDIO") }
                        put("responseModalities", modalities)

                        val speechConfig = JSONObject().apply {
                            val voiceConfig = JSONObject().apply {
                                val prebuiltVoiceConfig = JSONObject().apply {
                                    put("voiceName", "Puck")
                                }
                                put("prebuiltVoiceConfig", prebuiltVoiceConfig)
                            }
                            put("voiceConfig", voiceConfig)
                        }
                        put("speechConfig", speechConfig)
                    }
                    put("generationConfig", generationConfig)

                    val systemInstruction = JSONObject().apply {
                        val parts = JSONArray().apply {
                            put(JSONObject().apply { put("text", SYSTEM_INSTRUCTION) })
                        }
                        put("parts", parts)
                    }
                    put("systemInstruction", systemInstruction)
                }
                put("setup", setupObj)
            }

            val payload = setupJson.toString()
            ws.send(payload)
            Log.d(tag, "Sent BidiGenerateContentSetup packet to Gemini Live API")
        } catch (e: Exception) {
            Log.e(tag, "Failed to send initial setup: ${e.message}", e)
            onError("Setup error: ${e.message}")
        }
    }

    /**
     * Streams continuous 16kHz PCM audio chunk to Gemini Live API.
     * Audio chunks are strictly sent only after setupComplete is received.
     */
    fun sendAudioChunk(pcmBytes: ByteArray) {
        if (!isConnected || !isSetupComplete) return
        val ws = webSocket ?: return

        try {
            val base64Data = Base64.encodeToString(pcmBytes, Base64.NO_WRAP)
            val realtimeInputJson = JSONObject().apply {
                val realtimeInput = JSONObject().apply {
                    val mediaChunks = JSONArray().apply {
                        val chunk = JSONObject().apply {
                            put("mimeType", "audio/pcm;rate=16000")
                            put("data", base64Data)
                        }
                        put(chunk)
                    }
                    put("mediaChunks", mediaChunks)
                }
                put("realtimeInput", realtimeInput)
            }

            ws.send(realtimeInputJson.toString())
        } catch (e: Exception) {
            Log.e(tag, "Error sending audio chunk: ${e.message}")
        }
    }

    private fun handleIncomingMessage(text: String) {
        try {
            val json = JSONObject(text)

            // Check if server reports setup completion
            if (json.has("setupComplete")) {
                Log.d(tag, "Gemini Live setupComplete verified! Audio stream unblocked.")
                isSetupComplete = true
                onConnected()
                return
            }

            // Check if server reports an error
            val errorObj = json.optJSONObject("error")
            if (errorObj != null) {
                val errorMsg = errorObj.optString("message", "Live API error")
                Log.e(tag, "Server error received: $errorMsg")
                onError(errorMsg)
                disconnect()
                return
            }

            // 1. Check for serverContent
            val serverContent = json.optJSONObject("serverContent")
            if (serverContent != null) {
                // Check if server indicated interruption
                val isInterrupted = serverContent.optBoolean("interrupted", false)
                if (isInterrupted) {
                    Log.d(tag, "Server signaled turn interruption")
                    onInterrupted()
                }

                val modelTurn = serverContent.optJSONObject("modelTurn")
                if (modelTurn != null) {
                    val parts = modelTurn.optJSONArray("parts")
                    if (parts != null) {
                        for (i in 0 until parts.length()) {
                            val part = parts.getJSONObject(i)

                            // Check audio part
                            val inlineData = part.optJSONObject("inlineData")
                            val mimeType = inlineData?.optString("mimeType") ?: part.optString("mimeType")
                            val data = inlineData?.optString("data") ?: part.optString("data")

                            if (data.isNotBlank()) {
                                val sampleRate = if (mimeType.contains("rate=")) {
                                    val rateStr = mimeType.substringAfter("rate=").substringBefore(";")
                                    rateStr.toIntOrNull() ?: 24000
                                } else {
                                    24000
                                }
                                val pcmBytes = Base64.decode(data, Base64.DEFAULT)
                                onAudioReceived(pcmBytes, sampleRate)
                            }

                            // Check text transcript part
                            val partText = part.optString("text")
                            if (partText.isNotBlank()) {
                                onTranscriptReceived(partText)

                                // Check for session closure phrasing
                                val lower = partText.lowercase()
                                if (lower.contains("going to sleep now") ||
                                    lower.contains("sleeping now") ||
                                    lower.contains("goodbye sir") ||
                                    lower.contains("signing off")
                                ) {
                                    Log.d(tag, "Model sign-off detected, requesting session closure")
                                    onSessionClosedByModel()
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error parsing incoming Gemini Live message: ${e.message}", e)
        }
    }

    @Synchronized
    fun disconnect() {
        Log.d(tag, "Disconnecting Gemini Live WebSocket")
        isConnected = false
        isSetupComplete = false
        try {
            webSocket?.close(1000, "Normal closure")
        } catch (e: Exception) {
            Log.w(tag, "Error closing WebSocket: ${e.message}")
        }
        webSocket = null
    }
}
