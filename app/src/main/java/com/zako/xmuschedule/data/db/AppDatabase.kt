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

        /** 首次启动时写入默认节次时间表 */
        suspend fun ensureDefaultPeriods(context: Context) {
            val dao = get(context).periodTimeDao()
            if (dao.allNow().isEmpty()) {
                dao.upsertAll(defaultPeriodTimes())
            }
        }
    }
}
