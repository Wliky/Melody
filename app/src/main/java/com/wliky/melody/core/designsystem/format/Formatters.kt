package com.wliky.melody.core.designsystem.format

import java.util.Locale
import kotlin.math.abs

/** 毫秒 → m:ss（超过一小时 → h:mm:ss）。 */
fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0) return "0:00"
    val totalSeconds = durationMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

/** 播放量等大数字 → 12.3万 / 1.2亿。 */
fun formatCount(count: Long): String = when {
    count < 0 -> ""
    count < 10_000 -> count.toString()
    count < 100_000_000 -> String.format(Locale.US, "%.1f万", count / 10_000.0)
    else -> String.format(Locale.US, "%.1f亿", count / 100_000_000.0)
}

/** 相对时间：刚刚 / 12 分钟前 / 3 小时前 / 2 天前 / 具体日期。 */
fun formatRelativeTime(timestampMs: Long, now: Long = System.currentTimeMillis()): String {
    if (timestampMs <= 0) return ""
    val diff = now - timestampMs
    if (diff < 0) return "刚刚"
    val minutes = diff / 60_000
    val hours = diff / 3_600_000
    val days = diff / 86_400_000
    return when {
        minutes < 1 -> "刚刚"
        minutes < 60 -> "${minutes} 分钟前"
        hours < 24 -> "${hours} 小时前"
        days < 30 -> "${days} 天前"
        else -> {
            val calendar = java.util.Calendar.getInstance().apply { timeInMillis = timestampMs }
            String.format(
                Locale.US,
                "%04d-%02d-%02d",
                calendar.get(java.util.Calendar.YEAR),
                calendar.get(java.util.Calendar.MONTH) + 1,
                calendar.get(java.util.Calendar.DAY_OF_MONTH),
            )
        }
    }
}

/** 两位补零，歌词/队列序号用。 */
fun padStart2(value: Int): String = if (abs(value) < 10) "0$value" else value.toString()
