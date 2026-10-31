package com.focusforge.pro

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Small, dependency-free engines that power the dashboard, streaks, revision
 * planner, personal records and the shield schedule.
 *
 * Everything is persisted in the existing "FocusForgePrefs" file so the
 * clipboard backup/restore keeps working with no extra work.
 */

// =====================================================================
// DAILY ACTIVITY LOG  →  dashboard tiles, streaks, heat-map
// =====================================================================
object DailyLog {

    const val FIELD_FOCUS = "focus"     // study minutes
    const val FIELD_HABITS = "habits"   // habit checkmarks
    const val FIELD_GYM = "gym"         // workouts finished
    const val FIELD_BLOCKS = "blocks"   // distractions blocked

    private const val KEY = "daily_log_json"
    private const val MAX_DAYS = 400
    private val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun today(): String = fmt.format(Date())

    fun dayKey(offset: Int): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -offset)
        return fmt.format(cal.time)
    }

    fun read(prefs: SharedPreferences): JSONObject =
        try {
            JSONObject(prefs.getString(KEY, "{}"))
        } catch (e: Exception) {
            JSONObject()
        }

    fun write(prefs: SharedPreferences, obj: JSONObject) {
        prefs.edit().putString(KEY, obj.toString()).apply()
    }

    fun bump(prefs: SharedPreferences, field: String, delta: Int) {
        val root = read(prefs)
        val day = root.optJSONObject(today()) ?: JSONObject()
        day.put(field, (day.optInt(field, 0) + delta).coerceAtLeast(0))
        root.put(today(), day)
        write(prefs, root)
    }

    fun setToday(prefs: SharedPreferences, field: String, value: Int) {
        val root = read(prefs)
        val day = root.optJSONObject(today()) ?: JSONObject()
        day.put(field, value.coerceAtLeast(0))
        root.put(today(), day)
        write(prefs, root)
    }

    fun value(prefs: SharedPreferences, field: String, offset: Int = 0): Int {
        val day = read(prefs).optJSONObject(dayKey(offset)) ?: return 0
        return day.optInt(field, 0)
    }

    /** Oldest → newest, ending today. */
    fun series(prefs: SharedPreferences, field: String, days: Int): IntArray {
        val root = read(prefs)
        val out = IntArray(days)
        for (i in 0 until days) {
            val day = root.optJSONObject(dayKey(days - 1 - i)) ?: continue
            out[i] = day.optInt(field, 0)
        }
        return out
    }

    fun weekTotal(prefs: SharedPreferences, field: String): Int =
        series(prefs, field, 7).sum()

    private fun activeDay(prefs: SharedPreferences, offset: Int): Boolean {
        val day = read(prefs).optJSONObject(dayKey(offset)) ?: return false
        return day.optInt(FIELD_FOCUS, 0) > 0 || day.optInt(FIELD_GYM, 0) > 0
    }

    /** Consecutive active days ending today (or yesterday if today is still empty). */
    fun currentStreak(prefs: SharedPreferences): Int {
        var streak = 0
        var start = 0
        if (!activeDay(prefs, 0)) {
            if (!activeDay(prefs, 1)) return 0
            start = 1
        }
        var i = start
        while (i < MAX_DAYS && activeDay(prefs, i)) {
            streak++
            i++
        }
        return streak
    }

    fun bestStreak(prefs: SharedPreferences): Int {
        var best = 0
        var run = 0
        for (i in MAX_DAYS downTo 0) {
            if (activeDay(prefs, i)) {
                run++
                if (run > best) best = run
            } else if (i != 0 || run > 0) {
                run = 0
            }
        }
        return best
    }

    /** Rebuilds only today's counters from the real counters (used after restores). */
    fun reconcileToday(prefs: SharedPreferences) {
        val today = today()
        val root = read(prefs)
        if (root.optJSONObject(today) == null) {
            root.put(today, JSONObject().put(FIELD_FOCUS, 0).put(FIELD_HABITS, 0)
                .put(FIELD_GYM, 0).put(FIELD_BLOCKS, 0))
            write(prefs, root)
        }
    }
}

// =====================================================================
// PER-HABIT HISTORY → streaks for each habit row
// =====================================================================
object HabitHistory {

    private const val KEY = "habit_history_json"

    private fun read(prefs: SharedPreferences): JSONObject =
        try {
            JSONObject(prefs.getString(KEY, "{}"))
        } catch (e: Exception) {
            JSONObject()
        }

    fun record(prefs: SharedPreferences, habitTitle: String, dayKey: String, checked: Boolean) {
        val root = read(prefs)
        val habit = root.optJSONObject(habitTitle) ?: JSONObject()
        if (checked) habit.put(dayKey, true) else habit.remove(dayKey)
        root.put(habitTitle, habit)
        prefs.edit().putString(KEY, root.toString()).apply()
    }

