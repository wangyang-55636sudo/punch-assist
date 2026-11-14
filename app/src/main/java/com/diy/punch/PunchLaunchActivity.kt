package com.diy.punch

import android.app.Activity
import android.app.KeyguardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.WindowManager
import android.widget.Toast

/**
 * 透明 Activity：亮屏、打开八局通，然后自己退出。
 * 锁屏状态下不硬解，只亮屏；等用户解锁后由 UnlockReceiver 重新拉起。
 */
class PunchLaunchActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        val prefs = Prefs(this)

        if (prefs.pkg.isEmpty()) {
            Toast.makeText(this, "还没选八局通应用，先打开打卡助手设置", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // 锁屏就只亮屏，把任务留着，等解锁
        if (isLocked()) {
            Diag.add("屏幕锁定，已亮屏提醒，等解锁后自动补打卡")
            prefs.lastResult = "锁屏挂起中"
            Handler(Looper.getMainLooper()).postDelayed({ finish() }, 1200)
            return
        }

        val launch = packageManager.getLaunchIntentForPackage(prefs.pkg)
        if (launch == null) {
            Toast.makeText(this, "打不开 ${prefs.pkg}，检查包名", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        try {
            startActivity(launch)
            Diag.add("已打开八局通，等待无障碍服务点击")
        } catch (e: Exception) {
            Diag.add("打开八局通失败：${e.message}")
            Notifier.fallback(this, prefs.mode)
        }

        Handler(Looper.getMainLooper()).postDelayed({ finish() }, 700)
    }

    private fun isLocked(): Boolean {
        val km = getSystemService(KeyguardManager::class.java)
        return km?.isKeyguardLocked == true
    }
}
