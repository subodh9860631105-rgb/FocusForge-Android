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

    private var lastActionTime = 0L
    private var lastRewardTime = 0L
    private var rapidSpamWindowStart = 0L
    private var rapidSpamCount = 0
    private val mainHandler = Handler(Looper.getMainLooper())

    private val verifyRunnable = Runnable {
        recheckActiveWindow()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val info = serviceInfo ?: AccessibilityServiceInfo()
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                AccessibilityEvent.TYPE_VIEW_SCROLLED or
                AccessibilityEvent.TYPE_VIEW_CLICKED or
                AccessibilityEvent.TYPE_VIEW_SELECTED
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        info.notificationTimeout = 15
        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return

        if (pkg == "com.focusforge.pro" || pkg == "com.android.systemui" || pkg.contains("launcher")) {
            return
        }

        inspectAndEnforce(pkg, rootInActiveWindow ?: event.source)
    }

    private fun recheckActiveWindow() {
        val rootNode = rootInActiveWindow ?: return
        val pkg = rootNode.packageName?.toString() ?: return
        if (pkg == "com.focusforge.pro" || pkg == "com.android.systemui" || pkg.contains("launcher")) {
            return
        }
        inspectAndEnforce(pkg, rootNode)
    }

    private fun inspectAndEnforce(pkg: String, rootNode: AccessibilityNodeInfo?) {
        val prefs = getSharedPreferences("FocusForgePrefs", Context.MODE_PRIVATE)
        val isBlockerActive = prefs.getBoolean("blocker_enabled", false)
        if (!isBlockerActive) return

        val blockShortsOnly = prefs.getBoolean("block_shorts", true)
        val blockFullYoutube = prefs.getBoolean("block_full_yt", false)
        val blockInsta = prefs.getBoolean("block_insta", true)
        val blockSocialGames = prefs.getBoolean("block_social_games", true)

        // 1. Nuclear Mode: Full YouTube Lock
        if (pkg.contains("youtube") && blockFullYoutube) {
            enforceBlock(prefs, "YouTube is locked during Strict Study Mode!", forceHome = true)
            return
        }

        // 2. Instagram & Reels Lock
        if ((pkg == "com.instagram.android" || pkg == "com.instagram.lite") && blockInsta) {
            enforceBlock(prefs, "Instagram & Reels blocked! Stay focused.", forceHome = true)
            return
        }

        // 3. Distracting Social & Short-Video Apps Lock
        val socialPackages = setOf(
            "com.facebook.katana",
            "com.facebook.lite",
            "com.snapchat.android",
            "com.twitter.android",
            "com.zhiliaoapp.musically",
            "com.ss.android.ugc.trill",
            "com.reddit.frontpage"
        )
        if (blockSocialGames && socialPackages.contains(pkg)) {
            enforceBlock(prefs, "Distracting social app blocked!", forceHome = true)
            return
        }

        // 4. Strict YouTube Shorts Interceptor (Allows Study Lectures, Blocks Shorts)
        if (pkg.contains("youtube") && blockShortsOnly && rootNode != null) {
            if (isYoutubeShortsActive(rootNode)) {
                enforceBlock(prefs, "YouTube Shorts Blocked! Back to studying.", forceHome = false)
            }
        }
    }

    private fun isYoutubeShortsActive(rootNode: AccessibilityNodeInfo): Boolean {
        val knownShortsIds = listOf(
            "com.google.android.youtube:id/reel_recycler",
            "com.google.android.youtube:id/reel_player_page_container",
            "com.google.android.youtube:id/reel_watch_player",
            "com.google.android.youtube:id/shorts_player_container",
            "com.google.android.youtube:id/reel_watch_fragment_root",
            "com.google.android.youtube:id/reel_scrubber",
            "com.google.android.youtube:id/reel_rhs_container",
            "com.google.android.youtube:id/reel_multi_format_link"
        )
        for (id in knownShortsIds) {
            val matches = rootNode.findAccessibilityNodeInfosByViewId(id)
            if (!matches.isNullOrEmpty()) return true
        }
        return containsShortsNodeRecursive(rootNode, 0)
    }

    private fun containsShortsNodeRecursive(node: AccessibilityNodeInfo?, depth: Int): Boolean {
        if (node == null || depth > 14) return false

        val viewId = node.viewIdResourceName ?: ""
        if (viewId.contains("reel_recycler") ||
            viewId.contains("reel_player") ||
            viewId.contains("shorts_player") ||
            viewId.contains("reel_watch") ||
            viewId.contains("reel_scrubber") ||
            viewId.contains("shorts_container")
        ) {
            return true
        }

        // Detect if the bottom navigation "Shorts" tab is actively selected
        val desc = node.contentDescription?.toString() ?: ""
        if (node.isSelected && (desc.equals("Shorts", ignoreCase = true) || desc.startsWith("Shorts", ignoreCase = true))) {
            return true
        }

        for (i in 0 until node.childCount) {
            if (containsShortsNodeRecursive(node.getChild(i), depth + 1)) {
                return true
            }
        }
        return false
    }

    private fun enforceBlock(prefs: SharedPreferences, message: String, forceHome: Boolean) {
        val now = System.currentTimeMillis()

        // 60ms micro-debounce so rapid spam clicks are blocked immediately
        if (now - lastActionTime < 60L) return
        lastActionTime = now

        // Track rapid spam-clicking within a 2.5s window
        if (now - rapidSpamWindowStart < 2500L) {
            rapidSpamCount++
        } else {
            rapidSpamWindowStart = now
            rapidSpamCount = 1
        }

        val escalateToHome = forceHome || rapidSpamCount >= 3

        if (escalateToHome) {
            performGlobalAction(GLOBAL_ACTION_HOME)
        } else {
            performGlobalAction(GLOBAL_ACTION_BACK)
        }

        // Schedule follow-up checks at 150ms and 350ms to catch mid-animation Shorts
        mainHandler.removeCallbacks(verifyRunnable)
        mainHandler.postDelayed(verifyRunnable, 150L)
        mainHandler.postDelayed(verifyRunnable, 350L)

        // Throttle XP and Toast notifications to once every 3 seconds
        if (now - lastRewardTime > 3000L) {
            lastRewardTime = now
            val blockedCount = prefs.getInt("distractions_blocked", 0) + 1
            val currentXp = prefs.getInt("hunter_xp", 0) + 15
            prefs.edit()
                .putInt("distractions_blocked", blockedCount)
                .putInt("hunter_xp", currentXp)
                .apply()

            val displayMsg = if (rapidSpamCount >= 3 && !forceHome) {
                "Spam-click detected! Exiting to Home Screen."
            } else {
                "$message (+15 XP)"
            }

            mainHandler.post {
                Toast.makeText(applicationContext, "🛡️ $displayMsg", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onInterrupt() {
        mainHandler.removeCallbacks(verifyRunnable)
    }
}
