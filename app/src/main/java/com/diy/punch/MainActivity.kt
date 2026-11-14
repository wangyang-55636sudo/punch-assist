package com.diy.punch

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.TimePicker
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var status: TextView
    private lateinit var pkgView: TextView
    private lateinit var logView: TextView
    private lateinit var drySwitch: Switch
    private lateinit var inPicker: TimePicker
    private lateinit var outPicker: TimePicker
    private lateinit var workbenchEdit: EditText
    private lateinit var entryEdit: EditText
    private lateinit var inBtnEdit: EditText
    private lateinit var outBtnEdit: EditText

    private val ui = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            refreshStatus()
            logView.text = Diag.dump()
            ui.postDelayed(this, 1500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        setContentView(buildUi())
        refreshStatus()
        logView.text = Diag.dump()
    }

    override fun onResume() {
        super.onResume()
        ui.post(tick)
    }

    override fun onPause() {
        super.onPause()
        ui.removeCallbacks(tick)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun title(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 14f
        setTextColor(Color.parseColor("#444444"))
        setPadding(0, dp(16), 0, dp(4))
    }

    private fun button(text: String, onClick: () -> Unit): Button = Button(this).apply {
        this.text = text
        isAllCaps = false
        setOnClickListener { onClick() }
    }

    private fun edit(value: String): EditText = EditText(this).apply {
        setText(value)
        inputType = InputType.TYPE_CLASS_TEXT
    }

    private fun buildUi(): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(20), dp(18), dp(30))
        }

        status = TextView(this).apply { textSize = 14f; setTextColor(Color.parseColor("#222222")) }
        col.addView(status)

        pkgView = TextView(this).apply { textSize = 14f; setPadding(0, dp(10), 0, dp(4)) }
        col.addView(pkgView)

        col.addView(title("第一步：权限"))
        col.addView(button("① 选择八局通应用") { pickApp() })
        col.addView(button("② 打开无障碍设置") {
            try {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } catch (e: Exception) {
                toast("打不开无障碍设置")
            }
        })
        col.addView(button("③ 允许后台弹出界面") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        })
        col.addView(button("④ 忽略电池优化") {
            try {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        })

        col.addView(title("第二步：测试"))
        col.addView(button("测试上班打卡") { test("in") })
        col.addView(button("测试下班打卡") { test("out") })

        drySwitch = Switch(this).apply {
            text = "只诊断不点击（把界面文字打进日志）"
            textSize = 14f
            isChecked = prefs.dryRun
            setOnCheckedChangeListener { _, checked -> prefs.dryRun = checked }
        }
        col.addView(drySwitch)

        col.addView(title("第三步：时间"))
        inPicker = TimePicker(this).apply { setIs24HourView(true) }
        val (ih, im) = AlarmScheduler.parse(prefs.inTime)
        inPicker.hour = ih
        inPicker.minute = im
        col.addView(inPicker)
        col.addView(TextView(this).apply {
            text = "上班时间：" + prefs.inTime
            textSize = 12.5f
            setTextColor(Color.parseColor("#666666"))
        })

        outPicker = TimePicker(this).apply { setIs24HourView(true) }
        val (oh, om) = AlarmScheduler.parse(prefs.outTime)
        outPicker.hour = oh
        outPicker.minute = om
        col.addView(outPicker)
        col.addView(TextView(this).apply {
            text = "下班时间：" + prefs.outTime
            textSize = 12.5f
            setTextColor(Color.parseColor("#666666"))
        })

        col.addView(title("第四步：界面文字（对不上就改这里）"))
        col.addView(labelSmall("工作台按钮文字"))
        workbenchEdit = edit(prefs.workbench)
        col.addView(workbenchEdit)

        col.addView(labelSmall("打卡入口文字"))
        entryEdit = edit(prefs.entry)
        col.addView(entryEdit)

        col.addView(labelSmall("上班打卡按钮文字"))
        inBtnEdit = edit(prefs.inButton)
        col.addView(inBtnEdit)

        col.addView(labelSmall("下班打卡按钮文字"))
        outBtnEdit = edit(prefs.outButton)
        col.addView(outBtnEdit)

        col.addView(button("保存并排程") { save() })
        col.addView(button("清空任务") {
            AlarmScheduler.cancelAll(this)
            prefs.pending = false
            prefs.deadline = 0L
            prefs.lastResult = "已手动清空"
            toast("已清空定时和挂起任务")
        })

        col.addView(title("日志（每秒刷新）"))
        logView = TextView(this).apply {
            textSize = 11.5f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.parseColor("#333333"))
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setBackgroundColor(Color.parseColor("#F2F2F2"))
        }
        col.addView(logView)
        col.addView(button("清空日志") { Diag.clear(); logView.text = Diag.dump() })

        col.addView(TextView(this).apply {
            text = "锁屏时会挂起任务，等你解锁后自动补打卡。任务有效期 30 分钟，" +
                "超时会放弃并写进日志。定位不在范围内系统会拦，工具无能为力。"
            textSize = 12.5f
            setTextColor(Color.parseColor("#666666"))
            setPadding(0, dp(18), 0, 0)
        })

        return ScrollView(this).apply { addView(col) }
    }

    private fun labelSmall(t: String): TextView = TextView(this).apply {
        text = t
        textSize = 13f
        setTextColor(Color.parseColor("#666666"))
        setPadding(0, dp(8), 0, dp(2))
    }

    private fun refreshStatus() {
        val acc = isAccessibilityOn()
        val overlay = Settings.canDrawOverlays(this)
        val battery = isIgnoringBattery()
        val pendingText = if (prefs.pending) {
            val left = ((prefs.deadline - System.currentTimeMillis()) / 60000).coerceAtLeast(0)
            "有挂起任务（${if (prefs.mode == "out") "签退" else "签到"}，剩 $left 分钟）"
        } else {
            "无挂起任务"
        }
        status.text = buildString {
            appendLine("无障碍服务：" + if (acc) "已开" else "未开")
            appendLine("后台弹出权限：" + if (overlay) "已允许" else "未允许")
            appendLine("电池优化：" + if (battery) "已忽略" else "仍受限")
            appendLine("任务状态：$pendingText")
            appendLine("上次结果：" + prefs.lastResult)
        }
        pkgView.text = if (prefs.pkg.isEmpty()) "八局通：未选择" else "八局通：${prefs.pkg}"
    }

    private fun save() {
        prefs.workbench = workbenchEdit.text.toString().ifBlank { "工作台" }
        prefs.entry = entryEdit.text.toString().ifBlank { "打卡" }
        prefs.inButton = inBtnEdit.text.toString().ifBlank { "上班打卡" }
        prefs.outButton = outBtnEdit.text.toString().ifBlank { "下班打卡" }
        prefs.inTime = AlarmScheduler.format(inPicker.hour, inPicker.minute)
        prefs.outTime = AlarmScheduler.format(outPicker.hour, outPicker.minute)
        prefs.enabled = true

        if (prefs.pkg.isEmpty()) {
            toast("先选八局通应用")
            return
        }
        AlarmScheduler.scheduleBoth(this)
        refreshStatus()
        toast("已排程：${prefs.inTime} / ${prefs.outTime}")
    }

    private fun test(mode: String) {
        if (prefs.pkg.isEmpty()) {
            toast("先选八局通应用")
            return
        }
        if (!isAccessibilityOn()) {
            toast("先开无障碍服务，否则点不动")
            return
        }
        prefs.mode = mode
        prefs.pending = true
        prefs.deadline = System.currentTimeMillis() + 10 * 60 * 1000
        prefs.lastResult = "测试触发（${if (mode == "out") "签退" else "签到"}）"
        Diag.add("手动测试：${if (mode == "out") "签退" else "签到"}")
        startActivity(Intent(this, PunchLaunchActivity::class.java))
    }

    private fun pickApp() {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != packageName }
            .map { it.loadLabel(pm).toString() to it.activityInfo.packageName }
            .distinctBy { it.second }
            .sortedBy { it.first }

        if (apps.isEmpty()) {
            toast("没查到可启动的应用")
            return
        }
        val items = apps.map { "${it.first}\n${it.second}" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("选择八局通")
            .setItems(items) { _, i ->
                prefs.pkg = apps[i].second
                refreshStatus()
                toast("已选择：${apps[i].first}")
            }
            .show()
    }

    private fun isAccessibilityOn(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val list = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        return list.any { it.resolveInfo?.serviceInfo?.packageName == packageName }
    }

    private fun isIgnoringBattery(): Boolean {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
