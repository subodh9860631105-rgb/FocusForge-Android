package com.focusforge.pro

import android.animation.ValueAnimator
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
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
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
import kotlin.math.sin

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private var countDownTimer: CountDownTimer? = null
    private var isTimerRunning = false
    private var initialTimeMillis: Long = 45 * 60 * 1000L
    private var timeLeftInMillis: Long = initialTimeMillis
    private var selectedSubject = "CA Found: Business Laws"
    private var selectedCourseFilter = "All"
    private var selectedMuscleFilter = "All"
    private var selectedEquipmentFilter = "All"
    private var currentTab = 0

    private lateinit var tabFocusContainer: LinearLayout
    private lateinit var tabSyllabusContainer: LinearLayout
    private lateinit var tabConsistencyContainer: LinearLayout
    private lateinit var tabWorkoutContainer: LinearLayout
    private lateinit var tabShieldContainer: LinearLayout
    private val navButtons = mutableListOf<Button>()

    private lateinit var tvHunterBadge: TextView
    private lateinit var tvXpSub: TextView
    private lateinit var xpProgressBar: ProgressBar
    private lateinit var tvAccessibilityBanner: TextView
    private lateinit var tvExamCountdown: TextView
    private lateinit var focusRingView: CircularRingView
    private lateinit var habitRingView: CircularRingView
    private lateinit var tvFocusMetricSummary: TextView
    private lateinit var tvTimerDisplay: TextView
    private lateinit var tvTimerStatus: TextView
    private lateinit var btnStartPause: Button
    private lateinit var cbStrictLock: CheckBox
    private lateinit var subjectChipRow: LinearLayout

    private lateinit var courseFilterRow: LinearLayout
    private lateinit var syllabusListContainer: LinearLayout
    private lateinit var consistencyContentContainer: LinearLayout
    private lateinit var workoutContentContainer: LinearLayout
    private lateinit var btnBlockerToggle: Button

    data class ExerciseMeta(
        val id: String,
        val name: String,
        val muscle: String,
        val secondaryMuscles: String,
        val equipment: String,
        val animType: Int,
        val howToSteps: String
    )

    private val exerciseCatalog = listOf(
        ExerciseMeta(
            "row_bb", "Bent-Over Row • Barbell", "Back", "Back • Arms • Core", "Barbell", 1,
            "1. Stand with feet shoulder-width apart, knees slightly bent.\n2. Hinge forward at hips keeping back flat.\n3. Pull barbell toward lower chest/upper abs, squeezing shoulder blades.\n4. Lower slowly under control."
        ),
        ExerciseMeta(
            "bench_bb", "Flat Bench Press • Barbell", "Chest", "Chest • Shoulders • Arms", "Barbell", 0,
            "1. Lie flat on bench with eyes under the bar.\n2. Grip bar slightly wider than shoulder-width.\n3. Lower bar to mid-chest keeping elbows at 45°.\n4. Press bar up explosively until arms lock out."
        ),
        ExerciseMeta(
            "pushup_bw", "Push-Up • Classic", "Chest", "Chest • Arms • Abs", "Body Weight", 0,
            "1. Place hands slightly wider than shoulders in high plank.\n2. Keep core braced and body in a straight line.\n3. Lower chest to just above the floor.\n4. Push back up to starting plank position."
        ),
        ExerciseMeta(
            "curl_db", "Bicep Curl • Dumbbell", "Arms", "Arms • Forearms", "Dumbbell", 2,
            "1. Stand tall holding dumbbells at sides with palms facing forward.\n2. Keep elbows pinned to your torso.\n3. Curl weights up toward shoulders.\n4. Squeeze biceps at top, then lower slowly."
        ),
        ExerciseMeta(
            "lat_cable", "Lat Pulldown • Cable Machine", "Back", "Back • Arms", "Cable Machine", 4,
            "1. Sit securely with thighs under pads and grip bar wide.\n2. Pull bar down smoothly to upper chest while leaning slightly back.\n3. Squeeze lats hard at the bottom.\n4. Return bar upward with control."
        ),
        ExerciseMeta(
            "squat_smith", "Squat • Smith Machine / Barbell", "Legs", "Legs • Glutes • Core", "Smith Machine", 3,
            "1. Position bar across upper traps and unrack.\n2. Brace core and sit hips back and down until thighs are parallel.\n3. Drive through heels to stand back up."
        ),
        ExerciseMeta(
            "ab_wheel", "Ab Wheel Rollout / Plank", "Abs", "Abs • Core • Lower Back", "Body Weight", 5,
            "1. Kneel on floor holding ab wheel or assume forearm plank.\n2. Roll forward slowly while keeping abs tight and lower back flat.\n3. Pull back using core muscles to starting position."
        ),
        ExerciseMeta(
            "shoulder_db", "Overhead Shoulder Press • Dumbbell", "Shoulders", "Shoulders • Arms", "Dumbbell", 4,
            "1. Sit or stand holding dumbbells at shoulder height.\n2. Press weights overhead until arms are extended.\n3. Lower slowly back to ear level."
        ),
        ExerciseMeta(
            "lunge_db", "Backward Lunge • Dumbbell", "Legs", "Legs • Glutes", "Dumbbell", 3,
            "1. Stand tall holding dumbbells at sides.\n2. Step one foot backward and lower until both knees form 90° angles.\n3. Push through front heel to return to standing."
        ),
        ExerciseMeta(
            "side_plank", "Side Plank Knee to Elbow", "Core", "Core • Abs • Obliques", "Body Weight", 5,
            "1. Lie on side propped on forearm with hips lifted.\n2. Bring top knee and top elbow together over your waist.\n3. Extend back out while keeping hips elevated."
        )
    )

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
            setPadding(34, 48, 34, 48)
        }

        // TOP GLASS HEADER
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
            textSize = 21f
            setTypeface(null, Typeface.BOLD)
        }

        val tvRoleTag = TextView(this).apply {
            text = "CA (Found • Inter • Final), B.Com & Workout.Cool Suite"
            setTextColor(Color.parseColor("#38BDF8"))
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 4, 0, 0)
        }

        titleCol.addView(tvAppName)
        titleCol.addView(tvRoleTag)

        tvHunterBadge = TextView(this).apply {
            setTextColor(Color.parseColor("#FDE047"))
            textSize = 11.5f
            setTypeface(null, Typeface.BOLD)
            background = createGlassDrawable("#1E293B", "#FACC15", 36f)
            setPadding(22, 12, 22, 12)
        }

        titleRow.addView(titleCol)
        titleRow.addView(tvHunterBadge)

        tvXpSub = TextView(this).apply {
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 12f
            setPadding(0, 14, 0, 8)
        }

        xpProgressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 500
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 14)
        }

        tvAccessibilityBanner = TextView(this).apply {
            textSize = 11.5f
            setTypeface(null, Typeface.BOLD)
            setPadding(24, 16, 24, 16)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, 16, 0, 0)
            layoutParams = params
            setOnClickListener { openAccessibilitySettings() }
        }

        headerCard.addView(titleRow)
        headerCard.addView(tvXpSub)
        headerCard.addView(xpProgressBar)
        headerCard.addView(tvAccessibilityBanner)
        rootContent.addView(headerCard)

        // 5 TAB CONTAINERS
        tabFocusContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        tabSyllabusContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        tabConsistencyContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        tabWorkoutContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        tabShieldContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        buildFocusTab()
        buildSyllabusTab()
        buildConsistencyTab()
        buildWorkoutStudioTab()
        buildShieldTab()

        rootContent.addView(tabFocusContainer)
        rootContent.addView(tabSyllabusContainer)
        rootContent.addView(tabConsistencyContainer)
        rootContent.addView(tabWorkoutContainer)
        rootContent.addView(tabShieldContainer)

        scrollView.addView(rootContent)
        mainScreen.addView(scrollView)

        // BOTTOM 5-TAB NAVIGATION BAR
        val bottomNav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = createGlassDrawable("#0D1526", "#1E293B", 0f)
            setPadding(10, 14, 10, 18)
            gravity = Gravity.CENTER
        }

        val tabs = listOf("⚡ Focus", "📚 Syllabus", "📊 Habits", "🏋️ Workout", "🛡️ Shield")
        tabs.forEachIndexed { index, label ->
            val btn = Button(this).apply {
                text = label
                isAllCaps = false
                textSize = 10.5f
                setTypeface(null, Typeface.BOLD)
                setPadding(4, 12, 4, 12)
                val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                params.setMargins(4, 0, 4, 0)
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
        tabWorkoutContainer.visibility = if (index == 3) View.VISIBLE else View.GONE
        tabShieldContainer.visibility = if (index == 4) View.VISIBLE else View.GONE

        navButtons.forEachIndexed { idx, btn ->
            if (idx == index) {
                btn.background = createGlassDrawable("#1E293B", "#38BDF8", 26f)
                btn.setTextColor(Color.parseColor("#38BDF8"))
            } else {
                btn.background = createGlassDrawable("#0D1526", "#1E293B", 26f)
                btn.setTextColor(Color.parseColor("#94A3B8"))
            }
        }
        refreshAllUI()
    }

    // ====================================================
    // TAB 1: EXAM COUNTDOWN, FOCUS RINGS & CUSTOM H:M CLOCK
    // ====================================================
    private fun buildFocusTab() {
        tvExamCountdown = TextView(this).apply {
            setTextColor(Color.parseColor("#FDE047"))
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            background = createGlassDrawable("#10192D", "#F59E0B", 28f)
            setPadding(30, 22, 30, 22)
            val p = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            p.setMargins(0, 0, 0, 28)
            layoutParams = p
            setOnClickListener { showExamCountdownDialog() }
        }
        tabFocusContainer.addView(tvExamCountdown)

        val ringCard = createGlassCard("#10192D", "#233B5E")
        ringCard.addView(createSectionLabel("DAILY FOCUS & CONSISTENCY RINGS"))

        val ringsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 14, 0, 14)
        }

        focusRingView = CircularRingView(this, "#38BDF8", "Study Goal").apply {
            layoutParams = LinearLayout.LayoutParams(310, 310).apply { setMargins(14, 0, 14, 0) }
        }
        habitRingView = CircularRingView(this, "#10B981", "Daily Habits").apply {
            layoutParams = LinearLayout.LayoutParams(310, 310).apply { setMargins(14, 0, 14, 0) }
        }

        ringsRow.addView(focusRingView)
        ringsRow.addView(habitRingView)

        tvFocusMetricSummary = TextView(this).apply {
            setTextColor(Color.parseColor("#E2E8F0"))
            textSize = 12.5f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            background = createGlassDrawable("#090F1D", "#1E293B", 24f)
            setPadding(22, 18, 22, 18)
        }

        ringCard.addView(ringsRow)
        ringCard.addView(tvFocusMetricSummary)
        tabFocusContainer.addView(ringCard)

        val timerCard = createGlassCard("#10192D", "#2A4365")
        timerCard.addView(createSectionLabel("CUSTOMIZABLE STUDY CLOCK (HOURS & MINUTES)"))

        val chipScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            setPadding(0, 10, 0, 10)
        }
        subjectChipRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        chipScroll.addView(subjectChipRow)

        tvTimerDisplay = TextView(this).apply {
            text = formatTime(timeLeftInMillis)
            setTextColor(Color.parseColor("#F8FAFC"))
            textSize = 54f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 14, 0, 4)
            setOnClickListener { showCustomTimerDialog() }
        }

        tvTimerStatus = TextView(this).apply {
            text = "Active: $selectedSubject (Tap clock to set custom H:M)"
            setTextColor(Color.parseColor("#38BDF8"))
            textSize = 12.5f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 18)
        }

        val fineTuneRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 12)
        }
        fineTuneRow.addView(createPillButton("- 5m") { adjustTimerByMinutes(-5) })
        fineTuneRow.addView(createPillButton("⏱️ Set Custom H:M") { showCustomTimerDialog() })
        fineTuneRow.addView(createPillButton("+ 5m") { adjustTimerByMinutes(5) })

        val presetRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16)
        }
        presetRow.addView(createPillButton("25m") { setTimerPreset(25 * 60 * 1000L, "25m Pomodoro • $selectedSubject") })
        presetRow.addView(createPillButton("45m") { setTimerPreset(45 * 60 * 1000L, "45m Study • $selectedSubject") })
        presetRow.addView(createPillButton("1h 30m") { setTimerPreset(90 * 60 * 1000L, "1h 30m Deep • $selectedSubject") })
        presetRow.addView(createPillButton("3h Exam") { setTimerPreset(180 * 60 * 1000L, "3h CA Exam • $selectedSubject") })

        cbStrictLock = CheckBox(this).apply {
            text = "🔒 Regain Strict Mode (Lock Shield until timer completes)"
            setTextColor(Color.parseColor("#FDE047"))
            textSize = 12.5f
            isChecked = prefs.getBoolean("strict_lock_mode", true)
            setPadding(10, 10, 10, 16)
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
            setPadding(0, 16, 0, 0)
        }

        val btnQuickLog = createActionButton("✓ Log Study Block", "#064E3B", "#10B981") {
            recordCompletedSession()
        }
        val btnUndoLog = createActionButton("↩️ Undo Last Log", "#450A0A", "#EF4444") {
            undoLastStudySession()
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

    private fun showExamCountdownDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 36, 50, 20)
        }
        val inputName = EditText(this).apply {
            hint = "Exam Name (e.g., CA Foundation / Inter / B.Com Sem 1)"
            setText(prefs.getString("exam_name", "CA & B.Com Target Exam"))
        }
        val inputDays = EditText(this).apply {
            hint = "Days Remaining (e.g., 45)"
            setRawInputType(InputType.TYPE_CLASS_NUMBER)
            setText(prefs.getInt("exam_days_left", 45).toString())
        }
        layout.addView(inputName)
        layout.addView(inputDays)

        AlertDialog.Builder(this)
            .setTitle("🎯 Set Exam Countdown")
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                val name = inputName.text.toString().trim().ifEmpty { "CA & B.Com Exam" }
                val days = inputDays.text.toString().toIntOrNull()?.coerceAtLeast(0) ?: 45
                prefs.edit().putString("exam_name", name).putInt("exam_days_left", days).apply()
                refreshAllUI()
            }
            .setNegativeButton("Cancel", null)
            .show()
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
            setPadding(56, 36, 56, 20)
        }
        val currentTotalMins = (initialTimeMillis / 60000L).toInt()
        val inputHours = EditText(this).apply {
            setRawInputType(InputType.TYPE_CLASS_NUMBER)
            setText((currentTotalMins / 60).toString())
            hint = "Hours (0 - 12)"
        }
        val inputMinutes = EditText(this).apply {
            setRawInputType(InputType.TYPE_CLASS_NUMBER)
            setText((currentTotalMins % 60).toString())
            hint = "Minutes (0 - 59)"
        }
        layout.addView(TextView(this).apply { text = "Hours (0 to 12):"; setTypeface(null, Typeface.BOLD) })
        layout.addView(inputHours)
        layout.addView(TextView(this).apply { text = "Minutes (0 to 59):"; setTypeface(null, Typeface.BOLD); setPadding(0, 20, 0, 0) })
        layout.addView(inputMinutes)

        AlertDialog.Builder(this)
            .setTitle("⏱️ Set Custom Focus Timer")
            .setView(layout)
            .setPositiveButton("Set Timer") { _, _ ->
                val hrs = inputHours.text.toString().toIntOrNull()?.coerceIn(0, 12) ?: 0
                val mins = inputMinutes.text.toString().toIntOrNull()?.coerceIn(0, 59) ?: 0
                val totalMinutes = (hrs * 60 + mins).coerceAtLeast(1)
                setTimerPreset(totalMinutes * 60 * 1000L, "Custom ${hrs}h ${mins}m • $selectedSubject")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ====================================================
    // TAB 2: CA (FOUND / INTER / FINAL) & B.COM SYLLABUS
    // ====================================================
    private fun buildSyllabusTab() {
        val topHeaderCard = createGlassCard("#10192D", "#2A4365")
        topHeaderCard.addView(createSectionLabel("CA (FOUNDATION • INTER • FINAL) & B.COM SYLLABUS"))

        val tvDesc = TextView(this).apply {
            text = "Tap status to cycle (⬜ Todo → ⏳ Studying → ✅ Done), tap 🔄 Rev to track revisions (+1x, +2x), or use ✏️ Edit and ✕ Delete."
            setTextColor(Color.parseColor("#CBD5E1"))
            textSize = 12.5f
            setPadding(0, 4, 0, 16)
        }

        val filterScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            setPadding(0, 0, 0, 16)
        }
        courseFilterRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        filterScroll.addView(courseFilterRow)

        val btnRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        btnRow.addView(createActionButton("+ Add Subject", "#0284C7", "#38BDF8") { showAddSubjectDialog() })
        btnRow.addView(createActionButton("📚 CA / B.Com Packs", "#1E293B", "#FACC15") { showCoursePackTemplatesDialog() })

        topHeaderCard.addView(tvDesc)
        topHeaderCard.addView(filterScroll)
        topHeaderCard.addView(btnRow)
        tabSyllabusContainer.addView(topHeaderCard)

        syllabusListContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        tabSyllabusContainer.addView(syllabusListContainer)
    }

    private fun renderSyllabusCards() {
        syllabusListContainer.removeAllViews()
        subjectChipRow.removeAllViews()
        courseFilterRow.removeAllViews()

        val levels = listOf("All", "CA Foundation", "CA Inter", "CA Final", "B.Com")
        levels.forEach { lvl ->
            val isSelected = selectedCourseFilter == lvl
            val fBtn = Button(this).apply {
                text = lvl
                isAllCaps = false
                textSize = 11.5f
                setTypeface(null, Typeface.BOLD)
                setTextColor(if (isSelected) Color.parseColor("#060A12") else Color.parseColor("#E2E8F0"))
                background = if (isSelected) createGlassDrawable("#38BDF8", "#7DD3FC", 30f) else createGlassDrawable("#1E293B", "#334155", 30f)
                setPadding(28, 10, 28, 10)
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                lp.setMargins(0, 0, 12, 0)
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
                textSize = 11.5f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
                background = if (subjName == selectedSubject) createGlassDrawable("#0284C7", "#38BDF8", 30f) else createGlassDrawable("#1E293B", "#334155", 30f)
                setPadding(28, 10, 28, 10)
                val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                p.setMargins(0, 0, 14, 0)
                layoutParams = p
                setOnClickListener {
                    selectedSubject = subjName
                    tvTimerStatus.text = "Active Subject: $selectedSubject"
                    renderSyllabusCards()
                }
            }
            subjectChipRow.addView(chipBtn)

            if (selectedCourseFilter != "All" && courseLevel != selectedCourseFilter) continue

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
                textSize = 10.5f
                setTypeface(null, Typeface.BOLD)
                background = createGlassDrawable("#0B1528", "#1E3A5F", 18f)
                setPadding(20, 8, 20, 8)
            }
            val spacer = View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) }
            val btnEditSubj = createMiniTagButton("✏️ Edit", "#1E293B", "#334155", "#CBD5E1") { showEditSubjectDialog(i) }
            val btnDeleteSubj = createMiniTagButton("🗑️ Delete", "#450A0A", "#EF4444", "#FCA5A5") { confirmDeleteSubject(i, subjName) }

            badgeRow.addView(tvLevelTag)
            badgeRow.addView(spacer)
            badgeRow.addView(btnEditSubj)
            badgeRow.addView(btnDeleteSubj)

            val tvSubjTitle = TextView(this).apply {
                text = "📘 $subjName"
                setTextColor(Color.parseColor("#F8FAFC"))
                textSize = 18f
                setTypeface(null, Typeface.BOLD)
                setPadding(0, 4, 0, 6)
            }
            val tvMetaRow = TextView(this).apply {
                text = "⏱️ Studied: ${studiedMins / 60}h ${studiedMins % 60}m   •   ✅ $completedUnits/$totalUnits Units ($pct%)"
                setTextColor(Color.parseColor("#94A3B8"))
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                setPadding(0, 0, 0, 12)
            }
            val pb = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100
                progress = pct
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 14).apply { setMargins(0, 0, 0, 20) }
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
                val revisions = unitObj.optInt("rev", 0)

                val (statusLabel, colorHex, bgHex) = when (status) {
                    2 -> Triple("✅ Done", "#6EE7B7", "#064E3B")
                    1 -> Triple("⏳ Studying", "#FDE047", "#422006")
                    else -> Triple("⬜ Todo", "#CBD5E1", "#090F1D")
                }

                val unitContainer = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    background = createGlassDrawable(bgHex, "#1E293B", 22f)
                    setPadding(24, 20, 20, 20)
                    val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    p.setMargins(0, 0, 0, 14)
                    layoutParams = p
                }

                val textCol = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    setOnClickListener {
                        val nextStatus = (status + 1) % 3
                        unitObj.put("status", nextStatus)
                        prefs.edit().putString("syllabus_json", subjectsArray.toString()).apply()
                        if (nextStatus == 2) addXp(35, "Unit Completed! +35 XP")
                        renderSyllabusCards()
                    }
                }

                val tvModBadge = TextView(this).apply {
                    text = "$moduleTag  •  $statusLabel"
                    setTextColor(Color.parseColor("#38BDF8"))
                    textSize = 11f
                    setTypeface(null, Typeface.BOLD)
                }
                val tvUnitName = TextView(this).apply {
                    text = unitTitle
                    setTextColor(Color.parseColor(colorHex))
                    textSize = 14f
                    setTypeface(null, Typeface.BOLD)
                    setPadding(0, 6, 10, 0)
                }
                textCol.addView(tvModBadge)
                textCol.addView(tvUnitName)

                val btnRev = createMiniTagButton("🔄 ${revisions}x", "#0F172A", "#38BDF8", "#38BDF8") {
                    unitObj.put("rev", revisions + 1)
                    prefs.edit().putString("syllabus_json", subjectsArray.toString()).apply()
                    addXp(15, "Revision +1 logged! (+15 XP)")
                    renderSyllabusCards()
                }
                val btnEditUnit = createMiniTagButton("✏️", "#1E293B", "#334155", "#CBD5E1") { showEditUnitDialog(i, u) }
                val btnRemoveUnit = createMiniTagButton("✕", "#450A0A", "#EF4444", "#FCA5A5") {
                    unitsArray.remove(u)
                    prefs.edit().putString("syllabus_json", subjectsArray.toString()).apply()
                    renderSyllabusCards()
                }

                unitContainer.addView(textCol)
                unitContainer.addView(btnRev)
                unitContainer.addView(btnEditUnit)
                unitContainer.addView(btnRemoveUnit)
                card.addView(unitContainer)
            }

            val btnAddUnit = createActionButton("+ Add Module / Unit to $subjName", "#0B1528", "#1E3A5F") {
                showAddUnitDialog(i)
            }
            card.addView(btnAddUnit)
            syllabusListContainer.addView(card)
        }
    }

    private fun showAddSubjectDialog() {
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(50, 32, 50, 16) }
        val inputLevel = EditText(this).apply {
            hint = "Course Level (CA Foundation / CA Inter / CA Final / B.Com)"
            setText(if (selectedCourseFilter == "All") "CA Foundation" else selectedCourseFilter)
        }
        val inputSubj = EditText(this).apply { hint = "Subject Name (e.g., Corporate Law / Costing)" }
        val inputMod = EditText(this).apply { hint = "First Module Tag"; setText("Module 1 • Unit 1") }
        val inputUnit = EditText(this).apply { hint = "First Chapter / Unit Title" }
        layout.addView(inputLevel); layout.addView(inputSubj); layout.addView(inputMod); layout.addView(inputUnit)

        AlertDialog.Builder(this)
            .setTitle("📘 Add New Subject")
            .setView(layout)
            .setPositiveButton("Add") { _, _ ->
                val name = inputSubj.text.toString().trim()
                if (name.isNotEmpty()) {
                    val arr = JSONArray(prefs.getString("syllabus_json", "[]"))
                    val units = JSONArray().put(
                        JSONObject()
                            .put("module", inputMod.text.toString().trim().ifEmpty { "Module 1" })
                            .put("title", inputUnit.text.toString().trim().ifEmpty { "Core Concepts" })
                            .put("status", 0)
                            .put("rev", 0)
                    )
                    arr.put(JSONObject().put("name", name).put("level", inputLevel.text.toString().trim().ifEmpty { "CA Foundation" }).put("units", units))
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
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(50, 32, 50, 16) }
        val inputLevel = EditText(this).apply { setText(subjObj.optString("level", "CA Foundation")) }
        val inputName = EditText(this).apply { setText(subjObj.getString("name")) }
        layout.addView(inputLevel); layout.addView(inputName)

        AlertDialog.Builder(this)
            .setTitle("✏️ Edit Subject")
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                val newName = inputName.text.toString().trim()
                if (newName.isNotEmpty()) {
                    subjObj.put("name", newName)
                    subjObj.put("level", inputLevel.text.toString().trim().ifEmpty { "CA Foundation" })
                    prefs.edit().putString("syllabus_json", arr.toString()).apply()
                    renderSyllabusCards()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDeleteSubject(subjectIndex: Int, subjName: String) {
        AlertDialog.Builder(this)
            .setTitle("Delete Subject?")
            .setMessage("Remove '$subjName' and all its modules?")
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
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(50, 32, 50, 16) }
        val inputModule = EditText(this).apply { setText("Module 1 • Unit 1") }
        val inputTitle = EditText(this).apply { hint = "Chapter / Lesson Title" }
        layout.addView(inputModule); layout.addView(inputTitle)

        AlertDialog.Builder(this)
            .setTitle("➕ Add Module & Unit")
            .setView(layout)
            .setPositiveButton("Add") { _, _ ->
                val title = inputTitle.text.toString().trim()
                if (title.isNotEmpty()) {
                    val arr = JSONArray(prefs.getString("syllabus_json", "[]"))
                    arr.getJSONObject(subjectIndex).getJSONArray("units").put(
                        JSONObject().put("module", inputModule.text.toString().trim().ifEmpty { "Module" }).put("title", title).put("status", 0).put("rev", 0)
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
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(50, 32, 50, 16) }
        val inputModule = EditText(this).apply { setText(unitObj.optString("module", "Module 1")) }
        val inputTitle = EditText(this).apply { setText(unitObj.getString("title")) }
        layout.addView(inputModule); layout.addView(inputTitle)

        AlertDialog.Builder(this)
            .setTitle("✏️ Edit Module / Unit")
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                val newTitle = inputTitle.text.toString().trim()
                if (newTitle.isNotEmpty()) {
                    unitObj.put("module", inputModule.text.toString().trim().ifEmpty { "Module" })
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
            "➕ CA Intermediate Pack (Adv Accounts, Corp Law, Tax, Costing, Audit, FM)",
            "➕ CA Final Pack (FR, AFM, Adv Audit, DT, IDT)",
            "➕ B.Com Semester Pack (Financial Acc, Business Mgmt, Economics)"
        )
        AlertDialog.Builder(this)
            .setTitle("📚 Load CA / B.Com Packs")
            .setItems(options) { _, which ->
                val arr = JSONArray(prefs.getString("syllabus_json", "[]"))
                when (which) {
                    0 -> {
                        arr.put(createSubjectPack("CA Inter: Advanced Accounting", "CA Inter", listOf("Mod 1 • Accounting Standards", "Mod 2 • Company Accounts & Amalgamation")))
                        arr.put(createSubjectPack("CA Inter: Corporate & Other Laws", "CA Inter", listOf("Mod 1 • Companies Act Share Capital", "Mod 2 • FEMA & LLP Act")))
                        arr.put(createSubjectPack("CA Inter: Cost & Mgmt Accounting", "CA Inter", listOf("Mod 1 • Material, Labour & Overheads", "Mod 2 • Standard & Marginal Costing")))
                        arr.put(createSubjectPack("CA Inter: Taxation (DT & GST)", "CA Inter", listOf("Part I • Income Tax Heads", "Part II • GST Supply & ITC")))
                    }
                    1 -> {
                        arr.put(createSubjectPack("CA Final: Financial Reporting (FR)", "CA Final", listOf("Mod 1 • Ind AS Assets & Revenue", "Mod 2 • Business Combinations & CFS")))
                        arr.put(createSubjectPack("CA Final: Advanced Financial Mgmt", "CA Final", listOf("Mod 1 • Valuation & Derivatives", "Mod 2 • Forex & Mergers")))
                        arr.put(createSubjectPack("CA Final: Direct & International Tax", "CA Final", listOf("Mod 1 • Corporate Tax & Assessment", "Mod 2 • Transfer Pricing & DTAA")))
                    }
                    2 -> {
                        arr.put(createSubjectPack("B.Com: Corporate Accounting", "B.Com", listOf("Unit 1 • Share Issue & Redemption", "Unit 2 • Final Accounts & Valuation")))
                        arr.put(createSubjectPack("B.Com: Business Organization & Mgmt", "B.Com", listOf("Unit 1 • Management Functions", "Unit 2 • Leadership & Control")))
                    }
                }
                prefs.edit().putString("syllabus_json", arr.toString()).apply()
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
            unitsArr.put(JSONObject().put("module", mod).put("title", title).put("status", 0).put("rev", 0))
        }
        return JSONObject().put("name", name).put("level", level).put("units", unitsArr)
    }

    // ====================================================
    // TAB 3: STREAKO CONSISTENCY MATRIX
    // ====================================================
    private fun buildConsistencyTab() {
        consistencyContentContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
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
        bannerCard.addView(TextView(this).apply {
            text = "trust the process."
            setTextColor(Color.parseColor("#34D399"))
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
        })
        bannerCard.addView(TextView(this).apply {
            text = "\"Every checkmark builds momentum. See your CA study & workout consistency grow day by day.\""
            setTextColor(Color.parseColor("#CBD5E1"))
            textSize = 12f
            setPadding(0, 6, 0, 16)
        })
        bannerCard.addView(WeeklyBarChartView(this, dayPercentages).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 240)
        })
        bannerCard.addView(TextView(this).apply {
            text = "📈 Weekly Consistency: $weeklyPct% ($totalChecks / ${totalHabits * 7} checkmarks)"
            setTextColor(Color.parseColor("#A7F3D0"))
            textSize = 12.5f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 12, 0, 0)
        })
        consistencyContentContainer.addView(bannerCard)

        val matrixCard = createGlassCard("#10192D", "#2A4365")
        matrixCard.addView(createSectionLabel("7-DAY HABIT MATRIX (TAP ⚙️ TO EDIT OR DELETE HABIT)"))

        val headerRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 6, 0, 12) }
        headerRow.addView(TextView(this).apply {
            text = "Habit (⚙️ Edit/Del)"
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 11.5f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2.1f)
        })
        listOf("M", "T", "W", "T", "F", "S", "S").forEach { dLabel ->
            headerRow.addView(TextView(this).apply {
                text = dLabel
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#38BDF8"))
                textSize = 11.5f
                setTypeface(null, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.45f)
            })
        }
        matrixCard.addView(headerRow)

        for (i in 0 until habitsArray.length()) {
            val habitObj = habitsArray.getJSONObject(i)
            val title = habitObj.getString("title")
            val days = habitObj.getJSONArray("days")

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = createGlassDrawable("#090F1D", "#1E293B", 18f)
                setPadding(16, 14, 12, 14)
                val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                p.setMargins(0, 0, 0, 10)
                layoutParams = p
            }

            row.addView(TextView(this).apply {
                text = "⚙️ $title"
                setTextColor(Color.WHITE)
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2.1f)
                setOnClickListener { showManageHabitDialog(i) }
            })

            for (d in 0 until 7) {
                val checked = days.getBoolean(d)
                row.addView(TextView(this).apply {
                    text = if (checked) "✓" else "·"
                    gravity = Gravity.CENTER
                    setTextColor(if (checked) Color.parseColor("#064E3B") else Color.parseColor("#64748B"))
                    textSize = 13f
                    setTypeface(null, Typeface.BOLD)
                    background = if (checked) createGlassDrawable("#34D399", "#10B981", 14f) else createGlassDrawable("#1E293B", "#334155", 14f)
                    setPadding(0, 10, 0, 10)
                    val cp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.45f)
                    cp.setMargins(3, 0, 3, 0)
                    layoutParams = cp
                    setOnClickListener {
                        val newState = !checked
                        days.put(d, newState)
                        prefs.edit().putString("habits_matrix_json", habitsArray.toString()).apply()
                        addXp(if (newState) 20 else -20, if (newState) "Habit Checked! +20 XP" else "Checkmark Undone (-20 XP)")
                        refreshAllUI()
                    }
                })
            }
            matrixCard.addView(row)
        }

        val habitActionRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 10, 0, 0) }
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
    }

    private fun showAddHabitDialog() {
        val input = EditText(this).apply {
            hint = "e.g., Solve 10 Accounts Questions / 8 Hrs Sleep"
            setRawInputType(InputType.TYPE_CLASS_TEXT)
            setPadding(50, 36, 50, 36)
        }
        AlertDialog.Builder(this)
            .setTitle("➕ Add Daily Habit")
            .setView(input)
            .setPositiveButton("Add") { _, _ ->
                val title = input.text.toString().trim()
                if (title.isNotEmpty()) {
                    val arr = JSONArray(prefs.getString("habits_matrix_json", "[]"))
                    arr.put(JSONObject().put("title", title).put("days", JSONArray(listOf(false, false, false, false, false, false, false))))
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
        val input = EditText(this).apply {
            setText(habitObj.getString("title"))
            setPadding(50, 36, 50, 36)
        }
        AlertDialog.Builder(this)
            .setTitle("⚙️ Edit or Remove Habit")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val updated = input.text.toString().trim()
                if (updated.isNotEmpty()) {
                    habitObj.put("title", updated)
                    prefs.edit().putString("habits_matrix_json", arr.toString()).apply()
                    refreshAllUI()
                }
            }
            .setNeutralButton("🗑️ Delete") { _, _ ->
                arr.remove(habitIndex)
                prefs.edit().putString("habits_matrix_json", arr.toString()).apply()
                refreshAllUI()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ====================================================
    // TAB 4: 🏋️ WORKOUT STUDIO (WORKOUT.COOL & SCREENSHOT SUITE)
    // ====================================================
    private fun buildWorkoutStudioTab() {
        workoutContentContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        tabWorkoutContainer.addView(workoutContentContainer)
    }

    private fun renderWorkoutStudioTab() {
        workoutContentContainer.removeAllViews()

        val workoutsDone = prefs.getInt("workouts_completed", 0)
        val workoutMins = prefs.getInt("workout_minutes", 0)
        val totalVolumeKg = calculateTotalVolumeKg()

        // 1. FITNESS TRACKER SMART REPORT + ANATOMICAL MUSCLE BODY MAP
        val reportCard = createGlassCard("#10192D", "#EF4444")
        reportCard.addView(createSectionLabel("FITNESS TRACKER SMART • VOLUME & FOCUS AREA MAP"))

        val metricsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = createGlassDrawable("#090F1D", "#1E293B", 24f)
            setPadding(20, 22, 20, 22)
        }
        metricsRow.addView(createStatColumn("$workoutsDone", "Workouts", "#F8FAFC"))
        metricsRow.addView(createStatColumn("${workoutMins}m", "Time (min)", "#38BDF8"))
        metricsRow.addView(createStatColumn("$totalVolumeKg", "Volume (kg)", "#F43F5E"))
        reportCard.addView(metricsRow)

        val bodyMapView = MuscleBodyMapView(this, selectedMuscleFilter).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 330).apply {
                setMargins(0, 18, 0, 8)
            }
        }
        reportCard.addView(bodyMapView)
        workoutContentContainer.addView(reportCard)

        // 2. WORKOUT.COOL SMART FILTER: STEP 1 EQUIPMENT & STEP 2 MUSCLE FOCUS
        val filterCard = createGlassCard("#10192D", "#2A4365")
        filterCard.addView(createSectionLabel("STEP 1: SELECT EQUIPMENT (WORKOUT.COOL GENERATOR)"))

        val eqScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            setPadding(0, 6, 0, 18)
        }
        val eqRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val equipments = listOf("All", "Body Weight", "Dumbbell", "Barbell", "Cable Machine", "Smith Machine")
        equipments.forEach { eq ->
            val isSel = selectedEquipmentFilter == eq
            val btn = Button(this).apply {
                text = if (eq == "Body Weight") "🤸 Body Weight / No Eq" else "🏋️ $eq"
                isAllCaps = false
                textSize = 11.5f
                setTypeface(null, Typeface.BOLD)
                setTextColor(if (isSel) Color.parseColor("#060A12") else Color.WHITE)
                background = if (isSel) createGlassDrawable("#34D399", "#10B981", 30f) else createGlassDrawable("#1E293B", "#334155", 30f)
                setPadding(28, 12, 28, 12)
                val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                p.setMargins(0, 0, 12, 0)
                layoutParams = p
                setOnClickListener {
                    selectedEquipmentFilter = eq
                    renderWorkoutStudioTab()
                }
            }
            eqRow.addView(btn)
        }
        eqScroll.addView(eqRow)
        filterCard.addView(eqScroll)

        filterCard.addView(createSectionLabel("STEP 2: SELECT FOCUS AREA / TARGET MUSCLE"))
        val muscleScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            setPadding(0, 6, 0, 6)
        }
        val mRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val muscles = listOf("All", "Chest", "Back", "Arms", "Abs", "Shoulders", "Legs", "Core")
        muscles.forEach { m ->
            val isSel = selectedMuscleFilter == m
            val btn = Button(this).apply {
                text = m.uppercase(Locale.getDefault())
                isAllCaps = false
                textSize = 11.5f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
                background = if (isSel) createGlassDrawable("#DC2626", "#F87171", 30f) else createGlassDrawable("#1E293B", "#334155", 30f)
                setPadding(28, 12, 28, 12)
                val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                p.setMargins(0, 0, 12, 0)
                layoutParams = p
                setOnClickListener {
                    selectedMuscleFilter = m
                    renderWorkoutStudioTab()
                }
            }
            mRow.addView(btn)
        }
        muscleScroll.addView(mRow)
        filterCard.addView(muscleScroll)
        workoutContentContainer.addView(filterCard)

        // 3. EXERCISE CARDS WITH ANIMATED VISUAL DEMO & FLEXIBLE WEIGHTLIFTING SET LOGGER
        val setsStateObj = JSONObject(prefs.getString("workout_sets_json", "{}"))
        val filteredExercises = exerciseCatalog.filter { ex ->
            (selectedMuscleFilter == "All" || ex.muscle.equals(selectedMuscleFilter, true) || ex.secondaryMuscles.contains(selectedMuscleFilter, true)) &&
            (selectedEquipmentFilter == "All" || ex.equipment.equals(selectedEquipmentFilter, true))
        }

        filteredExercises.forEach { ex ->
            val exCard = createGlassCard("#10192D", "#243B61")
            val topRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 0, 0, 14)
            }

            // Live Animated Visual Demonstration Thumbnail
            val animThumb = ExerciseAnimView(this, ex.animType).apply {
                layoutParams = LinearLayout.LayoutParams(150, 150).apply { setMargins(0, 0, 20, 0) }
                setOnClickListener { showExerciseVideoGuideDialog(ex) }
            }

            val metaCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val exSetsArr = setsStateObj.optJSONArray(ex.id) ?: createDefaultSetsForExercise()
            setsStateObj.put(ex.id, exSetsArr)
            var doneSets = 0
            for (s in 0 until exSetsArr.length()) {
                if (exSetsArr.getJSONObject(s).optBoolean("done", false)) doneSets++
            }

            val tvExTitle = TextView(this).apply {
                text = ex.name
                setTextColor(Color.WHITE)
                textSize = 16.5f
                setTypeface(null, Typeface.BOLD)
            }
            val tvExSub = TextView(this).apply {
                text = "🎯 ${ex.secondaryMuscles}  •  🏋️ ${ex.equipment}"
                setTextColor(Color.parseColor("#38BDF8"))
                textSize = 11.5f
                setTypeface(null, Typeface.BOLD)
                setPadding(0, 4, 0, 4)
            }
            val tvDoneSets = TextView(this).apply {
                text = "$doneSets/${exSetsArr.length()} Sets Done"
                setTextColor(Color.parseColor("#A7F3D0"))
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
            }

            metaCol.addView(tvExTitle)
            metaCol.addView(tvExSub)
            metaCol.addView(tvDoneSets)

            val btnVideoGuide = Button(this).apply {
                text = "▶ Guide"
                isAllCaps = false
                setTextColor(Color.WHITE)
                textSize = 11.5f
                setTypeface(null, Typeface.BOLD)
                background = createGlassDrawable("#DC2626", "#F87171", 24f)
                setPadding(22, 12, 22, 12)
                setOnClickListener { showExerciseVideoGuideDialog(ex) }
            }

            topRow.addView(animThumb)
            topRow.addView(metaCol)
            topRow.addView(btnVideoGuide)
            exCard.addView(topRow)

            // Weightlifting Set Rows (Screenshot 7 Style: ✓ | Set # | 20 kg | 12 Reps)
            for (s in 0 until exSetsArr.length()) {
                val setObj = exSetsArr.getJSONObject(s)
                val kg = setObj.optInt("kg", 20)
                val reps = setObj.optInt("reps", 12)
                val isDone = setObj.optBoolean("done", false)

                val setRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    background = if (isDone) {
                        createGlassDrawable("#16A34A", "#4ADE80", 26f)
                    } else {
                        createGlassDrawable("#090F1D", "#1E293B", 26f)
                    }
                    setPadding(24, 16, 20, 16)
                    val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    lp.setMargins(0, 0, 0, 10)
                    layoutParams = lp
                }

                val tvCheck = TextView(this).apply {
                    text = if (isDone) "✓  Set ${s + 1}" else "○  Set ${s + 1}"
                    setTextColor(Color.WHITE)
                    textSize = 13.5f
                    setTypeface(null, Typeface.BOLD)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    setOnClickListener {
                        val nextState = !isDone
                        setObj.put("done", nextState)
                        prefs.edit().putString("workout_sets_json", setsStateObj.toString()).apply()
                        if (nextState) {
                            addXp(15, "Set ${s + 1} Done! (+${kg * reps} kg vol & +15 XP)")
                        } else {
                            addXp(-15, "Set ${s + 1} Unchecked (-15 XP)")
                        }
                        renderWorkoutStudioTab()
                    }
                }

                val tvKgPill = TextView(this).apply {
                    text = "$kg kg"
                    setTextColor(if (isDone) Color.parseColor("#064E3B") else Color.WHITE)
                    textSize = 13f
                    setTypeface(null, Typeface.BOLD)
                    background = createGlassDrawable(if (isDone) "#DCFCE7" else "#1E293B", "#334155", 20f)
                    setPadding(26, 10, 26, 10)
                    setOnClickListener { showEditSetDialog(setsStateObj, ex.id, s) }
                }

                val tvRepsPill = TextView(this).apply {
                    text = "$reps Reps"
                    setTextColor(if (isDone) Color.parseColor("#064E3B") else Color.WHITE)
                    textSize = 13f
                    setTypeface(null, Typeface.BOLD)
                    background = createGlassDrawable(if (isDone) "#DCFCE7" else "#1E293B", "#334155", 20f)
                    setPadding(26, 10, 26, 10)
                    val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    p.setMargins(12, 0, 12, 0)
                    layoutParams = p
                    setOnClickListener { showEditSetDialog(setsStateObj, ex.id, s) }
                }

                val btnRemoveSet = TextView(this).apply {
                    text = "✕"
                    setTextColor(if (isDone) Color.WHITE else Color.parseColor("#FCA5A5"))
                    textSize = 13f
                    setTypeface(null, Typeface.BOLD)
                    setPadding(16, 8, 16, 8)
                    setOnClickListener {
                        if (exSetsArr.length() > 1) {
                            exSetsArr.remove(s)
                            prefs.edit().putString("workout_sets_json", setsStateObj.toString()).apply()
                            renderWorkoutStudioTab()
                        }
                    }
                }

                setRow.addView(tvCheck)
                setRow.addView(tvKgPill)
                setRow.addView(tvRepsPill)
                setRow.addView(btnRemoveSet)
                exCard.addView(setRow)
            }

            val setActionRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 8, 0, 0)
            }
            setActionRow.addView(createPillButton("🎬 Detail & Video") { showExerciseVideoGuideDialog(ex) })
            setActionRow.addView(createPillButton("➕ Add a Set") {
                exSetsArr.put(JSONObject().put("kg", 25).put("reps", 12).put("done", false))
                prefs.edit().putString("workout_sets_json", setsStateObj.toString()).apply()
                renderWorkoutStudioTab()
            })
            setActionRow.addView(createPillButton("✓ Finish Exercise") {
                logWorkoutSession(ex.name, 15, "${exSetsArr.length()} sets completed (${ex.equipment})")
            })

            exCard.addView(setActionRow)
            workoutContentContainer.addView(exCard)
        }
        prefs.edit().putString("workout_sets_json", setsStateObj.toString()).apply()

        // 4. CUSTOM WORKOUT LOGGER & UNDOABLE WORKOUT HISTORY
        val historyCard = createGlassCard("#10192D", "#F59E0B")
        historyCard.addView(createSectionLabel("WORKOUT SESSION LOGGER & UNDO HISTORY"))

        val btnCustomWorkout = createActionButton("+ Log Custom Workout Session", "#B45309", "#F59E0B") {
            showCustomWorkoutDialog()
        }
        historyCard.addView(btnCustomWorkout)

        val workoutLogs = JSONArray(prefs.getString("workout_logs_json", "[]"))
        if (workoutLogs.length() > 0) {
            historyCard.addView(TextView(this).apply {
                text = "RECENT LOGGED SESSIONS (TAP ✕ UNDO TO CANCEL ACCIDENTAL CLICKS)"
                setTextColor(Color.parseColor("#94A3B8"))
                textSize = 11f
                setTypeface(null, Typeface.BOLD)
                setPadding(0, 16, 0, 10)
            })

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
                    setPadding(22, 16, 18, 16)
                    val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    p.setMargins(0, 0, 0, 10)
                    layoutParams = p
                }

                logRow.addView(TextView(this).apply {
                    text = "💪 $wType • ${wMins}m ($wTime)\n" + (if (wNotes.isNotEmpty()) "📝 $wNotes" else "⚡ +60 XP")
                    setTextColor(Color.parseColor("#E2E8F0"))
                    textSize = 12f
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })

                logRow.addView(createMiniTagButton("✕ Undo", "#450A0A", "#EF4444", "#FCA5A5") {
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
                })
                historyCard.addView(logRow)
            }
        }
        workoutContentContainer.addView(historyCard)
    }

    private fun createDefaultSetsForExercise(): JSONArray {
        return JSONArray().apply {
            put(JSONObject().put("kg", 20).put("reps", 12).put("done", true))
            put(JSONObject().put("kg", 25).put("reps", 12).put("done", false))
            put(JSONObject().put("kg", 30).put("reps", 10).put("done", false))
        }
    }

    private fun calculateTotalVolumeKg(): Int {
        val setsStateObj = JSONObject(prefs.getString("workout_sets_json", "{}"))
        var totalVol = 0
        val keys = setsStateObj.keys()
        while (keys.hasNext()) {
            val arr = setsStateObj.optJSONArray(keys.next()) ?: continue
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                if (o.optBoolean("done", false)) {
                    totalVol += o.optInt("kg", 0) * o.optInt("reps", 0)
                }
            }
        }
        return totalVol
    }

    private fun showEditSetDialog(setsStateObj: JSONObject, exId: String, setIndex: Int) {
        val setObj = setsStateObj.getJSONArray(exId).getJSONObject(setIndex)
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(50, 32, 50, 16) }
        val inputKg = EditText(this).apply {
            setRawInputType(InputType.TYPE_CLASS_NUMBER)
            setText(setObj.optInt("kg", 20).toString())
            hint = "Weight (kg)"
        }
        val inputReps = EditText(this).apply {
            setRawInputType(InputType.TYPE_CLASS_NUMBER)
            setText(setObj.optInt("reps", 12).toString())
            hint = "Reps"
        }
        layout.addView(TextView(this).apply { text = "Weight (kg):"; setTypeface(null, Typeface.BOLD) })
        layout.addView(inputKg)
        layout.addView(TextView(this).apply { text = "Repetitions (Reps):"; setTypeface(null, Typeface.BOLD); setPadding(0, 16, 0, 0) })
        layout.addView(inputReps)

        AlertDialog.Builder(this)
            .setTitle("🏋️ Edit Set ${setIndex + 1}")
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                val kg = inputKg.text.toString().toIntOrNull()?.coerceIn(0, 500) ?: 20
                val reps = inputReps.text.toString().toIntOrNull()?.coerceIn(1, 200) ?: 12
                setObj.put("kg", kg).put("reps", reps)
                prefs.edit().putString("workout_sets_json", setsStateObj.toString()).apply()
                renderWorkoutStudioTab()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showExerciseVideoGuideDialog(ex: ExerciseMeta) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0B1324"))
            setPadding(44, 40, 44, 36)
        }

        val tvTitle = TextView(this).apply {
            text = "🎬 ${ex.name}"
            setTextColor(Color.WHITE)
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 14)
        }

        val bigAnimPlayer = ExerciseAnimView(this, ex.animType).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 380).apply {
                setMargins(0, 0, 0, 18)
            }
        }

        val tvFocusArea = TextView(this).apply {
            text = "🎯 Focus Area: ${ex.secondaryMuscles}\n🏋️ Equipment: ${ex.equipment}"
            setTextColor(Color.parseColor("#F87171"))
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 14)
        }

        val tvSteps = TextView(this).apply {
            text = "📋 HOW TO DO:\n${ex.howToSteps}"
            setTextColor(Color.parseColor("#E2E8F0"))
            textSize = 13f
            setLineSpacing(6f, 1.1f)
            setPadding(0, 0, 0, 20)
        }

        val btnWatchOnlineVideo = createActionButton("📹 Watch HD Video Tutorial (Workout.Cool / YT)", "#DC2626", "#F87171") {
            val query = Uri.encode("${ex.name} proper form exercise demonstration")
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=$query")))
        }

        container.addView(tvTitle)
        container.addView(bigAnimPlayer)
        container.addView(tvFocusArea)
        container.addView(tvSteps)
        container.addView(btnWatchOnlineVideo)

        AlertDialog.Builder(this)
            .setView(container)
            .setPositiveButton("Done", null)
            .show()
    }

    private fun logWorkoutSession(type: String, mins: Int, notes: String) {
        val count = prefs.getInt("workouts_completed", 0) + 1
        val totalMins = prefs.getInt("workout_minutes", 0) + mins
        val timeStamp = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date())

        val logs = JSONArray(prefs.getString("workout_logs_json", "[]"))
        logs.put(JSONObject().put("type", type).put("mins", mins).put("notes", notes).put("time", timeStamp))

        prefs.edit()
            .putInt("workouts_completed", count)
            .putInt("workout_minutes", totalMins)
            .putString("workout_logs_json", logs.toString())
            .apply()
        addXp(60, "💪 $type (${mins}m) Logged! +60 XP")
        refreshAllUI()
    }

    private fun showCustomWorkoutDialog() {
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(50, 32, 50, 16) }
        val inputType = EditText(this).apply { hint = "Workout Name (e.g., Push Day / Morning Run)" }
        val inputMins = EditText(this).apply {
            hint = "Duration in Minutes"
            setRawInputType(InputType.TYPE_CLASS_NUMBER)
            setText("45")
        }
        val inputNotes = EditText(this).apply { hint = "Sets / Reps / Notes" }
        layout.addView(inputType); layout.addView(inputMins); layout.addView(inputNotes)

        AlertDialog.Builder(this)
            .setTitle("🏋️ Log Custom Workout")
            .setView(layout)
            .setPositiveButton("Log") { _, _ ->
                val type = inputType.text.toString().trim().ifEmpty { "Custom Workout" }
                val mins = inputMins.text.toString().toIntOrNull()?.coerceIn(1, 360) ?: 30
                logWorkoutSession(type, mins, inputNotes.text.toString().trim())
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ====================================================
    // TAB 5: SMART APP & YOUTUBE SHORTS SHIELD
    // ====================================================
    private fun buildShieldTab() {
        val shieldCard = createGlassCard("#10192D", "#2A4365")
        shieldCard.addView(createSectionLabel("REGAIN SMART APP & YOUTUBE SHORTS SHIELD"))

        var isBlockerEnabled = prefs.getBoolean("blocker_enabled", false)
        btnBlockerToggle = Button(this).apply {
            isAllCaps = false
            updateBlockerButtonStyle(this, isBlockerEnabled)
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            params.setMargins(0, 12, 0, 20)
            layoutParams = params
            setOnClickListener {
                if (isBlockerEnabled && isTimerRunning && cbStrictLock.isChecked) {
                    Toast.makeText(this@MainActivity, "🔒 Strict Lock is ON! Finish your focus session first.", Toast.LENGTH_LONG).show()
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
            shieldCard.addView(CheckBox(this).apply {
                text = label
                setTextColor(Color.WHITE)
                textSize = 13.5f
                isChecked = prefs.getBoolean(key, defaultVal)
                setPadding(12, 12, 12, 12)
                setOnCheckedChangeListener { _, checked -> prefs.edit().putBoolean(key, checked).apply() }
            })
        }

        val btnOpenAcc = createActionButton("⚙️ 1. Open Accessibility Settings", "#1E293B", "#38BDF8") { openAccessibilitySettings() }
        val btnOpenAppInfo = createActionButton("🔓 2. iQOO Z9 Android 14: Allow Restricted Settings", "#1E293B", "#FACC15") {
            try {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                })
                Toast.makeText(this, "Tap ⋮ (top-right) -> 'Allow restricted settings', and set Battery to Unrestricted!", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Could not open App Info", Toast.LENGTH_SHORT).show()
            }
        }
        (btnOpenAcc.layoutParams as LinearLayout.LayoutParams).setMargins(0, 20, 0, 12)
        (btnOpenAppInfo.layoutParams as LinearLayout.LayoutParams).setMargins(0, 0, 0, 0)

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
                    "Mod 1 • Theoretical Framework & BRS",
                    "Mod 2 • Depreciation, Inventories & Final Accounts",
                    "Mod 3 • Partnership & Company Accounts"
                )))
                put(createSubjectPack("CA Found: Business Laws", "CA Foundation", listOf(
                    "Mod 1 • Indian Contract Act, 1872",
                    "Mod 2 • Sale of Goods Act, 1930 & Partnership Act",
                    "Mod 3 • LLP Act & Companies Act, 2013"
                )))
                put(createSubjectPack("CA Found: Quantitative Aptitude", "CA Foundation", listOf(
                    "Part A • Time Value of Money & Equations",
                    "Part B • Logical Reasoning",
                    "Part C • Statistics & Probability"
                )))
                put(createSubjectPack("B.Com: Financial Accounting & Mgmt", "B.Com", listOf(
                    "Unit 1 • Accounting Standards & BRS",
                    "Unit 2 • Hire Purchase & Inland Branches",
                    "Unit 3 • Principles of Business Management"
                )))
            }
            prefs.edit().putString("syllabus_json", defaultSubjects.toString()).apply()
        }

        if (!prefs.contains("habits_matrix_json")) {
            val emptyWeek = listOf(false, false, false, false, false, false, false)
            val defaultHabits = JSONArray().apply {
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

        val examName = prefs.getString("exam_name", "CA & B.Com Target Exam")
        val examDays = prefs.getInt("exam_days_left", 45)
        tvExamCountdown.text = "⏳ $examName: $examDays Days Remaining (Tap to Edit)"

        val studyGoalPct = ((totalMinutes * 100) / 180).coerceAtMost(100)
        focusRingView.setProgress(studyGoalPct, "${totalMinutes}m / 180m")

        val habitsArray = JSONArray(prefs.getString("habits_matrix_json", "[]"))
        val todayIndex = ((Calendar.getInstance().get(Calendar.DAY_OF_WEEK) + 5) % 7)
        var todayDone = 0
        for (i in 0 until habitsArray.length()) {
            if (habitsArray.getJSONObject(i).getJSONArray("days").getBoolean(todayIndex)) todayDone++
        }
        val habitPct = if (habitsArray.length() > 0) (todayDone * 100) / habitsArray.length() else 0
        habitRingView.setProgress(habitPct, "$todayDone/${habitsArray.length()} Done")

        tvFocusMetricSummary.text =
            "⏱️ Study: ${totalMinutes / 60}h ${totalMinutes % 60}m ($sessions blocks)  •  " +
            "🛡️ Blocked: $blockedCount  •  🏋️ Workouts: $workouts"

        if (isAccessibilityServiceEnabled()) {
            tvAccessibilityBanner.text = "🟢 Regain Accessibility Shield: ACTIVE & PROTECTING"
            tvAccessibilityBanner.setTextColor(Color.parseColor("#6EE7B7"))
            tvAccessibilityBanner.background = createGlassDrawable("#064E3B", "#10B981", 22f)
        } else {
            tvAccessibilityBanner.text = "🔴 Shield Offline: Tap to Enable in Accessibility Settings"
            tvAccessibilityBanner.setTextColor(Color.parseColor("#FCA5A5"))
            tvAccessibilityBanner.background = createGlassDrawable("#7F1D1D", "#EF4444", 22f)
        }

        renderSyllabusCards()
        renderConsistencyTab()
        renderWorkoutStudioTab()
    }

    private fun addXp(amount: Int, toastMsg: String) {
        val newXp = (prefs.getInt("hunter_xp", 0) + amount).coerceAtLeast(0)
        prefs.edit().putInt("hunter_xp", newXp).apply()
        Toast.makeText(this, "⚡ $toastMsg", Toast.LENGTH_SHORT).show()
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val enabledServices = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
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
        Toast.makeText(this, "🎉 +${addedMins}m logged for $selectedSubject! (+$earnedXp XP)", Toast.LENGTH_LONG).show()
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
                playCompletionSound()
                recordCompletedSession()
            }
        }.start()

        isTimerRunning = true
        tvTimerStatus.text = "🔥 Focusing on $selectedSubject — Shield Locked"
        btnStartPause.text = "Pause"
    }

    private fun playCompletionSound() {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            RingtoneManager.getRingtone(applicationContext, uri)?.play()
        } catch (_: Exception) {}
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

    // ====================================================
    // UI HELPERS & CUSTOM CANVAS VIEWS
    // ====================================================
    private fun createStatColumn(value: String, label: String, colorHex: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(TextView(this@MainActivity).apply {
                text = value
                setTextColor(Color.parseColor(colorHex))
                textSize = 22f
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
            })
            addView(TextView(this@MainActivity).apply {
                text = label
                setTextColor(Color.parseColor("#94A3B8"))
                textSize = 11.5f
                gravity = Gravity.CENTER
            })
        }
    }

    private fun createMiniTagButton(label: String, bgHex: String, strokeHex: String, textHex: String, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            text = label
            setTextColor(Color.parseColor(textHex))
            textSize = 11.5f
            setTypeface(null, Typeface.BOLD)
            background = createGlassDrawable(bgHex, strokeHex, 16f)
            setPadding(20, 12, 20, 12)
            val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            p.setMargins(8, 0, 0, 0)
            layoutParams = p
            setOnClickListener { onClick() }
        }
    }

    private fun createSectionLabel(title: String): TextView {
        return TextView(this).apply {
            text = title
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            letterSpacing = 0.07f
            setPadding(0, 0, 0, 10)
        }
    }

    private fun createGlassCard(fillHex: String, strokeHex: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = createGlassDrawable(fillHex, strokeHex, 36f)
            setPadding(36, 36, 36, 36)
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            params.setMargins(0, 0, 0, 30)
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
            setTypeface(null, Typeface.BOLD)
            background = createGlassDrawable("#1E293B", "#334155", 24f)
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            params.setMargins(5, 0, 5, 0)
            layoutParams = params
            setOnClickListener { onClick() }
        }
    }

    private fun createActionButton(label: String, fillHex: String, strokeHex: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            isAllCaps = false
            setTextColor(Color.WHITE)
            textSize = 13.5f
            setTypeface(null, Typeface.BOLD)
            background = createGlassDrawable(fillHex, strokeHex, 26f)
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            params.setMargins(6, 0, 6, 0)
            layoutParams = params
            setOnClickListener { onClick() }
        }
    }

    // Custom Anatomical Front & Back Muscle Highlight View (Screenshot 2 & 3 Style)
    class MuscleBodyMapView(context: Context, private val activeMuscle: String) : View(context) {
        private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#334155") }
        private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#EF4444") }
        private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E2E8F0")
            textSize = 26f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        private fun paintFor(vararg groups: String): Paint {
            if (activeMuscle == "All") return highlightPaint
            return if (groups.any { it.equals(activeMuscle, true) }) highlightPaint else basePaint
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            val frontCx = w * 0.28f
            val backCx = w * 0.72f

            canvas.drawText("FRONT MUSCLE MAP", frontCx, 30f, labelPaint)
            canvas.drawText("BACK MUSCLE MAP", backCx, 30f, labelPaint)

            // Front Figure
            canvas.drawCircle(frontCx, 72f, 22f, basePaint) // Head
            canvas.drawRoundRect(frontCx - 48f, 100f, frontCx + 48f, 125f, 12f, 12f, paintFor("Shoulders")) // Shoulders
            canvas.drawRoundRect(frontCx - 34f, 108f, frontCx + 34f, 152f, 10f, 10f, paintFor("Chest")) // Chest
            canvas.drawRoundRect(frontCx - 26f, 156f, frontCx + 26f, 212f, 8f, 8f, paintFor("Abs", "Core")) // Abs
            canvas.drawRoundRect(frontCx - 56f, 126f, frontCx - 38f, 198f, 10f, 10f, paintFor("Arms")) // L Arm
            canvas.drawRoundRect(frontCx + 38f, 126f, frontCx + 56f, 198f, 10f, 10f, paintFor("Arms")) // R Arm
            canvas.drawRoundRect(frontCx - 30f, 218f, frontCx - 8f, 308f, 10f, 10f, paintFor("Legs")) // L Leg
            canvas.drawRoundRect(frontCx + 8f, 218f, frontCx + 30f, 308f, 10f, 10f, paintFor("Legs")) // R Leg

            // Back Figure
            canvas.drawCircle(backCx, 72f, 22f, basePaint) // Head
            canvas.drawRoundRect(backCx - 48f, 100f, backCx + 48f, 125f, 12f, 12f, paintFor("Shoulders")) // Rear Delts
            canvas.drawRoundRect(backCx - 36f, 112f, backCx + 36f, 185f, 12f, 12f, paintFor("Back")) // Lats / Back
            canvas.drawRoundRect(backCx - 28f, 188f, backCx + 28f, 218f, 10f, 10f, paintFor("Core", "Legs")) // Lower Back / Glutes
            canvas.drawRoundRect(backCx - 56f, 126f, backCx - 38f, 198f, 10f, 10f, paintFor("Arms")) // L Tricep
            canvas.drawRoundRect(backCx + 38f, 126f, backCx + 56f, 198f, 10f, 10f, paintFor("Arms")) // R Tricep
            canvas.drawRoundRect(backCx - 30f, 222f, backCx - 8f, 308f, 10f, 10f, paintFor("Legs")) // L Hamstring
            canvas.drawRoundRect(backCx + 8f, 222f, backCx + 30f, 308f, 10f, 10f, paintFor("Legs")) // R Hamstring
        }
    }

    // Live Animated Exercise Visual Player (Demonstrates Rep Mechanics in Real Time)
    class ExerciseAnimView(context: Context, private val animType: Int) : View(context) {
        private var phase = 0f
        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#162238") }
        private val bonePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E2E8F0")
            strokeWidth = 10f
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        private val muscleGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#EF4444")
            strokeWidth = 14f
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        private val equipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#38BDF8")
            strokeWidth = 8f
            style = Paint.Style.FILL_AND_STROKE
        }
        private val animator = ValueAnimator.ofFloat(0f, 6.283f).apply {
            duration = 1600L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                phase = (sin((it.animatedValue as Float).toDouble()).toFloat() + 1f) / 2f
                invalidate()
            }
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            animator.start()
        }

        override fun onDetachedFromWindow() {
            super.onDetachedFromWindow()
            animator.cancel()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            canvas.drawRoundRect(0f, 0f, w, h, 24f, 24f, bgPaint)

            val cx = w / 2f
            val cy = h / 2f

            when (animType) {
                0 -> { // Push-up / Bench Press motion
                    val yShift = phase * 22f
                    canvas.drawCircle(cx - 42f, cy - 10f + yShift, 12f, bonePaint) // Head
                    canvas.drawLine(cx - 30f, cy - 6f + yShift, cx + 40f, cy + 25f, muscleGlowPaint) // Torso
                    canvas.drawLine(cx - 18f, cy + yShift, cx - 18f, cy + 32f, bonePaint) // Arms pressing
                }
                1 -> { // Bent-Over Row motion
                    val pullY = cy + 25f - phase * 26f
                    canvas.drawCircle(cx - 25f, cy - 32f, 12f, bonePaint)
                    canvas.drawLine(cx - 18f, cy - 22f, cx + 18f, cy + 5f, muscleGlowPaint) // Back
                    canvas.drawLine(cx + 18f, cy + 5f, cx + 10f, cy + 45f, bonePaint) // Legs
                    canvas.drawLine(cx - 10f, cy - 15f, cx - 12f, pullY, bonePaint) // Pulling Arm
                    canvas.drawCircle(cx - 12f, pullY, 12f, equipPaint) // Barbell plate
                }
                2 -> { // Bicep Curl motion
                    val handY = cy + 22f - phase * 38f
                    canvas.drawCircle(cx, cy - 42f, 12f, bonePaint)
                    canvas.drawLine(cx, cy - 28f, cx, cy + 35f, bonePaint)
                    canvas.drawLine(cx, cy - 22f, cx + 22f, handY, muscleGlowPaint)
                    canvas.drawCircle(cx + 24f, handY, 9f, equipPaint)
                }
                3 -> { // Squat / Lunge motion
                    val hipDrop = phase * 26f
                    canvas.drawCircle(cx, cy - 42f + hipDrop, 12f, bonePaint)
                    canvas.drawLine(cx, cy - 28f + hipDrop, cx - 8f, cy + 4f + hipDrop, bonePaint)
                    canvas.drawLine(cx - 8f, cy + 4f + hipDrop, cx + 16f, cy + 22f, muscleGlowPaint) // Thigh
                    canvas.drawLine(cx + 16f, cy + 22f, cx + 4f, cy + 48f, bonePaint) // Calf
                }
                4 -> { // Overhead Press / Lat Pulldown
                    val barY = cy - 15f - phase * 32f
                    canvas.drawCircle(cx, cy - 8f, 12f, bonePaint)
                    canvas.drawLine(cx, cy + 6f, cx, cy + 45f, muscleGlowPaint)
                    canvas.drawLine(cx - 28f, barY, cx + 28f, barY, equipPaint)
                    canvas.drawLine(cx - 16f, cy + 8f, cx - 22f, barY, bonePaint)
                    canvas.drawLine(cx + 16f, cy + 8f, cx + 22f, barY, bonePaint)
                }
                else -> { // Ab Wheel / Core Rollout
                    val reachX = cx - 10f - phase * 28f
                    canvas.drawCircle(reachX, cy - 8f, 11f, bonePaint)
                    canvas.drawLine(reachX + 8f, cy, cx + 28f, cy + 18f, muscleGlowPaint)
                    canvas.drawCircle(reachX - 6f, cy + 22f, 10f, equipPaint)
                }
            }
        }
    }

    class CircularRingView(context: Context, accentHex: String, private val label: String) : View(context) {
        private var percentage = 0
        private var subLabel = "0%"
        private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 22f; color = Color.parseColor("#1E293B"); strokeCap = Paint.Cap.ROUND
        }
        private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 22f; color = Color.parseColor(accentHex); strokeCap = Paint.Cap.ROUND
        }
        private val pctPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textSize = 48f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); textAlign = Paint.Align.CENTER
        }
        private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#94A3B8"); textSize = 24f; textAlign = Paint.Align.CENTER
        }
        private val rect = RectF()

        fun setProgress(pct: Int, detail: String) {
            percentage = pct.coerceIn(0, 100); subLabel = detail; invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val pad = 28f
            rect.set(pad, pad, width - pad, height - pad)
            canvas.drawArc(rect, 0f, 360f, false, trackPaint)
            canvas.drawArc(rect, -90f, (percentage / 100f) * 360f, false, progressPaint)
            val cx = width / 2f; val cy = height / 2f
            canvas.drawText("$percentage%", cx, cy - 4f, pctPaint)
            canvas.drawText(label, cx, cy + 30f, labelPaint)
            canvas.drawText(subLabel, cx, cy + 58f, labelPaint)
        }
    }

    class WeeklyBarChartView(context: Context, private val dayScores: IntArray) : View(context) {
        private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1E293B") }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#CBD5E1"); textSize = 24f; textAlign = Paint.Align.CENTER
        }
        private val days = arrayOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        private val colors = arrayOf("#38BDF8", "#38BDF8", "#818CF8", "#A78BFA", "#F472B6", "#34D399", "#10B981")
        private val rect = RectF()

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (width == 0 || height == 0) return
            val colWidth = width / 7f
            val maxBarHeight = height - 70f
            for (i in 0 until 7) {
                val cx = colWidth * i + colWidth / 2f
                val barW = colWidth * 0.42f
                val topTrack = 26f
                val bottomTrack = topTrack + maxBarHeight
                rect.set(cx - barW / 2f, topTrack, cx + barW / 2f, bottomTrack)
                canvas.drawRoundRect(rect, 14f, 14f, bgPaint)
                val pct = dayScores.getOrElse(i) { 0 }.coerceIn(0, 100)
                val fillH = (pct / 100f) * maxBarHeight
                barPaint.color = Color.parseColor(colors[i])
                rect.set(cx - barW / 2f, bottomTrack - fillH, cx + barW / 2f, bottomTrack)
                canvas.drawRoundRect(rect, 14f, 14f, barPaint)
                canvas.drawText("$pct%", cx, 20f, textPaint)
                canvas.drawText(days[i], cx, height - 10f, textPaint)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }
}
