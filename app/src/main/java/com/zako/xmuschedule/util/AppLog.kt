package com.zako.xmuschedule.util

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 轻量本地事件日志：用于把关键状态转换持久化，随崩溃报告一起展示，便于远程排障 */
object AppLog {

    private const val FILE = "debug_log.txt"
    private const val MAX_LINES = 300

    @Synchronized
    fun event(context: Context, tag: String, message: String) {
        runCatching {
            val f = File(context.filesDir, FILE)
            val ts = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(Date())
            val line = "$ts [$tag] $message"
            val combined = (f.takeIf { it.exists() }?.readText().orEmpty() + "\n" + line)
                .lines()
                .takeLast(MAX_LINES)
                .joinToString("\n")
            f.writeText(combined)
        }
    }

    @Synchronized
    fun error(context: Context, tag: String, t: Throwable) {
        event(
            context, tag,
            "ERROR ${t.javaClass.simpleName}: ${t.message}\n" +
                android.util.Log.getStackTraceString(t).take(1200),
        )
    }

    fun read(context: Context): String =
        File(context.filesDir, FILE)
            .takeIf { it.exists() }
            ?.readText()
            .orEmpty()
            .lines()
            .takeLast(80)
            .joinToString("\n")

    @Synchronized
    fun clear(context: Context) {
        File(context.filesDir, FILE).delete()
    }
}
