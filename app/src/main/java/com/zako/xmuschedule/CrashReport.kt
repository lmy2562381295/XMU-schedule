package com.zako.xmuschedule

import android.content.Context
import android.util.Log
import java.io.File

/**
 * 全局崩溃捕获：未捕获异常写入 files/crash_report.txt 后交还系统处理。
 * 下次启动时 AppRoot 会读取并展示该报告，便于把堆栈反馈给开发者。
 */
object CrashReport {

    private const val FILE = "crash_report.txt"

    fun install(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                File(context.filesDir, FILE).writeText(
                    buildString {
                        append("time: ").append(System.currentTimeMillis()).append('\n')
                        append("thread: ").append(thread.name).append('\n')
                        append(Log.getStackTraceString(throwable))
                    },
                )
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun read(context: Context): String? =
        File(context.filesDir, FILE)
            .takeIf { it.exists() }
            ?.readText()
            ?.takeIf { it.isNotBlank() }

    fun clear(context: Context) {
        File(context.filesDir, FILE).delete()
    }
}
