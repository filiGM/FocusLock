package com.focuslock.app

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import java.util.Calendar

object UsageHelper {

    fun hasUsageAccess(c: Context): Boolean {
        val ops = c.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            ops.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun startOfDay(ts: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = ts
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /** Foreground milliseconds per package in [start, end], for the packages in [only]. */
    fun usageByPackage(c: Context, start: Long, end: Long, only: Set<String>): Map<String, Long> {
        val usm = c.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val events = usm.queryEvents(start, end)
        val e = UsageEvents.Event()
        val fgStart = HashMap<String, Long>()
        val total = HashMap<String, Long>()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            val pkg = e.packageName ?: continue
            if (pkg !in only) continue
            when (e.eventType) {
                1 -> fgStart[pkg] = e.timeStamp            // MOVE_TO_FOREGROUND / ACTIVITY_RESUMED
                2 -> {                                      // MOVE_TO_BACKGROUND / ACTIVITY_PAUSED
                    val s = fgStart.remove(pkg)
                    if (s != null) total[pkg] = (total[pkg] ?: 0L) + (e.timeStamp - s)
                }
            }
        }
        for ((pkg, s) in fgStart) total[pkg] = (total[pkg] ?: 0L) + (end - s)
        return total
    }

    fun label(c: Context, pkg: String): String = try {
        val pm = c.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (t: Throwable) {
        pkg
    }

    fun isInstalled(c: Context, pkg: String): Boolean = try {
        c.packageManager.getApplicationInfo(pkg, 0)
        true
    } catch (t: Throwable) {
        false
    }

    fun fmt(ms: Long): String {
        val totalMin = ms / 60_000
        val h = totalMin / 60
        val m = totalMin % 60
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }
}
