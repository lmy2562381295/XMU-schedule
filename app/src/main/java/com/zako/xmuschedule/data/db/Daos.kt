package com.zako.xmuschedule.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CourseDao {
    @Query("SELECT * FROM courses ORDER BY dayOfWeek, startSection")
    fun observeAll(): Flow<List<CourseEntity>>

    @Query("SELECT * FROM courses ORDER BY dayOfWeek, startSection")
    suspend fun allNow(): List<CourseEntity>

    @Query("SELECT * FROM courses WHERE id = :id")
    suspend fun byId(id: Long): CourseEntity?

    @Insert
    suspend fun insertAll(courses: List<CourseEntity>)

    @Insert
    suspend fun insert(course: CourseEntity): Long

    @Update
    suspend fun update(course: CourseEntity)

    @Query("DELETE FROM courses WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM courses")
    suspend fun clear()
}

@Dao
interface TermConfigDao {
    @Query("SELECT * FROM term_config WHERE id = 1")
    fun observe(): Flow<TermConfigEntity?>

    @Query("SELECT * FROM term_config WHERE id = 1")
    suspend fun now(): TermConfigEntity?

    @Upsert
    suspend fun upsert(config: TermConfigEntity)
}

@Dao
interface PeriodTimeDao {
    @Query("SELECT * FROM period_times ORDER BY section")
    fun observeAll(): Flow<List<PeriodTimeEntity>>

    @Query("SELECT * FROM period_times ORDER BY section")
    suspend fun allNow(): List<PeriodTimeEntity>

    @Upsert
    suspend fun upsertAll(items: List<PeriodTimeEntity>)

    @Query("DELETE FROM period_times")
    suspend fun clear()
}
