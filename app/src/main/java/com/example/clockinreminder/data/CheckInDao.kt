package com.example.clockinreminder.data

import androidx.room.*

@Dao
interface CheckInDao {
    // 唯一索引 + IGNORE：重复打卡同一天不会插入第二条
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(record: CheckInRecord): Long

    // 该任务已完成打卡的日期列表（去重、倒序）
    @Query("SELECT DISTINCT date FROM check_in_records WHERE taskId = :taskId ORDER BY date DESC")
    suspend fun getDatesForTask(taskId: Long): List<String>

    // 该任务累计打卡次数
    @Query("SELECT COUNT(*) FROM check_in_records WHERE taskId = :taskId")
    suspend fun getCountForTask(taskId: Long): Int
}
