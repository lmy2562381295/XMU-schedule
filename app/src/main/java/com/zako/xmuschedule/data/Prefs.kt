package com.zako.xmuschedule.data

import android.content.Context

/** 轻量设置存取（提醒提前量、已排闹钟记录等） */
object Prefs {
    private const val FILE = "xmu_schedule_settings"
    private const val KEY_LEAD_MINUTES = "lead_minutes"
    private const val KEY_SCHEDULED_CODES = "scheduled_codes"
    private const val KEY_PERIODS_VERSION = "periods_version"

    fun leadMinutes(context: Context): Int =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getInt(KEY_LEAD_MINUTES, 10)

    fun setLeadMinutes(context: Context, value: Int) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putInt(KEY_LEAD_MINUTES, value).apply()
    }

    fun scheduledCodes(context: Context): Set<String> =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getStringSet(KEY_SCHEDULED_CODES, emptySet()) ?: emptySet()

    fun setScheduledCodes(context: Context, codes: Set<String>) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putStringSet(KEY_SCHEDULED_CODES, codes).apply()
    }

    fun periodsVersion(context: Context): Int =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getInt(KEY_PERIODS_VERSION, 0)

    fun setPeriodsVersion(context: Context, version: Int) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putInt(KEY_PERIODS_VERSION, version).apply()
    }
}
