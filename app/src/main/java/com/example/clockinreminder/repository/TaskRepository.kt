package com.example.clockinreminder.repository

import com.example.clockinreminder.data.AppDatabase
import com.example.clockinreminder.data.CheckInRecord
import com.example.clockinreminder.data.Task

/**
 * 仓库层：统一封装数据库访问，Activity/ViewModel 不直接碰 DAO。
 */
class TaskRepository(private val db: AppDatabase) {
    val allTasks = db.taskDao().getAll()

    suspend fun insertTask(task: Task): Long = db.taskDao().insert(task)
    suspend fun updateTask(task: Task) = db.taskDao().update(task)
    suspend fun deleteTask(task: Task) = db.taskDao().delete(task)
    suspend fun getAllTasks(): List<Task> = db.taskDao().getAllList()

    suspend fun insertRecord(record: CheckInRecord) = db.checkInDao().insert(record)
    suspend fun getDatesForTask(taskId: Long): List<String> = db.checkInDao().getDatesForTask(taskId)
    suspend fun getCountForTask(taskId: Long): Int = db.checkInDao().getCountForTask(taskId)
}
