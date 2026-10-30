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

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#0F172A")

        webView = WebView(this)
        setContentView(webView)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
        }
        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = WebViewClient()
        webView.addJavascriptInterface(AndroidBridge(this), "AndroidBridge")

        webView.loadDataWithBaseURL(
            "https://focusforge.local/",
            DASHBOARD_HTML,
            "text/html",
            "UTF-8",
            null
        )
    }

    override fun onResume() {
        super.onResume()
        if (::webView.isInitialized) {
            webView.evaluateJavascript("if(window.refreshNativeStatus) window.refreshNativeStatus();", null)
        }
    }

    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    inner class AndroidBridge(private val context: Context) {
        private val prefs = context.getSharedPreferences("FocusForgePrefs", Context.MODE_PRIVATE)

        @JavascriptInterface
        fun isAccessibilityEnabled(): Boolean {
            val expected = "${context.packageName}/${FocusAccessibilityService::class.java.canonicalName}"
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return enabledServices.contains(expected)
        }

        @JavascriptInterface
        fun openAccessibilitySettings() {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
            Toast.makeText(context, "Find 'FocusForge Pro Blocker' and turn it ON", Toast.LENGTH_LONG).show()
        }

        @JavascriptInterface
        fun openDeviceAdminSettings() {
            val comp = ComponentName(context, StrictDeviceAdminReceiver::class.java)
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, comp)
                putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Prevents uninstalling FocusForge during Strict Study Sessions.")
            }
            startActivity(intent)
        }

        @JavascriptInterface
        fun updateBlockerState(focusActive: Boolean, blockShorts: Boolean, blockReels: Boolean) {
            prefs.edit()
                .putBoolean("focus_mode_active", focusActive)
                .putBoolean("block_yt_shorts", blockShorts)
                .putBoolean("block_ig_reels", blockReels)
                .apply()
        }
    }

    companion object {
        private const val DASHBOARD_HTML = """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
<title>FocusForge Pro</title>
<style>
  :root { --bg: #F4F6FB; --card: #FFFFFF; --text: #1E293B; --sub: #64748B; --indigo: #4F46E5; --pink: #EC4899; --mint: #10B981; --amber: #F59E0B; }
  * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; -webkit-tap-highlight-color: transparent; }
  body { background: var(--bg); color: var(--text); padding-bottom: 80px; }
  .header { background: #0F172A; color: #fff; padding: 16px; display: flex; justify-content: space-between; align-items: center; position: sticky; top: 0; z-index: 50; }
  .header h1 { font-size: 18px; font-weight: 800; letter-spacing: -0.5px; }
  .badge { font-size: 11px; padding: 5px 10px; border-radius: 20px; font-weight: 700; cursor: pointer; }
  .badge.on { background: #065F46; color: #A7F3D0; }
  .badge.off { background: #991B1B; color: #FECACA; }
  .container { padding: 14px; max-width: 600px; margin: 0 auto; }
  .card { background: var(--card); border-radius: 18px; padding: 16px; margin-bottom: 14px; box-shadow: 0 2px 10px rgba(15,23,42,0.05); }
  .card-title { font-size: 12px; font-weight: 800; text-transform: uppercase; color: var(--sub); letter-spacing: 0.6px; margin-bottom: 10px; }
  .rings-row { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
  .ring-box { text-align: center; padding: 12px; background: #F8FAFC; border-radius: 14px; }
  .big-pct { font-size: 26px; font-weight: 900; color: var(--indigo); margin: 4px 0; }
  .week-rings { display: grid; grid-template-columns: repeat(4, 1fr); gap: 8px; margin-top: 10px; }
  .w-pill { background: #F8FAFC; border-radius: 12px; padding: 8px 4px; text-align: center; font-size: 11px; font-weight: 700; }
  .bars-wrap { display: flex; align-items: flex-end; gap: 4px; height: 70px; margin-top: 12px; padding-top: 10px; border-bottom: 1px solid #E2E8F0; }
  .bar-col { flex: 1; background: #E0E7FF; border-radius: 4px 4px 0 0; position: relative; transition: height 0.3s; }
  .bar-col.active { background: var(--indigo); }
  .habit-row { display: flex; align-items: center; justify-content: space-between; padding: 10px 0; border-bottom: 1px solid #F1F5F9; }
  .habit-name { font-size: 13px; font-weight: 600; flex: 1; padding-right: 8px; }
  .days-strip { display: flex; gap: 5px; }
  .day-cb { width: 24px; height: 24px; border-radius: 6px; border: 2px solid #CBD5E1; display: flex; align-items: center; justify-content: center; font-size: 12px; font-weight: 800; color: #fff; cursor: pointer; }
  .day-cb.checked { background: var(--indigo); border-color: var(--indigo); }
  .btn { border: none; border-radius: 12px; padding: 11px 16px; font-size: 13px; font-weight: 700; cursor: pointer; color: #fff; background: var(--indigo); width: 100%; margin-top: 8px; }
  .btn.danger { background: #DC2626; }
  .btn.mint { background: var(--mint); }
  .timer-disp { font-size: 46px; font-weight: 900; text-align: center; margin: 12px 0; font-variant-numeric: tabular-nums; color: #0F172A; }
  .switch-row { display: flex; justify-content: space-between; align-items: center; padding: 10px 0; border-bottom: 1px solid #F1F5F9; font-size: 13px; font-weight: 600; }
  .bottom-nav { position: fixed; bottom: 0; left: 0; right: 0; background: #fff; border-top: 1px solid #E2E8F0; display: grid; grid-template-columns: repeat(4, 1fr); padding: 8px 0; z-index: 100; }
  .nav-item { text-align: center; font-size: 11px; font-weight: 700; color: var(--sub); padding: 6px 0; cursor: pointer; }
  .nav-item.active { color: var(--indigo); }
  .tab-pane { display: none; }
  .tab-pane.active { display: block; }
  input, select { width: 100%; padding: 10px; border-radius: 10px; border: 1px solid #CBD5E1; margin-bottom: 8px; font-size: 13px; }
  .water-grid { display: grid; grid-template-columns: repeat(8, 1fr); gap: 6px; margin-top: 8px; }
  .glass { padding: 10px 0; text-align: center; background: #E0F2FE; border-radius: 8px; font-size: 12px; font-weight: 800; color: #0369A1; cursor: pointer; }
  .glass.filled { background: #0284C7; color: #fff; }
</style>
</head>
<body>
  <div class="header">
    <div>
      <h1>FocusForge Pro</h1>
      <div style="font-size:11px;color:#94A3B8;">Streako Dashboard + Regain Shield</div>
    </div>
    <div id="serviceBadge" class="badge off" onclick="openAcc()">Shield: Tap to Enable</div>
  </div>

  <div class="container">
    <!-- TAB 1: STREAKO DASHBOARD -->
    <div id="tab-dash" class="tab-pane active">
      <div class="card" style="background:linear-gradient(135deg,#EEF2FF,#FCE7F3);">
        <div style="font-size:18px;font-weight:900;color:#065F46;">trust the process</div>
        <div style="font-size:12px;color:#334155;margin-top:2px;">Every checkmark builds momentum.</div>
        <div class="rings-row" style="margin-top:12px;">
          <div class="ring-box">
            <div class="card-title" style="margin:0;">TODAY PROGRESS</div>
            <div id="todayPct" class="big-pct">0%</div>
            <div id="todayCount" style="font-size:11px;color:var(--sub);">0 / 0 Completed</div>
          </div>
          <div class="ring-box">
            <div class="card-title" style="margin:0;">WEEKLY MOMENTUM</div>
            <div id="weekPct" class="big-pct" style="color:var(--pink);">0%</div>
            <div id="weekCount" style="font-size:11px;color:var(--sub);">0 / 0 Checks</div>
          </div>
        </div>
        <div id="barsContainer" class="bars-wrap"></div>
      </div>

      <div class="card">
        <div class="card-title">7-Day Streako Habit Grid (M T W T F S S)</div>
        <div id="habitList"></div>
        <div style="display:flex;gap:8px;margin-top:12px;">
          <input id="newHabitInput" placeholder="Add new habit (e.g. CA Law Revision)..." style="margin:0;flex:1;">
          <button class="btn" style="width:auto;margin:0;" onclick="addHabit()">+ Add</button>
        </div>
      </div>

      <div class="card">
        <div class="card-title">Top Habits Leaderboard</div>
        <div id="leaderboard"></div>
      </div>
    </div>

    <!-- TAB 2: REGAIN PRO BLOCKER -->
    <div id="tab-focus" class="tab-pane">
      <div class="card">
        <div class="card-title">Regain Pro Focus Timer & Strict Lock</div>
        <div id="timerDisplay" class="timer-disp">45:00</div>
        <div style="display:flex;gap:8px;margin-bottom:8px;">
          <button class="btn" style="background:#334155;" onclick="setMins(25)">25m Pomodoro</button>
          <button class="btn" style="background:#334155;" onclick="setMins(45)">45m Deep Study</button>
          <button class="btn" style="background:#334155;" onclick="setMins(60)">60m Exam Block</button>
        </div>
        <button id="startTimerBtn" class="btn mint" onclick="toggleTimer()">Start Strict Study Session</button>
      </div>

      <div class="card">
        <div class="card-title">Surgical Social Media Blocker</div>
        <div class="switch-row">
          <span>Block YouTube Shorts (Allow Lectures)</span>
          <input type="checkbox" id="chkShorts" checked style="width:20px;height:20px;margin:0;" onchange="syncBlocker()">
        </div>
        <div class="switch-row">
          <span>Block Instagram Reels (Allow DMs)</span>
          <input type="checkbox" id="chkReels" checked style="width:20px;height:20px;margin:0;" onchange="syncBlocker()">
        </div>
        <button class="btn" onclick="openAcc()">1. Enable Android Accessibility Shield</button>
        <button class="btn" style="background:#0F172A;" onclick="openAdmin()">2. Enable Strict Mode Anti-Uninstall</button>
      </div>

      <div class="card">
        <div class="card-title">Offline Study Soundscape (Brown Noise)</div>
        <button id="noiseBtn" class="btn" style="background:#0284C7;" onclick="toggleNoise()">Play Deep Focus Brown Noise</button>
      </div>
    </div>

    <!-- TAB 3: STUDY HUB -->
    <div id="tab-study" class="tab-pane">
      <div class="card">
        <div class="card-title">Subject Revision & Spaced Repetition</div>
        <div id="studyList"></div>
        <div style="display:flex;gap:8px;margin-top:10px;">
          <input id="newSubInput" placeholder="Add Subject / Chapter (e.g. Contract Act Ch 2)..." style="margin:0;flex:1;">
          <button class="btn" style="width:auto;margin:0;" onclick="addSubject()">+ Add</button>
        </div>
      </div>
    </div>

    <!-- TAB 4: GYM & HYDRATION -->
    <div id="tab-gym" class="tab-pane">
      <div class="card">
        <div class="card-title">Daily 8-Glass Hydration Tracker</div>
        <div id="waterGrid" class="water-grid"></div>
      </div>
      <div class="card">
        <div class="card-title">Gym Workout Split Logger (Sets x Reps x Kg)</div>
        <input id="gymEx" placeholder="Exercise (e.g. Bench Press / Squats)">
        <div style="display:flex;gap:8px;">
          <input id="gymSets" placeholder="Sets (e.g. 4)">
          <input id="gymReps" placeholder="Reps (e.g. 10)">
          <input id="gymWt" placeholder="Weight (kg)">
        </div>
        <button class="btn mint" onclick="addGymLog()">Log Workout Set</button>
        <div id="gymLogs" style="margin-top:12px;"></div>
      </div>
    </div>
  </div>

  <div class="bottom-nav">
    <div class="nav-item active" onclick="switchTab('dash', this)">Dashboard</div>
    <div class="nav-item" onclick="switchTab('focus', this)">Regain Lock</div>
    <div class="nav-item" onclick="switchTab('study', this)">Study Hub</div>
    <div class="nav-item" onclick="switchTab('gym', this)">Gym & Water</div>
  </div>

<script>
  const defaultHabits = [
    { name: "Review class notes & lectures", days: [true,true,false,false,false,false,false] },
    { name: "CA / B.Com Law & Accounts Practice", days: [true,true,false,false,false,false,false] },
    { name: "Complete assignments", days: [true,false,false,false,false,false,false] },
    { name: "Exercise / Gym for 45 mins", days: [true,true,false,false,false,false,false] },
    { name: "Drink 8 glasses of water", days: [true,true,false,false,false,false,false] },
    { name: "Zero Doomscrolling / Reels", days: [true,true,false,false,false,false,false] }
  ];
  let state = JSON.parse(localStorage.getItem('ff_state_v1') || 'null') || {
    habits: defaultHabits,
    subjects: [
      { name: "Indian Contract Act, 1872", r1: true, r2: true, r3: false },
      { name: "Indian Regulatory Framework", r1: true, r2: false, r3: false },
      { name: "Accountancy & Financial Statements", r1: true, r2: false, r3: false },
      { name: "Business Economics & Statistics", r1: false, r2: false, r3: false }
    ],
    water: 4,
    gym: [{ ex: "Pushups / Bench Press", sets: "4", reps: "12", wt: "Body/40kg" }]
  };

  function save() { localStorage.setItem('ff_state_v1', JSON.stringify(state)); render(); }

  function render() {
    const hList = document.getElementById('habitList');
    hList.innerHTML = '';
    let totalChecks = 0, todayChecks = 0;
    const dayTotals = [0,0,0,0,0,0,0];
    const dayLabels = ['M','T','W','T','F','S','S'];

    state.habits.forEach((h, hi) => {
      const row = document.createElement('div');
      row.className = 'habit-row';
      let strip = '<div class="days-strip">';
      h.days.forEach((checked, di) => {
        if (checked) { totalChecks++; dayTotals[di]++; if (di === 1) todayChecks++; }
        strip += `<div class="day-cb ${checked ? 'checked' : ''}" onclick="toggleDay(${hi},${di})">${dayLabels[di]}</div>`;
      });
      strip += '</div>';
      row.innerHTML = `<div class="habit-name">${h.name}</div>${strip}`;
      hList.appendChild(row);
    });

    const totalPossible = state.habits.length * 7;
    const todayPct = state.habits.length ? Math.round((todayChecks / state.habits.length) * 100) : 0;
    const weekPct = totalPossible ? Math.round((totalChecks / totalPossible) * 100) : 0;
    document.getElementById('todayPct').innerText = todayPct + '%';
    document.getElementById('todayCount').innerText = `${todayChecks} / ${state.habits.length} Completed`;
    document.getElementById('weekPct').innerText = weekPct + '%';
    document.getElementById('weekCount').innerText = `${totalChecks} / ${totalPossible} Checks`;

    const bars = document.getElementById('barsContainer');
    bars.innerHTML = '';
    dayTotals.forEach((cnt, idx) => {
      const pct = state.habits.length ? Math.max(10, Math.round((cnt / state.habits.length) * 100)) : 10;
      bars.innerHTML += `<div class="bar-col ${idx===1?'active':''}" style="height:${pct}%"></div>`;
    });

    const lb = document.getElementById('leaderboard');
    lb.innerHTML = '';
    state.habits.slice().sort((a,b) => b.days.filter(Boolean).length - a.days.filter(Boolean).length).forEach((h, i) => {
      const p = Math.round((h.days.filter(Boolean).length / 7) * 100);
      lb.innerHTML += `<div class="habit-row"><span>#${i+1} ${h.name}</span><strong style="color:var(--indigo)">${p}%</strong></div>`;
    });

    const sList = document.getElementById('studyList');
    sList.innerHTML = '';
    state.subjects.forEach((s, si) => {
      sList.innerHTML += `<div class="habit-row"><div class="habit-name">${s.name}</div><div class="days-strip">
        <div class="day-cb ${s.r1?'checked':''}" style="width:36px" onclick="toggleSub(${si},'r1')">R1</div>
        <div class="day-cb ${s.r2?'checked':''}" style="width:36px" onclick="toggleSub(${si},'r2')">R2</div>
        <div class="day-cb ${s.r3?'checked':''}" style="width:36px" onclick="toggleSub(${si},'r3')">R3</div>
      </div></div>`;
    });

    const wg = document.getElementById('waterGrid');
    wg.innerHTML = '';
    for (let i = 1; i <= 8; i++) {
      wg.innerHTML += `<div class="glass ${i <= state.water ? 'filled' : ''}" onclick="setWater(${i})">${i}</div>`;
    }

    const gl = document.getElementById('gymLogs');
    gl.innerHTML = '';
    state.gym.forEach(g => {
      gl.innerHTML += `<div class="habit-row"><span><strong>${g.ex}</strong></span><span>${g.sets}x${g.reps} (${g.wt})</span></div>`;
    });
  }

  function toggleDay(hi, di) { state.habits[hi].days[di] = !state.habits[hi].days[di]; save(); }
  function addHabit() {
    const inp = document.getElementById('newHabitInput');
    if (!inp.value.trim()) return;
    state.habits.push({ name: inp.value.trim(), days: [false,false,false,false,false,false,false] });
    inp.value = ''; save();
  }
  function toggleSub(si, key) { state.subjects[si][key] = !state.subjects[si][key]; save(); }
  function addSubject() {
    const inp = document.getElementById('newSubInput');
    if (!inp.value.trim()) return;
    state.subjects.push({ name: inp.value.trim(), r1: false, r2: false, r3: false });
    inp.value = ''; save();
  }
  function setWater(n) { state.water = n; save(); }
  function addGymLog() {
    const ex = document.getElementById('gymEx').value.trim();
    if (!ex) return;
    state.gym.unshift({
      ex,
      sets: document.getElementById('gymSets').value || '3',
      reps: document.getElementById('gymReps').value || '10',
      wt: document.getElementById('gymWt').value || '-'
    });
    document.getElementById('gymEx').value = ''; save();
  }

  function switchTab(id, el) {
    document.querySelectorAll('.tab-pane').forEach(p => p.classList.remove('active'));
    document.getElementById('tab-' + id).classList.add('active');
    document.querySelectorAll('.nav-item').forEach(n => n.classList.remove('active'));
    el.classList.add('active');
  }

  let timerSecs = 45 * 60, timerRunning = false, timerInt = null;
  function setMins(m) { if (timerRunning) return; timerSecs = m * 60; updateTimerUI(); }
  function updateTimerUI() {
    const m = String(Math.floor(timerSecs / 60)).padStart(2, '0');
    const s = String(timerSecs % 60).padStart(2, '0');
    document.getElementById('timerDisplay').innerText = `${m}:${s}`;
  }
  function toggleTimer() {
    timerRunning = !timerRunning;
    const btn = document.getElementById('startTimerBtn');
    if (timerRunning) {
      btn.innerText = 'End Focus Session'; btn.className = 'btn danger';
      timerInt = setInterval(() => { if (timerSecs > 0) { timerSecs--; updateTimerUI(); } else { toggleTimer(); } }, 1000);
    } else {
      btn.innerText = 'Start Strict Study Session'; btn.className = 'btn mint';
      clearInterval(timerInt);
    }
    syncBlocker();
  }

  function syncBlocker() {
    const s = document.getElementById('chkShorts').checked;
    const r = document.getElementById('chkReels').checked;
    if (window.AndroidBridge) window.AndroidBridge.updateBlockerState(timerRunning, s, r);
  }
  function openAcc() { if (window.AndroidBridge) window.AndroidBridge.openAccessibilitySettings(); }
  function openAdmin() { if (window.AndroidBridge) window.AndroidBridge.openDeviceAdminSettings(); }
  window.refreshNativeStatus = function() {
    if (window.AndroidBridge && window.AndroidBridge.isAccessibilityEnabled()) {
      const b = document.getElementById('serviceBadge');
      b.className = 'badge on'; b.innerText = 'Shield: ACTIVE';
    }
  };

  let audioCtx = null, noiseNode = null;
  function toggleNoise() {
    const btn = document.getElementById('noiseBtn');
    if (!audioCtx) {
      audioCtx = new (window.AudioContext || window.webkitAudioContext)();
      const bufSize = 2 * audioCtx.sampleRate, buf = audioCtx.createBuffer(1, bufSize, audioCtx.sampleRate);
      const out = buf.getChannelData(0);
      let last = 0.0;
      for (let i = 0; i < bufSize; i++) {
        const w = Math.random() * 2 - 1;
        out[i] = (last + (0.02 * w)) / 1.02; last = out[i]; out[i] *= 0.15;
      }
      noiseNode = audioCtx.createBufferSource();
      noiseNode.buffer = buf; noiseNode.loop = true;
      noiseNode.connect(audioCtx.destination); noiseNode.start(0);
      btn.innerText = 'Stop Brown Noise'; btn.className = 'btn danger';
    } else {
      audioCtx.close(); audioCtx = null;
      btn.innerText = 'Play Deep Focus Brown Noise'; btn.className = 'btn';
    }
  }

  render();
  window.refreshNativeStatus();
</script>
</body>
</html>
        """
    }
}
