package com.example.clockinreminder.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.clockinreminder.MainActivity
import com.example.clockinreminder.data.Task
import com.example.clockinreminder.receiver.AlarmReceiver
import com.example.clockinreminder.util.DateUtils

/**
 * 闹钟调度中心。
 *
 * 关键设计：
 * 1. 主用 AlarmManager.setAlarmClock 实现“闹钟式提醒”——这是安卓里最扛后台杀进程的闹钟类型：
 *    - 不受 Doze（省电）模式限制；
 *    - 不需要 SCHEDULE_EXACT_ALARM 权限（精确闹钟权限限制对它无效）；
 *    - 即使 App 进程被系统回收/划掉，到点系统也会重启进程来触发广播。
 *    对“必须准时提醒”的打卡场景，它比 setExactAndAllowWhileIdle 可靠得多。
 * 2. 每个任务用 task.id 作为 PendingIntent 的 requestCode，保证互不冲突、可单独取消。
 */
object AlarmScheduler {

    private fun pendingIntent(context: Context, task: Task): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("taskId", task.id)
            putExtra("taskName", task.name)
            putExtra("vibrate", task.vibrate)
        }
        return PendingIntent.getBroadcast(
            context,
            task.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** 为单个任务设定/更新闹钟 */
    fun schedule(context: Context, task: Task) {
        // 关闭状态：取消可能存在的旧闹钟
        if (!task.enabled) {
            cancel(context, task.id)
            return
        }
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntent(context, task)
        val triggerAt = DateUtils.nextTriggerMillis(task.hour, task.minute)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // 闹钟式提醒：最可靠，杀后台/Doze 下也能准时触发
            val showIntent = PendingIntent.getActivity(
                context,
                (task.id + 100000).toInt(), // 与上面 pi 区分，避免 PendingIntent 冲突
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val info = AlarmManager.AlarmClockInfo(triggerAt, showIntent)
            alarmManager.setAlarmClock(info, pi)
        } else {
            // 极低版本兜底（实际 minSdk=23 不会走到这里）
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    /** 取消某个任务的闹钟 */
    fun cancel(context: Context, taskId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java)
        // requestCode 必须与 schedule 时一致才能匹配到并取消
        val pi = PendingIntent.getBroadcast(
            context,
            taskId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pi)
    }

    /** 批量重新调度（开机/应用更新后调用） */
    fun rescheduleAll(context: Context, tasks: List<Task>) {
        tasks.forEach { schedule(context, it) }
    }
}
