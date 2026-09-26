package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class JarvisAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i("JarvisAccessibility", "Jarvis Accessibility Service Connected and Active")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Listening to active window events if needed
    }

    override fun onInterrupt() {
        Log.w("JarvisAccessibility", "Jarvis Accessibility Service Interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
    }

    fun scrollUp(): Boolean {
        return performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS) || scrollForward()
    }

    fun scrollDown(): Boolean {
        val root = rootInActiveWindow ?: return performSwipe(isUp = true)
        return scrollNode(root, isForward = true) || performSwipe(isUp = true)
    }

    fun performScrollDownAction(): Boolean {
        // Swipe up to scroll content down
        return performSwipe(isUp = true)
    }

    fun performScrollUpAction(): Boolean {
        // Swipe down to scroll content up
        return performSwipe(isUp = false)
    }

    fun performHome(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)
    fun performBack(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)
    fun performRecents(): Boolean = performGlobalAction(GLOBAL_ACTION_RECENTS)
    fun performNotifications(): Boolean = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
    fun performQuickSettings(): Boolean = performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
    fun performLockScreen(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        } else false
    }

    private fun scrollForward(): Boolean {
        val root = rootInActiveWindow ?: return false
        return scrollNode(root, true)
    }

    private fun scrollNode(node: AccessibilityNodeInfo, isForward: Boolean): Boolean {
        val action = if (isForward) {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        } else {
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        }
        if (node.isScrollable && node.performAction(action)) {
            return true
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (scrollNode(child, isForward)) {
                return true
            }
        }
        return false
    }

    private fun performSwipe(isUp: Boolean): Boolean {
        val metrics: DisplayMetrics = resources.displayMetrics
        val width = metrics.widthPixels.toFloat()
        val height = metrics.heightPixels.toFloat()

        val startX = width / 2f
        val startY = if (isUp) height * 0.75f else height * 0.25f
        val endX = startX
        val endY = if (isUp) height * 0.25f else height * 0.75f

        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 300))
            .build()

        return dispatchGesture(gesture, null, null)
    }

    companion object {
        var instance: JarvisAccessibilityService? = null
            private set

        val isRunning: Boolean
            get() = instance != null
    }
}
