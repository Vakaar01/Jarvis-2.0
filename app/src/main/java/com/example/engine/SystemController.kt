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

        // Known common package identifiers
        val commonApps = mapOf(
            "youtube" to "com.google.android.youtube",
            "whatsapp" to "com.whatsapp",
            "chrome" to "com.android.chrome",
            "maps" to "com.google.android.apps.maps",
            "camera" to "camera",
            "settings" to "com.android.settings",
            "calculator" to "calculator",
            "clock" to "deskclock",
            "spotify" to "com.spotify.music",
            "telegram" to "org.telegram.messenger",
            "gmail" to "com.google.android.gm",
            "photos" to "com.google.android.apps.photos"
        )

        for ((key, pkgOrTag) in commonApps) {
            if (query.contains(key)) {
                val launchIntent = pm.getLaunchIntentForPackage(pkgOrTag)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    return true
                }
            }
        }

        // Search installed apps by label
        try {
            val installedApps = pm.getInstalledApplications(0)
            for (app in installedApps) {
                val label = pm.getApplicationLabel(app).toString().lowercase()
                if (label.contains(query) || query.contains(label)) {
                    val launchIntent = pm.getLaunchIntentForPackage(app.packageName)
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(launchIntent)
                        return true
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SystemController", "Error querying installed apps", e)
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
            val intent = if (!query.isNullOrBlank()) {
                Intent(Intent.ACTION_SEARCH).apply {
                    setPackage("com.google.android.youtube")
                    putExtra("query", query)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            } else {
                context.packageManager.getLaunchIntentForPackage("com.google.android.youtube")?.apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            }
            if (intent != null) {
                context.startActivity(intent)
                true
            } else {
                // Fallback to browser YouTube
                val ytUrl = if (!query.isNullOrBlank()) {
                    "https://www.youtube.com/results?search_query=${Uri.encode(query)}"
                } else {
                    "https://www.youtube.com"
                }
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(ytUrl)).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(browserIntent)
                true
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

