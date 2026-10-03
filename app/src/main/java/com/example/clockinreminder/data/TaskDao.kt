package com.example.clockinreminder.data

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface TaskDao {
    // 列表观察（UI 自动刷新）
    @Query("SELECT * FROM tasks ORDER BY hour ASC, minute ASC")
    fun getAll(): LiveData<List<Task>>

    // 一次性取全量（用于 BootReceiver 等无观察者场景）
    @Query("SELECT * FROM tasks")
    suspend fun getAllList(): List<Task>

    @Insert
    suspend fun insert(task: Task): Long

    @Update
    suspend fun update(task: Task)

    @Delete
    suspend fun delete(task: Task)

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: Long): Task?
}
