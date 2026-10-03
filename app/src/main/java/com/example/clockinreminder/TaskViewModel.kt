package com.example.clockinreminder

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
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

    /**
     * 联动点①：今天已打卡的任务 id 集合。主页观察它，打卡后卡片状态自动刷新。
     *
     * 为什么用 MediatorLiveData 而不是直接 val：Room 的 LiveData 是在**创建那一刻**
     * 把日期写死在 SQL 参数里的。如果不管它，App 一直开着过了零点，"今日"就还是昨天。
     * 所以这里留了一个可以在跨天时换源的入口。
     */
    private val _todayDoneTaskIds = MediatorLiveData<List<Long>>()
    val todayDoneTaskIds: LiveData<List<Long>> get() = _todayDoneTaskIds

    /** 当前这份“今日”数据对应的日期 */
    private var todayKey: String = DateUtils.today()
    private var todaySource: LiveData<List<Long>>? = null

    init {
        val db = (application as ClockInApplication).database
        repository = TaskRepository(db)
        tasks = repository.allTasks
        attachToday()
    }

    private fun attachToday() {
        todaySource?.let { _todayDoneTaskIds.removeSource(it) }
        val source = repository.doneTaskIdsOn(todayKey)
        todaySource = source
        _todayDoneTaskIds.addSource(source) { _todayDoneTaskIds.value = it }
    }

    /**
     * 联动点⑤ 的一半（跨天自愈）：App 一直开着过了零点时，把"今日"重新指向新的一天。
     * 由 MainActivity.onResume 调用 —— 成本极低，每次回到前台检查一下即可。
     */
    fun rolloverIfDateChanged() {
        val now = DateUtils.today()
        if (now != todayKey) {
            todayKey = now
            attachToday()
        }
    }

    suspend fun insertTask(task: Task): Long = repository.insertTask(task)
    suspend fun updateTask(task: Task) = repository.updateTask(task)

    /** 删除任务（联动点⑥：连带清理打卡记录） */
    suspend fun deleteTask(task: Task) = repository.deleteTask(task)

    suspend fun getAllTasks(): List<Task> = repository.getAllTasks()
    suspend fun getTaskById(id: Long): Task? = repository.getTaskById(id)

    /** 一键标记完成：写入今日打卡记录（重复点击靠唯一索引去重） */
    suspend fun markDone(taskId: Long) {
        rolloverIfDateChanged()   // 保证写的是"今天"，而不是跨天前的昨天
        repository.insertRecord(CheckInRecord(taskId = taskId, date = todayKey))
    }

    /** 撤销今日打卡（联动点①：点错了要能改回来） */
    suspend fun undoDone(taskId: Long) {
        rolloverIfDateChanged()
        repository.undoRecordOn(taskId, todayKey)
    }

    suspend fun getDatesForTask(taskId: Long): List<String> = repository.getDatesForTask(taskId)
    suspend fun getCountForTask(taskId: Long): Int = repository.getCountForTask(taskId)
}
