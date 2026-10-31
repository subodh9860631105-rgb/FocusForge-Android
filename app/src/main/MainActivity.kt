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
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private var countDownTimer: CountDownTimer? = null
    private var isTimerRunning = false
    private var initialTimeMillis: Long = 25 * 60 * 1000L
    private var timeLeftInMillis: Long = initialTimeMillis
    private var selectedSubject = "CA: Business Law"
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
            setBackgroundColor(Color.parseColor("#070B14"))
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
            setPadding(36, 56, 36, 48)
        }

        // ==========================================
        // TOP GLASS HEADER (HUNTER RANK + SHIELD STATUS)
        // ==========================================
        val headerCard = createGlassCard("#11192C", "#243556")
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
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
        }

        val tvRoleTag = TextView(this).apply {
            text = "CA & B.Com Edition • Regain Focus & Consistency"
            setTextColor(Color.parseColor("#38BDF8"))
            textSize = 11.5f
            setPadding(0, 4, 0, 0)
        }

        titleCol.addView(tvAppName)
        titleCol.addView(tvRoleTag)

        tvHunterBadge = TextView(this).apply {
            setTextColor(Color.parseColor("#FDE047"))
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            background = createGlassDrawable("#1E293B", "#FACC15", 40f)
            setPadding(24, 12, 24, 12)
        }

        titleRow.addView(titleCol)
        titleRow.addView(tvHunterBadge)

        tvXpSub = TextView(this).apply {
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 12f
            setPadding(0, 18, 0, 8)
        }

        xpProgressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 500
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 14)
        }

        tvAccessibilityBanner = TextView(this).apply {
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setPadding(28, 18, 28, 18)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, 20, 0, 0)
            layoutParams = params
            setOnClickListener { openAccessibilitySettings() }
        }

        headerCard.addView(titleRow)
        headerCard.addView(tvXpSub)
        headerCard.addView(xpProgressBar)
        headerCard.addView(tvAccessibilityBanner)
        rootContent.addView(headerCard)

        // ==========================================
        // 4 TAB CONTAINERS
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
            background = createGlassDrawable("#0F172A", "#1E293B", 0f)
            setPadding(16, 16, 16, 20)
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
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                params.setMargins(6, 0, 6, 0)
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
                btn.background = createGlassDrawable("#1E293B", "#38BDF8", 30f)
                btn.setTextColor(Color.parseColor("#38BDF8"))
            } else {
                btn.background = createGlassDrawable("#0F172A", "#0F172A", 30f)
                btn.setTextColor(Color.parseColor("#94A3B8"))
            }
        }
        refreshAllUI()
    }

    // ====================================================
    // TAB 1: REGAIN-STYLE FOCUS RINGS & DEEP WORK TIMER
    // ====================================================
    private fun buildFocusTab() {
        val ringCard = createGlassCard("#11192C", "#1E3A5F")
        ringCard.addView(createSectionLabel("DAILY FOCUS & CONSISTENCY RINGS"))

        val ringsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 16)
        }

        focusRingView = CircularRingView(this, "#38BDF8", "Study Goal").apply {
            layoutParams = LinearLayout.LayoutParams(340, 340).apply { setMargins(16, 0, 16, 0) }
        }
        habitRingView = CircularRingView(this, "#10B981", "Daily Habits").apply {
            layoutParams = LinearLayout.LayoutParams(340, 340).apply { setMargins(16, 0, 16, 0) }
        }

        ringsRow.addView(focusRingView)
        ringsRow.addView(habitRingView)

        tvFocusMetricSummary = TextView(this).apply {
            setTextColor(Color.parseColor("#E2E8F0"))
            textSize = 13f
            gravity = Gravity.CENTER
            background = createGlassDrawable("#0B1120", "#1E293B", 24f)
            setPadding(24, 20, 24, 20)
        }

        ringCard.addView(ringsRow)
        ringCard.addView(tvFocusMetricSummary)
        tabFocusContainer.addView(ringCard)

        val timerCard = createGlassCard("#11192C", "#243556")
        timerCard.addView(createSectionLabel("POMODORO & CA DEEP WORK TIMER"))

        val chipScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            setPadding(0, 12, 0, 8)
        }
        subjectChipRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        chipScroll.addView(subjectChipRow)

        tvTimerDisplay = TextView(this).apply {
            text = formatTime(timeLeftInMillis)
            setTextColor(Color.parseColor("#F8FAFC"))
            textSize = 58f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 0)
        }

        tvTimerStatus = TextView(this).apply {
            text = "Active: $selectedSubject • 25m Focus"
            setTextColor(Color.parseColor("#38BDF8"))
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 20)
        }

        val presetRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16)
        }
        presetRow.addView(createPillButton("25m Pomodoro") {
            setTimerPreset(25 * 60 * 1000L, "25m Pomodoro • $selectedSubject")
        })
        presetRow.addView(createPillButton("50m CA Prep") {
            setTimerPreset(50 * 60 * 1000L, "50m Deep Study • $selectedSubject")
        })
        presetRow.addView(createPillButton("90m Mock") {
            setTimerPreset(90 * 60 * 1000L, "90m Exam Block • $selectedSubject")
        })
        presetRow.addView(createPillButton("5m Break") {
            setTimerPreset(5 * 60 * 1000L, "5m Rest Break")
        })

        cbStrictLock = CheckBox(this).apply {
            text = "🔒 Regain Strict Mode (Lock Shield until timer completes)"
            setTextColor(Color.parseColor("#FDE047"))
            textSize = 12.5f
            isChecked = prefs.getBoolean("strict_lock_mode", true)
            setPadding(12, 10, 12, 16)
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean("strict_lock_mode", checked).apply()
            }
        }

        val controlRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        btnStartPause = createActionButton("Start Focus Session", "#4F46E5", "#818CF8") {
            if (isTimerRunning) pauseTimer() else startTimer()
        }

        val btnReset = createActionButton("Reset", "#1E293B", "#334155") {
            if (isTimerRunning && cbStrictLock.isChecked) {
                Toast.makeText(this, "🔒 Strict Mode active! Pause or finish session.", Toast.LENGTH_SHORT).show()
            } else {
                resetTimer()
            }
        }

        controlRow.addView(btnStartPause)
        controlRow.addView(btnReset)

        val btnQuickLog = Button(this).apply {
            text = "✓ Log Completed Study Block (+XP & Subject Minutes)"
            isAllCaps = false
            setTextColor(Color.parseColor("#6EE7B7"))
            textSize = 12.5f
            background = createGlassDrawable("#064E3B", "#10B981", 26f)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, 18, 0, 0)
            layoutParams = params
            setOnClickListener { recordCompletedSession() }
        }

        timerCard.addView(chipScroll)
        timerCard.addView(tvTimerDisplay)
        timerCard.addView(tvTimerStatus)
        timerCard.addView(presetRow)
        timerCard.addView(cbStrictLock)
        timerCard.addView(controlRow)
        timerCard.addView(btnQuickLog)
        tabFocusContainer.addView(timerCard)
    }

    // ====================================================
    // TAB 2: CA & B.COM SUBJECT, MODULE & UNIT TRACKER
    // ====================================================
    private fun buildSyllabusTab() {
        val topHeaderCard = createGlassCard("#11192C", "#243556")
        topHeaderCard.addView(createSectionLabel("CA & B.COM 1ST YEAR SYLLABUS TRACKER"))

        val tvDesc = TextView(this).apply {
            text = "Track Subjects, Modules, Lessons & Units. Tap any unit to cycle status (⬜ Todo → ⏳ In Progress → ✅ Completed +35 XP)."
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 12.5f
            setPadding(0, 4, 0, 16)
        }

        val btnAddSubject = Button(this).apply {
            text = "+ Add New Subject (CA / B.Com / Skill)"
            isAllCaps = false
            setTextColor(Color.WHITE)
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            background = createGlassDrawable("#0284C7", "#38BDF8", 28f)
            setOnClickListener { showAddSubjectDialog() }
        }

        topHeaderCard.addView(tvDesc)
        topHeaderCard.addView(btnAddSubject)
        tabSyllabusContainer.addView(topHeaderCard)

        syllabusListContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        tabSyllabusContainer.addView(syllabusListContainer)
    }

    private fun renderSyllabusCards() {
        syllabusListContainer.removeAllViews()
        subjectChipRow.removeAllViews()

        val subjectsArray = JSONArray(prefs.getString("syllabus_json", "[]"))

        for (i in 0 until subjectsArray.length()) {
            val subjObj = subjectsArray.getJSONObject(i)
            val subjName = subjObj.getString("name")
            val unitsArray = subjObj.getJSONArray("units")

            val chipBtn = Button(this).apply {
                text = subjName
                isAllCaps = false
                textSize = 11.5f
                setTextColor(Color.WHITE)
                val isSel = subjName == selectedSubject
                background = if (isSel) {
                    createGlassDrawable("#0284C7", "#38BDF8", 32f)
                } else {
                    createGlassDrawable("#1E293B", "#334155", 32f)
                }
                setPadding(28, 10, 28, 10)
                val p = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                p.setMargins(0, 0, 14, 0)
                layoutParams = p
                setOnClickListener {
                    selectedSubject = subjName
                    tvTimerStatus.text = "Active: $selectedSubject"
                    renderSyllabusCards()
                }
            }
            subjectChipRow.addView(chipBtn)

            val card = createGlassCard("#11192C", "#1E293B")
            var completedUnits = 0
            for (u in 0 until unitsArray.length()) {
                if (unitsArray.getJSONObject(u).getInt("status") == 2) completedUnits++
            }
            val totalUnits = unitsArray.length()
            val pct = if (totalUnits > 0) (completedUnits * 100) / totalUnits else 0
            val studiedMins = prefs.getInt("subj_$subjName", 0)

            val headerRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val tvSubjTitle = TextView(this).apply {
                text = "📘 $subjName"
                setTextColor(Color.parseColor("#F8FAFC"))
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val tvPctBadge = TextView(this).apply {
                text = "$completedUnits/$totalUnits Units ($pct%)"
                setTextColor(Color.parseColor("#38BDF8"))
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
            }

            headerRow.addView(tvSubjTitle)
            headerRow.addView(tvPctBadge)

            val tvTimeLogged = TextView(this).apply {
                text = "⏱️ Focus Time Logged: ${studiedMins / 60}h ${studiedMins % 60}m"
                setTextColor(Color.parseColor("#94A3B8"))
                textSize = 12f
                setPadding(0, 6, 0, 10)
            }

            val pb = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100
                progress = pct
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 14).apply {
                    setMargins(0, 0, 0, 18)
                }
            }

            card.addView(headerRow)
            card.addView(tvTimeLogged)
            card.addView(pb)

            for (u in 0 until unitsArray.length()) {
                val unitObj = unitsArray.getJSONObject(u)
                val unitTitle = unitObj.getString("title")
                val status = unitObj.getInt("status")

                val (icon, colorHex, bgHex) = when (status) {
                    2 -> Triple("✅", "#6EE7B7", "#064E3B")
                    1 -> Triple("⏳", "#FDE047", "#422006")
                    else -> Triple("⬜", "#CBD5E1", "#0B1120")
                }

                val unitRow = TextView(this).apply {
                    text = "$icon  $unitTitle"
                    setTextColor(Color.parseColor(colorHex))
                    textSize = 13f
                    background = createGlassDrawable(bgHex, "#1E293B", 20f)
                    setPadding(24, 18, 24, 18)
                    val p = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    p.setMargins(0, 0, 0, 10)
                    layoutParams = p
                    setOnClickListener {
                        val nextStatus = (status + 1) % 3
                        unitObj.put("status", nextStatus)
                        prefs.edit().putString("syllabus_json", subjectsArray.toString()).apply()
                        if (nextStatus == 2) {
                            addXp(35, "Unit Mastered! +35 XP")
                        }
                        renderSyllabusCards()
                    }
                    setOnLongClickListener {
                        unitsArray.remove(u)
                        prefs.edit().putString("syllabus_json", subjectsArray.toString()).apply()
                        Toast.makeText(this@MainActivity, "Unit removed", Toast.LENGTH_SHORT).show()
                        renderSyllabusCards()
                        true
                    }
                }
                card.addView(unitRow)
            }

            val actionRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 10, 0, 0)
            }

            val btnAddUnit = createPillButton("+ Add Module / Lesson / Unit") {
                showAddUnitDialog(i)
            }
            val btnDeleteSubj = createPillButton("🗑️ Remove Subject") {
                subjectsArray.remove(i)
                prefs.edit().putString("syllabus_json", subjectsArray.toString()).apply()
                renderSyllabusCards()
            }

            actionRow.addView(btnAddUnit)
            actionRow.addView(btnDeleteSubj)
            card.addView(actionRow)

            syllabusListContainer.addView(card)
        }
    }

    private fun showAddSubjectDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }
        val inputSubj = EditText(this).apply {
            hint = "e.g., B.Com Corporate Accounting or CA Quantitative Aptitude"
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val inputFirstUnit = EditText(this).apply {
            hint = "First Module/Unit (e.g., Module 1: Partnership Accounts)"
            inputType = InputType.TYPE_CLASS_TEXT
        }
        layout.addView(inputSubj)
        layout.addView(inputFirstUnit)

        AlertDialog.Builder(this)
            .setTitle("Add New Subject")
            .setView(layout)
            .setPositiveButton("Add Subject") { _, _ ->
                val name = inputSubj.text.toString().trim()
                val firstUnit = inputFirstUnit.text.toString().trim()
                if (name.isNotEmpty()) {
                    val arr = JSONArray(prefs.getString("syllabus_json", "[]"))
                    val units = JSONArray()
                    if (firstUnit.isNotEmpty()) {
                        units.put(JSONObject().put("title", firstUnit).put("status", 0))
                    } else {
                        units.put(JSONObject().put("title", "Module 1 • Unit 1: Core Concepts").put("status", 0))
                    }
                    arr.put(JSONObject().put("name", name).put("units", units))
                    prefs.edit().putString("syllabus_json", arr.toString()).apply()
                    selectedSubject = name
                    renderSyllabusCards()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAddUnitDialog(subjectIndex: Int) {
        val input = EditText(this).apply {
            hint = "e.g., Mod 2 • Lesson 3: Negotiable Instruments Act"
            inputType = InputType.TYPE_CLASS_TEXT
            setPadding(48, 36, 48, 36)
        }
        AlertDialog.Builder(this)
            .setTitle("Add Module / Lesson / Unit")
            .setView(input)
            .setPositiveButton("Add") { _, _ ->
                val title = input.text.toString().trim()
                if (title.isNotEmpty()) {
                    val arr = JSONArray(prefs.getString("syllabus_json", "[]"))
                    val subj = arr.getJSONObject(subjectIndex)
                    subj.getJSONArray("units").put(
                        JSONObject().put("title", title).put("status", 0)
                    )
                    prefs.edit().putString("syllabus_json", arr.toString()).apply()
                    renderSyllabusCards()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ====================================================
    // TAB 3: STREAKO-STYLE CONSISTENCY & WORKOUT MATRIX
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

        val bannerCard = createGlassCard("#11192C", "#10B981")
        val tvTrust = TextView(this).apply {
            text = "trust the process."
            setTextColor(Color.parseColor("#34D399"))
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
        }
        val tvAffirmation = TextView(this).apply {
            text = "\"Every checkmark builds momentum. See your CA study & workout consistency grow day by day.\""
            setTextColor(Color.parseColor("#CBD5E1"))
            textSize = 12.5f
            setPadding(0, 6, 0, 18)
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
            textSize = 12.5f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 12, 0, 0)
        }

        bannerCard.addView(tvTrust)
        bannerCard.addView(tvAffirmation)
        bannerCard.addView(barChart)
        bannerCard.addView(tvWeeklySummary)
        consistencyContentContainer.addView(bannerCard)

        val matrixCard = createGlassCard("#11192C", "#243556")
        matrixCard.addView(createSectionLabel("7-DAY HABIT & CONSISTENCY MATRIX (M - S)"))

        val dayLabels = listOf("M", "T", "W", "T", "F", "S", "S")
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 8, 0, 12)
        }
        val tvHabitColHeader = TextView(this).apply {
            text = "Daily Habit"
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.9f)
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
                background = createGlassDrawable("#0B1120", "#1E293B", 18f)
                setPadding(16, 14, 12, 14)
                val p = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                p.setMargins(0, 0, 0, 10)
                layoutParams = p
            }

            val tvHabitTitle = TextView(this).apply {
                text = title
                setTextColor(Color.WHITE)
                textSize = 12f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.9f)
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
                    cp.setMargins(4, 0, 4, 0)
                    layoutParams = cp
                    setOnClickListener {
                        val newState = !checked
                        days.put(d, newState)
                        prefs.edit().putString("habits_matrix_json", habitsArray.toString()).apply()
                        if (newState) addXp(20, "Momentum Checkmark! +20 XP")
                        refreshAllUI()
                    }
                }
                row.addView(cell)
            }
            matrixCard.addView(row)
        }

        val habitActionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 10, 0, 0)
        }
        habitActionRow.addView(createPillButton("+ Add Custom Habit") { showAddHabitDialog() })
        habitActionRow.addView(createPillButton("Reset Week Grid") {
            for (i in 0 until habitsArray.length()) {
                val dArr = habitsArray.getJSONObject(i).getJSONArray("days")
                for (d in 0 until 7) dArr.put(d, false)
            }
            prefs.edit().putString("habits_matrix_json", habitsArray.toString()).apply()
            refreshAllUI()
        })
        matrixCard.addView(habitActionRow)
        consistencyContentContainer.addView(matrixCard)

        val workoutCard = createGlassCard("#11192C", "#F59E0B")
        workoutCard.addView(createSectionLabel("🏋️ WORKOUT & PHYSICAL TRAINING TRACKER"))

        val workoutsDone = prefs.getInt("workouts_completed", 0)
        val workoutMins = prefs.getInt("workout_minutes", 0)

        val tvWorkoutStats = TextView(this).apply {
            text = "🔥 Total Workouts: $workoutsDone Sessions  •  ⏱️ Training Time: ${workoutMins}m"
            setTextColor(Color.parseColor("#FDE047"))
            textSize = 13.5f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 6, 0, 16)
        }

        val workoutBtnsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        workoutBtnsRow.addView(createPillButton("💪 45m Gym / Strength") {
            logWorkoutSession(45, "Gym & Strength Training")
        })
        workoutBtnsRow.addView(createPillButton("🏃 30m Cardio / Run") {
            logWorkoutSession(30, "Cardio & Endurance")
        })
        workoutBtnsRow.addView(createPillButton("🧘 15m Mobility") {
            logWorkoutSession(15, "Home Calisthenics / Stretch")
        })

        workoutCard.addView(tvWorkoutStats)
        workoutCard.addView(workoutBtnsRow)
        consistencyContentContainer.addView(workoutCard)
    }

    private fun logWorkoutSession(mins: Int, type: String) {
        val count = prefs.getInt("workouts_completed", 0) + 1
        val totalMins = prefs.getInt("workout_minutes", 0) + mins
        prefs.edit()
            .putInt("workouts_completed", count)
            .putInt("workout_minutes", totalMins)
            .apply()
        addXp(60, "💪 $type Logged (+60 XP)!")
        refreshAllUI()
    }

    private fun showAddHabitDialog() {
        val input = EditText(this).apply {
            hint = "e.g., Revise Accounting Standards / 10k Steps"
            inputType = InputType.TYPE_CLASS_TEXT
            setPadding(48, 36, 48, 36)
        }
        AlertDialog.Builder(this)
            .setTitle("Add Daily Consistency Habit")
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

    // ====================================================
    // TAB 4: SMART APP & YOUTUBE SHORTS SHIELD + iQOO GUIDE
    // ====================================================
    private fun buildShieldTab() {
        val shieldCard = createGlassCard("#11192C", "#243556")
        shieldCard.addView(createSectionLabel("REGAIN SMART APP & YOUTUBE SHORTS SHIELD"))

        var isBlockerEnabled = prefs.getBoolean("blocker_enabled", false)

        btnBlockerToggle = Button(this).apply {
            isAllCaps = false
            updateBlockerButtonStyle(this, isBlockerEnabled)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, 12, 0, 20)
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
            Triple("block_shorts", "Block YouTube Shorts (Allow CA/B.Com Lectures)", true),
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
                setPadding(12, 12, 12, 12)
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
            background = createGlassDrawable("#1E293B", "#38BDF8", 26f)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, 20, 0, 12)
            layoutParams = params
            setOnClickListener { openAccessibilitySettings() }
        }

        val btnOpenAppInfo = Button(this).apply {
            text = "🔓 2. iQOO Z9 Android 14: Allow Restricted Settings"
            isAllCaps = false
            setTextColor(Color.parseColor("#FDE047"))
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
    // DEFAULT DATA & STATE REFRESH
    // ====================================================
    private fun ensureDefaultDataInitialized() {
        if (!prefs.contains("syllabus_json")) {
            val defaultSubjects = JSONArray().apply {
                put(JSONObject().apply {
                    put("name", "CA: Business Law")
                    put("units", JSONArray().apply {
                        put(JSONObject().put("title", "Mod 1 • Indian Contract Act, 1872").put("status", 1))
                        put(JSONObject().put("title", "Mod 2 • Sale of Goods Act, 1930").put("status", 0))
                        put(JSONObject().put("title", "Mod 3 • Indian Partnership Act & LLP").put("status", 0))
                        put(JSONObject().put("title", "Mod 4 • Companies Act, 2013 Basics").put("status", 0))
                    })
                })
                put(JSONObject().apply {
                    put("name", "CA/B.Com: Accounting")
                    put("units", JSONArray().apply {
                        put(JSONObject().put("title", "Unit 1 • Accounting Process & BRS").put("status", 1))
                        put(JSONObject().put("title", "Unit 2 • Depreciation & Inventories").put("status", 0))
                        put(JSONObject().put("title", "Unit 3 • Final Accounts & NPO").put("status", 0))
                        put(JSONObject().put("title", "Unit 4 • Partnership & Company Accounts").put("status", 0))
                    })
                })
                put(JSONObject().apply {
                    put("name", "CA: Quant & Maths")
                    put("units", JSONArray().apply {
                        put(JSONObject().put("title", "Unit 1 • Time Value of Money").put("status", 0))
                        put(JSONObject().put("title", "Unit 2 • Logical Reasoning").put("status", 0))
                        put(JSONObject().put("title", "Unit 3 • Statistical Description & Central Tendency").put("status", 0))
                    })
                })
                put(JSONObject().apply {
                    put("name", "B.Com: Economics & Mgmt")
                    put("units", JSONArray().apply {
                        put(JSONObject().put("title", "Unit 1 • Demand, Supply & Consumer Behaviour").put("status", 0))
                        put(JSONObject().put("title", "Unit 2 • Production, Cost & Markets").put("status", 0))
                        put(JSONObject().put("title", "Unit 3 • Business Organization & Management").put("status", 0))
                    })
                })
            }
            prefs.edit().putString("syllabus_json", defaultSubjects.toString()).apply()
        }

        if (!prefs.contains("habits_matrix_json")) {
            val defaultHabits = JSONArray().apply {
                val emptyWeek = listOf(false, false, false, false, false, false, false)
                put(JSONObject().put("title", "📚 2+ Hrs CA/B.Com Deep Study").put("days", JSONArray(emptyWeek)))
                put(JSONObject().put("title", "⚖️ Revise Law / Practice Accounts").put("days", JSONArray(emptyWeek)))
                put(JSONObject().put("title", "🏋️ Workout / Physical Training").put("days", JSONArray(emptyWeek)))
                put(JSONObject().put("title", "🛡️ Zero Doomscrolling / Shorts").put("days", JSONArray(emptyWeek)))
                put(JSONObject().put("title", "🥗 Eat Clean & 3L Hydration").put("days", JSONArray(emptyWeek)))
            }
            prefs.edit().putString("habits_matrix_json", defaultHabits.toString()).apply()
        }
    }

    private fun refreshAllUI() {
        val sessions = prefs.getInt("sessions_completed", 0)
        val totalMinutes = prefs.getInt("total_minutes", 0)
        val blockedCount = prefs.getInt("distractions_blocked", 0)
        val workouts = prefs.getInt("workouts_completed", 0)
        val xp = prefs.getInt("hunter_xp", 0)

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
        tvXpSub.text = "Hunter XP: $xp  •  Next Rank: $xpInLevel / 500 XP"
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
        val newXp = prefs.getInt("hunter_xp", 0) + amount
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
            .apply()

        refreshAllUI()
        Toast.makeText(
            this,
            "🎉 +${addedMins}m logged for $selectedSubject! (+$earnedXp XP)",
            Toast.LENGTH_LONG
        ).show()
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
        btnStartPause.text = "Start Focus Session"
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
                btnStartPause.text = "Start Focus Session"
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
        btnStartPause.text = "Start Focus Session"
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
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }

    private fun createSectionLabel(title: String): TextView {
        return TextView(this).apply {
            text = title
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 11.5f
            setTypeface(null, Typeface.BOLD)
            letterSpacing = 0.08f
            setPadding(0, 0, 0, 10)
        }
    }

    private fun createGlassCard(fillHex: String, strokeHex: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = createGlassDrawable(fillHex, strokeHex, 36f)
            setPadding(36, 36, 36, 36)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, 0, 0, 32)
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
            textSize = 11.5f
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
            textSize = 14f
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
