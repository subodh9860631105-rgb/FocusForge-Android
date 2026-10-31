package com.focusforge.pro

import android.content.Context
import android.content.Intent
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
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private var countDownTimer: CountDownTimer? = null
    private var isTimerRunning = false
    private var initialTimeMillis: Long = 25 * 60 * 1000L
    private var timeLeftInMillis: Long = initialTimeMillis

    private lateinit var tvTimerDisplay: TextView
    private lateinit var tvTimerStatus: TextView
    private lateinit var btnStartPause: Button
    private lateinit var tvStatsSummary: TextView
    private lateinit var btnBlockerToggle: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("FocusForgePrefs", Context.MODE_PRIVATE)
        var sessionsCompleted = prefs.getInt("sessions_completed", 0)
        var totalMinutes = prefs.getInt("total_minutes", 0)
        var isBlockerEnabled = prefs.getBoolean("blocker_enabled", false)

        val scrollView = ScrollView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#0F172A"))
            isFillViewport = true
        }

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(48, 80, 48, 80)
        }

        // Header Section
        val tvTitle = TextView(this).apply {
            text = "FocusForge Pro"
            setTextColor(Color.parseColor("#38BDF8"))
            textSize = 28f
            setTypeface(null, Typeface.BOLD)
        }

        val tvSubtitle = TextView(this).apply {
            text = "Study Focus Timer, App Blocker & Habit Tracker"
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 14f
            setPadding(0, 8, 0, 40)
        }

        rootLayout.addView(tvTitle)
        rootLayout.addView(tvSubtitle)

        // 1. Focus Timer Card
        val timerCard = createCardLayout()

        val tvTimerHeader = TextView(this).apply {
            text = "STUDY FOCUS TIMER"
            setTextColor(Color.parseColor("#CBD5E1"))
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
        }

        tvTimerDisplay = TextView(this).apply {
            text = formatTime(timeLeftInMillis)
            setTextColor(Color.WHITE)
            textSize = 52f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 8)
        }

        tvTimerStatus = TextView(this).apply {
            text = "Ready for 25m Pomodoro Session"
            setTextColor(Color.parseColor("#38BDF8"))
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 32)
        }

        // Preset Duration Row
        val presetRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 24)
        }

        val btn25m = createSmallButton("25m Study") {
            setTimerPreset(25 * 60 * 1000L, "25m Pomodoro Session")
        }
        val btn50m = createSmallButton("50m Deep") {
            setTimerPreset(50 * 60 * 1000L, "50m Deep Work Session")
        }
        val btn5m = createSmallButton("5m Break") {
            setTimerPreset(5 * 60 * 1000L, "5m Short Break")
        }

        presetRow.addView(btn25m)
        presetRow.addView(btn50m)
        presetRow.addView(btn5m)

        // Start / Reset Controls Row
        val controlRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        btnStartPause = createActionButton("Start Focus", "#4F46E5") {
            if (isTimerRunning) {
                pauseTimer()
            } else {
                startTimer {
                    val addedMins = (initialTimeMillis / 60000L).toInt().coerceAtLeast(1)
                    sessionsCompleted += 1
                    totalMinutes += addedMins
                    prefs.edit()
                        .putInt("sessions_completed", sessionsCompleted)
                        .putInt("total_minutes", totalMinutes)
                        .apply()
                    updateStatsText(sessionsCompleted, totalMinutes)
                }
            }
        }

        val btnReset = createActionButton("Reset", "#334155") {
            resetTimer()
        }

        controlRow.addView(btnStartPause)
        controlRow.addView(btnReset)

        timerCard.addView(tvTimerHeader)
        timerCard.addView(tvTimerDisplay)
        timerCard.addView(tvTimerStatus)
        timerCard.addView(presetRow)
        timerCard.addView(controlRow)
        rootLayout.addView(timerCard)

        // 2. Progress & Habit Tracker Card
        val statsCard = createCardLayout()

        val tvStatsHeader = TextView(this).apply {
            text = "STUDY PROGRESS & DAILY HABITS"
            setTextColor(Color.parseColor("#CBD5E1"))
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 16)
        }

        tvStatsSummary = TextView(this).apply {
            setTextColor(Color.parseColor("#A7F3D0"))
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 24)
        }
        updateStatsText(sessionsCompleted, totalMinutes)

        statsCard.addView(tvStatsHeader)
        statsCard.addView(tvStatsSummary)

        val habits = listOf(
            "habit_1" to "Complete 2 Deep Focus Study Blocks",
            "habit_2" to "Revise Core Subjects (Law / Accounts)",
            "habit_3" to "Zero Social Media During Study Hours"
        )

        habits.forEach { (key, label) ->
            val cb = CheckBox(this).apply {
                text = label
                setTextColor(Color.WHITE)
                textSize = 15f
                isChecked = prefs.getBoolean(key, false)
                setPadding(12, 12, 12, 12)
                setOnCheckedChangeListener { _, checked ->
                    prefs.edit().putBoolean(key, checked).apply()
                }
            }
            statsCard.addView(cb)
        }

        rootLayout.addView(statsCard)

        // 3. App Blocker Control Card
        val blockerCard = createCardLayout()

        val tvBlockerHeader = TextView(this).apply {
            text = "DISTRACTION APP BLOCKER"
            setTextColor(Color.parseColor("#CBD5E1"))
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 16)
        }

        btnBlockerToggle = Button(this).apply {
            updateBlockerButtonStyle(this, isBlockerEnabled)
            setOnClickListener {
                isBlockerEnabled = !isBlockerEnabled
                prefs.edit().putBoolean("blocker_enabled", isBlockerEnabled).apply()
                updateBlockerButtonStyle(this, isBlockerEnabled)
                val msg = if (isBlockerEnabled) "Strict Blocker Enabled!" else "Blocker Paused"
                Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
            }
        }

        val btnAccessibility = Button(this).apply {
            text = "Open Android Accessibility Settings"
            setTextColor(Color.WHITE)
            background = createRoundedDrawable("#334155")
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, 24, 0, 0)
            layoutParams = params
            setOnClickListener {
                try {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "Unable to open settings", Toast.LENGTH_SHORT).show()
                }
            }
        }

        blockerCard.addView(tvBlockerHeader)
        blockerCard.addView(btnBlockerToggle)
        blockerCard.addView(btnAccessibility)
        rootLayout.addView(blockerCard)

        scrollView.addView(rootLayout)
        setContentView(scrollView)
    }

    private fun setTimerPreset(durationMillis: Long, label: String) {
        countDownTimer?.cancel()
        isTimerRunning = false
        initialTimeMillis = durationMillis
        timeLeftInMillis = durationMillis
        tvTimerDisplay.text = formatTime(timeLeftInMillis)
        tvTimerStatus.text = label
        btnStartPause.text = "Start Focus"
    }

    private fun startTimer(onComplete: () -> Unit) {
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
                onComplete()
                Toast.makeText(this@MainActivity, "Focus Session Completed!", Toast.LENGTH_LONG).show()
            }
        }.start()

        isTimerRunning = true
        tvTimerStatus.text = "Focus Mode Active — Stay Locked In!"
        btnStartPause.text = "Pause"
    }

    private fun pauseTimer() {
        countDownTimer?.cancel()
        isTimerRunning = false
        tvTimerStatus.text = "Timer Paused"
        btnStartPause.text = "Resume"
    }

    private fun resetTimer() {
        countDownTimer?.cancel()
        isTimerRunning = false
        timeLeftInMillis = initialTimeMillis
        tvTimerDisplay.text = formatTime(timeLeftInMillis)
        tvTimerStatus.text = "Timer Reset"
        btnStartPause.text = "Start Focus"
    }

    private fun updateStatsText(sessions: Int, minutes: Int) {
        tvStatsSummary.text = "Sessions Completed: $sessions  |  Focus Time: ${minutes}m"
    }

    private fun updateBlockerButtonStyle(button: Button, enabled: Boolean) {
        if (enabled) {
            button.text = "Strict App Blocker: ACTIVE"
            button.background = createRoundedDrawable("#059669")
        } else {
            button.text = "Strict App Blocker: OFF (Tap to Enable)"
            button.background = createRoundedDrawable("#DC2626")
        }
        button.setTextColor(Color.WHITE)
    }

    private fun formatTime(millis: Long): String {
        val totalSeconds = millis / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }

    private fun createCardLayout(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = createRoundedDrawable("#1E293B")
            setPadding(44, 44, 44, 44)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, 0, 0, 40)
            layoutParams = params
        }
    }

    private fun createSmallButton(label: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            setTextColor(Color.WHITE)
            textSize = 12f
            background = createRoundedDrawable("#334155")
            val params = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            params.setMargins(8, 0, 8, 0)
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
            val params = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            params.setMargins(12, 0, 12, 0)
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
