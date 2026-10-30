package com.focusforge.pro

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            window.statusBarColor = Color.parseColor("#0F172A")

            val rootLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.parseColor("#0F172A"))
                setPadding(40, 60, 40, 40)
            }

            val scrollView = ScrollView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                )
            }

            val container = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
            }

            // Title Header
            val title = TextView(this).apply {
                text = "FocusForge Pro"
                setTextColor(Color.parseColor("#38BDF8"))
                textSize = 24f
                setTypeface(null, android.graphics.Typeface.BOLD)
                gravity = Gravity.CENTER
            }
            
            val subtitle = TextView(this).apply {
                text = "Student Focus & Blocker Dashboard"
                setTextColor(Color.parseColor("#94A3B8"))
                textSize = 12f
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, 30)
            }

            container.addView(title)
            container.addView(subtitle)

            // Status Badge
            val statusBadge = TextView(this).apply {
                text = "Shield Status: Tap to Enable Accessibility"
                setTextColor(Color.parseColor("#FCA5A5"))
                setBackgroundColor(Color.parseColor("#7F1D1D"))
                setPadding(20, 20, 20, 20)
                gravity = Gravity.CENTER
                textSize = 13f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setOnClickListener {
                    try {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        Toast.makeText(this@MainActivity, "Turn ON 'FocusForge Pro Blocker'", Toast.LENGTH_LONG).show()
                    } catch (e: Exception) {
                        Toast.makeText(this@MainActivity, "Open Settings -> Accessibility", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            container.addView(statusBadge)

            // Card 1: Timer & Focus
            val card1 = createCard(this, "Strict Study Session").apply {
                val timerText = TextView(this@MainActivity).apply {
                    text = "45:00"
                    setTextColor(Color.WHITE)
                    textSize = 42f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    gravity = Gravity.CENTER
                    setPadding(0, 10, 0, 10)
                }
                
                val btnStart = Button(this@MainActivity).apply {
                    text = "Start 45m Focus Block"
                    setBackgroundColor(Color.parseColor("#059669"))
                    setTextColor(Color.WHITE)
                    setOnClickListener {
                        Toast.makeText(this@MainActivity, "Focus Session Started! Reels Blocked.", Toast.LENGTH_SHORT).show()
                    }
                }
                addView(timerText)
                addView(btnStart)
            }
            container.addView(card1)

            // Card 2: Quick Study Checklist
            val card2 = createCard(this, "Daily Habit & Study Checklist").apply {
                val items = arrayOf(
                    "✓ Review CA Law & Accounts Ch. 1-2",
                    "✓ Complete College Assignments",
                    "✓ 45m Gym / Workout Split",
                    "✓ Hydration Tracker (8 Glasses)"
                )
                for (item in items) {
                    val tv = TextView(this@MainActivity).apply {
                        text = item
                        setTextColor(Color.parseColor("#E2E8F0"))
                        textSize = 14f
                        setPadding(0, 8, 0, 8)
                    }
                    addView(tv)
                }
            }
            container.addView(card2)

            scrollView.addView(container)
            rootLayout.addView(scrollView)
            setContentView(rootLayout)

        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    private fun createCard(context: Context, cardTitle: String): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1E293B"))
            setPadding(30, 30, 30, 30)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setMargins(0, 20, 0, 0)
            layoutParams = params

            val titleView = TextView(context).apply {
                text = cardTitle.uppercase()
                setTextColor(Color.parseColor("#94A3B8"))
                textSize = 11f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(0, 0, 0, 15)
            }
            addView(titleView)
        }
    }

    private fun setMargins(left: Int, top: Int, right: Int, bottom: Int) {
        val p = layoutParams as LinearLayout.LayoutParams
        p.setMargins(left, top, right, bottom)
        layoutParams = p
    }
}
