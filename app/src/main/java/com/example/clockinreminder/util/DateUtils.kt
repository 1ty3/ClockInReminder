package com.example.clockinreminder.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * 日期 / 时间相关工具。
 */
object DateUtils {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    /** 今天日期字符串，如 "2026-10-01" */
    fun today(): String = dateFormat.format(Calendar.getInstance().time)

    /** 昨天日期字符串 */
    fun yesterday(): String {
        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        return dateFormat.format(cal.time)
    }

    /** 格式化为 "HH:mm" */
    fun formatTime(hour: Int, minute: Int): String {
        val h = if (hour < 10) "0$hour" else "$hour"
        val m = if (minute < 10) "0$minute" else "$minute"
        return "$h:$m"
    }

    /**
     * 计算“下一次”触发时间（毫秒）。
     * 若今天的设定时间已过，则顺延到明天同一时刻。
     */
    fun nextTriggerMillis(hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }

    /**
     * 计算连续打卡天数。
     * 规则：若今天已打卡，从今天往前数；若今天还没打卡，则从昨天往前数（不中断“昨天及之前”的连续）。
     */
    fun currentStreak(dates: List<String>): Int {
        if (dates.isEmpty()) return 0
        val set = dates.toSet()
        var cursor = if (set.contains(today())) today() else yesterday()
        var streak = 0
        val cal = Calendar.getInstance()
        while (set.contains(cursor)) {
            streak++
            val parsed = dateFormat.parse(cursor) ?: break
            cal.time = parsed
            cal.add(Calendar.DAY_OF_YEAR, -1)
            cursor = dateFormat.format(cal.time)
        }
        return streak
    }
}
