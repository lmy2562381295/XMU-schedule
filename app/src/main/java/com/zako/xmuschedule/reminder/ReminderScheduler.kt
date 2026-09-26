package com.zako.xmuschedule.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.zako.xmuschedule.data.Prefs
import com.zako.xmuschedule.data.db.AppDatabase
import com.zako.xmuschedule.data.db.weekNumbers
import com.zako.xmuschedule.util.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 滚动 7 天窗口的课前提醒排程：
 * 只为未来 7 天内的课程设置精确闹钟，由每日维护任务（ReminderWorker）滚动补排，
 * 避免整学期数百个闹钟触及系统上限。
 */
object ReminderScheduler {

    private const val WINDOW_DAYS = 7L
    private const val INEXACT_WINDOW_MS = 10 * 60 * 1000L

    suspend fun scheduleWindow(context: Context) = withContext(Dispatchers.IO) {
        val db = AppDatabase.get(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        cancelAll(context, alarmManager)

        val config = db.termConfigDao().now()
        val firstMonday = config?.firstWeekMondayEpochDay
        if (firstMonday == null) {
            Prefs.setScheduledCodes(context, emptySet())
            return@withContext
        }

        val periods = db.periodTimeDao().allNow().associateBy { it.section }
        val courses = db.courseDao().allNow()
        val leadMinutes = Prefs.leadMinutes(context).toLong()
        val canExact = Build.VERSION.SDK_INT < 31 || alarmManager.canScheduleExactAlarms()

        val today = LocalDate.now()
        val scheduled = mutableSetOf<String>()

        for (offset in 0..WINDOW_DAYS) {
            val date = today.plusDays(offset)
            val epochDay = date.toEpochDay()
            val week = ((epochDay - firstMonday) / 7).toInt() + 1
            if (week < 1 || week > config.totalWeeks) continue

            val weekText = week.toString()
            for (course in courses) {
                if (course.dayOfWeek != date.dayOfWeek.value) continue
                if (!course.weekNumbers().contains(week)) continue

                val period = periods[course.startSection] ?: continue
                val start = runCatching { LocalTime.parse(period.startTime) }.getOrNull() ?: continue
                val triggerAt = date.atTime(start).minusMinutes(leadMinutes)
                if (!triggerAt.isAfter(LocalDateTime.now())) continue

                val rc = requestCode(epochDay, course.id)
                val pi = pendingIntent(context, rc, course.id, epochDay)
                val millis = TimeUtils.epochMillis(triggerAt)
                if (canExact) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
                } else {
                    alarmManager.setWindow(AlarmManager.RTC_WAKEUP, millis, INEXACT_WINDOW_MS, pi)
                }
                scheduled += rc.toString()
            }
        }
        Prefs.setScheduledCodes(context, scheduled)
    }

    fun cancelAll(context: Context, alarmManager: AlarmManager) {
        for (code in Prefs.scheduledCodes(context)) {
            val rc = code.toIntOrNull() ?: continue
            alarmManager.cancel(pendingIntent(context, rc, 0L, 0L))
        }
    }

    private fun requestCode(epochDay: Long, courseId: Long): Int =
        (epochDay * 1000 + (courseId % 1000)).toInt()

    private fun pendingIntent(context: Context, rc: Int, courseId: Long, epochDay: Long): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
            .putExtra(AlarmReceiver.EXTRA_COURSE_ID, courseId)
            .putExtra(AlarmReceiver.EXTRA_EPOCH_DAY, epochDay)
        return PendingIntent.getBroadcast(
            context, rc, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
