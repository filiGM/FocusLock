package com.focuslock.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var permView: TextView
    private lateinit var statsView: TextView
    private lateinit var enableSwitch: Switch
    private lateinit var limitEt: EditText
    private lateinit var coolEt: EditText
    private lateinit var winEt: EditText
    private var suppress = false

    private val refresh = object : Runnable {
        override fun run() {
            updatePerms()
            updateStats()
            handler.postDelayed(this, 5000)
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun heading(t: String) = TextView(this).apply {
        text = t
        textSize = 18f
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        setPadding(0, dp(20), 0, dp(6))
    }

    private fun btn(t: String, onClick: () -> Unit) = Button(this).apply {
        text = t
        setOnClickListener { onClick() }
    }

    private fun numberField(value: Int) = EditText(this).apply {
        inputType = InputType.TYPE_CLASS_NUMBER
        setText(value.toString())
        gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(dp(90), LinearLayout.LayoutParams.WRAP_CONTENT)
    }

    private fun row(label: String, field: EditText) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(TextView(this@MainActivity).apply {
            text = label
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        addView(field)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(32))
        }
        scroll.addView(col)

        // --- Permissions ---
        col.addView(heading("1. Permissions (all needed)"))
        permView = TextView(this)
        col.addView(permView)
        col.addView(btn("Allow Usage access") {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        })
        col.addView(btn("Allow Display over other apps") {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
        })
        col.addView(btn("Allow Notifications") {
            if (Build.VERSION.SDK_INT >= 33) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            }
        })
        col.addView(btn("Disable battery optimization") {
            try {
                startActivity(
                    Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:$packageName")
                    )
                )
            } catch (t: Throwable) {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        })

        // --- Switch ---
        col.addView(heading("2. Turn it on"))
        enableSwitch = Switch(this).apply {
            text = "Monitoring and locking"
            isChecked = Prefs.enabled(this@MainActivity)
            setOnCheckedChangeListener { _, on -> if (!suppress) onToggle(on) }
        }
        col.addView(enableSwitch)

        // --- Rules ---
        col.addView(heading("3. Rules"))
        limitEt = numberField(Prefs.limitMin(this))
        coolEt = numberField(Prefs.cooldownMin(this))
        winEt = numberField(Prefs.windowMin(this))
        col.addView(row("Daily limit per app (minutes)", limitEt))
        col.addView(row("Locked for (minutes)", coolEt))
        col.addView(row("Then allowed for (minutes)", winEt))
        col.addView(btn("Save rules") { saveRules() })
        col.addView(btn("Choose apps to lock") { chooseApps() })

        // --- Stats ---
        col.addView(heading("4. Your time"))
        statsView = TextView(this).apply { textSize = 15f }
        col.addView(statsView)

        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        suppress = true
        enableSwitch.isChecked = Prefs.enabled(this)
        suppress = false
        if (Prefs.enabled(this)) startMonitor()
        handler.post(refresh)
    }

    override fun onPause() {
        handler.removeCallbacks(refresh)
        super.onPause()
    }

    private fun startMonitor() {
        try {
            startForegroundService(Intent(this, MonitorService::class.java))
        } catch (t: Throwable) {
            Toast.makeText(this, "Could not start service: ${t.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun onToggle(on: Boolean) {
        if (!on && Prefs.anyLocked(this)) {
            Toast.makeText(this, "An app is locked right now. You can't switch FocusLock off.", Toast.LENGTH_LONG).show()
            suppress = true
            enableSwitch.isChecked = true
            suppress = false
            return
        }
        Prefs.setEnabled(this, on)
        if (on) startMonitor() else stopService(Intent(this, MonitorService::class.java))
    }

    private fun saveRules() {
        if (Prefs.anyLocked(this)) {
            Toast.makeText(this, "Rules can't be changed while an app is locked.", Toast.LENGTH_LONG).show()
            return
        }
        val l = limitEt.text.toString().toIntOrNull()?.coerceAtLeast(1) ?: 30
        val c = coolEt.text.toString().toIntOrNull()?.coerceAtLeast(1) ?: 180
        val w = winEt.text.toString().toIntOrNull()?.coerceAtLeast(1) ?: 10
        Prefs.saveSettings(this, l, c, w)
        Toast.makeText(this, "Saved: ${l}m/day, locked ${c}m, then ${w}m", Toast.LENGTH_SHORT).show()
        updateStats()
    }

    private fun chooseApps() {
        if (Prefs.anyLocked(this)) {
            Toast.makeText(this, "Can't change apps while an app is locked.", Toast.LENGTH_LONG).show()
            return
        }
        val pm = packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val items = pm.queryIntentActivities(launcher, 0)
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .filter { it.first != packageName }
            .distinctBy { it.first }
            .sortedBy { it.second.lowercase(Locale.getDefault()) }
        val current = Prefs.apps(this)
        val checked = BooleanArray(items.size) { items[it].first in current }
        AlertDialog.Builder(this)
            .setTitle("Apps to limit and lock")
            .setMultiChoiceItems(items.map { it.second }.toTypedArray(), checked) { _, i, v ->
                checked[i] = v
            }
            .setPositiveButton("Save") { _, _ ->
                val sel = HashSet<String>()
                items.forEachIndexed { i, p -> if (checked[i]) sel.add(p.first) }
                Prefs.setApps(this, sel)
                updateStats()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updatePerms() {
        val usage = UsageHelper.hasUsageAccess(this)
        val overlay = Settings.canDrawOverlays(this)
        val notif = Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val battery = (getSystemService(POWER_SERVICE) as PowerManager)
            .isIgnoringBatteryOptimizations(packageName)
        fun m(b: Boolean) = if (b) "✅" else "❌"
        permView.text = "${m(usage)} Usage access\n${m(overlay)} Display over other apps\n" +
            "${m(notif)} Notifications\n${m(battery)} Battery optimization off"
    }

    private fun updateStats() {
        if (!UsageHelper.hasUsageAccess(this)) {
            statsView.text = "Allow Usage access to see your time."
            return
        }
        val tracked = Prefs.apps(this).filter { UsageHelper.isInstalled(this, it) }
        if (tracked.isEmpty()) {
            statsView.text = "No tracked apps installed. Tap \"Choose apps to lock\"."
            return
        }
        val limitMs = Prefs.limitMin(this) * 60_000L
        Thread {
            val now = System.currentTimeMillis()
            val todayStart = UsageHelper.startOfDay(now)
            val set = tracked.toSet()
            val today = UsageHelper.usageByPackage(this, todayStart, now, set)
            val sb = StringBuilder("TODAY\n")
            for (p in tracked.sortedByDescending { today[it] ?: 0L }) {
                val used = today[p] ?: 0L
                val lock = Prefs.lockedUntil(this, p)
                val win = Prefs.windowUntil(this, p)
                val tag = when {
                    lock > now -> "  🔒 ${(lock - now) / 60000 + 1}m left"
                    win > now -> "  ⏳ window ${(win - now) / 60000 + 1}m"
                    else -> ""
                }
                sb.append("• ${UsageHelper.label(this, p)}: ${UsageHelper.fmt(used)} / ${Prefs.limitMin(this)}m$tag\n")
            }
            sb.append("Total today: ${UsageHelper.fmt(today.values.sum())}\n\n")

            val weekPer = HashMap<String, Long>()
            var weekTotal = 0L
            val days = StringBuilder()
            val dayFmt = SimpleDateFormat("EEE d MMM", Locale.getDefault())
            for (d in 0..6) {
                val cal = Calendar.getInstance().apply {
                    timeInMillis = todayStart
                    add(Calendar.DAY_OF_YEAR, -d)
                }
                val s = cal.timeInMillis
                val e = if (d == 0) now else Calendar.getInstance().apply {
                    timeInMillis = s
                    add(Calendar.DAY_OF_YEAR, 1)
                }.timeInMillis
                val m = UsageHelper.usageByPackage(this, s, e, set)
                val t = m.values.sum()
                weekTotal += t
                for ((k, v) in m) weekPer[k] = (weekPer[k] ?: 0L) + v
                days.append("${dayFmt.format(Date(s))}: ${UsageHelper.fmt(t)}\n")
            }
            sb.append("LAST 7 DAYS BY DAY\n").append(days).append("\nLAST 7 DAYS BY APP\n")
            for (p in tracked.sortedByDescending { weekPer[it] ?: 0L }) {
                sb.append("• ${UsageHelper.label(this, p)}: ${UsageHelper.fmt(weekPer[p] ?: 0L)}\n")
            }
            sb.append("Total (7 days): ${UsageHelper.fmt(weekTotal)}")
            runOnUiThread { statsView.text = sb.toString() }
        }.start()
    }
}
