package com.example.data.model

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sender: MessageSender,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val actionTag: String? = null,
    val isSystemLog: Boolean = false
)

enum class MessageSender {
    USER,
    JARVIS,
    SYSTEM
}

data class DeviceTelemetry(
    val batteryPercent: Int = 100,
    val isCharging: Boolean = false,
    val freeRamMb: Long = 0,
    val totalRamMb: Long = 0,
    val freeStorageGb: Double = 0.0,
    val totalStorageGb: Double = 0.0,
    val networkStatus: String = "WiFi Online",
    val volumePercent: Int = 70,
    val isFlashlightOn: Boolean = false
) {
    val ramUsedPercent: Int
        get() = if (totalRamMb > 0) (((totalRamMb - freeRamMb) * 100) / totalRamMb).toInt() else 0
}

data class JarvisPermissionStatus(
    val hasRecordAudio: Boolean = false,
    val hasPostNotifications: Boolean = false,
    val hasCamera: Boolean = false,
    val isBatteryOptimizationIgnored: Boolean = false,
    val hasAccessibility: Boolean = false
) {
    val isAllGranted: Boolean
        get() = hasRecordAudio && hasPostNotifications && hasCamera && isBatteryOptimizationIgnored && hasAccessibility
}

data class QuickAction(
    val id: String,
    val title: String,
    val subtitle: String,
    val iconName: String,
    val command: String
)
