package com.diy.punch

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val mode = intent.getStringExtra("mode") ?: "in"
        val prefs = Prefs(context)
        if (!prefs.enabled) return

        // 点亮屏幕
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        @Suppress("DEPRECATION")
        val wake = pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "punch:wake"
        )
        try {
            wake.acquire(10_000L)
        } catch (e: Exception) {
            // 忽略
        } finally {
            if (wake.isHeld) wake.release()
        }

        // 挂起任务：30 分钟内有效，锁屏不解锁就一直等
        prefs.mode = mode
        prefs.pending = true
        prefs.deadline = System.currentTimeMillis() + 30 * 60 * 1000
        prefs.lastResult = "到点触发，等待执行"
        Diag.add("定时触发（${if (mode == "out") "签退" else "签到"}），任务挂起 30 分钟")

        // 排下一天
        val (h, m) = AlarmScheduler.parse(if (mode == "out") prefs.outTime else prefs.inTime)
        AlarmScheduler.schedule(
            context, h, m, mode,
            if (mode == "out") AlarmScheduler.REQ_OUT else AlarmScheduler.REQ_IN
        )

        val launch = Intent(context, PunchLaunchActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(launch)
        } catch (e: Exception) {
            Diag.add("后台拉起失败，等解锁后补上")
            Notifier.fallback(context, mode)
        }
    }
}
