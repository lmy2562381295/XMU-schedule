package com.zako.xmuschedule.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.zako.xmuschedule.MainActivity
import com.zako.xmuschedule.R
import com.zako.xmuschedule.data.db.CourseEntity

object Notifications {

    const val CHANNEL_ID = "class_reminder"

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = context.getString(R.string.channel_desc) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun contentIntent(context: Context, courseId: Long): PendingIntent =
        PendingIntent.getActivity(
            context,
            (courseId % 100000).toInt(),
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    fun showClassReminder(context: Context, course: CourseEntity, leadMinutes: Int) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val body = buildString {
            append(course.room.ifBlank { "线上课" })
            append(" · 第${course.startSection}-${course.endSection}节")
            if (course.teacher.isNotBlank()) append(" · ").append(course.teacher)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notif_title, leadMinutes, course.name))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(contentIntent(context, course.id))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .build()
        NotificationManagerCompat.from(context).notify(course.id.toInt(), notification)
    }

    fun showTest(context: Context) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notif_test_title))
            .setContentText(context.getString(R.string.notif_test_body))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(999999, notification)
    }
}
