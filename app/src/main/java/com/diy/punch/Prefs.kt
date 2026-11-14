package com.diy.punch

import android.content.Context

class Prefs(ctx: Context) {

    private val sp = ctx.getSharedPreferences("cfg", Context.MODE_PRIVATE)

    var pkg: String
        get() = sp.getString("pkg", "") ?: ""
        set(v) = sp.edit().putString("pkg", v).apply()

    var inTime: String
        get() = sp.getString("inTime", "08:10") ?: "08:10"
        set(v) = sp.edit().putString("inTime", v).apply()

    var outTime: String
        get() = sp.getString("outTime", "17:30") ?: "17:30"
        set(v) = sp.edit().putString("outTime", v).apply()

    var workbench: String
        get() = sp.getString("workbench", "工作台") ?: "工作台"
        set(v) = sp.edit().putString("workbench", v).apply()

    var entry: String
        get() = sp.getString("entry", "打卡") ?: "打卡"
        set(v) = sp.edit().putString("entry", v).apply()

    var inButton: String
        get() = sp.getString("inButton", "上班打卡") ?: "上班打卡"
        set(v) = sp.edit().putString("inButton", v).apply()

    var outButton: String
        get() = sp.getString("outButton", "下班打卡") ?: "下班打卡"
        set(v) = sp.edit().putString("outButton", v).apply()

    var enabled: Boolean
        get() = sp.getBoolean("enabled", true)
        set(v) = sp.edit().putBoolean("enabled", v).apply()

    /** in / out */
    var mode: String
        get() = sp.getString("mode", "in") ?: "in"
        set(v) = sp.edit().putString("mode", v).apply()

    /** 是否有待完成的任务（到点触发后置 true，成功或超时置 false） */
    var pending: Boolean
        get() = sp.getBoolean("pending", false)
        set(v) = sp.edit().putBoolean("pending", v).apply()

    /** 任务截止时间戳。期间一直挂起，锁屏也不放弃；过了就清掉 */
    var deadline: Long
        get() = sp.getLong("deadline", 0L)
        set(v) = sp.edit().putLong("deadline", v).apply()

    /** 只诊断不点击：把界面上找到的文字打进日志，用来调文案 */
    var dryRun: Boolean
        get() = sp.getBoolean("dryRun", false)
        set(v) = sp.edit().putBoolean("dryRun", v).apply()

    /** 最近一次结果，给界面显示 */
    var lastResult: String
        get() = sp.getString("lastResult", "还没跑过") ?: "还没跑过"
        set(v) = sp.edit().putString("lastResult", v).apply()

    fun armed(): Boolean = pending && System.currentTimeMillis() < deadline
}
