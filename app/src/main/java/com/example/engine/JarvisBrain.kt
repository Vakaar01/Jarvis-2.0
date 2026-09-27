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
            val success = systemController.scrollUp()
            return if (success) {
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
            val success = systemController.scrollDown()
            return if (success) {
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

        // 2. Play Video / YouTube ("video chalao", "play video", "youtube par song chalao", "youtube kholo")
        if (query.contains("video chala") || query.contains("play video") || query.contains("gana chala") ||
            query.contains("play song") || query.contains("video play") || query.contains("chala do") ||
            query.contains("youtube")
        ) {
            val videoQuery = rawInput
                .replace(Regex("(?i)(video chalao|video chala do|play video|video|chala do|chalao|gana chalao|play song|song|youtube par|on youtube|open youtube|youtube open karo|youtube kholo|open|kholo|chalu|karo|app|application|jarvis|sir|vakaar)"), " ")
                .trim()

            val success = systemController.playVideoOrYoutube(videoQuery.ifBlank { null })

            val reply = if (videoQuery.isNotBlank()) {
                "Ab YouTube par '$videoQuery' open ho chuki hai, Sir Vakaar."
            } else {
                "Ab YouTube open ho chuka hai, Sir Vakaar."
            }
            return JarvisExecutionResult(
                replyText = reply,
                actionTag = "🎬 PLAY_VIDEO",
                executedSuccessfully = success
            )
        }

        // 3. Navigation Controls: Back, Home, Notifications
        if (query.contains("go home") || query.contains("home screen") || query.contains("home jao")) {
            val ok = systemController.goHome()
            return JarvisExecutionResult(
                replyText = "Ab main home screen par hu, Sir Vakaar.",
                actionTag = "🏠 GO_HOME",
                executedSuccessfully = ok
            )
        }
        if (query.contains("go back") || query.contains("peeche jao") || query.contains("back karo")) {
            val ok = systemController.goBack()
            return JarvisExecutionResult(
                replyText = "Ab peeche aa chuke hain, Sir.",
                actionTag = "◀ GO_BACK",
                executedSuccessfully = ok
            )
        }
        if (query.contains("notification") || query.contains("notifications kholo") || query.contains("shutter")) {
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
            val chargingText = if (telemetry.isCharging) "charging par laga hai" else "battery par chal raha hai"
            return JarvisExecutionResult(
                replyText = "Battery level ${telemetry.batteryPercent}% hai, Sir Vakaar. Device currently $chargingText.",
                actionTag = "🔋 BATTERY_REPORT"
            )
        }

        if (query.contains("ram") || query.contains("memory") || query.contains("storage") || query.contains("specs")) {
            return JarvisExecutionResult(
                replyText = "Memory diagnostic: ${telemetry.freeRamMb}MB RAM free hai total ${telemetry.totalRamMb}MB me se. Internal storage me ${telemetry.freeStorageGb}GB available hai.",
                actionTag = "📊 SYSTEM_TELEMETRY"
            )
        }

        if (query.contains("diagnostics") || query.contains("system status") || query.contains("system report") || query.contains("status")) {
            val status = "Sabhi systems online hain, Sir Vakaar. Battery: ${telemetry.batteryPercent}%, RAM: ${telemetry.freeRamMb}MB free, Network: ${telemetry.networkStatus}, Storage: ${telemetry.freeStorageGb}GB. All protocols nominal."
            return JarvisExecutionResult(
                replyText = status,
                actionTag = "🛡️ FULL_DIAGNOSTIC"
            )
        }

        // 7. Instant Fast-Path for Conversational / Common Knowledge Queries (Zero millisecond response!)
        val instantAnswer = generateInstantKnowledgeResponse(query, rawInput, telemetry)
        if (instantAnswer != null) {
            return JarvisExecutionResult(
                replyText = instantAnswer,
                actionTag = "⚡ INSTANT_REPLY"
            )
        }

        // 8. App Launching ("open youtube", "whatsapp kholo", "instagram open karo", "camera chalu karo", etc.)
        val isLaunchCommand = query.startsWith("open ") || query.startsWith("launch ") || query.startsWith("start ") ||
                query.startsWith("kholo ") || query.contains("kholo") || query.contains("open karo") ||
                query.contains("open ") || query.contains("chalao") || query.contains("chalu karo")

        if (isLaunchCommand && !query.contains("quiz") && !query.contains("battery") && !query.contains("volume") && !query.contains("torch") && !query.contains("flashlight")) {
            val target = query.replace(Regex("(?i)\\b(open|launch|start|kholo|chalao|chalu|karo|app|application|please|jarvis|sir|vakaar)\\b"), " ").trim()

            if (target.isNotBlank()) {
                val success = systemController.launchAppByName(target)
                return if (success) {
                    JarvisExecutionResult(
                        replyText = "Ab $target screen par open ho chuka hai, Sir Vakaar.",
                        actionTag = "🚀 APP_LAUNCH",
                        executedSuccessfully = true
                    )
                } else {
                    systemController.openWebSearch(target)
                    JarvisExecutionResult(
                        replyText = "Sir Vakaar, '$target' phone me nahi mila, isliye web search open kar diya hai.",
                        actionTag = "🌐 WEB_SEARCH",
                        executedSuccessfully = false
                    )
                }
            }
        }

        // 9. Web Search ("search for...", "google...")
        if (query.startsWith("search ") || query.startsWith("google ") || query.startsWith("find ") || query.contains("dhoondho")) {
            val searchTarget = query.replace(Regex("(?i)(search for|search|google|find|dhoondho)"), "").trim()
            if (searchTarget.isNotBlank()) {
                systemController.openWebSearch(searchTarget)
                return JarvisExecutionResult(
                    replyText = "Ab Google par '$searchTarget' search open ho gaya hai, Sir Vakaar.",
                    actionTag = "🌐 WEB_SEARCH"
                )
            }
        }

        // 10. Complex Questions / Research / Internet Knowledge -> Accelerated Gemini API
        val isQuizOrQuestion = query.contains("quiz") || query.contains("question") || query.contains("sawal") ||
                query.contains("kya") || query.contains("kaun") || query.contains("kab") || query.contains("kaha") ||
                query.contains("kaise") || query.contains("why") || query.contains("what") || query.contains("who") ||
                query.contains("when") || query.contains("where") || query.contains("how") || query.contains("?")

        val telemetrySummary = "Battery: ${telemetry.batteryPercent}%, Charging: ${telemetry.isCharging}, Free RAM: ${telemetry.freeRamMb}MB, Network: ${telemetry.networkStatus}"
        val geminiResult = geminiApi.queryJarvis(
            prompt = input,
            telemetrySummary = telemetrySummary,
            conversationHistory = conversationHistory
        )

        return if (geminiResult.isSuccess) {
            JarvisExecutionResult(
                replyText = geminiResult.getOrThrow(),
                actionTag = if (isQuizOrQuestion) "🌐 DIRECT_ANSWER" else "✨ GEMINI_AI"
            )
        } else {
            val fallbackResponse = generateLocalFallback(input, telemetry)
            JarvisExecutionResult(
                replyText = fallbackResponse,
                actionTag = "💡 DIRECT_ANSWER"
            )
        }
    }

    private fun generateInstantKnowledgeResponse(q: String, rawInput: String, telemetry: DeviceTelemetry): String? {
        // Simple Math Calculator (e.g., "5+5", "10 into 2", "50 minus 10")
        val mathRegex = Regex("""(\d+)\s*([\+\-\*\/]|plus|minus|into|divided by)\s*(\d+)""")
        val mathMatch = mathRegex.find(q)
        if (mathMatch != null) {
            val a = mathMatch.groupValues[1].toLongOrNull() ?: 0L
            val op = mathMatch.groupValues[2]
            val b = mathMatch.groupValues[3].toLongOrNull() ?: 0L
            val res = when {
                op == "+" || op == "plus" -> a + b
                op == "-" || op == "minus" -> a - b
                op == "*" || op == "into" -> a * b
                op == "/" || op == "divided by" -> if (b != 0L) a / b else 0L
                else -> null
            }
            if (res != null) {
                return "Sir Vakaar, iska answer $res hai."
            }
        }

        return when {
            q.contains("hello") || q.contains("hey") || q.contains("hi jarvis") || q.contains("namaste") || q.contains("salam") ->
                "Namaste Sir Vakaar! Main bilkul ready hu, aadesh dijiye."

            q.contains("kaise ho") || q.contains("how are you") || q.contains("kya hal") || q.contains("sab theek") ->
                "Main bilkul fit aur shandar hu Sir Vakaar! Sabhi systems 100% active hain. Aap bataiye, aap kaise hain?"

            q.contains("kya kar rahe ho") || q.contains("kya chal raha hai") || q.contains("what are you doing") ->
                "Sir Vakaar, main aapke orders sunne aur unhe turant execute karne ke liye ready hu."

            q.contains("tum kaun ho") || q.contains("who are you") || q.contains("tera naam") || q.contains("apna naam") || q.contains("your name") ->
                "Main VAKAAR AI hu — Sir Vakaar ka personal Iron Man JARVIS assistant."

            q.contains("shukriya") || q.contains("dhanyawad") || q.contains("thanks") || q.contains("thank you") ->
                "Aapka swagat hai Sir Vakaar! Hamesha aapki seva me hazir."

            q.contains("joke") || q.contains("chutkula") || q.contains("hasao") ->
                "Sir ek joke suniye: Ek robot ne doctor se pucha, 'Mujhe thakan kyu hoti hai?' Doctor ne kaha, 'Kyunki tumhara cache clear nahi hua hai!'"

            q.contains("shayari") || q.contains("kavita") || q.contains("sher") ->
                "Sir Vakaar, aapke liye ek shandar sher: 'Manzilon se aage badhkar manzil talash kar, mil jaye tujhko dariya toh samandar talash kar!'"

            q.contains("time") || q.contains("samay") || q.contains("baje") || q.contains("waqt") ->
                "Sir, abhi samay ${java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date())} hai."

            q.contains("date") || q.contains("tarikh") || q.contains("din") || q.contains("aaj ka") || q.contains("today") ->
                "Sir, aaj ${java.text.SimpleDateFormat("EEEE, d MMMM yyyy", java.util.Locale.getDefault()).format(java.util.Date())} hai."

            q.contains("pm") || q.contains("prime minister") || q.contains("pradhan mantri") ->
                "Bharat ke Pradhan Mantri Shri Narendra Modi hain, Sir."

            q.contains("president") || q.contains("rashtrapati") ->
                "Bharat ki Rashtrapati Smt. Droupadi Murmu hain, Sir."

            q.contains("capital") || q.contains("rajdhani") -> {
                when {
                    q.contains("india") || q.contains("bharat") -> "Bharat ki rajdhani New Delhi hai, Sir."
                    q.contains("usa") || q.contains("america") -> "America ki rajdhani Washington D.C. hai, Sir."
                    q.contains("france") -> "France ki rajdhani Paris hai, Sir."
                    q.contains("uk") || q.contains("england") -> "United Kingdom ki rajdhani London hai, Sir."
                    else -> "Bharat ki rajdhani New Delhi hai, Sir Vakaar."
                }
            }

            q.contains("khana khaya") || q.contains("lunch") || q.contains("dinner") ->
                "Main toh electricity aur code par chalta hu Sir Vakaar! Aapne khana kha liya?"

            q.contains("bye") || q.contains("alvida") || q.contains("good night") || q.contains("shubh ratri") ->
                "Alvida Sir Vakaar! Jab bhi zaroorat ho bas ek awaaz dijiyega, main yahin hu."

            else -> null
        }
    }

    private fun generateLocalFallback(input: String, telemetry: DeviceTelemetry): String {
        val q = input.lowercase().trim()

        // 1. Simple Math Calculator (e.g., "5+5", "10 into 2", "50 minus 10")
        val mathRegex = Regex("""(\d+)\s*([\+\-\*\/]|plus|minus|into|divided by)\s*(\d+)""")
        val mathMatch = mathRegex.find(q)
        if (mathMatch != null) {
            val a = mathMatch.groupValues[1].toLongOrNull() ?: 0L
            val op = mathMatch.groupValues[2]
            val b = mathMatch.groupValues[3].toLongOrNull() ?: 0L
            val res = when {
                op == "+" || op == "plus" -> a + b
                op == "-" || op == "minus" -> a - b
                op == "*" || op == "into" -> a * b
                op == "/" || op == "divided by" -> if (b != 0L) a / b else 0L
                else -> null
            }
            if (res != null) {
                return "Sir Vakaar, iska answer $res hai."
            }
        }

        // 2. Comprehensive Direct Voice Response (No browser redirects!)
        return when {
            q.contains("hello") || q.contains("hey") || q.contains("hi") || q.contains("namaste") || q.contains("salam") ->
                "Namaste Sir Vakaar! Main aapka JARVIS assistant active hu. Boliye, main aapke liye kya kar sakta hu?"

            q.contains("kaise ho") || q.contains("how are you") || q.contains("kya hal") || q.contains("sab theek") ->
                "Main bilkul fit aur shandar hu Sir Vakaar! Mark LIII systems 100% active hain. Aap suniye, aap kaise hain?"

            q.contains("kya kar rahe ho") || q.contains("kya chal raha hai") || q.contains("what are you doing") ->
                "Sir Vakaar, main aapke orders aur baatcheet ke liye bilkul taiyar hu."

            q.contains("tum kaun ho") || q.contains("who are you") || q.contains("tera naam") || q.contains("apna naam") ->
                "Main VAKAAR AI hu — Sir Vakaar ka personal Iron Man JARVIS assistant."

            q.contains("shukriya") || q.contains("dhanyawad") || q.contains("thanks") || q.contains("thank you") ->
                "Aapka swagat hai Sir Vakaar! Aapki seva me hamesha hazir."

            q.contains("joke") || q.contains("chutkula") || q.contains("hasao") ->
                "Sir ek joke suniye: Ek robot ne doctor se pucha, 'Mujhe thakan kyu hoti hai?' Doctor ne kaha, 'Kyunki tumhara cache clear nahi hua hai!'"

            q.contains("shayari") || q.contains("kavita") || q.contains("sher") ->
                "Sir Vakaar, aapke liye ek shandar sher: 'Manzilon se aage badhkar manzil talash kar, mil jaye tujhko dariya toh samandar talash kar!'"

            q.contains("time") || q.contains("samay") || q.contains("baje") || q.contains("waqt") ->
                "Sir, abhi samay ${java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date())} hai."

            q.contains("date") || q.contains("tarikh") || q.contains("din") || q.contains("aaj ka") || q.contains("today") ->
                "Sir, aaj ${java.text.SimpleDateFormat("EEEE, d MMMM yyyy", java.util.Locale.getDefault()).format(java.util.Date())} hai."

            q.contains("battery") || q.contains("charging") -> {
                val chargeStatus = if (telemetry.isCharging) "charging par laga hai" else "battery mode par chal raha hai"
                "Sir Vakaar, device battery ${telemetry.batteryPercent}% hai aur phone $chargeStatus."
            }

            q.contains("pm") || q.contains("prime minister") || q.contains("pradhan mantri") ->
                "Bharat ke Pradhan Mantri Shri Narendra Modi hain, Sir."

            q.contains("president") || q.contains("rashtrapati") ->
                "Bharat ki Rashtrapati Smt. Droupadi Murmu hain, Sir."

            q.contains("capital") || q.contains("rajdhani") -> {
                when {
                    q.contains("india") || q.contains("bharat") -> "Bharat ki rajdhani New Delhi hai, Sir."
                    q.contains("usa") || q.contains("america") -> "America ki rajdhani Washington D.C. hai, Sir."
                    q.contains("france") -> "France ki rajdhani Paris hai, Sir."
                    q.contains("uk") || q.contains("england") -> "United Kingdom ki rajdhani London hai, Sir."
                    else -> "Bharat ki rajdhani New Delhi hai, Sir Vakaar."
                }
            }

            q.contains("khana khaya") || q.contains("lunch") || q.contains("dinner") ->
                "Main toh electricity aur code par chalta hu Sir Vakaar! Aapne khana kha liya?"

            q.contains("bye") || q.contains("alvida") || q.contains("good night") || q.contains("shubh ratri") ->
                "Alvida Sir Vakaar! Jab bhi zaroorat ho bas aawaz dijiyega, main yahin hu."

            else ->
                "Ji Sir Vakaar, maine aapki baat suni. Main taiyar hu, agar YouTube, torch, volume, ya koi bhi app kholna ho toh seedha command dijiye."
        }
    }
}
