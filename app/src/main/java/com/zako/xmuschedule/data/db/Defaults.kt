package com.zako.xmuschedule.data.db

/** 默认节次时间表版本：学校作息调整时递增，App 启动会自动覆盖本地节次表 */
const val DEFAULT_PERIODS_VERSION = 3

/**
 * 默认节次时间表（11 节，依据厦门大学本科生作息时间表）。
 * 如与当学期实际作息有出入，可在「设置 → 节次时间」逐节修改。
 */
fun defaultPeriodTimes(): List<PeriodTimeEntity> = listOf(
    PeriodTimeEntity(1, "08:00", "08:45"),
    PeriodTimeEntity(2, "08:55", "09:40"),
    PeriodTimeEntity(3, "10:10", "10:55"),
    PeriodTimeEntity(4, "11:05", "11:50"),
    PeriodTimeEntity(5, "14:30", "15:15"),
    PeriodTimeEntity(6, "15:25", "16:10"),
    PeriodTimeEntity(7, "16:20", "17:05"),
    PeriodTimeEntity(8, "17:15", "18:00"),
    PeriodTimeEntity(9, "19:10", "19:55"),
    PeriodTimeEntity(10, "20:05", "20:50"),
    PeriodTimeEntity(11, "21:00", "21:45"),
)