    fun streak(prefs: SharedPreferences, habitTitle: String): Int {
        val habit = read(prefs).optJSONObject(habitTitle) ?: return 0
        var streak = 0
        var start = 0
        if (!habit.optBoolean(DailyLog.dayKey(0), false)) {
            if (!habit.optBoolean(DailyLog.dayKey(1), false)) return 0
            start = 1
        }
        var i = start
        while (i < 400 && habit.optBoolean(DailyLog.dayKey(i), false)) {
            streak++
            i++
        }
        return streak
    }

    fun bestStreak(prefs: SharedPreferences, habitTitle: String): Int {
        val habit = read(prefs).optJSONObject(habitTitle) ?: return 0
        var best = 0
        var run = 0
        for (i in 400 downTo 0) {
            if (habit.optBoolean(DailyLog.dayKey(i), false)) {
                run++
                if (run > best) best = run
            } else {
                run = 0
            }
        }
        return best
    }
}

// =====================================================================
// SPACED-REPETITION REVISION PLANNER  (syllabus units)
// =====================================================================
object RevisionPlanner {

    /** Days until the next revision, indexed by how many revisions are done. */
    private val SCHEDULE = intArrayOf(1, 3, 7, 14, 30, 60)
    private val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    data class DueUnit(
        val subjectIndex: Int,
        val unitIndex: Int,
        val subject: String,
        val moduleTag: String,
        val title: String,
        val dueDate: String,
        val daysOverdue: Int,
        val revisions: Int
    )

    fun dueDateAfter(revisions: Int): String {
        val idx = (revisions - 1).coerceIn(0, SCHEDULE.size - 1)
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, SCHEDULE[idx])
        return fmt.format(cal.time)
    }

    fun daysUntil(dateStr: String): Int {
        return try {
            val target = fmt.parse(dateStr) ?: return Int.MAX_VALUE
            val today = fmt.parse(fmt.format(Date())) ?: return Int.MAX_VALUE
            ((target.time - today.time) / 86_400_000L).toInt()
        } catch (e: Exception) {
            Int.MAX_VALUE
        }
    }

    /** Marks a unit due for its first revision, or clears it when un-completed. */
    fun markCompleted(unit: JSONObject, completed: Boolean) {
        if (completed) {
            if (unit.optString("revDue", "").isEmpty()) {
                unit.put("revDue", dueDateAfter(unit.optInt("rev", 0).coerceAtLeast(0) + 1))
            }
        } else {
            unit.put("revDue", "")
        }
    }

    fun logRevision(unit: JSONObject): Int {
        val revs = unit.optInt("rev", 0) + 1
        unit.put("rev", revs)
        unit.put("revLast", DailyLog.today())
        unit.put("revDue", dueDateAfter(revs))
        return revs
    }

    fun collectDue(subjects: JSONArray, limit: Int = 12): List<DueUnit> {
        val out = mutableListOf<DueUnit>()
        for (s in 0 until subjects.length()) {
            val subj = subjects.optJSONObject(s) ?: continue
            val units = subj.optJSONArray("units") ?: continue
            val name = subj.optString("name", "Subject")
            for (u in 0 until units.length()) {
                val unit = units.optJSONObject(u) ?: continue
                val due = unit.optString("revDue", "")
                if (due.isEmpty()) continue
                val days = daysUntil(due)
                if (days > 0) continue
                out.add(
                    DueUnit(
                        subjectIndex = s,
                        unitIndex = u,
                        subject = name,
                        moduleTag = unit.optString("module", "Module"),
                        title = unit.optString("title", "Unit"),
                        dueDate = due,
                        daysOverdue = -days,
                        revisions = unit.optInt("rev", 0)
                    )
                )
            }
        }
        return out.sortedByDescending { it.daysOverdue }.take(limit)
    }

    fun upcomingCount(subjects: JSONArray): Int {
        var count = 0
        for (s in 0 until subjects.length()) {
            val units = subjects.optJSONObject(s)?.optJSONArray("units") ?: continue
            for (u in 0 until units.length()) {
                val due = units.optJSONObject(u)?.optString("revDue", "") ?: ""
                if (due.isNotEmpty() && daysUntil(due) > 0) count++
            }
        }
        return count
    }

    /** 0-100 readiness: completion weighted with revision depth. */
    fun readiness(subjects: JSONArray): Int {
        var units = 0
        var score = 0.0
        for (s in 0 until subjects.length()) {
            val arr = subjects.optJSONObject(s)?.optJSONArray("units") ?: continue
            for (u in 0 until arr.length()) {
                val unit = arr.optJSONObject(u) ?: continue
                units++
                if (unit.optInt("status", 0) == 2) {
                    // completed = 60% of the value, extra revisions add the rest
                    score += 0.6 + 0.4 * (unit.optInt("rev", 0).coerceAtMost(3) / 3.0)
                }
            }
        }
        if (units == 0) return 0
        return ((score / units) * 100).roundToIntSafe()
    }

    private fun Double.roundToIntSafe(): Int = kotlin.math.round(this).toInt()
}

