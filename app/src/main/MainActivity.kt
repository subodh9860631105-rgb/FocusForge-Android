package com.focusforge.pro

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
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
import android.view.MotionEvent
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
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private var countDownTimer: CountDownTimer? = null
    private var restCountDownTimer: CountDownTimer? = null
    private var workoutStopwatchTimer: CountDownTimer? = null

    private var isTimerRunning = false
    private var initialTimeMillis: Long = 45 * 60 * 1000L
    private var timeLeftInMillis: Long = initialTimeMillis
    private var restTimeLeftSeconds: Int = 0
    private var workoutElapsedSeconds: Int = 0
    private var isWorkoutStopwatchRunning = false

    private var selectedSubject = "CA Found: Business Laws"
    private var selectedCourseFilter = "All"

    // Workout Studio State (4 Clean Sub-Tabs: 0=Training, 1=Workout.Cool Finder, 2=Exercises, 3=Report)
    private var workoutSubTab = 0
    private var selectedMuscleFilter = "All"
    private var selectedEquipmentFilter = "All"
    private var selectedSplitFilter = "All"
    private var exerciseSearchQuery = ""
    private val expandedExerciseIds = mutableSetOf("row_bb", "bench_bb", "pushup_bw")
    private var currentTab = 0

    // Main Tab Containers
    private lateinit var tabFocusContainer: LinearLayout
    private lateinit var tabSyllabusContainer: LinearLayout
    private lateinit var tabConsistencyContainer: LinearLayout
    private lateinit var tabWorkoutContainer: LinearLayout
    private lateinit var tabShieldContainer: LinearLayout
    private val navButtons = mutableListOf<Button>()

    // Header & Focus Views
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

    // Dynamic Containers
    private lateinit var courseFilterRow: LinearLayout
    private lateinit var syllabusListContainer: LinearLayout
    private lateinit var formulaVaultContainer: LinearLayout
    private lateinit var consistencyContentContainer: LinearLayout
    private lateinit var workoutContentContainer: LinearLayout
    private lateinit var customBlockedAppsContainer: LinearLayout
    private lateinit var btnBlockerToggle: Button
    private var tvRestTimerStatus: TextView? = null
    private var tvWorkoutStopwatch: TextView? = null

    data class ExerciseMeta(
        val id: String,
        val name: String,
        val muscle: String,
        val secondaryMuscles: String,
        val equipment: String,
        val splitTag: String,
        val animType: Int,
        val howToSteps: String,
        val proTips: String,
        val isCustom: Boolean = false
    )

    private val defaultExerciseCatalog = listOf(
        ExerciseMeta(
            "row_bb", "Bent-Over Row * Barbell", "Back", "Back • Chest • ABS • ARMS", "Barbell", "Pull", 1,
            "1. Stand with feet shoulder-width apart, knees slightly bent.\n2. Hinge forward at the hips (45° angle) keeping your spine neutral.\n3. Pull the barbell toward your lower chest/upper abs, driving elbows back.\n4. Squeeze your lats at the top for 1 second, then lower slowly.",
            "Keep the bar close to your thighs and avoid jerking your torso upward."
        ),
        ExerciseMeta(
            "lat_cable", "Lat Pulldown * Cable", "Back", "Back • Arms • Shoulders", "Cable Machine", "Pull", 4,
            "1. Sit tall with thighs locked under the pad and grip the bar wider than shoulder-width.\n2. Depress your shoulder blades and pull the bar smoothly down to your upper chest.\n3. Squeeze your lats hard at the bottom.\n4. Control the weight all the way back up until your lats are fully stretched.",
            "Think about pulling your elbows down into your back pockets rather than pulling with your hands."
        ),
        ExerciseMeta(
            "deadlift_bb", "Deadlift * Barbell", "Back", "Back • Legs • Glutes • Core", "Barbell", "Pull", 1,
            "1. Stand with mid-foot under the barbell, hip-width stance.\n2. Hinge down and grip the bar just outside your shins.\n3. Brace your core, flatten your back, and drive the floor away with your legs.\n4. Lock out hips and knees together at the top.",
            "Never round your lower back; keep the bar dragging lightly along your shins."
        ),
        ExerciseMeta(
            "back_ext", "Back Extension on Floor / Bench", "Back", "Lower Back • Glutes • Core", "Body Weight", "Home", 5,
            "1. Lie face down on a mat or Roman chair with hands behind your head or across chest.\n2. Engage your lower back and glutes to lift your upper torso off the floor.\n3. Hold briefly at the top, then lower gently.",
            "Avoid hyperextending your neck; keep your gaze down toward the mat."
        ),
        ExerciseMeta(
            "bench_bb", "Flat Bench Press * Barbell", "Chest", "Chest • Shoulders • Arms", "Barbell", "Push", 0,
            "1. Lie flat on the bench with your eyes directly underneath the bar.\n2. Plant feet firmly on the floor and retract your shoulder blades.\n3. Lower the bar under control to your mid-chest with elbows tucked ~45°.\n4. Press the bar up and slightly back toward your shoulders.",
            "Keep your wrists straight and glutes in contact with the bench throughout the set."
        ),
        ExerciseMeta(
            "incline_db", "Incline Chest Press * Dumbbell", "Chest", "Chest • Shoulders • Triceps", "Dumbbell", "Push", 0,
            "1. Set bench to a 30°–45° incline and sit back with dumbbells on thighs.\n2. Kick weights up to shoulder level with palms facing forward.\n3. Press dumbbells overhead until arms are extended without clanking them.\n4. Lower slowly until you feel a deep stretch in your upper chest.",
            "A 30° angle targets the upper pecs best without overworking the front delts."
        ),
        ExerciseMeta(
            "pushup_bw", "Push-up * Classic", "Chest", "Chest • Arms • Abs", "Body Weight", "Home", 0,
            "1. Set hands slightly wider than shoulder-width in a rigid plank position.\n2. Brace your abs and glutes so your body forms a straight line.\n3. Lower your chest until it is 1 inch above the floor.\n4. Push explosively back to full arm extension.",
            "Do not let your hips sag or flare your elbows out to 90 degrees."
        ),
        ExerciseMeta(
            "cable_fly", "Chest Fly * Cable Machine", "Chest", "Chest • Front Delts", "Cable Machine", "Push", 0,
            "1. Set both pulleys to chest height and step forward into a staggered stance.\n2. With a slight bend in your elbows, bring handles together in a wide arc.\n3. Squeeze your inner chest hard where hands meet.\n4. Return slowly until chest is stretched.",
            "Maintain the exact same elbow bend throughout the movement—hug a barrel."
        ),
        ExerciseMeta(
            "curl_db", "Bicep Curl * Dumbbell", "Arms", "Arms • Biceps • Forearms", "Dumbbell", "Pull", 2,
            "1. Stand tall holding dumbbells at your sides with palms facing forward.\n2. Pin your elbows tightly against your ribs.\n3. Curl the weights upward while supinating (twisting) your wrists.\n4. Contract biceps hard at the top and lower for a 2-second count.",
            "Avoid swinging your lower back; if you must swing, reduce the weight by 2.5 kg."
        ),
        ExerciseMeta(
            "tricep_cable", "Tricep Rope Pushdown * Cable", "Arms", "Arms • Triceps", "Cable Machine", "Push", 2,
            "1. Attach a rope to a high pulley and grab ends with a neutral grip.\n2. Pin upper arms to your sides and lean slightly forward.\n3. Extend forearms downward, pulling the rope ends apart at the bottom.\n4. Squeeze triceps for 1 second, then return to 90°.",
            "Only your forearms should move; keep your shoulders and elbows locked in place."
        ),
        ExerciseMeta(
            "shoulder_db", "Overhead Press * Dumbbell", "Shoulders", "Shoulders • Arms • Core", "Dumbbell", "Push", 4,
            "1. Sit on an upright bench or stand tall holding dumbbells at ear level.\n2. Brace your core and press weights vertically overhead.\n3. Stop just short of locking out and lower smoothly back to chin height.",
            "Press in a slight natural arc so the weights finish directly over your ears."
        ),
        ExerciseMeta(
            "lat_raise_db", "Lateral Raise & Arm Circles", "Shoulders", "Shoulders • Side Delts", "Dumbbell", "Push", 4,
            "1. Stand tall with dumbbells at your sides and a slight bend in elbows.\n2. Raise arms out to the sides until elbows reach shoulder height.\n3. Pause briefly at the top, then lower slowly resisting gravity.",
            "Lead with your elbows and pinkies slightly higher than thumbs to isolate side delts."
        ),
        ExerciseMeta(
            "squat_smith", "Squat * Smith Machine / Barbell", "Legs", "Legs • Glutes • Core", "Smith Machine", "Legs", 3,
            "1. Position bar across upper traps, unhook safety latches, and set feet shoulder-width.\n2. Inhale, brace your abs, and sit hips back and down until thighs are parallel.\n3. Drive hard through your mid-foot and heels to stand tall.",
            "Keep your knees tracking in the same direction as your toes throughout the squat."
        ),
        ExerciseMeta(
            "lunge_db", "Backward Lunge * Dumbbell", "Legs", "Legs • Glutes • Hamstrings", "Dumbbell", "Legs", 3,
            "1. Stand tall holding dumbbells at your sides.\n2. Step one foot backward and lower your back knee toward the floor.\n3. Keep your front shin vertical and drive through your front heel to return.",
            "Stepping backward places less shear stress on the knees and activates glutes more."
        ),
        ExerciseMeta(
            "ab_wheel", "Ab Wheel Rollout", "Abs", "Abs • Core • Lower Back", "Body Weight", "Home", 5,
            "1. Kneel on a padded mat holding the ab wheel handles directly under shoulders.\n2. Brace your abs tightly (posterior pelvic tilt) and roll forward slowly.\n3. Go as far as you can without letting your lower back arch.\n4. Crunch abs to pull yourself back to the kneeling position.",
            "Exhale as you roll back in and keep your ribcage tucked toward your pelvis."
        ),
        ExerciseMeta(
            "side_plank", "Side Plank Knee to Elbow", "Abs", "Abs • Core • Obliques", "Body Weight", "Home", 5,
            "1. Prop yourself up on one forearm in a side plank with hips lifted high.\n2. Extend your top arm overhead and top leg straight.\n3. Crunch your top elbow and top knee together over your waist.\n4. Extend back out while keeping hips off the floor.",
            "Great no-equipment movement for obliques and lateral core stability."
        ),
        ExerciseMeta(
            "situp_twist", "Sit-up Twist & Reverse Plank", "Core", "Abs • Core • Lower Back", "Body Weight", "Home", 5,
            "1. Lie on your back with knees bent and hands lightly behind ears.\n2. Sit up and rotate your torso to touch your right elbow to your left knee.\n3. Lower back down and alternate to the opposite side.",
            "Rotate your entire ribcage rather than just pulling on your neck with your hands."
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
            setPadding(32, 46, 32, 46)
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

        // 5 MAIN TAB CONTAINERS
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

        restoreRunningTimerIfNeeded()
        switchTab(0)
        refreshAllUI()
    }

    override fun onResume() {
        super.onResume()
        restoreRunningTimerIfNeeded()
        refreshAllUI()
    }

    private fun restoreRunningTimerIfNeeded() {
        val wasRunning = prefs.getBoolean("timer_is_active", false)
        val endTime = prefs.getLong("timer_end_millis", 0L)
        if (wasRunning && endTime > 0L) {
            val remaining = endTime - System.currentTimeMillis()
            if (remaining > 1000L) {
                timeLeftInMillis = remaining
                if (!isTimerRunning) {
                    startTimer(resumeExisting = true)
                }
            } else {
                prefs.edit().putBoolean("timer_is_active", false).putLong("timer_end_millis", 0L).apply()
                isTimerRunning = false
                timeLeftInMillis = initialTimeMillis
                tvTimerDisplay.text = formatTime(initialTimeMillis)
                tvTimerStatus.text = "Session Complete! Great work!"
                btnStartPause.text = "Start Focus"
                playCompletionSound()
                recordCompletedSession()
            }
        }
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
            setOnCheckedChangeListener { buttonView, checked ->
                if (isTimerRunning && !checked) {
                    buttonView.isChecked = true
                    Toast.makeText(this@MainActivity, "🔒 Cannot disable Strict Mode while timer is running!", Toast.LENGTH_SHORT).show()
                    return@setOnCheckedChangeListener
                }
                prefs.edit().putBoolean("strict_lock_mode", checked).apply()
            }
        }

        val controlRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        btnStartPause = createActionButton("Start Focus", "#4F46E5", "#818CF8") {
            if (isTimerRunning) {
                if (cbStrictLock.isChecked) {
                    Toast.makeText(this, "🔒 Strict Mode is ON! Complete the session without pausing.", Toast.LENGTH_SHORT).show()
                } else {
                    pauseTimer()
                }
            } else {
                startTimer(resumeExisting = false)
            }
        }

        val btnReset = createActionButton("Reset", "#1E293B", "#334155") {
            if (isTimerRunning && cbStrictLock.isChecked) {
                Toast.makeText(this, "🔒 Strict Mode active! Finish your study session first.", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(this, "🔒 Finish current session first.", Toast.LENGTH_SHORT).show()
            return
        }
        countDownTimer?.cancel()
        isTimerRunning = false
        prefs.edit().putBoolean("timer_is_active", false).apply()
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
            Toast.makeText(this, "🔒 Strict Mode Active! Finish session first.", Toast.LENGTH_SHORT).show()
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
    // TAB 2: CA (FOUND / INTER / FINAL) & B.COM SYLLABUS + VAULT
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

        formulaVaultContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        tabSyllabusContainer.addView(formulaVaultContainer)
    }

    private fun renderSyllabusCards() {
        syllabusListContainer.removeAllViews()
        subjectChipRow.removeAllViews()
        courseFilterRow.removeAllViews()
        formulaVaultContainer.removeAllViews()

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

        // Render CA Law Sections, AS & Formula Quick-Vault Card
        val vaultCard = createGlassCard("#10192D", "#8B5CF6")
        vaultCard.addView(createSectionLabel("📝 CA & B.COM LAW SECTIONS, AS & FORMULA QUICK-VAULT"))

        val notesArr = JSONArray(prefs.getString("QuickVault_json", "[]"))
        for (n in 0 until notesArr.length()) {
            val noteObj = notesArr.getJSONObject(n)
            val tag = noteObj.optString("tag", "Law / Formula")
            val body = noteObj.optString("body", "")

            val noteRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = createGlassDrawable("#090F1D", "#2E1065", 20f)
                setPadding(22, 18, 18, 18)
                val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                p.setMargins(0, 0, 0, 12)
                layoutParams = p
            }

            noteRow.addView(TextView(this).apply {
                text = "📌 [$tag]\n$body"
                setTextColor(Color.parseColor("#E2E8F0"))
                textSize = 12.5f
                setLineSpacing(4f, 1.1f)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })

            noteRow.addView(createMiniTagButton("✕", "#450A0A", "#EF4444", "#FCA5A5") {
                notesArr.remove(n)
                prefs.edit().putString("QuickVault_json", notesArr.toString()).apply()
                renderSyllabusCards()
            })

            vaultCard.addView(noteRow)
        }

        vaultCard.addView(createActionButton("+ Add Law Section / AS / Formula Note", "#4C1D95", "#8B5CF6") {
            showAddVaultNoteDialog()
        })
        formulaVaultContainer.addView(vaultCard)
    }

    private fun showAddVaultNoteDialog() {
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(50, 32, 50, 16) }
        val inputTag = EditText(this).apply { hint = "Topic Tag (e.g., Contract Act Sec 10 / AS 2 / Costing)" }
        val inputBody = EditText(this).apply { hint = "Section Rule or Formula to Remember" }
        layout.addView(inputTag); layout.addView(inputBody)

        AlertDialog.Builder(this)
            .setTitle("📝 Add Quick Revision Note")
            .setView(layout)
            .setPositiveButton("Save Note") { _, _ ->
                val tag = inputTag.text.toString().trim().ifEmpty { "CA / B.Com Note" }
                val body = inputBody.text.toString().trim()
                if (body.isNotEmpty()) {
                    val arr = JSONArray(prefs.getString("QuickVault_json", "[]"))
                    arr.put(JSONObject().put("tag", tag).put("body", body))
                    prefs.edit().putString("QuickVault_json", arr.toString()).apply()
                    addXp(15, "Revision Note Added! (+15 XP)")
                    renderSyllabusCards()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
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
    private fun buildWorkoutStudioTab() {
        workoutContentContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        tabWorkoutContainer.addView(workoutContentContainer)
    }

    private fun getFullExerciseCatalog(): List<ExerciseMeta> {
        val list = mutableListOf<ExerciseMeta>()
        list.addAll(defaultExerciseCatalog)
        val customArr = JSONArray(prefs.getString("custom_exercises_json", "[]"))
        for (i in 0 until customArr.length()) {
            val o = customArr.getJSONObject(i)
            val m = o.optString("muscle", "Chest")
            list.add(
                ExerciseMeta(
                    id = o.optString("id", "cust_$i"),
                    name = o.optString("name", "Custom Exercise"),
                    muscle = m,
                    secondaryMuscles = "$m • Core",
                    equipment = o.optString("equipment", "Dumbbell"),
                    splitTag = o.optString("split", "Custom"),
                    animType = o.optInt("animType", 0),
                    howToSteps = o.optString("steps", "Maintain controlled form and full range of motion."),
                    proTips = "Focus on mind-muscle connection and controlled 2-second lowering.",
                    isCustom = true
                )
            )
        }
        return list
    }

    private fun renderWorkoutStudioTab() {
        workoutContentContainer.removeAllViews()

        // Top Sub-Navigation Bar (Training | Generator | Exercises | Report)
        val subNavCard = createGlassCard("#10192D", "#2A4365").apply {
            setPadding(20, 20, 20, 20)
        }
        val subNavRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val subTabs = listOf(
            "🏋️ Training",
            "🧭 Generator",
            "📋 Exercises",
            "📊 Report"
        )
        subTabs.forEachIndexed { idx, title ->
            val isSel = workoutSubTab == idx
            val btn = Button(this).apply {
                text = title
                isAllCaps = false
                textSize = 11f
                setTypeface(null, Typeface.BOLD)
                setTextColor(if (isSel) Color.parseColor("#060A12") else Color.parseColor("#E2E8F0"))
                background = if (isSel) {
                    createGlassDrawable("#34D399", "#10B981", 24f)
                } else {
                    createGlassDrawable("#090F1D", "#1E293B", 24f)
                }
                setPadding(12, 12, 12, 12)
                val p = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                p.setMargins(4, 0, 4, 0)
                layoutParams = p
                setOnClickListener {
                    workoutSubTab = idx
                    renderWorkoutStudioTab()
                }
            }
            subNavRow.addView(btn)
        }
        subNavCard.addView(subNavRow)
        workoutContentContainer.addView(subNavCard)

        when (workoutSubTab) {
            0 -> renderActiveTrainingSubTab()
            1 -> renderWorkoutCoolGeneratorSubTab()
            2 -> renderExerciseLibrarySubTab()
            3 -> renderFitnessReportSubTab()
        }
    }

    // ----------------------------------------------------
    // SUB-TAB 0: WEIGHTLIFTING TOOL FLEXIBLE (ACTIVE TRAINING)
    // ----------------------------------------------------
    private fun renderActiveTrainingSubTab() {
        // 1. Live Session Stopwatch & Rest Timer Bar (Screenshot 7 Style)
        val topToolCard = createGlassCard("#10192D", "#10B981")
        val headerBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 14)
        }

        val activeFilterTitle = if (selectedMuscleFilter == "All" && selectedEquipmentFilter == "All") {
            "Active Workout"
        } else {
            "${if (selectedMuscleFilter == "All") "Full Body" else selectedMuscleFilter} • ${if (selectedEquipmentFilter == "All") "All Equip" else selectedEquipmentFilter}"
        }

        val tvWorkoutHeader = TextView(this).apply {
            text = "🏋️ $activeFilterTitle"
            setTextColor(Color.WHITE)
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        tvWorkoutStopwatch = TextView(this).apply {
            text = String.format(Locale.getDefault(), "⏱️ %02d:%02d", workoutElapsedSeconds / 60, workoutElapsedSeconds % 60)
            setTextColor(Color.parseColor("#4ADE80"))
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            background = createGlassDrawable("#064E3B", "#10B981", 20f)
            setPadding(24, 10, 24, 10)
            setOnClickListener { toggleWorkoutStopwatch() }
        }

        val btnFinishWorkout = Button(this).apply {
            text = "Finish"
            isAllCaps = false
            setTextColor(Color.WHITE)
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            background = createGlassDrawable("#1E293B", "#38BDF8", 20f)
            setPadding(26, 10, 26, 10)
            val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            p.setMargins(12, 0, 0, 0)
            layoutParams = p
            setOnClickListener {
                val mins = (workoutElapsedSeconds / 60).coerceAtLeast(25)
                val vol = calculateTotalVolumeKg()
                stopWorkoutStopwatch()
                logWorkoutSession(activeFilterTitle, mins, "Total Volume: $vol kg")
                workoutSubTab = 3
                renderWorkoutStudioTab()
            }
        }

        headerBar.addView(tvWorkoutHeader)
        headerBar.addView(tvWorkoutStopwatch)
        headerBar.addView(btnFinishWorkout)
        topToolCard.addView(headerBar)

        tvRestTimerStatus = TextView(this).apply {
            text = if (restTimeLeftSeconds > 0) {
                String.format(Locale.getDefault(), "🟢 REST TIMER: %02d:%02d", restTimeLeftSeconds / 60, restTimeLeftSeconds % 60)
            } else {
                "⏱️ Rest Timer Ready (Auto-starts 60s when you check a set)"
            }
            setTextColor(Color.parseColor("#A7F3D0"))
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            background = createGlassDrawable("#090F1D", "#1E293B", 18f)
            setPadding(20, 14, 20, 14)
        }
        topToolCard.addView(tvRestTimerStatus)

        val restBtnsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 10, 0, 0)
        }
        restBtnsRow.addView(createPillButton("30s") { startRestTimer(30) })
        restBtnsRow.addView(createPillButton("60s") { startRestTimer(60) })
        restBtnsRow.addView(createPillButton("90s") { startRestTimer(90) })
        restBtnsRow.addView(createPillButton("+15s") { startRestTimer(restTimeLeftSeconds + 15) })
        restBtnsRow.addView(createPillButton("Stop") { stopRestTimer() })
        topToolCard.addView(restBtnsRow)
        workoutContentContainer.addView(topToolCard)

        // 2. Quick Split & Filter Chips Bar
        val splitScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            setPadding(0, 0, 0, 20)
        }
        val splitRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val splits = listOf(
            "All" to "🔥 All Exercises",
            "Push" to "💪 Push (Chest/Shld/Tri)",
            "Pull" to "🦅 Pull (Back/Biceps)",
            "Legs" to "🦵 Legs & Glutes",
            "Home" to "🏠 Home (No Equip)"
        )
        splits.forEach { (key, label) ->
            val isSel = selectedSplitFilter == key
            val chip = Button(this).apply {
                text = label
                isAllCaps = false
                textSize = 11.5f
                setTypeface(null, Typeface.BOLD)
                setTextColor(if (isSel) Color.parseColor("#060A12") else Color.WHITE)
                background = if (isSel) createGlassDrawable("#38BDF8", "#7DD3FC", 28f) else createGlassDrawable("#10192D", "#243B61", 28f)
                setPadding(26, 10, 26, 10)
                val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                p.setMargins(0, 0, 12, 0)
                layoutParams = p
                setOnClickListener {
                    selectedSplitFilter = key
                    if (key == "Home") {
                        selectedEquipmentFilter = "Body Weight"
                        selectedMuscleFilter = "All"
                    } else if (key != "All") {
                        selectedEquipmentFilter = "All"
                        selectedMuscleFilter = "All"
                    }
                    renderWorkoutStudioTab()
                }
            }
            splitRow.addView(chip)
        }
        splitScroll.addView(splitRow)
        workoutContentContainer.addView(splitScroll)

        // Active filter status pill if filtered from Generator
        if (selectedMuscleFilter != "All" || selectedEquipmentFilter != "All") {
            val filterBanner = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = createGlassDrawable("#1E293B", "#38BDF8", 22f)
                setPadding(24, 14, 20, 14)
                val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                p.setMargins(0, 0, 0, 20)
                layoutParams = p
            }
            filterBanner.addView(TextView(this).apply {
                text = "🎯 Active Filter: Muscle = $selectedMuscleFilter  •  Equip = $selectedEquipmentFilter"
                setTextColor(Color.parseColor("#38BDF8"))
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            filterBanner.addView(createMiniTagButton("✕ Reset", "#450A0A", "#EF4444", "#FCA5A5") {
                selectedMuscleFilter = "All"
                selectedEquipmentFilter = "All"
                selectedSplitFilter = "All"
                renderWorkoutStudioTab()
            })
            workoutContentContainer.addView(filterBanner)
        }

        // 3. Flexible Weightlifting Exercise Cards
        val setsStateObj = JSONObject(prefs.getString("workout_sets_json", "{}"))
        val filteredExercises = getFullExerciseCatalog().filter { ex ->
            val matchMuscle = selectedMuscleFilter == "All" || ex.muscle.equals(selectedMuscleFilter, true) || ex.secondaryMuscles.contains(selectedMuscleFilter, true)
            val matchEquip = selectedEquipmentFilter == "All" || ex.equipment.equals(selectedEquipmentFilter, true)
            val matchSplit = selectedSplitFilter == "All" || ex.splitTag.equals(selectedSplitFilter, true)
            matchMuscle && matchEquip && matchSplit
        }

        filteredExercises.forEach { ex ->
            val isExpanded = expandedExerciseIds.contains(ex.id)
            val exCard = createGlassCard("#10192D", if (isExpanded) "#38BDF8" else "#243B61")

            val exSetsArr = setsStateObj.optJSONArray(ex.id) ?: createDefaultSetsForExercise()
            setsStateObj.put(ex.id, exSetsArr)
            var doneSets = 0
            var best1Rm = 0
            for (s in 0 until exSetsArr.length()) {
                val sObj = exSetsArr.getJSONObject(s)
                if (sObj.optBoolean("done", false)) doneSets++
                val kg = sObj.optInt("kg", 20)
                val reps = sObj.optInt("reps", 12)
                val est1Rm = (kg * (1.0 + reps / 30.0)).roundToInt()
                if (est1Rm > best1Rm) best1Rm = est1Rm
            }

            val topRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setOnClickListener {
                    if (isExpanded) expandedExerciseIds.remove(ex.id) else expandedExerciseIds.add(ex.id)
                    renderWorkoutStudioTab()
                }
            }

            val animThumb = MotionFigureView(this, ex.id).apply {
                layoutParams = LinearLayout.LayoutParams(140, 140).apply { setMargins(0, 0, 18, 0) }
                setOnClickListener { showExerciseVideoGuideDialog(ex) }
            }

            val metaCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            metaCol.addView(TextView(this).apply {
                text = ex.name
                setTextColor(Color.WHITE)
                textSize = 15.5f
                setTypeface(null, Typeface.BOLD)
            })
            metaCol.addView(TextView(this).apply {
                text = "🎯 ${ex.muscle}  •  🏋️ ${ex.equipment}  •  🏆 1RM: ${best1Rm}kg"
                setTextColor(Color.parseColor("#38BDF8"))
                textSize = 11f
                setTypeface(null, Typeface.BOLD)
                setPadding(0, 4, 0, 4)
            })
            metaCol.addView(TextView(this).apply {
                text = "$doneSets/${exSetsArr.length()} Done  (Tap card to ${if (isExpanded) "collapse ⌃" else "expand sets ⌄"})"
                setTextColor(if (doneSets == exSetsArr.length()) Color.parseColor("#4ADE80") else Color.parseColor("#94A3B8"))
                textSize = 11.5f
                setTypeface(null, Typeface.BOLD)
            })

            val btnVideoGuide = Button(this).apply {
                text = "▶ Video"
                isAllCaps = false
                setTextColor(Color.WHITE)
                textSize = 11f
                setTypeface(null, Typeface.BOLD)
                background = createGlassDrawable("#DC2626", "#F87171", 22f)
                setPadding(20, 10, 20, 10)
                setOnClickListener { showExerciseVideoGuideDialog(ex) }
            }

            topRow.addView(animThumb)
            topRow.addView(metaCol)
            topRow.addView(btnVideoGuide)
            exCard.addView(topRow)

            if (isExpanded) {
                val setsWrap = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(0, 18, 0, 0)
                }

                for (s in 0 until exSetsArr.length()) {
                    val setObj = exSetsArr.getJSONObject(s)
                    val kg = setObj.optInt("kg", 20)
                    val reps = setObj.optInt("reps", 12)
                    val isDone = setObj.optBoolean("done", false)

                    val setRow = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        background = if (isDone) {
                            createGlassDrawable("#16A34A", "#4ADE80", 28f)
                        } else {
                            createGlassDrawable("#090F1D", "#1E293B", 28f)
                        }
                        setPadding(22, 16, 18, 16)
                        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                        lp.setMargins(0, 0, 0, 10)
                        layoutParams = lp
                    }

                    val tvCheck = TextView(this).apply {
                        text = if (isDone) "✓   ${s + 1}" else "○   ${s + 1}"
                        setTextColor(Color.WHITE)
                        textSize = 14f
                        setTypeface(null, Typeface.BOLD)
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.8f)
                        setOnClickListener {
                            val nextState = !isDone
                            setObj.put("done", nextState)
                            prefs.edit().putString("workout_sets_json", setsStateObj.toString()).apply()
                            if (nextState) {
                                if (!isWorkoutStopwatchRunning) toggleWorkoutStopwatch()
                                addXp(15, "Set ${s + 1} Done! (+${kg * reps} kg & +15 XP)")
                                startRestTimer(60)
                            } else {
                                addXp(-15, "Set ${s + 1} Unchecked (-15 XP)")
                            }
                            renderWorkoutStudioTab()
                        }
                    }

                    val tvKgPill = TextView(this).apply {
                        text = "$kg"
                        setTextColor(if (isDone) Color.parseColor("#064E3B") else Color.WHITE)
                        textSize = 14f
                        setTypeface(null, Typeface.BOLD)
                        gravity = Gravity.CENTER
                        background = createGlassDrawable(if (isDone) "#FFFFFF" else "#1E293B", "#334155", 24f)
                        setPadding(28, 10, 28, 10)
                        setOnClickListener { showEditSetDialog(setsStateObj, ex.id, s) }
                    }

                    val tvKgUnit = TextView(this).apply {
                        text = " kg   "
                        setTextColor(Color.WHITE)
                        textSize = 12.5f
                        setTypeface(null, Typeface.BOLD)
                    }

                    val tvRepsPill = TextView(this).apply {
                        text = "$reps"
                        setTextColor(if (isDone) Color.parseColor("#064E3B") else Color.WHITE)
                        textSize = 14f
                        setTypeface(null, Typeface.BOLD)
                        gravity = Gravity.CENTER
                        background = createGlassDrawable(if (isDone) "#DCFCE7" else "#1E293B", "#334155", 24f)
                        setPadding(28, 10, 28, 10)
                        setOnClickListener { showEditSetDialog(setsStateObj, ex.id, s) }
                    }

                    val tvRepsUnit = TextView(this).apply {
                        text = " Reps"
                        setTextColor(Color.WHITE)
                        textSize = 12.5f
                        setTypeface(null, Typeface.BOLD)
                    }

                    val btnRemoveSet = TextView(this).apply {
                        text = "✕"
                        setTextColor(if (isDone) Color.WHITE else Color.parseColor("#FCA5A5"))
                        textSize = 13f
                        setTypeface(null, Typeface.BOLD)
                        setPadding(20, 8, 12, 8)
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
                    setRow.addView(tvKgUnit)
                    setRow.addView(tvRepsPill)
                    setRow.addView(tvRepsUnit)
                    setRow.addView(btnRemoveSet)
                    setsWrap.addView(setRow)
                }

                // Detail + Add a Set Row (Screenshot 7 Style)
                val midActionRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, 6, 0, 12)
                }
                midActionRow.addView(createPillButton("🎬 Detail & Muscle") { showExerciseVideoGuideDialog(ex) })
                midActionRow.addView(createPillButton("➕ Add a set") {
                    val lastKg = if (exSetsArr.length() > 0) exSetsArr.getJSONObject(exSetsArr.length() - 1).optInt("kg", 25) else 25
                    exSetsArr.put(JSONObject().put("kg", lastKg).put("reps", 12).put("done", false))
                    prefs.edit().putString("workout_sets_json", setsStateObj.toString()).apply()
                    renderWorkoutStudioTab()
                })
                setsWrap.addView(midActionRow)

                // Bottom "LOG NEXT SET" Bar (Screenshot 7 Style)
                val btnLogNextSet = Button(this).apply {
                    text = if (doneSets < exSetsArr.length()) "✓ LOG NEXT SET (SET ${doneSets + 1})" else "🎉 ALL SETS COMPLETED (LOG SESSION)"
                    isAllCaps = false
                    setTextColor(Color.WHITE)
                    textSize = 13f
                    setTypeface(null, Typeface.BOLD)
                    background = createGlassDrawable("#090F1D", "#10B981", 28f)
                    setPadding(24, 16, 24, 16)
                    setOnClickListener {
                        var loggedOne = false
                        for (s in 0 until exSetsArr.length()) {
                            val sObj = exSetsArr.getJSONObject(s)
                            if (!sObj.optBoolean("done", false)) {
                                sObj.put("done", true)
                                prefs.edit().putString("workout_sets_json", setsStateObj.toString()).apply()
                                if (!isWorkoutStopwatchRunning) toggleWorkoutStopwatch()
                                addXp(15, "Logged Set ${s + 1} of ${ex.name}! (+15 XP)")
                                startRestTimer(60)
                                loggedOne = true
                                break
                            }
                        }
                        if (!loggedOne) {
                            logWorkoutSession(ex.name, 15, "${exSetsArr.length()} sets completed")
                        }
                        renderWorkoutStudioTab()
                    }
                }
                setsWrap.addView(btnLogNextSet)
                exCard.addView(setsWrap)
            }
            workoutContentContainer.addView(exCard)
        }
        prefs.edit().putString("workout_sets_json", setsStateObj.toString()).apply()
    }

    // ----------------------------------------------------
    // SUB-TAB 1: WORKOUT.COOL GENERATOR (EQUIPMENT + FOCUS AREA)
    // ----------------------------------------------------
    private fun renderWorkoutCoolGeneratorSubTab() {
        // Step 1: Equipment Radio Cards (Screenshots 1 & 5 Style)
        val equipCard = createGlassCard("#10192D", "#2A4365")
        equipCard.addView(createSectionLabel("STEP 1: WHICH TYPE OF EQUIPMENT DO YOU HAVE?"))

        val equipmentOptions = listOf(
            Triple("Body Weight", "Body Weight / No Equipment", "🤸 Home & Calisthenics"),
            Triple("Dumbbell", "Dumbbell", "🏋️ Free Weights & Home Gym"),
            Triple("Barbell", "Barbell", "🏋️‍♂️ Compound Strength Lifts"),
            Triple("Cable Machine", "Cable Machine", "⚙️ Constant Tension Isolation"),
            Triple("Smith Machine", "Smith Machine", "🔩 Guided Bar Squats & Presses"),
            Triple("All", "All Equipment (Full Gym)", "🌐 Show All Available Exercises")
        )

        equipmentOptions.forEach { (eqKey, eqTitle, eqSub) ->
            val isSel = selectedEquipmentFilter == eqKey
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = if (isSel) {
                    createGlassDrawable("#1E293B", "#34D399", 28f)
                } else {
                    createGlassDrawable("#090F1D", "#1E293B", 28f)
                }
                setPadding(28, 22, 28, 22)
                val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                p.setMargins(0, 0, 0, 12)
                layoutParams = p
                setOnClickListener {
                    selectedEquipmentFilter = eqKey
                    selectedSplitFilter = "All"
                    renderWorkoutStudioTab()
                }
            }

            val radioCircle = TextView(this).apply {
                text = if (isSel) "🔘" else "⚪"
                textSize = 16f
                setPadding(0, 0, 22, 0)
            }

            val textCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            textCol.addView(TextView(this).apply {
                text = eqTitle
                setTextColor(Color.WHITE)
                textSize = 15f
                setTypeface(null, Typeface.BOLD)
            })
            textCol.addView(TextView(this).apply {
                text = eqSub
                setTextColor(Color.parseColor("#94A3B8"))
                textSize = 11.5f
            })

            row.addView(radioCircle)
            row.addView(textCol)
            equipCard.addView(row)
        }
        workoutContentContainer.addView(equipCard)

        // Step 2: Interactive Muscle Map + Gym Workout Trainer Banners (Screenshots 1, 2, 3 Style)
        val muscleCard = createGlassCard("#10192D", "#EF4444")
        muscleCard.addView(createSectionLabel("STEP 2: SELECT FOCUS AREA (TAP BODY MAP OR MUSCLE BANNER)"))

        val interactiveBodyMap = AnatomyMapView(this, selectedMuscleFilter, { tappedMuscle ->
            selectedMuscleFilter = tappedMuscle
            selectedSplitFilter = "All"
            renderWorkoutStudioTab()
        }).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 340).apply {
                setMargins(0, 10, 0, 18)
            }
        }
        muscleCard.addView(interactiveBodyMap)

        val muscleBanners = listOf(
            Triple("Chest", "CHEST", "Pectorals • Push-ups, Bench & Incline Press"),
            Triple("Abs", "ABS & CORE", "Six-Pack & Obliques • Ab Wheel, Planks & Twists"),
            Triple("Arms", "ARMS", "Biceps & Triceps • Dumbbell Curls & Pushdowns"),
            Triple("Back", "BACK", "Lats & Traps • Barbell Rows, Pulldowns & Deadlifts"),
            Triple("Shoulders", "SHOULDERS", "Deltoids • Overhead Press & Lateral Raises"),
            Triple("Legs", "LEGS & GLUTES", "Quads & Hamstrings • Squats & Backward Lunges"),
            Triple("All", "FULL BODY (ALL)", "Show All Target Muscle Groups")
        )

        muscleBanners.forEach { (mKey, mTitle, mDesc) ->
            val isSel = selectedMuscleFilter == mKey
            val banner = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = if (isSel) {
                    createGlassDrawable("#B91C1C", "#FCA5A5", 26f)
                } else {
                    createGlassDrawable("#18181B", "#DC2626", 26f)
                }
                setPadding(30, 24, 26, 24)
                val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                p.setMargins(0, 0, 0, 12)
                layoutParams = p
                setOnClickListener {
                    selectedMuscleFilter = mKey
                    selectedSplitFilter = "All"
                    renderWorkoutStudioTab()
                }
            }

            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            col.addView(TextView(this).apply {
                text = mTitle
                setTextColor(Color.WHITE)
                textSize = 18f
                setTypeface(null, Typeface.BOLD)
            })
            col.addView(TextView(this).apply {
                text = mDesc
                setTextColor(Color.parseColor("#FECACA"))
                textSize = 11.5f
                setPadding(0, 4, 0, 0)
            })

            val badge = TextView(this).apply {
                text = if (isSel) "✓ SELECTED" else "SELECT ›"
                setTextColor(Color.WHITE)
                textSize = 11.5f
                setTypeface(null, Typeface.BOLD)
            }

            banner.addView(col)
            banner.addView(badge)
            muscleCard.addView(banner)
        }

        // Step 3: Live motion preview of the matching exercises (3D mannequin demos)
        val matchedPreview = getFullExerciseCatalog().filter { ex ->
            (selectedMuscleFilter == "All" || ex.muscle.equals(selectedMuscleFilter, true) ||
                ex.secondaryMuscles.contains(selectedMuscleFilter, true)) &&
            (selectedEquipmentFilter == "All" || ex.equipment.equals(selectedEquipmentFilter, true))
        }.take(8)

        if (matchedPreview.isNotEmpty()) {
            muscleCard.addView(TextView(this).apply {
                text = "STEP 3: PREVIEW THE MOVEMENTS (${matchedPreview.size} MATCHING DEMOS)"
                setTextColor(Color.parseColor("#FCA5A5"))
                textSize = 11f
                setTypeface(null, Typeface.BOLD)
                setPadding(0, 16, 0, 10)
            })

            val previewScroll = HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
                setPadding(0, 0, 0, 10)
            }
            val previewRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

            matchedPreview.forEach { ex ->
                val tile = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    background = createGlassDrawable("#0B1324", "#243B61", 20f)
                    setPadding(10, 10, 10, 12)
                    val lp = LinearLayout.LayoutParams(170, LinearLayout.LayoutParams.WRAP_CONTENT)
                    lp.setMargins(0, 0, 12, 0)
                    layoutParams = lp
                    setOnClickListener { showExerciseVideoGuideDialog(ex) }
                }
                tile.addView(MotionFigureView(this, ex.id).apply {
                    layoutParams = LinearLayout.LayoutParams(148, 148)
                })
                tile.addView(TextView(this).apply {
                    text = ex.name
                    setTextColor(Color.WHITE)
                    textSize = 10.5f
                    setTypeface(null, Typeface.BOLD)
                    gravity = Gravity.CENTER
                    maxLines = 2
                    setPadding(2, 6, 2, 0)
                })
                previewRow.addView(tile)
            }
            previewScroll.addView(previewRow)
            muscleCard.addView(previewScroll)
        }

        val matchingCount = getFullExerciseCatalog().count { ex ->
            (selectedMuscleFilter == "All" || ex.muscle.equals(selectedMuscleFilter, true) || ex.secondaryMuscles.contains(selectedMuscleFilter, true)) &&
            (selectedEquipmentFilter == "All" || ex.equipment.equals(selectedEquipmentFilter, true))
        }

        val btnFinishGenerator = Button(this).apply {
            text = "FINISH • LAUNCH $matchingCount MATCHING EXERCISES"
            isAllCaps = false
            setTextColor(Color.WHITE)
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            background = createGlassDrawable("#059669", "#34D399", 30f)
            setPadding(24, 20, 24, 20)
            val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            p.setMargins(0, 12, 0, 0)
            layoutParams = p
            setOnClickListener {
                workoutSubTab = 0
                renderWorkoutStudioTab()
            }
        }
        muscleCard.addView(btnFinishGenerator)
        workoutContentContainer.addView(muscleCard)
    }

    // ----------------------------------------------------
    // SUB-TAB 2: CUSTOMIZE EXERCISE & VIDEO CATALOG (SCREENSHOT 6)
    // ----------------------------------------------------
    private fun renderExerciseLibrarySubTab() {
        val searchCard = createGlassCard("#10192D", "#2A4365")
        searchCard.addView(createSectionLabel("SMART SEARCH & CUSTOMIZE EXERCISE CATALOG"))

        val searchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 4, 0, 14)
        }

        val inputSearch = EditText(this).apply {
            hint = "🔍 Search (e.g. Row, Push-up, Ab Wheel, Barbell)..."
            setText(exerciseSearchQuery)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#64748B"))
            textSize = 13f
            background = createGlassDrawable("#090F1D", "#1E293B", 24f)
            setPadding(28, 18, 28, 18)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val btnSearchGo = Button(this).apply {
            text = "Search"
            isAllCaps = false
            setTextColor(Color.WHITE)
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            background = createGlassDrawable("#0284C7", "#38BDF8", 24f)
            val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            p.setMargins(10, 0, 0, 0)
            layoutParams = p
            setOnClickListener {
                exerciseSearchQuery = inputSearch.text.toString().trim()
                renderWorkoutStudioTab()
            }
        }

        searchRow.addView(inputSearch)
        searchRow.addView(btnSearchGo)
        searchCard.addView(searchRow)

        val btnAddCustomEx = createActionButton("+ Create Custom Exercise & Add to Library", "#4F46E5", "#818CF8") {
            showAddCustomExerciseDialog()
        }
        searchCard.addView(btnAddCustomEx)
        workoutContentContainer.addView(searchCard)

        val allExercises = getFullExerciseCatalog().filter { ex ->
            exerciseSearchQuery.isEmpty() ||
            ex.name.contains(exerciseSearchQuery, true) ||
            ex.muscle.contains(exerciseSearchQuery, true) ||
            ex.equipment.contains(exerciseSearchQuery, true)
        }

        allExercises.forEach { ex ->
            val isPinned = expandedExerciseIds.contains(ex.id)
            val rowCard = createGlassCard("#10192D", if (isPinned) "#34D399" else "#1E293B").apply {
                setPadding(26, 22, 26, 22)
            }

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val checkIcon = TextView(this).apply {
                text = if (isPinned) "☑️" else "⚪"
                textSize = 18f
                setPadding(0, 0, 18, 0)
                setOnClickListener {
                    if (isPinned) expandedExerciseIds.remove(ex.id) else expandedExerciseIds.add(ex.id)
                    renderWorkoutStudioTab()
                }
            }

            val animMini = MotionFigureView(this, ex.id).apply {
                layoutParams = LinearLayout.LayoutParams(124, 124).apply { setMargins(0, 0, 18, 0) }
                setOnClickListener { showExerciseVideoGuideDialog(ex) }
            }

            val infoCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener { showExerciseVideoGuideDialog(ex) }
            }

            infoCol.addView(TextView(this).apply {
                text = ex.name
                setTextColor(Color.WHITE)
                textSize = 15f
                setTypeface(null, Typeface.BOLD)
            })
            infoCol.addView(TextView(this).apply {
                text = "${ex.muscle}  •  ${ex.equipment}"
                setTextColor(Color.parseColor("#94A3B8"))
                textSize = 12f
                setPadding(0, 4, 0, 0)
            })

            val btnGuide = createMiniTagButton("🎬 Guide", "#DC2626", "#F87171", "#FFFFFF") {
                showExerciseVideoGuideDialog(ex)
            }

            row.addView(checkIcon)
            row.addView(animMini)
            row.addView(infoCol)
            row.addView(btnGuide)
            if (ex.isCustom) {
                row.addView(createMiniTagButton("✕", "#450A0A", "#EF4444", "#FCA5A5") {
                    deleteCustomExercise(ex.id)
                })
            }

            rowCard.addView(row)
            workoutContentContainer.addView(rowCard)
        }

        val btnLaunchSelected = createActionButton("🏋️ Train Selected Exercises (${expandedExerciseIds.size} Expanded)", "#059669", "#34D399") {
            workoutSubTab = 0
            renderWorkoutStudioTab()
        }
        workoutContentContainer.addView(btnLaunchSelected)
    }

    // ----------------------------------------------------
    // SUB-TAB 3: FITNESS TRACKER SMART REPORT (SCREENSHOT 2)
    // ----------------------------------------------------
    private fun renderFitnessReportSubTab() {
        val workoutsDone = prefs.getInt("workouts_completed", 0)
        val workoutMins = prefs.getInt("workout_minutes", 0)
        val totalVolumeKg = calculateTotalVolumeKg()

        val reportCard = createGlassCard("#10192D", "#EF4444")
        reportCard.addView(createSectionLabel("FITNESS TRACKER SMART • WEEKLY CALENDAR & VOLUME"))

        // 7-Day Calendar Strip (Sun..Sat with Today highlighted like Screenshot 2)
        val calRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 6, 0, 20)
        }
        val cal = Calendar.getInstance()
        val todayDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) // 1=Sun .. 7=Sat
        val dayNames = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
        for (d in 1..7) {
            val isToday = d == todayDayOfWeek
            val dayCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            dayCol.addView(TextView(this).apply {
                text = dayNames[d - 1]
                setTextColor(if (isToday) Color.parseColor("#38BDF8") else Color.parseColor("#94A3B8"))
                textSize = 11f
                setTypeface(null, Typeface.BOLD)
            })
            val dayNum = (cal.get(Calendar.DAY_OF_MONTH) - (todayDayOfWeek - d)).coerceAtLeast(1)
            dayCol.addView(TextView(this).apply {
                text = "$dayNum"
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                textSize = 12.5f
                setTypeface(null, Typeface.BOLD)
                background = if (isToday) createGlassDrawable("#DC2626", "#F87171", 40f) else createGlassDrawable("#1E293B", "#334155", 40f)
                setPadding(18, 12, 18, 12)
                val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                p.setMargins(0, 6, 0, 0)
                layoutParams = p
            })
            calRow.addView(dayCol)
        }
        reportCard.addView(calRow)

        val metricsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = createGlassDrawable("#090F1D", "#1E293B", 24f)
            setPadding(20, 22, 20, 22)
        }
        metricsRow.addView(createStatColumn("$workoutsDone", "Workouts", "#F8FAFC"))
        metricsRow.addView(createStatColumn("$workoutMins", "Time (min)", "#38BDF8"))
        metricsRow.addView(createStatColumn("$totalVolumeKg", "Volume (kg)", "#F43F5E"))
        reportCard.addView(metricsRow)

        val bodyMapView = AnatomyMapView(this, selectedMuscleFilter, { tapped ->
            selectedMuscleFilter = tapped
            renderWorkoutStudioTab()
        }).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 330).apply {
                setMargins(0, 20, 0, 8)
            }
        }
        reportCard.addView(bodyMapView)
        workoutContentContainer.addView(reportCard)

        // Custom Workout Logger & Undo History Card
        val historyCard = createGlassCard("#10192D", "#F59E0B")
        historyCard.addView(createSectionLabel("WORKOUT SESSION LOGGER & UNDO HISTORY"))

        historyCard.addView(createActionButton("+ Log Custom Workout Session", "#B45309", "#F59E0B") {
            showCustomWorkoutDialog()
        })

        val workoutLogs = JSONArray(prefs.getString("workout_logs_json", "[]"))
        if (workoutLogs.length() > 0) {
            historyCard.addView(TextView(this).apply {
                text = "RECENT LOGGED SESSIONS (TAP ✕ UNDO TO CANCEL MISTAKES)"
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

    // ----------------------------------------------------
    // 99+ VIDEO FITNESS GUIDE MODAL (SCREENSHOT 4: MUSCLE | HOW TO DO)
    // ----------------------------------------------------
    private fun showExerciseVideoGuideDialog(ex: ExerciseMeta) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0B1324"))
            setPadding(40, 36, 40, 32)
        }

        // Top Floating White/Glass Title Pill (Screenshot 4 Style)
        val tvTitlePill = TextView(this).apply {
            text = "🎬  ${ex.name}"
            setTextColor(Color.WHITE)
            textSize = 16.5f
            setTypeface(null, Typeface.BOLD)
            background = createGlassDrawable("#1E293B", "#38BDF8", 28f)
            setPadding(32, 16, 32, 16)
            val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            p.setMargins(0, 0, 0, 18)
            layoutParams = p
        }
        container.addView(tvTitlePill)

        // Large animated 3D mannequin demonstration (tap to pause / replay)
        val bigAnimPlayer = MotionFigureView(this, ex.id).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 380).apply {
                setMargins(0, 0, 0, 10)
            }
        }
        container.addView(bigAnimPlayer)

        // Playback controls: pause/resume + slow motion
        val playbackRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 8)
        }
        var slowMo = false
        val btnPlayPause = Button(this).apply {
            text = "⏸ Pause"
            isAllCaps = false
            textSize = 11.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#E2E8F0"))
            background = createGlassDrawable("#111C31", "#334155", 22f)
            setPadding(26, 8, 26, 8)
            setOnClickListener {
                val nowPaused = !bigAnimPlayer.isPaused()
                bigAnimPlayer.setPaused(nowPaused)
                text = if (nowPaused) "▶ Play" else "⏸ Pause"
            }
        }
        val btnSlow = Button(this).apply {
            text = "🐢 Slow-mo"
            isAllCaps = false
            textSize = 11.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#E2E8F0"))
            background = createGlassDrawable("#111C31", "#334155", 22f)
            setPadding(26, 8, 26, 8)
            setOnClickListener {
                slowMo = !slowMo
                bigAnimPlayer.setSpeed(if (slowMo) 0.5f else 1f)
                text = if (slowMo) "🐇 Normal" else "🐢 Slow-mo"
            }
        }
        playbackRow.addView(btnPlayPause)
        val gap = View(this)
        gap.layoutParams = LinearLayout.LayoutParams(10, 1)
        playbackRow.addView(gap)
        playbackRow.addView(btnSlow)
        container.addView(playbackRow)

        container.addView(TextView(this).apply {
            text = "Tap the animation to pause • looped 3D motion demo"
            setTextColor(Color.parseColor("#64748B"))
            textSize = 11f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16)
        })

        // Segmented Toggle: [ Muscle ] | [ How To Do ] (Screenshot 4 Style)
        val toggleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = createGlassDrawable("#1E293B", "#334155", 32f)
            setPadding(8, 8, 8, 8)
            val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            p.setMargins(0, 0, 0, 18)
            layoutParams = p
        }

        val dynamicDetailBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        lateinit var btnMuscleTab: Button
        lateinit var btnHowToTab: Button

        fun renderModalTab(tabIdx: Int) {
            dynamicDetailBox.removeAllViews()
            if (tabIdx == 0) {
                btnMuscleTab.background = createGlassDrawable("#090F1D", "#38BDF8", 28f)
                btnMuscleTab.setTextColor(Color.WHITE)
                btnHowToTab.background = createGlassDrawable("#1E293B", "#1E293B", 28f)
                btnHowToTab.setTextColor(Color.parseColor("#94A3B8"))

                dynamicDetailBox.addView(TextView(this).apply {
                    text = "Focus Area:  • ${ex.secondaryMuscles.replace("•", "  • ")}"
                    setTextColor(Color.parseColor("#F87171"))
                    textSize = 13f
                    setTypeface(null, Typeface.BOLD)
                    setPadding(0, 0, 0, 12)
                })
                dynamicDetailBox.addView(AnatomyMapView(this, ex.muscle, null,
                    "Highlighted: ${ex.muscle}").apply {
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 320)
                })
                dynamicDetailBox.addView(TextView(this).apply {
                    text = "🏋️ Equipment Required: ${ex.equipment}"
                    setTextColor(Color.parseColor("#38BDF8"))
                    textSize = 12.5f
                    setTypeface(null, Typeface.BOLD)
                    setPadding(0, 10, 0, 14)
                })
            } else {
                btnHowToTab.background = createGlassDrawable("#090F1D", "#38BDF8", 28f)
                btnHowToTab.setTextColor(Color.WHITE)
                btnMuscleTab.background = createGlassDrawable("#1E293B", "#1E293B", 28f)
                btnMuscleTab.setTextColor(Color.parseColor("#94A3B8"))

                dynamicDetailBox.addView(TextView(this).apply {
                    text = "📋 STEP-BY-STEP EXECUTION:\n${ex.howToSteps}\n\n💡 PRO BIOMECHANICS TIP:\n${ex.proTips}"
                    setTextColor(Color.parseColor("#E2E8F0"))
                    textSize = 13f
                    setLineSpacing(6f, 1.12f)
                    setPadding(0, 4, 0, 16)
                })
            }
        }

        btnMuscleTab = Button(this).apply {
            text = "Muscle"
            isAllCaps = false
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { renderModalTab(0) }
        }
        btnHowToTab = Button(this).apply {
            text = "How To Do"
            isAllCaps = false
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { renderModalTab(1) }
        }

        toggleRow.addView(btnMuscleTab)
        toggleRow.addView(btnHowToTab)
        container.addView(toggleRow)
        container.addView(dynamicDetailBox)
        renderModalTab(0)

        val btnWatchOnlineVideo = createActionButton("📹 Watch HD Video Demo (Workout.Cool / YouTube)", "#DC2626", "#F87171") {
            val query = Uri.encode("${ex.name.replace("*", "")} proper form exercise demonstration")
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=$query")))
        }
        container.addView(btnWatchOnlineVideo)

        AlertDialog.Builder(this)
            .setView(container)
            .setPositiveButton("Done", null)
            .show()
    }

    private fun toggleWorkoutStopwatch() {
        if (isWorkoutStopwatchRunning) {
            workoutStopwatchTimer?.cancel()
            isWorkoutStopwatchRunning = false
            Toast.makeText(this, "⏸️ Workout Stopwatch Paused", Toast.LENGTH_SHORT).show()
        } else {
            isWorkoutStopwatchRunning = true
            workoutStopwatchTimer = object : CountDownTimer(3 * 3600 * 1000L, 1000L) {
                override fun onTick(millisUntilFinished: Long) {
                    workoutElapsedSeconds++
                    tvWorkoutStopwatch?.text = String.format(
                        Locale.getDefault(),
                        "⏱️ %02d:%02d",
                        workoutElapsedSeconds / 60,
                        workoutElapsedSeconds % 60
                    )
                }
                override fun onFinish() {}
            }.start()
        }
    }

    private fun stopWorkoutStopwatch() {
        workoutStopwatchTimer?.cancel()
        isWorkoutStopwatchRunning = false
        workoutElapsedSeconds = 0
    }

    private fun startRestTimer(seconds: Int) {
        restCountDownTimer?.cancel()
        restTimeLeftSeconds = seconds.coerceAtLeast(5)
        restCountDownTimer = object : CountDownTimer(restTimeLeftSeconds * 1000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                restTimeLeftSeconds = (millisUntilFinished / 1000L).toInt()
                tvRestTimerStatus?.text = String.format(
                    Locale.getDefault(),
                    "🟢 REST TIMER: %02d:%02d",
                    restTimeLeftSeconds / 60,
                    restTimeLeftSeconds % 60
                )
            }

            override fun onFinish() {
                restTimeLeftSeconds = 0
                tvRestTimerStatus?.text = "⚡ REST COMPLETE! Start Next Set!"
                playCompletionSound()
            }
        }.start()
    }

    private fun stopRestTimer() {
        restCountDownTimer?.cancel()
        restTimeLeftSeconds = 0
        tvRestTimerStatus?.text = "⏱️ Rest Timer Stopped"
    }

    private fun showAddCustomExerciseDialog() {
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(50, 32, 50, 16) }
        val inputName = EditText(this).apply { hint = "Exercise Name (e.g., Preacher Curl * EZ Bar)" }
        val inputMuscle = EditText(this).apply {
            hint = "Target Muscle (Chest / Back / Arms / Abs / Shoulders / Legs)"
            setText(if (selectedMuscleFilter == "All") "Chest" else selectedMuscleFilter)
        }
        val inputEquip = EditText(this).apply {
            hint = "Equipment (Body Weight / Dumbbell / Barbell / Cable Machine)"
            setText(if (selectedEquipmentFilter == "All") "Dumbbell" else selectedEquipmentFilter)
        }
        val inputSteps = EditText(this).apply { hint = "How To Do / Form Cues" }

        layout.addView(inputName); layout.addView(inputMuscle); layout.addView(inputEquip); layout.addView(inputSteps)

        AlertDialog.Builder(this)
            .setTitle("➕ Create Custom Exercise")
            .setView(layout)
            .setPositiveButton("Save Exercise") { _, _ ->
                val name = inputName.text.toString().trim()
                if (name.isNotEmpty()) {
                    val arr = JSONArray(prefs.getString("custom_exercises_json", "[]"))
                    val id = "custom_${System.currentTimeMillis()}"
                    arr.put(
                        JSONObject()
                            .put("id", id)
                            .put("name", name)
                            .put("muscle", inputMuscle.text.toString().trim().ifEmpty { "Chest" })
                            .put("equipment", inputEquip.text.toString().trim().ifEmpty { "Dumbbell" })
                            .put("steps", inputSteps.text.toString().trim().ifEmpty { "Focus on controlled eccentric and full range of motion." })
                            .put("animType", 0)
                    )
                    prefs.edit().putString("custom_exercises_json", arr.toString()).apply()
                    expandedExerciseIds.add(id)
                    renderWorkoutStudioTab()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteCustomExercise(exId: String) {
        val arr = JSONArray(prefs.getString("custom_exercises_json", "[]"))
        for (i in 0 until arr.length()) {
            if (arr.getJSONObject(i).optString("id") == exId) {
                arr.remove(i)
                break
            }
        }
        prefs.edit().putString("custom_exercises_json", arr.toString()).apply()
        renderWorkoutStudioTab()
    }

    private fun createDefaultSetsForExercise(): JSONArray {
        return JSONArray().apply {
            put(JSONObject().put("kg", 20).put("reps", 12).put("done", true))
            put(JSONObject().put("kg", 25).put("reps", 12).put("done", false))
            put(JSONObject().put("kg", 30).put("reps", 12).put("done", false))
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
    // TAB 5: SHIELD + INSTALLED APP SELECTOR + BACKUP/RESTORE
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
            Triple("block_social_games", "Block Social Apps (FB, Snapchat, X, TikTok, Reddit)", true),
            Triple("block_full_yt", "Nuclear Mode: Block Entire YouTube App", false)
        )
        shieldRules.forEach { (key, label, defaultVal) ->
            shieldCard.addView(CheckBox(this).apply {
                text = label
                setTextColor(Color.WHITE)
                textSize = 13.5f
                isChecked = prefs.getBoolean(key, defaultVal)
                setPadding(12, 12, 12, 12)
                setOnCheckedChangeListener { buttonView, checked ->
                    if (!checked && isTimerRunning && cbStrictLock.isChecked) {
                        buttonView.isChecked = true
                        Toast.makeText(this@MainActivity, "🔒 Strict Lock Active! Cannot weaken Shield rules while timer runs.", Toast.LENGTH_SHORT).show()
                        return@setOnCheckedChangeListener
                    }
                    prefs.edit().putBoolean(key, checked).apply()
                }
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

        customBlockedAppsContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        tabShieldContainer.addView(customBlockedAppsContainer)

        val backupCard = createGlassCard("#10192D", "#10B981")
        backupCard.addView(createSectionLabel("💾 CLOUD / CLIPBOARD DATA BACKUP & RESTORE"))
        backupCard.addView(TextView(this).apply {
            text = "Save or restore your Hunter XP, CA/B.Com syllabus, habits, and workout logs across APK updates."
            setTextColor(Color.parseColor("#CBD5E1"))
            textSize = 12f
            setPadding(0, 0, 0, 14)
        })
        val backupRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        backupRow.addView(createActionButton("📋 Copy Backup Code", "#064E3B", "#10B981") { exportBackupToClipboard() })
        backupRow.addView(createActionButton("📥 Restore Backup", "#1E293B", "#38BDF8") { showRestoreBackupDialog() })
        backupCard.addView(backupRow)
        tabShieldContainer.addView(backupCard)
    }

    private fun renderCustomBlockedAppsCard() {
        customBlockedAppsContainer.removeAllViews()
        val card = createGlassCard("#10192D", "#EF4444")
        card.addView(createSectionLabel("📲 CUSTOM BLOCKED APPS & GAMES ON YOUR iQOO Z9"))

        val btnSelectApps = createActionButton("📲 Select Installed Apps / Games to Block", "#991B1B", "#F87171") {
            showInstalledAppsPickerDialog()
        }
        (btnSelectApps.layoutParams as LinearLayout.LayoutParams).setMargins(0, 0, 0, 16)
        card.addView(btnSelectApps)

        val customArr = JSONArray(prefs.getString("custom_blocked_apps_json", "[]"))
        if (customArr.length() == 0) {
            card.addView(TextView(this).apply {
                text = "No custom apps added yet. Tap above to block games (BGMI, Free Fire, etc.) or distracting apps."
                setTextColor(Color.parseColor("#94A3B8"))
                textSize = 12f
            })
        } else {
            for (i in 0 until customArr.length()) {
                val obj = customArr.getJSONObject(i)
                val appName = obj.optString("name", "App")
                val appPkg = obj.optString("pkg", "")

                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    background = createGlassDrawable("#090F1D", "#1E293B", 18f)
                    setPadding(22, 14, 18, 14)
                    val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    p.setMargins(0, 0, 0, 10)
                    layoutParams = p
                }

                row.addView(TextView(this).apply {
                    text = "🚫 $appName\n($appPkg)"
                    setTextColor(Color.parseColor("#FCA5A5"))
                    textSize = 12f
                    setTypeface(null, Typeface.BOLD)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })

                row.addView(createMiniTagButton("✕ Unblock", "#450A0A", "#EF4444", "#FCA5A5") {
                    if (isTimerRunning && cbStrictLock.isChecked) {
                        Toast.makeText(this@MainActivity, "🔒 Strict Lock Active! Cannot unblock apps while timer runs.", Toast.LENGTH_SHORT).show()
                        return@createMiniTagButton
                    }
                    customArr.remove(i)
                    prefs.edit().putString("custom_blocked_apps_json", customArr.toString()).apply()
                    renderCustomBlockedAppsCard()
                })
                card.addView(row)
            }
        }
        customBlockedAppsContainer.addView(card)
    }

    private fun showInstalledAppsPickerDialog() {
        try {
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = packageManager.queryIntentActivities(mainIntent, 0)
            val appItems = mutableListOf<Pair<String, String>>()
            val seenPkgs = mutableSetOf<String>()

            for (ri in resolveInfos) {
                val pkg = ri.activityInfo.packageName
                if (pkg == packageName || pkg == "com.android.settings" || pkg.contains("launcher") || seenPkgs.contains(pkg)) continue
                seenPkgs.add(pkg)
                val label = ri.loadLabel(packageManager).toString()
                appItems.add(label to pkg)
            }
            appItems.sortBy { it.first.lowercase(Locale.getDefault()) }

            val customArr = JSONArray(prefs.getString("custom_blocked_apps_json", "[]"))
            val currentlyBlocked = mutableSetOf<String>()
            for (i in 0 until customArr.length()) {
                currentlyBlocked.add(customArr.getJSONObject(i).optString("pkg"))
            }

            val namesArray = appItems.map { "${it.first} (${it.second})" }.toTypedArray()
            val checkedArray = BooleanArray(appItems.size) { idx ->
                currentlyBlocked.contains(appItems[idx].second)
            }

            AlertDialog.Builder(this)
                .setTitle("📲 Select Apps & Games to Block")
                .setMultiChoiceItems(namesArray, checkedArray) { _, which, isChecked ->
                    if (!isChecked && isTimerRunning && cbStrictLock.isChecked) {
                        checkedArray[which] = true
                        Toast.makeText(this, "🔒 Strict Mode Active! Cannot unblock while timer runs.", Toast.LENGTH_SHORT).show()
                    } else {
                        checkedArray[which] = isChecked
                    }
                }
                .setPositiveButton("Save Blocklist") { _, _ ->
                    val newArr = JSONArray()
                    for (idx in appItems.indices) {
                        if (checkedArray[idx]) {
                            newArr.put(JSONObject().put("name", appItems[idx].first).put("pkg", appItems[idx].second))
                        }
                    }
                    prefs.edit().putString("custom_blocked_apps_json", newArr.toString()).apply()
                    Toast.makeText(this, "🛡️ Blocked ${newArr.length()} custom apps!", Toast.LENGTH_SHORT).show()
                    renderCustomBlockedAppsCard()
                }
                .setNegativeButton("Cancel", null)
                .show()
        } catch (e: Exception) {
            Toast.makeText(this, "Could not load app list", Toast.LENGTH_SHORT).show()
        }
    }

    private fun exportBackupToClipboard() {
        val backupObj = JSONObject()
        for ((k, v) in prefs.all) {
            backupObj.put(k, v)
        }
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("FocusForgeBackup", backupObj.toString()))
        Toast.makeText(this, "📋 Backup Code copied to Clipboard! Save it in Notes.", Toast.LENGTH_LONG).show()
    }

    private fun showRestoreBackupDialog() {
        val input = EditText(this).apply {
            hint = "Paste your FocusForge Backup JSON code here"
            setPadding(50, 36, 50, 36)
        }
        AlertDialog.Builder(this)
            .setTitle("📥 Restore Backup Code")
            .setView(input)
            .setPositiveButton("Restore Now") { _, _ ->
                try {
                    val raw = input.text.toString().trim()
                    val obj = JSONObject(raw)
                    val editor = prefs.edit()
                    val keys = obj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        when (val v = obj.get(k)) {
                            is Boolean -> editor.putBoolean(k, v)
                            is Int -> editor.putInt(k, v)
                            is Long -> editor.putLong(k, v)
                            is String -> editor.putString(k, v)
                        }
                    }
                    editor.apply()
                    refreshAllUI()
                    Toast.makeText(this, "🎉 Progress & Syllabus Restored Successfully!", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Invalid Backup Code", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
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

        if (!prefs.contains("QuickVault_json")) {
            val defaultVault = JSONArray().apply {
                put(JSONObject().put("tag", "Contract Act • Sec 10").put("body", "All agreements are contracts if made by free consent of parties competent to contract, for a lawful consideration and with a lawful object."))
                put(JSONObject().put("tag", "Accounting • AS 2").put("body", "Inventories should be valued at lower of Cost and Net Realisable Value (NRV)."))
                put(JSONObject().put("tag", "Quant • Compound Interest").put("body", "A = P(1 + r/n)^(nt)  |  Effective Rate E = (1 + i)^n - 1"))
            }
            prefs.edit().putString("QuickVault_json", defaultVault.toString()).apply()
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
        renderCustomBlockedAppsCard()
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
        prefs.edit().putBoolean("timer_is_active", false).apply()
        initialTimeMillis = durationMillis
        timeLeftInMillis = durationMillis
        tvTimerDisplay.text = formatTime(timeLeftInMillis)
        tvTimerStatus.text = label
        btnStartPause.text = "Start Focus"
    }

    private fun startTimer(resumeExisting: Boolean) {
        prefs.edit().putBoolean("blocker_enabled", true).apply()
        updateBlockerButtonStyle(btnBlockerToggle, true)

        if (!resumeExisting) {
            prefs.edit()
                .putBoolean("timer_is_active", true)
                .putLong("timer_end_millis", System.currentTimeMillis() + timeLeftInMillis)
                .apply()
        }

        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(timeLeftInMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timeLeftInMillis = millisUntilFinished
                tvTimerDisplay.text = formatTime(timeLeftInMillis)
            }

            override fun onFinish() {
                isTimerRunning = false
                prefs.edit().putBoolean("timer_is_active", false).putLong("timer_end_millis", 0L).apply()
                timeLeftInMillis = initialTimeMillis
                tvTimerDisplay.text = formatTime(initialTimeMillis)
                tvTimerStatus.text = "Session Complete! Great work!"
                btnStartPause.text = "Start Focus"
                playCompletionSound()
                recordCompletedSession()
            }
        }.start()

        isTimerRunning = true
        tvTimerStatus.text = "🔥 Focusing on $selectedSubject — Strict Shield Locked"
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
        prefs.edit().putBoolean("timer_is_active", false).apply()
        tvTimerStatus.text = "Paused • $selectedSubject"
        btnStartPause.text = "Resume"
    }

    private fun resetTimer() {
        countDownTimer?.cancel()
        isTimerRunning = false
        prefs.edit().putBoolean("timer_is_active", false).putLong("timer_end_millis", 0L).apply()
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
    // UI HELPERS & CUSTOM INTERACTIVE CANVAS VIEWS
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
            setPadding(34, 34, 34, 34)
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            params.setMargins(0, 0, 0, 28)
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
        restCountDownTimer?.cancel()
        workoutStopwatchTimer?.cancel()
    }
}
