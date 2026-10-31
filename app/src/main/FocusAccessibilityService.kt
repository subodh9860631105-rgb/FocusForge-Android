package com.focusforge.pro

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

class FocusAccessibilityService : AccessibilityService() {

    private var lastBlockTime = 0L
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        val info = serviceInfo ?: AccessibilityServiceInfo()
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                AccessibilityEvent.TYPE_VIEW_SCROLLED
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        info.notificationTimeout = 100
        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return

        if (pkg == "com.focusforge.pro" || pkg == "com.android.systemui" || pkg.contains("launcher")) {
            return
        }

        val prefs = getSharedPreferences("FocusForgePrefs", Context.MODE_PRIVATE)
        val isBlockerActive = prefs.getBoolean("blocker_enabled", false)
        if (!isBlockerActive) return

        val blockShortsOnly = prefs.getBoolean("block_shorts", true)
        val blockFullYoutube = prefs.getBoolean("block_full_yt", false)
        val blockInsta = prefs.getBoolean("block_insta", true)
        val blockSocialGames = prefs.getBoolean("block_social_games", true)

        // 1. Full YouTube Lock (if enabled)
        if (pkg.contains("youtube") && blockFullYoutube) {
            triggerBlock(prefs, "YouTube is locked during Strict Study Mode!", goHome = true)
            return
        }

        // 2. Instagram Lock
        if (pkg == "com.instagram.android" && blockInsta) {
            triggerBlock(prefs, "Instagram & Reels blocked! Stay focused.", goHome = true)
            return
        }

        // 3. Social Media & Distracting Apps Lock
        val socialPackages = setOf(
            "com.facebook.katana",
            "com.snapchat.android",
            "com.twitter.android",
            "com.zhiliaoapp.musically",
            "com.ss.android.ugc.trill"
        )
        if (blockSocialGames && socialPackages.contains(pkg)) {
            triggerBlock(prefs, "Distracting social app blocked!", goHome = true)
            return
        }

        // 4. YouTube Shorts Blocker (Allows study videos, blocks Shorts player)
        if (pkg.contains("youtube") && blockShortsOnly) {
            val rootNode = rootInActiveWindow ?: event.source ?: return
            if (isYoutubeShortsActive(rootNode)) {
                triggerBlock(prefs, "YouTube Shorts Blocked! Back to studying.", goHome = false)
            }
        }
    }

    private fun isYoutubeShortsActive(rootNode: AccessibilityNodeInfo): Boolean {
        val knownShortsIds = listOf(
            "com.google.android.youtube:id/reel_recycler",
            "com.google.android.youtube:id/reel_player_page_container",
            "com.google.android.youtube:id/reel_watch_player",
            "com.google.android.youtube:id/shorts_player_container"
        )
        for (id in knownShortsIds) {
            val matches = rootNode.findAccessibilityNodeInfosByViewId(id)
            if (!matches.isNullOrEmpty()) return true
        }
        return containsShortsNodeRecursive(rootNode, 0)
    }

    private fun containsShortsNodeRecursive(node: AccessibilityNodeInfo?, depth: Int): Boolean {
        if (node == null || depth > 12) return false
        val viewId = node.viewIdResourceName ?: ""
        if (viewId.contains("reel_recycler") ||
            viewId.contains("reel_player") ||
            viewId.contains("shorts_player") ||
            viewId.contains("reel_watch")
        ) {
            return true
        }
        for (i in 0 until node.childCount) {
            if (containsShortsNodeRecursive(node.getChild(i), depth + 1)) {
                return true
            }
        }
        return false
    }

    private fun triggerBlock(prefs: SharedPreferences, message: String, goHome: Boolean) {
        val now = System.currentTimeMillis()
        if (now - lastBlockTime < 1500L) return
        lastBlockTime = now

        val blockedCount = prefs.getInt("distractions_blocked", 0) + 1
        val currentXp = prefs.getInt("hunter_xp", 0) + 15
        prefs.edit()
            .putInt("distractions_blocked", blockedCount)
            .putInt("hunter_xp", currentXp)
            .apply()

        if (goHome) {
            performGlobalAction(GLOBAL_ACTION_HOME)
        } else {
            performGlobalAction(GLOBAL_ACTION_BACK)
        }

        mainHandler.post {
            Toast.makeText(applicationContext, "🛡️ $message (+15 XP)", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onInterrupt() {}
}
