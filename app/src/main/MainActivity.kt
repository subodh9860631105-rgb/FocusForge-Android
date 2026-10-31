package com.focusforge.pro

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.CountDownTimer
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private var countDownTimer: CountDownTimer? = null
    private var isTimerRunning = false
    private var initialTimeMillis: Long = 25 * 60 * 1000L
    private var timeLeftInMillis: Long = initialTimeMillis
    private var selectedSubject = "Business Law"

    private lateinit var tvAccessibilityBanner: TextView
    private lateinit var tvHunterRank: TextView
    private lateinit var tvXpDetails: TextView
    private lateinit var xpProgressBar: ProgressBar
    private lateinit var tvDashboardMetrics: TextView
    private lateinit var tvSubjectBreakdown: TextView
    private lateinit var tvGoalProgress: TextView
    private lateinit var goalProgressBar: ProgressBar

    private lateinit var tvTimerDisplay: TextView
    private lateinit var tvTimerStatus: TextView
    private lateinit var btnStartPause: Button
    private lateinit var btnBlockerToggle: Button
    private lateinit var cbStrictLock: CheckBox
    private val subjectButtons = mutableMapOf<String, Button>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("FocusForgePrefs", Context.MODE_PRIVATE)

        val scrollView = ScrollView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#0B1120"))
            isFillViewport = true
        }

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(44, 70, 44, 90)
        }

        // Top App Title & Pro Badge
        val tvTitle = TextView(this).apply {
            text = "FocusForge Pro ⚡"
            setTextColor(Color.parseColor("#38BDF8"))
            textSize = 28f
            setTypeface(null, Typeface.BOLD)
        }

        val tvSubtitle = TextView(this).apply {
            text = "Command Center • Deep Work, Shorts Shield & Analytics"
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 13f
            setPadding(0, 6, 0, 28)
        }

        // Live Accessibility Status Banner
        tvAccessibilityBanner = TextView(this).apply {
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setPadding(32, 24, 32, 24)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, 0, 0, 36)
            layoutParams = params
            setOnClickListener { openAccessibilitySettings() }
        }

        rootLayout.addView(tvTitle)
        rootLayout.addView(tvSubtitle)
        rootLayout.addView(tvAccessibilityBanner)

        // ==========================================
        // 1. CENTRAL PROGRESS & HUNTER RANK DASHBOARD
        // ==========================================
        val dashboardCard = createCardLayout("#1E293B")

        val tvDashHeader = createSectionTitle("CENTRAL PROGRESS DASHBOARD")

        tvHunterRank = TextView(this).apply {
            setTextColor(Color.parseColor("#FACC15"))
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 8, 0, 4)
        }

        tvXpDetails = TextView(this).apply {
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 13f
            setPadding(0, 0, 0, 12)
        }

        xpProgressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 500
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                22
            ).apply { setMargins(0, 0, 0, 28) }
        }

        tvDashboardMetrics = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setLineSpacing(10f, 1.1f)
            background = createRoundedDrawable("#0F172A")
            setPadding(32, 28, 32, 28)
        }

        tvSubjectBreakdown = TextView(this).apply {
            setTextColor(Color.parseColor("#38BDF8"))
            textSize = 13f
            setPadding(0, 20, 0, 16)
        }

        tvGoalProgress = TextView(this).apply {
            setTextColor(Color.parseColor("#A7F3D0"))
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 4, 0, 8)
        }

        goalProgressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 120
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                20
            )
        }

        dashboardCard.addView(tvDashHeader)
        dashboardCard.addView(tvHunterRank)
        dashboardCard.addView(tvXpDetails)
        dashboardCard.addView(xpProgressBar)
        dashboardCard.addView(tvDashboardMetrics)
        dashboardCard.addView(tvSubjectBreakdown)
        dashboardCard.addView(tvGoalProgress)
        dashboardCard.addView(goalProgressBar)
        rootLayout.addView(dashboardCard)

        // ==========================================
        // 2. STUDY FOCUS TIMER & SUBJECT ENGINE
        // ==========================================
        val timerCard = createCardLayout("#1E293B")
        timerCard.addView(createSectionTitle("STUDY FOCUS TIMER & SUBJECT TAG"))

        // Subject Tag Row
        val subjectRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 12)
        }

        val subjects = listOf("Business Law", "Accounts", "IT / Code", "Entrep.")
        subjects.forEach { subj ->
            val btn = Button(this).apply {
                text = subj
                textSize = 11f
                setTextColor(Color.WHITE)
                val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                params.setMargins(6, 0, 6, 0)
                layoutParams = params
                setOnClickListener {
                    selectedSubject = subj
                    updateSubjectSelectionUI()
                    tvTimerStatus.text = "Active Subject: $selectedSubject"
                }
            }
            subjectButtons[subj] = btn
            subjectRow.addView(btn)
        }
        updateSubjectSelectionUI()

        tvTimerDisplay = TextView(this).apply {
            text = formatTime(timeLeftInMillis)
            setTextColor(Color.WHITE)
            textSize = 54f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 4)
        }

        tvTimerStatus = TextView(this).apply {
            text = "Active Subject: $selectedSubject (25m Pomodoro)"
            setTextColor(Color.parseColor("#38BDF8"))
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 24)
        }

        // Preset Durations Row
        val presetRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 20)
        }

        presetRow.addView(createSmallButton("25m Study") {
            setTimerPreset(25 * 60 * 1000L, "25m Pomodoro • $selectedSubject")
        })
        presetRow.addView(createSmallButton("50m Deep") {
            setTimerPreset(50 * 60 * 1000L, "50m Deep Work • $selectedSubject")
        })
        presetRow.addView(createSmallButton("90m Exam") {
            setTimerPreset(90 * 60 * 1000L, "90m Exam Prep • $selectedSubject")
        })
        presetRow.addView(createSmallButton("5m Break") {
            setTimerPreset(5 * 60 * 1000L, "5m Rest Break")
        })

        // Strict Lock Mode Checkbox
        cbStrictLock = CheckBox(this).apply {
            text = "🔒 Strict Lock Mode (Prevent disabling blocker while timer runs)"
            setTextColor(Color.parseColor("#FDE047"))
            textSize = 13f
            isChecked = prefs.getBoolean("strict_lock_mode", true)
            setPadding(12, 8, 12, 20)
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean("strict_lock_mode", checked).apply()
            }
        }

        // Start / Reset / Log Buttons
        val controlRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        btnStartPause = createActionButton("Start Focus", "#4F46E5") {
            if (isTimerRunning) {
                pauseTimer()
            } else {
                startTimer()
            }
        }

        val btnReset = createActionButton("Reset", "#334155") {
            if (isTimerRunning && cbStrictLock.isChecked) {
                Toast.makeText(this, "🔒 Strict Lock Active! Pause first or finish session.", Toast.LENGTH_SHORT).show()
            } else {
                resetTimer()
            }
        }

        controlRow.addView(btnStartPause)
        controlRow.addView(btnReset)

        val btnCompleteNow = Button(this).apply {
            text = "✓ Log Completed Session Now (+XP & Study Minutes)"
            setTextColor(Color.parseColor("#A7F3D0"))
            textSize = 12f
            background = createRoundedDrawable("#0F172A")
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, 20, 0, 0)
            layoutParams = params
            setOnClickListener {
                recordCompletedSession()
            }
        }

        timerCard.addView(subjectRow)
        timerCard.addView(tvTimerDisplay)
        timerCard.addView(tvTimerStatus)
        timerCard.addView(presetRow)
        timerCard.addView(cbStrictLock)
        timerCard.addView(controlRow)
        timerCard.addView(btnCompleteNow)
        rootLayout.addView(timerCard)

        // ==========================================
        // 3. SMART DISTRACTION & SHORTS SHIELD
        // ==========================================
        val blockerCard = createCardLayout("#1E293B")
        blockerCard.addView(createSectionTitle("SMART APP & YOUTUBE SHORTS SHIELD"))

        var isBlockerEnabled = prefs.getBoolean("blocker_enabled", false)

        btnBlockerToggle = Button(this).apply {
            updateBlockerButtonStyle(this, isBlockerEnabled)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, 16, 0, 20)
            layoutParams = params
            setOnClickListener {
                if (isBlockerEnabled && isTimerRunning && cbStrictLock.isChecked) {
                    Toast.makeText(
                        this@MainActivity,
                        "🔒 Strict Lock is ON! You cannot disable the shield until your study timer finishes!",
                        Toast.LENGTH_LONG
                    ).show()
                    return@setOnClickListener
                }
                isBlockerEnabled = !isBlockerEnabled
                prefs.edit().putBoolean("blocker_enabled", isBlockerEnabled).apply()
                updateBlockerButtonStyle(this, isBlockerEnabled)
            }
        }
        blockerCard.addView(btnBlockerToggle)

        val shieldRules = listOf(
             Triple("block_shorts", "Block YouTube Shorts (Allow Study Lectures)", true),
            Triple("block_insta", "Block Instagram & Reels Completely", true),
            Triple("block_social_games", "Block Social Apps (FB, Snapchat, X, TikTok)", true),
            Triple("block_full_yt", "Strict Lock: Block Entire YouTube App", false)
        )

        shieldRules.forEach { (key, label, defaultVal) ->
            val cb = CheckBox(this).apply {
                text = label
                setTextColor(Color.WHITE)
                textSize = 14f
                isChecked = prefs.getBoolean(key, defaultVal)
                setPadding(12, 10, 12, 10)
                setOnCheckedChangeListener { _, checked ->
                    prefs.edit().putBoolean(key, checked).apply()
                }
            }
            blockerCard.addView(cb)
        }

        val btnAccessibility = Button(this).apply {
            text = "⚙️ Open Android Accessibility Settings"
            setTextColor(Color.WHITE)
            background = createRoundedDrawable("#334155")
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, 24, 0, 0)
            layoutParams = params
            setOnClickListener { openAccessibilitySettings() }
        }

        blockerCard.addView(btnAccessibility)
        rootLayout.addView(blockerCard)

        // ==========================================
        // 4. DAILY STUDY QUESTS & HABIT TRACKER
        // ==========================================
        val habitsCard = createCardLayout("#1E293B")
        habitsCard.addView(createSectionTitle("DAILY STUDY QUESTS (+50 XP EACH)"))

        val quests = listOf(
            "habit_1" to "Complete 2 Deep Focus Study Blocks",
            "habit_2" to "Revise Indian Contract Act / Business Law",
            "habit_3" to "Practice Accountancy / Core Coursework",
            "habit_4" to "Zero YouTube Shorts During Study Hours"
        )

        val questCheckBoxes = mutableListOf<CheckBox>()

        quests.forEach { (key, label) ->
            val cb = CheckBox(this).apply {
                text = label
                setTextColor(Color.WHITE)
                textSize = 14f
                isChecked = prefs.getBoolean(key, false)
                setPadding(12, 12, 12, 12)
                setOnCheckedChangeListener { _, checked ->
                    val wasChecked = prefs.getBoolean(key, false)
                    prefs.edit().putBoolean(key, checked).apply()
                    if (checked && !wasChecked) {
                        val xp = prefs.getInt("hunter_xp", 0) + 50
                        prefs.edit().putInt("hunter_xp", xp).apply()
                        Toast.makeText(this@MainActivity, "⚔️ Quest Completed! +50 XP", Toast.LENGTH_SHORT).show()
                        refreshDashboardUI()
                    }
                }
            }
            questCheckBoxes.add(cb)
            habitsCard.addView(cb)
        }

        val btnResetQuests = Button(this).apply {
            text = "Start New Day (Reset Daily Quests)"
            setTextColor(Color.parseColor("#CBD5E1"))
            textSize = 12f
            background = createRoundedDrawable("#0F172A")
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, 20, 0, 0)
            layoutParams = params
            setOnClickListener {
                questCheckBoxes.forEachIndexed { index, box ->
                    val key = quests[index].first
                    prefs.edit().putBoolean(key, false).apply()
                    box.isChecked = false
                }
                Toast.makeText(this@MainActivity, "Daily Quests Reset for New Day!", Toast.LENGTH_SHORT).show()
            }
        }

        habitsCard.addView(btnResetQuests)
        rootLayout.addView(habitsCard)

        scrollView.addView(rootLayout)
        setContentView(scrollView)

        refreshDashboardUI()
    }

    override fun onResume() {
        super.onResume()
        refreshDashboardUI()
    }

    private fun refreshDashboardUI() {
        val sessions = prefs.getInt("sessions_completed", 0)
        val totalMinutes = prefs.getInt("total_minutes", 0)
        val blockedCount = prefs.getInt("distractions_blocked", 0)
        val xp = prefs.getInt("hunter_xp", 0)

        // Calculate Hunter Rank & Level
        val level = (xp / 500) + 1
        val xpInCurrentLevel = xp % 500
        val rankTitle = when {
            level >= 10 -> "S-Rank Focus Monarch 👑"
            level >= 7 -> "A-Rank Elite Hunter 🔥"
            level >= 5 -> "B-Rank Deep Scholar ⚔️"
            level >= 3 -> "C-Rank Disciplined Striker 🛡️"
            level >= 2 -> "D-Rank Focus Challenger ⚡"
            else -> "E-Rank Novice Hunter 🌱"
        }

        tvHunterRank.text = "Lv. $level • $rankTitle"
        tvXpDetails.text = "Total XP: $xp  |  Next Level Progress: $xpInCurrentLevel / 500 XP"
        xpProgressBar.progress = xpInCurrentLevel

        val hours = totalMinutes / 60
        val mins = totalMinutes % 60
        tvDashboardMetrics.text =
            "⏱️ Total Focus Time: ${hours}h ${mins}m   |   🎯 Sessions: $sessions\n" +
            "🛡️ Shorts & Apps Blocked: $blockedCount   |   ⚡ Hunter XP: $xp"

        val lawMins = prefs.getInt("subj_Business Law", 0)
        val accMins = prefs.getInt("subj_Accounts", 0)
        val itMins = prefs.getInt("subj_IT / Code", 0)
        val entMins = prefs.getInt("subj_Entrep.", 0)
        tvSubjectBreakdown.text =
            "📊 Subject Breakdown: Law ${lawMins}m • Accounts ${accMins}m • IT ${itMins}m • Entrep ${entMins}m"

        val goalPct = ((totalMinutes * 100) / 120).coerceAtMost(100)
        tvGoalProgress.text = "Daily 2-Hour Study Target: ${totalMinutes}m / 120m ($goalPct%)"
        goalProgressBar.progress = totalMinutes.coerceAtMost(120)

        // Check if FocusAccessibilityService is enabled in Android Settings
        val isServiceEnabled = isAccessibilityServiceEnabled()
        if (isServiceEnabled) {
            tvAccessibilityBanner.text = "🟢 Accessibility Shield: CONNECTED & PROTECTING"
            tvAccessibilityBanner.setTextColor(Color.parseColor("#A7F3D0"))
            tvAccessibilityBanner.background = createRoundedDrawable("#064E3B")
        } else {
            tvAccessibilityBanner.text = "🔴 Accessibility Shield: NOT ENABLED (Tap here to turn ON in Settings)"
            tvAccessibilityBanner.setTextColor(Color.parseColor("#FECACA"))
            tvAccessibilityBanner.background = createRoundedDrawable("#7F1D1D")
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.contains(packageName)
    }

    private fun recordCompletedSession() {
        val addedMins = (initialTimeMillis / 60000L).toInt().coerceAtLeast(1)
        val sessions = prefs.getInt("sessions_completed", 0) + 1
        val totalMinutes = prefs.getInt("total_minutes", 0) + addedMins
        val subjKey = "subj_$selectedSubject"
        val subjMins = prefs.getInt(subjKey, 0) + addedMins
        val earnedXp = addedMins * 5
        val newXp = prefs.getInt("hunter_xp", 0) + earnedXp

        prefs.edit()
            .putInt("sessions_completed", sessions)
            .putInt("total_minutes", totalMinutes)
            .putInt(subjKey, subjMins)
            .putInt("hunter_xp", newXp)
            .apply()

        refreshDashboardUI()
        Toast.makeText(
            this,
            "🎉 +${addedMins}m logged for $selectedSubject! (+$earnedXp XP)",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun updateSubjectSelectionUI() {
        subjectButtons.forEach { (subj, btn) ->
            if (subj == selectedSubject) {
                btn.background = createRoundedDrawable("#0284C7")
            } else {
                btn.background = createRoundedDrawable("#334155")
            }
        }
    }

    private fun setTimerPreset(durationMillis: Long, label: String) {
        if (isTimerRunning && cbStrictLock.isChecked) {
            Toast.makeText(this, "🔒 Strict Lock Active! Finish current session first.", Toast.LENGTH_SHORT).show()
            return
        }
        countDownTimer?.cancel()
        isTimerRunning = false
        initialTimeMillis = durationMillis
        timeLeftInMillis = durationMillis
        tvTimerDisplay.text = formatTime(timeLeftInMillis)
        tvTimerStatus.text = label
        btnStartPause.text = "Start Focus"
    }

    private fun startTimer() {
        // Automatically enable blocker when starting focus timer
        prefs.edit().putBoolean("blocker_enabled", true).apply()
        updateBlockerButtonStyle(btnBlockerToggle, true)

        countDownTimer = object : CountDownTimer(timeLeftInMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timeLeftInMillis = millisUntilFinished
                tvTimerDisplay.text = formatTime(timeLeftInMillis)
            }

            override fun onFinish() {
                isTimerRunning = false
                timeLeftInMillis = initialTimeMillis
                tvTimerDisplay.text = formatTime(initialTimeMillis)
                tvTimerStatus.text = "Session Complete! Great work!"
                btnStartPause.text = "Start Focus"
                recordCompletedSession()
            }
        }.start()

        isTimerRunning = true
        tvTimerStatus.text = "🔥 Focus Active ($selectedSubject) — Shield Locked!"
        btnStartPause.text = "Pause"
    }

    private fun pauseTimer() {
        countDownTimer?.cancel()
        isTimerRunning = false
        tvTimerStatus.text = "Timer Paused ($selectedSubject)"
        btnStartPause.text = "Resume"
    }

    private fun resetTimer() {
        countDownTimer?.cancel()
        isTimerRunning = false
        timeLeftInMillis = initialTimeMillis
        tvTimerDisplay.text = formatTime(timeLeftInMillis)
        tvTimerStatus.text = "Timer Reset ($selectedSubject)"
        btnStartPause.text = "Start Focus"
    }

    private fun updateBlockerButtonStyle(button: Button, enabled: Boolean) {
        if (enabled) {
            button.text = "🛡️ STRICT APP & SHORTS SHIELD: ACTIVE"
            button.background = createRoundedDrawable("#059669")
        } else {
            button.text = "⚠️ STRICT APP & SHORTS SHIELD: OFF (Tap to Enable)"
            button.background = createRoundedDrawable("#DC2626")
        }
        button.setTextColor(Color.WHITE)
    }

    private fun openAccessibilitySettings() {
        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (e: Exception) {
            Toast.makeText(this, "Unable to open Accessibility settings", Toast.LENGTH_SHORT).show()
        }
    }

    private fun formatTime(millis: Long): String {
        val totalSeconds = millis / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }

    private fun createSectionTitle(title: String): TextView {
        return TextView(this).apply {
            text = title
            setTextColor(Color.parseColor("#CBD5E1"))
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 8)
        }
    }

    private fun createCardLayout(colorHex: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = createRoundedDrawable(colorHex)
            setPadding(40, 40, 40, 40)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, 0, 0, 36)
            layoutParams = params
        }
    }

    private fun createSmallButton(label: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            setTextColor(Color.WHITE)
            textSize = 11f
            background = createRoundedDrawable("#334155")
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            params.setMargins(6, 0, 6, 0)
            layoutParams = params
            setOnClickListener { onClick() }
        }
    }

    private fun createActionButton(label: String, colorHex: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            setTextColor(Color.WHITE)
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            background = createRoundedDrawable(colorHex)
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            params.setMargins(10, 0, 10, 0)
            layoutParams = params
            setOnClickListener { onClick() }
        }
    }

    private fun createRoundedDrawable(colorHex: String): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 28f
            setColor(Color.parseColor(colorHex))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }
}
