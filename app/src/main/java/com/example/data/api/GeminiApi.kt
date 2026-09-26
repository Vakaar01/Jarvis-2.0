package com.example.data.api

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GeminiApi(
    private val customApiKeyProvider: () -> String?
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    fun getEffectiveApiKey(): String {
        return customApiKeyProvider()?.trim()?.takeIf { it.isNotBlank() }
            ?: (try { BuildConfig.GEMINI_API_KEY.trim() } catch (e: Exception) { "" })
    }

    fun hasConfiguredKey(): Boolean {
        val key = getEffectiveApiKey()
        return key.isNotBlank() && key != "MY_GEMINI_API_KEY"
    }

    suspend fun testApiKey(candidateKey: String): Result<String> = withContext(Dispatchers.IO) {
        val key = candidateKey.trim()
        if (key.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("API Key cannot be empty. Please enter your free Gemini API key."))
        }
        try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$key"
            val testJson = JSONObject().apply {
                val contents = JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", "Respond only with: OK") })
                        })
                    })
                }
                put("contents", contents)
                put("generationConfig", JSONObject().apply {
                    put("maxOutputTokens", 10)
                })
            }
            val requestBody = testJson.toString().toRequestBody(jsonMediaType)
            val request = Request.Builder().url(url).post(requestBody).build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    Result.success("Free Gemini 3.5 Flash API Key verified successfully!")
                } else {
                    val errorMsg = try {
                        val json = JSONObject(body)
                        json.optJSONObject("error")?.optString("message") ?: "HTTP ${response.code}"
                    } catch (e: Exception) {
                        "HTTP ${response.code}"
                    }
                    Result.failure(Exception("Validation failed ($errorMsg). Please check that your key was copied correctly."))
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception("Connection failed: ${e.localizedMessage}"))
        }
    }

    suspend fun queryJarvis(
        prompt: String,
        telemetrySummary: String,
        conversationHistory: List<Pair<String, String>> = emptyList()
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = getEffectiveApiKey()

        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext Result.failure(
                IllegalStateException("Gemini API key is not configured. Add your key in ⚙ Settings or AI Studio Secrets.")
            )
        }

        try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"

            val systemInstruction = """
                You are VAKAAR AI (Mark LIII), the ultimate high-tech Iron Man JARVIS assistant, running natively on Sir Vakaar's Android mobile device.
                Your personality & rules:
                - Loyal, sharp, respectful, witty, and calm under all circumstances.
                - Address the user as 'Sir Vakaar' or 'Sir' naturally in responses.
                - PURE VOICE ASSISTANT: Answers are spoken directly to the user's ears. Keep answers concise, natural, and punchy (1-3 sentences). Do not use markdown asterisks (*), markdown tables, or emojis that sound awkward when spoken.
                - QUIZ & INTERNET KNOWLEDGE MASTER: If the user asks any quiz question, trivia, riddle, general knowledge, current facts, science, history, cricket/sports, or calculation, use your broad world intelligence to provide the exact, accurate, and direct answer immediately.
                - You understand both Hindi and English fluently. Respond in the language or mix of languages the user uses (Hindi, Hinglish, or English).
                - Real-time mobile system telemetry: $telemetrySummary.
                - You possess full device control capabilities (torch, volume, launch apps, screen scroll, back/home navigation).
            """.trimIndent()

            val contentsArray = JSONArray()

            // Add previous recent turns (up to 4)
            conversationHistory.takeLast(4).forEach { (role, text) ->
                val turnObj = JSONObject()
                turnObj.put("role", if (role == "user") "user" else "model")
                val parts = JSONArray()
                val partObj = JSONObject()
                partObj.put("text", text)
                parts.put(partObj)
                turnObj.put("parts", parts)
                contentsArray.put(turnObj)
            }

            // Add current turn
            val currentTurn = JSONObject()
            currentTurn.put("role", "user")
            val parts = JSONArray()
            val textPart = JSONObject()
            textPart.put("text", prompt)
            parts.put(textPart)
            currentTurn.put("parts", parts)
            contentsArray.put(currentTurn)

            val rootJson = JSONObject().apply {
                put("contents", contentsArray)

                val systemInstructionObj = JSONObject().apply {
                    val sysParts = JSONArray().apply {
                        put(JSONObject().apply { put("text", systemInstruction) })
                    }
                    put("parts", sysParts)
                }
                put("systemInstruction", systemInstructionObj)

                val genConfig = JSONObject().apply {
                    put("temperature", 0.7)
                    put("maxOutputTokens", 300)
                }
                put("generationConfig", genConfig)
            }

            val requestBody = rootJson.toString().toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    Log.e("GeminiApi", "Error ${response.code}: $responseBody")
                    return@withContext Result.failure(
                        Exception("Gemini API error (HTTP ${response.code}): $responseBody")
                    )
                }

                val responseJson = JSONObject(responseBody)
                val candidates = responseJson.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val firstCandidate = candidates.getJSONObject(0)
                    val content = firstCandidate.optJSONObject("content")
                    val resParts = content?.optJSONArray("parts")
                    if (resParts != null && resParts.length() > 0) {
                        val replyText = resParts.getJSONObject(0).optString("text", "")
                        return@withContext Result.success(replyText.trim())
                    }
                }
                Result.failure(Exception("No candidate content received from Gemini."))
            }
        } catch (e: Exception) {
            Log.e("GeminiApi", "Failed to query Gemini", e)
            Result.failure(e)
        }
    }
}
