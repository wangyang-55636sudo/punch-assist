package com.diy.punch

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.KeyguardManager
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

/**
 * 核心：在八局通界面里按文字找节点并点击。
 *
 * 行为：
 *  - 到点后任务挂起（Prefs.pending），屏幕锁着就一直等，不消耗重试次数
 *  - 解锁后继续，扫描顺序：目标按钮 → 打卡入口 → 工作台
 *  - 干跑模式（dryRun）只把界面文字写进日志，不点击，用来调文案
 */
class PunchAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var attempts = 0
    private var dryRounds = 0
    private var lastKey = ""
    private var lastClickAt = 0L
    private var loggedLocked = false
    private var loggedPkg = ""

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        kick()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        kick()
    }

    /** 只在有待办任务时启动扫描 */
    private fun kick() {
        if (running) return
        val p = Prefs(this)
        if (!p.pending) return
        if (System.currentTimeMillis() > p.deadline) {
            finish("已超时，未完成打卡")
            return
        }
        running = true
        attempts = 0
        dryRounds = 0
        handler.post { scan() }
    }

    private fun scan() {
        val p = Prefs(this)
        if (!p.pending) { running = false; return }
        if (System.currentTimeMillis() > p.deadline) { finish("已超时，未完成打卡"); return }

        // 锁屏：挂起等待，不解锁不动手，也不耗重试
        if (isLocked()) {
            if (!loggedLocked) {
                Diag.add("锁屏中，任务挂起，等解锁后自动补打卡")
                prefs().lastResult = "锁屏挂起中"
                loggedLocked = true
            }
            handler.postDelayed({ scan() }, 2000)
            return
        }
        loggedLocked = false

        val root = rootInActiveWindow
        if (root == null) {
            if (attempts++ > 40) { finish("读不到界面") } else retry()
            return
        }

        val currentPkg = root.packageName?.toString() ?: ""
        if (p.pkg.isNotEmpty() && currentPkg != p.pkg) {
            if (loggedPkg != currentPkg) {
                Diag.add("当前前台是 $currentPkg，不是八局通，继续等")
                loggedPkg = currentPkg
            }
            if (attempts++ > 40) { finish("八局通没到前台") } else retry()
            return
        }
        loggedPkg = currentPkg

        // 干跑：只记录文字
        if (p.dryRun) {
            val texts = collectTexts(root).take(40)
            Diag.add("诊断扫描：$texts")
            dryRounds++
            if (dryRounds >= 3) finish("诊断结束，去日志看文字") else retry()
            return
        }

        val target = if (p.mode == "out") p.outButton else p.inButton

        val targetNode = findNode(root, target)
        if (targetNode != null) {
            Diag.add("找到按钮「$target」")
            if (clickNode(targetNode, "target")) { finish("已点击「$target」"); return }
        }

        val entryNode = findNode(root, p.entry)
        if (entryNode != null && clickNode(entryNode, "entry")) {
            Diag.add("点击入口「${p.entry}」，等待页面切换")
            retry(); return
        }

        val wbNode = findNode(root, p.workbench)
        if (wbNode != null && clickNode(wbNode, "workbench")) {
            Diag.add("点击「${p.workbench}」，等待页面切换")
            retry(); return
        }

        if (attempts++ > 40) finish("没找到按钮，检查文字设置") else retry()
    }

    private fun prefs() = Prefs(this)

    private fun retry() {
        handler.postDelayed({ scan() }, 900)
    }

    private fun isLocked(): Boolean {
        val km = getSystemService(KeyguardManager::class.java)
        if (km != null && km.isKeyguardLocked) return true
        val pm = getSystemService(PowerManager::class.java)
        return pm != null && !pm.isInteractive
    }

    private fun clickNode(node: AccessibilityNodeInfo, tag: String): Boolean {
        val key = tag + "|" + node.viewIdResourceName + "|" + node.text
        val now = System.currentTimeMillis()
        if (key == lastKey && now - lastClickAt < 2500) return false

        val clickable = clickableAncestor(node)
        var ok = false
        if (clickable != null && clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)) ok = true
        if (!ok) ok = tapCenterOf(node)

        if (ok) {
            lastKey = key
            lastClickAt = now
        }
        return ok
    }

    private fun tapCenterOf(node: AccessibilityNodeInfo): Boolean {
        val r = Rect()
        node.getBoundsInScreen(r)
        if (r.width() <= 0 || r.height() <= 0) return false
        val path = Path().apply { moveTo(r.exactCenterX(), r.exactCenterY()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 60L))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    private fun clickableAncestor(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        var cur = node
        var depth = 0
        while (cur != null && depth < 6) {
            if (cur.isClickable) return cur
            cur = cur.parent
            depth++
        }
        return null
    }

    private fun findNode(root: AccessibilityNodeInfo, keyword: String): AccessibilityNodeInfo? {
        val k = keyword.trim()
        if (k.isEmpty()) return null
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var guard = 0
        while (queue.isNotEmpty() && guard < 1200) {
            guard++
            val n = queue.removeFirst()
            val text = n.text?.toString() ?: ""
            val desc = n.contentDescription?.toString() ?: ""
            if (text.contains(k) || desc.contains(k)) return n
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }
        return null
    }

    private fun collectTexts(root: AccessibilityNodeInfo): List<String> {
        val out = LinkedHashSet<String>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var guard = 0
        while (queue.isNotEmpty() && guard < 1200 && out.size < 60) {
            guard++
            val n = queue.removeFirst()
            val t = n.text?.toString()?.trim().orEmpty()
            val d = n.contentDescription?.toString()?.trim().orEmpty()
            if (t.isNotEmpty()) out.add(t)
            if (d.isNotEmpty()) out.add("[$d]")
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }
        return out.toList()
    }

    private fun finish(msg: String) {
        running = false
        attempts = 0
        val p = Prefs(this)
        p.pending = false
        p.deadline = 0L
        p.lastResult = msg
        Diag.add(msg)
        Toast.makeText(this, "打卡助手：$msg", Toast.LENGTH_SHORT).show()
    }

    override fun onInterrupt() {
        running = false
    }

    companion object {
        var instance: PunchAccessibilityService? = null
    }
}
