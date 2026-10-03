package com.example.clockinreminder.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 打卡记录实体。
 * taskId + date 设为唯一索引，保证“同一任务同一天”只记录一次（重复点击不会刷数据）。
 */
@Entity(
    tableName = "check_in_records",
    indices = [Index(value = ["taskId", "date"], unique = true)]
)
data class CheckInRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val date: String,  // 形如 "2026-10-01"，按天去重
    val completedAt: Long = System.currentTimeMillis()
)