// =====================================================================
// PERSONAL RECORDS + 1RM
// =====================================================================
object PRTracker {

    data class Record(
        val id: String,
        val name: String,
        val weightKg: Int,
        val reps: Int,
        val estOneRm: Int,
        val volumeKg: Int
    )

    /** Epley formula, the one most gym apps show. */
    fun estOneRm(weightKg: Int, reps: Int): Int {
        if (weightKg <= 0 || reps <= 0) return 0
        if (reps == 1) return weightKg
        return (weightKg * (1 + reps / 30.0)).toInt()
    }

    /** Brzycki alternative, shown next to Epley in the calculator. */
    fun brzycki(weightKg: Int, reps: Int): Int {
        if (weightKg <= 0 || reps <= 0 || reps >= 37) return 0
        return (weightKg * 36.0 / (37 - reps)).toInt()
    }

    fun topLifts(
        setsState: JSONObject,
        nameOf: (String) -> String,
        limit: Int = 6
    ): List<Record> {
        val out = mutableListOf<Record>()
        val keys = setsState.keys()
        while (keys.hasNext()) {
            val id = keys.next()
            val arr = setsState.optJSONArray(id) ?: continue
            var bestWeight = 0
            var bestReps = 0
            var bestOneRm = 0
            var volume = 0
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val kg = o.optInt("kg", 0)
                val reps = o.optInt("reps", 0)
                if (!o.optBoolean("done", false)) continue
                volume += kg * reps
                val orm = estOneRm(kg, reps)
                if (orm > bestOneRm) {
                    bestOneRm = orm
                    bestWeight = kg
                    bestReps = reps
                }
            }
            if (bestOneRm > 0) {
                out.add(Record(id, nameOf(id), bestWeight, bestReps, bestOneRm, volume))
            }
        }
        return out.sortedByDescending { it.estOneRm }.take(limit)
    }
}

// =====================================================================
// SHIELD SCHEDULE / SNOOZE / WHITELIST
// =====================================================================
object ShieldGuard {

    const val PREF_ENABLED = "blocker_enabled"
    const val PREF_SCHEDULE_ON = "shield_schedule_on"
    const val PREF_START = "shield_schedule_start"
    const val PREF_END = "shield_schedule_end"
    const val PREF_SNOOZE_UNTIL = "shield_snooze_until"
    const val PREF_WHITELIST = "shield_whitelist_json"

    fun minutesOfDay(text: String): Int {
        val parts = text.split(":")
        val h = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: 0
        val m = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 0
        return (h.coerceIn(0, 23) * 60 + m.coerceIn(0, 59))
    }

    fun nowMinutes(): Int {
        val cal = Calendar.getInstance()
        return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    }

    fun snoozeUntil(prefs: SharedPreferences): Long = prefs.getLong(PREF_SNOOZE_UNTIL, 0L)

    fun isSnoozed(prefs: SharedPreferences): Boolean = snoozeUntil(prefs) > System.currentTimeMillis()

    fun snoozeMinutesLeft(prefs: SharedPreferences): Int {
        val left = snoozeUntil(prefs) - System.currentTimeMillis()
        return if (left <= 0) 0 else ((left / 60000L) + 1).toInt()
    }

    fun snooze(prefs: SharedPreferences, minutes: Int) {
        prefs.edit().putLong(PREF_SNOOZE_UNTIL, System.currentTimeMillis() + minutes * 60_000L).apply()
    }

    fun clearSnooze(prefs: SharedPreferences) {
        prefs.edit().putLong(PREF_SNOOZE_UNTIL, 0L).apply()
    }

    /** Inside the configured focus window (supports windows that cross midnight). */
    fun inSchedule(prefs: SharedPreferences): Boolean {
        if (!prefs.getBoolean(PREF_SCHEDULE_ON, false)) return true
        val start = minutesOfDay(prefs.getString(PREF_START, "08:00") ?: "08:00")
        val end = minutesOfDay(prefs.getString(PREF_END, "22:00") ?: "22:00")
        val now = nowMinutes()
        return if (start <= end) now in start until end
        else now >= start || now < end
    }

    /** Master switch used by the accessibility service. */
    fun isGuarding(prefs: SharedPreferences): Boolean {
        if (!prefs.getBoolean(PREF_ENABLED, false)) return false
        if (isSnoozed(prefs)) return false
        return inSchedule(prefs)
    }

