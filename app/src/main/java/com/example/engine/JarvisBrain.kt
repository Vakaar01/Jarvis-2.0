package com.example.engine

import com.example.data.api.GeminiApi
import com.example.data.model.DeviceTelemetry
import kotlinx.coroutines.delay

data class JarvisExecutionResult(
    val replyText: String,
    val actionTag: String? = null,
    val executedSuccessfully: Boolean = true
)

class JarvisBrain(
    private val systemController: SystemController,
    private val geminiApi: GeminiApi
) {

    suspend fun processCommand(
        input: String,
        telemetry: DeviceTelemetry,
        conversationHistory: List<Pair<String, String>> = emptyList(),
        onProgress: (suspend (stepText: String, actionTag: String?) -> Unit)? = null
    ): JarvisExecutionResult {
        val rawInput = input.trim()
        val query = rawInput.lowercase()

        // 1. Accessibility Gestures: Scroll Up / Down
        if (query.contains("scroll up") || query.contains("upar scroll") || query.contains("upar karo") || query.contains("scroll upar")) {
            onProgress?.invoke("Screen gesture coordinates calculate kar raha hu...", "📜 GESTURE_START")
            delay(700)
            val success = systemController.scrollUp()
            return if (success) {
                onProgress?.invoke("Ab scroll up execute ho raha hai...", "📜 GESTURE_RUN")
                delay(600)
                JarvisExecutionResult(
                    replyText = "Ab screen upar scroll kar di hai, Sir Vakaar.",
                    actionTag = "📜 SCROLL_UP"
                )
            } else {
                systemController.openAccessibilitySettings()
                JarvisExecutionResult(
                    replyText = "Sir, scrolling access ke liye please VAKAAR Accessibility Service ko enable kijiye.",
                    actionTag = "⚠️ NEED_ACCESSIBILITY",
                    executedSuccessfully = false
                )
            }
        }

        if (query.contains("scroll down") || query.contains("niche scroll") || query.contains("niche karo") || query.contains("scroll niche") || query.contains("scroll")) {
            onProgress?.invoke("Screen gesture coordinates calculate kar raha hu...", "📜 GESTURE_START")
            delay(700)
            val success = systemController.scrollDown()
            return if (success) {
                onProgress?.invoke("Ab scroll down execute ho raha hai...", "📜 GESTURE_RUN")
                delay(600)
                JarvisExecutionResult(
                    replyText = "Ab screen niche scroll kar di hai, Sir Vakaar.",
                    actionTag = "📜 SCROLL_DOWN"
                )
            } else {
                systemController.openAccessibilitySettings()
                JarvisExecutionResult(
                    replyText = "Sir, screen scroll karne ke liye VAKAAR Accessibility permission on karni hogi.",
                    actionTag = "⚠️ NEED_ACCESSIBILITY",
                    executedSuccessfully = false
                )
            }
        }

        // 2. Play Video / YouTube ("video chalao", "play video", "youtube par song chalao")
        if (query.contains("video chala") || query.contains("play video") || query.contains("gana chala") || query.contains("play song") || query.contains("video play") || query.contains("chala do")) {
            val videoQuery = rawInput
                .replace(Regex("(?i)(video chalao|video chala do|play video|video|chala do|chalao|gana chalao|play song|song|youtube par|on youtube)"), "")
                .trim()

            onProgress?.invoke("Task received, Sir Vakaar. Target video search kiya ja raha hai...", "🎬 TASK_INIT")
            delay(800)
            onProgress?.invoke("Ab YouTube module connect ho raha hai...", "🔗 CONNECTING")
            delay(700)

            val success = systemController.playVideoOrYoutube(videoQuery.ifBlank { null })
            delay(600)

            val reply = if (videoQuery.isNotBlank()) {
                "Ab '$videoQuery' screen par live chal rahi hai, Sir Vakaar."
            } else {
                "Ab video player load ho chuka hai, Sir Vakaar."
            }
            return JarvisExecutionResult(
                replyText = reply,
                actionTag = "🎬 PLAY_VIDEO",
                executedSuccessfully = success
            )
        }

        // 3. Navigation Controls: Back, Home, Notifications
        if (query.contains("go home") || query.contains("home screen") || query.contains("home jao")) {
            onProgress?.invoke("Home protocol trigger ho raha hai...", "🏠 INITIATING")
            delay(500)
            val ok = systemController.goHome()
            delay(500)
            return JarvisExecutionResult(
                replyText = "Ab main home screen par hu, Sir Vakaar.",
                actionTag = "🏠 GO_HOME",
                executedSuccessfully = ok
            )
        }
        if (query.contains("go back") || query.contains("peeche jao") || query.contains("back karo")) {
            onProgress?.invoke("Previous stack navigation trigger kiya...", "◀ INITIATING")
            delay(400)
            val ok = systemController.goBack()
            return JarvisExecutionResult(
                replyText = "Ab peeche aa chuke hain, Sir.",
                actionTag = "◀ GO_BACK",
                executedSuccessfully = ok
            )
        }
        if (query.contains("notification") || query.contains("notifications kholo") || query.contains("shutter")) {
            onProgress?.invoke("Notification panel pull down ho raha hai...", "🔔 INITIATING")
            delay(500)
            val ok = systemController.openNotifications()
            return JarvisExecutionResult(
                replyText = "Ab notification shade open ho chuka hai, Sir.",
                actionTag = "🔔 NOTIFICATIONS",
                executedSuccessfully = ok
            )
        }

        // 4. Hardware: Flashlight / Torch
        if (query.contains("flashlight") || query.contains("torch") || query.contains("light")) {
            if (query.contains("on") || query.contains("enable") || query.contains("activate") || query.contains("chalu") || query.contains("jalao")) {
                onProgress?.invoke("Camera flash hardware circuit connect kar raha hu...", "⚡ BUS_CONNECT")
                delay(600)
                val ok = systemController.setTorch(true)
                return if (ok) {
                    JarvisExecutionResult(
                        replyText = "Ab flashlight activate ho gayi hai, Sir Vakaar.",
                        actionTag = "⚡ FLASH_ON"
                    )
                } else {
                    JarvisExecutionResult(
                        replyText = "Camera flash module par access nahi mila, sir.",
                        actionTag = "⚠️ FLASH_ERROR",
                        executedSuccessfully = false
                    )
                }
            } else if (query.contains("off") || query.contains("disable") || query.contains("deactivate") || query.contains("band") || query.contains("bujhao")) {
                onProgress?.invoke("Flash cut-off signal send ho raha hai...", "⚡ BUS_DISCONNECT")
                delay(600)
                val ok = systemController.setTorch(false)
                return if (ok) {
                    JarvisExecutionResult(
                        replyText = "Ab flashlight band ho chuki hai, Sir.",
                        actionTag = "⚡ FLASH_OFF"
                    )
                } else {
                    JarvisExecutionResult(
                        replyText = "Unable to turn off flash module, sir.",
                        actionTag = "⚠️ FLASH_ERROR",
                        executedSuccessfully = false
                    )
                }
            } else if (query.contains("toggle")) {
                val newState = systemController.toggleTorch()
                return JarvisExecutionResult(
                    replyText = if (newState) "Ab flashlight on ho chuki hai, Sir Vakaar." else "Ab flashlight off ho chuki hai, Sir Vakaar.",
                    actionTag = "⚡ FLASH_TOGGLE"
                )
            }
        }

        // 5. Hardware: Volume Control
        if (query.contains("volume") || query.contains("sound") || query.contains("awaz")) {
            onProgress?.invoke("Audio mixer calibrate kar raha hu...", "🔊 AUDIO_SYNC")
            delay(500)
            if (query.contains("up") || query.contains("raise") || query.contains("increase") || query.contains("badhao") || query.contains("tez")) {
                val vol = systemController.adjustVolume(true)
                return JarvisExecutionResult(
                    replyText = "Ab volume badha diya hai. Main level $vol% par hu, Sir Vakaar.",
                    actionTag = "🔊 VOL_UP"
                )
            } else if (query.contains("down") || query.contains("lower") || query.contains("decrease") || query.contains("kam") || query.contains("dheemi")) {
                val vol = systemController.adjustVolume(false)
                return JarvisExecutionResult(
                    replyText = "Ab volume kam kar diya hai. Current level $vol% hai, Sir Vakaar.",
                    actionTag = "🔉 VOL_DOWN"
                )
            } else if (query.contains("mute") || query.contains("silent") || query.contains("chup") || query.contains("zero")) {
                val vol = systemController.setVolume(0)
                return JarvisExecutionResult(
                    replyText = "Ab audio output mute kar diya hai, sir.",
                    actionTag = "🔇 VOL_MUTE"
                )
            } else if (query.contains("max") || query.contains("full") || query.contains("100")) {
                val vol = systemController.setVolume(100)
                return JarvisExecutionResult(
                    replyText = "Ab volume full 100% par set ho gaya hai, Sir Vakaar.",
                    actionTag = "🔊 VOL_MAX"
                )
            }
            // Parse specific percentage (e.g., "volume 80")
            val regex = Regex("""\b(\d{1,3})\s*%?""")
            val match = regex.find(query)
            if (match != null) {
                val pct = match.groupValues[1].toIntOrNull()
                if (pct != null && pct in 0..100) {
                    val actual = systemController.setVolume(pct)
                    return JarvisExecutionResult(
                        replyText = "Ab volume $actual% par adjust kar diya hai, sir.",
                        actionTag = "🔊 VOL_SET"
                    )
                }
            }
        }

        // 6. System Telemetry & Diagnostics
        if (query.contains("battery") || query.contains("power") || query.contains("charging") || query.contains("charge")) {
            onProgress?.invoke("Power telemetry sensors query kar raha hu...", "🔋 POWER_CHECK")
            delay(500)
            val chargingText = if (telemetry.isCharging) "charging par laga hai" else "battery par chal raha hai"
            return JarvisExecutionResult(
                replyText = "Battery level ${telemetry.batteryPercent}% hai, Sir Vakaar. Device currently $chargingText.",
                actionTag = "🔋 BATTERY_REPORT"
            )
        }

        if (query.contains("ram") || query.contains("memory") || query.contains("storage") || query.contains("specs")) {
            onProgress?.invoke("Memory bus diagnostic check initiate...", "📊 RAM_CHECK")
            delay(500)
            return JarvisExecutionResult(
                replyText = "Memory diagnostic: ${telemetry.freeRamMb}MB RAM free hai total ${telemetry.totalRamMb}MB me se. Internal storage me ${telemetry.freeStorageGb}GB available hai.",
                actionTag = "📊 SYSTEM_TELEMETRY"
            )
        }

        if (query.contains("diagnostics") || query.contains("system status") || query.contains("system report") || query.contains("status")) {
            onProgress?.invoke("All systems diagnostic scan in progress...", "🛡️ SCAN")
            delay(600)
            val status = "Sabhi systems online hain, Sir Vakaar. Battery: ${telemetry.batteryPercent}%, RAM: ${telemetry.freeRamMb}MB free, Network: ${telemetry.networkStatus}, Storage: ${telemetry.freeStorageGb}GB. All protocols nominal."
            return JarvisExecutionResult(
                replyText = status,
                actionTag = "🛡️ FULL_DIAGNOSTIC"
            )
        }

        // 7. App Launching ("open youtube", "whatsapp kholo", etc.)
        if (query.startsWith("open ") || query.startsWith("launch ") || query.startsWith("start ") || query.startsWith("kholo ") || query.contains("kholo")) {
            val target = query.replace(Regex("(?i)(open|launch|start|kholo|app)"), "").trim()

            if (target.isNotBlank()) {
                onProgress?.invoke("Application database scan ho raha hai...", "🔍 SCANNING")
                delay(700)
                onProgress?.invoke("Ab $target launch kar raha hu...", "🚀 LAUNCHING")
                delay(600)
                val success = systemController.launchAppByName(target)
                return if (success) {
                    JarvisExecutionResult(
                        replyText = "Ab $target screen par open ho chuka hai, Sir Vakaar.",
                        actionTag = "🚀 APP_LAUNCH"
                    )
                } else {
                    JarvisExecutionResult(
                        replyText = "'$target' app phone me nahi mila sir, main web par dhoondh raha hu.",
                        actionTag = "⚠️ APP_NOT_FOUND"
                    ).also {
                        systemController.openWebSearch(target)
                    }
                }
            }
        }

        // 8. Web Search ("search for...", "google...")
        if (query.startsWith("search ") || query.startsWith("google ") || query.startsWith("find ") || query.contains("dhoondho")) {
            val searchTarget = query.replace(Regex("(?i)(search for|search|google|find|dhoondho)"), "").trim()
            if (searchTarget.isNotBlank()) {
                onProgress?.invoke("Google search query transmit kar raha hu...", "🌐 SEARCH_INIT")
                delay(500)
                systemController.openWebSearch(searchTarget)
                return JarvisExecutionResult(
                    replyText = "Ab Google par '$searchTarget' search open ho gaya hai, Sir Vakaar.",
                    actionTag = "🌐 WEB_SEARCH"
                )
            }
        }

        // 9. Identity & Protocol Information
        if (query.contains("who are you") || query.contains("your name") || query.contains("tum kaun ho") || query.contains("naam kya hai")) {
            return JarvisExecutionResult(
                replyText = "Main VAKAAR AI hu — Sir Vakaar ka custom Iron Man JARVIS assistant. Aapke pure mobile ko control karne aur nonstop orders follow karne ke liye taiyar.",
                actionTag = "🤖 IDENTITY"
            )
        }

        // 10. Quiz / Internet Knowledge / Questions / High Intelligence -> Gemini API
        val isQuizOrQuestion = query.contains("quiz") || query.contains("question") || query.contains("sawal") ||
                query.contains("kya") || query.contains("kaun") || query.contains("kab") || query.contains("kaha") ||
                query.contains("kaise") || query.contains("why") || query.contains("what") || query.contains("who") ||
                query.contains("when") || query.contains("where") || query.contains("how") || query.contains("?")

        if (isQuizOrQuestion) {
            onProgress?.invoke("Sir Vakaar, internet knowledge database scan kar raha hu...", "🌐 SCANNING_INTERNET")
            delay(500)
        } else {
            onProgress?.invoke("Neural core query process kar raha hai...", "🧠 THINKING")
            delay(300)
        }

        val telemetrySummary = "Battery: ${telemetry.batteryPercent}%, Charging: ${telemetry.isCharging}, Free RAM: ${telemetry.freeRamMb}MB, Network: ${telemetry.networkStatus}"
        val geminiResult = geminiApi.queryJarvis(
            prompt = input,
            telemetrySummary = telemetrySummary,
            conversationHistory = conversationHistory
        )

        return if (geminiResult.isSuccess) {
            JarvisExecutionResult(
                replyText = geminiResult.getOrThrow(),
                actionTag = if (isQuizOrQuestion) "🌐 INTERNET_QUIZ" else "✨ GEMINI_AI"
            )
        } else {
            val ex = geminiResult.exceptionOrNull()
            if (ex?.message?.contains("API key is not configured", ignoreCase = true) == true) {
                JarvisExecutionResult(
                    replyText = "Sir Vakaar, AI brain activate karne ke liye Setting (⚙️) me jakar apni FREE Google Gemini API key enter karein. Google AI Studio se key bilkul free milti hai.",
                    actionTag = "🔑 FREE_API_KEY_REQUIRED",
                    executedSuccessfully = false
                )
            } else {
                val fallbackResponse = generateLocalFallback(input, telemetry)
                JarvisExecutionResult(
                    replyText = fallbackResponse,
                    actionTag = "💡 LOCAL_HEURISTIC"
                )
            }
        }
    }

    private fun generateLocalFallback(input: String, telemetry: DeviceTelemetry): String {
        val q = input.lowercase()
        return when {
            q.contains("hello") || q.contains("hey") || q.contains("hi") || q.contains("namaste") || q.contains("salam") ->
                "Namaste Sir Vakaar! Main aapka JARVIS assistant hu, boliye kya order hai?"
            q.contains("time") || q.contains("samay") || q.contains("baje") ->
                "Sir, abhi ka samay ${java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date())} hai."
            q.contains("date") || q.contains("tarikh") || q.contains("din") ->
                "Sir, aaj ${java.text.SimpleDateFormat("EEEE, MMMM d, yyyy", java.util.Locale.getDefault()).format(java.util.Date())} hai."
            q.contains("kaise ho") || q.contains("how are you") ->
                "Main bilkul shandar hu Sir Vakaar! Mark LIII core systems 100% nominal condition me hain."
            else ->
                "Ji Sir Vakaar, aapka order receive ho gaya hai. Main aapke command par execute kar raha hu."
        }
    }
}
