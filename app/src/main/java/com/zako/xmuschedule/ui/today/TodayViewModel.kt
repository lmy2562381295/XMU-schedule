package com.zako.xmuschedule.ui.today

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zako.xmuschedule.data.ScheduleRepository
import com.zako.xmuschedule.data.db.AppDatabase
import com.zako.xmuschedule.data.db.CourseEntity
import com.zako.xmuschedule.data.db.TermConfigEntity
import com.zako.xmuschedule.data.db.weekNumbers
import com.zako.xmuschedule.reminder.ReminderScheduler
import com.zako.xmuschedule.util.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

data class TodayItem(
    val course: CourseEntity,
    val startText: String,
    val endText: String,
    val isNow: Boolean,
    val isPast: Boolean,
)

data class TodayUiState(
    val loading: Boolean = true,
    val needsConfig: Boolean = false,
    val isEmpty: Boolean = true,
    val weekNumber: Int? = null,
    val dateText: String = "",
    val items: List<TodayItem> = emptyList(),
)

class TodayViewModel(app: Application) : AndroidViewModel(app) {

    private val db = AppDatabase.get(app)
    private val repo = ScheduleRepository(app)

    val state: StateFlow<TodayUiState> = combine(
        db.courseDao().observeAll(),
        db.termConfigDao().observe(),
        db.periodTimeDao().observeAll(),
    ) { courses, config, periods ->
        buildState(courses, config, periods)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState())

    private fun buildState(
        courses: List<CourseEntity>,
        config: TermConfigEntity?,
        periods: List<com.zako.xmuschedule.data.db.PeriodTimeEntity>,
    ): TodayUiState {
        val today = LocalDate.now()
        val dateText = today.format(DateTimeFormatter.ofPattern("M月d日 EEEE"))
        if (config == null || config.firstWeekMondayEpochDay == null) {
            return TodayUiState(loading = false, needsConfig = true, isEmpty = courses.isEmpty(), dateText = dateText)
        }
        val week = TimeUtils.weekNumber(today.toEpochDay(), config.firstWeekMondayEpochDay)
        val times = periods.associateBy { it.section }
        val now = LocalTime.now()
        val dow = today.dayOfWeek.value
        val items = if (week == null || week > config.totalWeeks) emptyList() else courses
            .filter { it.dayOfWeek == dow && it.weekNumbers().contains(week) }
            .sortedBy { it.startSection }
            .map { c ->
                val start = times[c.startSection]?.let { runCatching { LocalTime.parse(it.startTime) }.getOrNull() }
                val end = times[c.endSection]?.let { runCatching { LocalTime.parse(it.endTime) }.getOrNull() }
                TodayItem(
                    course = c,
                    startText = start?.toString() ?: "第${c.startSection}节",
                    endText = end?.toString() ?: "",
                    isNow = start != null && end != null && now >= start && now <= end,
                    isPast = end != null && now > end,
                )
            }
        return TodayUiState(
            loading = false,
            needsConfig = false,
            isEmpty = courses.isEmpty(),
            weekNumber = week,
            dateText = dateText,
            items = items,
        )
    }

    fun loadDemo() {
        viewModelScope.launch(Dispatchers.IO) {
            AppDatabase.ensureDefaultPeriods(getApplication())
            repo.importDemo()
            ReminderScheduler.scheduleWindow(getApplication())
        }
    }
}
