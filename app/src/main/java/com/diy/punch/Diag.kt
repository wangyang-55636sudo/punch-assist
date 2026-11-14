package com.diy.punch

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 内存日志，方便在手机上直接看无障碍服务到底看到了什么。
 */
object Diag {

    private const val MAX = 200
    private val list = ArrayList<String>()
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    @Synchronized
    fun add(msg: String) {
        list.add(fmt.format(Date()) + "  " + msg)
        while (list.size > MAX) list.removeAt(0)
    }

    @Synchronized
    fun dump(): String = if (list.isEmpty()) "（还没有日志）" else list.joinToString("\n")

    @Synchronized
    fun clear() = list.clear()
}
