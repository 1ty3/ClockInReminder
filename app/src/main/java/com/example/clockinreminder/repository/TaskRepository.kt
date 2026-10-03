package com.example.clockinreminder.repository

import androidx.lifecycle.LiveData
import androidx.room.withTransaction
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

    /**
     * 删除任务（联动点⑥）：连带删掉该任务的全部打卡记录。
     * 两条语句放在同一个事务里 —— 要么都成功，要么都不做，不会留下"记录还在但任务没了"的脏数据。
     */
    suspend fun deleteTask(task: Task) = db.withTransaction {
        db.checkInDao().deleteForTask(task.id)
        db.taskDao().delete(task)
    }

    suspend fun getAllTasks(): List<Task> = db.taskDao().getAllList()
    suspend fun getTaskById(id: Long): Task? = db.taskDao().getById(id)

    suspend fun insertRecord(record: CheckInRecord) = db.checkInDao().insert(record)

    /** 联动点⑧：批量写入打卡记录（一键打卡用，一次事务搞定） */
    suspend fun insertRecords(records: List<CheckInRecord>) = db.checkInDao().insertAll(records)
    suspend fun getDatesForTask(taskId: Long): List<String> = db.checkInDao().getDatesForTask(taskId)
    suspend fun getCountForTask(taskId: Long): Int = db.checkInDao().getCountForTask(taskId)

    /** 某一天已打卡的任务 id（LiveData，主页观察用）—— 联动点① */
    fun doneTaskIdsOn(date: String): LiveData<List<Long>> = db.checkInDao().getTaskIdsOn(date)

    /** 撤销某任务某天的打卡 —— 联动点① */
    suspend fun undoRecordOn(taskId: Long, date: String) = db.checkInDao().deleteOn(taskId, date)
}
