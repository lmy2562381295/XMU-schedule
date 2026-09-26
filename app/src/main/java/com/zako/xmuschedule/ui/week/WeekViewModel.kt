package com.zako.xmuschedule.ui.week

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zako.xmuschedule.data.ScheduleRepository
import com.zako.xmuschedule.data.db.AppDatabase
import com.zako.xmuschedule.data.db.CourseEntity
import com.zako.xmuschedule.data.db.PeriodTimeEntity
import com.zako.xmuschedule.data.db.TermConfigEntity
import com.zako.xmuschedule.data.db.weekNumbers
import com.zako.xmuschedule.reminder.ReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class WeekUiState(
    val needsConfig: Boolean = false,
    val weekNumber: Int? = null,
    val totalWeeks: Int = 20,
    val isCurrentWeek: Boolean = true,
    val isEmpty: Boolean = true,
    val days: List<LocalDate> = emptyList(),
    val sections: List<Int> = emptyList(),
    val times: Map<Int, PeriodTimeEntity> = emptyMap(),
    /** (星期, 节次) → 在该节开课的课程 */
    val startCells: Map<Pair<Int, Int>, CourseEntity> = emptyMap(),
    /** 被课程占用的所有格子（含延续节次） */
    val occupied: Set<Pair<Int, Int>> = emptySet(),
)

class WeekViewModel(app: Application) : AndroidViewModel(app) {

    private val db = AppDatabase.get(app)
    private val repo = ScheduleRepository(app)
    private val weekOffset = MutableStateFlow(0)

    val state: StateFlow<WeekUiState> = combine(
        db.courseDao().observeAll(),
        db.termConfigDao().observe(),
        db.periodTimeDao().observeAll(),
        weekOffset,
    ) { courses, config, periods, offset ->
        buildState(courses, config, periods, offset)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeekUiState())

    private fun buildState(
        courses: List<CourseEntity>,
        config: TermConfigEntity?,
        periods: List<PeriodTimeEntity>,
        offset: Int,
    ): WeekUiState {
        val today = LocalDate.now()
        if (config == null || config.firstWeekMondayEpochDay == null) {
            return WeekUiState(needsConfig = true, isEmpty = courses.isEmpty())
        }
        val baseWeek = com.zako.xmuschedule.util.TimeUtils.weekNumber(today.toEpochDay(), config.firstWeekMondayEpochDay) ?: 1
        val weekNo = baseWeek + offset
        val weekMonday = LocalDate.ofEpochDay(config.firstWeekMondayEpochDay + (weekNo - 1) * 7L)
        val days = (0..6).map { weekMonday.plusDays(it.toLong()) }
        val maxSection = periods.maxOfOrNull { it.section } ?: 12
        val sections = (1..maxSection.coerceAtLeast(1)).toList()

        val startCells = mutableMapOf<Pair<Int, Int>, CourseEntity>()
        val occupied = mutableSetOf<Pair<Int, Int>>()
        for (course in courses) {
            // 只显示所选周次实际上课的安排
            if (!course.weekNumbers().contains(weekNo)) continue
            for (s in course.startSection..course.endSection) {
                val key = course.dayOfWeek to s
                if (s == course.startSection) startCells[key] = course
                occupied += key
            }
        }
        return WeekUiState(
            needsConfig = false,
            weekNumber = weekNo,
            totalWeeks = config.totalWeeks,
            isCurrentWeek = offset == 0,
            isEmpty = courses.isEmpty(),
            days = days,
            sections = sections,
            times = periods.associateBy { it.section },
            startCells = startCells,
            occupied = occupied,
        )
    }

    fun prevWeek() { weekOffset.value = (weekOffset.value - 1).coerceAtLeast(-25) }
    fun nextWeek() { weekOffset.value = (weekOffset.value + 1).coerceAtMost(25) }
    fun backToCurrent() { weekOffset.value = 0 }

    fun saveCourse(course: CourseEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.saveCourse(course)
            ReminderScheduler.scheduleWindow(getApplication())
        }
    }

    fun deleteCourse(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.deleteCourse(id)
            ReminderScheduler.scheduleWindow(getApplication())
        }
    }
}
