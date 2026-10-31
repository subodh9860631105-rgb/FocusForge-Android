package com.focusforge.pro

import android.os.Bundle
import android.graphics.Color
import android.widget.TextView
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val tv = TextView(this).apply {
            text = "FocusForge Pro Active!"
            setTextColor(Color.WHITE)
            textSize = 26f
            setPadding(60, 100, 60, 60)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#0F172A"))
        }
        setContentView(tv)
    }
}
