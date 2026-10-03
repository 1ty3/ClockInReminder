package com.example.clockinreminder.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 打卡任务实体。
 * 一个任务 = 一个打卡事项（如“上班”“运动”）+ 一个提醒时间。
 */
@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,            // 任务名称
    val hour: Int,               // 提醒小时 0-23
    val minute: Int,             // 提醒分钟 0-59
    val repeatDaily: Boolean = true,  // 是否每天重复
    val enabled: Boolean = true,      // 是否启用（关闭则不再提醒）
    val vibrate: Boolean = true,      // 提醒时是否震动
    val createdAt: Long = System.currentTimeMillis()
)
