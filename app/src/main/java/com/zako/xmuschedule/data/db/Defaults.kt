package com.zako.xmuschedule.data.db

/**
 * 默认节次时间表（12 节）。
 * 注意：这是占位默认值，请以厦门大学当学期实际作息时间为准，
 * 在 App「设置 → 节次时间」里逐节修改，修改后提醒会自动按新时间计算。
 */
fun defaultPeriodTimes(): List<PeriodTimeEntity> = listOf(
    PeriodTimeEntity(1, "08:00", "08:45"),
    PeriodTimeEntity(2, "08:55", "09:40"),
    PeriodTimeEntity(3, "10:00", "10:45"),
    PeriodTimeEntity(4, "10:55", "11:40"),
    PeriodTimeEntity(5, "11:50", "12:35"),
    PeriodTimeEntity(6, "14:30", "15:15"),
    PeriodTimeEntity(7, "15:25", "16:10"),
    PeriodTimeEntity(8, "16:20", "17:05"),
    PeriodTimeEntity(9, "17:15", "18:00"),
    PeriodTimeEntity(10, "19:00", "19:45"),
    PeriodTimeEntity(11, "19:55", "20:40"),
    PeriodTimeEntity(12, "20:50", "21:35"),
)