    fun whitelist(prefs: SharedPreferences): JSONArray =
        try {
            JSONArray(prefs.getString(PREF_WHITELIST, "[]"))
        } catch (e: Exception) {
            JSONArray()
        }

    fun isWhitelisted(prefs: SharedPreferences, pkg: String): Boolean {
        val arr = whitelist(prefs)
        for (i in 0 until arr.length()) {
            if (arr.optJSONObject(i)?.optString("pkg", "")?.equals(pkg, true) == true) return true
        }
        return false
    }

    fun addWhitelist(prefs: SharedPreferences, pkg: String, label: String) {
        val arr = whitelist(prefs)
        if (isWhitelisted(prefs, pkg)) return
        arr.put(JSONObject().put("pkg", pkg).put("name", label))
        prefs.edit().putString(PREF_WHITELIST, arr.toString()).apply()
    }

    fun removeWhitelist(prefs: SharedPreferences, pkg: String) {
        val arr = whitelist(prefs)
        val kept = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (!o.optString("pkg", "").equals(pkg, true)) kept.put(o)
        }
        prefs.edit().putString(PREF_WHITELIST, kept.toString()).apply()
    }
}

// =====================================================================
// VIEWS
// =====================================================================

/** GitHub-style contribution heat-map of the last [weeks] weeks. */
class HeatmapView(
    context: Context,
    private val values: IntArray,
    private val weeks: Int = 5,
    private val accentHex: String = "#10B981"
) : View(context) {

    private val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#64748B")
        textSize = 20f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    }
    private val accent = Color.parseColor(accentHex)
    private val empty = Color.parseColor("#16233A")
    private val rect = RectF()
    private val dayNames = arrayOf("M", "T", "W", "T", "F", "S", "S")

    init {
        labelPaint.textSize = 10f * context.resources.displayMetrics.density
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val dayCount = weeks * 7
        val data = IntArray(dayCount)
        val offset = dayCount - values.size
        for (i in values.indices) {
            val idx = offset + i
            if (idx in 0 until dayCount) data[idx] = values[i]
        }
        val maxValue = (data.maxOrNull() ?: 0).coerceAtLeast(1)

        val labelW = 14f * resources.displayMetrics.density
        val gap = 3f * resources.displayMetrics.density
        val topPad = 2f
        val cellW = (width - labelW - gap * weeks) / weeks
        val cellH = (height - topPad - gap * 7) / 7
        val size = minOf(cellW, cellH)

        // weekday labels
        val cal = Calendar.getInstance()
        val todayRow = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
        for (row in 0 until 7) {
            val y = topPad + row * (size + gap)
            canvas.drawText(dayNames[row], 0f, y + size * 0.8f, labelPaint)
        }

        // newest day lives in the last column
        val cells = ArrayList<Pair<Int, Int>>() // row, col
        var row = todayRow
        var col = weeks - 1
        for (i in dayCount - 1 downTo 0) {
            cells.add(row to col)
            row--
            if (row < 0) {
                row = 6
                col--
            }
        }
        cells.reverse()

        for (i in 0 until dayCount) {
            val (r, c) = cells[i]
            val v = data[i]
            val ratio = if (maxValue == 0) 0f else v.toFloat() / maxValue
            val color = when {
                v <= 0 -> empty
                ratio <= 0.25f -> blend(empty, accent, 0.30f)
                ratio <= 0.50f -> blend(empty, accent, 0.55f)
                ratio <= 0.75f -> blend(empty, accent, 0.78f)
                else -> accent
            }
            cellPaint.color = color
            val left = labelW + c * (size + gap)
            val top = topPad + r * (size + gap)
            rect.set(left, top, left + size, top + size)
            canvas.drawRoundRect(rect, size * 0.22f, size * 0.22f, cellPaint)
        }
    }

    private fun blend(from: Int, to: Int, t: Float): Int {
        val r = (Color.red(from) + (Color.red(to) - Color.red(from)) * t).toInt()
        val g = (Color.green(from) + (Color.green(to) - Color.green(from)) * t).toInt()
        val b = (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * t).toInt()
        return Color.rgb(r, g, b)
    }
}

/** Slim horizontal progress row used by the dashboard. */
class GoalBarView(
    context: Context,
    private var progress: Float,
    private val accentHex: String = "#38BDF8"
) : View(context) {

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#16233A") }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor(accentHex) }
    private val rect = RectF()

    fun setProgress(value: Float) {
        progress = value.coerceIn(0f, 1f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val h = height.toFloat()
        val r = h / 2f
        rect.set(0f, 0f, width.toFloat(), h)
        canvas.drawRoundRect(rect, r, r, trackPaint)
        val filled = width.toFloat() * progress
        if (filled > 1f) {
            rect.set(0f, 0f, filled, h)
            canvas.drawRoundRect(rect, r, r, fillPaint)
        }
    }
}
