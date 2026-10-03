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
 * 3. 联动点④ 的「稍后提醒」用另一段 requestCode（+200000），与每日闹钟互不干扰，
 *    这样删任务时两个都能精确撤掉，不会漏响。
 */
object AlarmScheduler {

    /** 通知点击时展示用的 PendingIntent 偏移（与闹钟本身区分） */
    private const val SHOW_INTENT_OFFSET = 100_000

    /** 联动点④：snooze 闹钟的 requestCode 偏移 */
    private const val SNOOZE_REQUEST_OFFSET = 200_000

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
                (task.id + SHOW_INTENT_OFFSET).toInt(), // 与上面 pi 区分，避免 PendingIntent 冲突
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

    /**
     * 联动点④：排一个一次性的「稍后提醒」闹钟。
     * 到点后仍走 AlarmReceiver，但带 snoozeCount=1 —— 补发的通知不再给“稍后”按钮，
     * 于是“最多只推一次”天然成立，不需要额外存状态。
     */
    fun scheduleSnooze(
        context: Context,
        taskId: Long,
        taskName: String,
        vibrate: Boolean,
        delayMinutes: Int
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("taskId", taskId)
            putExtra("taskName", taskName)
            putExtra("vibrate", vibrate)
            putExtra("snoozeCount", 1)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            (taskId + SNOOZE_REQUEST_OFFSET).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val triggerAt = System.currentTimeMillis() + delayMinutes * 60_000L

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val showIntent = PendingIntent.getActivity(
                context,
                (taskId + SHOW_INTENT_OFFSET).toInt(),
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, showIntent), pi)
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    /** 只撤掉「稍后提醒」闹钟（用户在通知栏直接打卡后用，免得 10 分钟后又响） */
    fun cancelSnooze(context: Context, taskId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        existingAlarmPi(context, taskId, SNOOZE_REQUEST_OFFSET)?.let { alarmManager.cancel(it) }
    }

    /** 取消某个任务的全部闹钟：每日闹钟 + 可能还排着的 snooze 闹钟 */
    fun cancel(context: Context, taskId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // requestCode 必须与 schedule 时一致才能匹配到并取消
        existingAlarmPi(context, taskId, 0)?.let { alarmManager.cancel(it) }
        // 联动点④：snooze 用的是另一段 requestCode，不单独撤的话，
        // “删掉/禁用任务之后它还会响最后一次”。
        existingAlarmPi(context, taskId, SNOOZE_REQUEST_OFFSET)?.let { alarmManager.cancel(it) }
    }

    /**
     * 取出「已经存在」的闹钟 PendingIntent：用 FLAG_NO_CREATE —— 有就返回，没有就返回 null。
     * 比 FLAG_UPDATE_CURRENT 更稳：不会顺手改写已有 PendingIntent 的内容（避免把里面的 extra 冲掉）。
     */
    private fun existingAlarmPi(context: Context, taskId: Long, offset: Int): PendingIntent? =
        PendingIntent.getBroadcast(
            context,
            (taskId + offset).toInt(),
            Intent(context, AlarmReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )

    /** 批量重新调度（开机/应用更新后调用） */
    fun rescheduleAll(context: Context, tasks: List<Task>) {
        tasks.forEach { schedule(context, it) }
    }
}
