package com.example.engine

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import com.example.data.model.DeviceTelemetry

class SystemController(private val context: Context) {

    private val cameraManager by lazy {
        context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    }

    private val audioManager by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }

    private val powerManager by lazy {
        context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    }

    private var isTorchActive = false

    fun toggleTorch(): Boolean {
        return setTorch(!isTorchActive)
    }

    fun setTorch(enable: Boolean): Boolean {
        val manager = cameraManager ?: return false
        return try {
            val cameraId = manager.cameraIdList.firstOrNull { id ->
                val chars = manager.getCameraCharacteristics(id)
                chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                        chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            } ?: manager.cameraIdList.firstOrNull()

            if (cameraId != null) {
                manager.setTorchMode(cameraId, enable)
                isTorchActive = enable
                true
            } else {
                false
            }
        } catch (e: CameraAccessException) {
            Log.e("SystemController", "CameraAccessException toggling torch", e)
            false
        } catch (e: Exception) {
            Log.e("SystemController", "Failed to toggle torch", e)
            false
        }
    }

    fun getTorchState(): Boolean = isTorchActive

    fun setVolume(percentage: Int): Int {
        val am = audioManager ?: return 50
        val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val target = ((percentage.coerceIn(0, 100) / 100f) * maxVol).toInt()
        am.setStreamVolume(AudioManager.STREAM_MUSIC, target, AudioManager.FLAG_SHOW_UI)
        return getVolumePercentage()
    }

    fun adjustVolume(increase: Boolean): Int {
        val am = audioManager ?: return 50
        val direction = if (increase) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
        am.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
        return getVolumePercentage()
    }

    fun getVolumePercentage(): Int {
        val am = audioManager ?: return 50
        val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (maxVol == 0) return 0
        val current = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        return ((current.toFloat() / maxVol) * 100).toInt()
    }

    fun getDeviceTelemetry(): DeviceTelemetry {
        // Battery
        val batteryIntent = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: 50
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: 100
        val batteryPct = if (scale > 0) (level * 100 / scale) else 50
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        // Memory (RAM)
        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager?.getMemoryInfo(memInfo)
        val freeRamMb = memInfo.availMem / (1024 * 1024)
        val totalRamMb = memInfo.totalMem / (1024 * 1024)

        // Storage
        val stat = StatFs(Environment.getDataDirectory().path)
        val freeBytes = stat.availableBlocksLong * stat.blockSizeLong
        val totalBytes = stat.blockCountLong * stat.blockSizeLong
        val freeGb = Math.round((freeBytes.toDouble() / (1024 * 1024 * 1024)) * 10.0) / 10.0
        val totalGb = Math.round((totalBytes.toDouble() / (1024 * 1024 * 1024)) * 10.0) / 10.0

        // Network
        val connMgr = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        var netDesc = "Offline"
        connMgr?.let {
            val network = it.activeNetwork
            val capabilities = it.getNetworkCapabilities(network)
            if (capabilities != null) {
                netDesc = when {
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi Online (High-Speed)"
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular 4G/5G"
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet Link"
                    else -> "Connected"
                }
            }
        }

        return DeviceTelemetry(
            batteryPercent = batteryPct,
            isCharging = isCharging,
            freeRamMb = freeRamMb,
            totalRamMb = totalRamMb,
            freeStorageGb = freeGb,
            totalStorageGb = totalGb,
            networkStatus = netDesc,
            volumePercent = getVolumePercentage(),
            isFlashlightOn = isTorchActive
        )
    }

    fun isBatteryOptimizationIgnored(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = powerManager ?: return false
            return pm.isIgnoringBatteryOptimizations(context.packageName)
        }
        return true
    }

    fun requestIgnoreBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                // Fallback to battery saver settings
                try {
                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                } catch (ex: Exception) {
                    Log.e("SystemController", "Failed to open battery optimization settings", ex)
                }
            }
        }
    }

    fun launchAppByName(name: String): Boolean {
        val pm = context.packageManager
        val query = name.lowercase().trim()

        // 1. Check for standard Android system intent actions first
        if (query.contains("camera") || query.contains("photo") || query.contains("tasveer") || query.contains("selfie")) {
            try {
                val camIntent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(camIntent)
                return true
            } catch (e: Exception) {
                Log.w("SystemController", "Direct camera intent failed, falling back to package scan", e)
            }
        }

        if (query.contains("setting") || query.contains("settings")) {
            try {
                val setIntent = Intent(Settings.ACTION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(setIntent)
                return true
            } catch (e: Exception) {
                Log.w("SystemController", "Direct settings intent failed", e)
            }
        }

        if (query.contains("dialer") || query.contains("phone") || query.contains("dial") || query.contains("call")) {
            try {
                val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(dialIntent)
                return true
            } catch (e: Exception) {
                Log.w("SystemController", "Direct dialer intent failed", e)
            }
        }

        if (query.contains("calculator") || query.contains("calc") || query.contains("hisab")) {
            try {
                val calcIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_APP_CALCULATOR)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(calcIntent)
                return true
            } catch (e: Exception) {
                Log.w("SystemController", "Category calculator failed", e)
            }
        }

        if (query.contains("clock") || query.contains("alarm") || query.contains("ghadi") || query.contains("samay")) {
            try {
                val clockIntent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(clockIntent)
                return true
            } catch (e: Exception) {
                Log.w("SystemController", "AlarmClock intent failed", e)
            }
        }

        if (query.contains("gallery") || query.contains("photos")) {
            try {
                val galIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_APP_GALLERY)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(galIntent)
                return true
            } catch (e: Exception) {
                Log.w("SystemController", "Gallery category failed", e)
            }
        }

        if (query.contains("map") || query.contains("maps") || query.contains("location") || query.contains("rasta")) {
            try {
                val mapIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(mapIntent)
                return true
            } catch (e: Exception) {
                Log.w("SystemController", "Map intent failed", e)
            }
        }

        if (query.contains("youtube") || query.contains("yt")) {
            return playVideoOrYoutube(null)
        }

        if (query.contains("play store") || query.contains("playstore")) {
            try {
                val playIntent = pm.getLaunchIntentForPackage("com.android.vending") ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                playIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(playIntent)
                return true
            } catch (e: Exception) {
                Log.w("SystemController", "Play Store launch failed", e)
            }
        }

        if (query.contains("chrome") || query.contains("browser") || query.contains("internet")) {
            try {
                val chromeIntent = pm.getLaunchIntentForPackage("com.android.chrome") ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                chromeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chromeIntent)
                return true
            } catch (e: Exception) {
                Log.w("SystemController", "Chrome/Browser launch failed", e)
            }
        }

        // 2. Known popular package identifiers
        val knownPackages = mapOf(
            "whatsapp" to listOf("com.whatsapp", "com.whatsapp.w4b"),
            "instagram" to listOf("com.instagram.android"),
            "telegram" to listOf("org.telegram.messenger", "org.telegram.messenger.web"),
            "spotify" to listOf("com.spotify.music"),
            "facebook" to listOf("com.facebook.katana", "com.facebook.lite"),
            "gmail" to listOf("com.google.android.gm"),
            "snapchat" to listOf("com.snapchat.android"),
            "twitter" to listOf("com.twitter.android"),
            "x" to listOf("com.twitter.android"),
            "truecaller" to listOf("com.truecaller"),
            "paytm" to listOf("net.one97.paytm"),
            "phonepe" to listOf("com.phonepe.app")
        )

        for ((key, packages) in knownPackages) {
            if (query.contains(key)) {
                for (pkg in packages) {
                    val intent = pm.getLaunchIntentForPackage(pkg)
                    if (intent != null) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                        return true
                    }
                }
            }
        }

        // 3. Scan all installed launcher apps on the device (requires QUERY_ALL_PACKAGES)
        try {
            val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfoList = pm.queryIntentActivities(launcherIntent, 0)
            for (info in resolveInfoList) {
                val label = info.loadLabel(pm).toString().lowercase()
                val pkgName = info.activityInfo.packageName.lowercase()

                // Check label match or package match
                if (label.contains(query) || query.contains(label) || pkgName.contains(query)) {
                    val intent = pm.getLaunchIntentForPackage(info.activityInfo.packageName)
                    if (intent != null) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                        return true
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SystemController", "Error querying launcher intent activities", e)
        }

        return false
    }

    fun openWebSearch(searchQuery: String) {
        try {
            val escapedQuery = Uri.encode(searchQuery)
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$escapedQuery")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("SystemController", "Failed to launch web search", e)
        }
    }

    fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("SystemController", "Failed to open URL $url", e)
        }
    }

    fun playVideoOrYoutube(query: String?): Boolean {
        return try {
            val pm = context.packageManager
            if (!query.isNullOrBlank()) {
                val ytSearchIntent = Intent(Intent.ACTION_SEARCH).apply {
                    setPackage("com.google.android.youtube")
                    putExtra("query", query)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                try {
                    context.startActivity(ytSearchIntent)
                    return true
                } catch (e: Exception) {
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(query)}")).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(browserIntent)
                    return true
                }
            } else {
                val ytLaunchIntent = pm.getLaunchIntentForPackage("com.google.android.youtube")
                if (ytLaunchIntent != null) {
                    ytLaunchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(ytLaunchIntent)
                    return true
                } else {
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com")).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(browserIntent)
                    return true
                }
            }
        } catch (e: Exception) {
            Log.e("SystemController", "Failed to play video/youtube", e)
            false
        }
    }

    fun scrollUp(): Boolean {
        val service = com.example.service.JarvisAccessibilityService.instance
        return service?.performScrollUpAction() ?: false
    }

    fun scrollDown(): Boolean {
        val service = com.example.service.JarvisAccessibilityService.instance
        return service?.performScrollDownAction() ?: false
    }

    fun goHome(): Boolean {
        val service = com.example.service.JarvisAccessibilityService.instance
        return service?.performHome() ?: false
    }

    fun goBack(): Boolean {
        val service = com.example.service.JarvisAccessibilityService.instance
        return service?.performBack() ?: false
    }

    fun openNotifications(): Boolean {
        val service = com.example.service.JarvisAccessibilityService.instance
        return service?.performNotifications() ?: false
    }

    fun openAccessibilitySettings() {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("SystemController", "Failed to open accessibility settings", e)
        }
    }

    fun isAccessibilityEnabled(): Boolean {
        return com.example.service.JarvisAccessibilityService.isRunning
    }
}

