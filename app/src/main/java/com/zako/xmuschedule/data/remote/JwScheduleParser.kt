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
        return ScheduleParseResult(courses, unrecognized, body, semester)
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
        val campus = firstNonBlank(row, "XQMC", "XXXQDM_DISPLAY", "CAMPUS", "XQ") ?: ""
        val room = listOfNotNull(
            firstNonBlank(row, "JASMC"),
            firstNonBlank(row, "JXL"),
        ).filter { it.isNotBlank() }.joinToString(" ").ifBlank {
            firstNonBlank(row, "JSMC", "ROOM") ?: ""
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
}
