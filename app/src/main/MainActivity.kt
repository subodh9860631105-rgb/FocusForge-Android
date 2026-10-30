package com.focusforge.pro

import android.annotation.SuppressLint
import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast

class MainActivity : Activity() {

    private var webView: WebView? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#0F172A")

        try {
            webView = WebView(this).apply {
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                }
                webChromeClient = WebChromeClient()
                webViewClient = WebViewClient()
                addJavascriptInterface(AndroidBridge(context), "AndroidBridge")
                loadDataWithBaseURL("https://focusforge.local/", SAFE_DASHBOARD_HTML, "text/html", "UTF-8", null)
            }
            setContentView(webView!!)
        } catch (e: Exception) {
            Toast.makeText(this, "Startup Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        webView?.evaluateJavascript("if(window.refreshNativeStatus) window.refreshNativeStatus();", null)
    }

    inner class AndroidBridge(private val context: Context) {
        @JavascriptInterface
        fun isAccessibilityEnabled(): Boolean {
            val expected = "${context.packageName}/${FocusAccessibilityService::class.java.canonicalName}"
            val enabledServices = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            return enabledServices.contains(expected)
        }

        @JavascriptInterface
        fun openAccessibilitySettings() {
            try {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
                Toast.makeText(context, "Turn ON 'FocusForge Pro Blocker'", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Open Settings -> Accessibility manually", Toast.LENGTH_LONG).show()
            }
        }

        @JavascriptInterface
        fun openDeviceAdminSettings() {
            try {
                val comp = ComponentName(context, StrictDeviceAdminReceiver::class.java)
                context.startActivity(Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, comp)
                    putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Prevents uninstalling during study sessions.")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                })
            } catch (e: Exception) {
                Toast.makeText(context, "Error opening Admin settings", Toast.LENGTH_SHORT).show()
            }
        }

        @JavascriptInterface
        fun updateBlockerState(focusActive: Boolean, blockShorts: Boolean, blockReels: Boolean) {
            context.getSharedPreferences("FocusForgePrefs", Context.MODE_PRIVATE).edit()
                .putBoolean("focus_mode_active", focusActive)
                .putBoolean("block_yt_shorts", blockShorts)
                .putBoolean("block_ig_reels", blockReels)
                .apply()
        }
    }

    companion object {
        private const val SAFE_DASHBOARD_HTML = """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>FocusForge Pro</title>
<style>
  body { background: #0F172A; color: #F8FAFC; font-family: sans-serif; padding: 20px; margin: 0; text-align: center; }
  h1 { font-size: 22px; color: #38BDF8; margin-bottom: 5px; }
  .card { background: #1E293B; border-radius: 14px; padding: 16px; margin: 12px 0; text-align: left; box-shadow: 0 4px 12px rgba(0,0,0,0.3); }
  .title { font-size: 11px; font-weight: bold; color: #94A3B8; text-transform: uppercase; margin-bottom: 8px; }
  .btn { background: #4F46E5; color: white; border: none; border-radius: 10px; padding: 12px; width: 100%; font-size: 14px; font-weight: bold; margin-top: 8px; cursor: pointer; }
  .btn.danger { background: #DC2626; }
  .btn.mint { background: #059669; }
  .badge { display: inline-block; padding: 6px 12px; border-radius: 20px; font-size: 12px; font-weight: bold; background: #7F1D1D; color: #FCA5A5; margin-top: 10px; }
  .badge.active { background: #064E3B; color: #A7F3D0; }
  .row { display: flex; justify-content: space-between; align-items: center; margin: 10px 0; font-size: 14px; }
  input[type=checkbox] { width: 20px; height: 20px; }
</style>
</head>
<body>
  <h1>FocusForge Pro</h1>
  <div style="font-size:12px;color:#94A3B8;">Streako Dashboard & Regain Shield</div>
  
  <div id="serviceBadge" class="badge" onclick="openAcc()">Shield: Tap to Enable</div>

  <div class="card">
    <div class="title">Strict Study Session</div>
    <div id="timerDisplay" style="font-size:40px;font-weight:900;text-align:center;margin:10px 0;">45:00</div>
    <button id="timerBtn" class="btn mint" onclick="toggleTimer()">Start 45m Focus Block</button>
  </div>

  <div class="card">
    <div class="title">Surgical Social Media Blocker</div>
    <div class="row">
      <span>Block YouTube Shorts</span>
      <input type="checkbox" id="chkShorts" checked onchange="sync()">
    </div>
    <div class="row">
      <span>Block Instagram Reels</span>
      <input type="checkbox" id="chkReels" checked onchange="sync()">
    </div>
    <button class="btn" onclick="openAcc()">1. Enable Accessibility Shield</button>
    <button class="btn" style="background:#334155;" onclick="openAdmin()">2. Enable Anti-Uninstall Lock</button>
  </div>

  <div class="card">
    <div class="title">Today's Habits & Study Check</div>
    <div class="row"><span>Review CA Law & Accounts</span> <input type="checkbox" checked></div>
    <div class="row"><span>Complete Assignments</span> <input type="checkbox"></div>
    <div class="row"><span>45m Gym / Workout</span> <input type="checkbox" checked></div>
    <div class="row"><span>Drink 8 Glasses Water</span> <input type="checkbox" checked></div>
  </div>

<script>
  let timerSecs = 45 * 60, running = false, timerInterval = null;

  function updateTimer() {
    let m = String(Math.floor(timerSecs / 60)).padStart(2, '0');
    let s = String(timerSecs % 60).padStart(2, '0');
    document.getElementById('timerDisplay').innerText = m + ':' + s;
  }

  function toggleTimer() {
    running = !running;
    let btn = document.getElementById('timerBtn');
    if (running) {
      btn.innerText = 'End Focus Session'; btn.className = 'btn danger';
      timerInterval = setInterval(() => {
        if(timerSecs > 0) { timerSecs--; updateTimer(); } else { toggleTimer(); }
      }, 1000);
    } else {
      btn.innerText = 'Start 45m Focus Block'; btn.className = 'btn mint';
      clearInterval(timerInterval);
    }
    sync();
  }

  function sync() {
    let s = document.getElementById('chkShorts').checked;
    let r = document.getElementById('chkReels').checked;
    if (window.AndroidBridge) window.AndroidBridge.updateBlockerState(running, s, r);
  }

  function openAcc() { if (window.AndroidBridge) window.AndroidBridge.openAccessibilitySettings(); }
  function openAdmin() { if (window.AndroidBridge) window.AndroidBridge.openDeviceAdminSettings(); }

  window.refreshNativeStatus = function() {
    if (window.AndroidBridge && window.AndroidBridge.isAccessibilityEnabled()) {
      let b = document.getElementById('serviceBadge');
      b.className = 'badge active'; b.innerText = 'Shield: ACTIVE';
    }
  };
  refreshNativeStatus();
</script>
</body>
</html>
        """
    }
}
