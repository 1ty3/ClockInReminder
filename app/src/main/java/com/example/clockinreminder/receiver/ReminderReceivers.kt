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
 * 联动点③（通知带连续天数）：把连续打卡天数算出来写进通知文案。
 * 联动点④（断签补救）：正常提醒带「稍后 N 分钟」按钮；由 snooze 补发的提醒不再带。
 *
 * 因为多了数据库查询，处理逻辑变成了异步的，所以用 goAsync() 告诉系统
 * “我还没处理完”，否则 onReceive 一返回系统就可能回收本广播所在的进程，
 * 通知就弹不出来了（这是加查询后最容易踩的坑）。
 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra("taskId", -1)
        if (taskId <= 0) return
        val name = intent.getStringExtra("taskName") ?: "打卡"
        val vibrate = intent.getBooleanExtra("vibrate", true)
        // 联动点④：这是 snooze 补发出来的那一次（snoozeCount=1）→ 不再给 snooze 按钮，实现“只补一次”
        val isSnoozeWakeUp = intent.getIntExtra("snoozeCount", 0) > 0
        val appContext = context.applicationContext

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = (appContext as ClockInApplication).database
                // 先把任务读出来：既用于“任务是否还在”的判断，也用于后面决定要不要排下一次
                val task = db.taskDao().getById(taskId)

                if (task == null) {
                    // 任务已被删除 —— 这条闹钟是孤儿（可能是 snooze 排下之后用户删了任务），
                    // 直接收掉残留通知，不要再弹。
                    NotificationHelper.cancelReminder(appContext, taskId)
                    return@launch
                }

                val today = DateUtils.today()
                val alreadyDone = db.checkInDao().countOn(taskId, today) > 0

                if (alreadyDone) {
                    // 联动点②：已打卡 → 不弹通知、不震动，静默跳过
                    // （顺手取消可能还挂在通知栏的旧提醒，避免"打过卡了但通知还在"）
                    NotificationHelper.cancelReminder(appContext, taskId)
                } else {
                    // 联动点③：算出连续打卡天数，写进通知文案
                    val dates = db.checkInDao().getDatesForTask(taskId)
                    val streak = DateUtils.currentStreak(dates)
                    NotificationHelper.showReminder(
                        context = appContext,
                        taskId = taskId,
                        taskName = name,
                        vibrate = vibrate,
                        streak = streak,
                        allowSnooze = !isSnoozeWakeUp   // 联动点④：只有第一次提醒才给“稍后”
                    )
                }

                // 后台重新读取的任务，若是“每天重复”则排下一次
                if (task.enabled && task.repeatDaily) {
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
                // 联动点④的收尾：既然已经从通知栏打过卡了，把可能排着的「稍后提醒」闹钟撤掉，
                // 否则 10 分钟后它还会再响一次。
                AlarmScheduler.cancelSnooze(appContext, taskId)
                appContext.getSystemService(NotificationManager::class.java).cancel(taskId.toInt())
            } finally {
                pending.finish()
            }
        }
    }
}

/**
 * 联动点④：通知栏「稍后 N 分钟」按钮。
 *
 * 做两件事：
 * 1. 先把当前这条通知收掉（用户已经表达了“我知道，待会儿再说”）；
 * 2. 排一个一次性闹钟，N 分钟后重新触发 AlarmReceiver。
 */
class SnoozeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra("taskId", -1)
        if (taskId <= 0) return
        val name = intent.getStringExtra("taskName") ?: "打卡"
        val vibrate = intent.getBooleanExtra("vibrate", true)
        val appContext = context.applicationContext

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 无论后续怎么判断，这条通知都该先收掉
                NotificationHelper.cancelReminder(appContext, taskId)

                val db = (appContext as ClockInApplication).database
                val task = db.taskDao().getById(taskId)
                val alreadyDone = db.checkInDao().countOn(taskId, DateUtils.today()) > 0
                // 任务没了 / 已被禁用 / 这 N 分钟里已经打过卡 → 就不用再排了，补救到此为止
                if (task != null && task.enabled && !alreadyDone) {
                    AlarmScheduler.scheduleSnooze(
                        context = appContext,
                        taskId = taskId,
                        taskName = name,
                        vibrate = vibrate,
                        delayMinutes = NotificationHelper.SNOOZE_MINUTES
                    )
                }
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
