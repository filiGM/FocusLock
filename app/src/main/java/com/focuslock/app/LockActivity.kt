package com.focuslock.app

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** Full-screen "time is up" screen shown on top of a locked social app. */
class LockActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private var until = 0L
    private lateinit var title: TextView
    private lateinit var timer: TextView

    private val tick = object : Runnable {
        override fun run() {
            val left = until - System.currentTimeMillis()
            if (left <= 0) {
                goHome()
                return
            }
            val s = left / 1000
            timer.text = String.format("%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#101820"))
            setPadding(48, 48, 48, 48)
        }
        title = TextView(this).apply {
            textSize = 24f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        val sub = TextView(this).apply {
            text = "Daily limit reached. Locked for:"
            textSize = 16f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 8)
        }
        timer = TextView(this).apply {
            textSize = 48f
            setTextColor(Color.parseColor("#FFB300"))
            gravity = Gravity.CENTER
        }
        val note = TextView(this).apply {
            text = "When the lock ends you get a short window of use.\nGo do something good for yourself."
            textSize = 14f
            setTextColor(Color.GRAY)
            gravity = Gravity.CENTER
            setPadding(0, 32, 0, 32)
        }
        val btn = Button(this).apply {
            text = "Go to home screen"
            setOnClickListener { goHome() }
        }
        col.addView(title)
        col.addView(sub)
        col.addView(timer)
        col.addView(note)
        col.addView(btn)
        setContentView(col)
        applyIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyIntent(intent)
    }

    private fun applyIntent(i: Intent?) {
        val pkg = i?.getStringExtra("pkg") ?: ""
        until = i?.getLongExtra("until", 0L) ?: 0L
        title.text = "Time's up for " + UsageHelper.label(this, pkg)
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        goHome()
    }

    private fun goHome() {
        handler.removeCallbacks(tick)
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finish()
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        super.onDestroy()
    }
}
