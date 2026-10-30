package com.focusforge.pro

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            val layout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.parseColor("#0F172A"))
                setPadding(50, 100, 50, 50)
                gravity = Gravity.CENTER_HORIZONTAL
            }

            val title = TextView(this).apply {
                text = "FocusForge Pro"
                setTextColor(Color.parseColor("#38BDF8"))
                textSize = 28f
                setTypeface(null, android.graphics.Typeface.BOLD)
                gravity = Gravity.CENTER
            }

            val subtitle = TextView(this).apply {
                text = "Student Focus & Blocker Dashboard"
                setTextColor(Color.parseColor("#94A3B8"))
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(0, 10, 0, 40)
            }

            val statusBox = TextView(this).apply {
                text = "Status: Successfully Running on iQOO Z9!"
                setTextColor(Color.parseColor("#A7F3D0"))
                setBackgroundColor(Color.parseColor("#064E3B"))
                setPadding(30, 30, 30, 30)
                textSize = 15f
                gravity = Gravity.CENTER
                setTypeface(null, android.graphics.Typeface.BOLD)
            }

            val btnAction = Button(this).apply {
                text = "Test App Connection"
                setPadding(20, 20, 20, 20)
                setOnClickListener {
                    Toast.makeText(this@MainActivity, "FocusForge Pro is fully active!", Toast.LENGTH_SHORT).show()
                }
                setBackgroundColor(Color.parseColor("#4F46E5"))
                setTextColor(Color.WHITE)
            }

            layout.addView(title)
            layout.addView(subtitle)
            layout.addView(statusBox)

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, 50, 0, 0)
            btnAction.layoutParams = params
            layout.addView(btnAction)

            setContentView(layout)
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }
}
