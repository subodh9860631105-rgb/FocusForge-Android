package com.focusforge.pro

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val tv = TextView(this).apply {
            text = "FocusForge Pro is Running Successfully!"
            setTextColor(Color.BLACK)
            textSize = 24f
            setPadding(50, 100, 50, 50)
        }
        setContentView(tv)
    }
}
