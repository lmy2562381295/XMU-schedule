package com.zako.xmuschedule.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 一条课程排课记录（同一门课可能有多条：不同周次/节次/教室） */
@Entity(tableName = "courses")
data class CourseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val teacher: String = "",
    /** 1=周一 ... 7=周日 */
    val dayOfWeek: Int,
    val startSection: Int,
    val endSection: Int,
    /** 规范化后的周次集合，逗号分隔，如 "1,3,5" */
    val weeksSet: String,
    /** 原始周次文本，如 "1-16周(单)"，仅用于展示 */
    val weeksText: String = "",
    val campus: String = "",
    val room: String = "",
    /** import=教务导入 / demo=演示 / manual=手动 */
    val source: String = "import",
)

fun CourseEntity.weekNumbers(): Set<Int> =
    weeksSet.split(',').mapNotNull { it.trim().toIntOrNull() }.toSet()

/** 学期配置（单例，id 恒为 1） */
@Entity(tableName = "term_config")
data class TermConfigEntity(
    @PrimaryKey val id: Int = 1,
    /** 第一周周一的 epochDay（LocalDate.toEpochDay()），null=未设置 */
    val firstWeekMondayEpochDay: Long? = null,
    val totalWeeks: Int = 20,
    val semesterCode: String = "",
)

/** 每节课的上下课时间（可编辑） */
@Entity(tableName = "period_times")
data class PeriodTimeEntity(
    @PrimaryKey val section: Int,
    /** "08:00" 24 小时制 */
    val startTime: String,
    val endTime: String,
)
