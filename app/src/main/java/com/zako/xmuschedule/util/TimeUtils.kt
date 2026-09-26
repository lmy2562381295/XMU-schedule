package com.zako.xmuschedule.util

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

object TimeUtils {

    /** 某日期处于第几周（第一周周一 epochDay 为基准）；不在学期范围内返回 null */
    fun weekNumber(epochDay: Long, firstWeekMondayEpochDay: Long): Int? {
        if (epochDay < firstWeekMondayEpochDay) return null
        val week = ((epochDay - firstWeekMondayEpochDay) / 7).toInt() + 1
        return if (week >= 1) week else null
    }

    fun weekNumber(date: LocalDate, firstWeekMonday: LocalDate): Int? =
        weekNumber(date.toEpochDay(), firstWeekMonday.toEpochDay())

    /** 包含指定日期的那一周的周一 */
    fun mondayOf(date: LocalDate): LocalDate = date.with(DayOfWeek.MONDAY)

    fun epochMillis(localDateTime: LocalDateTime): Long =
        localDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** 根据当前日期估算厦大学期代码，如 2026-2027-1（仅作预填，可手动修改） */
    fun guessSemesterCode(today: LocalDate = LocalDate.now()): String {
        val y = today.year
        return when (today.monthValue) {
            1 -> "${y - 1}-$y-1"
            in 2..7 -> "${y - 1}-$y-2"
            else -> "$y-${y + 1}-1"
        }
    }
}
