package com.example.clockinreminder.data

import androidx.lifecycle.LiveData
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

    // ================= 联动点① 打卡状态回流主页 =================
    // 某一天「已打卡」的任务 id 集合。返回 LiveData，主页观察它 → 打卡后列表自动刷新。
    @Query("SELECT taskId FROM check_in_records WHERE date = :date")
    fun getTaskIdsOn(date: String): LiveData<List<Long>>

    // ================= 联动点② 已打卡免打扰 =================
    // 某任务某天是否已打卡（返回 0 = 没打过）
    @Query("SELECT COUNT(*) FROM check_in_records WHERE taskId = :taskId AND date = :date")
    suspend fun countOn(taskId: Long, date: String): Int

    // 撤销某任务某天的打卡（主页「撤销」按钮用）
    @Query("DELETE FROM check_in_records WHERE taskId = :taskId AND date = :date")
    suspend fun deleteOn(taskId: Long, date: String): Int

    // ================= 联动点⑥ 删除任务的联动清理 =================
    // 删除任务时连带清掉它的全部打卡记录，不留孤儿数据
    @Query("DELETE FROM check_in_records WHERE taskId = :taskId")
    suspend fun deleteForTask(taskId: Long): Int

    // ================= 联动点⑧ 批量一键打卡 =================
    // 一次事务写入多条今日打卡记录（重复的靠唯一索引 IGNORE 自动跳过）
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(records: List<CheckInRecord>): List<Long>
}
