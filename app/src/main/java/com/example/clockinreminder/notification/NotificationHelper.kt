package com.example.clockinreminder.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.example.clockinreminder.MainActivity
import com.example.clockinreminder.R
import com.example.clockinreminder.receiver.MarkDoneReceiver

/**
 * 通知 + 震动模块。
 *
 * - Android 8+ 必须创建 NotificationChannel，否则通知不显示。
 * - 通过 NotificationCompat 保证低版本兼容。
 * - 除了通知自带的震动，额外用 Vibrator 再震一次，提升可靠性（部分机型通知震动被系统吞掉）。
 */
object NotificationHelper {
    const val CHANNEL_ID = "clockin_reminder_channel"
    private val VIBRATION_PATTERN = longArrayOf(0, 600, 300, 600) // 震-停-震

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val soundUri: Uri = Settings.System.DEFAULT_NOTIFICATION_URI
            val channel = NotificationChannel(
                CHANNEL_ID,
                "打卡提醒",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "定时打卡提醒通知"
                enableVibration(true)
                vibrationPattern = VIBRATION_PATTERN
                // 显式设置声音，避免部分 ROM 默认静音导致毫无提示
                setSound(
                    soundUri,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    /**
     * 弹出打卡提醒通知，并附带“标记完成”快捷操作。
     *
     * 关键设计：
     * - setOngoing(true) 让用户无法通过侧滑或通知中心的“全部清除”删掉该通知；
     * - 它只能等用户在 App 内打卡（或通知栏点“标记完成”）后，由应用主动 cancel 才会消失。
     */
    fun showReminder(context: Context, taskId: Long, taskName: String, vibrate: Boolean) {
        createChannel(context)

        // 点击通知主体 → 打开 App 主页
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 锁屏全屏强提醒（类似来电），确保息屏/锁屏也能看到，不被折叠忽略
        val fullScreenIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val fullScreenPI = PendingIntent.getActivity(
            context, 0x1001, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 通知内“标记完成”按钮 → 广播写入打卡记录
        val doneIntent = Intent(context, MarkDoneReceiver::class.java).apply {
            putExtra("taskId", taskId)
        }
        val donePendingIntent = PendingIntent.getBroadcast(
            context, (taskId + 100000).toInt(), doneIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("⏰ 打卡提醒")
            .setContentText("该「$taskName」啦，点击去打卡！")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(contentPendingIntent)
            .setFullScreenIntent(fullScreenPI, true)
            .setOngoing(true)          // 常驻通知：清不掉、划不掉、全部清除无效
            .setAutoCancel(false)      // 点击通知主体也不自动消失，必须打完卡才消失
            .addAction(R.drawable.ic_notification, "标记完成", donePendingIntent)

        if (vibrate) builder.setVibrate(VIBRATION_PATTERN)

        context.getSystemService(NotificationManager::class.java)
            .notify(taskId.toInt(), builder.build())

        if (vibrate) doVibrate(context)
    }

    /** 取消某条提醒通知（用户完成打卡或删除任务后调用） */
    fun cancelReminder(context: Context, taskId: Long) {
        context.getSystemService(NotificationManager::class.java)
            ?.cancel(taskId.toInt())
    }

    /** 直接调用系统震动服务 */
    private fun doVibrate(context: Context) {
        val vibrator = context.getSystemService(Vibrator::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(VIBRATION_PATTERN, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(VIBRATION_PATTERN, -1)
        }
    }
}
