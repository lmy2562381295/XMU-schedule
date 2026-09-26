package com.zako.xmuschedule.ui.settings

import android.app.AlarmManager
import android.app.Application
import android.content.Context
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zako.xmuschedule.data.Prefs
import com.zako.xmuschedule.data.ScheduleRepository
import com.zako.xmuschedule.data.db.AppDatabase
import com.zako.xmuschedule.data.db.PeriodTimeEntity
import com.zako.xmuschedule.data.db.TermConfigEntity
import com.zako.xmuschedule.data.db.defaultPeriodTimes
import com.zako.xmuschedule.reminder.Notifications
import com.zako.xmuschedule.reminder.ReminderScheduler
import com.zako.xmuschedule.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val firstWeekMondayEpochDay: Long? = null,
    val totalWeeks: Int = 20,
    val semesterCode: String = "",
    val periods: List<PeriodTimeEntity> = emptyList(),
)

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val db = AppDatabase.get(app)
    private val repo = ScheduleRepository(app)

    val state: StateFlow<SettingsUiState> = combine(
        db.termConfigDao().observe(),
        db.periodTimeDao().observeAll(),
    ) { config, periods ->
        SettingsUiState(
            firstWeekMondayEpochDay = config?.firstWeekMondayEpochDay,
            totalWeeks = config?.totalWeeks ?: 20,
            semesterCode = config?.semesterCode.orEmpty(),
            periods = periods,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun leadMinutes(): Int = Prefs.leadMinutes(getApplication())

    fun setFirstWeekMonday(epochDay: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val config = db.termConfigDao().now() ?: TermConfigEntity()
                db.termConfigDao().upsert(config.copy(firstWeekMondayEpochDay = epochDay))
                ReminderScheduler.scheduleWindow(getApplication())
                AppLog.event(getApplication(), "settings", "第一周设为 epochDay=$epochDay")
            } catch (t: Throwable) {
                AppLog.error(getApplication(), "settings", t)
            }
        }
    }

    fun changeTotalWeeks(delta: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val config = db.termConfigDao().now() ?: TermConfigEntity()
                val next = (config.totalWeeks + delta).coerceIn(4, 30)
                db.termConfigDao().upsert(config.copy(totalWeeks = next))
                ReminderScheduler.scheduleWindow(getApplication())
            } catch (t: Throwable) {
                AppLog.error(getApplication(), "settings", t)
            }
        }
    }

    fun setLeadMinutes(minutes: Int) {
        Prefs.setLeadMinutes(getApplication(), minutes)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                ReminderScheduler.scheduleWindow(getApplication())
            } catch (t: Throwable) {
                AppLog.error(getApplication(), "settings", t)
            }
        }
    }

    fun updatePeriod(section: Int, start: String, end: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                db.periodTimeDao().upsertAll(listOf(PeriodTimeEntity(section, start, end)))
                ReminderScheduler.scheduleWindow(getApplication())
            } catch (t: Throwable) {
                AppLog.error(getApplication(), "settings", t)
            }
        }
    }

    fun resetPeriods() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                db.periodTimeDao().clear()
                db.periodTimeDao().upsertAll(defaultPeriodTimes())
                ReminderScheduler.scheduleWindow(getApplication())
            } catch (t: Throwable) {
                AppLog.error(getApplication(), "settings", t)
            }
        }
    }

    fun loadDemo() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                AppDatabase.ensureDefaultPeriods(getApplication())
                repo.importDemo()
                ReminderScheduler.scheduleWindow(getApplication())
                AppLog.event(getApplication(), "settings", "载入演示课表完成")
            } catch (t: Throwable) {
                AppLog.error(getApplication(), "settings", t)
            }
        }
    }

    fun clearAll() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repo.clearAll()
                ReminderScheduler.scheduleWindow(getApplication())
            } catch (t: Throwable) {
                AppLog.error(getApplication(), "settings", t)
            }
        }
    }

    fun testNotification() {
        Notifications.showTest(getApplication())
    }

    fun canScheduleExact(): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        val am = getApplication<Application>().getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return am.canScheduleExactAlarms()
    }
}
