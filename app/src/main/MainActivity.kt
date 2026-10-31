package com.focusforge.pro

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private var countDownTimer: CountDownTimer? = null
    private var isTimerRunning = false
    private var initialTimeMillis: Long = 45 * 60 * 1000L
    private var timeLeftInMillis: Long = initialTimeMillis
    private var selectedSubject = "CA Found: Business Laws"
    private var selectedCourseFilter = "All"
    private var currentTab = 0

    // Tab Containers
    private lateinit var tabFocusContainer: LinearLayout
    private lateinit var tabSyllabusContainer: LinearLayout
    private lateinit var tabConsistencyContainer: LinearLayout
    private lateinit var tabShieldContainer: LinearLayout
    private val navButtons = mutableListOf<Button>()

    // Header & Focus Views
    private lateinit var tvHunterBadge: TextView
    private lateinit var tvXpSub: TextView
    private lateinit var xpProgressBar: ProgressBar
    private lateinit var tvAccessibilityBanner: TextView
    private lateinit var focusRingView: CircularRingView
    private lateinit var habitRingView: CircularRingView
    private lateinit var tvFocusMetricSummary: TextView
    private lateinit var tvTimerDisplay: TextView
    private lateinit var tvTimerStatus: TextView
    private lateinit var btnStartPause: Button
    private lateinit var cbStrictLock: CheckBox
    private lateinit var subjectChipRow: LinearLayout

    // Dynamic Containers
    private lateinit var courseFilterRow: LinearLayout
    private lateinit var syllabusListContainer: LinearLayout
    private lateinit var consistencyContentContainer: LinearLayout
    private lateinit var btnBlockerToggle: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("FocusForgePrefs", Context.MODE_PRIVATE)
        ensureDefaultDataInitialized()

        val mainScreen = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#060A12"))
        }

        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            isFillViewport = true
        }

        val rootContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(40, 60, 40, 60)
        }

        // ==========================================
        // TOP FROSTED GLASS COMMAND HEADER
        // ==========================================
        val headerCard = createGlassCard("#10192D", "#2A4365")
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val titleCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val tvAppName = TextView(this).apply {
            text = "FocusForge Pro ⚡"
            setTextColor(Color.parseColor("#F8FAFC"))
            textSize = 23f
            setTypeface(null, Typeface.BOLD)
        }

        val tvRoleTag = TextView(this).apply {
            text = "CA (Found • Inter • Final) & B.Com Suite"
            setTextColor(Color.parseColor("#38BDF8"))
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 6, 0, 0)
        }

        titleCol.addView(tvAppName)
        titleCol.addView(tvRoleTag)

        tvHunterBadge = TextView(this).apply {
            setTextColor(Color.parseColor("#FDE047"))
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            background = createGlassDrawable("#1E293B", "#FACC15", 40f)
            setPadding(28, 14, 28, 14)
        }

        titleRow.addView(titleCol)
        titleRow.addView(tvHunterBadge)

        tvXpSub = TextView(this).apply {
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 12.5f
            setPadding(0, 20, 0, 10)
        }

        xpProgressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 500
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 16)
        }

        tvAccessibilityBanner = TextView(this).apply {
            textSize = 12.5f
            setTypeface(null, Typeface.BOLD)
            setPadding(30, 20, 30, 20)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, 22, 0, 0)
            layoutParams = params
            setOnClickListener { openAccessibilitySettings() }
        }

        headerCard.addView(titleRow)
        headerCard.addView(tvXpSub)
        headerCard.addView(xpProgressBar)
        headerCard.addView(tvAccessibilityBanner)
        rootContent.addView(headerCard)

        // ==========================================
        // 4 SPACIOUS TAB CONTAINERS
        // ==========================================
        tabFocusContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        tabSyllabusContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        tabConsistencyContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        tabShieldContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        buildFocusTab()
        buildSyllabusTab()
        buildConsistencyTab()
        buildShieldTab()

        rootContent.addView(tabFocusContainer)
        rootContent.addView(tabSyllabusContainer)
        rootContent.addView(tabConsistencyContainer)
        rootContent.addView(tabShieldContainer)

        scrollView.addView(rootContent)
        mainScreen.addView(scrollView)

        // ==========================================
        // BOTTOM GLASS NAVIGATION BAR
        // ==========================================
        val bottomNav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = createGlassDrawable("#0D1526", "#1E293B", 0f)
            setPadding(20, 18, 20, 22)
            gravity = Gravity.CENTER
        }

        val tabs = listOf(
            "⚡ Focus",
            "📚 Syllabus",
            "📊 Consistency",
            "🛡️ Shield"
        )

        tabs.forEachIndexed { index, label ->
            val btn = Button(this).apply {
                text = label
                isAllCaps = false
                textSize = 12.5f
                setTypeface(null, Typeface.BOLD)
                val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                params.setMargins(8, 0, 8, 0)
                layoutParams = params
                setOnClickListener { switchTab(index) }
            }
            navButtons.add(btn)
            bottomNav.addView(btn)
        }

        mainScreen.addView(bottomNav)
        setContentView(mainScreen)

        switchTab(0)
        refreshAllUI()
    }

    override fun onResume() {
        super.onResume()
        refreshAllUI()
    }

    private fun switchTab(index: Int) {
        currentTab = index
        tabFocusContainer.visibility = if (index == 0) View.VISIBLE else View.GONE
        tabSyllabusContainer.visibility = if (index == 1) View.VISIBLE else View.GONE
        tabConsistencyContainer.visibility = if (index == 2) View.VISIBLE else View.GONE
        tabShieldContainer.visibility = if (index == 3) View.VISIBLE else View.GONE

        navButtons.forEachIndexed { idx, btn ->
            if (idx == index) {
                btn.background = createGlassDrawable("#1E293B", "#38BDF8", 32f)
                btn.setTextColor(Color.parseColor("#38BDF8"))
            } else {
                btn.background = createGlassDrawable("#0D1526", "#1E293B", 32f)
                btn.setTextColor(Color.parseColor("#94A3B8"))
            }
        }
        refreshAllUI()
    }

    // ====================================================
    // TAB 1: CUSTOMIZABLE H:M FOCUS TIMER & REGAIN RINGS
    // ====================================================
    private fun buildFocusTab() {
        val ringCard = createGlassCard("#10192D", "#233B5E")
        ringCard.addView(createSectionLabel("DAILY FOCUS & CONSISTENCY RINGS"))

        val ringsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 20)
        }

        focusRingView = CircularRingView(this, "#38BDF8", "Study Target").apply {
            layoutParams = LinearLayout.LayoutParams(340, 340).apply { setMargins(18, 0, 18, 0) }
        }
        habitRingView = CircularRingView(this, "#10B981", "Daily Habits").apply {
            layoutParams = LinearLayout.LayoutParams(340, 340).apply { setMargins(18, 0, 18, 0) }
        }

        ringsRow.addView(focusRingView)
        ringsRow.addView(habitRingView)

        tvFocusMetricSummary = TextView(this).apply {
            setTextColor(Color.parseColor("#E2E8F0"))
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            background = createGlassDrawable("#090F1D", "#1E293B", 26f)
            setPadding(28, 22, 28, 22)
        }

        ringCard.addView(ringsRow)
        ringCard.addView(tvFocusMetricSummary)
        tabFocusContainer.addView(ringCard)

        // Precision Customizable Timer Card
        val timerCard = createGlassCard("#10192D", "#2A4365")
        timerCard.addView(createSectionLabel("CUSTOMIZABLE STUDY CLOCK (HOURS & MINUTES)"))

        val chipScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            setPadding(0, 14, 0, 14)
        }
        subjectChipRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        chipScroll.addView(subjectChipRow)

        tvTimerDisplay = TextView(this).apply {
            text = formatTime(timeLeftInMillis)
            setTextColor(Color.parseColor("#F8FAFC"))
            textSize = 56f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 18, 0, 4)
            setOnClickListener { showCustomTimerDialog() }
        }

        tvTimerStatus = TextView(this).apply {
            text = "Active Subject: $selectedSubject (Tap clock to set custom H:M)"
            setTextColor(Color.parseColor("#38BDF8"))
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 22)
        }

        val fineTuneRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16)
        }
        fineTuneRow.addView(createPillButton("- 5m") { adjustTimerByMinutes(-5) })
        fineTuneRow.addView(createPillButton("⏱️ Set Custom H:M") { showCustomTimerDialog() })
        fineTuneRow.addView(createPillButton("+ 5m") { adjustTimerByMinutes(5) })

        val presetRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 20)
        }
        presetRow.addView(createPillButton("25m") {
            setTimerPreset(25 * 60 * 1000L, "25m Pomodoro • $selectedSubject")
        })
        presetRow.addView(createPillButton("45m") {
            setTimerPreset(45 * 60 * 1000L, "45m Study Block • $selectedSubject")
        })
        presetRow.addView(createPillButton("1h 30m") {
            setTimerPreset(90 * 60 * 1000L, "1h 30m Deep Prep • $selectedSubject")
        })
        presetRow.addView(createPillButton("3h Exam") {
            setTimerPreset(180 * 60 * 1000L, "3h CA Exam Simulation • $selectedSubject")
        })

        cbStrictLock = CheckBox(this).apply {
            text = "🔒 Regain Strict Mode (Lock Shield until timer completes)"
            setTextColor(Color.parseColor("#FDE047"))
            textSize = 13f
            isChecked = prefs.getBoolean("strict_lock_mode", true)
            setPadding(12, 12, 12, 20)
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean("strict_lock_mode", checked).apply()
            }
        }

        val controlRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        btnStartPause = createActionButton("Start Focus", "#4F46E5", "#818CF8") {
            if (isTimerRunning) pauseTimer() else startTimer()
        }

        val btnReset = createActionButton("Reset", "#1E293B", "#334155") {
            if (isTimerRunning && cbStrictLock.isChecked) {
                Toast.makeText(this, "🔒 Strict Mode active! Pause first or finish session.", Toast.LENGTH_SHORT).show()
            } else {
                resetTimer()
            }
        }

        controlRow.addView(btnStartPause)
        controlRow.addView(btnReset)

        val logActionsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 0)
        }

        val btnQuickLog = Button(this).apply {
            text = "✓ Log Study Block"
            isAllCaps = false
            setTextColor(Color.parseColor("#6EE7B7"))
            textSize = 12.5f
            setTypeface(null, Typeface.BOLD)
            background = createGlassDrawable("#064E3B", "#10B981", 26f)
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f)
            params.setMargins(0, 0, 10, 0)
            layoutParams = params
            setOnClickListener { recordCompletedSession() }
        }

        val btnUndoLog = Button(this).apply {
            text = "↩️ Undo Last Log"
            isAllCaps = false
            setTextColor(Color.parseColor("#FCA5A5"))
            textSize = 12.5f
            setTypeface(null, Typeface.BOLD)
            background = createGlassDrawable("#450A0A", "#EF4444", 26f)
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.9f)
            params.setMargins(10, 0, 0, 0)
            layoutParams = params
            setOnClickListener { undoLastStudySession() }
        }

        logActionsRow.addView(btnQuickLog)
        logActionsRow.addView(btnUndoLog)

        timerCard.addView(chipScroll)
        timerCard.addView(tvTimerDisplay)
        timerCard.addView(tvTimerStatus)
        timerCard.addView(fineTuneRow)
        timerCard.addView(presetRow)
        timerCard.addView(cbStrictLock)
        timerCard.addView(controlRow)
        timerCard.addView(logActionsRow)
        tabFocusContainer.addView(timerCard)
    }

    private fun adjustTimerByMinutes(deltaMinutes: Int) {
        if (isTimerRunning && cbStrictLock.isChecked) {
            Toast.makeText(this, "🔒 Pause or finish current session first.", Toast.LENGTH_SHORT).show()
            return
        }
        countDownTimer?.cancel()
        isTimerRunning = false
        val newMillis = (initialTimeMillis + deltaMinutes * 60 * 1000L).coerceIn(60 * 1000L, 12 * 3600 * 1000L)
        initialTimeMillis = newMillis
        timeLeftInMillis = newMillis
        tvTimerDisplay.text = formatTime(timeLeftInMillis)
        val totalMins = (newMillis / 60000L).toInt()
        tvTimerStatus.text = "Custom Duration: ${totalMins / 60}h ${totalMins % 60}m • $selectedSubject"
        btnStartPause.text = "Start Focus"
    }

    private fun showCustomTimerDialog() {
        if (isTimerRunning && cbStrictLock.isChecked) {
            Toast.makeText(this, "🔒 Strict Mode Active! Pause or finish session first.", Toast.LENGTH_SHORT).show()
            return
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(56, 40, 56, 20)
        }

        val currentTotalMins = (initialTimeMillis / 60000L).toInt()
        val currentHrs = currentTotalMins / 60
        val currentMins = currentTotalMins % 60

        val tvHoursLabel = TextView(this).apply {
            text = "Hours (0 to 12):"
            setTypeface(null, Typeface.BOLD)
            textSize = 14f
        }
        val inputHours = EditText(this).apply {
            setRawInputType(InputType.TYPE_CLASS_NUMBER)
            setText(currentHrs.toString())
            hint = "Hours (e.g. 1)"
        }

        val tvMinsLabel = TextView(this).apply {
            text = "Minutes (0 to 59):"
            setTypeface(null, Typeface.BOLD)
            textSize = 14f
            setPadding(0, 24, 0, 0)
        }
        val inputMinutes = EditText(this).apply {
            setRawInputType(InputType.TYPE_CLASS_NUMBER)
            setText(currentMins.toString())
            hint = "Minutes (e.g. 45)"
        }

        layout.addView(tvHoursLabel)
        layout.addView(inputHours)
        layout.addView(tvMinsLabel)
        layout.addView(inputMinutes)

        AlertDialog.Builder(this)
            .setTitle("⏱️ Set Custom Focus Timer")
            .setView(layout)
            .setPositiveButton("Set Timer") { _, _ ->
                val hrs = inputHours.text.toString().toIntOrNull()?.coerceIn(0, 12) ?: 0
                val mins = inputMinutes.text.toString().toIntOrNull()?.coerceIn(0, 59) ?: 0
                val totalMinutes = (hrs * 60 + mins).coerceAtLeast(1)
                val millis = totalMinutes * 60 * 1000L
                setTimerPreset(millis, "Custom ${hrs}h ${mins}m • $selectedSubject")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ====================================================
    // TAB 2: CA (FOUNDATION / INTER / FINAL) & B.COM SYLLABUS
    // ====================================================
    private fun buildSyllabusTab() {
        val topHeaderCard = createGlassCard("#10192D", "#2A4365")
        topHeaderCard.addView(createSectionLabel("CA (FOUNDATION • INTER • FINAL) & B.COM SYLLABUS"))

        val tvDesc = TextView(this).apply {
            text = "Organize Subjects, Modules & Units with bold clarity. Tap status to cycle (⬜ Todo → ⏳ Studying → ✅ Done) or use ✏️ Edit and ✕ Remove."
            setTextColor(Color.parseColor("#CBD5E1"))
            textSize = 13f
            setLineSpacing(6f, 1.1f)
            setPadding(0, 6, 0, 20)
        }

        val filterScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            setPadding(0, 0, 0, 20)
        }
        courseFilterRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        filterScroll.addView(courseFilterRow)

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val btnAddSubject = Button(this).apply {
            text = "+ Add Custom Subject"
            isAllCaps = false
            setTextColor(Color.WHITE)
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            background = createGlassDrawable("#0284C7", "#38BDF8", 28f)
            val p = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            p.setMargins(0, 0, 10, 0)
            layoutParams = p
            setOnClickListener { showAddSubjectDialog() }
        }

        val btnLoadTemplates = Button(this).apply {
            text = "📚 Load CA / B.Com Packs"
            isAllCaps = false
            setTextColor(Color.parseColor("#FDE047"))
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            background = createGlassDrawable("#1E293B", "#FACC15", 28f)
            val p = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            p.setMargins(10, 0, 0, 0)
            layoutParams = p
            setOnClickListener { showCoursePackTemplatesDialog() }
        }

        btnRow.addView(btnAddSubject)
        btnRow.addView(btnLoadTemplates)

        topHeaderCard.addView(tvDesc)
        topHeaderCard.addView(filterScroll)
        topHeaderCard.addView(btnRow)
        tabSyllabusContainer.addView(topHeaderCard)

        syllabusListContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        tabSyllabusContainer.addView(syllabusListContainer)
    }

    private fun renderSyllabusCards() {
        syllabusListContainer.removeAllViews()
        subjectChipRow.removeAllViews()
        courseFilterRow.removeAllViews()

        val levels = listOf("All", "CA Foundation", "CA Inter", "CA Final", "B.Com")
        levels.forEach { lvl ->
            val fBtn = Button(this).apply {
                text = lvl
                isAllCaps = false
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                val isSelected = selectedCourseFilter == lvl
                setTextColor(if (isSelected) Color.parseColor("#060A12") else Color.parseColor("#E2E8F0"))
                background = if (isSelected) {
                    createGlassDrawable("#38BDF8", "#7DD3FC", 32f)
                } else {
                    createGlassDrawable("#1E293B", "#334155", 32f)
                }
                setPadding(32, 12, 32, 12)
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.setMargins(0, 0, 14, 0)
                layoutParams = lp
                setOnClickListener {
                    selectedCourseFilter = lvl
                    renderSyllabusCards()
                }
            }
            courseFilterRow.addView(fBtn)
        }

        val subjectsArray = JSONArray(prefs.getString("syllabus_json", "[]"))

        for (i in 0 until subjectsArray.length()) {
            val subjObj = subjectsArray.getJSONObject(i)
            val subjName = subjObj.getString("name")
            val courseLevel = subjObj.optString("level", "CA Foundation")
            val unitsArray = subjObj.getJSONArray("units")

            val chipBtn = Button(this).apply {
                text = subjName
                isAllCaps = false
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
                val isSel = subjName == selectedSubject
                background = if (isSel) {
                    createGlassDrawable("#0284C7", "#38BDF8", 32f)
                } else {
                    createGlassDrawable("#1E293B", "#334155", 32f)
                }
                setPadding(32, 12, 32, 12)
                val p = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                p.setMargins(0, 0, 16, 0)
                layoutParams = p
                setOnClickListener {
                    selectedSubject = subjName
                    tvTimerStatus.text = "Active Subject: $selectedSubject"
                    renderSyllabusCards()
                }
            }
            subjectChipRow.addView(chipBtn)

            if (selectedCourseFilter != "All" && courseLevel != selectedCourseFilter) {
                continue
            }

            val card = createGlassCard("#10192D", "#243B61")
            var completedUnits = 0
            for (u in 0 until unitsArray.length()) {
                if (unitsArray.getJSONObject(u).getInt("status") == 2) completedUnits++
            }
            val totalUnits = unitsArray.length()
            val pct = if (totalUnits > 0) (completedUnits * 100) / totalUnits else 0
            val studiedMins = prefs.getInt("subj_$subjName", 0)

            val badgeRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 0, 0, 10)
            }

            val tvLevelTag = TextView(this).apply {
                text = courseLevel.uppercase(Locale.getDefault())
                setTextColor(Color.parseColor("#38BDF8"))
                textSize = 11f
                setTypeface(null, Typeface.BOLD)
                background = createGlassDrawable("#0B1528", "#1E3A5F", 20f)
                setPadding(22, 8, 22, 8)
            }

            val spacer = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
            }

            val btnEditSubj = TextView(this).apply {
                text = "✏️ Edit"
                setTextColor(Color.parseColor("#CBD5E1"))
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                background = createGlassDrawable("#1E293B", "#334155", 20f)
                setPadding(24, 10, 24, 10)
                setOnClickListener { showEditSubjectDialog(i) }
            }

            val btnDeleteSubj = TextView(this).apply {
                text = "🗑️ Delete"
                setTextColor(Color.parseColor("#FCA5A5"))
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                background = createGlassDrawable("#450A0A", "#EF4444", 20f)
                setPadding(24, 10, 24, 10)
                val p = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                p.setMargins(12, 0, 0, 0)
                layoutParams = p
                setOnClickListener { confirmDeleteSubject(i, subjName) }
            }

            badgeRow.addView(tvLevelTag)
            badgeRow.addView(spacer)
            badgeRow.addView(btnEditSubj)
            badgeRow.addView(btnDeleteSubj)

            val tvSubjTitle = TextView(this).apply {
                text = "📘 $subjName"
                setTextColor(Color.parseColor("#F8FAFC"))
                textSize = 19f
                setTypeface(null, Typeface.BOLD)
                setPadding(0, 6, 0, 8)
            }

            val tvMetaRow = TextView(this).apply {
                text = "⏱️ Studied: ${studiedMins / 60}h ${studiedMins % 60}m   •   ✅ Completed: $completedUnits / $totalUnits Units ($pct%)"
                setTextColor(Color.parseColor("#94A3B8"))
                textSize = 12.5f
                setTypeface(null, Typeface.BOLD)
                setPadding(0, 0, 0, 14)
            }

            val pb = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100
                progress = pct
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 16).apply {
                    setMargins(0, 0, 0, 24)
                }
            }

            card.addView(badgeRow)
            card.addView(tvSubjTitle)
            card.addView(tvMetaRow)
            card.addView(pb)

            for (u in 0 until unitsArray.length()) {
                val unitObj = unitsArray.getJSONObject(u)
                val moduleTag = unitObj.optString("module", "Module ${u + 1}")
                val unitTitle = unitObj.getString("title")
                val status = unitObj.getInt("status")

                val (statusLabel, colorHex, bgHex) = when (status) {
                    2 -> Triple("✅ Done", "#6EE7B7", "#064E3B")
                    1 -> Triple("⏳ Studying", "#FDE047", "#422006")
                    else -> Triple("⬜ Todo", "#CBD5E1", "#090F1D")
                }

                val unitContainer = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    background = createGlassDrawable(bgHex, "#1E293B", 24f)
                    setPadding(26, 22, 22, 22)
                    val p = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    p.setMargins(0, 0, 0, 16)
                    layoutParams = p
                }

                val textCol = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    setOnClickListener {
                        val nextStatus = (status + 1) % 3
                        unitObj.put("status", nextStatus)
                        prefs.edit().putString("syllabus_json", subjectsArray.toString()).apply()
                        if (nextStatus == 2) {
                            addXp(35, "Module/Unit Completed! +35 XP")
                        }
                        renderSyllabusCards()
                    }
                }

                val tvModBadge = TextView(this).apply {
                    text = "$moduleTag  •  $statusLabel"
                    setTextColor(Color.parseColor("#38BDF8"))
                    textSize = 11.5f
                    setTypeface(null, Typeface.BOLD)
                }

                val tvUnitName = TextView(this).apply {
                    text = unitTitle
                    setTextColor(Color.parseColor(colorHex))
                    textSize = 14.5f
                    setTypeface(null, Typeface.BOLD)
                    setPadding(0, 6, 12, 0)
                }

                textCol.addView(tvModBadge)
                textCol.addView(tvUnitName)

                val btnEditUnit = TextView(this).apply {
                    text = "✏️"
                    textSize = 14f
                    background = createGlassDrawable("#1E293B", "#334155", 18f)
                    setPadding(22, 14, 22, 14)
                    setOnClickListener { showEditUnitDialog(i, u) }
                }

                val btnRemoveUnit = TextView(this).apply {
                    text = "✕"
                    setTextColor(Color.parseColor("#FCA5A5"))
                    textSize = 14f
                    setTypeface(null, Typeface.BOLD)
                    background = createGlassDrawable("#450A0A", "#EF4444", 18f)
                    setPadding(24, 14, 24, 14)
                    val p = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    p.setMargins(12, 0, 0, 0)
                    layoutParams = p
                    setOnClickListener {
                        unitsArray.remove(u)
                        prefs.edit().putString("syllabus_json", subjectsArray.toString()).apply()
                        Toast.makeText(this@MainActivity, "Removed: $unitTitle", Toast.LENGTH_SHORT).show()
                        renderSyllabusCards()
                    }
                }

                unitContainer.addView(textCol)
                unitContainer.addView(btnEditUnit)
                unitContainer.addView(btnRemoveUnit)
                card.addView(unitContainer)
            }

            val btnAddUnit = Button(this).apply {
                text = "+ Add Module / Chapter / Unit to $subjName"
                isAllCaps = false
                setTextColor(Color.parseColor("#38BDF8"))
                textSize = 13f
                setTypeface(null, Typeface.BOLD)
                background = createGlassDrawable("#0B1528", "#1E3A5F", 26f)
                val p = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                p.setMargins(0, 8, 0, 0)
                layoutParams = p
                setOnClickListener { showAddUnitDialog(i) }
            }

            card.addView(btnAddUnit)
            syllabusListContainer.addView(card)
        }
    }

    private fun showAddSubjectDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(56, 36, 56, 20)
        }
        val inputLevel = EditText(this).apply {
            hint = "Course Level (CA Foundation / CA Inter / CA Final / B.Com)"
            setText(if (selectedCourseFilter == "All") "CA Foundation" else selectedCourseFilter)
        }
        val inputSubj = EditText(this).apply {
            hint = "Subject Name (e.g., Corporate & Other Laws / Costing)"
        }
        val inputMod = EditText(this).apply {
            hint = "First Module No. (e.g., Module 1 • Unit 1)"
            setText("Module 1 • Unit 1")
        }
        val inputUnit = EditText(this).apply {
            hint = "First Lesson / Chapter Title"
        }

        layout.addView(inputLevel)
        layout.addView(inputSubj)
        layout.addView(inputMod)
        layout.addView(inputUnit)

        AlertDialog.Builder(this)
            .setTitle("📘 Add New Subject")
            .setView(layout)
            .setPositiveButton("Add Subject") { _, _ ->
                val level = inputLevel.text.toString().trim().ifEmpty { "CA Foundation" }
                val name = inputSubj.text.toString().trim()
                val mod = inputMod.text.toString().trim().ifEmpty { "Module 1" }
                val firstUnit = inputUnit.text.toString().trim().ifEmpty { "Core Chapter & Concepts" }
                if (name.isNotEmpty()) {
                    val arr = JSONArray(prefs.getString("syllabus_json", "[]"))
                    val units = JSONArray().apply {
                        put(JSONObject().put("module", mod).put("title", firstUnit).put("status", 0))
                    }
                    arr.put(
                        JSONObject()
                            .put("name", name)
                            .put("level", level)
                            .put("units", units)
                    )
                    prefs.edit().putString("syllabus_json", arr.toString()).apply()
                    selectedSubject = name
                    renderSyllabusCards()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEditSubjectDialog(subjectIndex: Int) {
        val arr = JSONArray(prefs.getString("syllabus_json", "[]"))
        val subjObj = arr.getJSONObject(subjectIndex)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(56, 36, 56, 20)
        }
        val inputLevel = EditText(this).apply {
            setText(subjObj.optString("level", "CA Foundation"))
            hint = "Course Level (CA Foundation / CA Inter / CA Final / B.Com)"
        }
        val inputName = EditText(this).apply {
            setText(subjObj.getString("name"))
            hint = "Subject Name"
        }
        layout.addView(inputLevel)
        layout.addView(inputName)

        AlertDialog.Builder(this)
            .setTitle("✏️ Edit Subject")
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                val newName = inputName.text.toString().trim()
                val newLevel = inputLevel.text.toString().trim().ifEmpty { "CA Foundation" }
                if (newName.isNotEmpty()) {
                    subjObj.put("name", newName)
                    subjObj.put("level", newLevel)
                    prefs.edit().putString("syllabus_json", arr.toString()).apply()
                    renderSyllabusCards()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDeleteSubject(subjectIndex: Int, subjName: String) {
        AlertDialog.Builder(this)
            .setTitle("Remove Subject?")
            .setMessage("Are you sure you want to delete '$subjName' and its modules?")
            .setPositiveButton("Delete") { _, _ ->
                val arr = JSONArray(prefs.getString("syllabus_json", "[]"))
                arr.remove(subjectIndex)
                prefs.edit().putString("syllabus_json", arr.toString()).apply()
                renderSyllabusCards()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAddUnitDialog(subjectIndex: Int) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(56, 36, 56, 20)
        }
        val inputModule = EditText(this).apply {
            hint = "Module / Unit Tag (e.g., Module 2 • Unit 1)"
            setText("Module 1 • Unit 1")
        }
        val inputTitle = EditText(this).apply {
            hint = "Chapter / Lesson Name (e.g., Indian Contract Act - Bailment)"
        }
        layout.addView(inputModule)
        layout.addView(inputTitle)

        AlertDialog.Builder(this)
            .setTitle("➕ Add Module & Lesson/Unit")
            .setView(layout)
            .setPositiveButton("Add") { _, _ ->
                val mod = inputModule.text.toString().trim().ifEmpty { "Module" }
                val title = inputTitle.text.toString().trim()
                if (title.isNotEmpty()) {
                    val arr = JSONArray(prefs.getString("syllabus_json", "[]"))
                    val subj = arr.getJSONObject(subjectIndex)
                    subj.getJSONArray("units").put(
                        JSONObject().put("module", mod).put("title", title).put("status", 0)
                    )
                    prefs.edit().putString("syllabus_json", arr.toString()).apply()
                    renderSyllabusCards()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEditUnitDialog(subjectIndex: Int, unitIndex: Int) {
        val arr = JSONArray(prefs.getString("syllabus_json", "[]"))
        val unitObj = arr.getJSONObject(subjectIndex).getJSONArray("units").getJSONObject(unitIndex)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(56, 36, 56, 20)
        }
        val inputModule = EditText(this).apply {
            setText(unitObj.optString("module", "Module 1"))
            hint = "Module / Unit Tag"
        }
        val inputTitle = EditText(this).apply {
            setText(unitObj.getString("title"))
            hint = "Lesson / Unit Title"
        }
        layout.addView(inputModule)
        layout.addView(inputTitle)

        AlertDialog.Builder(this)
            .setTitle("✏️ Edit Module / Unit")
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                val newMod = inputModule.text.toString().trim().ifEmpty { "Module" }
                val newTitle = inputTitle.text.toString().trim()
                if (newTitle.isNotEmpty()) {
                    unitObj.put("module", newMod)
                    unitObj.put("title", newTitle)
                    prefs.edit().putString("syllabus_json", arr.toString()).apply()
                    renderSyllabusCards()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showCoursePackTemplatesDialog() {
        val options = arrayOf(
            "➕ Add CA Intermediate Pack (Adv Accounts, Corp Law, Tax, Costing, Audit, FM)",
            "➕ Add CA Final Pack (FR, AFM, Adv Audit, DT, IDT, IBS)",
            "➕ Add B.Com Semester Pack (Financial Acc, Business Mgmt, Corp Law, Economics)"
        )
        AlertDialog.Builder(this)
            .setTitle("📚 Load CA / B.Com Subject Packs")
            .setItems(options) { _, which ->
                val arr = JSONArray(prefs.getString("syllabus_json", "[]"))
                when (which) {
                    0 -> {
                        arr.put(createSubjectPack("CA Inter: Advanced Accounting", "CA Inter", listOf(
                            "Mod 1 • Accounting Standards (AS 1 to AS 29)",
                            "Mod 2 • Company Financial Statements & Amalgamation",
                            "Mod 3 • Internal Reconstruction & Consolidation"
                        )))
                        arr.put(createSubjectPack("CA Inter: Corporate & Other Laws", "CA Inter", listOf(
                            "Mod 1 • Preliminary to Share Capital & Debentures",
                            "Mod 2 • Management, Administration & Audit",
                            "Mod 3 • FEMA, 1999 & LLP Act"
                        )))
                        arr.put(createSubjectPack("CA Inter: Taxation (DT & GST)", "CA Inter", listOf(
                            "Part I • Income Tax: Heads of Income & Deductions",
                            "Part II • GST: Supply, ITC, Registration & Returns"
                        )))
                        arr.put(createSubjectPack("CA Inter: Cost & Management Accounting", "CA Inter", listOf(
                            "Mod 1 • Material, Employee & Overheads Cost",
                            "Mod 2 • Process, Contract & Service Costing",
                            "Mod 3 • Standard, Marginal & Budgetary Control"
                        )))
                    }
                    1 -> {
                        arr.put(createSubjectPack("CA Final: Financial Reporting (FR)", "CA Final", listOf(
                            "Mod 1 • Ind AS on Presentation & Assets",
                            "Mod 2 • Ind AS 115 Revenue & Financial Instruments",
                            "Mod 3 • Business Combinations & Consolidated FS"
                        )))
                        arr.put(createSubjectPack("CA Final: Advanced Financial Mgmt (AFM)", "CA Final", listOf(
                            "Mod 1 • Capital Budgeting, Portfolio & Derivatives",
                            "Mod 2 • Forex Exposure, Mergers & Valuation"
                        )))
                        arr.put(createSubjectPack("CA Final: Direct Tax & International Tax", "CA Final", listOf(
                            "Mod 1 • Corporate Taxation, Assessment & Appeals",
                            "Mod 2 • Transfer Pricing, DTAA & Non-Resident Tax"
                        )))
                    }
                    2 -> {
                        arr.put(createSubjectPack("B.Com: Financial & Corporate Accounting", "B.Com", listOf(
                            "Unit 1 • Theoretical Framework & BRS",
                            "Unit 2 • Final Accounts, Branch & Departmental",
                            "Unit 3 • Issue of Shares & Company Final Accounts"
                        )))
                        arr.put(createSubjectPack("B.Com: Business Organization & Mgmt", "B.Com", listOf(
                            "Unit 1 • Forms of Business Enterprise",
                            "Unit 2 • Planning, Organizing, Staffing & Directing",
                            "Unit 3 • Leadership, Motivation & Control"
                        )))
                    }
                }
                prefs.edit().putString("syllabus_json", arr.toString()).apply()
                Toast.makeText(this, "📚 Course pack added to Syllabus!", Toast.LENGTH_SHORT).show()
                renderSyllabusCards()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun createSubjectPack(name: String, level: String, unitsList: List<String>): JSONObject {
        val unitsArr = JSONArray()
        unitsList.forEachIndexed { idx, item ->
            val parts = item.split("•")
            val mod = if (parts.size > 1) parts[0].trim() else "Module ${idx + 1}"
            val title = if (parts.size > 1) parts.drop(1).joinToString(" • ").trim() else item
            unitsArr.put(JSONObject().put("module", mod).put("title", title).put("status", 0))
        }
        return JSONObject().put("name", name).put("level", level).put("units", unitsArr)
    }

    // ====================================================
    // TAB 3: STREAKO CONSISTENCY + REVERSIBLE WORKOUT SUITE
    // ====================================================
    private fun buildConsistencyTab() {
        consistencyContentContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        tabConsistencyContainer.addView(consistencyContentContainer)
    }

    private fun renderConsistencyTab() {
        consistencyContentContainer.removeAllViews()

        val habitsArray = JSONArray(prefs.getString("habits_matrix_json", "[]"))
        var totalChecks = 0
        val dayTotals = IntArray(7)
        val totalHabits = habitsArray.length().coerceAtLeast(1)

        for (i in 0 until habitsArray.length()) {
            val days = habitsArray.getJSONObject(i).getJSONArray("days")
            for (d in 0 until 7) {
                if (days.getBoolean(d)) {
                    totalChecks++
                    dayTotals[d]++
                }
            }
        }
        val weeklyPct = (totalChecks * 100) / (totalHabits * 7)
        val dayPercentages = dayTotals.map { (it * 100) / totalHabits }.toIntArray()

        val bannerCard = createGlassCard("#10192D", "#10B981")
        val tvTrust = TextView(this).apply {
            text = "trust the process."
            setTextColor(Color.parseColor("#34D399"))
            textSize = 23f
            setTypeface(null, Typeface.BOLD)
        }
        val tvAffirmation = TextView(this).apply {
            text = "\"Every checkmark builds momentum. See your CA study & workout consistency grow day by day.\""
            setTextColor(Color.parseColor("#CBD5E1"))
            textSize = 12.5f
            setPadding(0, 6, 0, 20)
        }

        val barChart = WeeklyBarChartView(this, dayPercentages).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                260
            )
        }

        val tvWeeklySummary = TextView(this).apply {
            text = "📈 Weekly Consistency Score: $weeklyPct% ($totalChecks / ${totalHabits * 7} checkmarks completed)"
            setTextColor(Color.parseColor("#A7F3D0"))
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 14, 0, 0)
        }

        bannerCard.addView(tvTrust)
        bannerCard.addView(tvAffirmation)
        bannerCard.addView(barChart)
        bannerCard.addView(tvWeeklySummary)
        consistencyContentContainer.addView(bannerCard)

        val matrixCard = createGlassCard("#10192D", "#2A4365")
        matrixCard.addView(createSectionLabel("7-DAY HABIT MATRIX (TAP ⚙️ TO EDIT OR DELETE HABIT)"))

        val dayLabels = listOf("M", "T", "W", "T", "F", "S", "S")
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 8, 0, 14)
        }
        val tvHabitColHeader = TextView(this).apply {
            text = "Habit (⚙️ Edit/Del)"
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 11.5f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2.1f)
        }
        headerRow.addView(tvHabitColHeader)
        dayLabels.forEach { dLabel ->
            val tvDay = TextView(this).apply {
                text = dLabel
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#38BDF8"))
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.45f)
            }
            headerRow.addView(tvDay)
        }
        matrixCard.addView(headerRow)

        for (i in 0 until habitsArray.length()) {
            val habitObj = habitsArray.getJSONObject(i)
            val title = habitObj.getString("title")
            val days = habitObj.getJSONArray("days")

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = createGlassDrawable("#090F1D", "#1E293B", 20f)
                setPadding(16, 16, 12, 16)
                val p = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                p.setMargins(0, 0, 0, 12)
                layoutParams = p
            }

            val tvHabitTitle = TextView(this).apply {
                text = "⚙️ $title"
                setTextColor(Color.WHITE)
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2.1f)
                setPadding(0, 0, 8, 0)
                setOnClickListener { showManageHabitDialog(i) }
            }
            row.addView(tvHabitTitle)

            for (d in 0 until 7) {
                val checked = days.getBoolean(d)
                val cell = TextView(this).apply {
                    text = if (checked) "✓" else "·"
                    gravity = Gravity.CENTER
                    setTextColor(if (checked) Color.parseColor("#064E3B") else Color.parseColor("#64748B"))
                    textSize = 13f
                    setTypeface(null, Typeface.BOLD)
                    background = if (checked) {
                        createGlassDrawable("#34D399", "#10B981", 14f)
                    } else {
                        createGlassDrawable("#1E293B", "#334155", 14f)
                    }
                    setPadding(0, 10, 0, 10)
                    val cp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.45f)
                    cp.setMargins(3, 0, 3, 0)
                    layoutParams = cp
                    setOnClickListener {
                        val newState = !checked
                        days.put(d, newState)
                        prefs.edit().putString("habits_matrix_json", habitsArray.toString()).apply()
                        if (newState) {
                            addXp(20, "Habit Checked! +20 XP")
                        } else {
                            addXp(-20, "Checkmark Removed (-20 XP)")
                        }
                        refreshAllUI()
                    }
                }
                row.addView(cell)
            }
            matrixCard.addView(row)
        }

        val habitActionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 12, 0, 0)
        }
        habitActionRow.addView(createPillButton("+ Add Custom Habit") { showAddHabitDialog() })
        habitActionRow.addView(createPillButton("Reset Week Checkmarks") {
            for (i in 0 until habitsArray.length()) {
                val dArr = habitsArray.getJSONObject(i).getJSONArray("days")
                for (d in 0 until 7) dArr.put(d, false)
            }
            prefs.edit().putString("habits_matrix_json", habitsArray.toString()).apply()
            refreshAllUI()
        })
        matrixCard.addView(habitActionRow)
        consistencyContentContainer.addView(matrixCard)

        val workoutCard = createGlassCard("#10192D", "#F59E0B")
        workoutCard.addView(createSectionLabel("🏋️ WORKOUT & PHYSICAL TRAINING TRACKER"))

        val workoutsDone = prefs.getInt("workouts_completed", 0)
        val workoutMins = prefs.getInt("workout_minutes", 0)

        val tvWorkoutStats = TextView(this).apply {
            text = "🔥 Total Workouts: $workoutsDone Sessions   •   ⏱️ Active Time: ${workoutMins}m"
            setTextColor(Color.parseColor("#FDE047"))
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 8, 0, 16)
        }

        val workoutQuickRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 12)
        }
        workoutQuickRow.addView(createPillButton("💪 45m Gym") {
            logWorkoutSession("Gym & Strength", 45, "Compound Lifts & Hypertrophy")
        })
        workoutQuickRow.addView(createPillButton("🏃 30m Cardio") {
            logWorkoutSession("Cardio / Running", 30, "Endurance & Conditioning")
        })
        workoutQuickRow.addView(createPillButton("🧘 20m Mobility") {
            logWorkoutSession("Calisthenics & Mobility", 20, "Core & Stretching")
        })

        val btnCustomWorkout = Button(this).apply {
            text = "+ Log Custom Workout (Exercise, Duration, Sets/Reps)"
            isAllCaps = false
            setTextColor(Color.WHITE)
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            background = createGlassDrawable("#B45309", "#F59E0B", 26f)
            val p = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            p.setMargins(0, 4, 0, 20)
            layoutParams = p
            setOnClickListener { showCustomWorkoutDialog() }
        }

        workoutCard.addView(tvWorkoutStats)
        workoutCard.addView(workoutQuickRow)
        workoutCard.addView(btnCustomWorkout)

        val workoutLogs = JSONArray(prefs.getString("workout_logs_json", "[]"))
        if (workoutLogs.length() > 0) {
            val tvHistoryHeader = TextView(this).apply {
                text = "RECENT WORKOUT LOGS (TAP ✕ TO UNDO / CANCEL MISTAKES)"
                setTextColor(Color.parseColor("#94A3B8"))
                textSize = 11f
                setTypeface(null, Typeface.BOLD)
                setPadding(0, 4, 0, 12)
            }
            workoutCard.addView(tvHistoryHeader)

            for (w in workoutLogs.length() - 1 downTo 0) {
                val wObj = workoutLogs.getJSONObject(w)
                val wType = wObj.getString("type")
                val wMins = wObj.getInt("mins")
                val wNotes = wObj.optString("notes", "")
                val wTime = wObj.optString("time", "Logged")

                val logRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    background = createGlassDrawable("#090F1D", "#1E293B", 20f)
                    setPadding(24, 18, 20, 18)
                    val p = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    p.setMargins(0, 0, 0, 10)
                    layoutParams = p
                }

                val tvLogInfo = TextView(this).apply {
                    text = "💪 $wType • ${wMins}m ($wTime)\n" +
                           (if (wNotes.isNotEmpty()) "📝 $wNotes" else "⚡ +60 XP Earned")
                    setTextColor(Color.parseColor("#E2E8F0"))
                    textSize = 12.5f
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }

                val btnUndoWorkout = TextView(this).apply {
                    text = "✕ Undo"
                    setTextColor(Color.parseColor("#FCA5A5"))
                    textSize = 12f
                    setTypeface(null, Typeface.BOLD)
                    background = createGlassDrawable("#450A0A", "#EF4444", 18f)
                    setPadding(22, 12, 22, 12)
                    setOnClickListener {
                        workoutLogs.remove(w)
                        val updatedCount = (prefs.getInt("workouts_completed", 1) - 1).coerceAtLeast(0)
                        val updatedMins = (prefs.getInt("workout_minutes", wMins) - wMins).coerceAtLeast(0)
                        prefs.edit()
                            .putString("workout_logs_json", workoutLogs.toString())
                            .putInt("workouts_completed", updatedCount)
                            .putInt("workout_minutes", updatedMins)
                            .apply()
                        addXp(-60, "Workout Undone (-${wMins}m & -60 XP)")
                        refreshAllUI()
                    }
                }

                logRow.addView(tvLogInfo)
                logRow.addView(btnUndoWorkout)
                workoutCard.addView(logRow)
            }
        }

        consistencyContentContainer.addView(workoutCard)
    }

    private fun logWorkoutSession(type: String, mins: Int, notes: String) {
        val count = prefs.getInt("workouts_completed", 0) + 1
        val totalMins = prefs.getInt("workout_minutes", 0) + mins
        val timeStamp = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date())

        val logs = JSONArray(prefs.getString("workout_logs_json", "[]"))
        logs.put(
            JSONObject()
                .put("type", type)
                .put("mins", mins)
                .put("notes", notes)
                .put("time", timeStamp)
        )

        prefs.edit()
            .putInt("workouts_completed", count)
            .putInt("workout_minutes", totalMins)
            .putString("workout_logs_json", logs.toString())
            .apply()
        addXp(60, "💪 $type (${mins}m) Logged! +60 XP")
        refreshAllUI()
    }

    private fun showCustomWorkoutDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(56, 36, 56, 20)
        }
        val inputType = EditText(this).apply {
            hint = "Workout Name (e.g., Push Day / Morning Run / Yoga)"
        }
        val inputMins = EditText(this).apply {
            hint = "Duration in Minutes (e.g., 60)"
            setRawInputType(InputType.TYPE_CLASS_NUMBER)
            setText("45")
        }
        val inputNotes = EditText(this).apply {
            hint = "Sets / Reps / Notes (e.g., Bench Press 4x10, Squats 4x8)"
        }
        layout.addView(inputType)
        layout.addView(inputMins)
        layout.addView(inputNotes)

        AlertDialog.Builder(this)
            .setTitle("🏋️ Log Custom Workout")
            .setView(layout)
            .setPositiveButton("Log Workout") { _, _ ->
                val type = inputType.text.toString().trim().ifEmpty { "Custom Workout" }
                val mins = inputMins.text.toString().toIntOrNull()?.coerceIn(1, 360) ?: 30
                val notes = inputNotes.text.toString().trim()
                logWorkoutSession(type, mins, notes)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAddHabitDialog() {
        val input = EditText(this).apply {
            hint = "e.g., Solve 10 Practical Accounts Questions / 8 Hrs Sleep"
            setRawInputType(InputType.TYPE_CLASS_TEXT)
            setPadding(56, 40, 56, 40)
        }
        AlertDialog.Builder(this)
            .setTitle("➕ Add Daily Consistency Habit")
            .setView(input)
            .setPositiveButton("Add") { _, _ ->
                val title = input.text.toString().trim()
                if (title.isNotEmpty()) {
                    val arr = JSONArray(prefs.getString("habits_matrix_json", "[]"))
                    val days = JSONArray(listOf(false, false, false, false, false, false, false))
                    arr.put(JSONObject().put("title", title).put("days", days))
                    prefs.edit().putString("habits_matrix_json", arr.toString()).apply()
                    refreshAllUI()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showManageHabitDialog(habitIndex: Int) {
        val arr = JSONArray(prefs.getString("habits_matrix_json", "[]"))
        val habitObj = arr.getJSONObject(habitIndex)
        val currentTitle = habitObj.getString("title")

        val input = EditText(this).apply {
            setText(currentTitle)
            setPadding(56, 40, 56, 40)
        }

        AlertDialog.Builder(this)
            .setTitle("⚙️ Edit or Remove Habit")
            .setView(input)
            .setPositiveButton("Save Name") { _, _ ->
                val updated = input.text.toString().trim()
                if (updated.isNotEmpty()) {
                    habitObj.put("title", updated)
                    prefs.edit().putString("habits_matrix_json", arr.toString()).apply()
                    refreshAllUI()
                }
            }
            .setNeutralButton("🗑️ Delete Habit") { _, _ ->
                arr.remove(habitIndex)
                prefs.edit().putString("habits_matrix_json", arr.toString()).apply()
                Toast.makeText(this, "Habit removed", Toast.LENGTH_SHORT).show()
                refreshAllUI()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ====================================================
    // TAB 4: SMART APP & YOUTUBE SHORTS SHIELD
    // ====================================================
    private fun buildShieldTab() {
        val shieldCard = createGlassCard("#10192D", "#2A4365")
        shieldCard.addView(createSectionLabel("REGAIN SMART APP & YOUTUBE SHORTS SHIELD"))

        var isBlockerEnabled = prefs.getBoolean("blocker_enabled", false)

        btnBlockerToggle = Button(this).apply {
            isAllCaps = false
            updateBlockerButtonStyle(this, isBlockerEnabled)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, 14, 0, 22)
            layoutParams = params
            setOnClickListener {
                if (isBlockerEnabled && isTimerRunning && cbStrictLock.isChecked) {
                    Toast.makeText(
                        this@MainActivity,
                        "🔒 Strict Lock is ON! Finish your focus session first.",
                        Toast.LENGTH_LONG
                    ).show()
                    return@setOnClickListener
                }
                isBlockerEnabled = !isBlockerEnabled
                prefs.edit().putBoolean("blocker_enabled", isBlockerEnabled).apply()
                updateBlockerButtonStyle(this, isBlockerEnabled)
            }
        }
        shieldCard.addView(btnBlockerToggle)

        val shieldRules = listOf(
            Triple("block_shorts", "Block YouTube Shorts (Allow CA/B.Com Study Lectures)", true),
            Triple("block_insta", "Block Instagram & Reels Completely", true),
            Triple("block_social_games", "Block Social Apps (FB, Snapchat, X, TikTok)", true),
            Triple("block_full_yt", "Nuclear Mode: Block Entire YouTube App", false)
        )

        shieldRules.forEach { (key, label, defaultVal) ->
            val cb = CheckBox(this).apply {
                text = label
                setTextColor(Color.WHITE)
                textSize = 13.5f
                isChecked = prefs.getBoolean(key, defaultVal)
                setPadding(14, 14, 14, 14)
                setOnCheckedChangeListener { _, checked ->
                    prefs.edit().putBoolean(key, checked).apply()
                }
            }
            shieldCard.addView(cb)
        }

        val btnOpenAcc = Button(this).apply {
            text = "⚙️ 1. Open Accessibility Settings"
            isAllCaps = false
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            background = createGlassDrawable("#1E293B", "#38BDF8", 26f)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, 22, 0, 14)
            layoutParams = params
            setOnClickListener { openAccessibilitySettings() }
        }

        val btnOpenAppInfo = Button(this).apply {
            text = "🔓 2. iQOO Z9 Android 14: Allow Restricted Settings"
            isAllCaps = false
            setTextColor(Color.parseColor("#FDE047"))
            setTypeface(null, Typeface.BOLD)
            background = createGlassDrawable("#1E293B", "#FACC15", 26f)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams = params
            setOnClickListener {
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", packageName, null)
                    }
                    startActivity(intent)
                    Toast.makeText(
                        this@MainActivity,
                        "Tap ⋮ (top-right) -> 'Allow restricted settings', and set Battery to Unrestricted!",
                        Toast.LENGTH_LONG
                    ).show()
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "Could not open App Info", Toast.LENGTH_SHORT).show()
                }
            }
        }

        shieldCard.addView(btnOpenAcc)
        shieldCard.addView(btnOpenAppInfo)
        tabShieldContainer.addView(shieldCard)
    }

    // ====================================================
    // DEFAULT DATA INITIALIZATION & STATE REFRESH
    // ====================================================
    private fun ensureDefaultDataInitialized() {
        if (!prefs.contains("syllabus_json")) {
            val defaultSubjects = JSONArray().apply {
                put(createSubjectPack("CA Found: Accounting", "CA Foundation", listOf(
                    "Mod 1 • Unit 1 • Theoretical Framework & Accounting Process",
                    "Mod 1 • Unit 2 • Bank Reconciliation Statement & Inventories",
                    "Mod 2 • Unit 3 • Depreciation, Bills of Exchange & Final Accounts",
                    "Mod 3 • Unit 4 • Partnership & Company Accounts"
                )))
                put(createSubjectPack("CA Found: Business Laws", "CA Foundation", listOf(
                    "Mod 1 • Unit 1 • Indian Regulatory Framework & Contract Act, 1872",
                    "Mod 2 • Unit 2 • Sale of Goods Act, 1930",
                    "Mod 2 • Unit 3 • Indian Partnership Act, 1932 & LLP Act",
                    "Mod 3 • Unit 4 • Companies Act, 2013 & Negotiable Instruments"
                )))
                put(createSubjectPack("CA Found: Quantitative Aptitude", "CA Foundation", listOf(
                    "Part A • Business Mathematics & Time Value of Money",
                    "Part B • Logical Reasoning (Number Series, Coding, Seating)",
                    "Part C • Statistics (Central Tendency, Dispersion, Probability)"
                )))
                put(createSubjectPack("CA Found: Business Economics", "CA Foundation", listOf(
                    "Ch 1 • Nature & Scope of Business Economics",
                    "Ch 2 • Theory of Demand, Supply & Consumer Behaviour",
                    "Ch 3 • Price Determination in Different Markets & Macro"
                )))
                put(createSubjectPack("B.Com: Financial Accounting & Mgmt", "B.Com", listOf(
                    "Unit 1 • Accounting Standards & Single/Double Entry",
                    "Unit 2 • Hire Purchase, Inland Branches & Dissolution",
                    "Unit 3 • Principles of Business Management"
                )))
            }
            prefs.edit().putString("syllabus_json", defaultSubjects.toString()).apply()
        }

        if (!prefs.contains("habits_matrix_json")) {
            val defaultHabits = JSONArray().apply {
                val emptyWeek = listOf(false, false, false, false, false, false, false)
                put(JSONObject().put("title", "📚 3+ Hrs CA / B.Com Deep Study").put("days", JSONArray(emptyWeek)))
                put(JSONObject().put("title", "⚖️ Revise Business Law / Audit").put("days", JSONArray(emptyWeek)))
                put(JSONObject().put("title", "📊 Solve Practical Accounts Problems").put("days", JSONArray(emptyWeek)))
                put(JSONObject().put("title", "🏋️ Workout / Physical Training").put("days", JSONArray(emptyWeek)))
                put(JSONObject().put("title", "🛡️ Zero YouTube Shorts / Reels").put("days", JSONArray(emptyWeek)))
            }
            prefs.edit().putString("habits_matrix_json", defaultHabits.toString()).apply()
        }
    }

    private fun refreshAllUI() {
        val sessions = prefs.getInt("sessions_completed", 0)
        val totalMinutes = prefs.getInt("total_minutes", 0)
        val blockedCount = prefs.getInt("distractions_blocked", 0)
        val workouts = prefs.getInt("workouts_completed", 0)
        val xp = prefs.getInt("hunter_xp", 0).coerceAtLeast(0)

        val level = (xp / 500) + 1
        val xpInLevel = xp % 500
        val rank = when {
            level >= 10 -> "S-Rank Monarch 👑"
            level >= 7 -> "A-Rank Elite 🔥"
            level >= 5 -> "B-Rank Scholar ⚔️"
            level >= 3 -> "C-Rank Striker 🛡️"
            level >= 2 -> "D-Rank Challenger ⚡"
            else -> "E-Rank Hunter 🌱"
        }

        tvHunterBadge.text = "Lv.$level • $rank"
        tvXpSub.text = "Hunter XP: $xp  •  Next Rank Progress: $xpInLevel / 500 XP"
        xpProgressBar.progress = xpInLevel

        val studyGoalPct = ((totalMinutes * 100) / 180).coerceAtMost(100)
        focusRingView.setProgress(studyGoalPct, "${totalMinutes}m / 180m")

        val habitsArray = JSONArray(prefs.getString("habits_matrix_json", "[]"))
        val todayIndex = ((Calendar.getInstance().get(Calendar.DAY_OF_WEEK) + 5) % 7)
        var todayDone = 0
        for (i in 0 until habitsArray.length()) {
            if (habitsArray.getJSONObject(i).getJSONArray("days").getBoolean(todayIndex)) {
                todayDone++
            }
        }
        val habitPct = if (habitsArray.length() > 0) (todayDone * 100) / habitsArray.length() else 0
        habitRingView.setProgress(habitPct, "$todayDone/${habitsArray.length()} Done")

        tvFocusMetricSummary.text =
            "⏱️ Study: ${totalMinutes / 60}h ${totalMinutes % 60}m ($sessions blocks)   •   " +
            "🛡️ Blocked: $blockedCount   •   🏋️ Workouts: $workouts"

        val isServiceEnabled = isAccessibilityServiceEnabled()
        if (isServiceEnabled) {
            tvAccessibilityBanner.text = "🟢 Regain Accessibility Shield: ACTIVE & PROTECTING"
            tvAccessibilityBanner.setTextColor(Color.parseColor("#6EE7B7"))
            tvAccessibilityBanner.background = createGlassDrawable("#064E3B", "#10B981", 24f)
        } else {
            tvAccessibilityBanner.text = "🔴 Shield Offline: Tap to Enable in Accessibility Settings"
            tvAccessibilityBanner.setTextColor(Color.parseColor("#FCA5A5"))
            tvAccessibilityBanner.background = createGlassDrawable("#7F1D1D", "#EF4444", 24f)
        }

        renderSyllabusCards()
        renderConsistencyTab()
    }

    private fun addXp(amount: Int, toastMsg: String) {
        val newXp = (prefs.getInt("hunter_xp", 0) + amount).coerceAtLeast(0)
        prefs.edit().putInt("hunter_xp", newXp).apply()
        Toast.makeText(this, "⚡ $toastMsg", Toast.LENGTH_SHORT).show()
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
            .putInt("last_logged_mins", addedMins)
            .putString("last_logged_subj", selectedSubject)
            .apply()

        refreshAllUI()
        Toast.makeText(
            this,
            "🎉 +${addedMins}m logged for $selectedSubject! (+$earnedXp XP)",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun undoLastStudySession() {
        val lastMins = prefs.getInt("last_logged_mins", 0)
        val lastSubj = prefs.getString("last_logged_subj", "") ?: ""
        if (lastMins <= 0 || lastSubj.isEmpty()) {
            Toast.makeText(this, "No recent study block to undo.", Toast.LENGTH_SHORT).show()
            return
        }
        val sessions = (prefs.getInt("sessions_completed", 1) - 1).coerceAtLeast(0)
        val totalMinutes = (prefs.getInt("total_minutes", lastMins) - lastMins).coerceAtLeast(0)
        val subjKey = "subj_$lastSubj"
        val subjMins = (prefs.getInt(subjKey, lastMins) - lastMins).coerceAtLeast(0)
        val deductedXp = lastMins * 5
        val newXp = (prefs.getInt("hunter_xp", deductedXp) - deductedXp).coerceAtLeast(0)

        prefs.edit()
            .putInt("sessions_completed", sessions)
            .putInt("total_minutes", totalMinutes)
            .putInt(subjKey, subjMins)
            .putInt("hunter_xp", newXp)
            .putInt("last_logged_mins", 0)
            .apply()

        refreshAllUI()
        Toast.makeText(this, "↩️ Undid ${lastMins}m session for $lastSubj (-$deductedXp XP)", Toast.LENGTH_SHORT).show()
    }

    private fun setTimerPreset(durationMillis: Long, label: String) {
        if (isTimerRunning && cbStrictLock.isChecked) {
            Toast.makeText(this, "🔒 Strict Mode Active! Finish current session first.", Toast.LENGTH_SHORT).show()
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
        tvTimerStatus.text = "🔥 Focusing on $selectedSubject — Shield Locked"
        btnStartPause.text = "Pause"
    }

    private fun pauseTimer() {
        countDownTimer?.cancel()
        isTimerRunning = false
        tvTimerStatus.text = "Paused • $selectedSubject"
        btnStartPause.text = "Resume"
    }

    private fun resetTimer() {
        countDownTimer?.cancel()
        isTimerRunning = false
        timeLeftInMillis = initialTimeMillis
        tvTimerDisplay.text = formatTime(timeLeftInMillis)
        tvTimerStatus.text = "Ready • $selectedSubject"
        btnStartPause.text = "Start Focus"
    }

    private fun updateBlockerButtonStyle(button: Button, enabled: Boolean) {
        if (enabled) {
            button.text = "🛡️ REGAIN APP & SHORTS SHIELD: ACTIVE"
            button.background = createGlassDrawable("#065F46", "#10B981", 28f)
        } else {
            button.text = "⚠️ REGAIN APP & SHORTS SHIELD: OFF (Tap to Enable)"
            button.background = createGlassDrawable("#7F1D1D", "#EF4444", 28f)
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
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        }
    }

    private fun createSectionLabel(title: String): TextView {
        return TextView(this).apply {
            text = title
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 11.5f
            setTypeface(null, Typeface.BOLD)
            letterSpacing = 0.08f
            setPadding(0, 0, 0, 12)
        }
    }

    private fun createGlassCard(fillHex: String, strokeHex: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = createGlassDrawable(fillHex, strokeHex, 38f)
            setPadding(42, 42, 42, 42)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, 0, 0, 36)
            layoutParams = params
        }
    }

    private fun createGlassDrawable(fillHex: String, strokeHex: String, radius: Float): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(Color.parseColor(fillHex))
            setStroke(3, Color.parseColor(strokeHex))
        }
    }

    private fun createPillButton(label: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            isAllCaps = false
            setTextColor(Color.parseColor("#E2E8F0"))
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            background = createGlassDrawable("#1E293B", "#334155", 26f)
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            params.setMargins(6, 0, 6, 0)
            layoutParams = params
            setOnClickListener { onClick() }
        }
    }

    private fun createActionButton(label: String, fillHex: String, strokeHex: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            isAllCaps = false
            setTextColor(Color.WHITE)
            textSize = 14.5f
            setTypeface(null, Typeface.BOLD)
            background = createGlassDrawable(fillHex, strokeHex, 28f)
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            params.setMargins(8, 0, 8, 0)
            layoutParams = params
            setOnClickListener { onClick() }
        }
    }

    class CircularRingView(
        context: Context,
        private val accentHex: String,
        private val label: String
    ) : View(context) {
        private var percentage = 0
        private var subLabel = "0%"
        private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 24f
            color = Color.parseColor("#1E293B")
            strokeCap = Paint.Cap.ROUND
        }
        private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 24f
            color = Color.parseColor(accentHex)
            strokeCap = Paint.Cap.ROUND
        }
        private val pctPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 52f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#94A3B8")
            textSize = 26f
            textAlign = Paint.Align.CENTER
        }
        private val rect = RectF()

        fun setProgress(pct: Int, detail: String) {
            percentage = pct.coerceIn(0, 100)
            subLabel = detail
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val pad = 32f
            rect.set(pad, pad, width - pad, height - pad)
            canvas.drawArc(rect, 0f, 360f, false, trackPaint)
            val sweep = (percentage / 100f) * 360f
            canvas.drawArc(rect, -90f, sweep, false, progressPaint)

            val cx = width / 2f
            val cy = height / 2f
            canvas.drawText("$percentage%", cx, cy - 4f, pctPaint)
            canvas.drawText(label, cx, cy + 34f, labelPaint)
            canvas.drawText(subLabel, cx, cy + 66f, labelPaint)
        }
    }

    class WeeklyBarChartView(
        context: Context,
        private val dayScores: IntArray
    ) : View(context) {
        private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1E293B")
        }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#CBD5E1")
            textSize = 26f
            textAlign = Paint.Align.CENTER
        }
        private val days = arrayOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        private val colors = arrayOf(
            "#38BDF8", "#38BDF8", "#818CF8", "#A78BFA", "#F472B6", "#34D399", "#10B981"
        )
        private val rect = RectF()

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (width == 0 || height == 0) return
            val colWidth = width / 7f
            val maxBarHeight = height - 75f

            for (i in 0 until 7) {
                val cx = colWidth * i + colWidth / 2f
                val barW = colWidth * 0.42f
                val topTrack = 28f
                val bottomTrack = topTrack + maxBarHeight

                rect.set(cx - barW / 2f, topTrack, cx + barW / 2f, bottomTrack)
                canvas.drawRoundRect(rect, 14f, 14f, bgPaint)

                val pct = dayScores.getOrElse(i) { 0 }.coerceIn(0, 100)
                val fillH = (pct / 100f) * maxBarHeight
                barPaint.color = Color.parseColor(colors[i])
                rect.set(cx - barW / 2f, bottomTrack - fillH, cx + barW / 2f, bottomTrack)
                canvas.drawRoundRect(rect, 14f, 14f, barPaint)

                canvas.drawText("${pct}%", cx, 22f, textPaint)
                canvas.drawText(days[i], cx, height - 10f, textPaint)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }
}
