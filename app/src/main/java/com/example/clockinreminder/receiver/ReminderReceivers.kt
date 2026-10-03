package com.example.clockinreminder.receiver

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.clockinreminder.ClockInApplication
import com.example.clockinreminder.data.CheckInRecord
import com.example.clockinreminder.notification.NotificationHelper
import com.example.clockinreminder.scheduler.AlarmScheduler
import com.example.clockinreminder.util.DateUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 闹钟到点：弹出通知 + 震动，并自动排好“明天同一时刻”的下一次提醒。
 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra("taskId", -1)
        val name = intent.getStringExtra("taskName") ?: "打卡"
        val vibrate = intent.getBooleanExtra("vibrate", true)

        NotificationHelper.showReminder(context, taskId, name, vibrate)

        // 后台重新读取任务，若是“每天重复”则排下一次
        CoroutineScope(Dispatchers.IO).launch {
            val db = (context.applicationContext as ClockInApplication).database
            val task = db.taskDao().getById(taskId)
            if (task != null && task.enabled && task.repeatDaily) {
                AlarmScheduler.schedule(context.applicationContext, task)
            }
        }
    }
}

/**
 * 通知栏“标记完成”按钮：直接写入一条今日打卡记录，并关闭该通知。
 */
class MarkDoneReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra("taskId", -1)
        if (taskId <= 0) return
        CoroutineScope(Dispatchers.IO).launch {
            val db = (context.applicationContext as ClockInApplication).database
            db.checkInDao().insert(CheckInRecord(taskId = taskId, date = DateUtils.today()))
            context.getSystemService(NotificationManager::class.java).cancel(taskId.toInt())
        }
    }
}

/**
 * 开机 / 应用更新后：把数据库里所有启用的任务重新排一遍闹钟。
 * （Android 重启后所有 AlarmManager 闹钟会清空，必须重排）
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            CoroutineScope(Dispatchers.IO).launch {
                val db = (context.applicationContext as ClockInApplication).database
                val tasks = db.taskDao().getAllList()
                tasks.forEach { AlarmScheduler.schedule(context.applicationContext, it) }
            }
        }
    }
}
