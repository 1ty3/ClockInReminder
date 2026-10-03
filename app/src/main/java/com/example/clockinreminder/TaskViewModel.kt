package com.example.clockinreminder

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import com.example.clockinreminder.data.CheckInRecord
import com.example.clockinreminder.data.Task
import com.example.clockinreminder.repository.TaskRepository
import com.example.clockinreminder.util.DateUtils

/**
 * ViewModel：持有任务列表 LiveData，并暴露增删改 / 打卡 / 历史查询等挂起函数。
 */
class TaskViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: TaskRepository
    val tasks: LiveData<List<Task>>

    init {
        val db = (application as ClockInApplication).database
        repository = TaskRepository(db)
        tasks = repository.allTasks
    }

    suspend fun insertTask(task: Task): Long = repository.insertTask(task)
    suspend fun updateTask(task: Task) = repository.updateTask(task)
    suspend fun deleteTask(task: Task) = repository.deleteTask(task)
    suspend fun getAllTasks(): List<Task> = repository.getAllTasks()

    /** 一键标记完成：写入今日打卡记录（重复点击靠唯一索引去重） */
    suspend fun markDone(taskId: Long) {
        repository.insertRecord(CheckInRecord(taskId = taskId, date = DateUtils.today()))
    }

    suspend fun getDatesForTask(taskId: Long): List<String> = repository.getDatesForTask(taskId)
    suspend fun getCountForTask(taskId: Long): Int = repository.getCountForTask(taskId)
}
