package com.diy.punch

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 解锁后补打卡：到点时如果屏幕是锁着的，任务会挂起；
 * 你一解锁，这里立刻重新拉起八局通，无障碍服务接着点。
 */
class UnlockReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_USER_PRESENT) return

        val p = Prefs(context)
        if (!p.armed()) return

        Diag.add("检测到解锁，继续未完成的打卡任务")
        p.lastResult = "解锁后补打卡中"

        val launch = Intent(context, PunchLaunchActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(launch)
        } catch (e: Exception) {
            Notifier.fallback(context, p.mode)
        }
    }
}
