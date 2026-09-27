package com.zako.xmuschedule.data.remote

import com.zako.xmuschedule.util.SectionsParser
import com.zako.xmuschedule.util.WeeksParser
import org.json.JSONArray
import org.json.JSONObject

/**
 * 金智教务 xskcb 课表返回解析。
 * 标准结构：{"isSuccess":true,"datas":{"xskcb":[{...},{...}]}}
 * 各校字段命名略有差异，这里对每类字段尝试多个候选键名，解析失败的行原样保留供反馈适配。
 */
object JwScheduleParser {

    data class ParsedCourse(
        val name: String,
        val teacher: String,
        val dayOfWeek: Int,
        val startSection: Int,
        val endSection: Int,
        val weeks: Set<Int>,
        val weeksText: String,
        val campus: String,
        val room: String,
        val jxbid: String? = null,
    )

    data class ScheduleParseResult(
        val courses: List<ParsedCourse>,
        val unrecognized: List<String>,
        val rawJson: String,
        val semesterCode: String?,
    )

    fun parse(body: String): ScheduleParseResult {
        val root = runCatching { JSONObject(body) }.getOrElse {
            return ScheduleParseResult(emptyList(), listOf(body.take(2000)), body, null)
        }
        val rows = extractRows(root)
        val courses = mutableListOf<ParsedCourse>()
        val unrecognized = mutableListOf<String>()
        if (rows != null) {
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i)
                if (row == null) {
                    unrecognized += rows.opt(i).toString()
                    continue
                }
                val parsed = parseRow(row)
                if (parsed == null) unrecognized += row.toString() else courses += parsed
            }
        } else if (root.length() > 0) {
            unrecognized += "返回中未找到 datas.xskcb 数组"
        }
        val semester = root.optJSONObject("datas")?.optString("xnxqdm")?.takeIf { it.isNotBlank() }
        val merged = mergeContiguous(courses)
        // 无教室（线上/占位）行若与同一天的实体课时间重叠且周次重叠，视为占位重复行去除
        val physicalRows = merged.filter { it.room.isNotBlank() }
        val finalCourses = merged.filter { course ->
            course.room.isNotBlank() || physicalRows.none { p ->
                p.dayOfWeek == course.dayOfWeek &&
                    course.startSection <= p.endSection && p.startSection <= course.endSection &&
                    course.weeks.intersect(p.weeks).isNotEmpty()
            }
        }
        return ScheduleParseResult(finalCourses, unrecognized, body, semester)
    }

    /**
     * 同一门课相邻节次常被拆成多行（如 7-7 与 8-8），合并为连续跨度；
     * 另外学校会把同一节课按平行分班拆成多行（同一时间、不同教室），时间重叠的行也去重合并，
     * 否则顺序布局会把第二行挤到后面的节次显示（表现为凭空多出的块）。
     */
    private fun mergeContiguous(courses: List<ParsedCourse>): List<ParsedCourse> {
        val merged = mutableListOf<ParsedCourse>()
        val groups = courses.groupBy {
            listOf(
                it.jxbid?.takeIf { j -> j.isNotBlank() } ?: (it.name + "|" + it.teacher),
                it.dayOfWeek.toString(),
            )
        }
        for (group in groups.values) {
            val sorted = group.sortedBy { it.startSection }
            var acc = sorted.first()
            for (i in 1 until sorted.size) {
                val cur = sorted[i]
                val overlaps = cur.startSection <= acc.endSection
                val contiguousSameRoom = cur.startSection == acc.endSection + 1 && acc.room == cur.room
                // 时间重叠时的合并规则：
                //  两个都有教室（平行分班，不同教室）→ 合并一块，教室并列；
                //  一个有教室一个无教室（线上/线下混合）→ 不合并，各保留自己的周次；
                //  两个都无教室（重复的线上课）→ 合并去重。
                // 相邻（前一节结束+1）且同教室 → 跨度合并；相邻但不同教室 / 不相交时间 → 分开。
                val accOnline = acc.room.isBlank()
                val curOnline = cur.room.isBlank()
                val shouldMerge = if (overlaps) {
                    (accOnline && curOnline) || (!accOnline && !curOnline)
                } else {
                    contiguousSameRoom
                }
                if (shouldMerge) {
                    acc = acc.copy(
                        endSection = maxOf(acc.endSection, cur.endSection),
                        weeks = acc.weeks + cur.weeks,
                        room = if (accOnline && curOnline) "" else mergeRooms(listOf(acc.room, cur.room)),
                    )
                } else {
                    merged.add(acc)
                    acc = cur
                }
            }
            merged.add(acc)
        }
        return merged.sortedWith(compareBy({ it.dayOfWeek }, { it.startSection }))
    }

    private fun extractRows(root: JSONObject): JSONArray? {
        val datas = root.optJSONObject("datas")
        val node = datas?.opt("xskcb")
            ?: root.opt("xskcb")
            ?: datas?.opt("xskcbList")
        return when (node) {
            is JSONArray -> node
            is JSONObject -> node.optJSONArray("list") ?: node.optJSONArray("rows")
            else -> null
        }
    }

    private fun parseRow(row: JSONObject): ParsedCourse? {
        val name = firstNonBlank(row, "KCM", "KCZWMC", "KCMC") ?: return null
        val day = firstIntInRange(row, 1..7, "SKXQ", "XQJ", "XQ", "WEEKDAY", "XQJDM") ?: return null
        val sections = parseSections(row) ?: return null
        val weeksBitmap = firstNonBlank(row, "SKZC")
        val weeksDisplay = firstNonBlank(row, "ZCMC", "ZC", "ZCBH", "ZCZ", "QSZC", "WEEKS")
        val weeks = WeeksParser.parse(weeksBitmap ?: weeksDisplay)
        if (weeks.isEmpty()) return null
        val teacher = firstNonBlank(row, "SKJS", "JSMC", "JSXM", "TEACHER") ?: ""
        var campus = firstNonBlank(row, "XQMC", "XXXQDM_DISPLAY", "CAMPUS", "XQ") ?: ""
        var room = listOfNotNull(
            firstNonBlank(row, "JASMC"),
            firstNonBlank(row, "JXL"),
        ).filter { it.isNotBlank() }.joinToString(" ")
        // 教室缺失时从 YPSJDD 兜底提取（兼容 [7-8节] 括号格式）；
        // 提取结果必须含数字且不含 "周" 字，避免把 "1-2周" 这类周次文本当成教室
        if (room.isBlank()) {
            val ypsjdd = firstNonBlank(row, "YPSJDD")
            if (ypsjdd != null) {
                val firstMeeting = ypsjdd.split(',').firstOrNull().orEmpty()
                val tail = firstMeeting.substringAfterLast("节").trimStart(']', '）', ')', ' ', '　')
                if (tail.contains(Regex("\\d")) && !tail.contains("周")) {
                    room = tail
                    if (campus.isBlank()) campus = firstMeeting.substringAfter("星期", "").trim()
                }
            }
        }
        return ParsedCourse(
            name = name.trim(),
            teacher = teacher.trim(),
            dayOfWeek = day,
            startSection = sections.first,
            endSection = sections.second,
            weeks = weeks,
            weeksText = (weeksDisplay ?: weeksBitmap ?: "").trim(),
            campus = campus.trim(),
            room = room.trim(),
            jxbid = firstNonBlank(row, "JXBID"),
        )
    }

    /** 节次：优先合并字段（JC），否则用分离的起止字段（厦大新版 KSJC/JSJC） */
    private fun parseSections(row: JSONObject): Pair<Int, Int>? {
        SectionsParser.parse(firstNonBlank(row, "JC", "JCDM", "JIECI"))?.let { return it }
        val start = firstIntInRange(row, 1..20, "KSJC", "KSJC_DM") ?: return null
        val end = firstIntInRange(row, 1..20, "JSJC", "JSJC_DM") ?: return null
        if (end < start) return null
        return start to end
    }

    private fun firstNonBlank(row: JSONObject, vararg keys: String): String? {
        for (key in keys) {
            val v = row.optString(key, "").trim()
            if (v.isNotBlank() && v != "null") return v
        }
        return null
    }

    private fun firstIntInRange(row: JSONObject, range: IntRange, vararg keys: String): Int? {
        for (key in keys) {
            if (!row.has(key)) continue
            val v = row.optString(key, "").trim().toIntOrNull()
            if (v != null && v in range) return v
        }
        return null
    }

    /** 多教室合并展示：同楼不同室压缩为 "文宣楼（4号楼）B404/B405"，否则用顿号并列 */
    private fun mergeRooms(rooms: List<String>): String {
        val distinct = rooms.filter { it.isNotBlank() }.distinct()
        if (distinct.size <= 1) return distinct.firstOrNull().orEmpty()
        var prefix = distinct.first()
        for (other in distinct.drop(1)) {
            while (prefix.isNotEmpty() && !other.startsWith(prefix)) {
                prefix = prefix.dropLast(1)
            }
        }
        if (prefix.length >= 3) {
            val rests = distinct.map { it.removePrefix(prefix) }
            val simple = rests.all { r -> r.length <= 6 && !r.contains('（') && !r.contains('(') }
            if (simple) return prefix + rests.joinToString("/")
        }
        return distinct.joinToString("、")
    }
}
