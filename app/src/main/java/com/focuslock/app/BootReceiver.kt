package com.focuslock.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Restarts monitoring after the phone reboots. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED && Prefs.enabled(context)) {
            try {
                context.startForegroundService(Intent(context, MonitorService::class.java))
            } catch (t: Throwable) {
            }
        }
    }
}
