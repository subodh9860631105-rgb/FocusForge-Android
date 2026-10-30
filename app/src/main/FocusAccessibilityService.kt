package com.focusforge.pro

import android.accessibilityservice.AccessibilityService
import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast

class FocusAccessibilityService : AccessibilityService() {

    private var lastToastTime = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName || pkg.contains("launcher") || pkg.contains("systemui")) return

        val prefs = getSharedPreferences("FocusForgePrefs", Context.MODE_PRIVATE)
        val focusActive = prefs.getBoolean("focus_mode_active", false)
        val blockShorts = prefs.getBoolean("block_yt_shorts", true)
        val blockReels = prefs.getBoolean("block_ig_reels", true)
        val blockedCsv = prefs.getString(
            "blocked_packages",
            "com.instagram.android,com.snapchat.android,com.facebook.katana,com.zhiliaoapp.musically,com.dts.freefireth,com.dts.freefiremax"
        ) ?: ""

        // 1. Full App Blocking during Active Focus Session
        if (focusActive) {
            val blockedSet = blockedCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            if (blockedSet.contains(pkg)) {
                performGlobalAction(GLOBAL_ACTION_HOME)
                notifyBlocked("Focus Mode Active! Distracting app blocked.")
                return
            }
        }

        val rootNode = rootInActiveWindow ?: return

        // 2. Surgical YouTube Shorts Interception (Allows study videos, blocks Shorts player)
        if (blockShorts && pkg == "com.google.android.youtube") {
            val shortsIds = listOf(
                "com.google.android.youtube:id/reel_recycler",
                "com.google.android.youtube:id/reel_player_page_container",
                "com.google.android.youtube:id/shorts_container"
            )
            for (id in shortsIds) {
                if (rootNode.findAccessibilityNodeInfosByViewId(id).isNotEmpty()) {
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    notifyBlocked("YouTube Shorts blocked by FocusForge!")
                    return
                }
            }
        }

        // 3. Surgical Instagram Reels Interception (Allows DMs, blocks Reels tab)
        if (blockReels && pkg == "com.instagram.android") {
            val reelIds = listOf(
                "com.instagram.android:id/clips_video_container",
                "com.instagram.android:id/reel_viewer_root"
            )
            for (id in reelIds) {
                if (rootNode.findAccessibilityNodeInfosByViewId(id).isNotEmpty()) {
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    notifyBlocked("Instagram Reels blocked by FocusForge!")
                    return
                }
            }
        }
    }

    private fun notifyBlocked(message: String) {
        val now = System.currentTimeMillis()
        if (now - lastToastTime > 2500) {
            lastToastTime = now
            Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onInterrupt() {}
}

class StrictDeviceAdminReceiver : DeviceAdminReceiver()
