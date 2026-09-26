package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class JarvisBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = context.getSharedPreferences("jarvis_prefs", Context.MODE_PRIVATE)
            val alwaysOn = prefs.getBoolean("always_on_service", true)
            if (alwaysOn) {
                JarvisService.startService(context)
            }
        }
    }
}
