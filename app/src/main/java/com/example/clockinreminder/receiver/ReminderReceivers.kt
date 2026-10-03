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
 *
 * 联动点②（已打卡免打扰）：到点后**先查今天打过卡没有，打过就一声不响地跳过**，
 * 不再是无脑弹通知。
 *
 * 因为多了这一次数据库查询，处理逻辑变成了异步的，所以用 goAsync() 告诉系统
 * “我还没处理完”，否则 onReceive 一返回系统就可能回收本广播所在的进程，
 * 通知就弹不出来了（这是加查询后最容易踩的坑）。
 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra("taskId", -1)
        if (taskId <= 0) return
        val name = intent.getStringExtra("taskName") ?: "打卡"
        val vibrate = intent.getBooleanExtra("vibrate", true)
        val appContext = context.applicationContext

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = (appContext as ClockInApplication).database
                val today = DateUtils.today()
                val alreadyDone = db.checkInDao().countOn(taskId, today) > 0

                if (alreadyDone) {
                    // 联动点②：已打卡 → 不弹通知、不震动，静默跳过
                    // （顺手取消可能还挂在通知栏的旧提醒，避免"打过卡了但通知还在"）
                    NotificationHelper.cancelReminder(appContext, taskId)
                } else {
                    NotificationHelper.showReminder(appContext, taskId, name, vibrate)
                }

                // 后台重新读取任务，若是“每天重复”则排下一次
                val task = db.taskDao().getById(taskId)
                if (task != null && task.enabled && task.repeatDaily) {
                    AlarmScheduler.schedule(appContext, task)
                }
            } finally {
                pending.finish()
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
        val appContext = context.applicationContext

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = (appContext as ClockInApplication).database
                // 联动点⑥：任务可能已经被删掉了，此时这张通知是"孤儿"，
                // 不能再往库里写记录（否则留下查不到的脏数据），直接收掉通知即可。
                val task = db.taskDao().getById(taskId)
                if (task != null) {
                    db.checkInDao().insert(CheckInRecord(taskId = taskId, date = DateUtils.today()))
                }
                appContext.getSystemService(NotificationManager::class.java).cancel(taskId.toInt())
            } finally {
                pending.finish()
            }
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
