package com.focuslock.app

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** All settings and lock state live here (SharedPreferences). */
object Prefs {

    // TikTok (+Lite), Facebook (+Lite), Instagram (+Lite), YouTube
    val DEFAULT_APPS: Set<String> = setOf(
        "com.zhiliaoapp.musically",
        "com.zhiliaoapp.musically.go",
        "com.ss.android.ugc.trill",
        "com.facebook.katana",
        "com.facebook.lite",
        "com.instagram.android",
        "com.instagram.lite",
        "com.google.android.youtube"
    )

    private fun sp(c: Context): SharedPreferences =
        c.getSharedPreferences("focuslock", Context.MODE_PRIVATE)

    // ---- settings ----
    fun enabled(c: Context) = sp(c).getBoolean("enabled", false)
    fun setEnabled(c: Context, v: Boolean) = sp(c).edit().putBoolean("enabled", v).apply()

    /** Daily limit per app, minutes. */
    fun limitMin(c: Context) = sp(c).getInt("limit", 30)

    /** How long an app stays locked after the limit is hit, minutes. */
    fun cooldownMin(c: Context) = sp(c).getInt("cooldown", 180)

    /** How long you may use the app after the lock ends, minutes. */
    fun windowMin(c: Context) = sp(c).getInt("window", 10)

    fun saveSettings(c: Context, limit: Int, cooldown: Int, window: Int) {
        sp(c).edit()
            .putInt("limit", limit)
            .putInt("cooldown", cooldown)
            .putInt("window", window)
            .apply()
    }

    fun apps(c: Context): Set<String> =
        sp(c).getStringSet("apps", null)?.toSet() ?: DEFAULT_APPS

    fun setApps(c: Context, apps: Set<String>) =
        sp(c).edit().putStringSet("apps", HashSet(apps)).apply()

    // ---- per-app lock state ----
    fun lockedUntil(c: Context, pkg: String) = sp(c).getLong("lock_$pkg", 0L)
    fun windowUntil(c: Context, pkg: String) = sp(c).getLong("win_$pkg", 0L)
    fun pending(c: Context, pkg: String) = sp(c).getBoolean("pend_$pkg", false)

    fun startCooldown(c: Context, pkg: String, now: Long) {
        sp(c).edit()
            .putLong("lock_$pkg", now + cooldownMin(c) * 60_000L)
            .putLong("win_$pkg", 0L)
            .putBoolean("pend_$pkg", true)
            .apply()
    }

    fun startWindow(c: Context, pkg: String, now: Long) {
        sp(c).edit()
            .putLong("win_$pkg", now + windowMin(c) * 60_000L)
            .putBoolean("pend_$pkg", false)
            .apply()
    }

    fun anyLocked(c: Context): Boolean {
        val now = System.currentTimeMillis()
        return sp(c).all.any { (k, v) -> k.startsWith("lock_") && (v as? Long ?: 0L) > now }
    }

    /** New day => daily limits and locks start fresh. */
    fun rollDayIfNeeded(c: Context) {
        val today = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        val p = sp(c)
        if (p.getString("day", "") == today) return
        val ed = p.edit()
        for (k in p.all.keys) {
            if (k.startsWith("lock_") || k.startsWith("win_") || k.startsWith("pend_")) ed.remove(k)
        }
        ed.putString("day", today).apply()
    }
}
