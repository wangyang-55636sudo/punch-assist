package com.diy.punch

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 重启或应用更新后重新排程，否则定时任务会丢。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val p = Prefs(context)
        if (!p.enabled) return
        if (p.pkg.isEmpty()) return
        AlarmScheduler.scheduleBoth(context)
    }
}
