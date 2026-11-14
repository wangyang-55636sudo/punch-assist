package com.diy.punch

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

/**
 * 每天固定时间触发一次。触发后 Receiver 会自己排下一天。
 */
object AlarmScheduler {

    const val REQ_IN = 1001
    const val REQ_OUT = 1002
    private const val ACTION = "com.diy.punch.ALARM"

    fun schedule(context: Context, hour: Int, minute: Int, mode: String, req: Int) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pending(context, mode, req)
        val at = nextTrigger(hour, minute)
        try {
            val exactAllowed = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
            if (exactAllowed) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    fun scheduleBoth(context: Context) {
        val p = Prefs(context)
        val (ih, im) = parse(p.inTime)
        val (oh, om) = parse(p.outTime)
        schedule(context, ih, im, "in", REQ_IN)
        schedule(context, oh, om, "out", REQ_OUT)
    }

    fun cancelAll(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pending(context, "in", REQ_IN))
        am.cancel(pending(context, "out", REQ_OUT))
    }

    private fun pending(context: Context, mode: String, req: Int): PendingIntent {
        val i = Intent(context, AlarmReceiver::class.java)
            .setAction(ACTION)
            .putExtra("mode", mode)
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= 23) flags = flags or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, req, i, flags)
    }

    private fun nextTrigger(hour: Int, minute: Int): Long {
        val c = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        return c.timeInMillis
    }

    fun parse(hhmm: String): Pair<Int, Int> {
        val parts = hhmm.split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: 8
        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
        return h.coerceIn(0, 23) to m.coerceIn(0, 59)
    }

    fun format(h: Int, m: Int): String = String.format("%02d:%02d", h, m)
}
