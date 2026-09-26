package com.zako.xmuschedule

import android.app.Application
import com.zako.xmuschedule.reminder.Notifications
import com.zako.xmuschedule.reminder.ReminderWorker

class XmuScheduleApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannel(this)
        ReminderWorker.enqueuePeriodic(this)
    }
}
