package com.zako.xmuschedule.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.zako.xmuschedule.data.Prefs
import com.zako.xmuschedule.data.db.AppDatabase
import com.zako.xmuschedule.util.TimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** 课前提醒闹钟触发：复核周次与课程仍有效后发通知 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val courseId = intent.getLongExtra(EXTRA_COURSE_ID, -1L)
        val epochDay = intent.getLongExtra(EXTRA_EPOCH_DAY, -1L)
        if (courseId <= 0 || epochDay <= 0) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.get(context)
                val course = db.courseDao().byId(courseId) ?: return@launch
                val config = db.termConfigDao().now() ?: return@launch
                val firstMonday = config.firstWeekMondayEpochDay ?: return@launch

                val week = TimeUtils.weekNumber(epochDay, firstMonday) ?: return@launch
                if (week > config.totalWeeks) return@launch
                if (!course.weekNumbers().contains(week)) return@launch

                Notifications.showClassReminder(context, course, Prefs.leadMinutes(context))
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_COURSE_ID = "courseId"
        const val EXTRA_EPOCH_DAY = "epochDay"
    }
}
