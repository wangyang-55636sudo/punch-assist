package com.diy.punch

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * 后台启动 Activity 被系统拦下时的兜底：发一条高优先级通知，点一下即可继续。
 */
object Notifier {

    private const val CHANNEL = "punch"
    private const val ID = 2001

    fun fallback(context: Context, mode: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CHANNEL, "打卡提醒", NotificationManager.IMPORTANCE_HIGH)
            nm.createNotificationChannel(ch)
        }
        val pi = PendingIntent.getActivity(
            context,
            3001,
            Intent(context, PunchLaunchActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val title = if (mode == "out") "该签退了" else "该签到了"
        val builder = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(context, CHANNEL)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }
        val n = builder
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText("点此打开八局通")
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        try {
            nm.notify(ID, n)
        } catch (e: Exception) {
            // 没有通知权限时静默
        }
    }
}
