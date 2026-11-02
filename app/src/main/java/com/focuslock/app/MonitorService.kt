package com.focuslock.app

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper

/**
 * Runs in the background (foreground service). Every second it checks which app is on screen.
 * If it is a tracked social app that is over its daily limit (or still locked), it shows
 * the lock screen on top and kills the app's background process.
 */
class MonitorService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var lastQuery = 0L
    private var current: String? = null
    private var lastBlockAt = 0L
    private val usedCache = HashMap<String, Pair<Long, Long>>() // pkg -> (checkedAt, usedMs)

    private val tick = object : Runnable {
        override fun run() {
            try {
                check()
            } catch (t: Throwable) {
                // never let the loop die
            }
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "FocusLock", NotificationManager.IMPORTANCE_LOW)
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val n: Notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle("FocusLock is active")
            .setContentText("Watching your social media time")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }
        handler.post(tick)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        super.onDestroy()
    }

    private fun check() {
        if (!Prefs.enabled(this) || !UsageHelper.hasUsageAccess(this)) return
        val now = System.currentTimeMillis()
        Prefs.rollDayIfNeeded(this)
        updateForeground(now)
        val pkg = current ?: return
        if (pkg !in Prefs.apps(this)) return
        decide(pkg, now)
    }

    /** Work out which app is in the foreground from usage events. */
    private fun updateForeground(now: Long) {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val from = if (lastQuery == 0L) now - 10 * 60_000L else lastQuery
        val events = usm.queryEvents(from, now)
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            when (e.eventType) {
                1 -> current = e.packageName
                2 -> if (current == e.packageName) current = null
            }
        }
        lastQuery = now - 1500
    }

    private fun decide(pkg: String, now: Long) {
        val lockedUntil = Prefs.lockedUntil(this, pkg)
        val windowUntil = Prefs.windowUntil(this, pkg)

        // 1) Still locked
        if (now < lockedUntil) {
            block(pkg, lockedUntil, now)
            return
        }
        // 2) Inside the short unlock window
        if (windowUntil > 0L) {
            if (now < windowUntil) return
            Prefs.startCooldown(this, pkg, now)          // window over -> lock again
            block(pkg, Prefs.lockedUntil(this, pkg), now)
            return
        }
        // 3) Lock just ended and the app was opened again -> start the window
        if (Prefs.pending(this, pkg)) {
            Prefs.startWindow(this, pkg, now)
            return
        }
        // 4) Normal use: compare today's usage with the daily limit
        val used = usedToday(pkg, now)
        if (used >= Prefs.limitMin(this) * 60_000L) {
            Prefs.startCooldown(this, pkg, now)
            usedCache.remove(pkg)
            block(pkg, Prefs.lockedUntil(this, pkg), now)
        }
    }

    private fun usedToday(pkg: String, now: Long): Long {
        val c = usedCache[pkg]
        if (c != null && now - c.first < 5000) return c.second + (now - c.first)
        val u = UsageHelper.usageByPackage(this, UsageHelper.startOfDay(now), now, setOf(pkg))[pkg] ?: 0L
        usedCache[pkg] = Pair(now, u)
        return u
    }

    private fun block(pkg: String, until: Long, now: Long) {
        if (now - lastBlockAt < 3000) return
        lastBlockAt = now
        val i = Intent(this, LockActivity::class.java)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION
            )
            .putExtra("pkg", pkg)
            .putExtra("until", until)
        startActivity(i)
        // Once the lock screen covers it, also stop the app's process.
        handler.postDelayed({
            try {
                (getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
                    .killBackgroundProcesses(pkg)
            } catch (t: Throwable) {
            }
        }, 1500)
    }

    companion object {
        private const val CHANNEL = "focuslock"
    }
}
