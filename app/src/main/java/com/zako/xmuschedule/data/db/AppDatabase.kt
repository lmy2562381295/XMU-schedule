package com.zako.xmuschedule.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [CourseEntity::class, TermConfigEntity::class, PeriodTimeEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun courseDao(): CourseDao
    abstract fun termConfigDao(): TermConfigDao
    abstract fun periodTimeDao(): PeriodTimeDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "xmu_schedule.db",
                ).build().also { instance = it }
            }

        /** 启动时保证节次表存在；默认表版本升级时自动覆盖（用户可在设置里再改） */
        suspend fun ensureDefaultPeriods(context: Context) {
            val dao = get(context).periodTimeDao()
            val storedVersion = com.zako.xmuschedule.data.Prefs.periodsVersion(context)
            if (dao.allNow().isEmpty() || storedVersion < DEFAULT_PERIODS_VERSION) {
                dao.clear()
                dao.upsertAll(defaultPeriodTimes())
                com.zako.xmuschedule.data.Prefs.setPeriodsVersion(context, DEFAULT_PERIODS_VERSION)
            }
        }
    }
}
