package com.zako.xmuschedule.data

import android.content.Context
import com.zako.xmuschedule.data.db.AppDatabase
import com.zako.xmuschedule.data.db.CourseEntity
import com.zako.xmuschedule.data.db.TermConfigEntity
import com.zako.xmuschedule.data.remote.JwClient
import com.zako.xmuschedule.data.remote.JwScheduleParser
import com.zako.xmuschedule.util.AppLog
import com.zako.xmuschedule.util.TimeUtils
import com.zako.xmuschedule.util.WeeksParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

sealed interface ImportResult {
    data class Success(val count: Int, val unrecognized: Int, val semester: String) : ImportResult
    data class Failure(val message: String) : ImportResult
}

class ScheduleRepository(private val context: Context) {

    private val db = AppDatabase.get(context)

    /** 教务系统一键导入 */
    suspend fun importFromJw(client: JwClient, semesterCodeInput: String?): ImportResult =
        withContext(Dispatchers.IO) {
            val code = semesterCodeInput?.takeIf { it.isNotBlank() }
                ?: client.fetchSemesterCode(TimeUtils.guessSemesterCode().substringBefore('-'))
                ?: TimeUtils.guessSemesterCode()
            AppLog.event(context, "repo", "importFromJw: 学期代码=$code (输入=${semesterCodeInput ?: "自动"})")

            val raw = client.fetchScheduleRaw(code)
                ?: return@withContext ImportResult.Failure(
                    "未能获取课表数据：会话可能已过期，请重新登录；若持续失败请检查学期代码（当前：$code）"
                )
            AppLog.event(context, "repo", "原始返回长度=${raw.length}")

            val parsed = JwScheduleParser.parse(raw)
            AppLog.event(context, "repo", "解析结果: 课程=${parsed.courses.size} 未识别=${parsed.unrecognized.size}")
            saveRawDump(raw, code)

            if (parsed.courses.isEmpty()) {
                return@withContext ImportResult.Failure(
                    "接口返回了 ${parsed.unrecognized.size} 条数据但未能解析出任何课程（字段命名可能与预期不同）。原始返回已保存，可用于适配。"
                )
            }
            replaceAll(parsed, code)
            AppLog.event(context, "repo", "已写入数据库: ${parsed.courses.size} 条")
            ImportResult.Success(parsed.courses.size, parsed.unrecognized.size, code)
        }

    /** 页面内取数结果写库（WebView fetch 拿到的原始文本） */
    suspend fun applyWebImport(raw: String, semesterCode: String): ImportResult =
        withContext(Dispatchers.IO) {
            val parsed = JwScheduleParser.parse(raw)
            AppLog.event(
                context, "repo",
                "页面内导入: 学期=$semesterCode 课程=${parsed.courses.size} 未识别=${parsed.unrecognized.size}",
            )
            // 无教室的行完整打进日志（教室可能藏在别的字段名里）
            val blankRoomRows: List<String> = runCatching {
                val datas = JSONObject(raw).optJSONObject("datas") ?: return@runCatching emptyList()
                val node = datas.opt("xskcb") ?: return@runCatching emptyList()
                val rows = when (node) {
                    is org.json.JSONArray -> node
                    is JSONObject -> node.optJSONArray("rows")
                    else -> null
                } ?: return@runCatching emptyList()
                (0 until rows.length()).mapNotNull { rows.optJSONObject(it) }
                    .filter { r ->
                        val room = r.optString("JASMC", "").ifBlank { r.optString("JSMC", "") }
                        room.isBlank()
                    }
                    .take(2)
                    .map { it.toString() }
            }.getOrDefault(emptyList())
            blankRoomRows.forEachIndexed { i, rowJson ->
                AppLog.event(context, "repo", "无教室原始行${i + 1}: $rowJson")
            }
            // 导入明细（便于远程核对合并与字段解析结果）
            val dayNames = listOf("一", "二", "三", "四", "五", "六", "日")
            val detail = parsed.courses.joinToString("；") {
                "${it.name} 周${dayNames[it.dayOfWeek - 1]} ${it.startSection}-${it.endSection}节 " +
                    "周${it.weeks.min()}-${it.weeks.max()} ${it.room.ifBlank { "无教室" }}"
            }
            AppLog.event(context, "repo", "导入明细: ${detail.take(1500)}")
            saveRawDump(raw, semesterCode)
            if (parsed.courses.isEmpty()) {
                ImportResult.Failure(
                    "接口返回了 ${parsed.unrecognized.size} 条数据但未能解析出任何课程（字段命名可能与预期不同）。原始返回已保存，可用于适配。"
                )
            } else {
                replaceAll(parsed, semesterCode)
                ImportResult.Success(parsed.courses.size, parsed.unrecognized.size, semesterCode)
            }
        }

    /** 解析结果写入数据库（整表替换），学期代码同步更新 */
    suspend fun replaceAll(parsed: JwScheduleParser.ScheduleParseResult, semesterCode: String) {
        db.courseDao().clear()
        db.courseDao().insertAll(
            parsed.courses.map {
                CourseEntity(
                    name = it.name,
                    teacher = it.teacher,
                    dayOfWeek = it.dayOfWeek,
                    startSection = it.startSection,
                    endSection = it.endSection,
                    weeksSet = WeeksParser.serialize(it.weeks),
                    weeksText = it.weeksText,
                    campus = it.campus,
                    room = it.room,
                    source = "import",
                )
            }
        )
        val config = db.termConfigDao().now() ?: TermConfigEntity()
        db.termConfigDao().upsert(config.copy(semesterCode = semesterCode))
    }

    /** 原始返回存档，便于字段适配排查 */
    private suspend fun saveRawDump(raw: String, code: String) = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.filesDir, "dumps").apply { mkdirs() }
            File(dir, "schedule_raw_$code.json").writeText(raw, Charsets.UTF_8)
        }
    }

    /** 演示课表：读取 assets 里的样例返回并走同一条解析链路 */
    suspend fun importDemo(): ImportResult = withContext(Dispatchers.IO) {
        val raw = context.assets.open("demo_schedule.json").bufferedReader().use { it.readText() }
        val parsed = JwScheduleParser.parse(raw)
        if (parsed.courses.isEmpty()) {
            return@withContext ImportResult.Failure("演示数据解析失败，请反馈。")
        }
        replaceAll(parsed, "演示学期")
        // 演示模式把第一周设为本周周一，方便立即看到效果和收到提醒
        val config = db.termConfigDao().now() ?: TermConfigEntity()
        db.termConfigDao().upsert(config.copy(firstWeekMondayEpochDay = TimeUtils.mondayOf(LocalDate.now()).toEpochDay()))
        ImportResult.Success(parsed.courses.size, parsed.unrecognized.size, "演示学期")
    }

    /** 手动新增/修改课程 */
    suspend fun saveCourse(course: CourseEntity) {
        if (course.id == 0L) db.courseDao().insert(course.copy(source = "manual"))
        else db.courseDao().update(course)
    }

    suspend fun deleteCourse(id: Long) {
        db.courseDao().deleteById(id)
    }

    suspend fun clearAll() {
        db.courseDao().clear()
        db.termConfigDao().upsert(TermConfigEntity())
    }
}
